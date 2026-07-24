import sys
from types import ModuleType, SimpleNamespace

from workers.unimumer.app.adapter import UniMumerLoraAdapter


MODEL_REVISION = "40a6288292057f1c162b3b0eaccd362036dbd495"


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
