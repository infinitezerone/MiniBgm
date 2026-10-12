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
  * **面板类浮层（持续面板/抽屉）同样走 OverlayRequest**：`var xxxOpen` 布尔状态机的关闭时机要在返回键回退链、全屏切换、横竖屏切换、进 PiP 多处手动同步，且多个标记可同时为 true（叠层）。收敛进 `BgmOverlayHost` 后单槽天然互斥、每个面板自带 `BackHandler`（优先级由单槽语义自动保证）、打开-关闭-取结果写在同一处挂起调用。带退出动画的抽屉用 `MutableTransitionState` 把 respond 延迟到动画播完；宿主状态切换（全屏/PiP）用 `overlayHostState.cancelActive()` 收起面板。参照 `PlayerPanelOverlays.kt`。
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
- **UI 自查无需设备（Roborazzi + Robolectric）**：改 Compose 面板/控制层后写截图测试自查，输出到 `src/test/snapshots/images/` 再读图检查。要点：模块需接 roborazzi 四件套 + `testOptions.unitTests.isIncludeAndroidResources` + Test 任务 `roborazzi.test.record=true` 与 JDK 21 launcher（参照 `core/designsystem/build.gradle.kts` 与 `feature/subject/PlayerPanelsScreenshotTest.kt`）；`@Config(qualifiers = "w420dp-h1600dp")` 控制视口尺寸（默认窗口仅 320dp 宽，横排按钮会溢出"消失"）；深色背景模拟视频底，否则白色控制图标"隐形"；LazyColumn 跨分组 items key 必须唯一。

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
6. **依赖约束先看契约源码，不看搜索摘要**：
   * 接口有没有端点、是否强制验证码、是否支持原生发帖（如 P-005 Turnstile 阻塞），必须查验 OpenAPI 或实测返回，不盲信模型搜索猜测。

---

## 剧本 8：原生流播与后台传输真实性规范（Stream Engine & Native Fidelity）
* **规范先例**：`crates/minibgm-torrent` 与 `AndroidTorrentStreamEngine`
* **流媒体发流真实性**：
  * 严禁在生产代码中使用空 `ServerSocket(0)` 占位或以纯文本假装动态库（如已废止的 `minibgm_streamer_placeholder` 反模式）；
  * 流媒体引擎向播放器派发 `127.0.0.1:port` 地址前，本地 HTTP 守护服务必须已真实就绪，并严格支持 HTTP 206 Partial Content / Range 头部响应（满足播放器 moov/Seek 元数据探针需求）；
  * 若底层 Native 模块或资源未就绪，必须显式抛出异常或下发 `TorrentStreamState.Error` 阻断，绝不允许吞掉初始化超时派发不可达的伪地址导致播放器静默超时崩溃（`SocketTimeoutException`）。
* **纯内存流播防 OOM 与接口安全（librqbit）**：
  * **单集严格锁定与候选过滤**：番剧 BD/合集种子常含十几集几十 GB 文件，纯内存存储（`InMemoryExampleStorageFactory`）无自动 LRU 淘汰。添加种子时必须配置 `only_files_regex` 过滤视频，并在元数据就绪后，严格通过 `f.included && is_video_file(&f.name)` 双重白名单选取最大视频，绝不在全量文件中盲取最大（防止非视频 ISO/解压包夺占正片），再调用 `session.update_only_files(&handle, &HashSet::from([target_file_id]))` 锁定目标单集，杜绝全季文件同时进 RAM 导致 OOM；
  * **本地控制面封死**：本地 HTTP API 必须配置 `read_only: true` 与 `allow_create: false`，避免同一设备上其他恶意进程扫描回环端口向会话注入种子或篡改状态；
  * **禁止擅自注入公共 Tracker（零第三方广播泄露）**：严禁在代码中硬编码公共 Tracker 列表注入请求。公共 Tracker 会将用户本机 IP 与 infohash 广播给未知的第三方服务器，且域名存在存活期腐烂失效问题；严格只使用磁链自身声明的 Tracker 或纯经由分布式 DHT 网络寻道；
  * **进度分母真实性**：进度分母必须采用当前目标单集的真实字节大小（`target_file_len`），分子采用 `stats.file_progress[target_file_id]`，禁止使用全种子大小（会导致进度永远不到 100%）或写死假兜底分母；
  * **应用内下载真实性（绝不留 Fake 循环）**：离线落盘与在线内存流播必须泾渭分明。未接入真实持久化落盘管道前，严禁在 Repository/Service 中写死 `2.5MB/s` 拟真推进循环与硬编码 peers 欺骗用户；能力未就绪必须直接下发明确状态阻断并引导外部专业客户端。
* **重要写操作/外部下载前置确认**：
  * 任何涉及消耗流量下载扩展组件或启动后台大任务的操作，必须通过 `BgmOverlayHost` + `overlayHostState.await(ConfirmDialogAction(...))` 获得用户显式确认后方可执行。

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

