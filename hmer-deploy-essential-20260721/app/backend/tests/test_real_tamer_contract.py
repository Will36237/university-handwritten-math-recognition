import os
from pathlib import Path

import pytest
from PIL import Image

from workers.tamer.app.adapter import TamerA3Adapter


EXPECTED_LATEX = (
    r"\cos y + \sqrt { t ^ { 2 } + 7 } + u \cdot m ^ { 2 } "
    r"+ \log ( v + 2 ) + n ^ { 5 } + \log k "
    r"+ \frac { m ^ { 4 } } { m + 5 }"
)


def _required_path(name: str) -> Path:
    value = os.environ.get(name)
    if not value:
        pytest.skip(f"{name} is not configured")
    return Path(value)


def test_real_tamer_checkpoint_preserves_exact_fixture_output():
    adapter = TamerA3Adapter(
        _required_path("HMER_TAMER_PROJECT_ROOT"),
        _required_path("HMER_TAMER_CHECKPOINT"),
        _required_path("HMER_TAMER_DICTIONARY"),
    )
    image = Image.open(_required_path("HMER_TAMER_FIXTURE"))

    assert adapter.predict(image) == EXPECTED_LATEX
    assert adapter.device == "cuda"
