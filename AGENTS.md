# MiniBgm AI Agent & Developer Guidelines

MiniBgm：Bangumi（bgm.tv）追番排期与收藏管理客户端。模块化 Clean Architecture，仿 Google *Now in Android*。本文只写"删了会改变行为"的内容；细节一律以代码、测试与钩子为准。

## 版本控制

仓库与 jj colocate，历史操作用 `jj commit` / `jj git push`；提交摘要遵循 Conventional Commits（如 `feat(core:ai): xxx` / `chore: xxx`）。git 只读（log/diff/show/ls-remote/tag/clone）——git 写子命令被 VCS 层的 `tools/githooks` 硬拒，新 clone 跑一次 `tools/githooks/setup.sh` 接线，`tools/jgate` 会自愈补配；jj 不运行 git 钩子，与合法路径零冲突。

- 一个提交一个目的；同一文件别混两个目的——事后 `jj split` 拆不出可编译的中间态。
- 同一时刻只允许一条 Gradle 命令在跑（长构建后台 + 足够超时，避免 daemon 互踩）。
- OAuth 令牌交换/刷新代理在独立的私有 Cloudflare Workers 项目，本仓库搜不到；业务 API 直连 api.bgm.tv。

## 构建与验证

提交前唯一入口（含"绿≠测过"的 XML 真实性校验）：

```bash
# Windows: .\tools\jgate.ps1  |  Linux/CI: bash tools/jgate
# 流程：素材体积 → Rust 门禁 → spotlessApply → 架构红线 → 触及模块测试 → :app:assembleDebug → 结果校验
```

- 目标按改动自动推断（工作副本 `@` → 最近提交 `@-`）；触及底层基础层（`:core:model/data/network/database/datastore`、`build-logic`、根配置）自动提升为全量。日常提交不跑 release；编码中间态秒级红线自检使用 `.\tools\quick-check.ps1`。
- 素材体积门：图片单张 > 200 KB 或合计 > 1 MB 硬拦（必须压为 800px WebP）。jj 不运行 git 钩子，门禁由 jgate 兜底。
- 模块依赖变化后跑 `./gradlew graphUpdate` 重建依赖图；Android Lint 增量基线在各模块 `lint-baseline.xml`。
- Rust 原生库（`crates/minibgm-ech` + `crates/minibgm-torrent`）：构建解耦，统一由 `tools/build-native`（内置源码 SHA-256 指纹缓存）调用 `cargo-ndk` 产出 `.so`；Gradle 绝不跨界调度 Cargo / NDK，仅通过 `registerPrebuiltJniLibs` 挂载产物并在缺库时硬失败，禁止静默回退。目标 ABI 集合的唯一真源是 `native/abis.txt`——构建脚本、release workflow、`ProjectExtensions` 的产物校验与客户端 `SUPPORTED_ABIS`（`NativeAbiContractTest` 强制）全部与它对齐，改 ABI 只改这一处。`libminibgm_torrent.so` 在 release 中被 `configureOnDemandNativePackaging` 排除出 APK，改由 GitHub Release 资产按需动态下载装载。

## 硬红线

机械执行：源语义由 `:core:testing` 的 ArchitectureRulesTest，依赖图由 build-logic 的 ModuleBoundaryConventionPlugin（配置期断言）。此处只留索引，细节看测试：

