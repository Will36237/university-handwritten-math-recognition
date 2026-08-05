# Installation and Run Guide

This is the canonical guide for installing, running, testing, and stopping
University HMER. The primary local workflow is Windows 11 with PowerShell and
Android Studio. Real inference is documented separately for a native Ubuntu
host with Docker and an NVIDIA GPU.

## 1. Choose a run mode

| Mode | Android client | Backend | Model weights | Intended use |
| --- | --- | --- | --- | --- |
| Local mock | Windows laptop | Windows CPU, three Python services | Not required | UI development, API integration, and automated tests |
| Personal GPU | Windows or a physical phone | Native Ubuntu on the same machine or LAN | Required | Real inference on personally owned hardware |
| Remote GPU | Windows or a physical phone | Private Ubuntu GPU server reached through SSH | Required | Real inference without a local NVIDIA GPU |

The complete real stack has been verified on native Ubuntu with an NVIDIA RTX
3090 Ti 24 GB. A lower-memory GPU, Windows Docker Desktop with WSL2, or another
CUDA stack may work, but those combinations are not a verified baseline for
this repository.

The network paths are:

```text
Android emulator
  http://10.0.2.2:8000
          |
          v
Windows gateway at 127.0.0.1:8000
          |
          +--> TAMER-A3 worker at 127.0.0.1:8101
          |
          +--> Uni-MuMER worker at 127.0.0.1:8102
```

For a physical phone, `adb reverse` maps the phone's
`127.0.0.1:8000` to the laptop. For a remote GPU, an SSH tunnel maps the
laptop's `127.0.0.1:8000` to the server's loopback-only gateway.

## 2. Get repository access and clone

This is a private GitHub repository. The owner must add each team member as a
collaborator, and the invitation must be accepted before cloning.

Use HTTPS:

```powershell
Set-Location '<parent-directory-for-projects>'
git clone https://github.com/Will36237/university-handwritten-math-recognition.git
Set-Location '.\university-handwritten-math-recognition'
git status --short --branch
```

Alternatively, after adding an SSH public key to the team member's GitHub
account:

```powershell
Set-Location '<parent-directory-for-projects>'
git clone git@github.com:Will36237/university-handwritten-math-recognition.git
Set-Location '.\university-handwritten-math-recognition'
git status --short --branch
```

The expected branch is `main`. Do not place a GitHub token in a clone URL or
commit it to a file.

## 3. Windows 11 prerequisites

Install:

- Git for Windows.
- 64-bit Python 3.11 with the Python Launcher (`py.exe`).
- Android Studio with its embedded JetBrains Runtime.
- Android SDK Platform 36, SDK Platform-Tools, Android SDK Build-Tools, and the
  Android Emulator.
- A Windows account with hardware virtualization enabled if an emulator will
  be used.

Docker is not required for the local mock workflow. The first Python install
and Gradle build need internet access to download dependencies.

Verify the tools in PowerShell:

```powershell
git --version
py -3.11 --version

$env:JAVA_HOME = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:ANDROID_HOME\platform-tools\adb.exe" version
```

All remaining Windows commands assume PowerShell is opened at the repository
root unless another directory is stated.

## 4. Run the local mock backend

Mock mode preserves the production API contract but returns deterministic
LaTeX without loading PyTorch, CUDA, or model weights.

### 4.1 Create the Python environment

```powershell
Set-Location '.\hmer-deploy-essential-20260721\app\backend'

py -3.11 -m venv hmer_ui
.\hmer_ui\Scripts\python.exe -m pip install --upgrade pip
.\hmer_ui\Scripts\python.exe -m pip install -r .\requirements-test.txt
```

The `hmer_ui` directory is ignored by Git. Do not commit a virtual
environment.

### 4.2 Start the three services

Open three PowerShell windows. In every window, change to
`hmer-deploy-essential-20260721\app\backend`.

Terminal 1 — TAMER-A3 mock worker:

```powershell
$env:HMER_TAMER_MODE = 'mock'
$env:HMER_TAMER_EAGER_LOAD = 'false'
.\hmer_ui\Scripts\python.exe -m uvicorn `
  workers.tamer.app.main:app `
  --host 127.0.0.1 `
  --port 8101
```

Terminal 2 — Uni-MuMER mock worker:

```powershell
$env:HMER_UNIMUMER_MODE = 'mock'
$env:HMER_UNIMUMER_EAGER_LOAD = 'false'
.\hmer_ui\Scripts\python.exe -m uvicorn `
  workers.unimumer.app.main:app `
  --host 127.0.0.1 `
  --port 8102
```

