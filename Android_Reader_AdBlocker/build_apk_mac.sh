#!/usr/bin/env bash
# ==============================================================================
# macOS 一鍵安裝輕量編譯環境並打包 Android APK 腳本 (免安裝完整 Android Studio)
# 適用：Apple Silicon (M1/M2/M3/M4) 與 Intel Mac
# ==============================================================================
set -e

echo "🍎 [1/4] 檢查 Homebrew..."
if ! command -v brew &> /dev/null; then
    echo "請先安裝 Homebrew: https://brew.sh/"
    exit 1
fi

echo "☕ [2/4] 檢查並安裝 OpenJDK 17、Gradle 與 Android Command-Line Tools..."
brew install openjdk@17 gradle
brew install --cask android-commandlinetools

export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/Library/Android/sdk"
mkdir -p "$ANDROID_HOME"

# 自動定位 sdkmanager 路徑
SDKMANAGER_BIN="$(brew --prefix)/share/android-commandlinetools/cmdline-tools/latest/bin/sdkmanager"
if [ ! -f "$SDKMANAGER_BIN" ]; then
    SDKMANAGER_BIN="$(which sdkmanager)"
fi

echo "📦 [3/4] 自動同意授權並下載 Android 34 最小編譯套件..."
yes | "$SDKMANAGER_BIN" --sdk_root="$ANDROID_HOME" --licenses > /dev/null || true
"$SDKMANAGER_BIN" --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-34" "build-tools;34.0.0"

echo "🚀 [4/4] 開始編譯 APK..."
gradle assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "✅ 編譯完成！"
echo "📱 APK 安裝檔位置：$APK_PATH"
echo "💡 如果已用 USB 連接 Android 手機，可執行以下指令直接安裝到手機："
echo "   $ANDROID_HOME/platform-tools/adb install -r $APK_PATH"
