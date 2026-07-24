import csv
import json
from pathlib import Path

import numpy as np

from tamer.datamodule.university_datamodule import BalancedReplayBatchSampler
from tamer.datamodule.vocab import CROHMEVocab
from tamer.university.augmentation import DynamicPaperAugmentation
from tamer.university.latex import categorize_formula, normalize_and_tokenize
from tamer.university.metrics import compute_metrics, write_metric_report


def test_vocabulary_indices_and_round_trip_are_stable(dictionary_path: Path):
    vocabulary = CROHMEVocab()
    vocabulary.init(str(dictionary_path))

    assert vocabulary.word2idx["<pad>"] == 0
    assert vocabulary.word2idx["<sos>"] == 1
    assert vocabulary.word2idx["<eos>"] == 2
    assert vocabulary.words2indices(["x", "+", "y"]) == [3, 4, 5]
    assert vocabulary.indices2words([3, 4, 5]) == ["x", "+", "y"]


def test_latex_normalization_and_categories_are_stable(dictionary_path: Path):
    vocabulary = {
        line.strip()
        for line in dictionary_path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    }
    tokens, error = normalize_and_tokenize(r"x^2 \le y", vocabulary)

    assert error is None
    assert tokens == ["x", "^", "{", "2", "}", r"\leq", "y"]
    assert categorize_formula(r"\int x \, dx") == "integral"
    assert categorize_formula(r"\lim_{x\to0}\sin x") == "limit"


def test_metrics_and_report_files_are_stable(tmp_path: Path):
    records = [
        {
            "sample_id": "a",
            "pred_tokens": ["x", "+", "y"],
            "gt_tokens": ["x", "+", "y"],
            "category": "integral",
            "source": "university",
            "severity": "mild",
        },
        {
            "sample_id": "b",
            "pred_tokens": ["x"],
            "gt_tokens": ["x", "+", "y"],
            "category": "integral",
            "source": "university",
            "severity": "hard",
        },
    ]

    metrics = compute_metrics(records)
    assert metrics == {
        "count": 2,
        "ExpRate": 0.5,
        "ExpRate_le_1": 0.5,
        "ExpRate_le_2": 1.0,
        "TokenErrorRate": 2 / 6,
        "ValidLaTeX": 1.0,
        "total_edit_distance": 2,
        "total_gt_tokens": 6,
    }

    report = write_metric_report(records, str(tmp_path), extra={"run": "baseline"})
    assert report["run"] == "baseline"
    assert json.loads((tmp_path / "metrics.json").read_text(encoding="utf-8")) == report
    predictions = json.loads(
        (tmp_path / "predictions.json").read_text(encoding="utf-8")
    )
    assert predictions[0]["pred"] == "x + y"
    assert predictions[1]["edit_distance"] == 2
    with (tmp_path / "category_metrics.csv").open(
        encoding="utf-8", newline=""
    ) as stream:
        assert list(csv.DictReader(stream))[0]["category"] == "integral"


def test_dynamic_augmentation_is_repeatable_for_a_fixed_seed():
    image = np.full((32, 64), 255, dtype=np.uint8)
    augmenter = DynamicPaperAugmentation(background_dir=None)

    first = augmenter(image.copy(), seed=1234)
    second = augmenter(image.copy(), seed=1234)

    assert np.array_equal(first, second)
    assert first.shape == (60, 112)
    assert first.dtype == image.dtype


def test_balanced_replay_sampler_is_deterministic_per_new_instance():
    options = dict(
        university_size=10,
        replay_size=8,
        batch_size=4,
        replay_ratio=0.5,
        seed=7,
    )
    first = list(BalancedReplayBatchSampler(**options))
    second = list(BalancedReplayBatchSampler(**options))

    assert first == second
    assert all(len(batch) == 4 for batch in first)
    assert all(
        sum(index >= 10 for index, _epoch in batch) == 2 for batch in first
    )