Terminal 3 — API gateway:

```powershell
.\hmer_ui\Scripts\python.exe -m uvicorn `
  gateway.app.main:app `
  --host 127.0.0.1 `
  --port 8000
```

Keep all three windows open.

### 4.3 Verify health and prediction

In a fourth PowerShell window, from the backend directory:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8101/health' |
  ConvertTo-Json -Depth 5

Invoke-RestMethod 'http://127.0.0.1:8102/health' |
  ConvertTo-Json -Depth 5

Invoke-RestMethod 'http://127.0.0.1:8000/health' |
  ConvertTo-Json -Depth 5
```

Both workers and the gateway should report `ready`. Swagger UI is available at
<http://127.0.0.1:8000/docs>.

Test both models with a tracked sample image:

```powershell
$ImagePath = (
  Resolve-Path `
    '..\..\..\android-ui-project\app\src\main\res\drawable\sample_hard_01.png'
).Path

curl.exe `
  --fail-with-body `
  -X POST 'http://127.0.0.1:8000/predict' `
  -F 'model=tamer_a3' `
  -F "image=@$ImagePath;type=image/png"

curl.exe `
  --fail-with-body `
  -X POST 'http://127.0.0.1:8000/predict' `
  -F 'model=unimumer_lora' `
  -F "image=@$ImagePath;type=image/png"
```

Each response should contain a non-empty `request_id`, `valid_latex: true`,
positive image dimensions, and `mock: true`.

## 5. Configure and run the Android app

### 5.1 Configure the Android SDK

From the repository root:

```powershell
Set-Location '.\android-ui-project'

$env:JAVA_HOME = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

$SdkForProperties = $env:ANDROID_HOME -replace '\\', '/'
Set-Content `
  -LiteralPath '.\local.properties' `
  -Value "sdk.dir=$SdkForProperties" `
  -Encoding ascii

.\gradlew.bat --version
.\gradlew.bat --console=plain assembleDebug
```

`local.properties` is machine-specific and ignored by Git.

### 5.2 Create and start an emulator

In Android Studio:

1. Open `android-ui-project`.
2. Open **Tools > Device Manager**.
3. Select **Add Device > Create Virtual Device**.
4. Choose **Small Phone**.
5. Choose the stable **API 36.0 / Android 16** Google Play Intel x86_64 system
   image with the normal page size. Do not select a preview image.
6. Name the AVD `HMER_Test_API`, finish creation, and start it.

Verify that exactly one emulator is connected:

```powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb devices -l
```

Install and launch the app:

```powershell
.\run_android_demo.ps1 `
  -Target emulator `
  -ModelUiMode uni_only
```

The script builds the APK with `http://10.0.2.2:8000`, installs it on the
emulator, and launches it. `uni_only` is the default and displays one
Uni-MuMER action and one Uni-MuMER result card. The mock backend from Section 4
must still be running.

To restore the complete TAMER-A3, Uni-MuMER, and compare-models interface:

```powershell
.\run_android_demo.ps1 `
  -Target emulator `
  -ModelUiMode all_models
```

### 5.3 Use a physical Android phone

1. Enable Developer options and USB debugging.
2. Connect the phone by USB.
3. Unlock it and accept the RSA authorization prompt.
4. Verify the device:

```powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb devices -l
```

5. Build, install, configure `adb reverse`, and launch:

```powershell
.\run_android_demo.ps1 `
  -Target phone `
  -ModelUiMode uni_only
```

If multiple physical devices are connected:

```powershell
$DeviceSerial = Read-Host 'Authorized physical-device serial'
.\run_android_demo.ps1 `
  -Target phone `
  -Serial $DeviceSerial `
  -ModelUiMode uni_only
```

The script compiles `http://127.0.0.1:8000` into the phone build and creates
the required reverse-port mapping. Use `-ModelUiMode all_models` with either
target when the full two-model interface is needed.

The model UI mode is compiled into the APK. Run the script without
`-SkipBuild` after changing modes. `-SkipBuild` only reinstalls the APK that
was built most recently; it cannot change that APK's mode.

To build without installing, use one of these commands from
`android-ui-project`:

```powershell
.\gradlew.bat `
  --console=plain `
  -PHMER_MODEL_UI_MODE=uni_only `
  assembleDebug

