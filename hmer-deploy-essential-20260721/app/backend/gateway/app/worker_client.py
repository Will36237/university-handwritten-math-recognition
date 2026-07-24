import httpx

from shared.contracts import ModelName, WorkerPrediction
from shared.errors import ApiError
from shared.settings import GatewaySettings


class WorkerClient:
    def __init__(
        self,
        settings: GatewaySettings,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self.settings = settings
        client_options = {}
        if transport is not None:
            client_options["transport"] = transport
        self.client = httpx.AsyncClient(**client_options)

    async def close(self) -> None:
        await self.client.aclose()

    async def health(self, model: str, url: str) -> tuple[str, str]:
        try:
            response = await self.client.get(
                f"{url}/health",
                timeout=self.settings.connect_timeout_seconds,
            )
            if response.is_success:
                return model, response.json().get("status", "ready")
            return model, "unavailable"
        except (httpx.HTTPError, ValueError):
            return model, "unavailable"

    async def predict(
        self,
        model: ModelName,
        request_id: str,
        filename: str,
        content_type: str,
        payload: bytes,
    ) -> WorkerPrediction:
        try:
            response = await self.client.post(
                f"{self.settings.workers[model]}/predict",
                headers={"X-Request-ID": request_id},
                files={"image": (filename, payload, content_type)},
                timeout=self.settings.predict_timeout_seconds,
            )
        except httpx.TimeoutException as error:
            raise ApiError(
                504,
                "MODEL_TIMEOUT",
                "Mô hình phản hồi quá thời gian cho phép.",
            ) from error
        except httpx.HTTPError as error:
            raise ApiError(
                503,
                "MODEL_UNAVAILABLE",
                "Không thể kết nối tới worker của mô hình.",
            ) from error

        if not response.is_success:
            try:
                detail = response.json().get("error", {})
            except ValueError:
                detail = {}
            raise ApiError(
                response.status_code,
                detail.get("code", "MODEL_ERROR"),
                detail.get("message", "Worker không thể xử lý ảnh."),
            )

        try:
            return WorkerPrediction.model_validate(response.json())
        except (ValueError, TypeError) as error:
            raise ApiError(
                502,
                "INVALID_WORKER_RESPONSE",
                "Worker trả về dữ liệu không hợp lệ.",
            ) from error
