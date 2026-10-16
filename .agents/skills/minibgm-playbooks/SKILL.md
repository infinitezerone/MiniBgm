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
  * **面板类浮层（持续面板/抽屉）同样走 OverlayRequest**：`var xxxOpen` 布尔状态机的关闭时机要在返回键回退链、全屏切换、横竖屏切换、进 PiP 多处手动同步，且多个标记可同时为 true（叠层）。收敛进 `BgmOverlayHost` 后单槽天然互斥、每个面板自带 `BackHandler`（优先级由单槽语义自动保证）、打开-关闭-取结果写在同一处挂起调用。带退出动画的抽屉用 `MutableTransitionState` 把 respond 延迟到动画播完；宿主状态切换（全屏/PiP）用 `overlayHostState.cancelActive()` 收起面板。参照 `PlayerPanelOverlays.kt`。播放器线路切换同样收敛至统一面板（`PlayerSourcesBottomSheet`），选集抽屉（`PlayerEpisodeQueueDrawer`）专心负责选集与连播，严禁在子抽屉中重复内嵌平铺线路切换芯片。
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

---

## 剧本 5：装饰性功能 Fail-Open 与硬件位图安全（Bitmap & Fail-Open）
* **规范先例**：汲取硬件位图崩溃复盘经验
* **安全规则**：
  * 纯装饰性功能（如封面微光、光晕、动效、横幅）内部异常一律用 `runCatching` 降级为“没有装饰”，绝不崩溃宿主页面；
  * 真机 Coil 默认解码为 `Config#HARDWARE`（硬件位图，禁止直接 `getPixels`）；在读取位图像素前，必须先判断并通过 `copy(Bitmap.Config.ARGB_8888, false)` 转为软件位图。

---

## 剧本 6：快速反馈与最终闭环（Verification Workflow）
* **编码中间态快速自检与微验证（秒级 Micro-Eval）**：
  ```powershell
  .\tools\quick-check.ps1                        # 跑 20+ 条架构红线自动化断言
  .\tools\quick-check.ps1 -Format                # 自动执行 spotlessApply 并查红线
  .\tools\quick-check.ps1 -Modules core/network  # 跑红线 + network 模块定向单测（极速微验证）
  .\tools\quick-check.ps1 -Only core/data        # 仅测目标模块（跳过红线，极限循环）
  ```
* **提交前唯一门禁（物理锁死）**：
  ```powershell
  .\tools\jgate.ps1                              # 完整门禁（CommitLint、素材、Spotless、红线、测试、XML校验、assembleDebug、结构化诊断）
  .\tools\jgate.ps1 --stack                      # 验证本地变更栈（trunk()..@ 栈内逐个提交格式与全栈改动汇总）
  .\tools\jgate.ps1 --fast                       # 跳过截图测试（本机减负；提交前必须复跑不带 --fast 的一次）
  ```
  两处减负都是**自动或显式**且会在输出里标注，绝不静默：纯 `*.md`/`docs/`/`.agents/`/`.github/` 改动会自动跳过 `:app:assembleDebug`（架构红线照跑，装配门对这类改动零信息量；`native/`、`tools/` 仍跑），`--fast` 才会跳过截图测试。截图测试实测占单测耗时 43%（59s/137s，15 例），省的是 Robolectric NATIVE 图形栈每个类的冷启动。门禁若有拦截，自动在 `build/reports/jgate/diagnostic.json` 产出结构化机器诊断供自愈。
* **单测配置的单一真源**：带 Robolectric/Roborazzi 的模块一律调用 build-logic 的 `configureAndroidUnitTests()`（Java 21 launcher + `-Proborazzi.record=true` 录制 + `-Pminibgm.skipScreenshots` 跳过），禁止再各自手抄 `tasks.withType<Test>` 块——三份副本曾是各自漂移的温床。
- **UI 自查无需设备（Roborazzi + Robolectric）**：改 Compose 面板/控制层后写截图测试自查，输出到 `src/test/snapshots/images/` 再读图检查。要点：模块需接 roborazzi 四件套 + `testOptions.unitTests.isIncludeAndroidResources` + Test 任务 `roborazzi.test.record=true` 与 JDK 21 launcher 由 `configureAndroidUnitTests()` 统一提供（模块侧只写这一行，实现在 `build-logic/convention/.../AndroidUnitTest.kt`；测试写法参照 `feature/subject/PlayerPanelsScreenshotTest.kt`）；`@Config(qualifiers = "w420dp-h1600dp")` 控制视口尺寸（默认窗口仅 320dp 宽，横排按钮会溢出"消失"）；深色背景模拟视频底，否则白色控制图标"隐形"；LazyColumn 跨分组 items key 必须唯一。

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
5. **控制栏显隐用"转变沿"而非状态捆绑**：暂停/结束时若把 `!isPlaying` 写进常显条件，用户将永远无法隐藏控制栏、得不到纯净暂停帧（B 站/YouTube 均支持暂停后单击隐藏）。正确形态：进入暂停的瞬间自动置 `controlsVisible = true`（转变沿），之后恢复单击切换语义；强制常显只保留给"有必须可见内容"的状态（缓冲 spinner、结束态、错误态）。同理，播放出错时收起活跃面板（`overlayHostState.cancelActive()`），避免面板遮罩挡住错误态的重试/换源按钮。
6. **横屏控制栏与统一面板入口对齐**：
   * 竖屏与横屏全屏下的核心能力（选集、线路切换、播放设置）必须全场景对齐，严禁横屏全屏下缺失线路切换入口迫使用户退回竖屏；统一面板（如 `PlayerSourcesBottomSheet`）中的二级流向（如“管理规则”与“AI 找源”）必须在语义和回调上严格解耦，严禁因回调为空而随意降级导流。
7. **依赖约束先看契约源码，不看搜索摘要**：
   * 接口有没有端点、是否强制验证码、是否支持原生发帖（如 P-005 Turnstile 阻塞），必须查验 OpenAPI 或实测返回，不盲信模型搜索猜测。
8. **整体覆盖式状态重置会静默吞掉引擎侧偏好**：
   * 用"整体覆盖"重置媒体态（`_state.value = PlaybackState(...)`）时，任何没被显式带过去的字段都等于被悄悄改回默认值。单集循环 `isLooping` 由引擎 `repeatMode` 持有、UI 只持状态副本，于是出现"面板显示已关闭、视频仍在循环"的幽灵态（开关要点两次才关得掉）。正确形态：重置只覆盖媒体相关字段，偏好类字段显式保留并与引擎保持一致——`PlayerController.setMedia` 里 `val looping = _state.value.isLooping`，且 URL 变化、URL 清空两条路径都要覆盖；新增"重置媒体态"的写法时先问一句"哪些字段是引擎/用户偏好、删了会变行为"。同源陷阱：`PlayerController.resetTrackOverridesOnMediaChange()` 曾顺手 `setTrackTypeDisabled(TEXT, false)`，于是用户显式关掉字幕后每换一条线路/换一集都要重关一次——`disabledTrackTypes` 是**用户级偏好**，换媒体只允许清「跟着上一份媒体选的」轨道 override（`clearOverridesOfType`），红线由 `PlayerControllerTest#disableSubtitles_survivesMediaChange` 钉死。
9. **AGP 的 `splits` 是全局 DSL，没有"按变体开关"的公开 API（9.3.2 的 `Splits` 只在 `com.android.build.api.dsl`）**：
   * 所以"debug 单包 / release 分包"只能靠**项目属性 + 工作流保证**：`app/build.gradle.kts` 里 `val enableAbiSplits = (project.findProperty("minibgm.abiSplits") as? String)?.toBoolean() ?: false`，发版时由 `release.yml` 传 `-Pminibgm.abiSplits=true`。改这类"默认关、发版开"的开关时，**必须在发版链路补产物断言**（缺包直接 exit 1），否则一改就静默退化成 fat APK，下游的分 ABI 资产与 universal 别名全部变形。
10. **续播点属于"分集"而不是"上一个地址"**：
   * 任何换集入口都必须显式按新分集查表下发 `resumePositionMs`。直链换集时新地址当下可知，当场查表；规则源换集时旧画面被故意保留、新地址要等嗅探结果，只能把查表推迟到解析成功那一刻一次性消费（`consumePendingResumePositionForNewEpisode`），而同分集内换线路必须沿用当前进度（不可消费）。只靠"`resumePositionMs == 0` 才回填"的仓库快照回填会漏掉换集路径，把上一集进度当成新分集续播点下发，导致新分集跳到旧分集时间轴、短集瞬间播完并连播空跳。新增换集入口时，同步补一条"换集后 `resumePositionMs` 等于新分集自己的记录"的回归单测。
