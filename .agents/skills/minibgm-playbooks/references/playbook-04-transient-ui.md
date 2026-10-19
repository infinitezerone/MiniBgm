## 剧本 4：瞬态交互浮层与一次性事件（Transient UI & One-Off Events）
* **规范先例**：`BgmOverlayHost`
* **浮层收敛**：
  * **严禁在 feature 中散落声明 `var showDialog = remember { ... }` 状态机**；
  * 所有 UI 确认与交互浮层统一通过 `BgmOverlayHost` + `overlayHostState.await(...)` 挂起承接；
  * 通用登录提示严格收口于 `BgmLoginPromptDialog`。
* **一次性事件**：
  * ViewModel 一次性事件使用 `Channel(BUFFERED)`；
  * UI 侧严禁裸调用 `.collect`，必须经 `:core:designsystem` 的 `ObserveAsEvents` 绑定生命周期（`Lifecycle.State.STARTED`）；
  * **面板类浮层（持续面板/抽屉）同样走 OverlayRequest**：`var xxxOpen` 布尔状态机的关闭时机要在返回键回退链、全屏切换、横竖屏切换、进 PiP 多处手动同步，且多个标记可同时为 true（叠层）。收敛进 `BgmOverlayHost` 后单槽天然互斥、每个面板自带 `BackHandler`（优先级由单槽语义自动保证）、打开-关闭-取结果写在同一处挂起调用。带退出动画的抽屉用 `MutableTransitionState` + `snapshotFlow` 把 respond 延迟到动画播完（禁 `SideEffect`，下树需 `DisposableEffect` 兜底响应），其 `BackHandler` 必须按 `enabled = visibleState.targetState` 条件启闭（严禁无条件拦截致使后续系统返回键死锁）；宿主状态切换（全屏/PiP）用 `overlayHostState.cancelActive()` 收起面板。参照 `PlayerPanelOverlays.kt`。播放器线路切换同样收敛至统一面板（`PlayerSourcesBottomSheet`），选集抽屉（`PlayerEpisodeQueueDrawer`）专心负责选集与连播，严禁在子抽屉中重复内嵌平铺线路切换芯片。
  * **事件文案资源化**：`ShowSnackbar` 类事件严禁内联用户可见文案（ArchitectureRulesTest #18 物理拦截）。正向模版：
    ```kotlin
    // 事件携带 @StringRes + 格式化参数；服务端运行时消息走 message 字段（非空白优先展示）
    data class ShowSnackbar(
        val message: String? = null,
        @StringRes val messageRes: Int = 0,
        val formatArgs: List<String> = emptyList(),
    ) : XxxUiEvent

    // UI 收集侧解析（Composable 作用域内严禁裸调 context.getString，需经 resources.getString）
    val resources = LocalContext.current.resources
    val text = event.message?.takeIf { it.isNotBlank() }
        ?: resources.getString(event.messageRes, *event.formatArgs.toTypedArray())
    ```
  * **状态机模型文案传递规范**：组件状态机（如诊断卡片 `Failure`、校验提示等）严禁在状态组装期直接调用 `context.getString(...)`（触发 `LocalContextGetResourceValueCall`）。必须由状态机直接持有 `@StringRes val summaryRes: Int`、`@StringRes val detailRes: Int? = null` 与服务端原文 `val detailMessage: String? = null`，交由 UI 渲染层统一通过 Compose 原生的 `stringResource(state.summaryRes)` 响应式解析。
