#!/usr/bin/env node
// 项目门禁（Bash PreToolUse）：机械执行 AGENTS.md 中"可机械化"的规则。
// 输入：stdin JSON { tool_name, tool_input: { command } }；exit 0 = 放行，exit 2 = 拦截（stderr 为理由）。
// 规则新增/修改须同步 AGENTS.md 的索引行，反之亦然。
import { readFileSync } from "node:fs";

let cmd = "";
try {
  const input = JSON.parse(readFileSync(0, "utf8"));
  cmd = input?.tool_input?.command ?? "";
} catch {
  process.exit(0); // 解析失败不拦，交由其他门禁（Mimosa/CI）
}
if (typeof cmd !== "string" || cmd.length === 0) process.exit(0);

const deny = (msg) => {
  console.error(`[project-gate] ${msg}`);
  process.exit(2);
};

// 1) git 变更类子命令：本仓库与 jj colocate，历史操作只允许 jj。
//    只读 git（log/diff/show/ls-remote/tag/clone/fetch）不受限；git push 一并拦截，
//    推送统一走 jj git push（tag 推送例外，见下）。
const gitMutation = cmd.match(/\bgit\s+(commit|push|rebase|reset|checkout|switch|merge|revert|cherry-pick|restore|clean|rm|stash|am|apply)\b/);
if (gitMutation) {
  const isTagPush = /\bgit\s+push\b/.test(cmd) && /--tags\b/.test(cmd) && !/\bgit\s+push\b[^&;|]*\brefs\/heads\b/.test(cmd);
  if (!isTagPush) {
    deny(
      `检测到 git ${gitMutation[1]}：本仓库与 jj colocate，git 写命令会绕过 jj 操作日志。` +
        `历史操作用 jj（jj commit / jj git push --bookmark main）；tag 允许 git tag 与 git push --tags。`,
    );
  }
}

// 2) jj commit/describe 必须带 -m 且摘要符合 Conventional Commits。
//    无 -m 会打开交互编辑器（agent 环境直接挂起）；摘要格式由钩子强制而非 prose。
if (/\bjj\s+(commit|describe)\b/.test(cmd)) {
  const m = cmd.match(/(?:^|\s)(?:-m|--message)\s+(?:"((?:[^"\\]|\\.)*)"|'([^']*)'|(\S+))/);
  if (!m) {
    deny("jj commit/describe 必须带 -m：<类型>(<模块>): <摘要>。无 -m 会打开交互编辑器并挂起。");
  }
  const msg = (m[1] ?? m[2] ?? m[3] ?? "").trim();
  if (!/^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([^)]*\))?!?:\s\S/.test(msg)) {
    deny(`提交摘要须为 Conventional Commits（如 feat(core:ai): xxx / chore: xxx），实际收到："${msg}"`);
  }
}

process.exit(0);
