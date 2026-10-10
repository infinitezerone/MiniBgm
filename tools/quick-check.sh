#!/usr/bin/env bash
# tools/quick-check.sh —— Agent 轻量级快速自检（小手术刀）
# 仅执行：17 条架构红线自动化断言（:core:testing:testAndroidHostTest）
# 专为开发中间态设计：秒级反馈，不启动庞大的 app:assembleDebug
set -euo pipefail
cd "$(dirname "$0")/.."

format=0
if [ "${1:-}" = "-f" ] || [ "${1:-}" = "--format" ]; then
  format=1
fi

echo "[quick-check] 🔍 启动快速自检..."
tasks=(":core:testing:testAndroidHostTest")
if [ "$format" = "1" ]; then
  echo "[quick-check] 🎨 附加 spotlessApply 代码格式化"
  tasks=("spotlessApply" "${tasks[@]}")
fi

./gradlew "${tasks[@]}"
echo "[quick-check] ✅ 架构红线全绿！"
