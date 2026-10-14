# tools/build-ech.ps1 -- 仅编译 ECH 原生库 (Windows PowerShell)
$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Push-Location $repoRoot
try {
    & (Join-Path $PSScriptRoot "jgate.ps1") native ech @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
