import torch

from tamer.datamodule.datamodule import collate_fn, pad_images
from tamer.datamodule.university_datamodule import collate_formula_samples
from tamer.datamodule.vocab import vocab


def _initialize_vocabulary(dictionary_path):
    vocab.init(str(dictionary_path))


def test_pad_images_returns_exact_zero_padding_and_true_padding_mask():
    images = [
        torch.tensor([[[1.0, 2.0], [3.0, 4.0]]]),
        torch.tensor([[[5.0, 6.0, 7.0]]]),
    ]

    padded, mask = pad_images(images)

    assert padded.shape == (2, 1, 2, 3)
    assert padded[0, 0, 1].tolist() == [3.0, 4.0, 0.0]
    assert mask.dtype == torch.bool
    assert mask[0].tolist() == [[False, False, True], [False, False, True]]
    assert mask[1].tolist() == [[False, False, False], [True, True, True]]


def test_legacy_collate_padding_and_mask_are_stable(dictionary_path):
    _initialize_vocabulary(dictionary_path)
    first = torch.tensor([[[1.0, 2.0], [3.0, 4.0]]])
    second = torch.tensor([[[5.0, 6.0, 7.0]]])
    batch = collate_fn(
        [(["a", "b"], [first, second], [["x"], ["y"]])]
    )

    assert batch.imgs.shape == (2, 1, 2, 3)
    assert batch.indices == [[3], [5]]
    assert torch.equal(
        batch.mask,
        torch.tensor(
            [
                [[False, False, True], [False, False, True]],
                [[False, False, False], [True, True, True]],
            ]
        ),
    )
    assert batch.imgs[0, 0, 1].tolist() == [3.0, 4.0, 0.0]
    assert batch.imgs[1, 0, 1].tolist() == [0.0, 0.0, 0.0]


def test_university_collate_preserves_metadata(dictionary_path):
    _initialize_vocabulary(dictionary_path)
    samples = [
        ("a", torch.ones(1, 2, 2), ["x"], "integral", "uni", "mild"),
        ("b", torch.ones(1, 1, 3), ["y"], "limit", "hme", "hard"),
    ]

    batch = collate_formula_samples(samples)

    assert batch.imgs.shape == (2, 1, 2, 3)
    assert batch.indices == [[3], [5]]
    assert batch.categories == ["integral", "limit"]
    assert batch.sources == ["uni", "hme"]
    assert batch.severities == ["mild", "hard"]