11. **离线视频播放器工厂与种子规则隔离**：
   * ExoPlayer 引擎的 `DefaultMediaSourceFactory` 必须经 `DefaultDataSource.Factory(context, httpDataSourceFactory)` 包装。若裸传 `httpDataSourceFactory`，Media3 播放离线下载的 `file://` 视频时会强转为 `HttpURLConnection` 造成 `ClassCastException` 崩溃；
   * 播放器线路构建（`PlayerViewModel.buildSources`）必须过滤 `kind != PlaybackRuleKind.TORRENT`，避免将 BT 种子嗅探规则误暴露给播放器作为普通在线流；
   * 剧集队列匹配条件必须允许 `episodeSort >= 0f`（第 0 话序章），剧集标签展示需通过 `toEpisodeLabel()` 保留半集/总集篇小数字号（如 0.5、6.5）。
12. **Flow 算子内 launch 副作用＝无界协程堆积**：
   * `map` / `onEach` / `filter` / `transform` 这类算子的 lambda 是「每次发射都要跑一遍」的位置，在里面 `launch` 等于每次发射都新起一个无人等待的协程。`DownloadViewModel` 曾在 `tasks.map { }` 里 `launch { refreshStorageUsage() }`，而任务流在下载期间每秒推送进度、`getStorageUsage()` 又是全量 `walkTopDown()` 扫盘，于是下载页每秒并发堆积一次全盘遍历（还叠加 `refreshStorageUsage()` 内部再 `launch` 一层）。
   * 正确形态：需要「随流持续刷新」时用**单条收集协程串行执行**（`viewModelScope.launch { tasks.collect { _storageUsage.value = repo.getStorageUsage() } }`）——StateFlow 自带 conflate，天然背压，扫描期间的发射被丢弃，始终只有一个任务在跑；只有 UI 动作触发的刷新才保留独立的 `refreshStorageUsage()`。
   * 红线：`ArchitectureRulesTest#flow_operators_never_launch_side_effect_coroutines` 对 `core/`、`feature/`、`app/`、`sync/` 全量源码静态闭锁（词法脱敏后按 `算子 {` … `launch {` 跨行匹配）。

13. **关掉的开关自己长回来：播放页偏好只活在会话内存里**：
   * `toggleDanmaku()` 曾只做 `_uiState.update { it.copy(isDanmakuEnabled = ...) }`：换集/重进播放页就悄悄回到「开」，用户关掉的弹幕自己长回来，关闭期间还在后台按集拉弹幕；自动连播是同一形态（关掉后换一集又自动连播）。反向的坑在 UI 侧——画面比例/播放速度私藏在 `remember { mutableStateOf }` 里，仓库层完全不知情，重启即丢。
   * 正确形态：UI 可见偏好一律写 `SettingsRepository`（`UserPreferences.danmakuEnabled` / `autoNextEnabled` / `subtitlesDisabled`），再由**快照投影**回读，保持单一真源（`PlayViewPreferences` 承载画中画/弹幕/连播/字幕四条偏好——同一份偏好拆进两条 combine 支路时，一次写入会各推一次、产出两份快照）；UI 局部状态只留瞬态交互。红线 `ArchitectureRulesTest#player_view_preferences_must_be_persisted_not_held_in_session_state` 物理闭锁：既禁 VM 就地翻转偏好字段/漏写 setter，也含 PlayerScreen 局部状态白名单（新增一处就要显式回答「它该不该持久化」）。
   * **「关闭字幕」落库的是立场、不是轨道**：`subtitlesDisabled` 只记用户"要不要字幕"，不记选了哪条轨（轨道 override 跟着具体媒体走，换源/换集由 `resetTrackOverridesOnMediaChange()` 收敛）。恢复只能走 `PlayerController.applySubtitlesDisabledPreference(disabled)` 的**单向**语义——`disabled == false` 直接 return，只处理「关」；若反向无条件 `setTrackTypeDisabled(TEXT, false)` + `clearOverridesOfType(TEXT)`，进页那一刻就会把用户刚选的轨道抹掉。UI 侧形态与清晰度一致：引擎动作当场生效（`disableSubtitles()` / `selectSubtitleTrack()` / `autoSelectSubtitles()`）+ 立场落库（`setSubtitlesDisabled(true/false)`）。
   * 反向同样要钉住：**播放速度与画面比例刻意不持久化**——它们跟着「这条视频/这次观看」走，持久化会让下一部番莫名被裁剪、被变速（B 站/YouTube 的倍速同样不跨视频记忆）；**单集循环 `isLooping` 同样刻意不落库**——它由引擎 `repeatMode` 持有，是进程内的会话态（换集/换源保留、重启即回默认，B 站/YouTube 的循环也不跨视频）。红线同一条测试里机械闭锁：`UserPreferences` 出现该字段、或 `SettingsRepository` 出现同名标识（字段/setter/投影）即失败，另有「速度/比例/循环」三组关键词兜底防改名绕过（`preferredPlaybackSpeed` / `autoLoopEpisode` 之类同样报错）。
   * 关闭即零请求：弹幕关闭期间不得拉取；自动拉取闸口必须**同步**读投影 + 按 `episodeId:episodeSort` 请求键去重。兜底拉取的判据是「弹幕为空」，而空结果会一直为空，播放进度落盘、源健康度变化都会重放快照——没有请求键就是把同一分集重拉成无界重试；异步回读设置更糟：同一写入推来的两份快照会各自放行一次。「重开弹幕但没有弹幕」的提示只能由那次补拉的**真实空结果**给出，严禁在拨开关的瞬间预判为空。
   * **「分集纠正」入口同样属于这条闸口**：该入口（控制栏 `DanmakuMatchEntryButton` 与播放设置面板里的那一行）会 `saveDanmakuOverride` + 立即拉一次弹幕，弹幕关掉后入口还亮着，用户点下去就是给「已关闭的功能」写持久化覆盖并发请求。两处入口都必须一并按 `isDanmakuEnabled` 收起（`hasCandidates = isDanmakuEnabled && ...` / `takeIf { isDanmakuEnabled && ... }`），`selectDanmakuCandidate` 再兜一层竞态闸口（关掉开关的那一帧里旧入口可能尚未重组掉）。红线：`ArchitectureRulesTest#danmaku_match_entry_must_be_gated_by_the_danmaku_toggle`。

14. **同一组件在多页各持一份偏好＝改一处不动另一处**：
   * 评论区组件（`CommentsSection`）同时挂在播放页、分集详情页与讨论帖详情页，但每边各持一份会话状态：播放页在 `PlayerViewModel` 的 `commentsDelegate.state.sortOrder`，详情页是一份私有的 `MutableStateFlow(CommentSortOrder.HOT)`，讨论帖页又是 `MutableStateFlow(CommentSortOrder.ASCENDING)`。于是「改成最新」只影响当前那一页，切到另一页仍是热门/正序，重启又双双回默认。
   * 正确形态：跨页共用的稳定立场必须**只写一条设备侧偏好**（`SettingsRepository.setCommentSortOrder`），各页各自用 `map { CommentSortOrder.fromKey(it.commentSortOrder) }` 投影回读；解析统一走 `fromKey`（枚举名不参与持久化，`sortKey` 才是持久化键），各页各自写默认值正是「同一语义两套降级」的温床。
   * `fromKey(sortKey, fallback)` 的 `fallback` 只在**用户从未表态（空串）**时生效，表达的是各页自己的阅读默认：视频吐槽默认「热门」、讨论帖回帖默认楼层正序（论坛按楼层读）。一旦在任何一处选过，三处共享同一个选择——不要用 `fallback` 去表达"这一页的默认"，更不要在页面里补写默认值落库（那会把「没选过」变成「选过热门」，把别的页面的默认一起改掉）。
   * 判据仍是「稳定立场 vs 单次观看意图」：弹幕显示/自动连播/吐槽排序/字幕开关是稳定立场；播放速度、画面比例、单集循环不是。
   * 红线：`ArchitectureRulesTest#comment_sort_order_must_be_a_shared_persisted_preference`（三页都必须调 `setCommentSortOrder`、都不得再留 `MutableStateFlow(CommentSortOrder...)`、都必须经 `fromKey` 解析）。

