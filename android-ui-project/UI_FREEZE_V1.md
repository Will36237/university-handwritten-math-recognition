# HMER Demo — UI Freeze v1

Frozen on: 2026-07-20

## Frozen scope

- Three-screen Vietnamese research story/onboarding.
- Camera, Android Photo Picker, and five randomized real handwritten samples.
- Separate original-image and cropped-model-input previews.
- Free-form uCrop workflow with emulator-friendly handles and re-cropping.
- TAMER-A3 RealFT and Uni-MuMER LoRA result cards.
- Loading, input-validation, API/model error, and retry states.
- 720 x 1280 portrait layout for the Android emulator and Android phones.

## Allowed changes after freeze

- Bug fixes and accessibility corrections.
- Launcher logo/icon and final branding assets.
- FastAPI networking and real inference integration.
- Real LaTeX rendering, result copying, and server-status wiring.
- API base URL configuration and local ADB reverse tooling.
- Release signing and build configuration.

## Changes requiring explicit re-approval

- Navigation flow or onboarding story.
- Input/crop interaction.
- Model selection layout.
- Major color, typography, spacing, or card-layout changes.

## Snapshot

- APK: `artifacts/android/HMER-Demo-UI-Freeze-v1-debug.apk`
- SHA-256: `835FFC2FB55C36C464EAC9F3B9AC391F70D08C481721A7CCC985E3D001B92DCF`
