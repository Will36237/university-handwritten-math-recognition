import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, Form, Request, UploadFile

from shared.contracts import GatewayHealth, ModelName, PredictionResponse
from shared.errors import ApiError, api_error_handler
from shared.image_validation import MAX_IMAGE_BYTES, validate_image
from shared.request_context import install_request_context
from shared.settings import GatewaySettings

from .worker_client import WorkerClient


SERVICE_VERSION = "0.2.0-gateway"
settings = GatewaySettings.from_env()
worker_client = WorkerClient(settings)


@asynccontextmanager
async def lifespan(app: FastAPI):
    yield
    await worker_client.close()


app = FastAPI(
    title="University HMER API Gateway",
    version=SERVICE_VERSION,
    lifespan=lifespan,
)
app.add_exception_handler(ApiError, api_error_handler)
install_request_context(app)


@app.get("/health", response_model=GatewayHealth)
async def health() -> GatewayHealth:
    statuses = dict(
        await asyncio.gather(
            *(
                worker_client.health(model, url)
                for model, url in settings.workers.items()
            ),
        ),
    )
    ready = all(status in {"ready", "configured"} for status in statuses.values())
    return GatewayHealth(
        status="ready" if ready else "degraded",
        service_version=SERVICE_VERSION,
        models=statuses,
    )


@app.post("/predict", response_model=PredictionResponse)
async def predict(
    request: Request,
    image: UploadFile = File(...),
    model: ModelName = Form(...),
) -> PredictionResponse:
    payload = await image.read(MAX_IMAGE_BYTES + 1)
    validated = validate_image(payload)
    request_id = request.state.request_id
    result = await worker_client.predict(
        model=model,
        request_id=request_id,
        filename=image.filename or "formula.png",
        content_type=image.content_type or "application/octet-stream",
        payload=payload,
    )

    return PredictionResponse(
        request_id=request_id,
        model=result.model,
        latex=result.latex,
        latency_ms=result.latency_ms,
        valid_latex=result.valid_latex,
        image=result.image.model_copy(
            update={
                "width": validated.width,
                "height": validated.height,
                "format": validated.format,
            },
        ),
        mock=result.mock,
    )
