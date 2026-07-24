# University HMER RTX 3090 deployment

This bundle contains only the runtime code and required trained weights. Training,
validation, blind-test images, W&B logs, redundant checkpoints, and report files are
intentionally excluded.

## Expected files on the server

```text
hmer-deploy-essential-20260721/
├── app/
│   ├── backend/
│   └── hmer-project/
├── hf-cache/                         # created during restore
└── unimumer_base_model_hf_cache.tar.gz
```

## Restore the Uni-MuMER cache

Run from `hmer-deploy-essential-20260721`:

```bash
mkdir -p hf-cache
tar -xzf unimumer_base_model_hf_cache.tar.gz -C hf-cache --strip-components=2
test -f hf-cache/hub/models--phxember--Uni-MuMER-Qwen3.5-2B/snapshots/40a6288292057f1c162b3b0eaccd362036dbd495/model.safetensors
```

The archive stores `.cache/huggingface/hub/...`; stripping two components produces the
`hub/...` layout expected by `HF_HOME=/opt/huggingface` in the Uni-MuMER container.

## Preflight

```bash
nvidia-smi
docker run --rm --gpus all nvidia/cuda:11.8.0-base-ubuntu22.04 nvidia-smi
cd app/backend
cp .env.gpu.example .env.gpu
./verify_bundle.sh .env.gpu
docker compose --env-file .env.gpu -f docker-compose.gpu.yml config --quiet
```

The default host paths are resolved from `app/backend`: `../hmer-project` for the
research project and `../../hf-cache` for the Hugging Face cache. No manual symbolic
link under `app/` is required.

## Build and run

```bash
docker compose --env-file .env.gpu -f docker-compose.gpu.yml up -d --build
docker compose --env-file .env.gpu -f docker-compose.gpu.yml ps
docker compose --env-file .env.gpu -f docker-compose.gpu.yml logs --tail=200 tamer unimumer
curl http://127.0.0.1:8000/health
```

Expected health status is `ready` for `tamer_a3` and `unimumer_lora`. Do not expose
ports 8101 or 8102 publicly. Port 8000 binds to `127.0.0.1` by default and should be
reached through an SSH tunnel or ADB reverse. Do not bind it to a public interface
without authentication, TLS, rate limiting, and a firewall/reverse proxy.