15. **LocalContext 资源获取与 LocalContextGetResourceValueCall 拦截**：
   * 在 Composable 视图渲染内部直接调用 `LocalContext.current.getString(...)` 会被 Compose 官方 Lint 规则 `LocalContextGetResourceValueCall` 拦截（因其不感知 `Configuration` 动态切换，在深色模式、屏幕旋转或语言切换时会返回陈旧资源）。
   * 正确形态：Composable 内部静态文案一律使用 `stringResource(...)`；状态模型（如诊断状态、错误卡片等）必须以 `@StringRes` 资源 ID 传递并在 UI 渲染层统一响应式解析；非 Composable 协程回调或一次性事件收集如需通过 Context 解析，必须经 `LocalContext.current.resources.getString(...)`。
   * 存量基线收敛：当依赖升级暴露大批量存量静态检查基线漂移时，经校验无误后通过 `./gradlew updateLintBaseline` 收敛更新各模块 `lint-baseline.xml`。

16. **CRAP 复杂度死锁（CC > 30 无论覆盖率多高必死）**：
   * CRAP 公式为 $CRAP(m) = CC^2 \times (1 - Cov)^3 + CC$。当圈复杂度 $CC > 30$ 时，即便测试覆盖率达到 99%，计算结果也必然大于 30.0 阈值而触发 CI 硬失败。
   * 正确形态：当复杂业务（如多源检索、多策略解析器）逻辑庞大时，切忌在一个方法体内堆叠 `when`/`for`/`if` 分支；必须拆解为职责单一的高内聚纯函数（使各个纯函数 $CC < 10$）。平台边界方法（如磁盘 IO、网络回退）必须编写单元测试，杜绝 $Cov = 0\%$ 导致的 CRAP 爆表（$Cov=0\%$ 时 $CC \ge 6$ 即超标）。验证命令：`./gradlew :core:testing:crapCheck`。

---

## 剧本 8：原生流播与后台传输真实性规范（Stream Engine & Native Fidelity）
* **规范先例**：`crates/minibgm-torrent` 与 `AndroidTorrentStreamEngine`
* **流媒体发流真实性**：
  * 严禁在生产代码中使用空 `ServerSocket(0)` 占位或以纯文本假装动态库（如已废止的 `minibgm_streamer_placeholder` 反模式）；
  * 流媒体引擎向播放器派发 `127.0.0.1:port` 地址前，本地 HTTP 守护服务必须已真实就绪，并严格支持 HTTP 206 Partial Content / Range 头部响应（满足播放器 moov/Seek 元数据探针需求）；
  * 若底层 Native 模块或资源未就绪，必须显式抛出异常或下发 `TorrentStreamState.Error` 阻断，绝不允许吞掉初始化超时派发不可达的伪地址导致播放器静默超时崩溃（`SocketTimeoutException`）。
* **纯内存流播防 OOM 与接口安全（librqbit）**：
  * **单集严格锁定与候选过滤**：番剧 BD/合集种子常含十几集几十 GB 文件，纯内存存储（`InMemoryExampleStorageFactory`）无自动 LRU 淘汰。添加种子时必须配置 `only_files_regex`（由 `VIDEO_EXTENSIONS` 单一真源派生，严禁第二份硬编码扩展名清单），并必须以 `paused: true` 添加：元数据就绪后先经 `select_target_video_file` 纯函数在 `f.included && is_video_file(&f.name)` 双重白名单内按 `episodeHint` 精确命中目标集（防止非视频 ISO/解压包夺占正片），再调用 `session.update_only_files(&handle, &HashSet::from([target_file_id]))` 收敛单集，最后才 `session.unpause` 解冻开流，杜绝全季文件同时进 RAM 导致 OOM；若写成 `paused: false`，种子会在收敛前先按整包视频选片，合集/BD 包将白下其他剧集的分片；多视频文件未命中集数时必须显式失败（`该种子包含 N 个视频文件，未能按集数 X 定位目标文件`），严禁静默回退"体积最大文件"——那会稳定挑错集且全程无任何提示；集数后缀白名单必须覆盖 `04v2`/`01v2` 这类重制命名（漏 `v` 会让单集种子被误判为未命中）；librqbit 的 `only_files_regex` 在无任何文件匹配时会整体 `bail!`，必须识别并映射为可读提示，不得让用户只看到"添加种子至 Session 失败"；
  * **本地控制面封死**：本地 HTTP API 必须配置 `read_only: true` 与 `allow_create: false`，避免同一设备上其他恶意进程扫描回环端口向会话注入种子或篡改状态；
  * **禁止擅自注入公共 Tracker（零第三方广播泄露）**：严禁在代码中硬编码公共 Tracker 列表注入请求。公共 Tracker 会将用户本机 IP 与 infohash 广播给未知的第三方服务器，且域名存在存活期腐烂失效问题；严格只使用磁链自身声明的 Tracker 或纯经由分布式 DHT 网络寻道；
    * **合成磁链零 Tracker（`TorrentRssParser` 复盘）**：`TorrentRssParser` 曾在 `infoHash` 分支给合成磁链拼上三条硬编码公共 Tracker（`tr.cili001.com` / `tracker.opentrackr.org` / `open.stealth.si`），把用户本机 IP 与 infohash 广播给未知第三方；修正后合成磁链只保留 `magnet:?xt=urn:btih:<hash>`，寻道完全依赖 Rust 会话已启用的 DHT（`SessionOptions.dht`），只有 Feed 自带的 `magnet:?` 才原样透传其自身声明的 Tracker。红线：`ArchitectureRulesTest#production_sources_must_not_inject_hardcoded_public_trackers` 对 `core/`、`feature/`、`app/`、`sync/` 生产源码做**原始文本**扫描（`&tr=` 与 `/announce`）——此处不可用 `KotlinSourceScanner.sanitize()`，因为违规形态必然落在字符串字面量里，脱敏后会被一并抹掉而漏检。
  * **进度分母真实性**：进度分母必须采用当前目标单集的真实字节大小（`target_file_len`），分子采用 `stats.file_progress[target_file_id]`，禁止使用全种子大小（会导致进度永远不到 100%）或写死假兜底分母；
  * **引擎级错误态可见性（统计数组第 5 位）**：`stats_to_jni` 的布局是 `[已下载, 总长, 速度, Peer 数, 引擎状态码]`，Kotlin 侧以 `STATS_ARRAY_SIZE=5` 判「读取失败」、以 `STATS_INDEX_STATE=4` 读状态码，命中错误码时必须立刻读 `nativeGetLastError`（Rust `TorrentStats.error`）并以**原生原文**收敛 `TorrentStreamState.Error`（原文缺失才退回通用文案）。原因：librqbit 的 `TorrentStatsState::Error` 只在磁盘写入失败（`torrent_state/live/mod.rs` 的 `on_fatal_error`）或初始化校验失败时进入，与「死种 0 速度」是两回事——Peer 连不上时 librqbit 只报 0 速度、不会进 Error。不上报就会让用户在 45 秒后被死种看门狗告知「连不上做种节点」，把磁盘写满这类**用户可自救**的真实原因伪装成死种；错误态下 `localPlayableUri()` 也严禁交地址（地址背后的会话已经死了），否则用户拿到的是一个泛化的播放错误而不是真实原因。四处锁步（数组长度 5 / 状态码下标 4 / `nativeGetLastError` 符号 / Rust `TorrentStatsState::Error` 映射）钉死于 `ArchitectureRulesTest#jni_symbol_binding_and_stats_failure_signal_keep_kotlin_and_rust_in_lockstep`；注意 `TorrentStatsState` 是 librqbit 根导出的枚举，`TorrentStats.error` 只在 Error 态有值，状态码与错误原文必须同帧读取，杜绝「已恢复但文案仍在」的错配；
  * **应用内下载真实性（绝不留 Fake 循环）**：离线落盘与在线内存流播必须泾渭分明。未接入真实持久化落盘管道前，严禁在 Repository/Service 中写死 `2.5MB/s` 拟真推进循环与硬编码 peers 欺骗用户；能力未就绪必须直接下发明确状态阻断并引导外部专业客户端。
