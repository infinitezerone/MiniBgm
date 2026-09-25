#!/usr/bin/env python3
"""
跑一次 Gradle 验证，并把**真实结果**记到画布的验证层里。

为什么需要它：`build/test-results/*.xml` 只能说明「某个时刻跑过」，
回答不了「谁在什么状态下跑了什么、结果如何」。而这个问题的答案只能来自
执行 Gradle 的那一次调用本身，所以必须有人在那一刻把它记下来。

用法:
    python3 tools/codebase-canvas/verify.py :core:testing:testAndroid
    python3 tools/codebase-canvas/verify.py spotlessCheck :core:testing:testAndroid
    python3 tools/codebase-canvas/verify.py --timeout 1800 :app:assembleDebug

记录写到 tools/codebase-canvas/out/validation-log.jsonl（每行一条），
原始输出落到 tools/codebase-canvas/out/validation/<时间戳>.log。

任务级结果的来源是 Gradle `--console=plain` 的输出行：
    > Task :core:testing:testAndroid
    > Task :app:assembleDebug FAILED
    > Task :core:model:testAndroidHostTest UP-TO-DATE
解析的是 Gradle 自己打印的状态，不是猜的。全部产物都被打上「实测」。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from datetime import datetime, timezone

TASK_RE = re.compile(r"^>\s*Task\s+(:[^\s]+)(?:\s+(\S+))?\s*$")
OUTCOME_MARKERS = {"FAILED", "UP-TO-DATE", "FROM-CACHE", "SKIPPED", "NO-SOURCE", "DID-NO-WORK"}


def sh(args: list[str], cwd: str) -> str:
    try:
        return subprocess.run(
            args, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True
        ).stdout.strip()
    except Exception:
        return ""


def main() -> int:
    ap = argparse.ArgumentParser(description="跑 Gradle 验证并记录结果")
    ap.add_argument("tasks", nargs="+", help="Gradle 任务，例如 :core:testing:testAndroid")
    ap.add_argument("--root", default=".", help="工程根目录")
    ap.add_argument("--timeout", type=int, default=3600, help="超时秒数")
    # 认不出的参数原样透传给 Gradle（例如 --rerun、-Pxxx=yyy），
    # 位置放在任务名之后，任务级选项才能生效：gradlew :t --rerun
    ap.add_argument("extra", nargs=argparse.REMAINDER, help="透传给 Gradle 的参数")
    args = ap.parse_args()
    extra = [a for a in args.extra if a != "--"]

    root = os.path.abspath(args.root)
    out_dir = os.path.join(root, "tools", "codebase-canvas", "out")
    log_dir = os.path.join(out_dir, "validation")
    # 防路径穿越：输出目录固定落在工程根目录之内，避免 root 异常时写到仓库之外
    try:
        escapes_root = os.path.commonpath([os.path.abspath(log_dir), root]) != root
    except ValueError:  # Windows 跨盘符时 commonpath 直接抛错
        escapes_root = True
    if escapes_root:
        ap.error(f"输出目录必须位于工程根目录 {root} 内: {log_dir}")
    os.makedirs(log_dir, exist_ok=True)

    started = time.time()
    stamp = datetime.now().astimezone().strftime("%Y%m%d-%H%M%S")
    raw_path = os.path.join(log_dir, f"{stamp}.log")
    cmd = ["./gradlew", "--console=plain", *args.tasks, *extra]

    head = sh(["git", "rev-parse", "--short", "HEAD"], root)
    branch = sh(["git", "rev-parse", "--abbrev-ref", "HEAD"], root)
    dirty = sh(["git", "status", "--porcelain"], root)
    dirty_files = [l for l in dirty.splitlines() if l.strip()]

    print(f"[verify] {' '.join(cmd)}", file=sys.stderr)
    print(f"[verify] HEAD {head} · 工作区 {len(dirty_files)} 个未提交文件", file=sys.stderr)

    lines: list[str] = []
    timed_out = False
    try:
        proc = subprocess.Popen(
            cmd, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, bufsize=1,
        )
        assert proc.stdout is not None
        for line in proc.stdout:
            lines.append(line.rstrip("\n"))
            print(line, end="", flush=True)
            if time.time() - started > args.timeout:
                timed_out = True
                proc.kill()
                break
        exit_code = proc.wait()
    except FileNotFoundError:
        print("[verify] 找不到 ./gradlew", file=sys.stderr)
        return 2

    duration = round(time.time() - started, 1)
    elapsed_ok = not timed_out

    # 任务级结果
    tasks: list[dict] = []
    seen: set[str] = set()
    for line in lines:
        m = TASK_RE.match(line.strip())
        if not m:
            continue
        name, marker = m.group(1), (m.group(2) or "").upper()
        outcome = marker if marker in OUTCOME_MARKERS else "EXECUTED"
        if name in seen:
            continue
        seen.add(name)
        tasks.append({"name": name, "outcome": outcome})

    build_line = next(
        (l for l in reversed(lines) if l.startswith("BUILD SUCCESSFUL") or l.startswith("BUILD FAILED")),
        "",
    )
    ok = exit_code == 0 and build_line.startswith("BUILD SUCCESSFUL")

    with open(raw_path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")

    record = {
        "at": datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
        "command": " ".join(cmd),
        "requested": args.tasks + extra,
        "exitCode": exit_code,
        "ok": ok,
        "timedOut": timed_out,
        "durationSec": duration,
        "buildLine": build_line,
        "head": head,
        "branch": branch,
        "dirtyFiles": len(dirty_files),
        "dirtyPaths": [l[3:] for l in dirty_files][:60],
        "tasks": tasks,
        "logFile": os.path.relpath(raw_path, root),
    }

    log_path = os.path.join(out_dir, "validation-log.jsonl")
    with open(log_path, "a", encoding="utf-8") as fh:
        fh.write(json.dumps(record, ensure_ascii=False) + "\n")

    icon = "✓" if ok else "✗"
    print(f"\n[verify] {icon} {build_line or ('timeout' if timed_out else 'no BUILD line')}", file=sys.stderr)
    print(f"[verify] {len(tasks)} 个任务 · 耗时 {duration}s", file=sys.stderr)
    for t in tasks:
        print(f"         {t['outcome']:<12} {t['name']}", file=sys.stderr)
    print(f"[verify] 已记入 {os.path.relpath(log_path, root)}", file=sys.stderr)
    print("[verify] 重新跑 scan.py 即可在画布上看到这次验证", file=sys.stderr)
    return 0 if ok and elapsed_ok else 1


if __name__ == "__main__":
    sys.exit(main())
