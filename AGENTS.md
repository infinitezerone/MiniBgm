# MiniBgm AI Agent & Developer Guidelines

MiniBgm：Bangumi（bgm.tv）追番排期与收藏管理客户端。模块化 Clean Architecture，仿 Google *Now in Android*。本文只写"删了会改变行为"的内容；细节一律以代码、测试与钩子为准。

## 版本控制

仓库与 jj colocate，历史操作用 `jj commit` / `jj git push`；提交摘要遵循 Conventional Commits（如 `feat(core:ai): xxx` / `chore: xxx`）。git 只读（log/diff/show/ls-remote/tag/clone）——git 写子命令被 VCS 层的 `tools/githooks` 硬拒，新 clone 跑一次 `tools/githooks/setup.sh` 接线，`tools/jgate` 会自愈补配；jj 不运行 git 钩子，与合法路径零冲突。

- 一个提交一个目的；同一文件别混两个目的——事后 `jj split` 拆不出可编译的中间态。
- 同一时刻只允许一条 Gradle 命令在跑（长构建后台 + 足够超时，避免 daemon 互踩）。
- OAuth 令牌交换/刷新代理在独立的私有 Cloudflare Workers 项目，本仓库搜不到；业务 API 直连 api.bgm.tv。

## 构建与验证

提交前唯一入口（含"绿≠测过"的 XML 真实性校验与提交拦截）：

```bash
# 门禁校验：Windows: .\tools\jgate.ps1  |  Linux/CI: bash tools/jgate
# 门禁绑定提交（拦截器）：Windows: .\tools\jcommit.ps1  |  Linux/CI: bash tools/jcommit -m "feat: ..."
# 流程：Conventional Commits 预检 → 素材体积 → Rust 门禁 → spotlessApply → 架构红线 → 触及模块测试 → :app:assembleDebug → 结果校验 → 结构化诊断
```

- 目标按改动自动推断（工作副本 `@` → 最近提交 `@-`）；支持 `bash tools/jgate --stack`（`-s`）校验当前本地 JJ 变更栈（`trunk()..@`）；触及底层基础层（`:core:model/common/data/network/database/datastore/designsystem/navigation`、`build-logic`、根配置）自动提升为全量；门禁失败自动打印自愈诊断卡并输出结构化诊断报告（`build/reports/jgate/diagnostic.json`）辅助 Agent 自主自愈。日常提交不跑 release；编码中间态秒级红线自检与定向模块微验证（Micro-Eval）使用 `.\tools\quick-check.ps1`（支持传入模块如 `core/network`，`-o` 仅测目标）。推荐使用 `tools/jcommit` 提交代码，强制先验后提。`:app:testDebugUnitTest`（承载 `AppModuleTest` 的 DI 契约红线）横跨全部模块，因此任何模块代码改动都会一并复跑它——只按"改动路径落在哪个模块"推导会漏掉它（`feature/user` 的改动曾让 `DownloadViewModel` 构造期扫盘的回归静默通过门禁）。
- **本机减负开关（两处都显式、都不静默，且会在 jgate 输出里标注）**：① 纯 `*.md` / `docs/` / `.agents/` / `.github/` / LICENSE 改动会自动跳过装配门 `:app:assembleDebug`（架构红线照跑）——这是**显式白名单**而非"没落到模块目录就跳"，`native/`、`tools/`、`crates/` 等真能影响产物的路径仍照常跑装配门；② `bash tools/jgate --fast`（等价 `-Pminibgm.skipScreenshots=true`）跳过截图测试，实测省掉占单测耗时 **43%（59s/137s，仅 15 例）** 的 Roborazzi/Robolectric NATIVE 冷启动开销，但**本机绿 != CI 绿**，提交前必须复跑一次不带 `--fast` 的。
- 单元测试配置（测试 JVM=21、`-Proborazzi.record`、`-Pminibgm.skipScreenshots`）的唯一真源是 `build-logic` 的 `configureAndroidUnitTests()`：带 Robolectric/Roborazzi 的模块直接调用，禁止再各自手抄 `tasks.withType<Test>` 块（历史上三份副本各自漂移）。
- 素材体积门：图片单张 > 200 KB 或合计 > 1 MB 硬拦（必须压为 800px WebP）。jj 不运行 git 钩子，门禁由 jgate 兜底。
- 模块依赖变化后跑 `./gradlew graphUpdate` 重建依赖图；Android Lint 增量基线在各模块 `lint-baseline.xml`。
- Rust 原生库（`crates/minibgm-ech` + `crates/minibgm-torrent`）：构建解耦，底层由原生 CLI `jgate native`（`tools/build-native` 为跨平台薄壳转调）内置确定性 SHA-256 源码指纹缓存并跨平台调度 `cargo-ndk` 产出 `.so`，彻底消灭 Windows 借壳 Git Bash 的二级公民反模式；Gradle 绝不跨界调度 Cargo / NDK，仅通过 `registerPrebuiltJniLibs` 挂载产物并在缺库时硬失败，禁止静默回退。目标 ABI 集合的唯一真源是 `native/abis.txt`——构建脚本、release workflow、`ProjectExtensions` 的产物校验与客户端 `SUPPORTED_ABIS`（`NativeAbiContractTest` 强制）全部与它对齐，改 ABI 只改这一处。`libminibgm_torrent.so` 在 release 中被 `configureOnDemandNativePackaging` 排除出 APK，改由 GitHub Release 资产按需动态下载装载。

