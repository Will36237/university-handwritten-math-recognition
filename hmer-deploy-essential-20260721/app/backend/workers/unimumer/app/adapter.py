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
    "You classify images for a mathematical-expression recognition system. "
    "Decide whether the main subject contains a visible mathematical expression. "
    "Accept handwritten math, printed math, and math displayed on a screen. "
    "Reject ordinary prose or handwriting, animals, objects, people, scenery, "
    "and screenshots without mathematical notation. Use UNCERTAIN when the image "
    "is too ambiguous or illegible. Reply with exactly one label: MATH, "
    "NON_MATH, or UNCERTAIN. Do not explain."
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
        classifier_model: str,
        classifier_model_revision: str,
        adapter_path: Path,
    ) -> None:
        self.base_model = base_model
        self.base_model_revision = base_model_revision
        self.classifier_model = classifier_model
        self.classifier_model_revision = classifier_model_revision
        self.adapter_path = adapter_path.resolve()
        self._processor: Any = None
        self._model: Any = None
        self._classifier_processor: Any = None
        self._classifier_model: Any = None
        self._torch: Any = None
        self._lock = Lock()
        self.device = "unloaded"

    @property
    def loaded(self) -> bool:
        return self._model is not None and self._classifier_model is not None

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

            classifier_processor = AutoProcessor.from_pretrained(
                self.classifier_model,
                revision=self.classifier_model_revision,
                trust_remote_code=True,
            )
            classifier_model = AutoModelForMultimodalLM.from_pretrained(
                self.classifier_model,
                device_map="auto" if torch.cuda.is_available() else None,
                revision=self.classifier_model_revision,
                trust_remote_code=True,
                torch_dtype=dtype,
            )
            classifier_model.eval()

            self._torch = torch
            self._processor = processor
            self._model = model
            self._classifier_processor = classifier_processor
            self._classifier_model = classifier_model
            self.device = str(next(model.parameters()).device)

    def predict(self, image: Image.Image) -> str:
        self.load()
        torch = self._torch
        rgb = image.convert("RGB")
        with self._lock, torch.inference_mode():
            try:
                classification = self._generate_classifier(
                    self._classifier_processor,
                    self._classifier_model,
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
                self._processor,
                self._model,
                LATEX_RECOGNITION_PROMPT,
                rgb,
                max_new_tokens=256,
            )

    def _generate_classifier(
        self,
        processor: Any,
        model: Any,
        image: Image.Image,
        *,
        max_new_tokens: int,
    ) -> str:
        messages = [
            {
                "role": "user",
                "content": [
                    {"type": "image", "image": image},
                    {"type": "text", "text": MATH_IMAGE_CLASSIFICATION_PROMPT},
                ],
            },
        ]
        inputs = processor.apply_chat_template(
            messages,
            add_generation_prompt=True,
            tokenize=True,
            return_dict=True,
            return_tensors="pt",
        ).to(next(model.parameters()).device)
        return self._decode_generation(
            processor,
            model,
            inputs,
            max_new_tokens=max_new_tokens,
        )

    def _generate(
        self,
        processor: Any,
        model: Any,
        prompt: str,
        image: Image.Image,
        *,
        max_new_tokens: int,
    ) -> str:
        inputs = processor(
            text=[prompt],
            images=[image],
            padding=True,
            return_tensors="pt",
        ).to(next(model.parameters()).device)
        return self._decode_generation(
            processor,
            model,
            inputs,
            max_new_tokens=max_new_tokens,
        )

    @staticmethod
    def _decode_generation(
        processor: Any,
        model: Any,
        inputs: Any,
        *,
        max_new_tokens: int,
    ) -> str:
        outputs = model.generate(
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
        return processor.batch_decode(
            generated_ids,
            skip_special_tokens=True,
            clean_up_tokenization_spaces=False,
        )[0].strip()
