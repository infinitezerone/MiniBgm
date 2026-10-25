---
name: minibgm-flywheel
description: >-
  MiniBgm 经验沉淀与工程飞轮元技能（Flywheel Meta-Skill）。
  当同一条指令要写第二遍、同一个人工纠正再次出现、同类缺陷换个模块又犯，
  或攻克了隐蔽缺陷、确立了新范式、重构了反模式时，用它把教训升级成
  lint / 测试 / 构建期检查 / 脚本，而不是再写一段文字。
---

# MiniBgm 工程飞轮（Encode Lessons in Structure）

> "Encode recurring fixes in mechanisms (tools, code, metadata, automation) instead of textual instructions."
> —— Lauren Tan (poteto) 的 pstack skill `principle-encode-lessons-in-structure`（Matt Pocock 访谈里也讲过同一套）

## 一、什么时候必须机械化（触发判据）

命中任意一条，就**不要再写解释性文字**，直接升级成机制：

1. **第二次写同一条指令**（原始判据）：同一句「必须…／严禁…」写到第二遍；
2. **同一个人工纠正出现第二次**（review 反复指出同一个问题）；
3. **同类事故复发**（同一个 bug 换了个模块又犯一次）；
4. 排查到非直观的隐蔽缺陷（真机与单测不一致、竞态、状态边界缺依据）；
5. 确立新范式或重构反模式。

反面判据：只出现一次、且无法自动判定的取舍（业务方向、命名偏好），**不要**机械化——留一句结论即可。

## 二、手段优先级（越靠前越好）

1. **静态检查 / 测试**：`:core:testing` 的 `ArchitectureRulesTest`（源码扫描、反射、契约断言）；
2. **构建期检查**：Gradle 任务输入声明、依赖图断言、门禁脚本 `tools/jgate`；
3. **运行时检查**：断言、显式失败态、状态机守卫；
4. **元数据标记**：`@StringRes`、`@VisibleForTesting`、注解、`native/abis.txt` 这类单一真源文件；
5. **可执行自述**：`--help` / `--plan` / 自愈诊断卡；
6. **文字**（最后手段）：只允许一句索引 + 指向上面某个执行体，且受 `AGENTS.md` 预算约束（预算常量见 `agents_doc_must_stay_an_index_under_context_budget`，加字必须先删等量）。

## 三、落地位置

- 硬约束 → `ArchitectureRulesTest`（测试名即规则名，KDoc 写复盘）
- 全局一句话 → `AGENTS.md`（预算守卫见 `agents_doc_must_stay_an_index_under_context_budget`）
- 操作模版 → `minibgm-playbooks/references/` 下**对应的那一个** `playbook-NN-*.md`（不要往单一 SKILL.md 里堆）
- 门禁语义 → `crates/jgate-cli` 的 `--help`（改行为必须同 commit 改 help）

## 四、交付前自检

- [ ] 这条教训三个月后的另一个 Agent 会再踩一遍吗？
- [ ] 会给它加什么执行体（测试 / 构建期 / 运行时 / 元数据 / 自述）？
- [ ] 是否只留了一句索引，而不是又一段叙述？
- [ ] `tools/jgate.ps1` 全绿并生成真实测试凭据（不是 UP-TO-DATE 假绿）？
