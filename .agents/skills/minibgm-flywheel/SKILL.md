---
name: minibgm-flywheel
description: >-
  MiniBgm 经验沉淀与工程飞轮元技能（Flywheel Meta-Skill）。
  当在开发中攻克了隐蔽缺陷、排查了非平凡事故、确立了新设计范式或重构了反模式时，
  指导 Agent 遵循“教训编码进结构”的原则，自动执行三位一体的知识固化，让项目能力持续积累。
---

# MiniBgm 工程飞轮指南（Encode Lessons in Structure）

> “不要把踩坑经验留在对话里，要把教训编码到结构中。” —— Lauren Tan (poteto) & Matt Pocock

在 MiniBgm 的开发过程中，每次解决了一个复杂的非平凡问题，任务的终点**不是仅仅把代码改好**，而是触发**工程飞轮（Flywheel）**将知识永久沉淀。

---

## 一、何时触发飞轮？

当遇到以下任意一种情况时，必须触发经验沉淀：
1. **排查到非直观隐蔽缺陷**：例如真机与单测表现不一致（如 P-002 硬件位图崩溃）、多数据源竞态（如 P-001 列表跳动）、状态边界缺依据（如 P-003 分集断言）；
2. **确立了新的架构范式**：例如完成了对某个旧模式的重构（如废除 `loadAllEpisodes`，确立按需分页标准）；
3. **发现了高频易犯的架构坏味道**：例如某些依赖容易被漏掉或写错、生命周期易泄露。

---

## 二、三位一体固化协议（The 3-Step Distillation Protocol）

触发飞轮时，Agent 必须按以下顺序同步更新三个层级的资产：

### 第 1 步：问题档案固化（Reasoning & Evidence）
* **目标文件**：`docs/PROBLEM_LOG.md`
* **记录格式**：严格遵守「编号 · 标题 ➔ 背景 ➔ 假设 ➔ 证据 ➔ 结论与决策 ➔ 状态」结构。
* **准则**：不记流水账（做了什么看 VCS log），只记录“当时踩了什么坑、推翻了什么假设、为什么最终这么改”。

### 第 2 步：规则与自动化门禁固化（Rule & Invariant）
* **目标文件 1**：`AGENTS.md`
  * 若该教训具有全局约束力（“删了会改变行为”），以一两句话精炼地追加到 `AGENTS.md` 的《硬红线》或《领域规则》中。
* **目标文件 2**：`:core:testing:ArchitectureRulesTest`
  * 若该约束可以通过静态扫描或反射机械断言，立即在 `ArchitectureRulesTest.kt` 中追加对应的单测方法，把软性规则变成物理红线。

### 第 3 步：操作剧本固化（Playbook）
* **目标文件**：`.agents/skills/minibgm-playbooks/SKILL.md`
  * 若属于开发实现中的高频模版（如新的组件用法、新的分页状态机），更新或追加进 Playbook，使后续开发能直接“照方抓药”。

---

## 三、飞轮检查清单（Pre-Delivery Checklist）

完成一项重大任务时，Agent 自检问句：
- [ ] 这次排查的教训，三个月后的另一个 Agent 还会重新踩一遍吗？
- [ ] 如果会，我是否已经在 `docs/PROBLEM_LOG.md` 记录了因果？
- [ ] 我是否把操作指引沉淀到了 `minibgm-playbooks`？
- [ ] 门禁 `tools/jgate` 是否已全绿并生成了真实测试通过凭据？