## 硬红线

机械执行：源语义由 `:core:testing` 的 ArchitectureRulesTest，依赖图由 build-logic 的 ModuleBoundaryConventionPlugin（配置期断言）。此处只留索引，细节看测试：

1. feature 互不依赖；UI 只经 `:core:data` 仓库；`:core:model` 纯 Kotlin；feature 无原始 IO、无颜色字面量、不外露可变状态（MutableStateFlow 一律禁止；对外只暴露只读 StateFlow，或 Compose State 持有者的只读类型——多字段原子更新用 `Snapshot.withMutableSnapshot`，不用 `update { it.copy(…) }`）；Flow 算子（`map` / `onEach` / `filter` / `transform` 等）lambda 内严禁 `launch` 副作用协程——任务流在下载期间每秒都在发射，每次发射都新起协程且无人等待、无界堆积（`DownloadViewModel` 曾因此在下载页每秒并发触发一次全量磁盘扫描），需要持续刷新必须改用单条收集协程串行执行（StateFlow 自带 conflate，天然背压；ArchitectureRulesTest 物理红线拦截）；路由密封于 `BgmRoute` 并声明堆叠层。
2. 瞬态交互收敛：UI 确认/交互浮层统一由 `BgmOverlayHost` + `overlayHostState.await(...)` 挂起承接，严禁在 feature 中写散落的 `var showDialog` 状态机；通用登录提示收口于 `BgmLoginPromptDialog`；ViewModel 一次性事件（Channel(BUFFERED)）严禁裸 `collect`，必须经 `ObserveAsEvents` 绑定生命周期（STARTED）。一次性事件的用户可见文案必须以 `@StringRes` + 格式化参数下发、UI 侧解析（服务端运行时消息经 message 字段透传），严禁 VM 内联文案绕开资源体系；播放器线路选择收敛于统一面板（PlayerSourcesBottomSheet），选集抽屉专注选集、严禁嵌套平铺切源芯片或保留废弃源选择器条（ArchitectureRulesTest 物理红线拦截）。
3. 可播地址只来自 `findPlayableSources`；一切写操作止于 PENDING_CONFIRMATION 提案卡，用户确认才落库。
4. 凭据只存在于 `AuthTokensDataSource`；不绕过/削弱 `BgmPkce`；不剥离或覆盖 `User-Agent`；关键写操作包 `withContext(NonCancellable)`。
5. DI 真实性与契约闭环：底层平台模块（如 platformDataModule）与 feature ViewModel 必须保持依赖定义闭环；严禁在单测中仅用手工假模块伪造 Koin 验证，必须通过真实容器集成测试（AppModuleTest）断言全量解析，严禁构造器新增依赖后遗漏注册引发运行时 NoDefinitionFoundException。

## 领域规则

