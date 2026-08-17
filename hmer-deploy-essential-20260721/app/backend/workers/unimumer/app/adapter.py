"""Lazy Uni-MuMER LoRA adapter matching the recorded blind-test configuration."""

from enum import Enum
from pathlib import Path
from threading import Lock
from typing import Any

from PIL import Image

from shared.errors import ApiError


LATEX_RECOGNITION_PROMPT = (
    "<|im_start|>system\nYou are a helpful assistant.<|im_end|>\n"
    "<|im_start|>user\n<|vision_start|><|image_pad|><|vision_end|>"
    "Convert the mathematical formula in this image to LaTeX format."
    "<|im_end|>\n<|im_start|>assistant\n"
)

MATH_IMAGE_CLASSIFICATION_PROMPT = (
    "<|im_start|>system\n"
    "You classify images for a mathematical-expression recognition system."
    "<|im_end|>\n"
    "<|im_start|>user\n<|vision_start|><|image_pad|><|vision_end|>"
    "Decide whether the main subject contains a visible mathematical expression. "
    "Accept handwritten math, printed math, and math displayed on a screen. "
    "Reject ordinary prose or handwriting, animals, objects, people, scenery, "
    "and screenshots without mathematical notation. Use UNCERTAIN when the image "
    "is too ambiguous or illegible. Reply with exactly one label: MATH, "
    "NON_MATH, or UNCERTAIN. Do not explain."
    "<|im_end|>\n<|im_start|>assistant\n"
)


class MathImageDecision(str, Enum):
    MATH = "MATH"
    NON_MATH = "NON_MATH"
    UNCERTAIN = "UNCERTAIN"


def parse_math_image_decision(output: str) -> MathImageDecision:
    try:
        return MathImageDecision(output.strip().upper())
    except ValueError as error:
        raise ApiError(
            503,
            "IMAGE_CLASSIFIER_UNAVAILABLE",
            "Bộ kiểm tra ảnh toán trả về dữ liệu không hợp lệ.",
        ) from error


class UniMumerLoraAdapter:
    def __init__(
        self,
        base_model: str,
        base_model_revision: str,
        adapter_path: Path,
    ) -> None:
        self.base_model = base_model
        self.base_model_revision = base_model_revision
        self.adapter_path = adapter_path.resolve()
        self._processor: Any = None
        self._model: Any = None
        self._torch: Any = None
        self._lock = Lock()
        self.device = "unloaded"

    @property
    def loaded(self) -> bool:
        return self._model is not None

    def load(self) -> None:
        if self.loaded:
            return
        with self._lock:
            if self.loaded:
                return
            if not self.adapter_path.exists():
                raise RuntimeError(f"Uni-MuMER LoRA adapter not found: {self.adapter_path}")
            try:
                import torch
                from peft import PeftModel
                from transformers import AutoModelForMultimodalLM, AutoProcessor
            except ImportError as error:
                raise RuntimeError(
                    "Uni-MuMER runtime is missing. Install Transformers, PEFT and Accelerate first."
                ) from error

            dtype = torch.bfloat16 if torch.cuda.is_available() else torch.float32
            processor = AutoProcessor.from_pretrained(
                self.base_model,
                revision=self.base_model_revision,
                trust_remote_code=True,
            )
            model = AutoModelForMultimodalLM.from_pretrained(
                self.base_model,
                device_map="auto" if torch.cuda.is_available() else None,
                revision=self.base_model_revision,
                trust_remote_code=True,
                torch_dtype=dtype,
            )
            model = PeftModel.from_pretrained(model, str(self.adapter_path))
            model.eval()
            self._torch = torch
            self._processor = processor
            self._model = model
            self.device = str(next(model.parameters()).device)

    def predict(self, image: Image.Image) -> str:
        self.load()
        torch = self._torch
        rgb = image.convert("RGB")
        with self._lock, torch.inference_mode():
            try:
                with self._model.disable_adapter():
                    classification = self._generate(
                        MATH_IMAGE_CLASSIFICATION_PROMPT,
                        rgb,
                        max_new_tokens=8,
                    )
            except RuntimeError as error:
                raise ApiError(
                    503,
                    "IMAGE_CLASSIFIER_UNAVAILABLE",
                    "Bộ kiểm tra ảnh toán hiện không khả dụng.",
                ) from error

            decision = parse_math_image_decision(classification)
            if decision is not MathImageDecision.MATH:
                raise ApiError(
                    422,
                    "NON_MATH_IMAGE",
                    "Ảnh không chứa công thức toán đủ rõ để nhận dạng.",
                )

            return self._generate(
                LATEX_RECOGNITION_PROMPT,
                rgb,
                max_new_tokens=256,
            )

    def _generate(
        self,
        prompt: str,
        image: Image.Image,
        *,
        max_new_tokens: int,
    ) -> str:
        inputs = self._processor(
            text=[prompt],
            images=[image],
            padding=True,
            return_tensors="pt",
        ).to(next(self._model.parameters()).device)
        outputs = self._model.generate(
            **inputs,
            max_new_tokens=max_new_tokens,
            do_sample=False,
            num_beams=1,
            repetition_penalty=1.05,
        )
        generated_ids = [
            output_ids[len(input_ids):]
            for input_ids, output_ids in zip(inputs.input_ids, outputs)
        ]
        return self._processor.batch_decode(
            generated_ids,
            skip_special_tokens=True,
            clean_up_tokenization_spaces=False,
        )[0].strip()
