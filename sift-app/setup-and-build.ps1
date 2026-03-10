# ============================================================
#  SIFT — Setup & Build Script
#  Run this in PowerShell as Administrator
#  from inside the sift-app folder
# ============================================================

$ErrorActionPreference = "Stop"
$ProjectDir = $PSScriptRoot
if (-not $ProjectDir) { $ProjectDir = Get-Location }

Write-Host ""
Write-Host "=====================================" -ForegroundColor White
Write-Host "  SIFT Build Setup" -ForegroundColor White
Write-Host "=====================================" -ForegroundColor White
Write-Host ""

# ── Step 1: Verify Java ──────────────────────────────────────
Write-Host "[1/4] Checking Java..." -ForegroundColor Yellow
try {
    $jv = (java -version 2>&1) | Out-String
    Write-Host "      OK: $($jv.Trim().Split("`n")[0])" -ForegroundColor Green
} catch {
    Write-Host "      ERROR: Java not found. Install from https://adoptium.net" -ForegroundColor Red
    pause; exit 1
}

# ── Step 2: Download Gradle ──────────────────────────────────
Write-Host ""
Write-Host "[2/4] Setting up Gradle 8.9..." -ForegroundColor Yellow

$GradleVersion = "8.9"
$GradleDir     = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-$GradleVersion-bin"
$GradleBin     = "$GradleDir\gradle-$GradleVersion\bin\gradle.bat"

if (Test-Path $GradleBin) {
    Write-Host "      Gradle already downloaded." -ForegroundColor Green
} else {
    $GradleZip = "$env:TEMP\gradle-$GradleVersion-bin.zip"
    $GradleUrl = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"

    Write-Host "      Downloading Gradle $GradleVersion (~130MB)..." -ForegroundColor Yellow
    Write-Host "      URL: $GradleUrl" -ForegroundColor Gray

    try {
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $GradleUrl -OutFile $GradleZip -UseBasicParsing
        $ProgressPreference = 'Continue'
        Write-Host "      Download complete." -ForegroundColor Green
    } catch {
        Write-Host "      Download failed: $_" -ForegroundColor Red
        Write-Host "      Manually download Gradle from: $GradleUrl" -ForegroundColor Yellow
        Write-Host "      Extract to: $GradleDir" -ForegroundColor Yellow
        pause; exit 1
    }

    Write-Host "      Extracting Gradle..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Force -Path $GradleDir | Out-Null
    Expand-Archive -Path $GradleZip -DestinationPath $GradleDir -Force
    Remove-Item $GradleZip -Force
    Write-Host "      Gradle ready." -ForegroundColor Green
}

# ── Step 3: Set ANDROID_HOME ─────────────────────────────────
Write-Host ""
Write-Host "[3/4] Checking Android SDK..." -ForegroundColor Yellow

$AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk"
if (-not (Test-Path $AndroidSdk)) {
    # Try alternate location
    $AndroidSdk = "$env:USERPROFILE\AppData\Local\Android\Sdk"
}

if (Test-Path $AndroidSdk) {
    Write-Host "      Found Android SDK at: $AndroidSdk" -ForegroundColor Green
    $env:ANDROID_HOME = $AndroidSdk
    $env:ANDROID_SDK_ROOT = $AndroidSdk
} else {
    Write-Host ""
    Write-Host "      Android SDK not found at default location." -ForegroundColor Red
    Write-Host "      Enter your Android SDK path (or press Enter to skip):" -ForegroundColor Yellow
    Write-Host "      Default: $env:LOCALAPPDATA\Android\Sdk" -ForegroundColor Gray
    $customSdk = Read-Host "      Path"
    if ($customSdk -and (Test-Path $customSdk)) {
        $env:ANDROID_HOME = $customSdk
        $env:ANDROID_SDK_ROOT = $customSdk
        Write-Host "      Using: $customSdk" -ForegroundColor Green
    } else {
        Write-Host "      Continuing without SDK path — Gradle will try to locate it." -ForegroundColor Yellow
    }
}

# ── Step 4: Build APK ────────────────────────────────────────
Write-Host ""
Write-Host "[4/4] Building Sift APK..." -ForegroundColor Yellow
Write-Host "      This takes 5-15 minutes on first run." -ForegroundColor Gray
Write-Host "      Gradle is downloading Android build tools in the background." -ForegroundColor Gray
Write-Host ""

Set-Location $ProjectDir

# Create local.properties with correct SDK path
if ($env:ANDROID_HOME) {
    $sdkPath = $env:ANDROID_HOME -replace "\\", "\\\\"
    "sdk.dir=$sdkPath" | Set-Content "$ProjectDir\local.properties"
    Write-Host "      Written local.properties with sdk.dir" -ForegroundColor Green
}

# Run build
& $GradleBin assembleDebug --project-dir "$ProjectDir" --no-daemon

$success = $LASTEXITCODE -eq 0

# ── Result ───────────────────────────────────────────────────
Write-Host ""
if ($success) {
    $apk = "$ProjectDir\app\build\outputs\apk\debug\app-debug.apk"
    if (Test-Path $apk) {
        $size = [math]::Round((Get-Item $apk).Length / 1MB, 1)
        Write-Host "=====================================" -ForegroundColor Green
        Write-Host "  BUILD SUCCESSFUL" -ForegroundColor Green
        Write-Host "=====================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "  APK: $apk" -ForegroundColor Cyan
        Write-Host "  Size: $size MB" -ForegroundColor White
        Write-Host ""
        Write-Host "  Install to phone:" -ForegroundColor White
        Write-Host "  adb install `"$apk`"" -ForegroundColor Cyan
        Write-Host ""
        explorer.exe "$ProjectDir\app\build\outputs\apk\debug"
    } else {
        Write-Host "  Build returned success but APK not found. Check output above." -ForegroundColor Red
    }
} else {
    Write-Host "=====================================" -ForegroundColor Red
    Write-Host "  BUILD FAILED" -ForegroundColor Red
    Write-Host "=====================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Look for the first 'error:' line in the output above." -ForegroundColor Yellow
    Write-Host "  Copy it and share with Claude for help." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  Common fix — clean and retry:" -ForegroundColor White
    Write-Host "  & `"$GradleBin`" clean assembleDebug --project-dir `"$ProjectDir`" --no-daemon" -ForegroundColor Cyan
}

Write-Host ""
pause
