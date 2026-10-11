---
name: minibgm-playbooks
description: >-
  MiniBgm 架构实施剧本库。当在本项目中开发新特性、构建 UI、重构数据流或处理图片与弹窗时，
  指导 Agent 机械遵循 Now in Android / Clean Architecture 规范先例（只读页面、带参详情分页、
  写操作提案卡、瞬态浮层收敛、Fail-Open 装饰与图片安全）。
---

# MiniBgm 架构实施剧本库（Playbooks）

本项目遵循仿 Google *Now in Android* 的 Clean Architecture。所有开发动作**严禁自创模式**，必须机械对齐以下规范先例：

---

## 剧本 1：只读特性（Read-Only Features）
* **规范先例**：`:feature:schedule`
* **状态流向**：
  * UI 只经 `:core:data` 仓库获取数据；
  * ViewModel **严禁对外暴露 `MutableStateFlow`**；对外必须显式转为只读 `StateFlow`（`asStateFlow()`），或 Compose State 持有者的只读类型（`@Stable interface XxxUiState`）；
  * 多字段原子更新使用 `Snapshot.withMutableSnapshot`，不手写 `update { it.copy(...) }`。
* **数据真值**：
  * 排期真值只来自 AniList 已验证播出事件；禁止算术预测与合成剧集；bangumi-data 仅作元数据映射。

---

## 剧本 2：带参详情与标准按需分页（Detail & Pagination）
* **规范先例**：`:feature:subject`
* **分页规则**（汲取 commit `4ab43405` 经验）：
  * **禁止一次性全量加载所有分页**（如历史上的 `loadAllEpisodes` 反模式已废除）；
  * 必须采用标准的按需分页流（On-demand Pagination）；
  * 路由密封于 `BgmRoute` 并声明入栈层级语义。

---

## 剧本 3：用户写操作与提案卡（Write Operations）
* **规范先例**：`CollectionRepository`
* **安全红线**：
  * 一切写操作必须止于 `PENDING_CONFIRMATION` 提案卡，用户在 UI 明确点击确认后才落库；
  * 关键写操作必须包在 `withContext(NonCancellable)` 中，防止切页面时协程被无意取消导致数据状态不一致；
  * 凭据只存在于 `AuthTokensDataSource`；不绕过/削弱 `BgmPkce`；不剥离或覆盖 `User-Agent`。

---

## 剧本 4：瞬态交互浮层与一次性事件（Transient UI & One-Off Events）
* **规范先例**：`BgmOverlayHost`
* **浮层收敛**：
  * **严禁在 feature 中散落声明 `var showDialog = remember { ... }` 状态机**；
  * 所有 UI 确认与交互浮层统一通过 `BgmOverlayHost` + `overlayHostState.await(...)` 挂起承接；
  * 通用登录提示严格收口于 `BgmLoginPromptDialog`。
* **一次性事件**：
  * ViewModel 一次性事件使用 `Channel(BUFFERED)`；
  * UI 侧严禁裸调用 `.collect`，必须经 `:core:designsystem` 的 `ObserveAsEvents` 绑定生命周期（`Lifecycle.State.STARTED`）；
  * **事件文案资源化**：`ShowSnackbar` 类事件严禁内联用户可见文案（ArchitectureRulesTest #18 物理拦截）。正向模版：
    ```kotlin
    // 事件携带 @StringRes + 格式化参数；服务端运行时消息走 message 字段（非空白优先展示）
    data class ShowSnackbar(
        val message: String? = null,
        @StringRes val messageRes: Int = 0,
        val formatArgs: List<String> = emptyList(),
    ) : XxxUiEvent
    // UI 收集侧解析
    val text = event.message?.takeIf { it.isNotBlank() }
        ?: context.getString(event.messageRes, *event.formatArgs.toTypedArray())
    ```

---

## 剧本 5：装饰性功能 Fail-Open 与硬件位图安全（Bitmap & Fail-Open）
* **规范先例**：汲取硬件位图崩溃复盘经验
* **安全规则**：
  * 纯装饰性功能（如封面微光、光晕、动效、横幅）内部异常一律用 `runCatching` 降级为“没有装饰”，绝不崩溃宿主页面；
  * 真机 Coil 默认解码为 `Config#HARDWARE`（硬件位图，禁止直接 `getPixels`）；在读取位图像素前，必须先判断并通过 `copy(Bitmap.Config.ARGB_8888, false)` 转为软件位图。

---

## 剧本 6：快速反馈与最终闭环（Verification Workflow）
* **编码中间态快速自检（秒级）**：
  ```powershell
  .\tools\quick-check.ps1          # 跑 17 条架构红线自动化断言
  .\tools\quick-check.ps1 -Format  # 自动执行 spotlessApply 并查红线
  ```
* **提交前唯一门禁（物理锁死）**：
  ```powershell
  .\tools\jgate.ps1                # 完整门禁（素材体积、Spotless、红线、模块测试、XML校验、assembleDebug）
  ```

---

## 剧本 7：模式级避坑暗礁（来自真机实战复盘）
1. **LaunchedEffect 组合重建重放陷阱**：
   * 需要“单次执行”语义的副作用（如回顶、单次曝光、代数重置），必须使用 `rememberSaveable` 记录已应用标记，严禁寄希望于 key 相同而不重放。
2. **Room 分步写与数据流闪跳**：
   * Room 数据库“一次写入 = DAO 流一次外推”。UI 若需要展示多源对齐的“最终态”，必须在 Repository 建立闸门（如 `scheduleHoldGate`）扣住对外流，管线聚合完成后再一并发射，防止 UI 闪变多次。
3. **平台容错窗口必须同尺度同源**：
   * 系统非精确闹钟投递窗口可达 1 小时，业务容错如果按分钟设计等于被直接架空；检索 SQL 的时间范围必须与业务容错范围同源（共用相同算法计算 lookback），防止条目在被容错判定前就被 SQL 提前过滤。
4. **长周期链式预约窗口**：
   * 周播番剧开播周期为 7 天，闹钟的 Next-Up 候选检索窗必须大于 14 天（防止单集开播后因下周超出检索窗口而被误清空闹钟导致链条断裂）。
5. **依赖约束先看契约源码，不看搜索摘要**：
   * 接口有没有端点、是否强制验证码、是否支持原生发帖（如 P-005 Turnstile 阻塞），必须查验 OpenAPI 或实测返回，不盲信模型搜索猜测。
