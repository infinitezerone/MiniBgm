#!/usr/bin/env bash
# 新机器/新 clone 一次性接线：把本 clone 的 git 钩子指向仓库内版本。
# jj 的操作不运行 git 钩子，此接线只影响直连 git 的写路径（人 / AI / 间接脚本）。
cd "$(dirname "$0")/../.." || exit 1
git config core.hooksPath tools/githooks && echo "core.hooksPath → tools/githooks ✓"
