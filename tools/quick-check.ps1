# tools/quick-check.ps1 -- Fast architecture rules check for development iterations
param(
    [switch]$Format = $false
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Push-Location $repoRoot
try {
    Write-Host "[quick-check] Running architecture rules check..." -ForegroundColor Cyan
    
    $gradleArgs = @(":core:testing:testAndroidHostTest")
    if ($Format) {
        Write-Host "[quick-check] Adding spotlessApply formatting" -ForegroundColor DarkGray
        $gradleArgs = @("spotlessApply") + $gradleArgs
    }
    
    & .\gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[quick-check] Architecture rules check failed." -ForegroundColor Red
        exit $LASTEXITCODE
    }
    
    Write-Host "[quick-check] Architecture rules passed! Run tools/jgate before commit." -ForegroundColor Green
    exit 0
} finally {
    Pop-Location
}
