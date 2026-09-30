#!/usr/bin/env bash
set -euo pipefail

# 自动推断并配置 NDK 路径
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    SDK_DIR="${ANDROID_HOME:-/d/program/AndroidDev/sdk}"
    if [ -d "$SDK_DIR/ndk" ]; then
        # 优先使用版本 29+，兜底取最高可用版本
        LATEST_NDK=$(find "$SDK_DIR/ndk" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -n 1)
        export ANDROID_NDK_HOME="$LATEST_NDK"
    fi
fi

if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo "❌ 未检测到有效 ANDROID_NDK_HOME，请先配置 NDK 路径"
    exit 1
fi

echo "🦀 使用 NDK: $ANDROID_NDK_HOME 编译 libminibgm_ech.so ..."

# 为主流真实设备 (arm64-v8a) 与模拟器 (x86_64) 双架构编译 Release 产物
cargo ndk -t arm64-v8a -t x86_64 --platform 31 -o core/network/src/androidMain/jniLibs build --release -p minibgm-ech

echo "✅ libminibgm_ech.so 编译并同步至 core/network/src/androidMain/jniLibs 完成！"
