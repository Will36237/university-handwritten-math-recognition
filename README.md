# University HMER Demo

Android application and FastAPI backend for recognizing handwritten mathematical expressions and converting them into LaTeX.

## Overview

The backend supports two models:

- **TAMER-A3 RealFT:** fast specialist model for research and controlled-domain testing.
- **Uni-MuMER LoRA:** primary deployment model with stronger practical robustness.

The Android application exposes only **Uni-MuMER LoRA** for inference. TAMER-A3 remains available through the backend API for research and testing.

## Workflow

```text
Capture or select image
→ Crop formula
→ Validate image
→ Run Uni-MuMER LoRA
→ Display and render LaTeX
```

## Main Features

- In-app camera and gallery import
- Manual formula cropping
- Original and cropped-image previews
- Image-input validation
- FastAPI inference backend
- LaTeX output and mathematical rendering
- Loading, retry, and error handling

## Android Demo

The application was successfully built and demonstrated in **Android Studio** using an Android emulator.

To run the demo:

1. Open `android-ui-project` in Android Studio.
2. Wait for Gradle synchronization.
3. Select an emulator or connected Android device.
4. Click **Run ▶**.

The debug APK is generated at:

```text
android-ui-project/app/build/outputs/apk/debug/app-debug.apk
```

## Backend Connection

- Android Studio emulator: `http://10.0.2.2:8000`
- Physical Android device: use `adb reverse`
- LDPlayer: use the host or bridged-network IP
- Remote GPU server: use an SSH tunnel or protected HTTPS endpoint

## Model Files

Model weights, Hugging Face caches, virtual environments, generated APK/AAB files, `.env` files, and deployment archives are excluded from Git.

Real inference requires the Uni-MuMER base model and LoRA adapter. The backend additionally supports the TAMER-A3 checkpoint.

The inference pipeline was evaluated on an **NVIDIA GeForce RTX 3090 with 24 GB VRAM**.

## Research Repository

Training code, datasets, experiments, and evaluation results are maintained separately:

`[<RESEARCH_REPOSITORY_URL>](https://github.com/tuanfptu/SU26AI46_GSU08-Capstone-UniversityHMER)`

## License

See [LICENSE](LICENSE). Model weights and upstream dependencies remain subject to their respective licenses.