* **按需动态 `.so` 扩展下发与装载（On-Demand Native Plugin Delivery）**：
  * **构建与打包解耦与本地调试面板**：Rust 原生库统一由 `tools/build-native` 编译，ABI 真源唯一收口于 `native/abis.txt`（`NativeAbiContractTest` 强制对齐）；`libminibgm_torrent.so` 在 `debug` 随包内嵌供本地零配置调试，在 `release` 经 `configureOnDemandNativePackaging` 排除出 APK 并上传至 GitHub Release 按需拉取；本地若需在 Debug 包中免重编测试“未安装 -> 弹窗确认 -> 动态下载 `.so` -> `System.load`”完整链路，统一使用设置页内置的 `DeveloperDebugSettingsCard`（仅 Debug 可见，调用 `TorrentStreamEngine.setDebugSimulateUninstalled(true)` 屏蔽内嵌 `.so` 回退并清理私有目录动态库），不引入额外 Gradle 构建属性；
  * **版本隔离与平滑回退**：动态 `.so` 必须落盘于版本化子目录 `filesDir/modules/torrent/v{versionName}/libminibgm_torrent.so`，安装前调用 `cleanStalePluginDirs` 清理历史旧版本目录；下载优先请求与当前版本一致的 Release Tag（`releases/download/v{version}/...`），404 时回退至 `releases/latest/download/...`；`isPluginInstalled()` 每次必须复检目标文件存在且与当前版本一致；
  * **Android 10+ W^X 只读约束与并发互斥**：从应用私有目录调用 `System.load(absolutePath)` 前，必须先对 `.so` 文件调用 `targetSoFile.setReadOnly()`（否则 API 29+ 因 W^X 内存保护直接抛 `UnsatisfiedLinkError: Permission denied`）；`installPlugin()` 必须由 `Mutex` 保护防并发重入，并通过注入 `NativeTorrentBridge` 接口保障 JVM 单元测试覆盖率；
  * **JNI 符号绑定契约（`@JvmStatic` 决定符号落在哪个类）**：Kotlin `companion object` 里的 `external fun`，只有带 `@JvmStatic` 才会把 native 跳板生成在**外层类**上（`$Companion` 只留一个普通转发器），符号因此才是扁平的 `Java_com_..._AndroidTorrentStreamEngine_nativeXxx`，与 Rust 侧 `#[no_mangle] pub extern "system" fn` 逐字对齐；漏掉 `@JvmStatic` 会退回 `..._00024Companion_nativeXxx` → 真机 `UnsatisfiedLinkError` 闪退，而单测与静态检查面对的都是预编译 `.so`（`jgate native` 指纹命中时还会跳过重编），**这条链路在真机之前完全不可见**。审查手法：`javap -v -p` 看 `ACC_NATIVE` 落在哪个类、`dexdump` 看 DEX 里的所属类、`grep -a -o "Java_com[A-Za-z0-9_]*" libminibgm_torrent.so` 看 `.dynsym` 实际导出名（macOS 上 `objdump`/`strings` 被 Xcode 许可 shim 挡住时此法同样可用）。红线：`TorrentJniBindingTest`（反射断言 native 必须在外层类、Companion 不得有 native）+ `ArchitectureRulesTest#jni_symbol_binding_and_stats_failure_signal_keep_kotlin_and_rust_in_lockstep`。
  * **JNI 边界严禁 panic 与伪健康信号**：`extern "system"` 无法让 panic 展开，边界内任何 `unwrap()` / 裸锁 panic 都是 **abort 整个进程**（用户看到闪退）；活跃会话锁必须容忍毒锁、`new_string` 失败降级为 `null`、teardown（`stop()` / drop `Session`）必须在锁外执行。统计读取的"无活跃会话"必须经 `stats_to_jni → None` 返回**长度不足 4 的数组**，严禁全零数组兜底——全零会被 Kotlin 当作"健康的零进度"，`MAX_CONSECUTIVE_STAT_FAILURES` 永不计数，`streamState` 既不发射也不收敛 Error，下游 `first {}` 与死种看门狗双双无界挂起。
  * **重要写操作/外部下载前置确认**：任何涉及消耗流量下载扩展组件或启动后台大任务的操作，必须收口于单一确认辅助函数（如 `ensureTorrentPluginInstalled`），经 `BgmOverlayHost` + `overlayHostState.await(ConfirmDialogAction(...))` 获得用户显式确认后方可执行，严禁在 Repository 暴露无确认静默下载入口（如已废止的 `checkOrInstallEngine`）；
  * **边下边播与离线下载单盘合一与双消费者生命周期协同**：离线下载（`AndroidTorrentRepository`）严禁通过 `http://127.0.0.1` 把 `librqbit` 已写入磁盘的文件再拉取拷贝第二遍（会导致双倍磁盘占用、倍增闪存磨损、阻塞于分片 0 顺序读取以及强行篡改 `.mkv` 扩展名为 `.mp4`）。正确范式：`AndroidTorrentRepository` 向 `streamEngine.startStream` 传入持久化 `saveDirPath`，由 `librqbit`（`resolve_target_file_path` / `getActiveTargetFilePath`）直接落盘真实扩展名文件，并通过 `monitorDirectEngineDownload` 监听分片完成度；由于 `.torrent` 直链种子无需等待磁链元数据解析即进入 `Buffering(0f, 0L, target, 0L, 0)` 且 `StateFlow` 会自动去重合并相等状态，`monitorDirectEngineDownload` 必须内置独立协程超时守卫（`peerConnectTimeoutMs`），且活跃进度判据严禁被仅有连接但零速度（`speedBytesPerSec <= 0L && !bytesAdvanced`）的假 Peer 重置，持续超时即主动熔断为 `TorrentTaskStatus.ERROR`（`PEER_CONNECT_TIMEOUT_MESSAGE`）并唤醒后续排队任务；多下载任务通过 `scheduleNextIfIdle` 串行排队并经 `BgmHttpClient.jsonConfig` 持久化至 `tasks_manifest.json`；边下边播抢占正在执行的后台下载时，必须先同步关停旧会话（`streamEngine.stopStream(force = true)`）再执行 `preemptedJob.cancelAndJoin()`，并在抢占期间加锁（`isPreempting`）让旧协程 `finally` 中的自动调度失效，同时在下载协程的异常捕获中加入状态守卫（已置为 `QUEUED` 的被抢占任务禁止被覆盖为 `ERROR`）；UI 发起边下边播等待（`startStreamDownload`）必须包裹单次获取超时守卫（`withTimeout`，如 60 秒），防止死种导致 UI 协程无限挂起，且下载完成落盘时必须断言物理文件真实存在且字节数大于 0；流引擎维护 `downloadSessionProtected`（后台下载持有）与 `playerSessionActive`（前台播放器持有）双消费者守卫——下载中任务暴露 `streamUrl` 供随时切入边下边播，退出播放器调用 `releasePlayerSession()` 时若后台下载仍在进行则绝不关停引擎，后台下载刚满 100% 时若播放器仍在消费也绝不中途掐断 HTTP 流；**播放优先（下载起任务严禁掐断正在播放的本地流）**：单实例 librqbit 会话下，后台离线下载起任何新任务都会 release 掉当前会话——用户「看第 5 集时下第 6 集」时画面会被直接掐断且全程无提示。修正后的范式是两半，缺一不可：① **礼让**：`scheduleNextIfIdle` 在 `streamEngine.isPlayerSessionActive()` 期间直接返回，被礼让的下载任务保持 `QUEUED`（不是 `RESOLVING`——任务并未真正启动，状态必须诚实）；② **唤醒**：仓库必须订阅 `TorrentStreamEngine.playerSessionActive`（只读一次布尔值感知不到「释放」，任务会永久卡在 QUEUED、通知栏挂着下载中却永远 0 字节），在播放器释放会话（退出播放器 / 切到在线线路 / 播放器 `onCleared`）时重新调度接手。唯一例外是用户**显式点播**的边下边播：此刻播放器本就准备切到新地址，必须传 `allowPlayerTakeover = true` 允许接管，否则礼让判据会把 `startStreamDownload` 自己卡死到获取超时（红线 `ArchitectureRulesTest#offline_download_must_yield_to_active_playback_session`）。注意「停止起播 / 暂停 / 取消」对**该任务自身**仍是强停（`force = true`）——用户显式动作不属礼让范围；
  * **本地回环流分流与文件复写守卫**：`AssetDownloadServiceImpl` 面对 `http://127.0.0.1` / `localhost` 的本地回环 P2P 流时，必须分流至独立的纯流式 `HttpClient(CIO)`（严禁交由仅支持 `https://` 且在 JNI 内存全量缓冲的 `EchHttpClientEngine`）；`crates/minibgm-torrent/src/server.rs` 的 `AddTorrentOptions` 必须显式置 `overwrite: true`（防止重复播放/下载同一剧集时因缓存文件已存在报 `File exists (os error 17)`），且初始化中途失败时必须立即调用 `cancel_token.cancel()` 释放 DHT / HTTP 后台协程与端口；
  * **ECH 边缘优选池防协程取消污染**：`EchHttpClientEngine` 捕获 `CancellationException` 时必须区分真实请求超时（`TimeoutCancellationException` 或 `remainingMillis <= 0`）与常规协程取消（如 Coil 滚动取消头像加载 `JobCancellationException`），严禁在常规取消时调用 `AdaptiveDnsResolver.recordFailure(host)` 把 `EchEdgePool` 中的健康 Cloudflare 节点打满 `consecutiveFailures` 驱逐出池；`retry_configs` 自愈重试时必须通过 `prioritize_reachable_addr` 优先复用刚刚握手响应的边缘 IP，杜绝重新排在超时死节点后面再耗时等待。
  * **分集身份键隔离与种子载荷自愈（合集/BD 包静默错集复盘）**：
    * **三处复用点必须同键**：合集/BD 包内十几集共用同一条磁力（同一 infoHash），分集身份键 `TorrentTask.episodeKey`（`torrentEpisodeKey(episodeId, episodeSort, episodeName)`，`id:` > `sort:` > `name:` 三级降级，作为单一真源放在 `:core:model`）必须在三处同时参与判等，缺一处即静默播错集：① 去重（`AndroidTorrentRepository.startDownload` 的 `existingTask` 过滤条件，否则点第 N 集命中第 M 集任务，直接返回其 `streamUrl` 或已完成任务的 `file://` 落盘路径）；② 流会话复用（`AndroidTorrentStreamEngine.startStream` 除 `infoHash`/`activeSaveDir` 外必须比对 `activeEpisodeHint`，并在声明/比对/赋值/重置四处闭环，否则换集后复用上一集的会话与 `targetFile`）；③ 详情页已完成任务快捷复用（`SubjectSourcesBottomSheet` 直接播 `file://{savePath}` 前的过滤条件）。分集键严禁写成"有集数比集数、否则整体相等"之类的模糊匹配——合集包内任意两集都必须是不同的键；半集/总集篇（`0.5`、`6.5`）必须保留小数字号，不得取整。
    * **种子载荷必须校验（HTTP 200 的 HTML 错误页陷阱）**：`AssetDownloader` 的完成判据只有"字节数达到 Content-Length"，镜像返回 HTML 错误页同样会被判 `DownloadState.Completed` 并原子改名落盘；缓存命中若只看"文件名 + 非空"，坏种子会被永久复用（离线下载还会把路径写进 DB 的 `torrentFilePath`，重启后继续命中），用户表现为稳定复现的"解析种子元数据失败"且唯一出路是清应用数据。因此 `TorrentFileCache.isValidPayload`（bencode 字典：首个非空白字符为 `d` 且含 `4:info`，只读前 1 MiB）必须同时覆盖**下载落盘后**、**缓存命中时**与**DB 持久化路径复用**（`runTaskPipeline` 读回 `task.torrentFilePath` 时）三个入口，校验失败即删除并重下（自愈）；缓存键必须 `infoHash` 优先（`hash_<btih>`），退化到种子直链指纹（`url_<hex>`），最后才用标题——严禁只按标题命名（同名不同种会永久互串）。
    * **起播等待超时必须以 `Result` 返回**：`startStreamDownload` 的 `withTimeout` 超时必须 `catch (TimeoutCancellationException)` 转 `Result.failure(IOException(...))`，真实协程取消（`CancellationException`）才原样重抛；严禁用 `getOrThrow()` 表达业务失败——`CancellationException` 会连带取消调用方协程，UI 的 `onFailure` 永不执行，用户全程无任何提示。
    * **红线兜底**：`:core:testing:ArchitectureRulesTest#torrent_episode_isolation_and_payload_validation_guards_are_enforced` 对上述五处（仓库去重键、仓库载荷校验、`getOrThrow` 禁用、引擎 `activeEpisodeHint` 四处闭环、引擎载荷校验、`runTaskPipeline` 从 DB 读回种子路径时的载荷校验、选源面板快捷复用键）做静态词法闭锁。
    * **种子检索别名回退与仓库契约参数闭环（死参数复盘）**：`TorrentFeedRepository.getTorrentResources` 的 `alternativeTitles` 曾长期是死参数——接口声明与两个调用方（`SubjectDetailViewModel` / `EpisodeDetailViewModel` 都在传 `altTitles`）齐全，实现体却从未读取：迁移自旧专有实现时把别名回退检索与它的单测一起删掉，只留下签名，于是「站点按原名/别名索引、主标题检索为空」时用户看到的是稳定空列表，且全程无任何报错。回退语义必须是**主标题优先、首个非空即返回**（主标题命中时零额外请求），候选标题按声明顺序去重，解析出的 URL 用 `triedUrls` 去重（规则模板不含 `{title}` 时所有候选同 URL，严禁重复拉取同一 Feed）；Feed 内存缓存必须有容量上限（插入序淘汰）并以 `Mutex` 串行化——详情页与分集页并发检索时裸 `MutableMap` 的结构修改会损坏桶链。红线：`ArchitectureRulesTest#repository_interface_parameters_must_be_read_in_implementation` 机械断言接口声明的每个参数在实现体内至少出现两次（override 签名 + 真实读取），把「只留签名不写实现」的假契约钉死在提交前。
  * **磁力交互面与业务闭环（列表去重 / 分集回退 / 下载确认 / 进度可见性复盘）**：
    * **去重键必须逐级回退**：多规则源结果合并后的去重键必须是 `TorrentMediaResource.dedupeKey`（`infoHash → magnetUrl → torrentUrl → title`）。旧写法 `it.infoHash.ifBlank { it.magnetUrl }` 对未携带 40 位哈希的 `.torrent` 直链条目（如 `xxx/download/1234567.torrent`）会集体退化为空串，`distinctBy` 只保留第一条——同一 Feed 的其余分集/清晰度被静默丢弃且全程无提示。
    * **分集未命中不得回退整份 Feed**：`TorrentFeedRepository.filterAndSort` 在 `episodeSort` 未命中时只允许回退合集整包项（`episodeSort == null`），仍为空则返回空列表。回退整份 Feed 时，请求第 5 集会列出第 1-4 集，点其中的单文件资源还会被 Rust 侧「多视频文件未命中即显式失败、单候选即选中」静默播成第 1 集。
    * **条目级动作禁止伪造集数**：未选中分集时的 `episodeSort` 必须回退资源自身声明的 `episodeSort`（合集为 `0f`），严禁 `?: 1f`——伪造的 `sort:1.0` 会让所有条目级资源共用同一个 `episodeKey` 而互相复用落盘文件，并把 `episodeHint="1"` 喂给 Rust 的选片逻辑。
    * **后台大任务必须显式确认并披露容量**：离线下载必须经 `overlayHostState.await(ConfirmDialogAction(...))` 确认，文案给出资源体积与 `getStorageUsage()` 的可用空间，空间不足直接拒绝；入队前调用 `rememberDownloadNotificationPermissionRequester()` 确保 Android 13+ 通知权限（通知栏是唯一的外部门控面）。组件安装失败必须用 `exceptionOrNull()?.message` 透出真实原因（ABI 不支持 / 镜像 404），严禁只 Toast 泛化文案。
    * **已落盘资源的动作必须诚实**：`completedTaskFor` 命中（该分集 + 该源已完整落盘）时「离线下载」必须禁用——放行只会落进 `startDownload` 的「既有完成任务直接返回 taskId」分支，随后弹「已加入下载队列，可在通知栏查看进度」，用户盯着通知栏等一个永不出现的进度；「已下载」判据还必须排除 `isFileMissing`：只认 `status == COMPLETED` 时，文件被清理的任务会同时点亮「已下载」徽标、把播放入口指向不存在的 `file://`，并把下载入口禁掉，用户卡在既播不了也重下不了的死路（`verifyTaskFiles` 标出的 `isFileMissing` 必须参与判据）。
    * **进度可见性与轮询韧性**：`TorrentStreamState`（`InstallingPlugin` / `Resolving` / `Buffering`）必须经 `TorrentRepository.streamState` 暴露给选源面板卡片（含取消入口），feature 严禁为此直引 `TorrentStreamEngine`；引擎轮询 `getStreamStats()` 的瞬时失败严禁 `?: break`（退出循环后 `streamState` 永不发射，下游 `first{}` 与死种看门狗双双无界挂起），只在连续失败超 `MAX_CONSECUTIVE_STAT_FAILURES` 时收敛为 `Error`。未配置 TORRENT 规则时该区块仍须渲染可达的配置引导，严禁整段隐藏。
    * **前台服务时长上限**：`compileSdk` 已进入 Android 15+ 区间，`dataSync` 前台服务 6 小时硬上限要求 `TorrentDownloadService` 实现 `onTimeout`（暂停活动任务 + 摘除前台通知 + `stopSelf`），否则超时被系统判 `ForegroundServiceDidNotStopInTimeException` 直接崩进程。
    * **红线兜底**：`ArchitectureRulesTest#torrent_ui_and_business_guards_enforce_confirmation_dedupe_and_stream_feedback` 对上述去重键、分集回退、`?: 1f` 禁用、下载确认与容量披露、轮询阈值与 `streamState` 契约做静态闭锁。