1. feature 互不依赖；UI 只经 `:core:data` 仓库；`:core:model` 纯 Kotlin；feature 无原始 IO、无颜色字面量、不外露可变状态（MutableStateFlow 一律禁止；对外只暴露只读 StateFlow，或 Compose State 持有者的只读类型——多字段原子更新用 `Snapshot.withMutableSnapshot`，不用 `update { it.copy(…) }`）；路由密封于 `BgmRoute` 并声明堆叠层。
2. 瞬态交互收敛：UI 确认/交互浮层统一由 `BgmOverlayHost` + `overlayHostState.await(...)` 挂起承接，严禁在 feature 中写散落的 `var showDialog` 状态机；通用登录提示收口于 `BgmLoginPromptDialog`；ViewModel 一次性事件（Channel(BUFFERED)）严禁裸 `collect`，必须经 `ObserveAsEvents` 绑定生命周期（STARTED）。一次性事件的用户可见文案必须以 `@StringRes` + 格式化参数下发、UI 侧解析（服务端运行时消息经 message 字段透传），严禁 VM 内联文案绕开资源体系；播放器线路选择收敛于统一面板（PlayerSourcesBottomSheet），选集抽屉专注选集、严禁嵌套平铺切源芯片或保留废弃源选择器条（ArchitectureRulesTest 物理红线拦截）。
3. 可播地址只来自 `findPlayableSources`；一切写操作止于 PENDING_CONFIRMATION 提案卡，用户确认才落库。
4. 凭据只存在于 `AuthTokensDataSource`；不绕过/削弱 `BgmPkce`；不剥离或覆盖 `User-Agent`；关键写操作包 `withContext(NonCancellable)`。
5. DI 真实性与契约闭环：底层平台模块（如 platformDataModule）与 feature ViewModel 必须保持依赖定义闭环；严禁在单测中仅用手工假模块伪造 Koin 验证，必须通过真实容器集成测试（AppModuleTest）断言全量解析，严禁构造器新增依赖后遗漏注册引发运行时 NoDefinitionFoundException。

## 领域规则

- 装饰性功能 fail-open：光晕/动效/横幅等内部异常一律 `runCatching` 降级为"没有装饰"，绝不崩宿主页面；读硬件位图像素前先 `copy` 成软件位图。
- 排期真值只来自 AniList 已验证播出事件；禁止算术预测与合成剧集；bangumi-data 仅作元数据与平台映射，不得污染官方播出时间。
- 复用 `BgmHttpClient.jsonConfig`，不手写 `Json {}`（模块内白名单见 ArchitectureRulesTest）。
- Ktor 401 自动刷新；刷新彻底失败自动登出——调用方永远不会看到 401。
- 流媒体发流、P2P 引擎与 ECH 优选池：
  - **原生插件安全与生命周期**：严禁派发未就绪的空 Socket 伪地址；扩展下载与后台大任务严禁静默触发，必须经 `BgmOverlayHost` 显式确认（严禁暴露无确认的 `checkOrInstallEngine` 后门）；按需动态下载的 `.so` 必须按应用版本隔离目录并清理旧版残留、加 `Mutex` 互斥，且在 `System.load` 前调用 `setReadOnly()` 满足 Android 10+ W^X 安全约束（`ArchitectureRulesTest` 物理红线拦截）。
  - **librqbit 会话约束**：会话必须配置后缀过滤、单集锁定（`only_files`）、`overwrite: true`（防二次启动报 `os error 17`）、`resolve_target_file_path` 暴露真实扩展名落盘路径及异常初始化 `cancel_token.cancel()` 守卫；多视频文件种子必须依据请求剧集（`episodeHint`）精确匹配目标视频，未命中才回退单集最大文件；本地 HTTP API 严格置 `read_only: true`。
  - **单盘落盘与抢占调度**：离线下载（`AndroidTorrentRepository`）必须传入持久化 `saveDirPath` 并通过 `getActiveTargetFilePath` + `monitorDirectEngineDownload` 直接复用 librqbit 单盘落盘与串行队列 `scheduleNextIfIdle`（严禁走二次 HTTP 回环顺序流双倍写盘）；边下边播抢占、暂停（`pauseDownload`）与取消（`cancelDownload`）后台任务时必须先同步停旧会话、`cancelAndJoin` 等待协程退出再调度下一项，设抢占守卫切断 `finally` 打架并在被取消/异常协程中禁止以 `ERROR` 冲刷 `QUEUED` 或 `PAUSED` 状态；任务恢复（`restorePersistedTasks`）必须通过门闩并合并内存任务防止覆盖并发创建的新任务；下载进度轮询严禁高频全量写库，仅限状态迁移与完成时通过单行通道有序写入；通知文案严禁使用反射 `getIdentifier` 跨模块动态查找资源（`ArchitectureRulesTest` 物理红线拦截）。
  - **死种看门狗与起播守卫**：看门狗探针（`peerConnectTimeoutMs`）严禁被仅有连接但零速度（`speedBytesPerSec == 0` 且无字节前进）的死种 Peer 刷新；落盘完成必须断言磁盘物理文件真实存在与非空；UI 边下边播起播等待必须包裹单次获取超时守卫（`withTimeout`）防止死种无界挂起。
  - **双消费者引用保护**：流引擎通过 `downloadSessionProtected` + `playerSessionActive` 双消费者引用守卫确保边下边播退出播放器或下载刚满 100% 时互不掐断；播放器端 `playerSessionActive` 必须与当前活跃播放的 `streamUrl`（是否为本地回环流）动态联动，严禁切换至在线线路或退出播放后残留悬挂。
  - **网络分流与 ECH 边缘节点保护**：`AssetDownloadService` 必须将 `http://127.0.0.1` 本地回环流分流至流式 `CIO` 引擎（严禁走仅支持 `https://` 且全量内存缓冲的 `EchHttpClientEngine`）；`EchHttpClientEngine` 捕获 `CancellationException` 时仅限在真实请求超时（`TimeoutCancellationException`）下调用 `recordFailure`，严禁因常规协程取消误杀 `EchEdgePool` 黄金边缘节点。
