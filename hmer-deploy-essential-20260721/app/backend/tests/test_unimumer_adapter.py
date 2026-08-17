from contextlib import contextmanager
import sys
from types import ModuleType, SimpleNamespace

import pytest
from PIL import Image

from shared.errors import ApiError
from workers.unimumer.app.adapter import (
    LATEX_RECOGNITION_PROMPT,
    MATH_IMAGE_CLASSIFICATION_PROMPT,
    MathImageDecision,
    UniMumerLoraAdapter,
    parse_math_image_decision,
)


MODEL_REVISION = "40a6288292057f1c162b3b0eaccd362036dbd495"


@pytest.mark.parametrize(
    ("output", "expected"),
    [
        ("MATH", MathImageDecision.MATH),
        (" non_math\n", MathImageDecision.NON_MATH),
        ("uncertain", MathImageDecision.UNCERTAIN),
    ],
)
def test_parse_math_image_decision_accepts_only_contract_labels(
    output: str,
    expected: MathImageDecision,
) -> None:
    assert parse_math_image_decision(output) is expected


@pytest.mark.parametrize("output", ["", "MATH because it is an equation", "TEXT"])
def test_parse_math_image_decision_rejects_malformed_output(output: str) -> None:
    with pytest.raises(ApiError) as captured:
        parse_math_image_decision(output)

    assert captured.value.status_code == 503
    assert captured.value.code == "IMAGE_CLASSIFIER_UNAVAILABLE"


class FakeInputs(dict):
    def __init__(self) -> None:
        input_ids = [[101, 102]]
        super().__init__(input_ids=input_ids)
        self.input_ids = input_ids

    def to(self, device):
        return self


class FakeProcessor:
    def __init__(self, outputs: list[str]) -> None:
        self.outputs = iter(outputs)
        self.prompts: list[str] = []

    def __call__(self, *, text, images, padding, return_tensors):
        self.prompts.append(text[0])
        return FakeInputs()

    def batch_decode(self, generated_ids, **options):
        return [next(self.outputs)]


class FakeGenerationModel:
    def __init__(self) -> None:
        self.adapter_disabled = False
        self.generate_adapter_states: list[bool] = []

    def parameters(self):
        return iter((SimpleNamespace(device="cpu"),))

    @contextmanager
    def disable_adapter(self):
        self.adapter_disabled = True
        try:
            yield
        finally:
            self.adapter_disabled = False

    def generate(self, **inputs):
        self.generate_adapter_states.append(self.adapter_disabled)
        return [inputs["input_ids"][0] + [201]]


class FakeTorch:
    @staticmethod
    @contextmanager
    def inference_mode():
        yield


def make_ready_adapter(tmp_path, outputs: list[str]):
    adapter_path = tmp_path / "adapter"
    adapter_path.mkdir()
    adapter = UniMumerLoraAdapter(
        base_model="trusted/model",
        base_model_revision=MODEL_REVISION,
        adapter_path=adapter_path,
    )
    processor = FakeProcessor(outputs)
    model = FakeGenerationModel()
    adapter._processor = processor
    adapter._model = model
    adapter._torch = FakeTorch()
    adapter.device = "cpu"
    return adapter, processor, model


def test_predict_classifies_with_base_model_then_runs_lora_ocr(tmp_path) -> None:
    adapter, processor, model = make_ready_adapter(tmp_path, ["MATH", r"x^2"])

    result = adapter.predict(Image.new("RGB", (128, 64), "white"))

    assert result == r"x^2"
    assert processor.prompts == [
        MATH_IMAGE_CLASSIFICATION_PROMPT,
        LATEX_RECOGNITION_PROMPT,
    ]
    assert model.generate_adapter_states == [True, False]


@pytest.mark.parametrize("decision", ["NON_MATH", "UNCERTAIN"])
def test_predict_rejects_non_math_before_lora_ocr(tmp_path, decision: str) -> None:
    adapter, processor, model = make_ready_adapter(tmp_path, [decision])

    with pytest.raises(ApiError) as captured:
        adapter.predict(Image.new("RGB", (128, 64), "white"))

    assert captured.value.status_code == 422
    assert captured.value.code == "NON_MATH_IMAGE"
    assert processor.prompts == [MATH_IMAGE_CLASSIFICATION_PROMPT]
    assert model.generate_adapter_states == [True]


def test_predict_rejects_malformed_classifier_output(tmp_path) -> None:
    adapter, processor, model = make_ready_adapter(tmp_path, ["This looks like math."])

    with pytest.raises(ApiError) as captured:
        adapter.predict(Image.new("RGB", (128, 64), "white"))

    assert captured.value.status_code == 503
    assert captured.value.code == "IMAGE_CLASSIFIER_UNAVAILABLE"
    assert processor.prompts == [MATH_IMAGE_CLASSIFICATION_PROMPT]
    assert model.generate_adapter_states == [True]


def test_load_pins_remote_code_to_configured_model_revision(
    tmp_path,
    monkeypatch,
) -> None:
    calls = {}

    class FakeAutoProcessor:
        @staticmethod
        def from_pretrained(model, **options):
            calls["processor"] = (model, options)
            return object()

    class FakeModel:
        def parameters(self):
            return iter((SimpleNamespace(device="cpu"),))

        def eval(self):
            calls["evaluated"] = True

    class FakeAutoModel:
        @staticmethod
        def from_pretrained(model, **options):
            calls["model"] = (model, options)
            return FakeModel()

    class FakePeftModel:
        @staticmethod
        def from_pretrained(model, adapter_path):
            calls["adapter_path"] = adapter_path
            return model

    torch = ModuleType("torch")
    torch.cuda = SimpleNamespace(is_available=lambda: False)
    torch.bfloat16 = "bfloat16"
    torch.float32 = "float32"

    transformers = ModuleType("transformers")
    transformers.AutoProcessor = FakeAutoProcessor
    transformers.AutoModelForMultimodalLM = FakeAutoModel

    peft = ModuleType("peft")
    peft.PeftModel = FakePeftModel

    monkeypatch.setitem(sys.modules, "torch", torch)
    monkeypatch.setitem(sys.modules, "transformers", transformers)
    monkeypatch.setitem(sys.modules, "peft", peft)

    adapter_path = tmp_path / "adapter"
    adapter_path.mkdir()
    adapter = UniMumerLoraAdapter(
        base_model="trusted/model",
        base_model_revision=MODEL_REVISION,
        adapter_path=adapter_path,
    )

    adapter.load()

    assert calls["processor"] == (
        "trusted/model",
        {
            "revision": MODEL_REVISION,
            "trust_remote_code": True,
        },
    )
    assert calls["model"][0] == "trusted/model"
    assert calls["model"][1]["revision"] == MODEL_REVISION
    assert calls["model"][1]["trust_remote_code"] is True
    assert calls["evaluated"] is True