---

## 剧本 9：依赖注入闭环与真实容器校验（Dependency Injection Fidelity）
* **规范先例**：`AppModuleTest` 与 `DataModule.android.kt`
* **根因警示（来自真机 NoDefinitionFoundException 复盘）**：
  * Koin 为运行时服务定位器，编译器与未跑真实模块的假单元测试无法静态发现缺失的单例绑定；
  * 若为类构造器增加底层依赖（如 `UpdateRepositoryImpl` 新增 `ApkInstaller`），但在对应 Koin 模块（如 `platformDataModule`）中遗漏了 `single<ApkInstaller>` 注册，真机首次运行注入时将瞬间抛出 `NoDefinitionFoundException` 导致 App 强退。
* **物理防线与操作规范**：
  1. **构造器与模块原子同步**：任何时候给生产类构造函数新增依赖参数，第一步必须检查所有提供该类的 Koin 模块，确保新依赖在相应模块中有显式单例声明；
  2. **红线物理兜底**：`:core:testing:ArchitectureRulesTest` 的 `platform_data_module_declares_all_referenced_services`（硬红线 20）对 `platformDataModule` 的核心服务声明进行静态词法闭锁，防止漏绑或误删；
  3. **拒绝虚假假模块自欺欺人**：严禁在单测中为了“图省事”只写手工 Fake 模块测试 ViewModel；每次新增依赖或修改 `platformDataModule` / `userModule`，必须在 `AppModuleTest` 中执行真实模块容器解析断言（`verifyRealPlatformDataModuleResolution` 与 `verifyRealUserModuleResolutionWithPlatformDataModule`），由真实的 Koin 容器对每一项注入进行深度验证。
  4. **门禁范围不得漏掉装配测试**：`AppModuleTest` 横跨全部模块，因此 `tools/jgate` 只要检出任何模块代码改动就必须一并跑 `:app:testDebugUnitTest`（只按"改动落在哪个模块"推导会漏掉它）；同理 `AppModuleTest` 的 Stub Context 必须跟随生产代码实际调用的 Context API 扩展（`AndroidTorrentRepository.resolveDownloadDir()` 用 `getExternalFilesDir(null)`，缺这个重写会抛 "Method getExternalFilesDir in android.content.ContextWrapper not mocked"，让下载页在构造期直接击穿装配红线——`TorrentRepositoryTest` 的每个 Stub Context 早已按此约定重写，`AppModuleTest` 曾漏配）。
  5. **手工 `viewModel { XxxViewModel(...) }` 块必须显式传全「可选依赖」**：构造器上带 `= null` 默认值的参数是可选依赖，手工块漏传时**三层防线同时失效**——编译器因默认值不报错、Koin 被默认值兜住（不像漏绑单例会抛 `NoDefinitionFoundException`）、单测又自己传 fake；结果只在真机上表现为整条链路静默退化成「没这个功能」。`SubjectModule` 漏传 `playbackHistoryRepository` 时，播放历史不落库、换集/换源的「历史兜底续播」全废且全程无提示。新增/改动手工 DI 块时逐个 ctor 参数点名核对，红线 `manual_view_model_blocks_must_inject_every_optional_dependency`（`ArchitectureRulesTest` 硬红线 21）会静态比对「ctor 里 `= null` 的参数」与「块里命名实参」，漏一个即失败；非 null 默认值（`autoStart: Boolean = true`、`clock: () -> Long = { ... }`）是有意的可选项，不受此约束。

