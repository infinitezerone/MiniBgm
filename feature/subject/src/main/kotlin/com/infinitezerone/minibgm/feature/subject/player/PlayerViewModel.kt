package com.infinitezerone.minibgm.feature.subject.player

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.ChineseConverter
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.common.onSuccess
import com.infinitezerone.minibgm.core.data.playback.PlaybackFailureStore
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import com.infinitezerone.minibgm.core.data.repository.EpisodeStreamResolver
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.data.repository.PlaybackSourceVerifier
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.model.toEpisodeLabel
import com.infinitezerone.minibgm.core.navigation.PlayerQueueEntry
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.feature.subject.EpisodeCommentsDelegate
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.components.CommentSortOrder
import com.infinitezerone.minibgm.feature.subject.components.isEpisodeWatched
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 断点续播落盘节流：进度回调约 500ms 一跳，每 20 跳（约 10 秒）落盘一次。
 * 用计数而不是定时器——避免在 ViewModel 里挂常驻协程。
 */
internal const val POSITION_SAVE_TICKS = 20

/** 位置太短没有恢复价值 */
internal const val MIN_RESUME_POSITION_MS = 5_000L

/**
 * 单次取源的关键词尝试总时限。
 *
 * 关键词变体是**串行**试探的（命中即停），站点挂掉或全部不命中时最坏要把每个变体
 * 都等到网络超时——没有总时限就是几十秒的静默。超时后按"未找到 + 已尝试次数"如实上报，
 * 而不是让用户对着转圈等。
 */
internal const val RESOLVE_TOTAL_TIMEOUT_MS = 20_000L

/**
 * 播放源标签
 */
data class PlayerSourceTab(
    val id: String,
    val name: String,
    val rule: PlaybackSourceRule? = null,
    val isDirect: Boolean = false,
    @StringRes val nameRes: Int? = null,
)

/**
 * 分集网格/列表条目
 */
data class PlayerEpisodeItem(
    val id: Long = 0L,
    val sort: Float = 1f,
    val type: Int = 0,
    val name: String = "",
    val nameCn: String = "",
    val duration: String = "",
    val airdate: String = "",
    val commentCount: Int = 0,
    val desc: String = "",
    val isWatched: Boolean = false,
) {
    val displayName: String
        get() =
            nameCn.ifBlank {
                name.ifBlank {
                    "第 ${sort.toEpisodeLabel()} 话"
                }
            }
}

/**
 * 播放器界面 UI 状态
 */
data class PlayerUiState(
    val subjectId: Long = 0L,
    val episodeId: Long = 0L,
    val streamUrl: String = "",
    val episodeName: String = "",
    val episodeSort: Float = 1f,
    val episodeType: Int = 0,
    val subjectName: String = "",
    val subjectCoverUrl: String = "",
    val subjectScore: Double = 0.0,
    val subjectDate: String = "",
    val subjectSummary: String = "",
    val subjectCollectionType: Int? = null,
    val currentEpisodeDesc: String = "",
    val currentEpisodeAirdate: String = "",
    val currentEpisodeCommentCount: Int = 0,
    val comments: List<EpisodeComment> = emptyList(),
    val commentSortOrder: CommentSortOrder = CommentSortOrder.HOT,
    val isCommentsLoading: Boolean = false,
    val commentsError: String? = null,
    val currentUserId: Long? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val isWatched: Boolean = false,
    val autoMarked: Boolean = false,
    val error: String? = null,
    /** 本次播放的分集队列；单集播放时长度为 1，选集抽屉与连播按钮仅在 size > 1 时展示 */
    val queue: List<PlayerQueueEntry> = emptyList(),
    val currentIndex: Int = 0,
    /** 播完自动连播下一集（可在选集抽屉内关闭） */
    val autoNextEnabled: Boolean = true,
    /** 当前分集的断点续播位置（毫秒，0 表示没有可恢复的记录；与打卡是两套独立进度） */
    val resumePositionMs: Long = 0L,
    /** 播放源标签列表（包含自备片单与外部规则源） */
    val sources: List<PlayerSourceTab> = emptyList(),
    /** 源标识 → 本次会话内的连续失败次数；用于在选源界面弱化屡试屡败的源 */
    val sourceFailureCounts: Map<String, Int> = emptyMap(),
    val selectedSourceIndex: Int = 0,
    /** 全部分集列表（由条目详情持续同步） */
    val episodes: List<PlayerEpisodeItem> = emptyList(),
    /** 异步嗅探直链中 */
    val isResolvingSource: Boolean = false,
    /** 取源时已发起的第几次关键词尝试（1 起）；0 表示未在解析 */
    val resolveAttempt: Int = 0,
    /** 本次解析的关键词尝试总次数；0 表示未在解析 */
    val resolveAttemptTotal: Int = 0,
    /** 画中画（PiP）开关 */
    val pipEnabled: Boolean = true,
) {
    val hasNext: Boolean
        get() {
            if (episodes.size > 1) {
                val idx = episodes.indexOfFirst { it.sort == episodeSort || (it.id > 0 && it.id == episodeId) }
                return idx in 0 until episodes.lastIndex
            }
            return currentIndex < queue.lastIndex
        }

    val hasPrevious: Boolean
        get() {
            if (episodes.size > 1) {
                val idx = episodes.indexOfFirst { it.sort == episodeSort || (it.id > 0 && it.id == episodeId) }
                return idx > 0
            }
            return currentIndex > 0
        }

    val currentSource: PlayerSourceTab?
        get() = sources.getOrNull(selectedSourceIndex)
}

