import json
import zipfile

import torch

import tamer.lit_tamer as lit_tamer_module
from tamer.lit_tamer import LitTAMER


def _small_model() -> LitTAMER:
    return LitTAMER(
        d_model=8,
        growth_rate=2,
        num_layers=1,
        nhead=1,
        num_decoder_layers=1,
        dim_feedforward=16,
        dropout=0.0,
        dc=4,
        cross_coverage=False,
        self_coverage=False,
        beam_size=1,
        max_len=4,
        alpha=1.0,
        early_stopping=True,
        temperature=1.0,
        learning_rate=1.0,
        patience=1,
        vocab_size=16,
    )


def test_optimizer_scheduler_and_default_milestones_are_stable():
    model = _small_model()
    configured = model.configure_optimizers()
    optimizer = configured["optimizer"]
    scheduler = configured["lr_scheduler"]

    assert model.hparams.milestones == [40, 55]
    assert isinstance(optimizer, torch.optim.Adadelta)
    assert optimizer.defaults["eps"] == 1e-6
    assert optimizer.defaults["weight_decay"] == 1e-4
    assert isinstance(scheduler, torch.optim.lr_scheduler.MultiStepLR)
    assert dict(scheduler.milestones) == {40: 1, 55: 1}


def test_crohme_report_files_preserve_exact_payloads(tmp_path):
    assert hasattr(lit_tamer_module, "write_crohme_test_outputs")
    outputs = [
        (
            ["a", "b"],
            [["x", "+", "y"], ["x"]],
            [["x", "+", "y"], ["x", "+", "y"]],
        )
    ]

    lit_tamer_module.write_crohme_test_outputs(outputs, tmp_path)

    with zipfile.ZipFile(tmp_path / "result.zip") as archive:
        assert archive.namelist() == ["a.txt", "b.txt"]
        assert archive.read("a.txt") == b"%a\n$['x', '+', 'y']$"
        assert archive.read("b.txt") == b"%b\n$['x']$"
    errors = json.loads(
        (tmp_path / "errors.json").read_text(encoding="utf-8")
    )
    predictions = json.loads(
        (tmp_path / "predictions.json").read_text(encoding="utf-8")
    )
    assert errors == {
        "b": {"pred": "x", "gt": "x + y", "dist": 2}
    }
    assert predictions == {
        "a": {"pred": "x + y", "gt": "x + y", "dist": 0},
        "b": {"pred": "x", "gt": "x + y", "dist": 2},
    }
