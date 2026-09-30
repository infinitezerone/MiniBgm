# libminibgm_ech.so 的唯一真源是 Gradle 任务 :core:network:buildEchNative
# （NDK 解析、ABI 集合、产物指纹校验都在那里，见 EchNativePlugin）。
# 本脚本只作为"不记得任务名"的便捷入口，避免出现第二份 ABI/NDK 实现。
$ErrorActionPreference = "Stop"

Push-Location (Join-Path $PSScriptRoot "..")
try {
    & .\gradlew.bat :core:network:buildEchNative @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
