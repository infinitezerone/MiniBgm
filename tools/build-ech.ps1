# PowerShell 一键编译 Rust ECH 动态库
$ErrorActionPreference = "Stop"

if (-not $env:ANDROID_NDK_HOME) {
    $ndkBase = "D:\program\AndroidDev\sdk\ndk"
    if (Test-Path $ndkBase) {
        $latestNdk = Get-ChildItem $ndkBase | Where-Object { Test-Path "$($_.FullName)\source.properties" } | Sort-Object Name -Descending | Select-Object -First 1
        if ($latestNdk) {
            $env:ANDROID_NDK_HOME = $latestNdk.FullName
        }
    }
}

if (-not $env:ANDROID_NDK_HOME -or -not (Test-Path $env:ANDROID_NDK_HOME)) {
    Write-Error "❌ 未找到有效 ANDROID_NDK_HOME，请配置 NDK 路径"
    exit 1
}

Write-Host "🦀 使用 NDK: $env:ANDROID_NDK_HOME 编译 libminibgm_ech.so ..." -ForegroundColor Cyan

cargo ndk -t arm64-v8a -t x86_64 --platform 31 -o core/network/src/androidMain/jniLibs build --release -p minibgm-ech

Write-Host "✅ libminibgm_ech.so 编译并同步至 core/network/src/androidMain/jniLibs 完成！" -ForegroundColor Green