/**
 * 播放器一次性单发事件
 */
sealed interface PlayerUiEvent {
    data class ShowSnackbar(
        val message: String,
    ) : PlayerUiEvent

    data class MarkedWatched(
        val epNumber: Int,
    ) : PlayerUiEvent
}

/**
 * 数据侧仓库流投影快照（私有于 [PlayerViewModel]）：
 * 续播位置表 / 播放规则 / 源健康度 / 条目元数据 / 全部分集 / PiP 偏好六个仓库流
 * 经单点 combine 合流为一份不可变快照，再统一并入 [_uiState]（写法对齐
 * [EpisodeDetailViewModel] 的仓库快照合流形态）。UI 可见的字段一律是投影，
 * 不存在逐路 `collect { _uiState.update {} }` 的第二事实源。
 */
private data class PlayerRepoSnapshot(
    val positions: Map<String, Long> = emptyMap(),
    val rules: List<PlaybackSourceRule> = emptyList(),
    val failureCounts: Map<String, Int> = emptyMap(),
    val subject: Subject? = null,
    val rawEpisodes: List<Episode> = emptyList(),
    val pipEnabled: Boolean = true,
    val collection: UserCollection? = null,
)

/**
 * 媒体会话命令层的私有状态容器（私有于 [PlayerViewModel]）。
 *
 * 规范第 5 条：命令密集型会话可保留显式命令层——位置节流落盘（20 跳计数）、自动打卡
 * 一次性守卫、换集"先落盘旧位置再切"的副作用顺序都是过程式命令，无法也不应改写成纯投影。
 * 原实现散落在 ViewModel 顶层的 8 个裸可变字段在此收敛为单个容器，集中管理、逐字段注释；
 * 对外仍只经 [PlayerViewModel.uiState] 暴露，容器内不持有任何 UI 可见状态的第二份拷贝。
 *
 * [positionsMap] / [subjectAliases] / [subjectOriginalName] 是仓库流的**命令侧只读镜像**：
 * 由投影收集器同步，仅供命令读取（换集按新地址查恢复点、取源拼关键词候选），
 * UI 展示值一律走 [_uiState] 投影，不在此处双写。
 */
private class PlaybackSession(
    startIndex: Int,
) {
    /** 分集队列中的当前集下标 */
    var currentIndex: Int = startIndex

    /** 自动打卡一次性守卫：一次播放会话仅触发一次，换集后复位 */
    var hasTriggeredAutoMark: Boolean = false

    /** 断点续播位置表镜像（由 [PlayerRepoSnapshot.positions] 持续同步） */
    var positionsMap: Map<String, Long> = emptyMap()

    /** 界面回报的当前播放位置（内存暂存，按 [POSITION_SAVE_TICKS] 节流落盘） */
    var latestPositionMs: Long = 0L

    /** 距上次落盘累计的进度跳数 */
    var progressTicksSinceSave: Int = 0

    /** 条目片名别名（台译/港译/英文名/罗马音），来自已缓存 Subject 的 infobox */
    var subjectAliases: List<String> = emptyList()

    /** 条目日文原名 */
    var subjectOriginalName: String = ""

    /** 已落盘的“上次可用源”标识，避免每次 READY 都重复写 DataStore */
    var lastSavedSourceId: String = ""
}

/**
 * 播放器 ViewModel —— 响应式 UDF，规范五条见 feature/search 的 [SeasonalGuideViewModel] 顶部，
 * 就近形态参照同模块刚完成同型迁移的 [EpisodeDetailViewModel]。本类是 Tier 3 保守迁移：
 *
 * 1. 对外只读 [uiState]；数据侧收敛为单点 [repoSnapshots] 投影收集器——sources 由播放规则派生、
 *    episodes / subjectName / resumePositionMs / pipEnabled / sourceFailureCounts 一律为仓库流投影，
 *    统一并入 [_uiState]，不再有散落的逐路订阅。
 * 2. 可变状态只剩媒体会话命令层（[PlaybackSession]，8 个裸字段的收敛容器）与取源 [Job] 柄：
 *    媒体会话是命令密集型会话，命令逻辑（节流/守卫/副作用顺序）逐点保留。
 * 3. 写后读：条目标题/别名/分集由仓库流投影回读，fetch 仅兜底写仓。
 * 4. 一次性事件（Snackbar / 打卡回执）走 Channel(BUFFERED) + receiveAsFlow()，与状态流隔离。
 * 5. 命令层保留：[onProgressChanged] 20 跳节流落盘、[markWatched] 一次性守卫、
 *    [onPlaybackEnded] / [selectEpisode] / [switchTo] 先落盘旧位置再切的顺序、
 *    直链精确匹配不串集——语义与迁移前完全一致。
 *
 * 维护分集队列中的当前播放会话（换集/连播）、处理自动打卡标记（观看进度达到阈值或播放完成时触发）、
 * 断点续播位置的节流落盘与恢复点下发、鉴权校验（未登录拦截）、错误提示与失败归因回传、
 * 播放源切换（直链/外部源）与分集流嗅探联动。
 */
