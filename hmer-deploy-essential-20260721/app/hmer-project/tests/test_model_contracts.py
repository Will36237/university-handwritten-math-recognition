import json
import os
from pathlib import Path

import pytest
import torch

from tamer.datamodule import vocab
from tamer.lit_university import LitUniversityTAMER


TEST_ROOT = Path(__file__).resolve().parent


def _required_path(name: str) -> Path:
    value = os.environ.get(name)
    if not value:
        pytest.skip(f"{name} is not configured")
    path = Path(value)
    if not path.exists():
        pytest.fail(f"{name} does not exist: {path}")
    return path


def _load_checkpoint() -> LitUniversityTAMER:
    checkpoint = _required_path("HMER_TAMER_CHECKPOINT")
    dictionary = _required_path("HMER_TAMER_DICTIONARY")
    vocab.init(str(dictionary))
    model = LitUniversityTAMER.load_from_checkpoint(
        str(checkpoint),
        prediction_output_dir="outputs/test_predictions",
        strict=True,
        use_fusion=True,
        map_location=torch.device("cuda"),
    )
    model.cuda().eval()
    return model


def test_checkpoint_state_keys_and_shapes_match_manifest():
    model = _load_checkpoint()
    expected = json.loads(
        (TEST_ROOT / "state_manifest.json").read_text(encoding="utf-8")
    )
    actual = {
        name: list(tensor.shape)
        for name, tensor in sorted(model.state_dict().items())
    }
    assert actual == expected
