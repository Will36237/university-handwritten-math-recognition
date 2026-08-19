# University HMER

University HMER is an Android and FastAPI system that captures or imports an image of a mathematical expression, recognizes the expression, and returns renderable LaTeX.

The Android app currently starts in `uni_only` mode, so the main interface exposes one recognition action: **Uni-MuMER LoRA**. TAMER-A3 remains available through the backend API and can be restored in the app by building with `all_models` mode.

## Documentation

- [Installation and Run Guide](docs/RUN_GUIDE.md) - Windows 11, Android Studio, local mock services, physical devices, Ubuntu, Docker, and remote GPU execution.
- [Technical Codebase Guide](docs/reports/REPORT_1_TECHNICAL_CODEBASE_GUIDE.md) - detailed source-code, architecture, and component reference.
- [Project Report](docs/reports/PROJECT_REPORT_FINAL_VER_1.md) - project goals, methods, design, evaluation, and academic context.
- [Camera Demo Guide](android-ui-project/CAMERA_DEMO_GUIDE.md) - camera behavior for an Android phone and emulator webcam fallback.

`README.md` and `docs/RUN_GUIDE.md` describe the current runtime. The two reports are supporting technical and academic references.

## Current Recognition Flow

```text
Camera, gallery, or bundled sample
        |
        v
Crop the mathematical expression
        |
        v
Android and backend structural image validation
        |
        v
FastAPI gateway -> Uni-MuMER worker
        |
        v
Qwen3.5-2B semantic math-image classifier
        |
        +-- Not a mathematical expression
        |      -> HTTP 422 / NON_MATH_IMAGE
        |      -> Android asks the user to capture or crop a math expression
        |
        +-- Mathematical expression
               -> Uni-MuMER Qwen3.5-2B base model + LoRA adapter
               -> LaTeX, latency, image metadata, and request ID
               -> Android displays and renders the result
```

The semantic gate accepts clearly visible mathematical expressions that are handwritten, printed, or shown on a screen. It rejects unrelated content such as ordinary prose, animals, objects, and scenery before Uni-MuMER generates LaTeX. This gate is part of the real Uni-MuMER runtime; mock mode is intended for API and UI contract testing rather than model-quality evaluation.

## Components

| Component | Responsibility |
| --- | --- |
| `android-ui-project` | Capture/import, crop, validate, submit, and render recognition results. |
| `backend/gateway` | Validate requests, route model calls, normalize responses, and propagate stable errors. |
| `backend/workers/unimumer` | Classify whether the image contains math, then run Uni-MuMER LoRA recognition. |
| `backend/workers/tamer` | Run the optional TAMER-A3 research and comparison model. |
| `hmer-project` | TAMER model, data, LaTeX, decoding, and research runtime code. |

The deployable backend is under:

```text
hmer-deploy-essential-20260721/app/backend
```

## Run Modes

| Mode | Purpose | GPU required |
| --- | --- | --- |
| Local mock | Android/API development, contract tests, and UI smoke tests | No |
| Local GPU | Real Qwen classifier and model inference on a sufficiently capable Ubuntu machine | Yes |
| Remote GPU | Real inference on a GPU server reached through an SSH tunnel or protected HTTPS endpoint | Yes |

Follow the complete [Installation and Run Guide](docs/RUN_GUIDE.md). A typical Windows emulator workflow is:

1. Start the three local mock services as described in the run guide.
2. Start an Android Virtual Device.
3. From `android-ui-project`, run:

   ```powershell
   .\run_android_demo.ps1 -Target emulator -ModelUiMode uni_only
   ```

4. Capture or select an image, crop the formula, and press **Uni-MuMER LoRA**.

The default emulator endpoint is `http://10.0.2.2:8000`. The API endpoint is compiled into the debug APK. If an SSH tunnel uses a different local port, rebuild and reinstall the app with the matching `-ApiBaseUrl`; changing the tunnel alone does not update an existing APK.

## Android Camera Support

- **Primary demo:** a physical Android phone uses its own camera.
- **Fallback demo:** an Android Studio emulator can map its virtual back camera to a laptop or external USB webcam.
- Camera selection for the emulator belongs to the AVD configuration, not the recognition or Qwen classifier code.
- If the external webcam is unavailable, select another detected webcam in Device Manager or use an emulated camera.

See the [Camera Demo Guide](android-ui-project/CAMERA_DEMO_GUIDE.md) for the exact setup and troubleshooting steps.

## Model Artifacts

Model weights are intentionally excluded from Git. Real inference requires private Hugging Face access and the artifact layout documented in the run guide.

The current pinned model inputs are:

- Math-image classifier: `Qwen/Qwen3.5-2B`
- Uni-MuMER base model: `phxember/Uni-MuMER-Qwen3.5-2B`
- Uni-MuMER LoRA adapter: supplied privately by the project team
- TAMER-A3 checkpoint: optional for backend research/comparison mode

Exact revisions and environment variable names are defined in:

```text
hmer-deploy-essential-20260721/app/backend/.env.gpu.example
```

Do not commit Hugging Face tokens, `.env` files, model caches, checkpoints, adapters, virtual environments, deployment archives, or generated APK/AAB files.

## Backend API

The gateway exposes:

- `GET /health`
- `POST /predict` with multipart fields `model` and `image`

The Android `uni_only` interface submits `model=unimumer_lora`. Backend clients may also submit `model=tamer_a3` when the TAMER worker is enabled.

## Verification

Backend safety net:

```powershell
Set-Location 'hmer-deploy-essential-20260721\app\backend'
python -m pytest -q
```

Android unit tests, lint, and debug APK:

```powershell
Set-Location 'android-ui-project'
.\gradlew.bat --console=plain testDebugUnitTest lintDebug assembleDebug
```

Real model quality, semantic rejection, GPU memory use, and latency must be verified with the real private artifacts on a supported NVIDIA GPU. Passing mock tests does not prove real-model accuracy.

## Repository Structure

```text
.
|-- android-ui-project/                 Android application and demo helpers
|-- docs/                               Run guide and project reports
|-- hmer-deploy-essential-20260721/
|   `-- app/
|       |-- backend/                    Gateway, workers, deployment, and tests
|       `-- hmer-project/               TAMER and research runtime code
|-- .gitignore
`-- LICENSE
```

## Research Repository

Training code, datasets, experiments, and evaluation results are maintained separately in the [University HMER Research Repository](https://github.com/tuanfptu/SU26AI46_GSU08-Capstone-UniversityHMER).

## Licensing

This repository does not currently include a project-level license file. Do not assume permission to redistribute the source code or private model artifacts. Model weights and upstream dependencies remain subject to their respective licenses and access terms.
