import asyncio

import httpx
import pytest

from gateway.app.worker_client import WorkerClient
from shared.errors import ApiError
from shared.settings import GatewaySettings


SETTINGS = GatewaySettings.from_env(
    {
        "HMER_TAMER_WORKER_URL": "http://tamer:8101",
        "HMER_UNIMUMER_WORKER_URL": "http://unimumer:8102",
        "HMER_CONNECT_TIMEOUT_SECONDS": "5",
        "HMER_PREDICT_TIMEOUT_SECONDS": "120",
    },
)
VALID_WORKER_RESPONSE = {
    "model": "tamer_a3",
    "latex": r"x^2",
    "latency_ms": 10.5,
    "valid_latex": True,
    "image": {"width": 100, "height": 80, "format": "PNG"},
    "mock": True,
}


def test_predict_forwards_request_id_and_parses_response() -> None:
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["request_id"] = request.headers["X-Request-ID"]
        seen["content_type"] = request.headers["Content-Type"]
        return httpx.Response(200, json=VALID_WORKER_RESPONSE)

    client = WorkerClient(SETTINGS, httpx.MockTransport(handler))

    result = asyncio.run(
        client.predict(
            "tamer_a3",
            "req-1",
            "formula.png",
            "image/png",
            b"png",
        ),
    )

    assert seen["url"] == "http://tamer:8101/predict"
    assert seen["request_id"] == "req-1"
    assert str(seen["content_type"]).startswith("multipart/form-data; boundary=")
    assert result.model == "tamer_a3"
    assert result.latex == r"x^2"


def test_health_maps_success_and_failures_to_status() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.host == "tamer":
            return httpx.Response(200, json={"status": "configured"})
        return httpx.Response(503)

    client = WorkerClient(SETTINGS, httpx.MockTransport(handler))

    ready = asyncio.run(client.health("tamer_a3", "http://tamer:8101"))
    unavailable = asyncio.run(
        client.health("unimumer_lora", "http://unimumer:8102"),
    )

    assert ready == ("tamer_a3", "configured")
    assert unavailable == ("unimumer_lora", "unavailable")


def test_predict_maps_timeout() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("slow", request=request)

    client = WorkerClient(SETTINGS, httpx.MockTransport(handler))

    with pytest.raises(ApiError) as captured:
        asyncio.run(client.predict("tamer_a3", "id", "f.png", "image/png", b"x"))

    assert captured.value.status_code == 504
    assert captured.value.code == "MODEL_TIMEOUT"
    assert captured.value.message == "Mô hình phản hồi quá thời gian cho phép."


def test_predict_maps_connection_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("offline", request=request)

    client = WorkerClient(SETTINGS, httpx.MockTransport(handler))

    with pytest.raises(ApiError) as captured:
        asyncio.run(client.predict("tamer_a3", "id", "f.png", "image/png", b"x"))

    assert captured.value.status_code == 503
    assert captured.value.code == "MODEL_UNAVAILABLE"
    assert captured.value.message == "Không thể kết nối tới worker của mô hình."


def test_predict_passes_through_worker_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            422,
            json={"error": {"code": "NO_FORMULA_CONTENT", "message": "no formula"}},
        )

    client = WorkerClient(SETTINGS, httpx.MockTransport(handler))

    with pytest.raises(ApiError) as captured:
        asyncio.run(client.predict("tamer_a3", "id", "f.png", "image/png", b"x"))

    assert captured.value.status_code == 422
    assert captured.value.code == "NO_FORMULA_CONTENT"
    assert captured.value.message == "no formula"


def test_predict_rejects_invalid_success_response() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, text="not-json")

    client = WorkerClient(SETTINGS, httpx.MockTransport(handler))

    with pytest.raises(ApiError) as captured:
        asyncio.run(client.predict("tamer_a3", "id", "f.png", "image/png", b"x"))

    assert captured.value.status_code == 502
    assert captured.value.code == "INVALID_WORKER_RESPONSE"
    assert captured.value.message == "Worker trả về dữ liệu không hợp lệ."
