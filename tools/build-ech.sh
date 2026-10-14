#!/usr/bin/env bash
# tools/build-ech.sh —— 仅编译 ECH 原生库（转调 jgate native ech）
set -euo pipefail
cd "$(dirname "$0")/.."
exec bash tools/jgate native ech "$@"
