from pathlib import Path
import sys

import pytest


PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


@pytest.fixture
def dictionary_path(tmp_path: Path) -> Path:
    path = tmp_path / "dictionary.txt"
    path.write_text("x\n+\ny\n^\n{\n2\n}\n\\leq\n", encoding="utf-8")
    return path
