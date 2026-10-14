# tools/quick-check.ps1 -- Fast architecture rules check & Micro-Eval test harness
param(
    [switch]$Format = $false,
    [switch]$Only = $false,
    [string[]]$Modules = @()
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Push-Location $repoRoot
try {
    $gradleArgs = @()
    if ($Format) {
        Write-Host "[quick-check] Adding spotlessApply formatting" -ForegroundColor DarkGray
        $gradleArgs += "spotlessApply"
    }

    if (-not $Only) {
        $gradleArgs += ":core:testing:testAndroidHostTest"
    }

    foreach ($raw in $Modules) {
        $norm = $raw.TrimStart(':')
        $pathDir = $norm.Replace(':', '/')
        $taskPath = ":" + $pathDir.Replace('/', ':')
        $buildFile = Join-Path $repoRoot (Join-Path $pathDir "build.gradle.kts")
        if (Test-Path $buildFile) {
            $content = Get-Content $buildFile -Raw
            if ($content -match "minibgm\.kmp") {
                $gradleArgs += "${taskPath}:testAndroid"
            } else {
                $gradleArgs += "${taskPath}:testDebugUnitTest"
            }
        } else {
            Write-Warning "[quick-check] Build file not found for: $raw"
        }
    }

    if ($gradleArgs.Count -eq 0) {
        Write-Error "[quick-check] No tasks to execute."
        exit 1
    }

    Write-Host "[quick-check] Running quick verification: $($gradleArgs -join ' ')" -ForegroundColor Cyan
    & .\gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[quick-check] Verification failed." -ForegroundColor Red
        exit $LASTEXITCODE
    }

    Write-Host "[quick-check] Quick verification passed! Run tools/jgate before commit." -ForegroundColor Green
    exit 0
} finally {
    Pop-Location
}