- 装饰性功能 fail-open：光晕/动效/横幅等内部异常一律 `runCatching` 降级为"没有装饰"，绝不崩宿主页面；读硬件位图像素前先 `copy` 成软件位图。
- 排期真值只来自 AniList 已验证播出事件；禁止算术预测与合成剧集；bangumi-data 仅作元数据与平台映射，不得污染官方播出时间。
- 复用 `BgmHttpClient.jsonConfig`，不手写 `Json {}`（模块内白名单见 ArchitectureRulesTest）。
- Ktor 401 自动刷新；刷新彻底失败自动登出——调用方永远不会看到 401。
- 播放器分集会话：续播点按**分集**维度重算——换集动作（`selectEpisode` / `switchTo`）必须按新分集自己的记录下发 `resumePositionMs`；规则源换集时新地址尚不可知，必须把查表推迟到嗅探出地址那一刻（`consumePendingResumePositionForNewEpisode`），同分集内换线路才沿用当前进度，绝不沿用上一集的时间轴（否则新分集跳到旧分集进度，短集会瞬间播完并连播空跳）。`PlayerController` 重置媒体态只允许重置媒体相关字段，必须显式保留引擎侧偏好（`isLooping` 由引擎 `repeatMode` 持有），严禁 `PlaybackState()` 无参整体重置（ArchitectureRulesTest 物理红线拦截）。ExoPlayer 引擎的 `DefaultMediaSourceFactory` 必须经 `DefaultDataSource.Factory(context, httpDataSourceFactory)` 包装以支持离线下载（`file://`）视频回放，严禁直传 `httpDataSourceFactory`（ArchitectureRulesTest 物理红线拦截）。播放器线路生成必须剔除 `PlaybackRuleKind.TORRENT`（BT 种子由离线下载面板承接，严禁作为在线流媒体线路呈现）。
- 流媒体发流、P2P 引擎与 ECH 优选池：
  - **原生插件安全与生命周期**：严禁派发未就绪的空 Socket 伪地址；扩展下载与后台大任务严禁静默触发，必须经 `BgmOverlayHost` 显式确认（严禁暴露无确认的 `checkOrInstallEngine` 后门）；按需动态下载的 `.so` 必须按应用版本隔离目录并清理旧版残留、加 `Mutex` 互斥，且在 `System.load` 前调用 `setReadOnly()` 满足 Android 10+ W^X 安全约束（`ArchitectureRulesTest` 物理红线拦截）。
  - **librqbit 会话约束**：会话必须配置后缀过滤、单集锁定（`only_files`）、`overwrite: true`（防二次启动报 `os error 17`）、`resolve_target_file_path` 暴露真实扩展名落盘路径及异常初始化 `cancel_token.cancel()` 守卫；必须以 `paused: true` 添加种子、待 `update_only_files` 锁定单集后才 `session.unpause` 开流（元数据阶段零下载，杜绝"先按整包视频选片、再收敛单集"的白下窗口）；目标单集由 `select_target_video_file` 纯函数挑选，多视频文件未命中请求剧集（`episodeHint`）必须显式失败，严禁静默回退体积最大文件；视频扩展名白名单的单一真源是 `VIDEO_EXTENSIONS`（同时派生 `only_files_regex` 与 `is_video_file`，且集数后缀白名单必须覆盖 `04v2` 这类重制命名）；本地 HTTP API 严格置 `read_only: true`。
  - **单盘落盘与抢占调度**：离线下载（`AndroidTorrentRepository`）必须传入持久化 `saveDirPath` 并通过 `getActiveTargetFilePath` + `monitorDirectEngineDownload` 直接复用 librqbit 单盘落盘与串行队列 `scheduleNextIfIdle`（严禁走二次 HTTP 回环顺序流双倍写盘）；边下边播抢占、暂停（`pauseDownload`）与取消（`cancelDownload`）后台任务时必须先同步停旧会话、`cancelAndJoin` 等待协程退出再调度下一项，设抢占守卫切断 `finally` 打架并在被取消/异常协程中禁止以 `ERROR` 冲刷 `QUEUED` 或 `PAUSED` 状态；任务恢复（`restorePersistedTasks`）必须通过门闩并合并内存任务防止覆盖并发创建的新任务；下载进度轮询严禁高频全量写库，仅限状态迁移与完成时通过单行通道有序写入；通知文案严禁使用反射 `getIdentifier` 跨模块动态查找资源（`ArchitectureRulesTest` 物理红线拦截）。
  - **死种看门狗与起播守卫**：看门狗探针（`peerConnectTimeoutMs`）严禁被仅有连接但零速度（`speedBytesPerSec == 0` 且无字节前进）的死种 Peer 刷新；落盘完成必须断言磁盘物理文件真实存在与非空；UI 边下边播起播等待必须包裹单次获取超时守卫（`withTimeout`）防止死种无界挂起。
  - **双消费者引用保护**：流引擎通过 `downloadSessionProtected` + `playerSessionActive` 双消费者引用守卫确保边下边播退出播放器或下载刚满 100% 时互不掐断；播放器端 `playerSessionActive` 必须与当前活跃播放的 `streamUrl`（是否为本地回环流）动态联动，严禁切换至在线线路或退出播放后残留悬挂。
  - **分集隔离与种子载荷自愈**：合集/BD 包内所有分集共用同一磁力，因此「去重键」（`AndroidTorrentRepository.startDownload`）、「流会话复用判据」（`AndroidTorrentStreamEngine.startStream` 的 `activeEpisodeHint`）与「已完成任务快捷复用」（`SubjectSourcesBottomSheet`）三处都必须以分集身份键 `TorrentTask.episodeKey`（`torrentEpisodeKey`：`id:` > `sort:` > `name:` 三级降级）隔离——只比 infoHash 会让点第 N 集命中第 M 集的既有任务，直接复用其 `streamUrl` 或 `file://` 落盘路径，静默播错集且全程无提示；种子（`.torrent`）载荷在**下载落盘、缓存命中与 DB 持久化路径复用（`runTaskPipeline` 读回 `task.torrentFilePath`）三个入口**都必须经 `TorrentFileCache.isValidPayload` 校验（bencode 字典 + `4:info`），镜像返回 HTTP 200 的 HTML 错误页同样会被 `AssetDownloader` 按字节数判成功，坏种子必须就地删除重下（离线下载会把路径写进 DB 的 `torrentFilePath`，任一入口漏校验都会在重启后永久命中），缓存键也必须 infoHash 优先，严禁只按标题命名；起播等待超时必须转为 `Result.failure`，严禁用 `getOrThrow()` 抛 `CancellationException` 连带取消调用方协程（UI 的 `onFailure` 将永不执行，用户全程无提示）。
  - **网络分流与 ECH 边缘节点保护**：`AssetDownloadService` 必须将 `http://127.0.0.1` 本地回环流分流至流式 `CIO` 引擎（严禁走仅支持 `https://` 且全量内存缓冲的 `EchHttpClientEngine`）；`EchHttpClientEngine` 捕获 `CancellationException` 时仅限在真实请求超时（`TimeoutCancellationException`）下调用 `recordFailure`，严禁因常规协程取消误杀 `EchEdgePool` 黄金边缘节点。
  - **P2P 协议与种子源中立性**：核心应用保持 100% 协议与站点中立，严禁在 `:core:data`、`:core:network` 与 `:feature:subject` 中硬编码特定第三方未授权种子站点域名（如 `mikanani.me` 专有 API 抓取，`ArchitectureRulesTest` 物理红线拦截）；P2P 资源发现统一由用户配置的通用 `PlaybackRuleKind.TORRENT`（BT RSS 规则集）驱动，未配置或未启用任何种子规则时零网络请求。
  - **零第三方广播与公共 Tracker 禁令**：严禁在代码中硬编码公共 Tracker 列表注入请求（例如给合成磁链拼 `&tr=`）——公共 Tracker 会把用户本机 IP 与 infohash 广播给未知第三方，且域名存活期腐烂；解析器合成的磁链只保留 `xt=urn:btih:<hash>`，寻道交由 DHT（Rust 侧 `SessionOptions.dht` 已启用），仅 Feed 自带磁链才原样透传其自身声明的 Tracker（`ArchitectureRulesTest` 物理红线拦截）。
  - **种子检索别名回退与仓库契约闭环**：`TorrentFeedRepository.getTorrentResources` 的 `alternativeTitles` 必须是真实读取的参数——主标题检索为空（站点常按原名/别名索引）时必须按声明顺序逐个回退重试，首个非空结果即返回（主标题命中时零额外请求），解析出的 URL 还要去重，规则模板不含 `{title}` 时严禁对同一 Feed 重复拉取；仓库接口声明的每个参数都必须在实现体内被真实读取，严禁只留签名不写实现的假契约（`ArchitectureRulesTest` 物理红线拦截）。Feed 内存缓存必须有容量上限（插入序淘汰）并以 `Mutex` 串行化——详情页与分集页会并发检索同一 Feed，裸 `MutableMap` 的并发结构修改会损坏桶链，无上限缓存会随关键词无限增长。
