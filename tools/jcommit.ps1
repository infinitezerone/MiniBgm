# tools/jcommit.ps1 -- Windows PowerShell wrapper enforcing jgate before jj commit
$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Push-Location $repoRoot
try {
    Write-Host "[jcommit.ps1] 正在通过 jgate 验证当前工作副本..." -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot "jgate.ps1")
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[jcommit.ps1] 门禁未通过，中止提交。"
        exit $LASTEXITCODE
    }
    Write-Host "[jcommit.ps1] 门禁全绿，准备提交..." -ForegroundColor Green
    jj commit @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
