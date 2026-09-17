#!/usr/bin/env bash
# 跑一次 Gradle 验证并把真实结果记进画布的验证层。
#
#   ./tools/codebase-canvas/verify.sh :core:testing:testAndroid
#   ./tools/codebase-canvas/verify.sh spotlessCheck :core:testing:testAndroid
#
# 完成后执行 python3 tools/codebase-canvas/scan.py . 刷新画布数据。
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
exec python3 "$ROOT/tools/codebase-canvas/verify.py" --root "$ROOT" "$@"