- 内置 WebView 与本地代理闭环：自定义 `WebChromeClient` 严禁忽略控制台日志，必须实现 `onConsoleMessage` 透传至 Logcat（ArchitectureRulesTest 物理红线拦截），消灭前端排查盲区；本地反向代理改写 HTML 资源时必须同步改写 `Content-Security-Policy` 响应头中的对应源，杜绝只改响应体导致浏览器安全策略阻断。

## 规范先例与协同流

新模式的唯一入口，不适配就停下提决策，勿发明新模式：只读特性 → `:feature:schedule`；带参详情 → `:feature:subject`；写操作 → `CollectionRepository`；认证 → `AuthRepository` + `BgmPkce`；新红线 → 先写 ArchitectureRulesTest。详细实现剧本参见 `.agents/skills/minibgm-playbooks/SKILL.md`。

AI 协同流水线（机械执行，严禁甩锅排错）：

1. **前置反诘（Spec 对齐与发射架）**：编码前反查硬红线与先例，输出 Mini-Spec 与改动栈；多 Agent 接力或跨切面任务统一暂存至根目录 `.task.md`（发射架机制：受 .gitignore 保护不入库，完工且门禁通过后即删），用户确认后开工。
2. **原子切片（Task Stacks）**：自底向上分层实现（model/data → ViewModel → UI），单次提交单一目的；中间态用 `.\tools\quick-check.ps1` 秒级自查红线。
3. **经验固化（Flywheel 飞轮，跑 `jgate` 前前置必检）**：凡完成缺陷修复、审查整改或架构重构，跑 `jgate` 终验前必须主动执行《MiniBgm 飞轮协议》（Encode lessons into structure，直接固化入 `ArchitectureRulesTest`、`AGENTS.md` 与 `.agents/skills/minibgm-playbooks`，不留纯文本日记，详见 `.agents/skills/minibgm-flywheel/SKILL.md`），严禁等用户提醒才补跑飞轮。
4. **自愈验证（Autonomous Loop）**：连同飞轮新增的红线单测一起自主运行 `.\tools\jgate.ps1`（或 `bash tools/jgate`），若未通过必须自行抓取日志修复直至全绿，严禁让用户试跑。

## 发布

tag 是版本唯一真源：推 `vX.Y.Z` 经 `-Pminibgm.versionName/-Pminibgm.versionCode` 注入构建；发布门禁在 release.yml（缺签名密钥直接失败）。勿为发版手改 build-logic 的默认版本——那是 v0.2.8 事故的根源。
