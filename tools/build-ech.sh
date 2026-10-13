#!/usr/bin/env bash
set -euo pipefail

# 兼容别名入口：转发至统一的独立原生构建脚本 tools/build-native
cd "$(dirname "$0")/.."
exec bash tools/build-native ech "$@"