class PlayerViewModel(
    private val route: PlayerRoute,
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val failureStore: PlaybackFailureStore? = null,
    private val subjectRepository: SubjectRepository? = null,
    private val playbackResolverRepository: PlaybackResolverRepository? = null,
    private val playbackSourceVerifier: PlaybackSourceVerifier? = null,
    private val communityRepository: CommunityRepository? = null,
) : ViewModel() {
    /** 播放队列：调用方给了分集队列就用之，否则退化为单集播放（兼容空直链占位启动） */
    private val queue: List<PlayerQueueEntry> =
        route.queue.ifEmpty {
            listOf(
                PlayerQueueEntry(
                    streamUrl = route.streamUrl,
                    episodeName = route.episodeName,
                    episodeSort = route.episodeSort,
                    episodeType = route.episodeType,
                    episodeId = route.episodeId,
                    requestHeaders = route.requestHeaders,
                ),
            )
        }

    /** 与 AI 找源共用的「规则 × 标题候选 → 直链」编排；未注入解析仓库时为 null */
    private val episodeStreamResolver =
        playbackResolverRepository?.let { resolver ->
            EpisodeStreamResolver(resolver = resolver, verifier = playbackSourceVerifier)
        }

    /** 媒体会话命令层状态（8 个裸字段的收敛容器，见 [PlaybackSession]） */
    private val session = PlaybackSession(startIndex = route.startIndex.coerceIn(queue.indices))

    private fun initialEpisodes(): List<PlayerEpisodeItem> =
        if (route.queue.isNotEmpty()) {
            route.queue.map { entry ->
                PlayerEpisodeItem(
                    id = entry.episodeId,
                    sort = entry.episodeSort,
                    type = entry.episodeType,
                    name = entry.episodeName,
                )
            }
        } else {
            listOf(
                PlayerEpisodeItem(
                    id = route.episodeId,
                    sort = route.episodeSort,
                    type = route.episodeType,
                    name = route.episodeName,
                ),
            )
        }

    private fun buildSources(rules: List<PlaybackSourceRule>): List<PlayerSourceTab> {
        val list = mutableListOf<PlayerSourceTab>()
        val hasDirect = route.queue.isNotEmpty() || route.streamUrl.isNotBlank()
        if (hasDirect) {
            list.add(
                PlayerSourceTab(
                    id = "direct",
                    name = if (route.queue.size > 1) "自备片单" else "默认直链",
                    isDirect = true,
                    nameRes =
                        if (route.queue.size > 1) {
                            R.string.feature_subject_source_user_playlist
                        } else {
                            R.string.feature_subject_player_source_default_direct
                        },
                ),
            )
        }
        rules.filter { it.isEnabled }.forEach { rule ->
            list.add(
                PlayerSourceTab(
                    id = rule.id,
                    name = rule.name,
                    rule = rule,
                    isDirect = false,
                ),
            )
        }
        return list
    }

    private fun createInitialState(): PlayerUiState {
        val entry = queue[session.currentIndex]
        val initialSources = buildSources(emptyList())
        return PlayerUiState(
            subjectId = route.subjectId,
            episodeId = entry.episodeId,
            streamUrl = entry.streamUrl,
            episodeName = entry.episodeName,
            episodeSort = entry.episodeSort,
            episodeType = entry.episodeType,
            subjectName = route.subjectName,
            requestHeaders = entry.requestHeaders,
            queue = queue,
            currentIndex = session.currentIndex,
            autoNextEnabled = true,
            resumePositionMs = 0L,
            sources = initialSources,
            selectedSourceIndex = 0,
            episodes = initialEpisodes(),
            isResolvingSource = false,
        )
    }

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlayerUiEvent>(Channel.BUFFERED)
    val events: Flow<PlayerUiEvent> = _events.receiveAsFlow()

    private val isLoggedIn =
        authRepository.isLoggedIn
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var resolveJob: Job? = null

    val commentsDelegate =
        EpisodeCommentsDelegate(
            communityRepository = communityRepository,
            authRepository = authRepository,
            scope = viewModelScope,
            onMessage = { message -> _events.trySend(PlayerUiEvent.ShowSnackbar(message)) },
        )

    /** 条目流：仅在有 subjectId 且注入了条目仓库时订阅 */
    private val subjectFlow: Flow<Subject?> =
        if (route.subjectId > 0) {
            subjectRepository?.getSubjectStream(route.subjectId) ?: flowOf(null)
        } else {
            flowOf(null)
        }

    /** 收藏状态流：用于同步在看/看过等收藏进度与看过分集状态 */
    private val collectionFlow: Flow<UserCollection?> =
        if (route.subjectId > 0) {
            collectionRepository.getCollectionStream(route.subjectId)
        } else {
            flowOf(null)
        }

    /** 全部分集流：过滤正片/PV 以外类型、按类型与序号排序 */
    private val rawEpisodesFlow: Flow<List<Episode>> =
        if (route.subjectId > 0) {
            (subjectRepository?.getEpisodesStream(route.subjectId) ?: flowOf(emptyList()))
                .map { eps ->
                    eps
                        .filter { it.type == 0 || it.type == 1 }
                        .sortedWith(compareBy({ it.type }, { it.sort }))
                }
        } else {
            flowOf(emptyList())
        }

    /** 画中画开关偏好流：仅在有 subjectId 时订阅（与迁移前收集器的门控一致） */
    private val pipEnabledFlow: Flow<Boolean> =
        if (route.subjectId > 0) {
            settingsRepository.settings.map { it.pipEnabled }
        } else {
            flowOf(true)
        }

    /**
     * 数据侧单点投影：全部仓库流 combine 成一个 [PlayerRepoSnapshot] 快照流。
     *
     * 条目/分集/PiP/收藏 四路仅在携带 subjectId 时订阅（与迁移前逐路收集器的门控一致，
     * 其余场景给空默认值，快照仍可即时产出）。
     */
    private val repoSnapshots: Flow<PlayerRepoSnapshot> =
        combine(
            combine(
                settingsRepository.playbackPositions,
                settingsRepository.playbackRules,
            ) { positions, rules ->
                positions to rules
            },
            combine(
                failureStore?.sourceHealth ?: flowOf(emptyMap()),
                pipEnabledFlow,
            ) { health, pipEnabled ->
                health.mapValues { it.value.consecutiveFailures } to pipEnabled
            },
            combine(
                subjectFlow,
                rawEpisodesFlow,
                collectionFlow,
            ) { subject, episodes, collection ->
                Triple(subject, episodes, collection)
            },
        ) { (positions, rules), (failureCounts, pipEnabled), (subject, episodes, collection) ->
            PlayerRepoSnapshot(
                positions = positions,
                rules = rules,
                failureCounts = failureCounts,
                subject = subject,
                rawEpisodes = episodes,
                pipEnabled = pipEnabled,
                collection = collection,
            )
        }

    init {
        viewModelScope.launch {
            commentsDelegate.state.collect { cs ->
                _uiState.update { current ->
                    current.copy(
                        comments = cs.comments,
                        commentSortOrder = cs.sortOrder,
                        isCommentsLoading = cs.isLoading,
                        commentsError = cs.error,
                        currentEpisodeCommentCount = cs.commentCount,
                        currentUserId = cs.currentUserId,
                    )
                }
            }
        }

        // 数据侧单点投影：任一仓库流变化即产出新快照，统一并入 _uiState（规范第 1 条）。
        // 首次进页优先选中上次成功起播的源——在收集开始前读一次（与迁移前一致的 only-once 语义）
        viewModelScope.launch {
            val preferredSourceId =
                runCatching { settingsRepository.lastPlaybackSourceId.first() }.getOrDefault("")
            repoSnapshots.collect { snapshot ->
                val sourcesChanged = applyRepoSnapshot(snapshot, preferredSourceId)
                // 命令层副作用（规范第 5 条）：源列表就绪/变化后，无直链且选中规则源时自动嗅探。
                // 以"列表真实变化"为触发判据——无关偏好写入（如 PiP 开关）重放同一列表时不再
                // 重复取消/重启在途嗅探
                if (sourcesChanged) {
                    val currentState = _uiState.value
                    val selectedTab = currentState.sources.getOrNull(currentState.selectedSourceIndex)
                    if (currentState.streamUrl.isBlank() && selectedTab != null && !selectedTab.isDirect) {
                        resolveCurrentEpisodeStream()
                    }
                }
            }
        }

        // 兜底拉取：结果经仓库流投影回读（写后读，规范第 3 条），此处不手写状态
        if (route.subjectId > 0) {
            viewModelScope.launch {
                subjectRepository?.fetchEpisodes(route.subjectId)
                if (route.subjectName.isBlank()) {
                    subjectRepository?.fetchSubjectDetail(route.subjectId)
                }
            }
        }

        if (route.episodeId > 0) {
            loadComments()
        }
    }

    /**
     * 把一份仓库流快照并入 [_uiState]：各字段按原有逐路收集器的守卫条件逐点投影。
     * @return 播放源列表是否发生了真实变化（命令层据此决定是否自动嗅探）
     */
    private fun applyRepoSnapshot(
        snapshot: PlayerRepoSnapshot,
        preferredSourceId: String,
    ): Boolean {
        // 会话镜像同步（命令侧只读，见 [PlaybackSession]）
        session.positionsMap = snapshot.positions
        snapshot.subject?.let { subject ->
            session.subjectOriginalName = subject.name
            session.subjectAliases = subject.titleAliases
        }
        var sourcesChanged = false
        _uiState.update { state ->
            var next = state
            // 条目主标题缺失时由条目流补充（冷启动直达时保持空，退化成老行为）
            if (next.subjectName.isBlank()) {
                snapshot.subject?.let { subject ->
                    next = next.copy(subjectName = subject.displayName)
                }
            }
            // 播放源列表：由规则流派生；仅在列表真实变化时重算选中项
            val newSources = buildSources(snapshot.rules)
            if (newSources != next.sources) {
                val targetRuleIndex =
                    if (route.initialRuleId.isNotBlank()) {
                        newSources.indexOfFirst { it.rule?.id == route.initialRuleId }.takeIf { it >= 0 }
                    } else {
                        null
                    }
                // 仅在首次构建源列表且入口未携带直链/片单时应用上次可用源：
                // 直链是用户显式给进来的（自备片单/助手），直链始终优先，记忆源只对无直链入口兜底；
                // 同时避免之后覆盖用户的手动选择
                val preferredIndex =
                    if (route.initialRuleId.isBlank() &&
                        preferredSourceId.isNotBlank() &&
                        next.sources.isEmpty()
                    ) {
                        val memoryIsHealthy = (snapshot.failureCounts[preferredSourceId] ?: 0) == 0
                        if (memoryIsHealthy) {
                            newSources.indexOfFirst { it.id == preferredSourceId }.takeIf { it >= 0 }
                        } else {
                            newSources.indexOfFirst { (snapshot.failureCounts[it.id] ?: 0) == 0 }.takeIf { it >= 0 }
                                ?: newSources.indexOfFirst { it.id == preferredSourceId }.takeIf { it >= 0 }
                        }
                    } else if (route.initialRuleId.isBlank() && next.sources.isEmpty()) {
                        newSources.indexOfFirst { (snapshot.failureCounts[it.id] ?: 0) == 0 }.takeIf { it >= 0 }
                    } else {
                        null
                    }
                val newIndex =
                    targetRuleIndex
                        ?: preferredIndex
                        ?: next.selectedSourceIndex.coerceIn(0, (newSources.size - 1).coerceAtLeast(0))
                next =
                    next.copy(
                        sources = newSources,
                        selectedSourceIndex = newIndex,
                    )
                sourcesChanged = true
            }
            // 全部分集列表与当前分集元数据（由条目详情流与收藏流持续同步）
            val watchedCount = snapshot.collection?.epStatus ?: 0
            val mappedEpisodes =
                if (snapshot.rawEpisodes.isNotEmpty()) {
                    snapshot.rawEpisodes.map { ep ->
                        PlayerEpisodeItem(
                            id = ep.id,
                            sort = ep.episodeNumber,
                            type = ep.type,
                            name = ep.name,
                            nameCn = ep.nameCn,
                            duration = ep.duration,
                            airdate = ep.airdate,
                            commentCount = ep.comment,
                            desc = ep.desc,
                            isWatched = isEpisodeWatched(ep, watchedCount),
                        )
                    }
                } else {
                    next.episodes.map { ep ->
                        ep.copy(
                            isWatched = if (ep.type == 0 && ep.sort.toInt() > 0) watchedCount >= ep.sort.toInt() else false,
                        )
                    }
                }

            if (mappedEpisodes.isNotEmpty() && mappedEpisodes != next.episodes) {
                next = next.copy(episodes = mappedEpisodes)
            }

            val currentEp =
                mappedEpisodes.firstOrNull {
                    (next.episodeId > 0 && it.id == next.episodeId) ||
                        (it.sort == next.episodeSort && it.type == next.episodeType)
                }
            val currentWatched =
                if (snapshot.collection != null && currentEp != null) {
                    currentEp.isWatched
                } else {
                    next.isWatched
                }

            next =
                next.copy(
                    isWatched = currentWatched,
                    episodeId = if (next.episodeId <= 0L && currentEp != null && currentEp.id > 0L) currentEp.id else next.episodeId,
                    currentEpisodeDesc = currentEp?.desc ?: next.currentEpisodeDesc,
                    currentEpisodeAirdate = currentEp?.airdate ?: next.currentEpisodeAirdate,
                    currentEpisodeCommentCount = currentEp?.commentCount ?: next.currentEpisodeCommentCount,
                    subjectCoverUrl = snapshot.subject?.images?.large ?: snapshot.subject?.images?.common ?: next.subjectCoverUrl,
                    subjectScore = snapshot.subject?.rating?.score ?: next.subjectScore,
                    subjectDate = snapshot.subject?.date?.ifBlank { snapshot.subject.airDate } ?: next.subjectDate,
                    subjectSummary = snapshot.subject?.summary ?: next.subjectSummary,
                    subjectCollectionType = snapshot.collection?.type ?: next.subjectCollectionType,
                )
            // 源健康度：某个源连错几次后在选源界面上弱化它（只标记，不改排序——
            // selectedSourceIndex 有位置语义，重排会让选中项错位）
            if (snapshot.failureCounts != next.sourceFailureCounts) {
                next = next.copy(sourceFailureCounts = snapshot.failureCounts)
            }
            // 画中画开关
            if (snapshot.pipEnabled != next.pipEnabled) {
                next = next.copy(pipEnabled = snapshot.pipEnabled)
            }
            // 尚无可恢复点时跟随位置表刷新当前分集（已下发/消费过的不回退）
            if (next.resumePositionMs == 0L && next.streamUrl.isNotBlank()) {
                next = next.copy(resumePositionMs = snapshot.positions[next.streamUrl] ?: 0L)
            }
            next
        }
        if (_uiState.value.episodeId > 0L && _uiState.value.comments.isEmpty() && !_uiState.value.isCommentsLoading) {
            loadComments()
        }
        return sourcesChanged
    }

    /** 界面周期回报播放进度（毫秒）：内存暂存，每 [POSITION_SAVE_TICKS] 跳落盘一次 */
    fun onProgressChanged(positionMs: Long) {
        if (positionMs <= 0L) return
        session.latestPositionMs = positionMs
        if (++session.progressTicksSinceSave >= POSITION_SAVE_TICKS) {
            session.progressTicksSinceSave = 0
            flushPlaybackPosition()
        }
    }

    /**
     * 把内存中的当前分集位置落盘（立即捕获 url/位置再异步写，换集时先于状态切换调用）。
     * 由节流循环与界面 ON_PAUSE 触发。
     */
    fun flushPlaybackPosition() {
        val url = _uiState.value.streamUrl
        val position = session.latestPositionMs
        if (url.isBlank() || position <= 0L) return
        viewModelScope.launch { settingsRepository.savePlaybackPosition(url, position) }
    }

    /**
     * 当视频播放进度达到 85% 或播放结束时调用：
     * 自动向 Bangumi 提交当前分集的看过标记（一次播放会话仅触发一次）。
     */
    fun markWatched(epNumber: Int) {
        if (session.hasTriggeredAutoMark || _uiState.value.isWatched) return
        if (!isLoggedIn.value) {
            return
        }

        session.hasTriggeredAutoMark = true
        viewModelScope.launch {
            val result =
                collectionRepository.updateEpisodeStatus(
                    subjectId = _uiState.value.subjectId,
                    episodeId = _uiState.value.episodeId,
                    isWatched = true,
                    epNumber = epNumber,
                )
            result
                .onSuccess {
                    _uiState.update { it.copy(isWatched = true, autoMarked = true) }
                    _events.send(PlayerUiEvent.MarkedWatched(epNumber))
                    _events.send(PlayerUiEvent.ShowSnackbar("已自动标记为看过（第 $epNumber 话）"))
                }.onError { _, message ->
                    session.hasTriggeredAutoMark = false
                    _events.send(PlayerUiEvent.ShowSnackbar(message))
                }
        }
    }

    /** 观看进度达到 85% 阈值时由界面调用：对当前分集打卡 */
    fun onWatchThresholdReached() {
        markWatched(_uiState.value.episodeSort.toInt())
    }

    /**
     * 播放结束时由界面调用：对当前分集打卡、清除其续播点（看完的内容无需再恢复），
     * 随后在开启连播且有下一集时自动切换。
     * @return 是否已自动切到下一集（false 时界面展示重播按钮）
     */
    fun onPlaybackEnded(): Boolean {
        val state = _uiState.value
        markWatched(state.episodeSort.toInt())
        val finishedUrl = state.streamUrl
        session.latestPositionMs = 0L
        session.progressTicksSinceSave = 0
        viewModelScope.launch { settingsRepository.clearPlaybackPosition(finishedUrl) }
        if (!state.autoNextEnabled) return false

        // 优先根据全部分集列表查找下一集
        val epIndex =
            state.episodes.indexOfFirst {
                it.sort == state.episodeSort || (it.id > 0 && it.id == state.episodeId)
            }
        if (epIndex in 0 until state.episodes.lastIndex) {
            selectEpisode(state.episodes[epIndex + 1])
            return true
        }

        if (state.hasNext) {
            switchTo(state.currentIndex + 1)
            return true
        }
        return false
    }

    /** 全屏内选集抽屉换集：先落盘旧分集位置，再切换 */
    fun switchTo(index: Int) {
        if (index !in queue.indices || index == _uiState.value.currentIndex) return
        flushPlaybackPosition()
        session.latestPositionMs = 0L
        session.progressTicksSinceSave = 0
        session.hasTriggeredAutoMark = false
        session.currentIndex = index
        val entry = queue[index]
        val currentEp =
            _uiState.value.episodes.firstOrNull {
                (entry.episodeId > 0 && it.id == entry.episodeId) ||
                    (it.sort == entry.episodeSort && it.type == entry.episodeType)
            }
        _uiState.update {
            it.copy(
                episodeId = entry.episodeId,
                streamUrl = entry.streamUrl,
                episodeName = entry.episodeName,
                episodeSort = entry.episodeSort,
                episodeType = entry.episodeType,
                requestHeaders = entry.requestHeaders,
                currentIndex = index,
                isWatched = currentEp?.isWatched ?: false,
                currentEpisodeDesc = currentEp?.desc ?: "",
                currentEpisodeAirdate = currentEp?.airdate ?: "",
                currentEpisodeCommentCount = currentEp?.commentCount ?: 0,
                comments = emptyList(),
                commentsError = null,
                autoMarked = false,
                resumePositionMs = session.positionsMap[entry.streamUrl] ?: 0L,
            )
        }
        loadComments(force = true)
    }

    /** 切换播放源 */
    fun selectSource(index: Int) {
        val state = _uiState.value
        if (index !in state.sources.indices || index == state.selectedSourceIndex) return
        flushPlaybackPosition()
        _uiState.update { it.copy(selectedSourceIndex = index) }
        resolveCurrentEpisodeStream()
    }

    /** 切到下一个播放源（优先选择健康源）；解析失败时的"换一个源试试"动作 */
    fun selectNextSource() {
        val state = _uiState.value
        val total = state.sources.size
        if (total <= 1) return
        val nextIndices = (1 until total).map { (state.selectedSourceIndex + it) % total }
        val healthyNext =
            nextIndices.firstOrNull { index ->
                val sourceId = state.sources[index].id
                (state.sourceFailureCounts[sourceId] ?: 0) == 0
            }
        selectSource(healthyNext ?: ((state.selectedSourceIndex + 1) % total))
    }

    /** 选中具体分集并播放 */
    fun selectEpisode(episode: PlayerEpisodeItem) {
        val state = _uiState.value
        if (episode.sort == state.episodeSort && episode.id == state.episodeId && state.streamUrl.isNotBlank()) return
        flushPlaybackPosition()
        session.latestPositionMs = 0L
        session.progressTicksSinceSave = 0
        session.hasTriggeredAutoMark = false

        val queueIndex =
            queue.indexOfFirst {
                (it.episodeId > 0 && it.episodeId == episode.id) ||
                    (it.episodeSort > 0 && it.episodeSort == episode.sort)
            }
        if (queueIndex >= 0) {
            session.currentIndex = queueIndex
        }

        val isDirect = state.currentSource?.isDirect == true
        val directEntry = if (isDirect && queueIndex >= 0) queue[queueIndex] else null
        val directMissing = isDirect && directEntry == null
        val currentEp =
            state.episodes.firstOrNull {
                (episode.id > 0 && it.id == episode.id) ||
                    (it.sort == episode.sort && it.type == episode.type)
            }

        _uiState.update {
            it.copy(
                episodeId = episode.id,
                episodeName = episode.displayName,
                episodeSort = episode.sort,
                episodeType = episode.type,
                currentIndex = if (queueIndex >= 0) queueIndex else it.currentIndex,
                isWatched = currentEp?.isWatched ?: false,
                currentEpisodeDesc = currentEp?.desc ?: episode.desc,
                currentEpisodeAirdate = currentEp?.airdate ?: episode.airdate,
                currentEpisodeCommentCount = currentEp?.commentCount ?: episode.commentCount,
                comments = emptyList(),
                commentsError = null,
                autoMarked = false,
                // 非直链源保留当前画面（不清空），等嗅探完成再替换，避免换集瞬间黑屏；
                // 直链源精确取队列中的对应集，未命中就如实报缺，绝不串到别的集
                streamUrl =
                    when {
                        directEntry != null -> directEntry.streamUrl
                        directMissing -> ""
                        else -> it.streamUrl
                    },
                requestHeaders =
                    when {
                        directEntry != null -> directEntry.requestHeaders
                        directMissing -> emptyMap()
                        else -> it.requestHeaders
                    },
                error = if (directMissing) "自备片单中没有该分集，请切换到其他播放源" else null,
            )
        }
        resolveCurrentEpisodeStream()
        commentsDelegate.loadComments(episode.id, force = true)
    }

    /**
     * 手动切换当前分集的看过/未看标记（支持用户点击打卡与撤销打卡）。
     */
    fun manualToggleWatched() {
        if (!isLoggedIn.value) {
            viewModelScope.launch {
                _events.send(PlayerUiEvent.ShowSnackbar("请先登录 Bangumi"))
            }
            return
        }
        val targetWatched = !_uiState.value.isWatched
        val epNumber = _uiState.value.episodeSort.toInt()
        viewModelScope.launch {
            val result =
                collectionRepository.updateEpisodeStatus(
                    subjectId = _uiState.value.subjectId,
                    episodeId = _uiState.value.episodeId,
                    isWatched = targetWatched,
                    epNumber = epNumber,
                )
            result
                .onSuccess {
                    _uiState.update { it.copy(isWatched = targetWatched) }
                    _events.send(
                        PlayerUiEvent.ShowSnackbar(
                            if (targetWatched) "已标记为看过（第 $epNumber 话）" else "已撤回看过标记（第 $epNumber 话）",
                        ),
                    )
                }.onError { _, message ->
                    _events.send(PlayerUiEvent.ShowSnackbar(message))
                }
        }
    }

    /**
     * 异步拉取当前分集的吐槽短评列表。
     */
    fun loadComments(force: Boolean = false) {
        val epId = _uiState.value.episodeId
        if (epId <= 0L) return
        commentsDelegate.loadComments(epId, force)
    }

    /** 切换单集吐槽排序规则（热门 / 楼层 / 最新） */
    fun setCommentSortOrder(order: CommentSortOrder) {
        commentsDelegate.setSortOrder(order)
    }

    /** 切换对吐槽短评的表情表态 */
    fun toggleCommentReaction(
        comment: EpisodeComment,
        reaction: CommentReaction,
    ) {
        commentsDelegate.toggleCommentReaction(comment, reaction)
    }

    /** 切换对吐槽短评的表情表态（按表情数值） */
    fun toggleCommentReaction(
        comment: EpisodeComment,
        reactionValue: Int,
    ) {
        commentsDelegate.toggleCommentReaction(comment, reactionValue)
    }

    /**
     * 快捷切换当前条目的追番状态（B站风格追番按钮）：
     * 未追番 -> 在看（DOING）；已在看 -> 撤回/想看或提示已在看。
     */
    fun toggleFollowSubject() {
        if (!isLoggedIn.value) {
            viewModelScope.launch {
                _events.send(PlayerUiEvent.ShowSnackbar("请先登录 Bangumi"))
            }
            return
        }
        val currentType = _uiState.value.subjectCollectionType
        val targetType =
            if (currentType == CollectionType.DOING.value) {
                CollectionType.WISH
            } else {
                CollectionType.DOING
            }
        viewModelScope.launch {
            val result =
                collectionRepository.updateCollectionStatus(
                    subjectId = _uiState.value.subjectId,
                    type = targetType,
                )
            result
                .onSuccess {
                    _uiState.update { it.copy(subjectCollectionType = targetType.value) }
                    _events.send(
                        PlayerUiEvent.ShowSnackbar(
                            if (targetType == CollectionType.DOING) "已加入追番（在看）" else "已更新追番状态",
                        ),
                    )
                }.onError { _, message ->
                    _events.send(PlayerUiEvent.ShowSnackbar(message))
                }
        }
    }

    /**
     * 针对当前选中的播放源与分集，解析可播放的媒体直链
     */
    fun resolveCurrentEpisodeStream() {
        val state = _uiState.value
        val currentTab = state.sources.getOrNull(state.selectedSourceIndex) ?: return
        if (currentTab.isDirect) {
            // 直链/自备片单：只按分集**精确匹配**。找不到就如实报无片源，
            // 绝不再回退 queue[currentIndex]——那会把 A 集串成 B 集的画面。
            val matchingEntry =
                state.queue.find {
                    (it.episodeId > 0 && it.episodeId == state.episodeId) ||
                        (it.episodeSort > 0 && it.episodeSort == state.episodeSort)
                }
            if (matchingEntry != null && matchingEntry.streamUrl.isNotBlank()) {
                _uiState.update {
                    it.copy(
                        streamUrl = matchingEntry.streamUrl,
                        requestHeaders = matchingEntry.requestHeaders,
                        isResolvingSource = false,
                        error = null,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        streamUrl = "",
                        requestHeaders = emptyMap(),
                        isResolvingSource = false,
                        error = "自备片单中没有该分集，请切换到其他播放源",
                    )
                }
            }
            return
        }

        val rule = currentTab.rule ?: return
        val streamResolver = episodeStreamResolver
        if (streamResolver == null) {
            _uiState.update {
                it.copy(
                    isResolvingSource = false,
                    error = "流解析器未配置",
                )
            }
            return
        }

        resolveJob?.cancel()
        resolveJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isResolvingSource = true,
                        resolveAttempt = 0,
                        resolveAttemptTotal = 0,
                        error = null,
                    )
                }
                try {
                    val epSort = _uiState.value.episodeSort
                    val primaryTitle = _uiState.value.subjectName.ifBlank { route.subjectName }
                    // 片名候选与助手侧同源（searchTitles：主标题 → 别名 → 日文原名），总数由 MAX_SEARCH_TITLES 封顶
                    val baseTitles =
                        ChineseConverter.searchTitles(
                            primary = primaryTitle,
                            aliases = session.subjectAliases,
                            origin = session.subjectOriginalName,
                        )

                    // 候选顺序/集号策略/超时/命中判定统一收敛在 EpisodeStreamResolver（与 AI 找源共用）
                    var lastAttempted = 0
                    var lastTotal = 0
                    val outcome =
                        streamResolver.probeRule(
                            rule = rule,
                            baseTitles = baseTitles,
                            epSort = epSort,
                            subjectId = _uiState.value.subjectId,
                            episodeId = _uiState.value.episodeId,
                            timeoutMs = RESOLVE_TOTAL_TIMEOUT_MS,
                        ) { attempted, attemptTotal ->
                            lastAttempted = attempted
                            lastTotal = attemptTotal
                            // 串行试探期间把进度透出去，避免用户对着转圈不知道在等什么
                            _uiState.update {
                                it.copy(resolveAttempt = attempted, resolveAttemptTotal = attemptTotal)
                            }
                        }
                    val playable = outcome?.directSource

                    if (playable != null) {
                        _uiState.update {
                            it.copy(
                                streamUrl = playable.url,
                                requestHeaders = playable.headers,
                                isResolvingSource = false,
                                resolveAttempt = 0,
                                resolveAttemptTotal = 0,
                                error = null,
                            )
                        }
                    } else {
                        // 区分"试完不命中"与"没试完就超时"：前者多半是站点没这部片，
                        // 后者多半是站点慢/挂了——两种给用户的动作不同，不能笼统说一句"没嗅探到"。
                        val attempted = outcome?.attempted ?: lastAttempted
                        val attemptTotal = outcome?.attemptTotal ?: lastTotal
                        val targetLabel = primaryTitle.ifBlank { "当前条目" }
                        val reason =
                            if (outcome == null) {
                                "在【${rule.name}】中尝试 $attempted/$attemptTotal 个关键词后超时" +
                                    "（${RESOLVE_TOTAL_TIMEOUT_MS / 1000} 秒），站点可能响应过慢或不可用"
                            } else {
                                "未在【${rule.name}】中解析到「$targetLabel」的可播放直链（已尝试 $attempted 个关键词）"
                            }
                        _uiState.update {
                            it.copy(
                                isResolvingSource = false,
                                resolveAttempt = 0,
                                resolveAttemptTotal = 0,
                                error = "$reason，可尝试切换其他播放源",
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isResolvingSource = false,
                            resolveAttempt = 0,
                            resolveAttemptTotal = 0,
                            // 不拼 e.message：原始异常文本（英文/类名）不该进 UI，而且这里给不出可行动信息。
                            // :feature:subject 依赖不到 :core:network 的文案工具，与上面的"试完不命中"统一口径。
                            error = "解析失败，可尝试切换其他播放源",
                        )
                    }
                }
            }
    }

    fun toggleAutoNext() {
        _uiState.update { it.copy(autoNextEnabled = !it.autoNextEnabled) }
    }

    /** 重试当前地址：清掉错误态，由界面重新 prepare 或重新嗅探 */
    fun retry() {
        _uiState.update { it.copy(error = null) }
        val state = _uiState.value
        val currentTab = state.sources.getOrNull(state.selectedSourceIndex)
        if (currentTab != null && !currentTab.isDirect) {
            resolveCurrentEpisodeStream()
        }
    }

    /** 播放成功建立（STATE_READY）：清除该地址的失败标记，并把当前源记为“上次可用源” */
    fun onPlaybackReady() {
        failureStore?.markPlayable(_uiState.value.streamUrl, currentSourceId())
        val id = currentSourceId() ?: return
        if (id == session.lastSavedSourceId) return
        session.lastSavedSourceId = id
        viewModelScope.launch { settingsRepository.setLastPlaybackSourceId(id) }
    }

    fun onPlaybackError(message: String) {
        _uiState.update { it.copy(error = message) }
        failureStore?.markFailed(_uiState.value.streamUrl, message, currentSourceId())
    }

    /** 当前选中源的标识；拿不到就返回 null（计数只记到能认出来的源上） */
    private fun currentSourceId(): String? = _uiState.value.let { state -> state.sources.getOrNull(state.selectedSourceIndex)?.id }
}
