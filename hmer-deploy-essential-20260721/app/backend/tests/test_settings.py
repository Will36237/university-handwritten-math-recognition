from pathlib import Path

from shared.settings import GatewaySettings, TamerSettings, UniMumerSettings


def test_gateway_settings_preserve_defaults_and_trim_urls() -> None:
    settings = GatewaySettings.from_env(
        {"HMER_TAMER_WORKER_URL": "http://worker:8101/"},
    )

    assert settings.workers == {
        "tamer_a3": "http://worker:8101",
        "unimumer_lora": "http://127.0.0.1:8102",
    }
    assert settings.connect_timeout_seconds == 5.0
    assert settings.predict_timeout_seconds == 120.0


def test_gateway_settings_accept_timeout_overrides() -> None:
    settings = GatewaySettings.from_env(
        {
            "HMER_CONNECT_TIMEOUT_SECONDS": "2.5",
            "HMER_PREDICT_TIMEOUT_SECONDS": "180",
        },
    )

    assert settings.connect_timeout_seconds == 2.5
    assert settings.predict_timeout_seconds == 180.0


def test_tamer_settings_preserve_mode_eager_and_paths(tmp_path: Path) -> None:
    settings = TamerSettings.from_env(
        {"HMER_TAMER_MODE": "REAL"},
        tmp_path,
    )

    assert (settings.mode, settings.eager_load) == ("real", True)
    assert settings.project_root == tmp_path / "University-TAMER-RTX3090-A0123-trained"
    assert settings.checkpoint.name == "epoch=56-val_university_ExpRate=0.5637.ckpt"
    assert settings.dictionary == settings.project_root / "data" / "HME100k" / "dictionary.txt"


def test_unimumer_settings_preserve_mock_defaults(tmp_path: Path) -> None:
    settings = UniMumerSettings.from_env({}, tmp_path)

    assert (settings.mode, settings.eager_load) == ("mock", False)
    assert settings.base_model == "phxember/Uni-MuMER-Qwen3.5-2B"
    assert settings.base_model_revision == (
        "40a6288292057f1c162b3b0eaccd362036dbd495"
    )
    assert settings.classifier_model == "Qwen/Qwen3.5-2B"
    assert settings.classifier_model_revision == (
        "15852e8c16360a2fea060d615a32b45270f8a8fc"
    )
    assert settings.adapter_path == (
        settings.project_root
        / "outputs"
        / "unimumer_lora_unsloth_real"
        / "best_adapter"
    )


def test_unimumer_settings_accept_classifier_overrides(tmp_path: Path) -> None:
    settings = UniMumerSettings.from_env(
        {
            "HMER_MATH_CLASSIFIER_MODEL": "trusted/classifier",
            "HMER_MATH_CLASSIFIER_REVISION": "classifier-revision",
        },
        tmp_path,
    )

    assert settings.classifier_model == "trusted/classifier"
    assert settings.classifier_model_revision == "classifier-revision"


def test_explicit_eager_load_overrides_mode_default(tmp_path: Path) -> None:
    tamer = TamerSettings.from_env(
        {
            "HMER_TAMER_MODE": "real",
            "HMER_TAMER_EAGER_LOAD": "false",
        },
        tmp_path,
    )
    uni = UniMumerSettings.from_env(
        {"HMER_UNIMUMER_EAGER_LOAD": "TRUE"},
        tmp_path,
    )

    assert tamer.eager_load is False
    assert uni.eager_load is True
