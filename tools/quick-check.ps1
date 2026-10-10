# tools/quick-check.ps1 —— Agent 轻量级快速自检（小手术刀）
# 仅执行：17 条架构红线自动化断言（:core:testing:testAndroidHostTest）
# 专为开发中间态设计：秒级反馈，不启动庞大的 app:assembleDebug，便于频繁自检
param(
    [switch]$Format = $false # 传 -Format 时执行 spotlessApply 自动格式化
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Push-Location $repoRoot
try {
    Write-Host "[quick-check] 🔍 启动快速自检..." -ForegroundColor Cyan
    
    $gradleArgs = @(":core:testing:testAndroidHostTest")
    if ($Format) {
        Write-Host "[quick-check] 🎨 附加 spotlessApply 代码格式化" -ForegroundColor DarkGray
        $gradleArgs = @("spotlessApply") + $gradleArgs
    }
    
    & .\gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[quick-check] ❌ 红线自检未通过，请根据堆栈修正架构违规。" -ForegroundColor Red
        exit $LASTEXITCODE
    }
    
    Write-Host "[quick-check] ✅ 架构红线全绿！可以放心继续实现或运行 tools/jgate 进行最终门禁。" -ForegroundColor Green
    exit 0
} finally {
    Pop-Location
}
