# tools/jgate.ps1 -- Windows PowerShell launcher for native Rust jgate CLI
$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Push-Location $repoRoot
try {
    $bin = Join-Path $repoRoot "target\release\jgate.exe"
    $needBuild = $false

    if (-not (Test-Path $bin)) {
        $needBuild = $true
    } else {
        $srcFiles = Get-ChildItem -Path (Join-Path $repoRoot "crates\jgate-cli") -Recurse -File -ErrorAction SilentlyContinue
        $binTime = (Get-Item $bin).LastWriteTime
        foreach ($f in $srcFiles) {
            if ($f.LastWriteTime -gt $binTime) {
                $needBuild = $true
                break
            }
        }
    }

    if ($needBuild) {
        Write-Host "[jgate.ps1] Building native jgate CLI..." -ForegroundColor Cyan
        cargo build --release -p jgate --quiet
        if ($LASTEXITCODE -ne 0) {
            Write-Error "[jgate.ps1] Failed to compile jgate CLI."
            exit $LASTEXITCODE
        }
    }

    & $bin @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