.\gradlew.bat `
  --console=plain `
  -PHMER_MODEL_UI_MODE=all_models `
  assembleDebug
```

### 5.4 Exercise the UI

1. Choose **Thư viện** or **Chụp ảnh**.
2. Select or capture a handwritten formula.
3. Choose **Cắt vùng công thức** and confirm the crop.
4. In the default `uni_only` build, run **Uni-MuMER**. In an `all_models`
   build, run **TAMER-A3**, **Uni-MuMER**, or **So sánh models**.
5. Confirm that the result contains LaTeX and that the formula is rendered.

See the
[camera demo guide](../android-ui-project/CAMERA_DEMO_GUIDE.md) for phone
camera, integrated webcam, external webcam, and camera troubleshooting.

## 6. Run real models on Ubuntu with Docker

### 6.1 Supported boundary

The verified production-shaped path is a native Ubuntu GPU host. The gateway
is published only at `127.0.0.1:8000`; workers remain private inside the
Docker network.

Install Docker Engine by following the
[official Ubuntu instructions](https://docs.docker.com/engine/install/ubuntu/).
Install and configure the
[NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html).

Verify the host:

```bash
nvidia-smi
docker --version
docker compose version
command -v nvidia-ctk

sudo docker run --rm --gpus all \
  nvidia/cuda:12.6.3-base-ubuntu22.04 \
  nvidia-smi
```

If the last command cannot see the GPU, do not build the project yet. Fix the
NVIDIA Container Toolkit first.

### 6.2 Supply the private model artifacts

Model weights are intentionally not stored in Git. Until the team's private
Hugging Face repositories and downloader are added, obtain the deployment
artifacts from the project maintainer and restore this layout:

```text
hmer-deploy-essential-20260721/
├── app/
│   ├── backend/
│   └── hmer-project/
│       ├── data/HME100k/dictionary.txt
│       └── outputs/
│           ├── real_ft_a3_dual_seed7/checkpoints/
│           │   └── epoch=56-val_university_ExpRate=0.5637.ckpt
│           └── unimumer_lora_unsloth_real/best_adapter/
│               └── adapter_model.safetensors
└── hf-cache/
    └── hub/models--phxember--Uni-MuMER-Qwen3.5-2B/
        └── snapshots/40a6288292057f1c162b3b0eaccd362036dbd495/
            └── model.safetensors
```

Do not rename the pinned Uni-MuMER snapshot directory. The preflight also
checks the tracked manifest at
`hmer-deploy-essential-20260721/app/CONTENTS_AND_HASHES.txt`.

### 6.3 Preflight, build, and start

On the Ubuntu host:

```bash
cd hmer-deploy-essential-20260721/app/backend

test -f .env.gpu || cp .env.gpu.example .env.gpu
bash verify_bundle.sh .env.gpu

sudo docker compose \
  --env-file .env.gpu \
  -f docker-compose.gpu.yml \
  config --quiet

sudo docker compose \
  --env-file .env.gpu \
  -f docker-compose.gpu.yml \
  up -d --build
```

Model images and dependencies are large, and both workers eagerly load before
they become healthy. Monitor startup:

```bash
sudo docker compose \
  --env-file .env.gpu \
  -f docker-compose.gpu.yml \
  ps

sudo docker compose \
  --env-file .env.gpu \
  -f docker-compose.gpu.yml \
  logs --tail=200 tamer unimumer gateway

curl --fail http://127.0.0.1:8000/health
```

The expected gateway result is `ready`, with `tamer_a3` and
`unimumer_lora` both `ready`.

For deeper deployment details, see the
[RTX 3090 deployment guide](../hmer-deploy-essential-20260721/app/DEPLOY_GUIDE.md)
and [backend guide](../hmer-deploy-essential-20260721/app/backend/README.md).

### 6.4 Connect Windows to a remote GPU

Open a dedicated PowerShell window on the Windows laptop:

```powershell
$GpuHost = Read-Host 'GPU SSH host or IP'
$GpuPort = [int](Read-Host 'GPU SSH port')
$GpuUser = Read-Host 'GPU SSH user'
$SshKey = Read-Host 'Absolute path to the SSH private key'

ssh -N `
  -i $SshKey `
  -p $GpuPort `
  -L 8000:127.0.0.1:8000 `
  "${GpuUser}@${GpuHost}"
```

Keep that window open. In a different PowerShell window:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8000/health' |
  ConvertTo-Json -Depth 5
```

The Android emulator and physical-phone launch commands from Section 5 remain
unchanged because they connect through the laptop's local port 8000.

Never write the real server address, SSH password, access token, or private-key
contents into this repository.

## 7. Run the automated safety nets

### 7.1 Backend tests

From `hmer-deploy-essential-20260721\app\backend`:

```powershell
.\hmer_ui\Scripts\python.exe -m pytest .\tests -q
```

The real TAMER checkpoint contract is skipped when its required environment
variables are absent. Mock, gateway, worker, settings, deployment, request
context, and adapter contract tests still run.

### 7.2 Android unit tests, lint, and APK

From `android-ui-project`:

```powershell
$env:JAVA_HOME = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

.\gradlew.bat --console=plain testDebugUnitTest lintDebug assembleDebug
```

### 7.3 Android instrumented tests

Start one emulator or connect one authorized phone, then run:

```powershell
.\gradlew.bat --console=plain connectedDebugAndroidTest
```

Instrumented tests require a connected Android device; they are not a
headless JVM-only test.

## 8. Stop services safely

- Mock backend: press `Ctrl+C` once in each of the three Uvicorn terminals.
- SSH tunnel: press `Ctrl+C` in the dedicated tunnel window.
- Android emulator: use **Stop** in Android Studio Device Manager.
- Physical phone: disconnect USB after Uvicorn and the SSH tunnel are stopped.
- Ubuntu Docker stack:

```bash
cd hmer-deploy-essential-20260721/app/backend
sudo docker compose \
  --env-file .env.gpu \
  -f docker-compose.gpu.yml \
  down
```

`docker compose down` removes project containers and the project network. It
does not delete the bind-mounted model files or Hugging Face cache.

## 9. Troubleshooting

### A port is already in use

Inspect listeners instead of terminating an unknown process:

```powershell
Get-NetTCPConnection -State Listen |
  Where-Object LocalPort -In 8000, 8101, 8102 |
  Select-Object LocalAddress, LocalPort, OwningProcess
```

Stop the Uvicorn instance that belongs to the project, then restart the three
services.

### Gateway health is `degraded`

Verify ports 8101 and 8102 directly. Start the missing worker before restarting
the gateway.

### Prediction returns HTTP 422

Confirm the multipart fields are exactly `model` and `image`. Use
`sample_hard_01.png` for the first smoke test; an empty, corrupted, oversized,
or contentless image is rejected intentionally.

### Android cannot reach the backend

- Emulator: the APK must use `http://10.0.2.2:8000`.
- Physical phone: rerun `run_android_demo.ps1 -Target phone` so it recreates
  `adb reverse`.
- Remote GPU: keep the SSH tunnel open and verify laptop
  `http://127.0.0.1:8000/health` first.

### ADB reports `unauthorized`

Unlock the phone, accept the RSA prompt, then rerun `adb devices -l`.

### Android Studio reports an incompatible Android Gradle Plugin

This project uses AGP 9.3.0. Update Android Studio to a version that supports
that plugin. Do not silently downgrade the plugin in a personal branch. The
Gradle command-line build remains the authoritative build check.

### Gradle reports an invalid SDK directory

Regenerate `android-ui-project\local.properties` using the forward-slash
PowerShell command in Section 5.1. Do not copy another developer's
`local.properties`.

### Docker reports no known GPU vendor or cannot select a GPU

On Ubuntu:

```bash
sudo nvidia-ctk runtime configure --runtime=docker
sudo systemctl restart docker

sudo docker run --rm --gpus all \
  nvidia/cuda:12.6.3-base-ubuntu22.04 \
  nvidia-smi
```

Do not start the HMER stack until the CUDA test container succeeds.

### A worker is unhealthy

Run `verify_bundle.sh` again, inspect worker logs, and check the exact artifact
paths from Section 6.2. Missing weights, a wrong hash, dependency mismatch, or
GPU memory pressure is surfaced during eager loading.

## 10. Security and team rules

- Keep the GitHub and future Hugging Face repositories private.
- Grant access only to team members who need it.
- Never commit `.env.gpu`, `local.properties`, virtual environments, APK/AAB
  files, model weights, Hugging Face caches, passwords, tokens, or private keys.
- Keep the gateway bound to `127.0.0.1`. Use SSH tunneling or `adb reverse`;
  do not publish port 8000 directly to the internet.
- Before sharing changes, run:

```powershell
git status --short
git diff --check
git diff --cached --check
```
