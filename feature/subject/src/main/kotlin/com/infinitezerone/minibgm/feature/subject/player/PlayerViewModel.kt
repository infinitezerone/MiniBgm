package com.infinitezerone.minibgm.feature.subject.player

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.ChineseConverter
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.common.onSuccess
import com.infinitezerone.minibgm.core.data.download.TorrentStreamEngine
import com.infinitezerone.minibgm.core.data.playback.PlaybackFailureStore
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import com.infinitezerone.minibgm.core.data.repository.DanmakuRepository
import com.infinitezerone.minibgm.core.data.repository.EpisodeStreamResolver
import com.infinitezerone.minibgm.core.data.repository.PlaybackHistoryRepository
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.data.repository.PlaybackSourceVerifier
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.DanmakuEpisodeCandidate
import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.DanmakuMatch
import com.infinitezerone.minibgm.core.model.DanmakuMatchState
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.PlaybackHistory
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * 线路类型分类
 */
enum class PlayerSourceKind {
    RULE,
    DIRECT,
    TORRENT_STREAM,
    LOCAL_FILE,
}

data class PlayerSourceTab(
    val id: String,
    val name: String,
    val rule: PlaybackSourceRule? = null,
    val isDirect: Boolean = false,
    @StringRes val nameRes: Int? = null,
    val kind: PlayerSourceKind = if (isDirect) PlayerSourceKind.DIRECT else PlayerSourceKind.RULE,
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
    /** 播完自动连播下一集（长期偏好，可在选集抽屉内关闭；真值来自设置仓库投影） */
    val autoNextEnabled: Boolean = true,
    /** 当前分集的断点续播位置（毫秒，0 表示没有可恢复的记录；与打卡是两套独立进度） */
    val resumePositionMs: Long = 0L,
    /** 播放源标签列表（包含默认直链与外部规则源） */
    val sources: List<PlayerSourceTab> = emptyList(),
    /** 源标识 → 本次会话内的连续失败次数；用于在选源界面弱化屡试屡败的源 */
    val sourceFailureCounts: Map<String, Int> = emptyMap(),
    val selectedSourceIndex: Int = 0,
    /**
     * 当前 [streamUrl] 真正归属的播放源标识（null 表示没有在播地址）。
     *
     * 与 [selectedSourceIndex] 分离：用户切到新线路后，旧线路的画面会被**故意保留**到解析完成，
     * 这十几秒里"选中的"与"正在播的"并不是同一条线。健康度归因、"上次可用源"与界面徽标必须读它，
     * 否则会把旧流的 Ready/Error 记到新源头上，并在切源失败后谎称新线路"正在使用"。
     */
    val playingSourceId: String? = null,
    /** 全部分集列表（由条目详情持续同步） */
    val episodes: List<PlayerEpisodeItem> = emptyList(),
    /** 条目总分集数（由条目或仓库总数探测得出） */
    val totalEpisodes: Int = 0,
    /** 是否还有未加载的分集 */
    val hasMoreEpisodes: Boolean = false,
    /** 是否正在后台续拉分集 */
    val isLoadingMoreEpisodes: Boolean = false,
    /** 异步嗅探直链中 */
    val isResolvingSource: Boolean = false,
    /** 取源时已发起的第几次关键词尝试（1 起）；0 表示未在解析 */
    val resolveAttempt: Int = 0,
    /** 本次解析的关键词尝试总次数；0 表示未在解析 */
    val resolveAttemptTotal: Int = 0,
    /** 画中画（PiP）开关 */
    val pipEnabled: Boolean = true,
    /** 源站返回的真实匹配条目名（用于在播放器与选源栏向用户展示） */
    val matchedVodName: String? = null,
    /** 弹幕显示开关（长期偏好，真值来自设置仓库投影；关闭期间不发起任何弹幕请求） */
    val isDanmakuEnabled: Boolean = true,
    /** 当前分集已加载的弹幕列表 */
    val danmakus: List<DanmakuItem> = emptyList(),
    /** 弹幕加载中状态 */
    val isDanmakuLoading: Boolean = false,
    /** 弹幕分集匹配元信息（用户覆盖 / 自动命中 / 回退兜底 + 全部候选） */
    val danmakuMatch: DanmakuMatch? = null,
    /**
     * 「关闭字幕」的长期偏好（真值来自设置仓库投影）。
     *
     * 只记录用户"要不要字幕"的立场，不记录具体轨道——轨道选择仍由引擎按系统语言/用户当场选择持有。
     * 界面据此把引擎字幕轨按偏好关掉，避免重启后用户被迫重新关一次。
     */
    val subtitlesDisabled: Boolean = false,
) {
    val hasNext: Boolean
        get() {
            if (episodes.size > 1) {
                val idx =
                    episodes.indexOfFirst {
                        (episodeId > 0 && it.id == episodeId) || (it.sort == episodeSort && it.type == episodeType)
                    }
                return idx in 0 until episodes.lastIndex || hasMoreEpisodes
            }
            return currentIndex < queue.lastIndex
        }

    val hasPrevious: Boolean
        get() {
            if (episodes.size > 1) {
                val idx =
                    episodes.indexOfFirst {
                        (episodeId > 0 && it.id == episodeId) || (it.sort == episodeSort && it.type == episodeType)
                    }
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
        /** 运行时文本（服务端消息等非资源文案）；非空白时优先展示 */
        val message: String? = null,
        /** 资源文案；message 为空白时使用，UI 侧负责解析 */
        @StringRes val messageRes: Int = 0,
        /** [messageRes] 的格式化参数 */
        val formatArgs: List<String> = emptyList(),
        /**
         * 可选动作按钮的文案资源（如换线失败后的「切换线路」）；0 表示这条提示没有动作。
         *
         * 只带资源 ID 与语义、不塞 lambda：UI 侧据此映射到本页已有入口，
         * ViewModel 不持有 UI 回调，配置变更后重新投递也依然成立。
         */
        @StringRes val actionRes: Int = 0,
        /** 动作语义；null 表示纯告知型提示 */
        val action: PlayerSnackbarAction? = null,
    ) : PlayerUiEvent

    data class MarkedWatched(
        val epNumber: Int,
    ) : PlayerUiEvent

    /**
     * 一次性提示上可携带的动作语义。
     *
     * 只枚举「要做什么」，具体入口由 UI 侧映射（避免事件携带 ViewModel / Composable 引用）。
     */
    enum class PlayerSnackbarAction {
        /** 换个播放线路：重新打开统一线路面板（横竖屏各走自己的形态） */
        OPEN_SOURCES,
    }
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
    val danmakuEnabled: Boolean = true,
    val autoNextEnabled: Boolean = true,
    val commentSortOrder: String = "",
    val subtitlesDisabled: Boolean = false,
    val collection: UserCollection? = null,
    val totalEpisodes: Int = 0,
    val hasMoreEpisodes: Boolean = false,
)

/**
 * 内容消费侧长期偏好投影（画中画 / 弹幕显示 / 自动连播 / 吐槽排序 / 关闭字幕），唯一真源是设置仓库。
 *
 * 几者必须走**同一条**流：一份偏好拆进两条 combine 支路时，一次写入会各推一次、产出两份快照，
 * 无卡口的下游（弹幕兜底拉取）就会被重复触发。吐槽排序是三个评论区页面**共用**的一条偏好
 * （这里只投影，写入侧各处共用同一个 setter），故解析必须统一走 [CommentSortOrder.fromKey]。
 */
private data class PlayViewPreferences(
    val pipEnabled: Boolean = true,
    val danmakuEnabled: Boolean = true,
    val autoNextEnabled: Boolean = true,
    val commentSortOrder: String = "",
    val subtitlesDisabled: Boolean = false,
)

private data class RepoSubjectData(
    val subject: Subject?,
    val episodes: List<Episode>,
    val collection: UserCollection?,
    val totalEpisodes: Int,
    val hasMoreEpisodes: Boolean,
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

    /** 弹幕 FALLBACK 提示一次性守卫：一部番仅提示一次，避免每集开播都吵 */
    var hasHintedDanmakuFallback: Boolean = false

    /**
     * 最近一次**真实发起**的弹幕拉取请求键（`episodeId:episodeSort`；null 表示本会话尚未发起）。
     *
     * 自动路径据此去重：兜底拉取的判据是「弹幕为空」，而空结果会一直为空——播放进度落盘、
     * 源健康度变化都会重放快照，没有请求键就会把同一分集重拉成无界重试；换集与手动重开弹幕
     * 都会改变请求键（重开时显式清空），该拉的一次都不会少。
     */
    var lastDanmakuRequestKey: String? = null

    /**
     * 「重开弹幕但确实没有弹幕」一次性提示意图：
     * 关闭期间不拉取弹幕、重开后才补拉，所以「当前没有弹幕」只能由**这次拉取的真实结果**给出，
     * 严禁在拨开关的瞬间预判为空——那会把还在路上的弹幕说成「未加载到弹幕」。
     */
    var pendingDanmakuEmptyHint: Boolean = false

    /** 自动打卡一次性守卫：一次播放会话仅触发一次，换集后复位 */
    var hasTriggeredAutoMark: Boolean = false

    /** 断点续播位置表镜像（由 [PlayerRepoSnapshot.positions] 持续同步） */
    var positionsMap: Map<String, Long> = emptyMap()

    /** 界面回报的当前播放位置（内存暂存，按 [POSITION_SAVE_TICKS] 节流落盘） */
    var latestPositionMs: Long = 0L

    /**
     * 界面回报的当前媒体总时长（内存暂存）。
     *
     * 观看记录的进度分母只能来自引擎实测时长——条目元数据里的时长是字符串、且常与实际片源不一致，
     * 拿它算进度会把「看到 23:50 / 24:00」算成假的未看完。
     */
    var latestDurationMs: Long = 0L

    /** 当前分集是否已完成入场续播点解析（锁定后，播放中途的自动落盘严禁反向冲刷成续播点） */
    var initialResumeResolvedEpisodeId: Long? = null

    /**
     * 换集/结束时统一清空进度跟踪。
     *
     * 位置、时长与节流计数必须同时归零：只清位置会让新分集沿用上一集的总时长算进度，
     * 短分集瞬间被判成「已看完」。
     */
    fun resetProgressTracking() {
        latestPositionMs = 0L
        latestDurationMs = 0L
        progressTicksSinceSave = 0
        initialResumeResolvedEpisodeId = null
    }

    /** 距上次落盘累计的进度跳数 */
    var progressTicksSinceSave: Int = 0

    /** 条目片名别名（台译/港译/英文名/罗马音），来自已缓存 Subject 的 infobox */
    var subjectAliases: List<String> = emptyList()

    /** 条目日文原名 */
    var subjectOriginalName: String = ""

    /** 已落盘的“上次可用源”标识，避免每次 READY 都重复写 DataStore */
    var lastSavedSourceId: String = ""

    /**
     * 用户当前选中的播放源标识（跨规则列表变化保持身份）。
     *
     * [PlayerUiState.selectedSourceIndex] 只有位置语义：规则被启停/增删后下标会指向另一条规则，
     * 列表刷新必须按标识找回选中项（找不到才回落位置），否则选中线路会静默串到别的规则上。
     */
    var selectedSourceId: String? = null

    /** 到达列表末尾且后端仍有更多分集时，等待续拉完成后自动连播下一集的意图标记 */
    var pendingAutoNextOnMoreLoaded: Boolean = false

    /**
     * 换集后「按新地址查续播点」的待办标记。
     *
     * 直链换集时新地址当下可知，当场查表下发；规则源换集时旧画面被故意保留、新地址要等嗅探结果，
     * 只能把查表推迟到解析成功那一刻一次性消费——否则会把上一集的进度当成新分集的续播点下发，
     * 让新分集直接跳到旧分集的时间轴（短集会因此瞬间播完并触发连播空跳）。
     */
    var pendingResumeLookupForNewEpisode: Boolean = false
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
    private val danmakuRepository: DanmakuRepository? = null,
    private val torrentStreamEngine: TorrentStreamEngine? = null,
    private val playbackHistoryRepository: PlaybackHistoryRepository? = null,
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
            val directUrl = route.queue.firstOrNull()?.streamUrl ?: route.streamUrl
            val directKind = resolvePlayerSourceKind(directUrl)
            val (nameRes, defaultName) =
                when (directKind) {
                    PlayerSourceKind.LOCAL_FILE ->
                        R.string.feature_subject_player_source_local_file to "本地离线"
                    PlayerSourceKind.TORRENT_STREAM ->
                        R.string.feature_subject_player_source_torrent_stream to "BT 边下边播"
                    else ->
                        R.string.feature_subject_player_source_default_direct to "默认直链"
                }
            list.add(
                PlayerSourceTab(
                    id = "direct",
                    name = defaultName,
                    isDirect = true,
                    nameRes = nameRes,
                    kind = directKind,
                ),
            )
        }
        rules.filter { it.isEnabled && it.kind != PlaybackRuleKind.TORRENT }.forEach { rule ->
            list.add(
                PlayerSourceTab(
                    id = rule.id,
                    name = rule.name,
                    rule = rule,
                    isDirect = false,
                    kind = PlayerSourceKind.RULE,
                ),
            )
        }
        return list
    }

    private fun createInitialState(): PlayerUiState {
        val entry = queue[session.currentIndex]
        val initialSources = buildSources(emptyList())
        val initialDirectId = initialSources.firstOrNull { it.isDirect }?.id
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
            resumePositionMs = 0L,
            sources = initialSources,
            selectedSourceIndex = 0,
            playingSourceId = initialDirectId?.takeIf { entry.streamUrl.isNotBlank() },
            episodes = initialEpisodes(),
            isResolvingSource = false,
        )
    }

    private val logger = bgmLogger("Bgm/Player")

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlayerUiEvent>(Channel.BUFFERED)
    val events: Flow<PlayerUiEvent> = _events.receiveAsFlow()

    private val isLoggedIn =
        authRepository.isLoggedIn
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var resolveJob: Job? = null
    private var danmakuJob: Job? = null

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

    /**
     * 播放页长期偏好流（画中画 / 弹幕显示 / 自动连播 / 吐槽排序）：设备侧稳定偏好，与条目无关，始终订阅
     * （不再按 subjectId 门控——门控只为迁就旧收集器，代价是同一份设置被拆成两条支路各推一次快照）。
     */
    private val playViewPreferencesFlow: Flow<PlayViewPreferences> =
        settingsRepository.settings.map {
            PlayViewPreferences(
                pipEnabled = it.pipEnabled,
                danmakuEnabled = it.danmakuEnabled,
                autoNextEnabled = it.autoNextEnabled,
                commentSortOrder = it.commentSortOrder,
                subtitlesDisabled = it.subtitlesDisabled,
            )
        }

    /** 仓库总分集数流 */
    private val totalEpisodesFlow: Flow<Int> =
        if (route.subjectId > 0) {
            subjectRepository?.getTotalEpisodesStream(route.subjectId) ?: flowOf(0)
        } else {
            flowOf(0)
        }

    /** 是否有未加载完分集流 */
    private val hasMoreEpisodesFlow: Flow<Boolean> =
        if (route.subjectId > 0) {
            subjectRepository?.hasMoreEpisodesStream(route.subjectId) ?: flowOf(false)
        } else {
            flowOf(false)
        }

    /**
     * 数据侧单点投影：全部仓库流 combine 成一个 [PlayerRepoSnapshot] 快照流。
     *
     * 条目/分集/收藏/总数 四路仅在携带 subjectId 时订阅（与迁移前逐路收集器的门控一致，
     * 其余场景给空默认值，快照仍可即时产出）；播放页偏好与源健康度常驻。
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
                playViewPreferencesFlow,
            ) { health, playView ->
                health.mapValues { it.value.consecutiveFailures } to playView
            },
            combine(
                subjectFlow,
                rawEpisodesFlow,
                collectionFlow,
                totalEpisodesFlow,
                hasMoreEpisodesFlow,
            ) { subject, episodes, collection, totalEpisodes, hasMoreEpisodes ->
                RepoSubjectData(subject, episodes, collection, totalEpisodes, hasMoreEpisodes)
            },
        ) { (positions, rules), (failureCounts, playView), subjectData ->
            PlayerRepoSnapshot(
                positions = positions,
                rules = rules,
                failureCounts = failureCounts,
                subject = subjectData.subject,
                rawEpisodes = subjectData.episodes,
                pipEnabled = playView.pipEnabled,
                danmakuEnabled = playView.danmakuEnabled,
                autoNextEnabled = playView.autoNextEnabled,
                commentSortOrder = playView.commentSortOrder,
                subtitlesDisabled = playView.subtitlesDisabled,
                collection = subjectData.collection,
                totalEpisodes = subjectData.totalEpisodes,
                hasMoreEpisodes = subjectData.hasMoreEpisodes,
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
            viewModelScope.launch {
                val history = playbackHistoryRepository?.getHistoryByEpisode(route.episodeId)
                session.initialResumeResolvedEpisodeId = route.episodeId
                if (history != null && history.positionMs >= MIN_RESUME_POSITION_MS && !history.isCompleted) {
                    _uiState.update { current ->
                        if (current.resumePositionMs == 0L) {
                            current.copy(resumePositionMs = history.positionMs)
                        } else {
                            current
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            _uiState
                .map { it.streamUrl }
                .distinctUntilChanged()
                .collect { url ->
                    val isTorrentLocal = url.startsWith("http://127.0.0.1")
                    torrentStreamEngine?.setPlayerSessionActive(isTorrentLocal)
                }
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
        // 吐槽排序（长期偏好，与分集详情页共用同一条设置）：真值在设置仓库，这里只投影，
        // 并驱动评论区按新规则重整（仅在真的变化时调用，避免每次快照重放都重排一遍列表）。
        // 放在 _uiState.update 之外：它是另一个状态容器的副作用，不该塞进快照写入的 CAS 重试里。
        val projectedSortOrder = CommentSortOrder.fromKey(snapshot.commentSortOrder)
        if (projectedSortOrder != commentsDelegate.state.value.sortOrder) {
            commentsDelegate.setSortOrder(projectedSortOrder)
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
                // 入口携带的初始规则只在首次建表时生效：之后用户手动选的线路不得被列表刷新拽回初始规则
                val targetRuleIndex =
                    if (route.initialRuleId.isNotBlank() && next.sources.isEmpty()) {
                        newSources.indexOfFirst { it.rule?.id == route.initialRuleId }.takeIf { it >= 0 }
                    } else {
                        null
                    }
                // 仅在首次构建源列表且入口未携带直链时应用上次可用源：
                // 直链是显式给进来的（默认直链/助手），直链始终优先，记忆源只对无直链入口兜底；
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
                // 身份优先：列表增删/启停后按源标识找回原选中项，绝不沿用旧下标
                // （下标只有位置语义，在前面插一条规则就会静默指向另一条线，随后健康度、
                //   "上次可用源"与界面徽标会全部记到错的源上）
                val previousSelectedId =
                    session.selectedSourceId ?: next.sources.getOrNull(next.selectedSourceIndex)?.id
                val identityIndex =
                    previousSelectedId?.let { id -> newSources.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
                val newIndex =
                    targetRuleIndex
                        ?: identityIndex
                        ?: preferredIndex
                        ?: next.selectedSourceIndex.coerceIn(0, (newSources.size - 1).coerceAtLeast(0))
                next =
                    next.copy(
                        sources = newSources,
                        selectedSourceIndex = newIndex,
                    )
                session.selectedSourceId = newSources.getOrNull(newIndex)?.id
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

            val effectiveTotalEpisodes =
                maxOf(
                    snapshot.totalEpisodes,
                    snapshot.subject?.eps ?: 0,
                    snapshot.subject?.totalEpisodes ?: 0,
                    mappedEpisodes.size,
                )

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
                    totalEpisodes = effectiveTotalEpisodes,
                    hasMoreEpisodes = snapshot.hasMoreEpisodes,
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
            // 弹幕显示开关（长期偏好，由设置仓库投影；命令侧据此决定是否补拉）
            if (snapshot.danmakuEnabled != next.isDanmakuEnabled) {
                next = next.copy(isDanmakuEnabled = snapshot.danmakuEnabled)
            }
            // 播放结束自动连播（长期偏好）
            if (snapshot.autoNextEnabled != next.autoNextEnabled) {
                next = next.copy(autoNextEnabled = snapshot.autoNextEnabled)
            }
            // 关闭字幕（长期偏好）：界面只投影，UI 侧据此按偏好把引擎字幕轨关掉
            if (snapshot.subtitlesDisabled != next.subtitlesDisabled) {
                next = next.copy(subtitlesDisabled = snapshot.subtitlesDisabled)
            }
            // 首次进页且尚未开始播放、尚未锁定初次续播点时，跟随位置表同步历史记录；
            // 播放一旦发生（latestPositionMs > 0 或已锁定），中途周期性落盘产生的新位置严禁反向冲刷回 resumePositionMs
            if (session.initialResumeResolvedEpisodeId != next.episodeId && session.latestPositionMs <= 0L) {
                if (next.resumePositionMs == 0L && next.streamUrl.isNotBlank()) {
                    val saved = snapshot.positions[next.streamUrl] ?: 0L
                    if (saved >= MIN_RESUME_POSITION_MS) {
                        next = next.copy(resumePositionMs = saved)
                        session.initialResumeResolvedEpisodeId = next.episodeId
                    }
                }
            }
            next
        }
        if (session.pendingAutoNextOnMoreLoaded) {
            val currentState = _uiState.value
            val currentIdx =
                currentState.episodes.indexOfFirst {
                    (currentState.episodeId > 0 && it.id == currentState.episodeId) ||
                        (it.sort == currentState.episodeSort && it.type == currentState.episodeType)
                }
            if (currentIdx in 0 until currentState.episodes.lastIndex) {
                session.pendingAutoNextOnMoreLoaded = false
                val nextEp = currentState.episodes[currentIdx + 1]
                if (nextEp.type == currentState.episodeType) {
                    selectEpisode(nextEp)
                }
            } else if (!currentState.isLoadingMoreEpisodes && !currentState.hasMoreEpisodes) {
                session.pendingAutoNextOnMoreLoaded = false
            }
        }
        if (_uiState.value.episodeId > 0L && _uiState.value.comments.isEmpty() && !_uiState.value.isCommentsLoading) {
            loadComments()
        }
        if (_uiState.value.subjectName.isNotBlank() && _uiState.value.danmakus.isEmpty() && !_uiState.value.isDanmakuLoading) {
            loadDanmakuIfEnabled()
        }
        return sourcesChanged
    }

    /** 界面周期回报播放进度（毫秒）：内存暂存，每 [POSITION_SAVE_TICKS] 跳落盘一次 */
    fun onProgressChanged(positionMs: Long) {
        if (positionMs <= 0L) return
        session.latestPositionMs = positionMs
        session.initialResumeResolvedEpisodeId = _uiState.value.episodeId
        if (++session.progressTicksSinceSave >= POSITION_SAVE_TICKS) {
            session.progressTicksSinceSave = 0
            flushPlaybackPosition()
        }
    }

    /** 界面回报当前媒体总时长（毫秒）：只看内存，落盘时随观看记录一并写入 */
    fun onDurationChanged(durationMs: Long) {
        if (durationMs <= 0L) return
        session.latestDurationMs = durationMs
    }

    /**
     * 把内存中的当前分集位置落盘（立即捕获 url/位置再异步写，换集时先于状态切换调用）。
     * 由节流循环与界面 ON_PAUSE 触发。
     */
    fun flushPlaybackPosition() {
        val state = _uiState.value
        val url = state.streamUrl
        val position = session.latestPositionMs
        if (url.isBlank() || position <= 0L) return
        viewModelScope.launch {
            settingsRepository.savePlaybackPosition(url, position)
            val epId = if (state.episodeId > 0L) state.episodeId else route.episodeId
            if (epId > 0L) {
                playbackHistoryRepository?.saveHistory(
                    PlaybackHistory(
                        episodeId = epId,
                        subjectId = if (state.subjectId > 0L) state.subjectId else route.subjectId,
                        subjectName = state.subjectName.ifBlank { route.subjectName },
                        episodeName = state.episodeName.ifBlank { route.episodeName },
                        episodeSort = state.episodeSort,
                        coverUrl = state.subjectCoverUrl,
                        streamUrl = url,
                        positionMs = position,
                        durationMs = session.latestDurationMs,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /**
     * 换集后一次性消费「按新地址查续播点」的待办：换集时新地址尚不可知（规则源要等嗅探结果），
     * 在真实地址落定的那一刻查表下发。未换集的路径（重试 / 换源）返回 null，
     * 保持「同一分集内换线路沿用当前进度」的原语义不变。
     *
     * @return 新分集自己的续播点（0 表示无记录）；null 表示本次不是换集，调用方不应改动续播点
     */
    private fun consumePendingResumePositionForNewEpisode(url: String): Long? {
        if (!session.pendingResumeLookupForNewEpisode) return null
        session.pendingResumeLookupForNewEpisode = false
        val memoryPosition = session.positionsMap[url]?.takeIf { it >= MIN_RESUME_POSITION_MS }
        syncResumePositionForEpisode(_uiState.value.episodeId, url)
        return memoryPosition
    }

    /**
     * 换集或规则源地址就绪时，联动 [PlaybackHistoryRepository] 兜底恢复历史进度：
     * 解决动态 URL、签名过期或内存表未命中导致换集续播点丢失的问题。
     */
    private fun syncResumePositionForEpisode(
        episodeId: Long,
        fallbackUrl: String? = null,
    ) {
        if (episodeId <= 0L && fallbackUrl.isNullOrBlank()) return
        viewModelScope.launch {
            val historyPosition =
                if (episodeId > 0L) {
                    val history = playbackHistoryRepository?.getHistoryByEpisode(episodeId)
                    if (history != null && history.positionMs > 0L && !history.isCompleted) {
                        history.positionMs
                    } else {
                        0L
                    }
                } else {
                    0L
                }
            val memoryPosition = fallbackUrl?.let { session.positionsMap[it] } ?: 0L
            val bestPosition = maxOf(historyPosition, memoryPosition)
            session.initialResumeResolvedEpisodeId = episodeId
            if (bestPosition >= MIN_RESUME_POSITION_MS) {
                _uiState.update { current ->
                    if (current.episodeId == episodeId && current.resumePositionMs == 0L) {
                        current.copy(resumePositionMs = bestPosition)
                    } else {
                        current
                    }
                }
            }
        }
    }

    /**
     * 显式续拉下一屏分集
     */
    fun loadMoreEpisodes() {
        if (route.subjectId <= 0 || _uiState.value.isLoadingMoreEpisodes || !_uiState.value.hasMoreEpisodes) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreEpisodes = true) }
            val result = subjectRepository?.loadMoreEpisodes(route.subjectId, descending = false)
            _uiState.update { current ->
                val nextHasMore =
                    when (result) {
                        is com.infinitezerone.minibgm.core.common.AppResult.Success -> result.data
                        else -> current.hasMoreEpisodes
                    }
                current.copy(
                    isLoadingMoreEpisodes = false,
                    hasMoreEpisodes = nextHasMore,
                )
            }
        }
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
                    _events.send(
                        PlayerUiEvent.ShowSnackbar(
                            messageRes = R.string.feature_subject_player_auto_marked_watched,
                            formatArgs = listOf(epNumber.toString()),
                        ),
                    )
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
        if (state.streamUrl.isBlank()) return false
        markWatched(state.episodeSort.toInt())
        val finishedUrl = state.streamUrl
        session.resetProgressTracking()
        viewModelScope.launch { settingsRepository.clearPlaybackPosition(finishedUrl) }
        if (!state.autoNextEnabled) return false

        // 优先根据全部分集列表查找下一集
        val epIndex =
            state.episodes.indexOfFirst {
                (state.episodeId > 0 && it.id == state.episodeId) ||
                    (it.sort == state.episodeSort && it.type == state.episodeType)
            }
        if (epIndex in 0 until state.episodes.lastIndex) {
            val nextEp = state.episodes[epIndex + 1]
            // 同类型分集才自动连播（正篇完结不自动切入特别篇/特典）
            if (nextEp.type == state.episodeType) {
                selectEpisode(nextEp)
                return true
            }
            return false
        }

        // 当前分集是列表末尾但后端仍有更多分集时，标记待连播并触发续拉
        if (epIndex == state.episodes.lastIndex && state.hasMoreEpisodes && route.subjectId > 0) {
            session.pendingAutoNextOnMoreLoaded = true
            loadMoreEpisodes()
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
        val entry = queue[index]
        val currentEp =
            _uiState.value.episodes.firstOrNull {
                (entry.episodeId > 0 && it.id == entry.episodeId) ||
                    (it.sort == entry.episodeSort && it.type == entry.episodeType)
            }
        if (currentEp != null) {
            selectEpisode(currentEp)
            return
        }

        flushPlaybackPosition()
        danmakuJob?.cancel()
        session.resetProgressTracking()
        session.hasTriggeredAutoMark = false
        session.pendingAutoNextOnMoreLoaded = false
        session.pendingDanmakuEmptyHint = false
        session.pendingResumeLookupForNewEpisode = _uiState.value.currentSource?.isDirect == false
        session.currentIndex = index

        _uiState.update {
            it.copy(
                episodeId = entry.episodeId,
                streamUrl = entry.streamUrl,
                episodeName = entry.episodeName,
                episodeSort = entry.episodeSort,
                episodeType = entry.episodeType,
                requestHeaders = entry.requestHeaders,
                playingSourceId =
                    when {
                        entry.streamUrl.isBlank() -> null
                        it.currentSource?.isDirect == true -> it.sources.getOrNull(it.selectedSourceIndex)?.id
                        // 规则源换集：队列里的临时地址随后会被嗅探结果替换，归属先保持不动
                        else -> it.playingSourceId
                    },
                currentIndex = index,
                isWatched = false,
                currentEpisodeDesc = "",
                currentEpisodeAirdate = "",
                currentEpisodeCommentCount = 0,
                comments = emptyList(),
                commentsError = null,
                danmakus = emptyList(),
                danmakuMatch = null,
                autoMarked = false,
                resumePositionMs = session.positionsMap[entry.streamUrl] ?: 0L,
                error = null,
            )
        }
        syncResumePositionForEpisode(entry.episodeId, entry.streamUrl)
        if (_uiState.value.currentSource?.isDirect == false || entry.streamUrl.isBlank()) {
            resolveCurrentEpisodeStream()
        }
        loadDanmakuIfEnabled()
        commentsDelegate.loadComments(entry.episodeId, force = true)
    }

    /** 切换播放源 */
    fun selectSource(index: Int) {
        val state = _uiState.value
        if (index !in state.sources.indices) return
        val targetId = state.sources[index].id
        // 只有「同一条线、确实正在播它、没有在途解析/未处理错误」才算重复点击。
        // 解析失败后必须允许再点一次重试，否则用户只能"先切到别的线，再切回来"
        if (index == state.selectedSourceIndex &&
            targetId == state.playingSourceId &&
            !state.isResolvingSource &&
            state.error == null
        ) {
            return
        }
        flushPlaybackPosition()
        session.selectedSourceId = targetId
        _uiState.update { it.copy(selectedSourceIndex = index, matchedVodName = null) }
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
        session.resetProgressTracking()
        session.hasTriggeredAutoMark = false
        session.pendingAutoNextOnMoreLoaded = false
        session.pendingDanmakuEmptyHint = false

        val queueIndex =
            queue.indexOfFirst {
                (it.episodeId > 0 && it.episodeId == episode.id) ||
                    (it.episodeSort >= 0f && it.episodeSort == episode.sort)
            }
        if (queueIndex >= 0) {
            session.currentIndex = queueIndex
        }

        val isDirect = state.currentSource?.isDirect == true
        val directEntry = if (isDirect && queueIndex >= 0) queue[queueIndex] else null
        val directMissing = isDirect && directEntry == null
        // 规则源此刻还拿不到新分集地址（旧画面被故意保留），续播点查表推迟到嗅探出地址那一刻
        session.pendingResumeLookupForNewEpisode = state.currentSource?.isDirect == false
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
                danmakus = emptyList(),
                danmakuMatch = null,
                autoMarked = false,
                // 非直链源保留当前画面（不清空），等嗅探完成再替换，避免换集瞬间黑屏；
                // 直链源精确取队列中的对应集，未命中就如实报缺，绝不串到别的集
                streamUrl =
                    when {
                        directEntry != null -> directEntry.streamUrl
                        directMissing -> ""
                        else -> it.streamUrl
                    },
                playingSourceId =
                    when {
                        directEntry != null -> it.sources.getOrNull(it.selectedSourceIndex)?.id
                        directMissing -> null
                        // 非直链源保留旧画面，归属要等嗅探出地址那一刻才替换
                        else -> it.playingSourceId
                    },
                requestHeaders =
                    when {
                        directEntry != null -> directEntry.requestHeaders
                        directMissing -> emptyMap()
                        else -> it.requestHeaders
                    },
                // 续播点属于"分集"而不是"上一个地址"：换集必须按新分集自己的记录下发，
                // 绝不能沿用上一集的进度（规则源的地址稍后由 consumePendingResumePositionForNewEpisode 补上）
                resumePositionMs = directEntry?.let { session.positionsMap[it.streamUrl] ?: 0L } ?: 0L,
                error =
                    when {
                        !directMissing -> null
                        state.currentSource?.kind == PlayerSourceKind.TORRENT_STREAM ->
                            "当前 BT 资源仅包含单集，请切换线路或重新选源"
                        state.currentSource?.kind == PlayerSourceKind.LOCAL_FILE ->
                            "当前离线缓存仅包含单集，请切换线路或重新选源"
                        else ->
                            "直链列表中没有该分集，请切换到其他播放源"
                    },
            )
        }
        syncResumePositionForEpisode(episode.id, directEntry?.streamUrl)
        resolveCurrentEpisodeStream()
        commentsDelegate.loadComments(episode.id, force = true)
        loadDanmakuIfEnabled()
    }

    /**
     * 手动切换当前分集的看过/未看标记（支持用户点击打卡与撤销打卡）。
     */
    fun manualToggleWatched() {
        if (!isLoggedIn.value) {
            viewModelScope.launch {
                _events.send(PlayerUiEvent.ShowSnackbar(messageRes = R.string.feature_subject_player_login_required))
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
                            messageRes =
                                if (targetWatched) {
                                    R.string.feature_subject_player_manual_marked_watched
                                } else {
                                    R.string.feature_subject_player_manual_unmarked_watched
                                },
                            formatArgs = listOf(epNumber.toString()),
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

    /**
     * 清晰度选择记忆偏好（0 = 自动自适应；>0 = 期望视频高度）。
     * 起播后由界面在轨道就绪时自动匹配最接近档位；用户手动切换时经 [onQualitySelected] 记录。
     */
    val preferredQualityHeight: StateFlow<Int> =
        settingsRepository.settings
            .map { it.preferredQualityHeight }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 记录用户手动选择的清晰度偏好（0 = 恢复自动自适应） */
    fun onQualitySelected(height: Int) {
        viewModelScope.launch {
            settingsRepository.setPreferredQualityHeight(height)
        }
    }

    /**
     * 切换弹幕显示开关。
     *
     * 这是**长期偏好**（重进播放页、换集、换条目都保持）：只写设置仓库、由快照投影回读，
     * 严禁就地翻转 UI 状态形成第二事实源；关闭期间不发起任何弹幕请求，重开时才补拉
     * （补拉由快照驱动的 [loadDanmakuIfEnabled] 承接），「当前没有弹幕」只在这次补拉
     * 真的返回空结果时才提示。
     */
    fun toggleDanmaku() {
        val targetEnabled = !_uiState.value.isDanmakuEnabled
        if (targetEnabled) {
            // 重开 = 显式要求补拉一次（关闭期间不拉，内存里也可能本就没有数据）
            session.lastDanmakuRequestKey = null
            session.pendingDanmakuEmptyHint = true
        }
        viewModelScope.launch {
            settingsRepository.setDanmakuEnabled(targetEnabled)
        }
    }

    /**
     * 自动路径的弹幕兜底拉取：弹幕关闭时零网络请求，同一分集只自动拉一次。
     *
     * 判据必须是**同步**的 [PlayerUiState.isDanmakuEnabled] 投影，且只在快照合流内调用：
     * 投影在同一次 [applyRepoSnapshot] 里先于兜底拉取更新，首个快照就已带持久化真值。
     * （异步回读设置会把闸口推迟到协程恢复之后，同一写入推来的两份快照会各自放行一次，重复拉取。）
     */
    private fun loadDanmakuIfEnabled() {
        val state = _uiState.value
        if (!state.isDanmakuEnabled) {
            logger.d { "跳过弹幕拉取（弹幕开关已关闭）" }
            return
        }
        if (session.lastDanmakuRequestKey == "${state.episodeId}:${state.episodeSort}") {
            logger.d { "跳过弹幕拉取（本分集已请求过，不因快照重放而重复拉取）" }
            return
        }
        loadDanmaku()
    }

    /** 异步拉取当前分集的弹弹play全网聚合弹幕 */
    fun loadDanmaku() {
        val repo = danmakuRepository ?: return
        val current = _uiState.value
        val title = current.subjectName.ifBlank { session.subjectOriginalName }
        val epSort = current.episodeSort
        if (title.isBlank() || epSort <= 0f) {
            logger.d { "跳过弹幕拉取 (标题为空或分集编号非法): title='$title', epSort=$epSort" }
            return
        }

        val epId = current.episodeId
        session.lastDanmakuRequestKey = "$epId:$epSort"
        danmakuJob?.cancel()
        danmakuJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isDanmakuLoading = true) }
                logger.i { "开始拉取分集弹幕: title='$title', epSort=$epSort, epId=$epId" }
                val result =
                    repo.getDanmaku(
                        bangumiEpisodeId = epId,
                        subjectId = current.subjectId,
                        title = title,
                        episodeSort = epSort,
                        episodeName = current.episodeName,
                    )
                val shouldHintEmpty = session.pendingDanmakuEmptyHint
                session.pendingDanmakuEmptyHint = false
                if (_uiState.value.episodeId == epId && _uiState.value.episodeSort == epSort) {
                    logger.i { "分集弹幕拉取完成: ${result.items.size} 条 (title='$title', epSort=$epSort, state=${result.match?.state})" }
                    _uiState.update {
                        it.copy(danmakus = result.items, danmakuMatch = result.match, isDanmakuLoading = false)
                    }
                    if (shouldHintEmpty && result.items.isEmpty()) {
                        _events.send(PlayerUiEvent.ShowSnackbar(messageRes = R.string.feature_subject_player_danmaku_empty_hint))
                    }
                    val match = result.match
                    if (match?.state == DanmakuMatchState.FALLBACK &&
                        match.candidates.isNotEmpty() &&
                        !session.hasHintedDanmakuFallback
                    ) {
                        session.hasHintedDanmakuFallback = true
                        _events.send(PlayerUiEvent.ShowSnackbar(messageRes = R.string.feature_subject_player_danmaku_match_fallback_hint))
                    }
                }
            }
    }

    /**
     * 用户手动纠正弹幕分集：落库覆盖后立即按新分集重载弹幕。
     * 覆盖是该 bangumi 分集的持久真值，之后的加载与重进播放都直接命中。
     */
    fun selectDanmakuCandidate(candidate: DanmakuEpisodeCandidate) {
        val repo = danmakuRepository ?: return
        val current = _uiState.value
        if (current.episodeId <= 0L) return
        // 弹幕关闭期间零请求：两个入口都已随开关收起（控制栏按钮 / 设置面板行），这里是竞态兜底——
        // 关掉开关的那一帧里入口可能尚未重组掉，放行会给「已关闭的功能」写持久化覆盖并立即拉一次弹幕。
        if (!current.isDanmakuEnabled) {
            logger.d { "忽略弹幕分集纠正（弹幕开关已关闭）" }
            return
        }
        viewModelScope.launch {
            repo.saveDanmakuOverride(
                bangumiEpisodeId = current.episodeId,
                subjectId = current.subjectId,
                candidate = candidate,
            )
            _events.send(
                PlayerUiEvent.ShowSnackbar(
                    messageRes = R.string.feature_subject_player_danmaku_switched,
                    formatArgs = listOf(candidate.animeTitle, candidate.episodeTitle),
                ),
            )
            loadDanmaku()
        }
    }

    /**
     * 切换单集吐槽排序规则（热门 / 楼层 / 最新）。
     *
     * 这是播放页与分集详情页**共用**的长期偏好：只写设置仓库、由快照投影回读（见 [applyRepoSnapshot]），
     * 严禁就地驱动 delegate 形成第二事实源——两处各持一份会话状态时，这里改成「最新」、详情页仍是「热门」，
     * 重启又双双回默认。
     */
    fun setCommentSortOrder(order: CommentSortOrder) {
        viewModelScope.launch {
            settingsRepository.setCommentSortOrder(order.sortKey)
        }
    }

    /**
     * 记录「关闭字幕」长期偏好（长期偏好，进页即按它把引擎的字幕轨关掉）。
     *
     * 只记立场、不记具体轨道：引擎侧动作由 UI 当场驱动（与清晰度同形——动作当场生效 + 立场落库），
     * 这里负责写库；进页时由 [PlayerUiState.subtitlesDisabled] 投影回读到引擎，重启后仍是「字幕关」。
     */
    fun setSubtitlesDisabled(disabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSubtitlesDisabled(disabled)
        }
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
                _events.send(PlayerUiEvent.ShowSnackbar(messageRes = R.string.feature_subject_player_login_required))
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
                            messageRes =
                                if (targetType == CollectionType.DOING) {
                                    R.string.feature_subject_player_followed_doing
                                } else {
                                    R.string.feature_subject_player_follow_updated
                                },
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
        resolveJob?.cancel()
        if (currentTab.isDirect) {
            // 默认直链：只按分集**精确匹配**。找不到就如实报无片源，
            // 绝不再回退 queue[currentIndex]——那会把 A 集串成 B 集的画面。
            val matchingEntry =
                state.queue.find {
                    (it.episodeId > 0 && it.episodeId == state.episodeId) ||
                        (it.episodeSort >= 0f && it.episodeSort == state.episodeSort)
                }
            if (matchingEntry != null && matchingEntry.streamUrl.isNotBlank()) {
                val isNewEpisode = session.pendingResumeLookupForNewEpisode
                val resumeForNewEpisode = consumePendingResumePositionForNewEpisode(matchingEntry.streamUrl)
                _uiState.update {
                    it.copy(
                        streamUrl = matchingEntry.streamUrl,
                        requestHeaders = matchingEntry.requestHeaders,
                        playingSourceId = currentTab.id,
                        isResolvingSource = false,
                        error = null,
                        resumePositionMs =
                            if (isNewEpisode) {
                                resumeForNewEpisode ?: 0L
                            } else {
                                session.latestPositionMs.takeIf { pos ->
                                    pos > 0L
                                } ?: it.resumePositionMs
                            },
                    )
                }
            } else {
                val errorMessage =
                    when (currentTab.kind) {
                        PlayerSourceKind.TORRENT_STREAM -> "当前 BT 资源仅包含单集，请切换线路或重新选源"
                        PlayerSourceKind.LOCAL_FILE -> "当前离线缓存仅包含单集，请切换线路或重新选源"
                        else -> "直链列表中没有该分集，请切换到其他播放源"
                    }
                _uiState.update {
                    it.copy(
                        streamUrl = "",
                        requestHeaders = emptyMap(),
                        playingSourceId = null,
                        isResolvingSource = false,
                        error = errorMessage,
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
                        // 换集到此才拿到新分集的真实地址，续播点在此刻按新地址查表下发；同分集内换线路沿用当前进度
                        val isNewEpisode = session.pendingResumeLookupForNewEpisode
                        val resumeForNewEpisode = consumePendingResumePositionForNewEpisode(playable.url)
                        _uiState.update {
                            it.copy(
                                streamUrl = playable.url,
                                requestHeaders = playable.headers,
                                playingSourceId = currentTab.id,
                                matchedVodName = playable.matchedVodName,
                                isResolvingSource = false,
                                resolveAttempt = 0,
                                resolveAttemptTotal = 0,
                                error = null,
                                resumePositionMs =
                                    if (isNewEpisode) {
                                        resumeForNewEpisode ?: 0L
                                    } else {
                                        session.latestPositionMs.takeIf { pos -> pos > 0L } ?: it.resumePositionMs
                                    },
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
                        reportResolveFailure("$reason，可尝试切换其他播放源")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportResolveFailure("解析失败，可尝试切换其他播放源")
                }
            }
    }

    /** 切换播放结束自动连播（长期偏好：只写设置仓库，由快照投影回读） */
    fun toggleAutoNext() {
        val targetEnabled = !_uiState.value.autoNextEnabled
        viewModelScope.launch {
            settingsRepository.setAutoNextEnabled(targetEnabled)
        }
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

    /**
     * 解析失败的诚实收敛。
     *
     * 切线路时旧线路的画面是被**故意保留**在播的（避免换源瞬间黑屏），所以解析失败不能直接落全屏错误态——
     * 那会把仍在正常播放的画面伪装成播放失败，而面板又会把解析失败的那条线标成「正在使用当前线路」。
     * 这里改为：失败先归因到当前**选中**源（它确实没解析出地址），随后把选中项回退到**正在播**的那条线，
     * 并用提示告知原因；只有当前根本没有在播地址时，才落错误态。
     */
    private suspend fun reportResolveFailure(message: String) {
        failureStore?.markFailed("", message, currentSourceId())
        val state = _uiState.value
        val playingIndex = state.playingSourceId?.let { id -> state.sources.indexOfFirst { it.id == id } }
        if (playingIndex != null && playingIndex >= 0) {
            session.selectedSourceId = state.sources[playingIndex].id
            _uiState.update {
                it.copy(
                    selectedSourceIndex = playingIndex,
                    matchedVodName = null,
                    isResolvingSource = false,
                    resolveAttempt = 0,
                    resolveAttemptTotal = 0,
                )
            }
            // 换线失败后用户的下一个动作几乎必然是「换一条线」：把面板入口做成这条提示的动作按钮。
            // 面板仍然是「选中即收起」语义，但重试路径从 2 击（点线路入口 + 点线路）降到 1 击。
            val canSwitch = state.sources.size > 1
            _events.send(
                PlayerUiEvent.ShowSnackbar(
                    message = message,
                    actionRes = if (canSwitch) R.string.feature_subject_player_switch_source else 0,
                    action = PlayerUiEvent.PlayerSnackbarAction.OPEN_SOURCES.takeIf { canSwitch },
                ),
            )
        } else {
            _uiState.update {
                it.copy(
                    matchedVodName = null,
                    isResolvingSource = false,
                    resolveAttempt = 0,
                    resolveAttemptTotal = 0,
                    error = message,
                )
            }
        }
    }

    /** 播放成功建立（STATE_READY）：清除该地址的失败标记，并把**在播源**记为“上次可用源” */
    fun onPlaybackReady() {
        val state = _uiState.value
        failureStore?.markPlayable(state.streamUrl, state.playingSourceId)
        val id = state.playingSourceId ?: return
        if (id == session.lastSavedSourceId) return
        session.lastSavedSourceId = id
        viewModelScope.launch { settingsRepository.setLastPlaybackSourceId(id) }
    }

    fun onPlaybackError(message: String) {
        val state = _uiState.value
        _uiState.update { it.copy(error = message) }
        failureStore?.markFailed(state.streamUrl, message, state.playingSourceId)
    }

    /**
     * 当前**选中**源的标识；拿不到就返回 null（计数只记到能认出来的源上）。
     *
     * 只用于"用户选了这个源，但它没解析出地址"这类归因；引擎回调（Ready/Error）必须用
     * [PlayerUiState.playingSourceId]，否则切源解析窗口内会把旧流的播报记到新源头上。
     */
    private fun currentSourceId(): String? = _uiState.value.let { state -> state.sources.getOrNull(state.selectedSourceIndex)?.id }

    override fun onCleared() {
        torrentStreamEngine?.releasePlayerSession()
    }
}

internal fun resolvePlayerSourceKind(url: String): PlayerSourceKind =
    when {
        url.startsWith("file://") -> PlayerSourceKind.LOCAL_FILE
        url.contains("127.0.0.1") || url.contains("localhost") -> PlayerSourceKind.TORRENT_STREAM
        else -> PlayerSourceKind.DIRECT
    }
