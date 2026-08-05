[CmdletBinding()]
param(
    [ValidateSet("emulator", "phone")]
    [string]$Target = "emulator",

    [string]$Serial = "",

    [string]$ApiBaseUrl = "",

    [ValidateSet("uni_only", "all_models")]
    [string]$ModelUiMode = "uni_only",

    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$gradle = Join-Path $PSScriptRoot "gradlew.bat"
$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
$packageName = "vn.edu.fpt.hmerdemo"

if (-not (Test-Path -LiteralPath $adb)) {
    throw "adb.exe not found at $adb"
}
if (-not (Test-Path -LiteralPath $gradle)) {
    throw "gradlew.bat not found at $gradle"
}

& $adb start-server | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Unable to start the ADB server."
}

$connectedSerials = @(
    & $adb devices |
        Select-Object -Skip 1 |
        ForEach-Object {
            if ($_ -match '^(\S+)\s+device$') {
                $Matches[1]
            }
        }
)

if ($Serial) {
    if ($Serial -notin $connectedSerials) {
        throw "Device '$Serial' is not connected and authorized."
    }
} else {
    $candidates = @(
        $connectedSerials |
            Where-Object {
                if ($Target -eq "emulator") {
                    $_ -like "emulator-*"
                } else {
                    $_ -notlike "emulator-*"
                }
            }
    )

    if ($candidates.Count -ne 1) {
        $found = if ($candidates.Count -eq 0) {
            "none"
        } else {
            $candidates -join ", "
        }
        throw "Expected exactly one $Target target; found: $found. Use -Serial."
    }
    $Serial = $candidates[0]
}

if (-not $ApiBaseUrl) {
    $ApiBaseUrl = if ($Target -eq "phone") {
        "http://127.0.0.1:8000"
    } else {
        "http://10.0.2.2:8000"
    }
}

if (-not $SkipBuild) {
    Push-Location $PSScriptRoot
    try {
        & $gradle `
            --console=plain `
            "-PHMER_API_BASE_URL=$ApiBaseUrl" `
            "-PHMER_MODEL_UI_MODE=$ModelUiMode" `
            assembleDebug
        if ($LASTEXITCODE -ne 0) {
            throw "Android debug build failed."
        }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path -LiteralPath $apk)) {
    throw "Debug APK not found at $apk"
}

function Invoke-TargetAdb {
    param(
        [Parameter(Mandatory)]
        [string[]]$AdbArguments
    )

    & $adb -s $Serial @AdbArguments
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed: adb -s $Serial $($AdbArguments -join ' ')"
    }
}

if ($Target -eq "phone") {
    Invoke-TargetAdb -AdbArguments @(
        "reverse",
        "tcp:8000",
        "tcp:8000"
    )
}

Invoke-TargetAdb -AdbArguments @("install", "-r", $apk)
Invoke-TargetAdb -AdbArguments @(
    "shell",
    "am",
    "force-stop",
    $packageName
)
Invoke-TargetAdb -AdbArguments @(
    "shell",
    "am",
    "start",
    "-W",
    "-n",
    "$packageName/.MainActivity"
)

Write-Host "HMER Demo installed and opened on $Serial."
Write-Host "Target: $Target"
Write-Host "API base URL compiled into this APK: $ApiBaseUrl"
if ($SkipBuild) {
    Write-Host "Model UI mode unchanged: existing APK reused; -ModelUiMode was not applied."
} else {
    Write-Host "Model UI mode compiled into this APK: $ModelUiMode"
}
if ($Target -eq "phone") {
    Write-Host "ADB reverse: device 127.0.0.1:8000 -> laptop 127.0.0.1:8000"
}
