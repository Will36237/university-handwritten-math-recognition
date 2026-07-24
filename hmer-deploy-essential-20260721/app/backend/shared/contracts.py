from typing import Literal

from pydantic import BaseModel


ModelName = Literal["tamer_a3", "unimumer_lora"]


class ImageInfo(BaseModel):
    width: int
    height: int
    format: str


class WorkerPrediction(BaseModel):
    model: ModelName
    latex: str
    latency_ms: float
    valid_latex: bool
    image: ImageInfo
    mock: bool


class PredictionResponse(WorkerPrediction):
    request_id: str


class WorkerHealth(BaseModel):
    status: str
    model: ModelName
    mode: str
    device: str


class GatewayHealth(BaseModel):
    status: str
    service_version: str
    models: dict[str, str]

