# tools/jcommit.ps1 -- Windows PowerShell wrapper enforcing jgate before jj commit
$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Push-Location $repoRoot
try {
    Write-Host "[jcommit.ps1] Verifying working copy with jgate..." -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot "jgate.ps1")
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[jcommit.ps1] Gate check failed, aborting commit."
        exit $LASTEXITCODE
    }
    Write-Host "[jcommit.ps1] Gate passed, committing..." -ForegroundColor Green
    jj commit @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
