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
CLASSIFIER_REVISION = "15852e8c16360a2fea060d615a32b45270f8a8fc"


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
        self.chat_messages: list[list[dict]] = []

    def __call__(self, *, text, images, padding, return_tensors):
        self.prompts.append(text[0])
        return FakeInputs()

    def apply_chat_template(self, messages, **options):
        self.chat_messages.append(messages)
        return FakeInputs()

    def batch_decode(self, generated_ids, **options):
        return [next(self.outputs)]


class FakeGenerationModel:
    def __init__(self) -> None:
        self.generate_calls = 0
        self.error: RuntimeError | None = None

    def parameters(self):
        return iter((SimpleNamespace(device="cpu"),))

    def generate(self, **inputs):
        self.generate_calls += 1
        if self.error is not None:
            raise self.error
        return [inputs["input_ids"][0] + [201]]


class FakeTorch:
    @staticmethod
    @contextmanager
    def inference_mode():
        yield


def make_ready_adapter(
    tmp_path,
    classifier_output: str,
    recognition_output: str | None = None,
):
    adapter_path = tmp_path / "adapter"
    adapter_path.mkdir()
    adapter = UniMumerLoraAdapter(
        base_model="trusted/model",
        base_model_revision=MODEL_REVISION,
        classifier_model="trusted/classifier",
        classifier_model_revision=CLASSIFIER_REVISION,
        adapter_path=adapter_path,
    )
    classifier_processor = FakeProcessor([classifier_output])
    classifier_model = FakeGenerationModel()
    recognition_processor = FakeProcessor(
        [] if recognition_output is None else [recognition_output],
    )
    recognition_model = FakeGenerationModel()
    adapter._classifier_processor = classifier_processor
    adapter._classifier_model = classifier_model
    adapter._processor = recognition_processor
    adapter._model = recognition_model
    adapter._torch = FakeTorch()
    adapter.device = "cpu"
    return (
        adapter,
        classifier_processor,
        classifier_model,
        recognition_processor,
        recognition_model,
    )


def test_predict_uses_official_classifier_then_runs_lora_ocr(tmp_path) -> None:
    (
        adapter,
        classifier_processor,
        classifier_model,
        recognition_processor,
        recognition_model,
    ) = make_ready_adapter(tmp_path, "MATH", r"x^2")

    result = adapter.predict(Image.new("RGB", (128, 64), "white"))

    assert result == r"x^2"
    assert classifier_processor.prompts == []
    assert len(classifier_processor.chat_messages) == 1
    classifier_content = classifier_processor.chat_messages[0][0]["content"]
    assert classifier_content[0]["type"] == "image"
    assert classifier_content[1] == {
        "type": "text",
        "text": MATH_IMAGE_CLASSIFICATION_PROMPT,
    }
    assert recognition_processor.prompts == [LATEX_RECOGNITION_PROMPT]
    assert classifier_model.generate_calls == 1
    assert recognition_model.generate_calls == 1


@pytest.mark.parametrize("decision", ["NON_MATH", "UNCERTAIN"])
def test_predict_rejects_non_math_before_lora_ocr(tmp_path, decision: str) -> None:
    (
        adapter,
        classifier_processor,
        classifier_model,
        recognition_processor,
        recognition_model,
    ) = make_ready_adapter(tmp_path, decision)

    with pytest.raises(ApiError) as captured:
        adapter.predict(Image.new("RGB", (128, 64), "white"))

    assert captured.value.status_code == 422
    assert captured.value.code == "NON_MATH_IMAGE"
    assert classifier_processor.prompts == []
    assert len(classifier_processor.chat_messages) == 1
    assert recognition_processor.prompts == []
    assert classifier_model.generate_calls == 1
    assert recognition_model.generate_calls == 0


def test_predict_rejects_malformed_classifier_output(tmp_path) -> None:
    (
        adapter,
        classifier_processor,
        classifier_model,
        recognition_processor,
        recognition_model,
    ) = make_ready_adapter(tmp_path, "This looks like math.")

    with pytest.raises(ApiError) as captured:
        adapter.predict(Image.new("RGB", (128, 64), "white"))

    assert captured.value.status_code == 503
    assert captured.value.code == "IMAGE_CLASSIFIER_UNAVAILABLE"
    assert classifier_processor.prompts == []
    assert len(classifier_processor.chat_messages) == 1
    assert recognition_processor.prompts == []
    assert classifier_model.generate_calls == 1
    assert recognition_model.generate_calls == 0


def test_predict_maps_classifier_runtime_failure_to_service_unavailable(tmp_path) -> None:
    (
        adapter,
        _,
        classifier_model,
        recognition_processor,
        recognition_model,
    ) = make_ready_adapter(tmp_path, "MATH")
    classifier_model.error = RuntimeError("out of memory")

    with pytest.raises(ApiError) as captured:
        adapter.predict(Image.new("RGB", (128, 64), "white"))

    assert captured.value.status_code == 503
    assert captured.value.code == "IMAGE_CLASSIFIER_UNAVAILABLE"
    assert recognition_processor.prompts == []
    assert recognition_model.generate_calls == 0


def test_load_pins_remote_code_to_configured_model_revision(
    tmp_path,
    monkeypatch,
) -> None:
    calls = {"processors": [], "models": [], "evaluated": []}

    class FakeAutoProcessor:
        @staticmethod
        def from_pretrained(model, **options):
            calls["processors"].append((model, options))
            return object()

    class FakeModel:
        def __init__(self, name: str) -> None:
            self.name = name

        def parameters(self):
            return iter((SimpleNamespace(device="cpu"),))

        def eval(self):
            calls["evaluated"].append(self.name)

    class FakeAutoModel:
        @staticmethod
        def from_pretrained(model, **options):
            calls["models"].append((model, options))
            return FakeModel(model)

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
        classifier_model="trusted/classifier",
        classifier_model_revision=CLASSIFIER_REVISION,
        adapter_path=adapter_path,
    )

    adapter.load()

    assert calls["processors"] == [
        (
            "trusted/model",
            {"revision": MODEL_REVISION, "trust_remote_code": True},
        ),
        (
            "trusted/classifier",
            {"revision": CLASSIFIER_REVISION, "trust_remote_code": True},
        ),
    ]
    assert [call[0] for call in calls["models"]] == [
        "trusted/model",
        "trusted/classifier",
    ]
    assert calls["models"][0][1]["revision"] == MODEL_REVISION
    assert calls["models"][1][1]["revision"] == CLASSIFIER_REVISION
    assert all(call[1]["trust_remote_code"] is True for call in calls["models"])
    assert calls["evaluated"] == ["trusted/model", "trusted/classifier"]
