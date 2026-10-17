## 剧本 1：只读特性（Read-Only Features）
* **规范先例**：`:feature:schedule`
* **状态流向**：
  * UI 只经 `:core:data` 仓库获取数据；
  * ViewModel **严禁对外暴露 `MutableStateFlow`**；对外必须显式转为只读 `StateFlow`（`asStateFlow()`），或 Compose State 持有者的只读类型（`@Stable interface XxxUiState`）；
  * 多字段原子更新使用 `Snapshot.withMutableSnapshot`，不手写 `update { it.copy(...) }`。
* **数据真值**：
  * 排期真值只来自 AniList 已验证播出事件；禁止算术预测与合成剧集；bangumi-data 仅作元数据映射。
