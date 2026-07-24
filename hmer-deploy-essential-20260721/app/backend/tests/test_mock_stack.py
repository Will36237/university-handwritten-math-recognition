from __future__ import annotations

import os
import socket
import subprocess
import sys
import time
from collections.abc import Iterator
from pathlib import Path
from urllib.parse import urlsplit
from uuid import UUID

import httpx
import pytest


BACKEND_ROOT = Path(__file__).resolve().parents[1]
BUNDLE_ROOT = BACKEND_ROOT.parents[1]

POSITIVE_IMAGE = BUNDLE_ROOT / "test_formula_tight.png"
NEGATIVE_IMAGE = BUNDLE_ROOT / "test_formula.png"


def _allocate_service_urls() -> tuple[str, str, str]:
    sockets: list[socket.socket] = []
    try:
        for _ in range(3):
            listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            listener.bind(("127.0.0.1", 0))
            sockets.append(listener)

        ports = [listener.getsockname()[1] for listener in sockets]
        return (
            f"http://127.0.0.1:{ports[0]}",
            f"http://127.0.0.1:{ports[1]}",
            f"http://127.0.0.1:{ports[2]}",
        )
    finally:
        for listener in sockets:
            listener.close()


TAMER_URL, UNIMUMER_URL, GATEWAY_URL = _allocate_service_urls()


def wait_for_health(
    base_url: str,
    process: subprocess.Popen[bytes],
    timeout_seconds: float = 15,
) -> None:
    deadline = time.monotonic() + timeout_seconds

    while time.monotonic() < deadline:
        if process.poll() is not None:
            pytest.fail(f"Service {base_url} exited with code {process.returncode}")

        try:
            response = httpx.get(f"{base_url}/health", timeout=0.5)
            if response.status_code == 200:
                return
        except httpx.HTTPError:
            pass

        time.sleep(0.1)

    pytest.fail(f"Service {base_url} did not become healthy")


def start_service(
    module: str,
    port: int,
    environment: dict[str, str],
) -> subprocess.Popen[bytes]:
    return subprocess.Popen(
        [
            sys.executable,
            "-m",
            "uvicorn",
            module,
            "--host",
            "127.0.0.1",
            "--port",
            str(port),
        ],
        cwd=BACKEND_ROOT,
        env=environment,
    )


@pytest.fixture(scope="session", autouse=True)
def mock_stack() -> Iterator[None]:
    environment = os.environ.copy()
    environment.update(
        {
            "HMER_TAMER_MODE": "mock",
            "HMER_TAMER_EAGER_LOAD": "false",
            "HMER_UNIMUMER_MODE": "mock",
            "HMER_UNIMUMER_EAGER_LOAD": "false",
            "HMER_TAMER_WORKER_URL": TAMER_URL,
            "HMER_UNIMUMER_WORKER_URL": UNIMUMER_URL,
        }
    )

    services = (
        ("workers.tamer.app.main:app", TAMER_URL),
        ("workers.unimumer.app.main:app", UNIMUMER_URL),
        ("gateway.app.main:app", GATEWAY_URL),
    )
    processes: list[subprocess.Popen[bytes]] = []

    try:
        for module, base_url in services:
            port = urlsplit(base_url).port
            if port is None:
                pytest.fail(f"Service URL has no port: {base_url}")
            process = start_service(module, port, environment)
            processes.append(process)
            wait_for_health(base_url, process)

        yield
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                process.terminate()

        for process in reversed(processes):
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)


@pytest.mark.parametrize(
    ("base_url", "expected_model"),
    (
        (TAMER_URL, "tamer_a3"),
        (UNIMUMER_URL, "unimumer_lora"),
    ),
)
def test_worker_health_contract(
    base_url: str,
    expected_model: str,
) -> None:
    response = httpx.get(f"{base_url}/health", timeout=2)

    assert response.status_code == 200
    assert response.json() == {
        "status": "ready",
        "model": expected_model,
        "mode": "mock",
        "device": "unloaded",
    }


def test_gateway_health_contract() -> None:
    response = httpx.get(f"{GATEWAY_URL}/health", timeout=2)

    assert response.status_code == 200
    assert response.json() == {
        "status": "ready",
        "service_version": "0.2.0-gateway",
        "models": {
            "tamer_a3": "ready",
            "unimumer_lora": "ready",
        },
    }


def predict(
    model: str,
    image_path: Path,
    request_id: str | None = None,
) -> httpx.Response:
    headers = {"X-Request-ID": request_id} if request_id else {}

    with image_path.open("rb") as image:
        return httpx.post(
            f"{GATEWAY_URL}/predict",
            data={"model": model},
            files={"image": (image_path.name, image, "image/png")},
            headers=headers,
            timeout=5,
        )


@pytest.mark.parametrize(
    ("model", "expected_latex"),
    (
        ("tamer_a3", r"\int_{0}^{1} x^{2}\,dx = \frac{1}{3}"),
        (
            "unimumer_lora",
            r"\int_{0}^{1} x^{2}\,\mathrm{d}x = \frac{1}{3}",
        ),
    ),
)
def test_predict_contract(
    model: str,
    expected_latex: str,
) -> None:
    response = predict(model, POSITIVE_IMAGE)

    assert response.status_code == 200, response.text

    result = response.json()
    UUID(result["request_id"])

    assert result["model"] == model
    assert result["latex"] == expected_latex
    assert result["latency_ms"] >= 0
    assert result["valid_latex"] is True
    assert result["image"] == {
        "width": 900,
        "height": 212,
        "format": "PNG",
    }
    assert result["mock"] is True


def test_request_id_is_propagated() -> None:
    request_id = "baseline-request-id"

    response = predict(
        "tamer_a3",
        POSITIVE_IMAGE,
        request_id=request_id,
    )

    assert response.status_code == 200
    assert response.headers["X-Request-ID"] == request_id
    assert response.json()["request_id"] == request_id


def test_no_formula_content_contract() -> None:
    response = predict("tamer_a3", NEGATIVE_IMAGE)

    assert response.status_code == 422

    error = response.json()["error"]
    UUID(error["request_id"])

    assert error["code"] == "NO_FORMULA_CONTENT"
    assert error["message"] == "Không phát hiện nét viết trong vùng đã cắt."
