# MiniBgm AI Agent & Developer Guidelines

MiniBgm：Bangumi（bgm.tv）追番排期与收藏管理客户端。模块化 Clean Architecture，仿 Google *Now in Android*。本文只写"删了会改变行为"的内容；细节一律以代码、测试与钩子为准。

## 版本控制

仓库与 jj colocate，历史操作用 `jj commit` / `jj git push`；提交摘要遵循 Conventional Commits（如 `feat(core:ai): xxx` / `chore: xxx`）。git 只读（log/diff/show/ls-remote/tag/clone）——git 写子命令被 VCS 层的 `tools/githooks` 硬拒，新 clone 跑一次 `tools/githooks/setup.sh` 接线，`tools/jgate` 会自愈补配；jj 不运行 git 钩子，与合法路径零冲突。

- 一个提交一个目的；同一文件别混两个目的——事后 `jj split` 拆不出可编译的中间态。
- 同一时刻只允许一条 Gradle 命令在跑（长构建后台 + 足够超时，避免 daemon 互踩）。
- OAuth 令牌交换/刷新代理在独立的私有 Cloudflare Workers 项目，本仓库搜不到；业务 API 直连 api.bgm.tv。

## 构建与验证

提交前唯一入口：`.\tools\jgate.ps1`（Linux/CI `bash tools/jgate`）；提交用 `tools/jcommit.ps1 -m "feat: ..."`（强制先验后提）。门禁做什么、目标如何推断、md 改动的白名单跳过、`--fast` 与 CI 的差异、诊断产物在哪——全部由 `.\tools\jgate.ps1 --help` 自述，本文件不复述——`--help` 就是门禁的用法规格，改 jgate 行为必须同 commit 改 help。

- `--fast` 只是本机减负：本机绿 != CI 绿，提交前必须复跑一次不带 `--fast` 的。
- 素材体积门：单张 > 200 KB 或合计 > 1 MB 硬拦（压 800px WebP）；单元测试配置唯一真源 `configureAndroidUnitTests()`，禁止各自手抄 `tasks.withType<Test>`；依赖图变更后 `./gradlew graphUpdate`，Lint 存量基线用 `updateLintBaseline`，质量门 `./gradlew :core:testing:crapCheck`（CRAP ≤ 30）。
- Rust 原生库：ABI 集合唯一真源 `native/abis.txt`（构建脚本、release workflow、`SUPPORTED_ABIS` 全与它对齐）；Gradle 绝不跨界调度 Cargo/NDK，只经 `registerPrebuiltJniLibs` 挂载产物并在缺库时硬失败；`libminibgm_torrent.so` 在 release 由 `configureOnDemandNativePackaging` 排除、按需下载。

编码中间态用 `.\tools\quick-check.ps1` 秒级自查（等价 `jgate quick`）。

## 硬红线

机械执行：源语义由 `:core:testing` 的 ArchitectureRulesTest，依赖图由 build-logic 的 ModuleBoundaryConventionPlugin（配置期断言）。**本文件自身也受机械预算约束**（总字数与单行长度，见 `agents_doc_must_stay_an_index_under_context_budget`）：新行为约束的落点永远是测试，这里只留一句索引，要加文字必须先删等量文字（确需扩写须显式上调常量并说明理由）。以下为索引，细节看测试：