---

## 剧本 10：应用内 WebView 与本地代理闭环规范（In-App WebView & Proxy Fidelity）
* **规范先例**：`InAppWebView.kt` 与 `OAuthHttpCodec.kt`
* **根因警示（来自真机 CSP 阻断与盲测复盘）**：
  * GFW 对外部公共 CDN（如 jsdelivr）存在严重 SNI 阻断。若代理侧仅在 HTML 文本中将脚本 URL 替换为镜像节点（如 `fastly.jsdelivr.net`），而遗漏了响应头 `Content-Security-Policy`（CSP）的同步改写，浏览器安全策略将硬性拦截外部脚本执行，导致页面 JS 整体瘫痪、表单退化为未拦截的原生 GET 提交；
  * `WebChromeClient` 若未实现 `onConsoleMessage`，所有前端 CSP 报错、语法错误与网络异常在 Logcat 中完全隐形，使排查沦为凭空猜测。
* **物理防线与操作规范**：
  1. **可观测性闭环**：生产代码中声明 `WebChromeClient` 必须重写 `onConsoleMessage`，并将消息根据级别分别以 `proxyLogger.e/w/i` 输出到 Logcat；在 `onPageFinished` 中必须注入全局 `unhandledrejection` 与 `error` 诊断监听（`:core:testing:ArchitectureRulesTest` 机械红线拦截）；
  2. **协议头与内容改写一致性**：环回反代若对响应体中的外部域名进行重写，必须在出站过滤器（`filterOutboundHeaders`）中同步改写 `Content-Security-Policy` 响应头，放行对应镜像源；
  3. **全域全路径 Cookie 注入**：会话鉴权 Cookie 必须在页面加载前，针对目标主机与根路径（`Path=/`）全面写入（如 `setCookie("http://$host", "$cookie; Path=/")`），确保后续同源 AJAX（如 `/p1/login` POST）能完整携带令牌通过代理校验。

---

## 剧本 11：P2P 种子协议中立性与起播抽屉分层规范（P2P Neutrality & Playback Sources Panel）
* **规范先例**：`SubjectSourcesBottomSheet.kt`、`TorrentFeedRepository.kt` 与 `TorrentRssParser.kt`
* **根因警示（来自合规审计与站点解耦复盘）**：
  * 应用若硬编码未授权抓取特定第三方种子站点，将使核心客户端背负版权与合规风险，且难以应对镜像站切换与结构调整；
  * 核心客户端必须维持类似 Tachiyomi/Kodi 的 100% 协议与架构中立性。
