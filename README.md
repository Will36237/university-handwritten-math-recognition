# University HMER

University HMER is an Android demonstration app and a GPU-ready backend for
recognizing handwritten mathematical expressions and returning LaTeX. A user can
choose an image or capture one with the phone camera, crop the formula, and run
either the TAMER-A3 or Uni-MuMER LoRA model through the same API.

## Repository layout

- `android-ui-project/`: Kotlin/Jetpack Compose application, camera/gallery import,
  crop flow, local KaTeX rendering, and Android tests.
- `hmer-deploy-essential-20260721/app/backend/`: FastAPI gateway, isolated model
  workers, Docker GPU deployment, and contract tests.
- `hmer-deploy-essential-20260721/app/hmer-project/`: refactored TAMER research
  runtime plus model contract tests.

## What is intentionally not in Git

Model weights, Hugging Face caches, Python virtual environments, APK/AAB outputs,
local Android SDK paths, `.env` files, and deployment archives are ignored. The
real GPU stack therefore needs the separately supplied deployment bundle and model
cache. The mock stack and Android UI can be built and tested without model weights.

## Installation & Run

For prerequisites, local mock setup, Android emulator/phone instructions,
Ubuntu GPU deployment, testing, shutdown, and troubleshooting, follow the
**[complete installation and run guide](docs/RUN_GUIDE.md)**.

## Local verification

Backend mock contracts, from `hmer-deploy-essential-20260721/app/backend`:

```powershell
py -3.11 -m venv hmer_ui
.\hmer_ui\Scripts\python.exe -m pip install -r .\requirements-test.txt
.\hmer_ui\Scripts\python.exe -m pytest .\tests -q
```

Android build and tests, from `android-ui-project`:

```powershell
.\gradlew.bat --console=plain testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat --console=plain connectedDebugAndroidTest
```

See [the backend guide](hmer-deploy-essential-20260721/app/backend/README.md) for
the mock and GPU services. See
[the camera demo guide](android-ui-project/CAMERA_DEMO_GUIDE.md) for a physical
phone, emulator, integrated webcam, or external webcam.

## Network boundary

The Docker gateway binds to `127.0.0.1:8000` by default. A physical Android phone
uses ADB reverse; an emulator uses `10.0.2.2`; a remote GPU is reached through an
SSH tunnel. The service has no public-internet authentication layer, so do not
publish the gateway port directly.

See [the security audit](docs/security-audit-2026-07-24.md) for the reviewed
boundaries, resolved findings, and accepted legacy-model risk.

## Status

The repository contains automated safety nets for API contracts, deployment
layout, Android state/orchestration, image URI handling, and network policy.
The real Docker stack and both models have been verified on an RTX 3090 Ti.
Camera preview/crop smoke testing still requires the selected physical phone or
an enabled laptop/external webcam.
