import os
from pathlib import Path

from shared.settings import UniMumerSettings
from shared.worker_app import WorkerSpec, create_worker_app

from .adapter import UniMumerLoraAdapter


WORKSPACE_ROOT = Path(__file__).resolve().parents[5]
settings = UniMumerSettings.from_env(os.environ, WORKSPACE_ROOT)
adapter = UniMumerLoraAdapter(
    base_model=settings.base_model,
    base_model_revision=settings.base_model_revision,
    adapter_path=settings.adapter_path,
)
spec = WorkerSpec(
    title="Uni-MuMER LoRA Worker",
    version="0.1.0",
    model="unimumer_lora",
    mode=settings.mode,
    eager_load=settings.eager_load,
    mock_delay_seconds=0.75,
    mock_latex=r"\int_{0}^{1} x^{2}\,\mathrm{d}x = \frac{1}{3}",
    empty_output_message="Uni-MuMER LoRA không sinh được LaTeX.",
    unsupported_mode_label="Uni-MuMER",
)
app = create_worker_app(spec, adapter)