* **物理防线与操作规范**：
  1. **100% 协议中立化与规则驱动**：
     * 核心应用仅保留底层 P2P 流播与下载能力及通用的 RSS/XML 种子提取器，严禁在底层数据层与详情层写死特定域名；
     * P2P 种子资源发现由用户配置的通用 `PlaybackRuleKind.TORRENT`（`RuleParserType.RSS_TORRENT`）规则驱动；未启用种子源时零网络消耗；
     * `:core:testing:ArchitectureRulesTest` 设物理红线断言，拦截核心层硬编码特定域名。
     * **RSS 提取出的 URL 必须还原标记实体（`TorrentRssParser` 复盘）**：`link` / `enclosure` 普遍把 `&` 写成 `&amp;`，不还原则磁链自带的 Tracker 声明（`&amp;tr=...`）会被下游当无效参数丢弃、种子直链带着 `&amp;` 去请求必然取不到——与 `MacCmsParser` / `ResolverMediaUrl` 同一教训。本类缺陷无法用词法红线表达（文件里存在 `unescapeXml` 不代表 URL 出站前真的调用过它，这正是本 bug 逃逸的原因），由 `TorrentRssParserTest` 的实体还原用例（精确断言还原后的磁链与直链）守住。
  2. **详情页起播抽屉（SubjectSourcesBottomSheet）现代分层布局**：
     * **顶部 Header**：分集标题与状态；
     * **主起播入口**：“立即起播”Hero 卡片；
     * **在线播放线路**：在线流媒体规则线路；
     * **P2P 原画与字幕组资源**：按字幕组归类的种子资源卡片，支持“边下边播”与“离线缓存”；未配置规则时引导用户配置；
     * **辅助工具与外部跳转**：AI 找源、外部跳转与规则管理入口。

    * **单实现优先：共享语义必须收敛到唯一真源（重复造轮子复盘）**：`infoHash` 解析曾在引擎 / 仓库 / 缓存 / UI 四处各写一遍，且降级语义还不一致（引擎返回 `"unknown_hash"` 哨兵、其余处返回空串）——同一语义两套降级正是"未知磁力互相误判为同一源"的温床，必须收敛到 `:core:model` 的 `magnetInfoHash`（解析不出统一返回**空串**，调用方一律 `isBlank()` 判定）；速率与体积格式化必须收敛到 `formatTransferSpeed` / `DownloadStorageUsage.formatBytes`，页面内手搓 `KB/s` 分支与无引用的 `formattedSpeed` 死代码属性一并清除（副本漂移会让同一数值两处显示不一致）；种子缓存键的直链指纹必须是 SHA-256 截断摘要，`Integer.toHexString(url.hashCode())` 这类 32 位弱哈希会碰撞，而载荷校验只验 bencode 结构不验身份，于是静默接受"合法但错误"的种子。红线：`ArchitectureRulesTest#torrent_implementation_consolidation_guards_are_enforced`（原始文本扫描全仓 `substringAfter("xt=urn:btih:")` 只允许出现在真源文件——此处不可用 `KotlinSourceScanner.sanitize()`，违规形态必然落在字符串字面量里会被脱敏抹掉；另闭锁哨兵字符串绝迹、`toHexString(` 禁用、`DownloadScreen` 必须消费真源）。
    * **「停止起播」必须真停底层任务 + 源同一性须含 torrentUrl**：`startStreamDownload` 必须经 `onTaskQueued(taskId)` 在任务落库瞬间把身份透出给 UI，UI 的停止动作必须 `pauseDownload(taskId)`（选**暂停**而非删除：该任务可能正是用户此前显式确认过的离线下载，删除会连半成品一起清掉）后再清忙态——只 `busyJob?.cancel()` 会让任务继续占着单实例 P2P 队列，并在下载页留下用户从未主动创建过的条目；起播进度与停止入口严禁包在「展开态」里（折叠后入口消失但任务仍在跑）；单实例会话期间所有资源动作必须一并禁用（可见的诚实禁用，而不是按钮亮着却因忙态守卫点了没反应）；既有任务比对必须同时含分集身份键与源同一性（`infoHash` 优先 → `magnetUrl` 原文 → `torrentUrl` 兜底），只比 `magnetUrl` 时 torrentUrl-only 资源两侧都是空串，`"" == ""` 会互相误命中并复用错误的流地址或 `file://` 落盘路径。
---

## 剧本 12：通用资产下载与落盘互斥规范（Asset Download & File Mutex）
* **规范先例**：`AssetDownloader.kt`、`AndroidAssetDownloader.kt` 与 `UpdateRepository.kt`
* **根因警示（来自共享单例 StateFlow 进度踩踏与 RandomAccessFile 冲突复盘）**：
  * 单例模式下若在接口暴露全局 `StateFlow<DownloadState>`，所有通过该单例并发执行的下载（如更新 APK、BT 原生扩展 .so、Torrent 种子）会互相冲刷进度状态；
  * 若提供无参数的全局 `cancelDownload()`，一处的取消调用会意外重置全局状态为 `Idle`，造成伪取消或误伤其他并发下载任务；
  * 若多个并发协程指向相同的目标落盘路径，由于缺乏路径互斥控制，多个协程会同时打开相同的 `.tmp` 中转文件并发写入，导致文件被严重损坏。
* **物理防线与操作规范**：
  1. **请求作用域冷流（Request-scoped Cold Flow）**：
     * `AssetDownloader.download(...)` 返回冷流 `Flow<DownloadState>`，调用方各取所需、天然隔离；
     * 协程生命周期驱动取消：调用方 `Job` 取消时，Flow 管道触发 `finally` 自动关闭并释放底层的网络连接与文件句柄，无需脆弱的全局无参取消方法。
  2. **路径级并发互斥锁（Path-Level Mutex）**：
     * `AndroidAssetDownloader` 维护 `pathLocks = ConcurrentHashMap<String, Mutex>()`；
     * 进入下载执行前必须获取目标规范化路径（`canonicalPath`）对应的 `Mutex.withLock`，确保相同目标文件的写入天然串行化，杜绝 `RandomAccessFile` 并发冲突。
  3. **业务层自治状态与任务**：
     * 业务门面（如 `UpdateRepositoryImpl`）根据自身业务需要维护专属的 `_downloadState` 与 `downloadJob`，对自身任务的生命周期与取消负全责，不向底层基础设施转嫁业务状态。
  4. **消费侧必须透出真实进度与取消入口（假进度复盘）**：
     * 业务门面暴露的 `downloadState` 必须被 UI 真正消费：`DownloadState.Downloading` → 整数百分比 + `formatTransferSpeed(speedBytesPerSec)`，`isIndeterminate`（服务端未给 Content-Length）时显示不带百分比的文案；**严禁**把百分比硬编码成常量——曾有整条链路（状态发射、`UpdateRepository.cancelDownload`、`UserViewModel.cancelUpdateDownload`）都已实现却无人调用，界面只弹一次「正在下载新版本… 0%」，用户既看不到进度也无法中止，只能杀进程；
     * 接口实现了但 UI 不调用，等于没有：新增后台大任务时，除了"确认弹窗 + 进度"，必须同时给出**取消入口**并在真机上验证它真的停得下来；
     * 进度发射频率（`AndroidAssetDownloader` 100ms 节流 ≈ 10Hz）不得直接当作 Compose effect 的 key，须按整数百分比去抖，否则 snackbar 每秒被重建十次；同一 `SnackbarHostState` 上的「进度」与「终态」提示必须合并为**单一** effect——分成两个 effect 时，先结束者协程被取消时的 `finally` 会清空 `currentSnackbarData`，把后结束者刚装上的「下载完成」提示一并抹掉；
     * 红线：`ArchitectureRulesTest#update_flow_must_surface_real_progress_and_cancel`（原始文本扫描"把固定百分比传给下载中文案"的字面量形态）。

---

## 剧本 13：现代内置开发面板与运行时调试收敛规范（Developer Panel & Runtime Debugging）
* **规范先例**：`DeveloperSettings.kt`、`SettingsRepository.kt` 与 `DeveloperOptionsBottomSheet.kt`
* **根因警示（来自静态 BuildConfig 与散落 FLAG_DEBUGGABLE 复盘）**：
  * 将 AI 助手、内置播放器等业务特性开关写死在编译期 `BuildConfig`，导致测试人员在 Dogfooding 或内部体验包中无法自由切换功能，排查偶现 Bug 必须反复重新构建全量 APK；
  * 各业务页随意读取 `FLAG_DEBUGGABLE` 内联展示调试按钮，破坏了界面的整洁度，且无法在非 Debuggable 环境复现底层原生异常。
* **物理防线与操作规范**：
  1. **触发与激活机制（Easter Egg & Persistence）**：
     - 在设置页版本号连续点击 7 次激活开发者模式，并持久化到 `UserPreferences`；
     - 激活后在设置页展现统一的「开发者选项」Row，点击唤起 `DeveloperOptionsBottomSheet`；
     - 提供一键停用开发者模式与重置所有覆写的功能。
  2. **状态响应式派生（Reactive StateFlow）**：
     - 严禁在 Feature / NavHost 业务代码中直接硬编码 `BuildConfig.ENABLE_*` 进行静态短路；
     - 必须通过 `SettingsRepository.isAiAssistantEnabled` / `isInternalPlayerEnabled` 等响应式只读流获取真值，优先消费开发者动态覆写值，未显式覆写时平滑回退到默认编译期配置。
  3. **调试能力统一收敛（Unified Drawer）**：
     - 特性开关覆写（AI / 播放器 / 详细网络与引擎日志）；
     - 原生流化组件模拟（模拟 BT 扩展未安装、重置并清理已下载的动态 `.so`）；
     - 加速镜像前缀覆写（GitHub Release 镜像代理动态调整）；
     - 系统与硬件诊断信息一键导出（机型、ABI 集合、系统版本、Package Name、Version Code）；
  4. **开关必须有真实消费方（No Decorative Toggles）**：
     - 面板里能拨的每个开关都必须在生产源码里至少有一个消费方；只能写设置、只能读设置却没人拿它做事，就是「装饰性开关」——用户拨完以为配置生效、行为毫无变化、全程无提示；
     - 复盘（一次审查连中两例）：`customMirrorPrefixOverride`（自定义加速镜像前缀）此前从未被任何下载路径读取，更新包与 BT 原生扩展照样走默认公共镜像——现已接到 `AndroidAssetDownloader(customMirrorPrefixProvider = ...)`，在 `download()` 冷流内按次读取（`developerSettings.first()`），显式入参优先，并同时覆盖更新包与 `.so` 下载；`verboseLoggingEnabled`（详细网络与引擎日志）此前门限只在 `MiniBgmApp.onCreate` 按 `BuildConfig` 定死一次，Release 包里打开开关依旧只有 Warn 以上日志——现由 `MiniBgmApp` 收集 `developerSettings.verboseLoggingEnabled`（`distinctUntilChanged`）动态调整 `Logger.setMinSeverity`；
     - **反转警示**：`simulateTorrentUninstalled` 的持久化值是 session-scoped 开关的遗留物——引擎侧 `setDebugSimulateUninstalled(true)` 会**删除整个动态 `.so` 目录**，因此严禁在启动期按持久化值回放；该值重启后不再有意义，面板状态以引擎内存态为准。
     - 红线：`ArchitectureRulesTest#developer_panel_settings_must_have_a_real_consumer`（前缀读取器 + DI 接线 + 日志门限 + `distinctUntilChanged`）与 `#downloaded_resource_actions_must_stay_honest`（已落盘禁用「离线下载」+ `completedTaskFor` 排除 `isFileMissing`）。
     - 最近崩溃日志（`CrashLog`）按需查看、复制、系统分享与清理。
