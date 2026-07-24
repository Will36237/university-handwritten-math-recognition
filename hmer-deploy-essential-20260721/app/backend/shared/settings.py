import os
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, cast

from .contracts import ModelName


Environment = Mapping[str, str]


def _environment(environ: Environment | None) -> Environment:
    return os.environ if environ is None else environ


def _mode_and_eager_load(
    environ: Environment,
    mode_name: str,
    eager_name: str,
) -> tuple[str, bool]:
    mode = environ.get(mode_name, "mock").lower()
    eager_default = "true" if mode == "real" else "false"
    eager_load = environ.get(eager_name, eager_default).lower() == "true"
    return mode, eager_load


@dataclass(frozen=True)
class GatewaySettings:
    workers: dict[ModelName, str]
    connect_timeout_seconds: float
    predict_timeout_seconds: float

    @classmethod
    def from_env(cls, environ: Environment | None = None) -> "GatewaySettings":
        source = _environment(environ)
        workers = {
            cast(ModelName, "tamer_a3"): source.get(
                "HMER_TAMER_WORKER_URL",
                "http://127.0.0.1:8101",
            ).rstrip("/"),
            cast(ModelName, "unimumer_lora"): source.get(
                "HMER_UNIMUMER_WORKER_URL",
                "http://127.0.0.1:8102",
            ).rstrip("/"),
        }
        return cls(
            workers=workers,
            connect_timeout_seconds=float(
                source.get("HMER_CONNECT_TIMEOUT_SECONDS", "5"),
            ),
            predict_timeout_seconds=float(
                source.get("HMER_PREDICT_TIMEOUT_SECONDS", "120"),
            ),
        )


@dataclass(frozen=True)
class TamerSettings:
    project_root: Path
    checkpoint: Path
    dictionary: Path
    mode: str
    eager_load: bool

    @classmethod
    def from_env(
        cls,
        environ: Environment | None,
        workspace_root: Path,
    ) -> "TamerSettings":
        source = _environment(environ)
        project_root = Path(
            source.get(
                "HMER_TAMER_PROJECT_ROOT",
                str(workspace_root / "University-TAMER-RTX3090-A0123-trained"),
            ),
        )
        mode, eager_load = _mode_and_eager_load(
            source,
            "HMER_TAMER_MODE",
            "HMER_TAMER_EAGER_LOAD",
        )
        return cls(
            project_root=project_root,
            checkpoint=Path(
                source.get(
                    "HMER_TAMER_CHECKPOINT",
                    str(
                        project_root
                        / "outputs"
                        / "real_ft_a3_dual_seed7"
                        / "checkpoints"
                        / "epoch=56-val_university_ExpRate=0.5637.ckpt"
                    ),
                ),
            ),
            dictionary=Path(
                source.get(
                    "HMER_TAMER_DICTIONARY",
                    str(project_root / "data" / "HME100k" / "dictionary.txt"),
                ),
            ),
            mode=mode,
            eager_load=eager_load,
        )


@dataclass(frozen=True)
class UniMumerSettings:
    project_root: Path
    base_model: str
    base_model_revision: str
    adapter_path: Path
    mode: str
    eager_load: bool

    @classmethod
    def from_env(
        cls,
        environ: Environment | None,
        workspace_root: Path,
    ) -> "UniMumerSettings":
        source = _environment(environ)
        project_root = Path(
            source.get(
                "HMER_UNIMUMER_PROJECT_ROOT",
                str(workspace_root / "University-TAMER-RTX3090-A0123-trained"),
            ),
        )
        mode, eager_load = _mode_and_eager_load(
            source,
            "HMER_UNIMUMER_MODE",
            "HMER_UNIMUMER_EAGER_LOAD",
        )
        return cls(
            project_root=project_root,
            base_model=source.get(
                "HMER_UNIMUMER_BASE_MODEL",
                "phxember/Uni-MuMER-Qwen3.5-2B",
            ),
            base_model_revision=source.get(
                "HMER_UNIMUMER_BASE_MODEL_REVISION",
                "40a6288292057f1c162b3b0eaccd362036dbd495",
            ),
            adapter_path=Path(
                source.get(
                    "HMER_UNIMUMER_ADAPTER",
                    str(
                        project_root
                        / "outputs"
                        / "unimumer_lora_unsloth_real"
                        / "best_adapter"
                    ),
                ),
            ),
            mode=mode,
            eager_load=eager_load,
        )
