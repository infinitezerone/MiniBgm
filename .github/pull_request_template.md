## 概要 (Summary)
<!-- 请简要说明本次改动的目的、影响范围以及关联的 Issue / 需求 -->

## 改动类型 (Change Type)
- [ ] 🚀 新特性 (feat)
- [ ] 🐛 缺陷修复 (fix)
- [ ] ♻️ 重构 (refactor)
- [ ] ⚡️ 性能优化 (perf)
- [ ] 🧪 测试 (test)
- [ ] 📝 文档 (docs)
- [ ] 🔧 构建/工具链 (build/ci/tools)

---

## 质量与架构审查清单 (Architecture & Quality Checklist)

### 1. 通用架构硬红线 (AGENTS.md & ArchitectureRulesTest)
- [ ] feature 间无直接依赖，UI 仅通过 `:core:data` 仓库层流转。
- [ ] 状态防线：未暴露任何 `MutableStateFlow`；无 Flow 算子内部 `launch` 副作用协程。
- [ ] 瞬态交互：浮层统一由 `BgmOverlayHost` 承接，无散落 `var showDialog`；通用登录提示使用 `BgmLoginPromptDialog`。
- [ ] 契约闭环：手工 `viewModel { ... }` 块包含全部带默认值的可选依赖，无静默退化。

### 2. Modifier.Node 规范自检清单 (Playbook 19 & 三层防御网)
> 凡涉及自定义修饰符（Custom Modifier）、手势或微交互动效时必检：
- [ ] **严禁遗留**：未在生产代码中使用 `Modifier.composed`（违者被 `custom_modifiers_must_never_use_legacy_modifier_composed` 拦截）。
- [ ] **Element 契约**：
  - [ ] 继承 `ModifierNodeElement` 的类声明为 `data class`（或显式完备重写 `equals` 与 `hashCode`），保障 Compose 节点复用与跳过（`modifier_node_elements_must_be_data_classes_or_implement_value_equality`）。
  - [ ] Element 构造参数中的所有可变属性均在 `update(node)` 方法体内完成赋值同步。
  - [ ] 视调试需要实现了 `InspectorInfo.inspectableProperties`。
- [ ] **Node 性能与生命周期**：
  - [ ] `measure()`、`layout()`、`draw()` 热路径内零临时对象分配（无临时 `Path`、`Paint` 或 Lambda 分配）。
  - [ ] 精确调用 `invalidateDraw()` / `invalidateMeasurement()`，避免无谓全层重测重绘。
  - [ ] 异步副作用绑定至 Node 自身 `coroutineScope`，并在 detach/reset 时自动安全退出。

---

## 验证与自测 (Verification)
- [ ] 本地运行 `tools/jgate quick` 秒级门禁通过。
- [ ] 提交前运行完整 `tools/jgate`（无 `--fast`）全量检查通过。
- [ ] 单元测试覆盖率满足要求且未破坏既有测试。
