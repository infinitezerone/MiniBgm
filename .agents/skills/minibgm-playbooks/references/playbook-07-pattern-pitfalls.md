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
   * 任何换集入口都必须显式按新分集查表下发 `resumePositionMs`。直链换集时新地址当下可知，当场查表；规则源换集时旧画面被故意保留、新地址要等嗅探结果，只能把查表推迟到解析成功那一刻一次性消费（`consumePendingResumePositionForNewEpisode`），而同分集内换线路必须沿用当前进度（不可消费）。
   * 严禁将未看过的分集回退至旧分集的 `latestPositionMs`（新分集无历史即下发 0L）；播放从头开始后，中途周期性自动落盘产生的位置表快照（`applyRepoSnapshot`）严禁反向冲刷回 `resumePositionMs`，避免未看过的视频播到 10 秒时突发「从上次开始播放」浮层与跳帧。
   * 新增换集入口时，同步补一条"换集后 `resumePositionMs` 等于新分集自己的记录"与"未看过视频续播点保持 0L"的回归单测。
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