- 内置 WebView 与本地代理闭环：自定义 `WebChromeClient` 严禁忽略控制台日志，必须实现 `onConsoleMessage` 透传至 Logcat（ArchitectureRulesTest 物理红线拦截），消灭前端排查盲区；本地反向代理改写 HTML 资源时必须同步改写 `Content-Security-Policy` 响应头中的对应源，杜绝只改响应体导致浏览器安全策略阻断。
- 资产下载与落盘互斥：通用资产下载契约（`AssetDownloader`）必须面向请求作用域冷流（`Flow<DownloadState>`）设计，严禁单例持有全局活跃下载状态或暴露无参全局 `cancelDownload` 误伤其他并发任务；调用方业务层（如 `UpdateRepository`）自行持有专有任务 `Job` 与 `StateFlow`；落盘同一 `destinationPath` 必须受路径级 `Mutex` 互斥保护，杜绝并发写入损坏中转文件（`.tmp`）。
- 现代开发面板与运行时调试收敛：特性开关与调试状态严禁仅靠硬编码 `BuildConfig` 静态布尔判断；必须经由 `DeveloperSettings` 与 `SettingsRepository` 暴露响应式状态流，支持真机/发版预览时动态覆写（7 次连击彩蛋激活）。UI 调试入口收敛于统一抽屉（`DeveloperOptionsBottomSheet`），严禁在业务页面散落内联的调试状态开关。
- 数据库版本策略（开发测试阶段）：`BgmDatabase.version` 固定为 **1**，**不写任何 Migration**，结构变更一律靠 `fallbackToDestructiveMigration(dropAllTables = true)` 整体重建；`exportSchema = false`（没有迁移就没有 schema 快照的用途）。**任何实体 / DAO 变更必须同步把 `version` 加一**——Room 在版本号未变而结构已变时会抛 `Room cannot verify the data integrity`，破坏性回退不兜这种情况。正式发布需要保全用户数据之前，先打开 `exportSchema` 从当前结构导出基线，再逐版本补 `Migration` 与防漂移 schema 测试。

