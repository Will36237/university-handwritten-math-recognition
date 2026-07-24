import os
from pathlib import Path

from shared.settings import TamerSettings
from shared.worker_app import WorkerSpec, create_worker_app

from .adapter import TamerA3Adapter


WORKSPACE_ROOT = Path(__file__).resolve().parents[5]
settings = TamerSettings.from_env(os.environ, WORKSPACE_ROOT)
adapter = TamerA3Adapter(
    settings.project_root,
    settings.checkpoint,
    settings.dictionary,
)
spec = WorkerSpec(
    title="TAMER-A3 Worker",
    version="0.1.0",
    model="tamer_a3",
    mode=settings.mode,
    eager_load=settings.eager_load,
    mock_delay_seconds=0.30,
    mock_latex=r"\int_{0}^{1} x^{2}\,dx = \frac{1}{3}",
    empty_output_message="TAMER-A3 không sinh được LaTeX.",
    unsupported_mode_label="TAMER",
)
app = create_worker_app(spec, adapter)
