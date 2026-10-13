# tools/build-native.ps1 -- Windows PowerShell wrapper for tools/build-native
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$bashExe = $null
$cmd = Get-Command bash -ErrorAction SilentlyContinue
if ($cmd -and $cmd.Source) {
    $bashExe = $cmd.Source
}
if (-not $bashExe) {
    $scoopGitBash = "D:\Applications\Scoop\apps\git\current\bin\bash.exe"
    if (Test-Path $scoopGitBash) {
        $bashExe = $scoopGitBash
    }
}
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
    Write-Error "[build-native.ps1] bash.exe not found. Please ensure Git for Windows is installed or in PATH."
    exit 1
}

Push-Location $repoRoot
try {
    & $bashExe tools/build-native @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