## 规范先例与协同流

新模式的唯一入口，不适配就停下提决策，勿发明新模式：只读特性 → `:feature:schedule`；带参详情 → `:feature:subject`；写操作 → `CollectionRepository`；认证 → `AuthRepository` + `BgmPkce`；新红线 → 先写 ArchitectureRulesTest。详细实现剧本参见 `.agents/skills/minibgm-playbooks/SKILL.md`。

AI 协同流水线（机械执行，严禁甩锅排错）：

1. **前置反诘（Spec 对齐与发射架）**：编码前反查硬红线与先例，输出 Mini-Spec 与改动栈；多 Agent 接力或跨切面任务统一暂存至根目录 `.task.md`（发射架机制：受 .gitignore 保护不入库，完工且门禁通过后即删），用户确认后开工。
2. **原子切片（Task Stacks）**：自底向上分层实现（model/data → ViewModel → UI），单次提交单一目的；中间态用 `.\tools\quick-check.ps1` 秒级自查红线。
3. **经验固化（Flywheel 飞轮，跑 `jgate` 前前置必检）**：凡完成缺陷修复、审查整改或架构重构，跑 `jgate` 终验前必须主动执行《MiniBgm 飞轮协议》（Encode lessons into structure，直接固化入 `ArchitectureRulesTest`、`AGENTS.md` 与 `.agents/skills/minibgm-playbooks`，不留纯文本日记，详见 `.agents/skills/minibgm-flywheel/SKILL.md`），严禁等用户提醒才补跑飞轮。
4. **自愈验证（Autonomous Loop）**：连同飞轮新增的红线单测一起自主运行 `.\tools\jgate.ps1`（或 `bash tools/jgate`），若未通过必须自行抓取日志修复直至全绿，严禁让用户试跑。

## 发布

tag 是版本唯一真源：推 `vX.Y.Z` 经 `-Pminibgm.versionName/-Pminibgm.versionCode` 注入构建；发布门禁在 release.yml（缺签名密钥直接失败）。勿为发版手改 build-logic 的默认版本——那是 v0.2.8 事故的根源。

ABI 分包：debug/日常构建**只产 1 个 APK**（`app/build.gradle.kts` 的 `enableAbiSplits` 默认 `false`——开启时 debug 要打 2 个 ABI 包 + 1 个 universal 包，实测 363MB 写盘，是每次 `assembleDebug` 都在付的重复开销）。发版必须产出分 ABI + universal 产物，由 release.yml 传 `-Pminibgm.abiSplits=true` 开启，并有产物断言步骤判死；**勿把仓库默认值改回 `true`**。
