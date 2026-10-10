# tools/jgate.ps1 —— Windows PowerShell 下便捷运行 jgate 门禁
# 自动探测 Scoop / Git for Windows 的 bash.exe 并透传参数
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

# 1. 探测 bash 可执行文件路径
$bashExe = $null

# 优先 PATH 中的 bash
$cmd = Get-Command bash -ErrorAction SilentlyContinue
if ($cmd -and $cmd.Source) {
    $bashExe = $cmd.Source
}

# 其次探测 Scoop Git
if (-not $bashExe) {
    $scoopGitBash = "D:\Applications\Scoop\apps\git\current\bin\bash.exe"
    if (Test-Path $scoopGitBash) {
        $bashExe = $scoopGitBash
    }
}

# 再次探测常见 Git 安装路径
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
    Write-Error "[jgate.ps1] ❌ 未在系统中检测到 bash.exe。请确保已安装 Git for Windows 或将其加入 PATH。"
    exit 1
}

# 2. 执行 tools/jgate
Push-Location $repoRoot
try {
    & $bashExe tools/jgate @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
