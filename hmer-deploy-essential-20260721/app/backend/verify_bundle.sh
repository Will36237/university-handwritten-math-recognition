#!/usr/bin/env bash
set -euo pipefail

BACKEND_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${1:-$BACKEND_ROOT/.env.gpu}"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

PROJECT_HOST_PATH="${HMER_PROJECT_HOST_PATH:-../hmer-project}"
HF_CACHE_HOST_PATH="${HMER_HF_CACHE_HOST_PATH:-../../hf-cache}"
PROJECT_ROOT="$(realpath -m "$BACKEND_ROOT/$PROJECT_HOST_PATH")"
HF_CACHE_ROOT="$(realpath -m "$BACKEND_ROOT/$HF_CACHE_HOST_PATH")"
HASH_MANIFEST="$BACKEND_ROOT/../CONTENTS_AND_HASHES.txt"

required=(
  "$PROJECT_ROOT/tamer/lit_university.py"
  "$PROJECT_ROOT/data/HME100k/dictionary.txt"
  "$PROJECT_ROOT/outputs/real_ft_a3_dual_seed7/checkpoints/epoch=56-val_university_ExpRate=0.5637.ckpt"
  "$PROJECT_ROOT/outputs/unimumer_lora_unsloth_real/best_adapter/adapter_config.json"
  "$PROJECT_ROOT/outputs/unimumer_lora_unsloth_real/best_adapter/adapter_model.safetensors"
  "$HF_CACHE_ROOT/hub/models--phxember--Uni-MuMER-Qwen3.5-2B/snapshots/40a6288292057f1c162b3b0eaccd362036dbd495/model.safetensors"
)

for path in "${required[@]}"; do
  test -s "$path" || { echo "MISSING_OR_EMPTY: $path" >&2; exit 1; }
done

test -s "$HASH_MANIFEST" || {
  echo "MISSING_OR_EMPTY: $HASH_MANIFEST" >&2
  exit 1
}
command -v sha256sum >/dev/null || {
  echo "MISSING_COMMAND: sha256sum" >&2
  exit 1
}

verified_hashes=0
while read -r expected relative_path; do
  asset_path="${relative_path#hmer-project/}"
  resolved_path="$PROJECT_ROOT/$asset_path"
  test -s "$resolved_path" || {
    echo "MISSING_OR_EMPTY: $resolved_path" >&2
    exit 1
  }
  printf '%s  %s\n' "${expected,,}" "$resolved_path" |
    sha256sum --check --strict -
  verified_hashes=$((verified_hashes + 1))
done < <(
  grep -E '^[[:xdigit:]]{64}[[:space:]]+hmer-project/' "$HASH_MANIFEST"
)

test "$verified_hashes" -gt 0 || {
  echo "NO_PROJECT_HASHES: $HASH_MANIFEST" >&2
  exit 1
}

echo "BUNDLE_PREFLIGHT_OK"
