# HMER Android Camera Demo

The primary demo target is a physical Android phone. The fallback is the
official Android emulator with a webcam connected to the laptop.

## Prerequisites

- The gateway health endpoint responds at `http://127.0.0.1:8000/health` on
  the laptop.
- Android Studio, Android SDK platform-tools, and the project JDK are
  installed.
- Run all PowerShell commands from `android-ui-project`.

## Optional private tunnel to a GPU gateway

When the gateway is running on a remote GPU server, create a local tunnel in a
dedicated PowerShell window:

```powershell
$GpuHost = Read-Host 'GPU SSH host'
$GpuPort = [int](Read-Host 'GPU SSH port')
$GpuUser = Read-Host 'GPU SSH user'
$SshKey = Read-Host 'Absolute path to the SSH private key'

ssh -N `
  -i $SshKey `
  -p $GpuPort `
  -L 8000:127.0.0.1:8000 `
  "${GpuUser}@${GpuHost}"
```

Keep that window open and verify:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8000/health' |
  ConvertTo-Json -Depth 5
```

Do not put the real host, port, password, or private key in Git.

## Primary demo: physical Android phone

1. Enable Developer options and USB debugging on the phone.
2. Connect the phone by USB and accept the RSA authorization prompt.
3. Verify that exactly one non-emulator device is authorized:

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb devices -l
```

4. Build, install, configure `adb reverse`, and launch:

```powershell
.\run_android_demo.ps1 -Target phone
```

If multiple physical devices are connected, copy the intended serial from
`adb devices -l` and run:

```powershell
$DeviceSerial = Read-Host 'Authorized physical-device serial'
.\run_android_demo.ps1 -Target phone -Serial $DeviceSerial
```

## Fallback demo: emulator with the integrated webcam

1. List host cameras:

```powershell
$emulator = Join-Path $env:LOCALAPPDATA 'Android\Sdk\emulator\emulator.exe'
& $emulator -webcam-list
```

2. In Android Studio, open **Device Manager**, edit `HMER_Test_API`, open
   **Additional settings**, and set **Back camera** to the identifier reported
   for the integrated camera. On the current laptop it is `webcam0`.
3. Make sure the laptop's physical camera shutter or privacy switch is open.
4. Cold boot the AVD.
5. Verify the emulator:

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb devices -l
```

6. Build, install, and launch:

```powershell
.\run_android_demo.ps1 -Target emulator
```

If the system camera still shows `VirtualScene`, stop the AVD and start it
once with an explicit camera flag:

```powershell
$emulator = Join-Path $env:LOCALAPPDATA 'Android\Sdk\emulator\emulator.exe'
$WebcamId = Read-Host 'Webcam identifier reported by emulator -webcam-list'

Start-Process `
  -FilePath $emulator `
  -ArgumentList @(
    '-avd',
    'HMER_Test_API',
    '-no-snapshot-load',
    '-camera-back',
    $WebcamId
  )
```

## Switching to an external laptop webcam

1. Close the emulator.
2. Connect the external webcam.
3. Run `emulator.exe -webcam-list` again.
4. In Device Manager, change **Back camera** from `webcam0` to the identifier
   reported for the external camera.
5. Cold boot the AVD and rerun the emulator launcher.

The external identifier is often `webcam1`, but always use the value reported
on the demo laptop. No Kotlin or APK change is required.

## Capture-to-crop smoke test

1. Open the recognition workspace.
2. Tap **Chụp ảnh**.
3. Capture a mathematical expression and accept the photo.
4. Confirm the captured source image appears.
5. Tap **Cắt công thức**, adjust the crop, and confirm it.
6. Confirm the cropped image appears and recognition controls are enabled.
7. Run both models and confirm each produces either a valid result or a
   structured model-specific error.
8. Repeat Steps 2-6 with **Thư viện** to confirm both inputs use the same crop
   flow.

## Troubleshooting

- `unauthorized`: unlock the phone and accept its USB debugging prompt.
- More than one matching target: rerun with `-Serial`.
- Phone cannot reach the gateway: copy its serial from `adb devices -l`, then
  run:

```powershell
$DeviceSerial = Read-Host 'Authorized physical-device serial'
& $adb -s $DeviceSerial reverse tcp:8000 tcp:8000
```

- Emulator shows a virtual scene: stop it, recheck the AVD back-camera
  mapping, cold boot, then use the explicit `-camera-back` command above if
  needed.
- Emulator shows a black preview: open the laptop's physical camera shutter
  or privacy switch and close other applications that are using the webcam.
- External camera is absent from `-webcam-list`: close applications using that
  webcam, reconnect it, and list cameras again.

## Rollback

Changing the AVD camera mapping does not modify application code. Set the AVD
back camera to its previous value (`virtualscene`) and cold boot to restore the
original emulator configuration.