1. feature 互不依赖；UI 只经 `:core:data` 仓库；`:core:model` 纯 Kotlin；feature 无原始 IO、无颜色字面量、不外露可变状态（MutableStateFlow 一律禁止；对外只暴露只读 StateFlow，或 Compose State 持有者的只读类型——多字段原子更新用 `Snapshot.withMutableSnapshot`，不用 `update { it.copy(…) }`）；Flow 算子（`map` / `onEach` / `filter` / `transform` 等）lambda 内严禁 `launch` 副作用协程——任务流在下载期间每秒都在发射，每次发射都新起协程且无人等待、无界堆积（`DownloadViewModel` 曾因此在下载页每秒并发触发一次全量磁盘扫描），需要持续刷新必须改用单条收集协程串行执行（StateFlow 自带 conflate，天然背压；ArchitectureRulesTest 物理红线拦截）；路由密封于 `BgmRoute` 并声明堆叠层。
2. 瞬态交互收敛：UI 确认/交互浮层统一由 `BgmOverlayHost` + `overlayHostState.await(...)` 挂起承接，严禁散落 `var showDialog`；通用登录提示收口 `BgmLoginPromptDialog`；一次性事件严禁裸 `collect`，必须 `ObserveAsEvents`（STARTED）；事件文案一律 `@StringRes` + 格式化参数下发、UI 侧解析（服务端消息经 message 透传），严禁 VM 内联文案；Composable 内禁止 `LocalContext.current.getString(...)`（不感知配置变更），静态文案用 `stringResource`，非 Composable 上下文经 `resources.getString`；播放器线路选择只走 `PlayerSourcesBottomSheet`（竖屏复用设计系统 `BgmSourcesBottomSheet`，横屏抽屉与选集抽屉同源主题 token——禁硬编码黑底，线路行/管理入口/AI 找源/空态一律 `BgmSourceActionCard` / `SourceEmptyStateCard`，见 `player_sources_panel_must_be_theme_driven` 与 `player_sources_panel_must_reuse_design_system`），选集抽屉专注选集；分集微操作与播放源收敛于 `SubjectSourcesBottomSheet`（支持原位切集，禁抽屉套抽屉）；多按钮横排必须显式 `contentPadding`（禁裸 Button 48dp 内边距截断中文）；底栏空态与配置跳转唯一真源 `BgmSourceActionCard`，跳外部界面必须经 `runAfterDismiss` / `onDismissAndRun`。
3. 可播地址只来自 `findPlayableSources`；一切写操作止于 PENDING_CONFIRMATION 提案卡，用户确认才落库。
4. 凭据只存在于 `AuthTokensDataSource`；不绕过/削弱 `BgmPkce`；不剥离或覆盖 `User-Agent`；关键写操作包 `withContext(NonCancellable)`。
5. DI 真实性与契约闭环：底层平台模块（如 platformDataModule）与 feature ViewModel 必须保持依赖定义闭环；严禁在单测中仅用手工假模块伪造 Koin 验证，必须通过真实容器集成测试（AppModuleTest）断言全量解析，严禁构造器新增依赖后遗漏注册引发运行时 NoDefinitionFoundException。手工 `viewModel { XxxViewModel(...) }` 块必须把目标类**全部带 `= null` 默认值的可选依赖**显式传上——这类漏传 Koin 被默认值兜住、单测又自己传 fake，只在真机上静默退化成「功能没接上」（红线 `manual_view_model_blocks_must_inject_every_optional_dependency` 把关）。

## 领域规则

每条规则的完整复盘都在对应红线测试的 KDoc 里（测试名即规则名，见 `ArchitectureRulesTest`）；这里只留可执行的约束。动手前先读 `.agents/skills/minibgm-playbooks/` 的路由表，只打开命中任务的 1-2 个 `references/` 剧本，禁止通读。

- 装饰性功能 fail-open：光晕/动效/横幅等内部异常一律 `runCatching` 降级为「没有装饰」，绝不崩宿主页面；读硬件位图像素前先 `copy` 成软件位图。
- 排期真值只来自 AniList 已验证播出事件；禁止算术预测与合成剧集；bangumi-data 只做元数据与平台映射（`schedule_repository_uses_anilist_as_the_only_roster_source`）。
- 复用 `BgmHttpClient.jsonConfig`，不手写 `Json {}`（`json_instances_have_a_single_home_per_scope`）。
- Ktor 401 自动刷新、刷新彻底失败自动登出——调用方永远不会看到 401。
- 播放器会话：续播点按**分集**绑定（`selectEpisode` / `switchTo` 按 `episodeId` 存取，与直链/源解耦；`player_view_model_playback_resume_must_bind_strictly_to_playback_history_repo`），同分集换线路沿用当前进度；
  - 「选中」≠「正在播」：徽标、健康度归因与上次可用源读 `playingSourceId`（`onPlaybackReady` / `onPlaybackError` 归属在播源）；切源失败回退选中项 + 轻提示，禁全屏错误盖住仍在播画面；选中项按**源标识**找回（下标仅位置语义）。
  - 引擎重置只重置媒体字段（`isLooping` 由 `repeatMode` 持有），轨道 override 只清「跟着上一份媒体选的」（`player_media_state_reset_must_preserve_engine_side_preferences`）。
  - 长期偏好（弹幕/连播/画中画/关闭字幕）经 `SettingsRepository` 落库由 `PlayViewPreferences` 投影回读，禁 VM 内存翻转、禁 UI `remember`；「关闭字幕」只记立场且走单向 `applySubtitlesDisabledPreference`；倍速/画幅/单集循环刻意不落库（`player_view_preferences_must_be_persisted_not_held_in_session_state`）。
  - 弹幕关闭即零请求（自动拉取按 `episodeId:episodeSort` 去重），分集纠正随开关两处收起，重开后空提示只能来自真实空结果（`danmaku_match_entry_must_be_gated_by_the_danmaku_toggle`）。
  - 三处评论区共用排序偏好（`SettingsRepository.setCommentSortOrder` + `CommentSortOrder.fromKey`；`comment_sort_order_must_be_a_shared_persisted_preference`）。
  - `DefaultMediaSourceFactory` 包 `DefaultDataSource.Factory` 支持 `file://`（`exoplayer_engine_media_source_factory_must_wrap_default_data_source`）；线路生成剔除 `TORRENT`（`player_view_model_must_filter_out_torrent_rules_from_online_sources`）；结构化 API 禁当直链（`isLikelyMediaStream` 断言接口型 URL）；播放错误浮层同时给 `onNextSource` 与 `onOpenSourcesPanel`。
