from pathlib import Path

from fastapi.testclient import TestClient
from PIL import Image

from shared.worker_app import WorkerSpec, create_worker_app


FORMULA_BYTES = (
    Path(__file__).resolve().parents[3] / "test_formula_tight.png"
).read_bytes()


class FakeAdapter:
    def __init__(self, result: str = r"x^2", error: RuntimeError | None = None) -> None:
        self.loaded = False
        self.device = "unloaded"
        self.result = result
        self.error = error
        self.load_calls = 0
        self.predict_calls = 0

    def load(self) -> None:
        self.load_calls += 1
        self.loaded = True
        self.device = "cpu"

    def predict(self, image: Image.Image) -> str:
        self.predict_calls += 1
        if self.error is not None:
            raise self.error
        return self.result


def make_spec(**overrides) -> WorkerSpec:
    values = {
        "title": "Test Worker",
        "version": "0.1.0",
        "model": "tamer_a3",
        "mode": "mock",
        "eager_load": False,
        "mock_delay_seconds": 0.0,
        "mock_latex": r"x",
        "empty_output_message": "empty output",
        "unsupported_mode_label": "TAMER",
    }
    values.update(overrides)
    return WorkerSpec(**values)


def predict(client: TestClient):
    return client.post(
        "/predict",
        files={"image": ("formula.png", FORMULA_BYTES, "image/png")},
    )


def test_mock_worker_preserves_response_contract() -> None:
    app = create_worker_app(make_spec(), FakeAdapter())

    response = predict(TestClient(app))

    assert response.status_code == 200
    assert response.json() == {
        "model": "tamer_a3",
        "latex": "x",
        "latency_ms": response.json()["latency_ms"],
        "valid_latex": True,
        "image": {"width": 900, "height": 212, "format": "PNG"},
        "mock": True,
    }


def test_health_reports_ready_for_mock_and_configured_for_unloaded_real() -> None:
    mock_health = TestClient(
        create_worker_app(make_spec(mode="mock"), FakeAdapter()),
    ).get("/health")
    real_health = TestClient(
        create_worker_app(make_spec(mode="real"), FakeAdapter()),
    ).get("/health")

    assert mock_health.json()["status"] == "ready"
    assert real_health.json()["status"] == "configured"


def test_real_worker_uses_adapter_output() -> None:
    adapter = FakeAdapter(result=r"y=1")
    app = create_worker_app(make_spec(mode="real"), adapter)

    response = predict(TestClient(app))

    assert response.status_code == 200
    assert response.json()["latex"] == r"y=1"
    assert response.json()["mock"] is False
    assert adapter.predict_calls == 1


def test_eager_load_runs_during_lifespan() -> None:
    adapter = FakeAdapter()
    app = create_worker_app(
        make_spec(mode="real", eager_load=True),
        adapter,
    )

    with TestClient(app):
        assert adapter.loaded is True

    assert adapter.load_calls == 1


def test_unsupported_mode_preserves_error_contract() -> None:
    app = create_worker_app(make_spec(mode="invalid"), FakeAdapter())

    response = predict(TestClient(app))

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "MODEL_UNAVAILABLE"
    assert response.json()["error"]["message"] == "Unsupported TAMER mode: invalid"


def test_empty_real_output_preserves_error_contract() -> None:
    app = create_worker_app(
        make_spec(mode="real", empty_output_message="TAMER empty"),
        FakeAdapter(result=""),
    )

    response = predict(TestClient(app))

    assert response.status_code == 500
    assert response.json()["error"]["code"] == "EMPTY_MODEL_OUTPUT"
    assert response.json()["error"]["message"] == "TAMER empty"


def test_adapter_runtime_error_maps_to_model_unavailable() -> None:
    app = create_worker_app(
        make_spec(mode="real"),
        FakeAdapter(error=RuntimeError("adapter failed")),
    )

    response = predict(TestClient(app))

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "MODEL_UNAVAILABLE"
    assert response.json()["error"]["message"] == "adapter failed"
