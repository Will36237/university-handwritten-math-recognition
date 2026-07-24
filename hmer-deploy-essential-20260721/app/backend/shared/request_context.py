from uuid import uuid4

from fastapi import FastAPI, Request


def install_request_context(app: FastAPI) -> None:
    @app.middleware("http")
    async def request_context(request: Request, call_next):
        request.state.request_id = request.headers.get("X-Request-ID") or str(uuid4())
        response = await call_next(request)
        response.headers["X-Request-ID"] = request.state.request_id
        return response