- 手势与进度条：屏幕横向手势必须把实时进度透传 `onScrubbing` 并联动底边进度线；进度条统一 `awaitEachGesture` 单手势循环，Thumb 行程按两端半径对齐，触摸靶区 ≥44dp；双击累加 + 650ms 防抖收敛为单次 Seek，侧边药丸承接、严禁中央 Seek HUD；动态数值用 `tnum` 且容器定宽（`player_gesture_hud_must_not_jitter_and_use_tnum_and_avoid_dual_hud_conflict`）。
- 图床与富文本：未知图床 Fail-Open 直连（后台异步探 ECH），BBCode 图片块必须有加载占位；带链接图片与畸形嵌套必须归一化并提取纯净直链（`image_loader_routing_must_fail_open_for_unknown_third_party_hosts`）。
- 原生插件：严禁派发未就绪 Socket；扩展下载与后台大任务必须经 `BgmOverlayHost` 确认；动态 `.so` 按版本隔离 + `Mutex` + `setReadOnly`（`dynamic_so_loading_enforces_wx_version_isolation_and_no_silent_install`、`stream_engines_never_use_fake_placeholders_or_unbound_sockets`）。
- JNI：`companion object` 内 `external fun` 必须逐个 `@JvmStatic`（否则符号落进 `$Companion`，真机闪退）；边界禁 `unwrap()`/裸锁；无活跃会话的统计必须返回长度不足 4 的数组（`jni_symbol_binding_and_stats_failure_signal_keep_kotlin_and_rust_in_lockstep`）。
- librqbit 会话：后缀过滤 + `only_files` 单集锁 + `overwrite: true` + 本地 API `read_only`；`paused: true` 加种、锁单集后再 `unpause`；目标单集由纯函数挑选，多视频未命中 `episodeHint` 必须显式失败；扩展名白名单单一真源 `VIDEO_EXTENSIONS`（`torrent_file_selection_locks_single_episode_before_starting_download`）。
- 落盘与调度：单盘直写（`saveDirPath` + 串行队列），严禁二次 HTTP 回环双倍写盘；抢占/暂停/取消先停旧会话再 `cancelAndJoin`，被取消/异常的协程禁用 `ERROR` 冲刷 `QUEUED`/`PAUSED`；恢复任务必须合并内存任务；下载进度禁止高频全量写库。
- 起播与看门狗：播放优先——`scheduleNextIfIdle` 在 `playerSessionActive` 期间礼让，只有显式点播可接管；探针不被零速度 Peer 刷新；落盘后断言文件存在且非空；引擎 Error 必须透出（第 5 位状态码 + `nativeGetLastError`），错误态 `localPlayableUri()` 不交地址（`offline_download_must_yield_to_active_playback_session`）。
- 检索与载荷：多源合并去重走 `dedupeKey` 逐级回退；分集未命中只回退合集整包；种子载荷三入口都过 `TorrentFileCache.isValidPayload`；缓存键 infoHash 优先；起播超时转 `Result.failure`（`torrent_episode_isolation_and_payload_validation_guards_are_enforced`）。
- 资源动作诚实：种子卡片只留播放 + 离线下载 + `⋮`，禁技术徽标；已落盘时禁用下载（且排除 `isFileMissing`）；停止起播走 `pauseDownload(taskId)`；既有任务比对必须同时含 `episodeKey` 与源同一性（`downloaded_resource_actions_must_stay_honest`、`torrent_resource_card_must_not_expose_technical_dual_source_badges_and_use_lean_actions`）。
- 网络与协议中立：本地回环流走 CIO（禁 ECH 引擎全量缓冲）；`EchHttpClientEngine` 只在真实请求超时时记失败；核心代码零种子站点域名、零公共 Tracker 注入；Feed 提取的链接先 `unescapeXml`（`core_data_and_feature_subject_sources_must_not_hardcode_proprietary_torrent_domains`、`production_sources_must_not_inject_hardcoded_public_trackers`）。
- WebView 与本地代理：`onConsoleMessage` 必须透传 Logcat；改写 HTML 资源时必须同步改写 CSP 响应头。
- 资产下载：面向请求作用域冷流，禁止单例全局状态；同一 `destinationPath` 落盘受路径级 `Mutex` 保护。
- 应用内更新：必须消费真实进度与取消入口，未知总量不得显示成 0%，进度与终态提示合并为单一 effect（`update_flow_must_surface_real_progress_and_cancel`）。
- 开发面板：每个开关必须有真实生产消费方（`developer_panel_settings_must_have_a_real_consumer`）。
- 数据库：`BgmDatabase.version` 固定 1、不写 Migration、`exportSchema = false`、`fallbackToDestructiveMigration` 重建；任何实体/DAO 变更必须同步加版本号。
- 订阅与规则导入：探测结果只用于诊断排序，订阅落库严禁按 `isAlive` 过滤；导入需支持订阅包与 TVBox 配置；规则数异常时自愈补齐（`subscription_sources_must_never_be_discarded_by_probe_status`）。

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
