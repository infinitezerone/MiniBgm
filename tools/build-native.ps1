# tools/build-native.ps1 -- Windows PowerShell wrapper forwarding to jgate native
$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Push-Location $repoRoot
try {
    & (Join-Path $PSScriptRoot "jgate.ps1") native @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
