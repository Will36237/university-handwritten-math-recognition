from dataclasses import dataclass
from io import BytesIO

from PIL import Image, ImageChops, ImageFilter, ImageStat, UnidentifiedImageError

from .errors import ApiError


MAX_IMAGE_BYTES = 10 * 1024 * 1024
MIN_IMAGE_SIDE = 64
ALLOWED_FORMATS = {"JPEG", "PNG", "WEBP"}
MIN_GLOBAL_CONTRAST = 3.0
MIN_STROKE_RATIO = 0.001
STROKE_DIFFERENCE_THRESHOLD = 8


@dataclass(frozen=True)
class ValidatedImage:
    image: Image.Image
    width: int
    height: int
    format: str


def ensure_formula_content(image: Image.Image) -> None:
    """Reject clearly empty/smooth crops without trying to replace either model."""
    grayscale = image.convert("L")
    grayscale.thumbnail((512, 512))
    contrast = ImageStat.Stat(grayscale).stddev[0]
    if contrast < MIN_GLOBAL_CONTRAST:
        raise ApiError(
            422,
            "NO_FORMULA_CONTENT",
            "Không phát hiện nét viết trong vùng đã cắt.",
        )

    # Handwritten strokes are high-frequency details. Broad lighting gradients and
    # shadows mostly disappear when compared with a lightly blurred copy.
    blurred = grayscale.filter(ImageFilter.GaussianBlur(radius=2.0))
    detail = ImageChops.difference(grayscale, blurred)
    histogram = detail.histogram()
    detailed_pixels = sum(histogram[STROKE_DIFFERENCE_THRESHOLD:])
    stroke_ratio = detailed_pixels / max(1, grayscale.width * grayscale.height)
    if stroke_ratio < MIN_STROKE_RATIO:
        raise ApiError(
            422,
            "NO_FORMULA_CONTENT",
            "Vùng đã cắt chủ yếu là nền hoặc bóng, chưa thấy công thức rõ ràng.",
        )


def validate_image(payload: bytes) -> ValidatedImage:
    if not payload:
        raise ApiError(400, "IMAGE_EMPTY", "Ảnh đầu vào không có dữ liệu.")
    if len(payload) > MAX_IMAGE_BYTES:
        raise ApiError(413, "IMAGE_TOO_LARGE", "Ảnh vượt quá giới hạn 10 MB.")
    try:
        source = Image.open(BytesIO(payload))
        image_format = (source.format or "").upper()
        source.verify()
        source = Image.open(BytesIO(payload))
        source.load()
    except (UnidentifiedImageError, OSError, ValueError) as error:
        raise ApiError(400, "IMAGE_DECODE_FAILED", "Không thể giải mã file ảnh đầu vào.") from error
    if image_format not in ALLOWED_FORMATS:
        raise ApiError(415, "UNSUPPORTED_IMAGE_TYPE", "Chỉ hỗ trợ JPEG, PNG và WEBP.")
    width, height = source.size
    if width < MIN_IMAGE_SIDE or height < MIN_IMAGE_SIDE:
        raise ApiError(400, "IMAGE_TOO_SMALL", "Ảnh phải có chiều rộng và chiều cao tối thiểu 64 px.")
    ensure_formula_content(source)
    return ValidatedImage(source, width, height, image_format)
