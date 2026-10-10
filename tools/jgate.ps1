# tools/jgate.ps1 -- Windows PowerShell wrapper for tools/jgate
# Automatically detects bash.exe from Scoop or Git for Windows and forwards arguments.
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

# 1. Detect bash executable
$bashExe = $null

# Priority 1: bash in PATH
$cmd = Get-Command bash -ErrorAction SilentlyContinue
if ($cmd -and $cmd.Source) {
    $bashExe = $cmd.Source
}

# Priority 2: Scoop Git
if (-not $bashExe) {
    $scoopGitBash = "D:\Applications\Scoop\apps\git\current\bin\bash.exe"
    if (Test-Path $scoopGitBash) {
        $bashExe = $scoopGitBash
    }
}

# Priority 3: Common Git install paths
if (-not $bashExe) {
    $candidates = @(
        "$env:LOCALAPPDATA\Programs\Git\bin\bash.exe",
        "$env:ProgramFiles\Git\bin\bash.exe",
        "${env:ProgramFiles(x86)}\Git\bin\bash.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) {
            $bashExe = $c
            break
        }
    }
}

if (-not $bashExe) {
    Write-Error "[jgate.ps1] bash.exe not found. Please ensure Git for Windows is installed or in PATH."
    exit 1
}

# 2. Run tools/jgate
Push-Location $repoRoot
try {
    & $bashExe tools/jgate @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
