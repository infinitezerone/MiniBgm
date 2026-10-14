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
   * 用"整体覆盖"重置媒体态（`_state.value = PlaybackState(...)`）时，任何没被显式带过去的字段都等于被悄悄改回默认值。单集循环 `isLooping` 由引擎 `repeatMode` 持有、UI 只持状态副本，于是出现"面板显示已关闭、视频仍在循环"的幽灵态（开关要点两次才关得掉）。正确形态：重置只覆盖媒体相关字段，偏好类字段显式保留并与引擎保持一致——`PlayerController.setMedia` 里 `val looping = _state.value.isLooping`，且 URL 变化、URL 清空两条路径都要覆盖；新增"重置媒体态"的写法时先问一句"哪些字段是引擎/用户偏好、删了会变行为"。
9. **AGP 的 `splits` 是全局 DSL，没有"按变体开关"的公开 API（9.3.2 的 `Splits` 只在 `com.android.build.api.dsl`）**：
   * 所以"debug 单包 / release 分包"只能靠**项目属性 + 工作流保证**：`app/build.gradle.kts` 里 `val enableAbiSplits = (project.findProperty("minibgm.abiSplits") as? String)?.toBoolean() ?: false`，发版时由 `release.yml` 传 `-Pminibgm.abiSplits=true`。改这类"默认关、发版开"的开关时，**必须在发版链路补产物断言**（缺包直接 exit 1），否则一改就静默退化成 fat APK，下游的分 ABI 资产与 universal 别名全部变形。
10. **续播点属于"分集"而不是"上一个地址"**：
   * 任何换集入口都必须显式按新分集查表下发 `resumePositionMs`。直链换集时新地址当下可知，当场查表；规则源换集时旧画面被故意保留、新地址要等嗅探结果，只能把查表推迟到解析成功那一刻一次性消费（`consumePendingResumePositionForNewEpisode`），而同分集内换线路必须沿用当前进度（不可消费）。只靠"`resumePositionMs == 0` 才回填"的仓库快照回填会漏掉换集路径，把上一集进度当成新分集续播点下发，导致新分集跳到旧分集时间轴、短集瞬间播完并连播空跳。新增换集入口时，同步补一条"换集后 `resumePositionMs` 等于新分集自己的记录"的回归单测。
11. **离线视频播放器工厂与种子规则隔离**：
   * ExoPlayer 引擎的 `DefaultMediaSourceFactory` 必须经 `DefaultDataSource.Factory(context, httpDataSourceFactory)` 包装。若裸传 `httpDataSourceFactory`，Media3 播放离线下载的 `file://` 视频时会强转为 `HttpURLConnection` 造成 `ClassCastException` 崩溃；
   * 播放器线路构建（`PlayerViewModel.buildSources`）必须过滤 `kind != PlaybackRuleKind.TORRENT`，避免将 BT 种子嗅探规则误暴露给播放器作为普通在线流；
   * 剧集队列匹配条件必须允许 `episodeSort >= 0f`（第 0 话序章），剧集标签展示需通过 `toEpisodeLabel()` 保留半集/总集篇小数字号（如 0.5、6.5）。

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
  * **进度分母真实性**：进度分母必须采用当前目标单集的真实字节大小（`target_file_len`），分子采用 `stats.file_progress[target_file_id]`，禁止使用全种子大小（会导致进度永远不到 100%）或写死假兜底分母；
  * **应用内下载真实性（绝不留 Fake 循环）**：离线落盘与在线内存流播必须泾渭分明。未接入真实持久化落盘管道前，严禁在 Repository/Service 中写死 `2.5MB/s` 拟真推进循环与硬编码 peers 欺骗用户；能力未就绪必须直接下发明确状态阻断并引导外部专业客户端。
