#!/usr/bin/env bash
# tools/quick-check.sh —— Agent 轻量级快速自检与 Micro-Eval（转调 jgate quick）
set -euo pipefail
cd "$(dirname "$0")/.."
exec bash tools/jgate quick "$@"
