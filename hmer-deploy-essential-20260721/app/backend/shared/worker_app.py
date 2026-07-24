import asyncio
from contextlib import asynccontextmanager
from dataclasses import dataclass
from time import perf_counter
from typing import Protocol

from fastapi import FastAPI, File, Request, UploadFile
from PIL import Image

from .contracts import ImageInfo, ModelName, WorkerHealth, WorkerPrediction
from .errors import ApiError, api_error_handler
from .image_validation import MAX_IMAGE_BYTES, validate_image
from .request_context import install_request_context


class InferenceAdapter(Protocol):
    @property
    def loaded(self) -> bool: ...

    @property
    def device(self) -> str: ...

    def load(self) -> None: ...

    def predict(self, image: Image.Image) -> str: ...


@dataclass(frozen=True)
class WorkerSpec:
    title: str
    version: str
    model: ModelName
    mode: str
    eager_load: bool
    mock_delay_seconds: float
    mock_latex: str
    empty_output_message: str
    unsupported_mode_label: str


def create_worker_app(spec: WorkerSpec, adapter: InferenceAdapter) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if spec.eager_load:
            await asyncio.to_thread(adapter.load)
        yield

    app = FastAPI(title=spec.title, version=spec.version, lifespan=lifespan)
    app.add_exception_handler(ApiError, api_error_handler)
    install_request_context(app)

    @app.get("/health", response_model=WorkerHealth)
    async def health() -> WorkerHealth:
        status = "ready" if spec.mode == "mock" or adapter.loaded else "configured"
        return WorkerHealth(
            status=status,
            model=spec.model,
            mode=spec.mode,
            device=adapter.device,
        )

    @app.post("/predict", response_model=WorkerPrediction)
    async def predict(
        request: Request,
        image: UploadFile = File(...),
    ) -> WorkerPrediction:
        validated = validate_image(await image.read(MAX_IMAGE_BYTES + 1))
        started = perf_counter()
        if spec.mode == "mock":
            await asyncio.sleep(spec.mock_delay_seconds)
            latex = spec.mock_latex
        elif spec.mode == "real":
            try:
                latex = await asyncio.to_thread(
                    adapter.predict,
                    validated.image.copy(),
                )
            except RuntimeError as error:
                raise ApiError(503, "MODEL_UNAVAILABLE", str(error)) from error
        else:
            raise ApiError(
                503,
                "MODEL_UNAVAILABLE",
                f"Unsupported {spec.unsupported_mode_label} mode: {spec.mode}",
            )

        if not latex:
            raise ApiError(500, "EMPTY_MODEL_OUTPUT", spec.empty_output_message)

        return WorkerPrediction(
            model=spec.model,
            latex=latex,
            latency_ms=round((perf_counter() - started) * 1000, 2),
            valid_latex=True,
            image=ImageInfo(
                width=validated.width,
                height=validated.height,
                format=validated.format,
            ),
            mock=spec.mode == "mock",
        )

    return app
