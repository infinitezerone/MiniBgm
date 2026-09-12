#!/usr/bin/env bash
# 双窗口尺寸动态验证门：在手机（Compact）与平板（Expanded）两种窗口形态下
# 安装并启动应用，校验无崩溃、主界面完成渲染，并留存证据截图。
#
# 用法（需先 ./gradlew :app:assembleDebug 并连接/启动模拟器）：
#   scripts/dual-screen-verify.sh [apk路径] [设备序列号]
#
# 对应 AGENTS.md「验证与发布」中的人工/agent 动态验证门；
# 截图命名与目录可直接交给多模态评审或人工复核。
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
SERIAL="${2:-}"
APP_ID="com.infinitezerone.minibgm"
ACTIVITY="com.infinitezerone.minibgm/.MainActivity"

ADB="adb"
[ -n "$SERIAL" ] && ADB="adb -s $SERIAL"

OUT_ROOT="build/verification"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="$OUT_ROOT/$STAMP"
mkdir -p "$OUT_DIR"

# 声明验证矩阵：名称|宽x高|密度dpi
# phone   : 1080x2340 @ 420dpi -> ~411dp 宽（Compact，单栏）
# expanded: 1920x1200 @ 240dpi -> 1280dp 宽（Expanded，ListDetail 分栏）
PROFILES=("phone|1080x2340|420" "expanded|1920x1200|240")

png_magic_ok() {
  # PNG 魔数校验：模拟器多显示器的 screencap 警告会污染 stdout（见 AGENTS.md 红线 5）
  [ "$(od -An -tx1 -N8 "$1" | tr -d ' \n')" = "89504e470d0a1a0a" ]
}

ORIG_SIZE="$($ADB shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1)"
ORIG_DENSITY="$($ADB shell wm density | grep -o '[0-9]*' | tail -1)"
echo "== 原始屏幕参数: $ORIG_SIZE @ $ORIG_DENSITYdpi（结束后恢复） =="

restore() {
  $ADB shell wm size reset >/dev/null 2>&1 || true
  $ADB shell wm density reset >/dev/null 2>&1 || true
}
trap restore EXIT

FAILED=0
for profile in "${PROFILES[@]}"; do
  IFS='|' read -r name size density <<<"$profile"
  echo "== [$name] 设置窗口 $size @ ${density}dpi =="
  $ADB shell wm size "$size" >/dev/null
  $ADB shell wm density "$density" >/dev/null
  sleep 2

  if [ -f "$APK" ]; then
    echo "== [$name] 安装 APK =="
    $ADB install -r "$APK" >/dev/null
  fi

  $ADB logcat -c
  $ADB shell am force-stop "$APP_ID"
  sleep 1
  $ADB shell am start -W -n "$ACTIVITY" >/dev/null
  # 冷启动 + 首帧渲染 + 骨架屏加载
  sleep 6

  shot="$OUT_DIR/$name-home.png"
  # 先落盘到设备再 pull，避免 exec-out 输出流污染（AGENTS.md 红线 5）
  $ADB shell screencap -p /data/local/tmp/minibgm-verify.png
  $ADB pull /data/local/tmp/minibgm-verify.png "$shot" >/dev/null
  $ADB shell rm /data/local/tmp/minibgm-verify.png

  if ! png_magic_ok "$shot"; then
    echo "✗ [$name] 截图不是有效 PNG：$shot"
    FAILED=1
    continue
  fi

  # 渲染断言：uiautomator dump 中必须存在本包名的界面节点
  $ADB shell uiautomator dump /data/local/tmp/minibgm-verify.xml >/dev/null 2>&1
  $ADB pull /data/local/tmp/minibgm-verify.xml "$OUT_DIR/$name-ui.xml" >/dev/null 2>&1 || true
  $ADB shell rm -f /data/local/tmp/minibgm-verify.xml
  if ! grep -q "$APP_ID" "$OUT_DIR/$name-ui.xml" 2>/dev/null; then
    echo "✗ [$name] 未检测到 $APP_ID 的界面节点（应用未渲染或崩溃弹窗拦截）"
    FAILED=1
    continue
  fi

  # 崩溃断言：crash 缓冲区内不得出现本包名的 AndroidRuntime 崩溃
  if $ADB logcat -d -b crash 2>/dev/null | grep -q "$APP_ID"; then
    echo "✗ [$name] crash 缓冲区检出 $APP_ID 崩溃："
    $ADB logcat -d -b crash | grep -A 5 "$APP_ID" | head -20 | tee "$OUT_DIR/$name-crash.log"
    FAILED=1
    continue
  fi

  echo "✓ [$name] 通过：$shot"
done

if [ "$FAILED" -ne 0 ]; then
  echo "验证失败，证据目录：$OUT_DIR"
  exit 1
fi
echo "双尺寸验证通过，证据目录：$OUT_DIR"
