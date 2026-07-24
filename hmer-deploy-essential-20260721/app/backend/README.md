# University HMER backend

The production-shaped backend is split into a FastAPI gateway and two isolated
model workers. Android continues to use the same `GET /health` and `POST /predict`
contract at port 8000.

## Local mock stack

Create and activate a Python 3.11 environment, install
`requirements-test.txt`, then open three PowerShell terminals in this directory.

Terminal 1:

```powershell
$env:HMER_TAMER_MODE = 'mock'
$env:HMER_TAMER_EAGER_LOAD = 'false'
python -m uvicorn workers.tamer.app.main:app --host 127.0.0.1 --port 8101
```

Terminal 2:

```powershell
$env:HMER_UNIMUMER_MODE = 'mock'
$env:HMER_UNIMUMER_EAGER_LOAD = 'false'
python -m uvicorn workers.unimumer.app.main:app --host 127.0.0.1 --port 8102
```

Terminal 3:

```powershell
python -m uvicorn gateway.app.main:app --host 127.0.0.1 --port 8000
```

Then open `http://127.0.0.1:8000/health`. Swagger is available at
`http://127.0.0.1:8000/docs`.

The official Android emulator reaches this gateway at `http://10.0.2.2:8000`.
A physical phone uses `http://127.0.0.1:8000` with ADB reverse. The Android
project's `run_android_demo.ps1` script configures the correct URL and port
mapping for each target.

## GPU deployment

Create one Python environment/container per worker:

- `workers/tamer/requirements.txt`: pinned RTX 3090 TAMER/PyTorch runtime.
- `workers/unimumer/requirements.txt`: Uni-MuMER Transformers/PEFT runtime.
- `gateway/requirements.txt`: lightweight public API runtime without CUDA.

Enable real inference with `HMER_TAMER_MODE=real` and
`HMER_UNIMUMER_MODE=real`. Worker addresses can be changed with
`HMER_TAMER_WORKER_URL` and `HMER_UNIMUMER_WORKER_URL`.

Relevant path overrides are `HMER_TAMER_PROJECT_ROOT`, `HMER_TAMER_CHECKPOINT`,
`HMER_TAMER_DICTIONARY`, `HMER_UNIMUMER_BASE_MODEL`,
`HMER_UNIMUMER_BASE_MODEL_REVISION`, and `HMER_UNIMUMER_ADAPTER`.

Uploaded JPEG/PNG/WEBP images are limited to 10 MB, validated in memory, forwarded to
one selected worker, and not stored by this codebase. See `ARCHITECTURE.md` for the
service boundary.

The shared validator also rejects clearly blank or smooth shadow-only crops with
`NO_FORMULA_CONTENT`. This is a conservative pre-check; it does not claim that every
accepted crop contains a valid mathematical expression.

## Docker on an RTX 3090 server

Run these commands from `app/backend`. Copy `.env.gpu.example` to `.env.gpu`, then
verify and start the stack:

```bash
bash verify_bundle.sh .env.gpu
docker compose --env-file .env.gpu -f docker-compose.gpu.yml config
docker compose --env-file .env.gpu -f docker-compose.gpu.yml up -d --build
docker compose --env-file .env.gpu -f docker-compose.gpu.yml ps
```

The preflight verifies the recorded SHA-256 values for the checkpoint,
dictionary, and LoRA adapter before either worker starts.

The canonical bundle keeps the project at `app/hmer-project` and the Hugging Face
cache at top-level `hf-cache`. Their default paths from this directory are therefore
`../hmer-project` and `../../hf-cache`; no `app/hf-cache` symlink is needed.

Only gateway port 8000 is published, and it binds to `127.0.0.1` by default. Access it
through an SSH tunnel or ADB reverse; do not set `HMER_GATEWAY_BIND_ADDRESS=0.0.0.0`
without adding authentication, TLS, rate limiting, and a firewall/reverse proxy. The
two model workers remain private inside the Docker network. The full research project
is mounted read-only, so checkpoints and datasets are not copied into container
images. Run `nvidia-smi` and confirm the NVIDIA Container Toolkit works before
building the GPU stack.

Real workers eagerly load their model before becoming healthy. A wrong checkpoint,
missing adapter, incompatible dependency, or unavailable GPU therefore appears during
deployment instead of during the first live demo request.