* **按需动态 `.so` 扩展下发与装载（On-Demand Native Plugin Delivery）**：
  * **构建与打包解耦与本地调试面板**：Rust 原生库统一由 `tools/build-native` 编译，ABI 真源唯一收口于 `native/abis.txt`（`NativeAbiContractTest` 强制对齐）；`libminibgm_torrent.so` 在 `debug` 随包内嵌供本地零配置调试，在 `release` 经 `configureOnDemandNativePackaging` 排除出 APK 并上传至 GitHub Release 按需拉取；本地若需在 Debug 包中免重编测试“未安装 -> 弹窗确认 -> 动态下载 `.so` -> `System.load`”完整链路，统一使用设置页内置的 `DeveloperDebugSettingsCard`（仅 Debug 可见，调用 `TorrentStreamEngine.setDebugSimulateUninstalled(true)` 屏蔽内嵌 `.so` 回退并清理私有目录动态库），不引入额外 Gradle 构建属性；
  * **版本隔离与平滑回退**：动态 `.so` 必须落盘于版本化子目录 `filesDir/modules/torrent/v{versionName}/libminibgm_torrent.so`，安装前调用 `cleanStalePluginDirs` 清理历史旧版本目录；下载优先请求与当前版本一致的 Release Tag（`releases/download/v{version}/...`），404 时回退至 `releases/latest/download/...`；`isPluginInstalled()` 每次必须复检目标文件存在且与当前版本一致；
  * **Android 10+ W^X 只读约束与并发互斥**：从应用私有目录调用 `System.load(absolutePath)` 前，必须先对 `.so` 文件调用 `targetSoFile.setReadOnly()`（否则 API 29+ 因 W^X 内存保护直接抛 `UnsatisfiedLinkError: Permission denied`）；`installPlugin()` 必须由 `Mutex` 保护防并发重入，并通过注入 `NativeTorrentBridge` 接口保障 JVM 单元测试覆盖率；
  * **重要写操作/外部下载前置确认**：任何涉及消耗流量下载扩展组件或启动后台大任务的操作，必须收口于单一确认辅助函数（如 `ensureTorrentPluginInstalled`），经 `BgmOverlayHost` + `overlayHostState.await(ConfirmDialogAction(...))` 获得用户显式确认后方可执行，严禁在 Repository 暴露无确认静默下载入口（如已废止的 `checkOrInstallEngine`）；
  * **边下边播与离线下载单盘合一与双消费者生命周期协同**：离线下载（`AndroidTorrentRepository`）严禁通过 `http://127.0.0.1` 把 `librqbit` 已写入磁盘的文件再拉取拷贝第二遍（会导致双倍磁盘占用、倍增闪存磨损、阻塞于分片 0 顺序读取以及强行篡改 `.mkv` 扩展名为 `.mp4`）。正确范式：`AndroidTorrentRepository` 向 `streamEngine.startStream` 传入持久化 `saveDirPath`，由 `librqbit`（`resolve_target_file_path` / `getActiveTargetFilePath`）直接落盘真实扩展名文件，并通过 `monitorDirectEngineDownload` 监听分片完成度；由于 `.torrent` 直链种子无需等待磁链元数据解析即进入 `Buffering(0f, 0L, target, 0L, 0)` 且 `StateFlow` 会自动去重合并相等状态，`monitorDirectEngineDownload` 必须内置独立协程超时守卫（`peerConnectTimeoutMs`），且活跃进度判据严禁被仅有连接但零速度（`speedBytesPerSec <= 0L && !bytesAdvanced`）的假 Peer 重置，持续超时即主动熔断为 `TorrentTaskStatus.ERROR`（`PEER_CONNECT_TIMEOUT_MESSAGE`）并唤醒后续排队任务；多下载任务通过 `scheduleNextIfIdle` 串行排队并经 `BgmHttpClient.jsonConfig` 持久化至 `tasks_manifest.json`；边下边播抢占正在执行的后台下载时，必须先同步关停旧会话（`streamEngine.stopStream(force = true)`）再执行 `preemptedJob.cancelAndJoin()`，并在抢占期间加锁（`isPreempting`）让旧协程 `finally` 中的自动调度失效，同时在下载协程的异常捕获中加入状态守卫（已置为 `QUEUED` 的被抢占任务禁止被覆盖为 `ERROR`）；UI 发起边下边播等待（`startStreamDownload`）必须包裹单次获取超时守卫（`withTimeout`，如 60 秒），防止死种导致 UI 协程无限挂起，且下载完成落盘时必须断言物理文件真实存在且字节数大于 0；流引擎维护 `downloadSessionProtected`（后台下载持有）与 `playerSessionActive`（前台播放器持有）双消费者守卫——下载中任务暴露 `streamUrl` 供随时切入边下边播，退出播放器调用 `releasePlayerSession()` 时若后台下载仍在进行则绝不关停引擎，后台下载刚满 100% 时若播放器仍在消费也绝不中途掐断 HTTP 流；
  * **本地回环流分流与文件复写守卫**：`AssetDownloadServiceImpl` 面对 `http://127.0.0.1` / `localhost` 的本地回环 P2P 流时，必须分流至独立的纯流式 `HttpClient(CIO)`（严禁交由仅支持 `https://` 且在 JNI 内存全量缓冲的 `EchHttpClientEngine`）；`crates/minibgm-torrent/src/server.rs` 的 `AddTorrentOptions` 必须显式置 `overwrite: true`（防止重复播放/下载同一剧集时因缓存文件已存在报 `File exists (os error 17)`），且初始化中途失败时必须立即调用 `cancel_token.cancel()` 释放 DHT / HTTP 后台协程与端口；
  * **ECH 边缘优选池防协程取消污染**：`EchHttpClientEngine` 捕获 `CancellationException` 时必须区分真实请求超时（`TimeoutCancellationException` 或 `remainingMillis <= 0`）与常规协程取消（如 Coil 滚动取消头像加载 `JobCancellationException`），严禁在常规取消时调用 `AdaptiveDnsResolver.recordFailure(host)` 把 `EchEdgePool` 中的健康 Cloudflare 节点打满 `consecutiveFailures` 驱逐出池；`retry_configs` 自愈重试时必须通过 `prioritize_reachable_addr` 优先复用刚刚握手响应的边缘 IP，杜绝重新排在超时死节点后面再耗时等待。

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
  2. **详情页起播抽屉（SubjectSourcesBottomSheet）现代分层布局**：
     * **顶部 Header**：分集标题与状态；
     * **主起播入口**：“立即起播”Hero 卡片；
     * **在线播放线路**：在线流媒体规则线路；
     * **P2P 原画与字幕组资源**：按字幕组归类的种子资源卡片，支持“边下边播”与“离线缓存”；未配置规则时引导用户配置；
     * **辅助工具与外部跳转**：AI 找源、外部跳转与规则管理入口。

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
     - 最近崩溃日志（`CrashLog`）按需查看、复制、系统分享与清理。
