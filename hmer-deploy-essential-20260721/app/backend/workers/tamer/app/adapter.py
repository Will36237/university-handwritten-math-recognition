"""Lazy TAMER-A3 RealFT inference adapter.

Heavy TAMER dependencies are imported only when real inference is enabled, so the
mock API remains runnable on a CPU development machine.
"""

from pathlib import Path
import sys
from threading import Lock
from typing import Any

from PIL import Image


class TamerA3Adapter:
    def __init__(self, project_root: Path, checkpoint: Path, dictionary: Path) -> None:
        self.project_root = project_root.resolve()
        self.checkpoint = checkpoint.resolve()
        self.dictionary = dictionary.resolve()
        self._model: Any = None
        self._torch: Any = None
        self._vocab: Any = None
        self._lock = Lock()
        self.device = "unloaded"

    @property
    def loaded(self) -> bool:
        return self._model is not None

    def load(self) -> None:
        if self.loaded:
            return
        with self._lock:
            if self.loaded:
                return
            for path, label in (
                (self.project_root, "TAMER project root"),
                (self.checkpoint, "TAMER checkpoint"),
                (self.dictionary, "TAMER dictionary"),
            ):
                if not path.exists():
                    raise RuntimeError(f"{label} not found: {path}")
            if str(self.project_root) not in sys.path:
                sys.path.insert(0, str(self.project_root))

            try:
                import torch
                from tamer.datamodule import vocab
                from tamer.lit_university import LitUniversityTAMER
            except ImportError as error:
                raise RuntimeError(
                    "TAMER runtime is missing. Install the RTX3090 requirements before enabling real mode."
                ) from error

            vocab.init(str(self.dictionary))
            device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
            model = LitUniversityTAMER.load_from_checkpoint(
                str(self.checkpoint),
                prediction_output_dir="outputs/api_predictions",
                strict=True,
                use_fusion=True,
                map_location=device,
            )
            model.to(device)
            model.eval()
            self._torch = torch
            self._vocab = vocab
            self._model = model
            self.device = str(device)

    def predict(self, image: Image.Image) -> str:
        self.load()
        torch = self._torch
        grayscale = image.convert("L")
        array = torch.as_tensor(list(grayscale.getdata()), dtype=torch.float32)
        tensor = array.reshape(grayscale.height, grayscale.width).div_(255.0)
        tensor = tensor.unsqueeze(0).unsqueeze(0).to(self._model.device)
        mask = torch.zeros(
            (1, grayscale.height, grayscale.width),
            dtype=torch.bool,
            device=self._model.device,
        )
        with self._lock, torch.inference_mode():
            hypotheses = self._model.approximate_joint_search(tensor, mask)
        if not hypotheses:
            return ""
        return " ".join(self._vocab.indices2words(hypotheses[0].seq)).strip()
