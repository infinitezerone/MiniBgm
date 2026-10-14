# tools/quick-check.ps1 -- 快速自检微验证 (Windows PowerShell，转调 jgate.ps1 quick)
$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Push-Location $repoRoot
try {
    & (Join-Path $PSScriptRoot "jgate.ps1") quick @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