---

## 剧本 14：统一分集抽屉与播放源设计系统收敛规范（Unified Episode Drawer & Sources Design System Conformance）
* **规范先例**：SubjectSourcesBottomSheet.kt 与 SubjectDetailOverlays.kt
* **根因警示（来自抽屉套抽屉、多按钮中文裁切与跨 Tab 样式割裂复盘）**：
  * **弹窗套弹窗（反模式）**：点击分集弹「分集微操作抽屉」，在其中点击「播放源」关掉当前抽屉又弹「播放源抽屉」——层级割裂、上下文丢失，让备选播放源彻底沦为隐藏死角；
  * **中文按钮文字截断**：Material 3 Button / FilledTonalButton 默认内边距为两侧各 24dp（合计 48dp）。在 3 按钮横排排版下，每个按钮宽度仅 ~100dp，扣除 48dp 边距与图标后剩余宽度不足 35dp，4 个中文汉字（如「标记已看」）必然被挤压裁切为「标记...」；
  * **跨 Tab 组件漂移（局部最优陷阱）**：在线播放 Tab 使用标准 BgmSourceActionCard（大圆角、左侧图标底盘、主副双行文案、右侧 › 箭头），而 BT 磁力 Tab 为了应付「不能整段隐藏」的测试红线，随手复用了下载卡片内部的 TorrentActionButton 拼凑空态——破坏了设计系统同构性，且漏掉了底栏收起动效。
* **物理防线与操作规范**：
  1. **分集微操作与播放源单一抽屉合流**：
     * 条目页点击任意分集直接路由至 SubjectSourcesBottomSheet(episode = selectedEpisode)；
     * 顶部提供原位切集导航（< 上一集 3 / 28 下一集 >），换集时抽屉无需反复关闭，标题、打卡状态、流媒体直链与 BT 规则在内部流式更新；
     * 分集主起播（Hero 立即播放卡片）直接作为抽屉主体平铺在打卡操作下方，彻底消除抽屉跳抽屉。
  2. **多按钮排版显式设定内边距（防截断铁律）**：
     * 超过 1 个按钮横排分布时，必须显式指定紧凑内容内边距：
       contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)；
     * 严格限制同行动作数量（主操作与打卡分离，操作行保持 <= 2 个按钮），配合极简文案（标记已看 / 取消打卡 / 看到此话），保障 100% 充裕排版宽度。
  3. **设计系统卡片收敛（BgmSourceActionCard 唯一真源）**：
     * 底栏内的一切「规则配置/外部管理」入口，唯一合法组件必须是 BgmSourceActionCard，严禁在不同 Tab 下拼凑私有小按钮；
     * 底栏内部的外部页面跳转动作必须严格经由 
unAfterDismiss / onDismissAndRun 调度收起动画，保持人机交互的顺畅连贯。
  4. **全局播放入口无分集回退与直链嗅探防线（No-Episode Fallback & API Sniffing Guard）**：
     * 条目详情页顶栏 TV 图标等全局播放源入口在未选中分集（selectedEpisodeForSources == null）时，SubjectDetailOverlays 必须按「下一集未看 > 首集」智能回退 ffectiveEpisode，抽屉内部 onlineRules 循环严禁因 pisode == null 整体跳过；
     * API 规则（如 MacCMS）严禁作为媒体直链直接传入 PlayerRoute.streamUrl，isLikelyMediaStream 必须对 .php / pi.php / .json 等接口型 URL 严格断言为非媒体流；
     * 播放器错误浮层（PlayerControlsOverlay）中的「切换下一个源」（onNextSource）与「切换线路」（onOpenSourcesPanel）必须并存呈现，且横向多按钮排版必须带 PaddingValues(horizontal = 12.dp, vertical = 0.dp) 杜绝截字。
  5. **红线拦截**：
     * ArchitectureRulesTest#subject_sources_sheet_must_use_unified_source_action_card_for_management
     * ArchitectureRulesTest#subject_detail_episode_click_must_route_to_unified_sources_sheet


---

## 剧本 15：播放源订阅与规则导入完整性规范（Playback Subscription & Rules Ingestion Integrity）
* **规范先例**：`CommunitySubscriptionService.kt` 与 `SettingsRepository.kt`
* **根因警示（来自订阅丢源与 403 误判存活复盘）**：
  * **连通性探测非硬拦截（测试丢弃反模式）**：订阅源连通性测试（`testSourceConnectivity`）纯用于 UI 延迟排序与诊断展示。MacCMS 源根路径普遍返回 HTTP 403（阻断爬虫/目录遍历，但实际 `/api.php/provide/vod` 接口可用），且移动蜂窝网络下也可能发生瞬时丢包；若在订阅同步落库（`refreshPlaybackSubscription`）时按 `isAlive` 过滤丢弃规则，会导致合法源被大量静默剔除（历史上 5 个可用 MacCMS 源被误删 4 个，仅剩 1 个）；
  * **导入协议单一与脏状态自愈缺失**：规则导入（`importRulesFromJson`）若仅支持裸 `List<PlaybackSourceRule>`，用户粘贴包含 `{"version": 1, "sources": [...]}` 的订阅包 JSON 会直接抛 `MissingFieldException` 导入失败；当订阅 URL 已配置但规则列表因历史版本异常缩减（`<= 1`）时，若无自动自愈机制，用户即使重启应用也无法恢复缺失的规则。
* **物理防线与操作规范**：
  1. **探测状态不污染订阅真值（Save All Ingested Sources）**：
     * `refreshPlaybackSubscription()` 必须将解析出的全部规则（`report.sources`）完整落库持久化，严禁使用 `.filter { it.isAlive }` 做破坏性过滤；
     * 存活探测探针（`CommunitySubscriptionService`）必须将 HTTP 200..499（排除 404 与 408）判定为主机存活（`isAlive = true`），宽容对待 API 服务器根目录的 403 阻断；网络超时门限合理设为 4000ms+。
  2. **多协议 JSON 兼容导入**：
     * `importRulesFromJson` 必须支持多协议分级解析：首选解析裸 `List<PlaybackSourceRule>`，失败时平滑回退解析 `CommunitySubscriptionPackage`（提取 `sources`）、TVBox 配置（`TvBoxConfig`）以及 `DiscoveredSource` 数组，杜绝格式不兼容阻碍用户直接导入；
     * 导入完成后同样执行全量持久化，绝不因未测试连通性而丢弃规则。
  3. **数据流入口自动自愈（Flow Auto-Healing）**：
     * `SettingsRepository.playbackRules` 流必须在启动时通过 `.onStart { triggerSubscriptionHealingIfMissing() }` 守卫订阅完整性：若配置了 `playbackSubscriptionUrl` 但当前规则数量 `<= 1`，后台自愈拉取全量订阅规则并补齐落库，无需用户手动折腾重置。
  4. **红线拦截**：
     * `ArchitectureRulesTest#subscription_sources_must_never_be_discarded_by_probe_status` 静态闭锁 `SettingsRepository` 禁按 `isAlive` 过滤，并校验探测存活判据。
