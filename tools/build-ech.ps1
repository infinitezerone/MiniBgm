# 兼容别名入口：转发至统一的独立原生构建脚本 tools/build-native.ps1
$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "build-native.ps1") ech @args
exit $LASTEXITCODE
