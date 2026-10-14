#!/usr/bin/env bash
# tools/quick-check.sh —— Agent 轻量级快速自检与 Micro-Eval 测试鞍具
# 默认执行：20+ 条架构红线自动化断言（:core:testing:testAndroidHostTest）
# 支持指定模块：bash tools/quick-check.sh core/network
# 专为开发中间态设计：秒级反馈，不启动庞大的 app:assembleDebug
set -euo pipefail
cd "$(dirname "$0")/.."

usage() {
  cat <<'USAGE'
用法：bash tools/quick-check.sh [选项] [模块...]

选项：
  -f, --format     附加 spotlessApply 代码格式化
  -o, --only       仅执行指定模块的单测，跳过架构红线（极限秒级反馈）
  -h, --help       显示帮助

示例：
  bash tools/quick-check.sh                      # 秒级红线自查
  bash tools/quick-check.sh core/network         # 红线 + network 模块测试
  bash tools/quick-check.sh -f feature/search    # 格式化 + 红线 + search 模块测试
  bash tools/quick-check.sh -o core/data         # 仅测 data 模块
USAGE
}

format=0
only_target=0
modules=()

while [ "$#" -gt 0 ]; do
  case "$1" in
    -f|--format) format=1 ;;
    -o|--only) only_target=1 ;;
    -h|--help) usage; exit 0 ;;
    --) shift; while [ "$#" -gt 0 ]; do modules+=("$1"); shift; done; break ;;
    -*) echo "[quick-check] ❌ 未知选项：$1" >&2; usage; exit 2 ;;
    *) modules+=("$1") ;;
  esac
  shift
done

tasks=()
if [ "$format" = "1" ]; then
  echo "[quick-check] 🎨 附加 spotlessApply 代码格式化"
  tasks+=("spotlessApply")
fi

if [ "$only_target" = "0" ]; then
  tasks+=(":core:testing:testAndroidHostTest")
fi

for raw in "${modules[@]+"${modules[@]}"}"; do
  norm="${raw#:}"
  path_dir="${norm//://}"
  task_path=":${path_dir//\//:}"
  build_file="$path_dir/build.gradle.kts"
  if [ -f "$build_file" ]; then
    if grep -q "minibgm.kmp" "$build_file"; then
      tasks+=("${task_path}:testAndroid")
    else
      tasks+=("${task_path}:testDebugUnitTest")
    fi
  else
    echo "[quick-check] ⚠️ 未找到模块构建文件：$build_file" >&2
  fi
done

if [ "${#tasks[@]}" -eq 0 ]; then
  echo "[quick-check] ❌ 无任务可执行" >&2
  exit 1
fi

echo "[quick-check] 🔍 启动快速自检/微验证：${tasks[*]}"
./gradlew "${tasks[@]}"
echo "[quick-check] ✅ 快速验证全绿！"
