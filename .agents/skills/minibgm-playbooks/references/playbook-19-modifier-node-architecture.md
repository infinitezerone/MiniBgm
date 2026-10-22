## 剧本 19：现代 Modifier.Node 架构与微交互动效规范（Modern Modifier.Node Architecture & Micro-interactions）

* **规范先例**：
  * 实现：`core/designsystem/src/main/kotlin/com/infinitezerone/minibgm/core/designsystem/component/ExpressiveMotion.kt`（`AquamorphicPressNodeElement`、`AquamorphicPressNode`、`Modifier.aquamorphicPress`）
  * 单测：`core/designsystem/src/test/kotlin/com/infinitezerone/minibgm/core/designsystem/component/AquamorphicPressTest.kt`
  * 红线测试：`core/testing/src/androidHostTest/kotlin/com/infinitezerone/minibgm/core/testing/ArchitectureRulesTest.kt`

* **根因警示（来自遗留 `composed` 性能退化、节点意外销毁与按压闪烁复盘）**：
  1. **`Modifier.composed` 的性能暗礁**：
     * `Modifier.composed` 在 Composition 阶段强制为每个修饰符创建闭包和重组作用域，无法利用 Compose Compiler 的跳过优化（Skipping），且无法直接介入 Layout 和 Draw 阶段；
     * 在高频交互（如点击按压、滚动、手势拖动）或懒加载列表中使用时，会引发无谓重组与内存抖动。
  2. **Element 比对失效导致 Node 频繁重建**：
     * 继承自 `ModifierNodeElement<T>` 的类若未声明为 `data class` 且未重写 `equals`/`hashCode`，Compose 只能按对象内存地址（引用相等性）进行比对；
     * 每次上层重组都会被误判为「全新的修饰符」，从而销毁旧 Node（触发 `onDetach`）并重新 `create()`，彻底丧失 Node 状态持久性，并导致执行中的协程和物理动画直接中断（表现为触控断流）。
  3. **热路径对象分配引发 GC 卡顿**：
     * 在 `measure()`、`layout()` 或 `draw()` 回调内临时创建对象（如构造 `Path()`、`Paint()`、闭包 Lambda），每一帧都在堆上分配，极易触发 GC 掉帧。
  4. **短按点击导致微交互动画被掐灭（闪烁或无反馈）**：
     * 用户轻点操作时，`PressInteraction.Press` 与 `PressInteraction.Release` 的间隔往往小于 50ms；
     * 若抬手立即启动反向恢复动画，按下动画尚未到达目标缩放值便被直接反转，在肉眼看来就是一闪而过甚至完全感知不到回弹。

---

* **三层质量防御体系（Three-tier Quality Defense）**：

### 1. 第一层：静态架构红线（ArchitectureRulesTest）
通过机械扫描拦截低级错误，不依赖人工肉眼盯防：
* **全面禁止遗留 `Modifier.composed`**：
  * 测试：`ArchitectureRulesTest#custom_modifiers_must_never_use_legacy_modifier_composed`
  * 规则：生产代码中出现 `Modifier.composed` 即刻测试失败。自定义修饰符必须基于现代 `Modifier.Node` 或标准修饰符扩展链（`this.then(...)`）。
* **Element 强制值比对（Value Equality）**：
  * 测试：`ArchitectureRulesTest#modifier_node_elements_must_be_data_classes_or_implement_value_equality`
  * 规则：凡继承 `ModifierNodeElement` 的类必须声明为 `data class` 或显式重写 `equals` 与 `hashCode`，保障 Compose 仅在参数真正变化时调用 `update()`，否则安全复用已有 Node。

### 2. 第二层：标准单元测试套件（Unit Testing）
所有新编写的 `Modifier.Node` 组件须提供测试保证：
1. **Element 相等性与 HashCode 验证**：断言相同入参的 Element 实例 `equals` 与 `hashCode` 均相等，不同入参不相等；
2. **状态传递完备性**：验证调用 `element.update(node)` 后，Node 内部状态完整同步并按需触发对应的失效通知；
3. **事件流并发与生命周期健壮性**：如 `CombinedInteractionSource` 验证多子组件并发点击、按下取消等事件流在协程收集下的无死锁与有序合并。

### 3. 第三层：Pull Request 审查清单（Checklist）
在 `.github/pull_request_template.md` 中机械固化：
* [ ] 未在生产代码中使用 `Modifier.composed`。
* [ ] `ModifierNodeElement` 声明为 `data class`。
* [ ] Element 构造器的所有可变参数在 `update(node)` 中均已完成赋值。
* [ ] `measure()` / `layout()` / `draw()` 热路径零对象分配。
* [ ] 异步逻辑绑定至 Node 自身 `coroutineScope`，生命周期安全。

---

* **水生触控动效标准规范（ColorOS Aquamorphic Physical Motion）**：
在实现点击收缩、卡片按压等物理反馈修饰符时，对齐系统级物理质感：
1. **轻点冲量保持保护（Impulse Hold）**：
   * 采用 `MIN_IMPULSE_MS = 90L` 的最小冲量窗口。
   * 当用户短按且抬起耗时 `elapsed < MIN_IMPULSE_MS` 时，挂起等待剩余时长 `delay(MIN_IMPULSE_MS - elapsed)`，确保按下收缩姿态充分展开后再执行回弹。
2. **非对称物理弹簧与二次微弹（Asymmetric Spring & Secondary Micro-bounce）**：
   * **按下（Press Down）**：紧致沉稳、迅速到位、无过冲（Damping 1.0f 临界阻尼）。
     `spring(dampingRatio = 1.0f, stiffness = 700f, visibilityThreshold = 0.0001f)`
   * **抬手（Release Up）**：水生流体弹性势能释放与二次微弹（Damping 0.35f，Stiffness 450f，Threshold 0.0001f）。
     * **主过冲（Primary Overshoot）**：在 ~150ms 处膨胀至 ~1.016x（+1.6% 弹性拉伸）；
     * **二次微弹（Secondary Micro-bounce）**：在 ~310ms 处回落微凹至 ~0.995x（-0.5% 水滴张力收缩）；
     * **平滑收敛**：在 ~420ms 处自然静止于 1.000x。
   * **⚠️ 核心暗礁（Compose 阈值抹杀陷阱）**：
     * Compose 的 `Animatable<Float>` 默认 `visibilityThreshold = 0.01f`（1%）。若不显式指定，当振荡幅度落入 1% 以内时，Compose 动画系统会直接判断达到稳态并强制 snap 到 1.0f，导致过冲和二次微弹在第一帧跨越目标时被瞬间掐灭！
     * 自定义物理微动效必须显式配置 `visibilityThreshold = 0.0001f`。
3. **直接操作渲染层（Zero Recomposition）**：
   * 继承 `LayoutModifierNode`，在 `placeWithLayer` 中直接更新 `scaleX` 与 `scaleY`，彻底避免重组开销。
