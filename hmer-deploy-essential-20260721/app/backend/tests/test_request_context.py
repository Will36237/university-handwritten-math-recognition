from fastapi import FastAPI, Request
from fastapi.testclient import TestClient

from shared.request_context import install_request_context


def make_client() -> TestClient:
    app = FastAPI()
    install_request_context(app)

    @app.get("/")
    async def root(request: Request) -> dict[str, str]:
        return {"request_id": request.state.request_id}

    return TestClient(app)


def test_preserves_client_request_id() -> None:
    response = make_client().get(
        "/",
        headers={"X-Request-ID": "fixed-id"},
    )

    assert response.json() == {"request_id": "fixed-id"}
    assert response.headers["X-Request-ID"] == "fixed-id"


def test_generates_same_request_id_for_state_and_response() -> None:
    response = make_client().get("/")

    generated = response.json()["request_id"]
    assert generated
    assert generated == response.headers["X-Request-ID"]
