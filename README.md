# University HMER Demo

Android application and FastAPI backend for recognizing handwritten mathematical expressions and converting them into LaTeX.

## Overview

The backend supports two models:

- **TAMER-A3 RealFT:** fast specialist model for research and controlled-domain testing.
- **Uni-MuMER LoRA:** primary deployment model with stronger practical robustness.

The Android application displays only **Uni-MuMER LoRA**. TAMER-A3 remains available through the backend API for research and testing.

## Workflow

```text
Capture or select image
→ Crop formula
→ Validate image
→ Run Uni-MuMER LoRA
→ Display and render LaTeX
Repository Structure
├── android-ui-project/                 # Kotlin/Jetpack Compose application
└── hmer-deploy-essential-20260721/
    └── app/
        ├── backend/                    # FastAPI gateway and model workers
        └── hmer-project/               # TAMER inference runtime
Main Features
In-app camera and gallery import
Manual formula cropping
Original and cropped-image previews
JPEG, PNG and WEBP validation
Maximum upload size of 10 MB
TAMER-A3 and Uni-MuMER LoRA backend workers
LaTeX output and mathematical rendering
Mock backend for local testing
Local Verification
Backend:
cd hmer-deploy-essential-20260721/app/backend
py -3.11 -m venv hmer_ui
.\hmer_ui\Scripts\python.exe -m pip install -r requirements-test.txt
.\hmer_ui\Scripts\python.exe -m pytest tests -q
Android:
cd android-ui-project
.\gradlew.bat --console=plain testDebugUnitTest lintDebug assembleDebug
Debug APK:
android-ui-project/app/build/outputs/apk/debug/app-debug.apk
Network
Android Emulator: http://10.0.2.2:8000
Physical device: use adb reverse
LDPlayer: use the host or bridged-network IP
Remote GPU: use an SSH tunnel or protected HTTPS endpoint
Do not expose the FastAPI port directly to the public Internet without authentication and HTTPS.
Model Files
Model weights, Hugging Face caches, virtual environments, APK/AAB outputs, .env files and deployment archives are excluded from Git.
Real inference requires the TAMER-A3 checkpoint, Uni-MuMER base model and LoRA adapter.
The inference pipeline has been evaluated on an NVIDIA GeForce RTX 3090 with 24 GB VRAM.
Research Repository
Training code, datasets, experiments and evaluation results are maintained separately:
<RESEARCH_REPOSITORY_URL>
License
See LICENSE. Model weights and upstream dependencies remain subject to their respective licenses.
