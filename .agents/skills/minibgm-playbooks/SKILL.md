---
name: minibgm-playbooks
description: >-
  MiniBgm 架构实施剧本库（按需读，不要通读）。当在 MiniBgm 中开发新特性、构建 UI、重构数据流，
  或涉及播放器会话/线路面板/弹幕/手势、种子与下载链路、图床与富文本、DI 与调试面板、订阅规则导入时，
  先按下方路由表打开 1-2 个 references/playbook-NN-*.md 再动手。
---

# MiniBgm 架构实施剧本库（Playbooks）

本项目遵循仿 Google *Now in Android* 的 Clean Architecture。所有开发动作**严禁自创模式**，必须机械对齐下列规范先例。

## 用法：渐进披露，别通读

1. 按任务在路由表里命中 **1-2 个**剧本文件，只读命中的那些；
2. references/ 下剧本有几十个文件、合计数万字符，通读会挤爆上下文——**没命中的一律不读**；
3. 剧本讲「怎么做」；可执行的硬约束在 `:core:testing` 的 `ArchitectureRulesTest`（测试名即规则名），事故复盘写在各测试的 KDoc；
4. 新增/修改规则前先看飞轮技能 `minibgm-flywheel`：**同一句规则写第二遍就该机械化，而不是再写一段文字**。

## 路由表

| 任务 | 读这些 |
|---|---|
| 新页面 / 只读列表 / 带参详情分页 / 写操作提案卡 | `references/playbook-01-read-only-features.md`、`references/playbook-02-detail-pagination.md`、`references/playbook-03-write-operations.md` |
| 弹窗 / 抽屉 / 一次性事件 / 分集抽屉与线路面板 | `references/playbook-04-transient-ui.md`、`references/playbook-14-unified-episode-drawer.md` |
| 提交前的验证与闭环（改完怎么自证） | `references/playbook-06-verification-workflow.md` |
| 模式级暗礁（跨模块通用坑，拿不准先读它） | `references/playbook-07-pattern-pitfalls.md` |
| 原生流播 / librqbit / JNI / 按需 .so / 下载落盘 / 种子卡片 | `references/playbook-08a-stream-fidelity.md`、`references/playbook-08b-native-plugin-jni.md`、`references/playbook-08c-magnet-interaction.md`、`references/playbook-12-asset-download-mutex.md`、`references/playbook-18-torrent-card-cache.md`、`references/playbook-11-p2p-neutrality.md` |
| DI 契约 / 开发面板与运行时调试 | `references/playbook-09-di-fidelity.md`、`references/playbook-13-developer-panel.md` |
| WebView 与本地代理 / 图床与富文本 | `references/playbook-10-webview-proxy.md`、`references/playbook-17-image-routing.md` |
| 播放器手势 / 进度条 / HUD 稳定性 | `references/playbook-16-player-gestures.md` |
| 播放源订阅与规则导入 | `references/playbook-15-subscription-integrity.md` |
| 装饰性功能 fail-open / 硬件位图安全 | `references/playbook-05-fail-open-decoration.md` |
| 现代 Modifier.Node / 自定义微交互修饰符 / 水生动效与触控反馈 | `references/playbook-19-modifier-node-architecture.md` |

## 跨剧本铁律

- **先查红线再写代码**：动手前 grep `ArchitectureRulesTest` 里与本任务相关的测试名，它会告诉你边界在哪。
- **小步自证**：每个中间态用 `.\tools\quick-check.ps1`（等价 `jgate quick`）秒级自查，别攒到最后。
- **一个提交一个目的**：同一文件别混两个目的——`jj split` 拆不出可编译的中间态。
- **能机械化就不写文字**：重复第二次的指令要变成 lint / 测试 / 构建期检查 / 脚本（见 `minibgm-flywheel`）。
