from pathlib import Path


BACKEND_ROOT = Path(__file__).resolve().parents[1]


def test_default_host_paths_match_bundle_layout() -> None:
    env = (BACKEND_ROOT / ".env.gpu.example").read_text(encoding="utf-8")
    compose = (BACKEND_ROOT / "docker-compose.gpu.yml").read_text(encoding="utf-8")

    assert "HMER_PROJECT_HOST_PATH=../hmer-project" in env
    assert "HMER_HF_CACHE_HOST_PATH=../../hf-cache" in env
    assert (
        "HMER_UNIMUMER_BASE_MODEL_REVISION="
        "40a6288292057f1c162b3b0eaccd362036dbd495"
    ) in env
    assert "${HMER_PROJECT_HOST_PATH:-../hmer-project}" in compose
    assert "${HMER_HF_CACHE_HOST_PATH:-../../hf-cache}" in compose
    assert "${HMER_UNIMUMER_BASE_MODEL_REVISION:-40a6288292057f1c162b3b0eaccd362036dbd495}" in compose


def test_gateway_is_bound_to_loopback_by_default() -> None:
    compose = (BACKEND_ROOT / "docker-compose.gpu.yml").read_text(encoding="utf-8")

    assert '"${HMER_GATEWAY_BIND_ADDRESS:-127.0.0.1}:8000:8000"' in compose
    assert '\n      - "8000:8000"' not in compose


def test_verifier_resolves_paths_from_backend_directory() -> None:
    script = (BACKEND_ROOT / "verify_bundle.sh").read_text(encoding="utf-8")

    assert 'BACKEND_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"' in script
    assert 'PROJECT_HOST_PATH="${HMER_PROJECT_HOST_PATH:-../hmer-project}"' in script
    assert 'HF_CACHE_HOST_PATH="${HMER_HF_CACHE_HOST_PATH:-../../hf-cache}"' in script
    assert 'HASH_MANIFEST="$BACKEND_ROOT/../CONTENTS_AND_HASHES.txt"' in script
    assert "sha256sum --check --strict" in script


def test_unimumer_declares_matching_torchvision_runtime() -> None:
    requirements = (
        BACKEND_ROOT / "workers" / "unimumer" / "requirements.txt"
    ).read_text(encoding="utf-8")

    assert "https://download.pytorch.org/whl/cu126" in requirements
    assert "torchvision==0.22.1+cu126" in requirements
