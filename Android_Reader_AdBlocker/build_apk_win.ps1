# ==============================================================================
# Windows 一鍵下載輕量級環境並打包 Android APK 腳本 (免安裝完整 Android Studio)
# ==============================================================================
$ErrorActionPreference = "Stop"
$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectDir

$ToolsDir = Join-Path $env:LOCALAPPDATA "AndroidLightBuild"
$JdkDir = Join-Path $ToolsDir "jdk-17"
$GradleDir = Join-Path $ToolsDir "gradle-8.7"
$SdkDir = Join-Path $ToolsDir "android-sdk"

New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
New-Item -ItemType Directory -Force -Path $SdkDir | Out-Null

# 1. 檢查系統是否已有 Java 17，若無則下載輕量可攜版 OpenJDK 17
$SystemJava = Get-Command java -ErrorAction SilentlyContinue
if ($SystemJava) {
    Write-Host "☕ [1/4] 偵測到系統已安裝 Java，直接使用現有環境。" -ForegroundColor Green
} else {
    $JavaExe = Get-ChildItem -Path $JdkDir -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $JavaExe) {
        Write-Host "☕ [1/4] 正在下載可攜式 OpenJDK 17..." -ForegroundColor Cyan
        $JdkZip = Join-Path $ToolsDir "jdk17.zip"
        Invoke-WebRequest -Uri "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse" -OutFile $JdkZip
        Expand-Archive -Path $JdkZip -DestinationPath $JdkDir -Force
        Remove-Item $JdkZip -Force
        $JavaExe = Get-ChildItem -Path $JdkDir -Filter "java.exe" -Recurse | Select-Object -First 1
    }
    $env:JAVA_HOME = $JavaExe.Directory.Parent.FullName
    $env:PATH = "$($JavaExe.Directory.FullName);$env:PATH"
}

# 2. 檢查或下載 Android Command-Line Tools
$SdkManagerBat = Join-Path $SdkDir "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $SdkManagerBat)) {
    Write-Host "📦 [2/4] 正在下載 Android Command-Line Tools..." -ForegroundColor Cyan
    $CmdZip = Join-Path $ToolsDir "cmdline-tools.zip"
    Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $CmdZip
    $TempExtract = Join-Path $ToolsDir "cmd_extract"
    Expand-Archive -Path $CmdZip -DestinationPath $TempExtract -Force
    $LatestDir = Join-Path $SdkDir "cmdline-tools\latest"
    New-Item -ItemType Directory -Force -Path (Split-Path $LatestDir) | Out-Null
    Move-Item -Path (Join-Path $TempExtract "cmdline-tools") -Destination $LatestDir -Force
    Remove-Item $CmdZip, $TempExtract -Recurse -Force -ErrorAction SilentlyContinue
}
$env:ANDROID_HOME = $SdkDir

# 自動建立 licences 憑證，免手動按 y
$LicensesDir = Join-Path $SdkDir "licenses"
New-Item -ItemType Directory -Force -Path $LicensesDir | Out-Null
Set-Content -Path (Join-Path $LicensesDir "android-sdk-license") -Value "`n24333f8a63b6825ea9c5514f83c2829b004d1fee" -NoNewline

Write-Host "🔧 [3/4] 正在安裝 Android SDK 34 必要編譯元件..." -ForegroundColor Cyan
& $SdkManagerBat --sdk_root="$SdkDir" "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# 3. 檢查或下載 Gradle 8.7
$GradleBat = Join-Path $GradleDir "gradle-8.7\bin\gradle.bat"
if (-not (Test-Path $GradleBat)) {
    Write-Host "🐘 正在下載 Gradle 8.7..." -ForegroundColor Cyan
    $GradleZip = Join-Path $ToolsDir "gradle.zip"
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-8.7-bin.zip" -OutFile $GradleZip
    Expand-Archive -Path $GradleZip -DestinationPath $GradleDir -Force
    Remove-Item $GradleZip -Force
}

Write-Host "🚀 [4/4] 正在打包 APK (assembleDebug)..." -ForegroundColor Green
& $GradleBat assembleDebug

$ApkOut = Join-Path $ProjectDir "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $ApkOut) {
    Write-Host "`n✅ APK 打包成功！" -ForegroundColor Green
    Write-Host "📱 檔案位置: $ApkOut" -ForegroundColor Yellow
}
