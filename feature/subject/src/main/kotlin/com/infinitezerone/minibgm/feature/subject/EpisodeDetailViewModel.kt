package com.infinitezerone.minibgm.feature.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.data.playback.PlaybackFailureStore
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.designsystem.component.bbcode.BgmBbCodeParser
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.CommunityLikeTarget
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.feature.subject.components.isEpisodeWatched
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 单集详情页 UI 状态 */
data class EpisodeDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val episode: Episode? = null,
    val isWatched: Boolean = false,
    val collection: UserCollection? = null,
    val allEpisodes: List<Episode> = emptyList(),
    val comments: List<EpisodeComment> = emptyList(),
    val isCommentsLoading: Boolean = false,
    val showLoginPromptDialog: Boolean = false,
    val error: String? = null,
    /** 吐槽短评加载错误信息（null = 无错误）；与 [error]（全局错误）独立 */
    val commentsError: String? = null,
    /** 当前登录用户 id（null = 未登录），用于判定吐槽表态是否为己方 */
    val currentUserId: Long? = null,
    val subject: Subject? = null,
    val playbackRules: List<PlaybackSourceRule> = emptyList(),
    val playlists: List<PlaybackPlaylist> = emptyList(),
    /** 近期播放失败归因：key = 播放地址，value = 可读原因（来源显示"打不开"） */
    val failedSourceReasons: Map<String, String> = emptyMap(),
)

/** 单集详情页一次性单发事件 */
sealed interface EpisodeDetailUiEvent {
    data class ShowSnackbar(
        val message: String,
    ) : EpisodeDetailUiEvent

    /** 把找源请求交接给 AI 助手会话（[prefillPrompt] 即助手首条提问） */
    data class OpenSourceSearch(
        val prefillPrompt: String,
    ) : EpisodeDetailUiEvent
}

/** 分集/收藏侧仓库流投影的中间态（私有于 [EpisodeDetailViewModel]） */
private data class EpisodeData(
    val episode: Episode?,
    val isWatched: Boolean,
    val collection: UserCollection?,
    val allEpisodes: List<Episode>,
)

/** 条目详情与登录用户投影的中间态（私有于 [EpisodeDetailViewModel]） */
private data class IdentityData(
    val subject: Subject?,
    val currentUserId: Long?,
)

/** 播放规则/片单/失败归因投影的中间态（私有于 [EpisodeDetailViewModel]） */
private data class PlaybackData(
    val playbackRules: List<PlaybackSourceRule>,
    val playlists: List<PlaybackPlaylist>,
    val failedSourceReasons: Map<String, String>,
)

/**
 * 评论刷新会话的落位状态：由刷新链与表态命令共同写入的私有会话容器
 * （同 [SeasonalGuideViewModel] 的分页游标先例：顺序过程私有容器，对外仍只经 [EpisodeDetailViewModel.uiState] 暴露）。
 * [hasSettled] 表示首刷已落定（成功或失败都算），用于把 isLoading 收敛为投影派生值。
 */
private data class CommentsUi(
    val comments: List<EpisodeComment> = emptyList(),
    val isCommentsLoading: Boolean = false,
    val commentsError: String? = null,
    val isRefreshing: Boolean = false,
    val hasSettled: Boolean = false,
)

/**
 * 评论刷新意图：flatMapLatest 的换挡判据。
 * 兜底拉取的判据（分集/条目是否未命中）在发意图时由投影快照判定并固化，
 * 内层会话只按意图执行，不再读可变状态。
 */
private data class CommentsRefreshIntent(
    val isUserPullToRefresh: Boolean,
    val shouldFetchEpisodes: Boolean,
    val shouldFetchSubject: Boolean,
)

/** 会话侧提示位（评论状态 + 登录提示 + 命令层错误位）的合流中间态 */
private data class SessionData(
    val comments: CommentsUi,
    val showLoginPromptDialog: Boolean,
    val actionError: String?,
)

/**
 * 单集详情 ViewModel —— 响应式 UDF，规范见 [SeasonalGuideViewModel] 顶部五条，逐条对齐：
 * 1. 对外只读 [uiState]；状态 = combine(仓库流, 会话/意图流) 的投影 + `stateIn(Eagerly)`。
 *    缓存预热值（getCachedSubject / getCachedEpisodes）作为投影基线并入各路流：
 *    流未命中（null / 空列表）时回落到预热值，与旧"预热初值 + 流覆盖"语义等价。
 * 2. 可变状态只剩用户意图与提示位：评论刷新改为"刷新意图 MutableSharedFlow →
 *    flatMapLatest(commentsSession)"换挡，新意图自动取消在途会话，无 refreshJob。
 * 3. 写后读：打卡 / 看到本集只写仓，经 getCollectionStream 回读（仓库本地优先写库后流自重发，
 *    单一事实源）。旧实现的手写乐观 collection/isWatched 与流投影是同一份数据的双写，
 *    属规范第 3 条明确禁止的"第二事实源"，故收敛为仓库流投影；UI 即时反馈由本地优先写库
 *    的流回读给出，失败时流保持原值即自然回滚，仅需以 actionError 提示。
 * 4. 一次性事件（Snackbar / 交接助手）走 Channel(BUFFERED) + receiveAsFlow()，与状态流隔离。
 * 5. 局部会话态保留为独立意图流/私有容器：登录提示、命令层错误位、评论刷新落位。
 *    规范第 5 条：命令密集型乐观会话，数据侧仍投影化——吐槽表态保留显式命令层（本地乐观
 *    回显 + 失败按原列表回滚），因为 comments 是一次性拉取结果而非仓库流，乐观值写回私有
 *    commentsUi 会话容器，不与任何仓库流投影双写。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeDetailViewModel(
    val subjectId: Long,
    val episodeId: Long,
    private val subjectRepository: SubjectRepository,
    private val collectionRepository: CollectionRepository,
    private val communityRepository: CommunityRepository,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository? = null,
    private val failureStore: PlaybackFailureStore? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    // ── 缓存预热基线：投影在各路仓库流未命中时的回落值，保证进页首帧即有数据 ──
    private val initialSubject = subjectRepository.getCachedSubject(subjectId)
    private val initialEpisodes = subjectRepository.getCachedEpisodes(subjectId).orEmpty()
    private val initialEpisode = initialEpisodes.firstOrNull { it.id == episodeId }

    // ── 用户意图与提示位：本类仅有的可变状态（规范第 5 条局部会话态）──
    private val loginPromptVisible = MutableStateFlow(false)

    /** 命令层错误位（打卡/看到本集失败提示）；刷新开始时清空，与旧 refresh 语义一致 */
    private val actionError = MutableStateFlow<String?>(null)

    /** 评论刷新意图源：replay=1 + DROP_OLDEST，漏掉旧意图即"最新意图赢"，等价旧 refreshJob 取消 */
    private val commentsRefreshIntents =
        MutableSharedFlow<CommentsRefreshIntent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 评论刷新会话与表态命令共用的落位容器（见 [CommentsUi]） */
    private val commentsUi = MutableStateFlow(CommentsUi())

    private val isLoggedIn =
        authRepository.isLoggedIn
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * 对外只读投影：任意上游变化即产出新快照。
     *
     * Eagerly 而非 WhileSubscribed：投影必须在无人订阅时也保持最新（进页即取数的原语义），
     * 否则单测读到的永远是初值；上游都是内存流与 Room 流，常驻收集的开销可忽略。
     */
    val uiState: StateFlow<EpisodeDetailUiState> =
        combine(
            combine(
                subjectRepository.getEpisodesStream(subjectId),
                collectionRepository.getCollectionStream(subjectId),
            ) { episodes, collection ->
                val streamEpisode = episodes.firstOrNull { it.id == episodeId }
                EpisodeData(
                    // 流未命中回落预热值；流命中后以流为准
                    episode = streamEpisode ?: initialEpisode,
                    isWatched = streamEpisode?.let { isEpisodeWatched(it, collection?.epStatus ?: 0) } ?: false,
                    collection = collection,
                    allEpisodes = if (episodes.isNotEmpty()) episodes else initialEpisodes,
                )
            },
            combine(
                subjectRepository.getSubjectStream(subjectId),
                authRepository.activeUserId,
            ) { subject, userId ->
                IdentityData(subject = subject ?: initialSubject, currentUserId = userId)
            },
            combine(
                settingsRepository?.playbackRules ?: flowOf(emptyList()),
                settingsRepository?.playlists ?: flowOf(emptyList()),
                failureStore?.recentFailures ?: flowOf(emptyMap()),
            ) { rules, playlists, failures ->
                PlaybackData(playbackRules = rules, playlists = playlists, failedSourceReasons = failures)
            },
            combine(commentsUi, loginPromptVisible, actionError) { comments, prompt, error ->
                SessionData(comments = comments, showLoginPromptDialog = prompt, actionError = error)
            },
        ) { episodeData, identity, playback, session ->
            EpisodeDetailUiState(
                // 分集就绪即不在加载；缓存全未命中时由首刷落定收口（与旧"settle 置 isLoading=false"等价）
                isLoading = episodeData.episode == null && !session.comments.hasSettled,
                isRefreshing = session.comments.isRefreshing,
                episode = episodeData.episode,
                isWatched = episodeData.isWatched,
                collection = episodeData.collection,
                allEpisodes = episodeData.allEpisodes,
                comments = session.comments.comments,
                isCommentsLoading = session.comments.isCommentsLoading,
                showLoginPromptDialog = session.showLoginPromptDialog,
                error = session.actionError,
                commentsError = session.comments.commentsError,
                currentUserId = identity.currentUserId,
                subject = identity.subject,
                playbackRules = playback.playbackRules,
                playlists = playback.playlists,
                failedSourceReasons = playback.failedSourceReasons,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            // 预热初值：与流投影的首个快照一致（缓存命中时 episode/subject/allEpisodes 同源）
            EpisodeDetailUiState(
                isLoading = initialEpisode == null,
                episode = initialEpisode,
                subject = initialSubject,
                allEpisodes = initialEpisodes,
            ),
        )

    private val _events = Channel<EpisodeDetailUiEvent>(Channel.BUFFERED)
    val events: Flow<EpisodeDetailUiEvent> = _events.receiveAsFlow()

    init {
        // 评论刷新链：意图一变整条取消换挡（flatMapLatest），旧短评响应不可能后到覆盖新数据
        commentsRefreshIntents
            .flatMapLatest { intent -> commentsSession(intent) }
            .launchIn(viewModelScope)
        // 进页首刷：兜底判据按预热基线固化（缓存命中则不触发网络拉取）
        commentsRefreshIntents.tryEmit(
            CommentsRefreshIntent(
                isUserPullToRefresh = false,
                shouldFetchEpisodes = initialEpisode == null,
                shouldFetchSubject = initialSubject == null,
            ),
        )
    }

    /**
     * 一次评论刷新会话（flatMapLatest 的内层流）：进入即"加载中"，结束时一次性落定。
     * 分集/条目元数据兜底只写仓（fetchEpisodes / fetchSubjectDetail），结果经仓库流投影回读，
     * 不在此处手写状态——写后读单一事实源。BBCode 预解析仍在会话流内、经 [defaultDispatcher] 执行。
     */
    private fun commentsSession(intent: CommentsRefreshIntent): Flow<Unit> =
        flow {
            commentsUi.update {
                it.copy(
                    isCommentsLoading = true,
                    isRefreshing = intent.isUserPullToRefresh,
                    commentsError = null,
                )
            }

            if (intent.shouldFetchEpisodes) {
                subjectRepository.fetchEpisodes(subjectId)
            }
            if (intent.shouldFetchSubject) {
                subjectRepository.fetchSubjectDetail(subjectId)
            }

            val commentsResult = communityRepository.getEpisodeComments(episodeId)
            if (commentsResult is AppResult.Success) {
                withContext(defaultDispatcher) {
                    for (comment in commentsResult.data) {
                        BgmBbCodeParser.parseBlocks(comment.content)
                    }
                }
            }
            commentsUi.update { current ->
                when (commentsResult) {
                    is AppResult.Success ->
                        current.copy(
                            comments = commentsResult.data,
                            isCommentsLoading = false,
                            isRefreshing = false,
                            commentsError = null,
                            hasSettled = true,
                        )
                    is AppResult.Error ->
                        current.copy(
                            isCommentsLoading = false,
                            isRefreshing = false,
                            commentsError = if (current.comments.isEmpty()) commentsResult.message else null,
                            hasSettled = true,
                        )
                    is AppResult.Loading -> current
                }
            }
        }

    /**
     * 把找源请求交接给 AI 助手会话：本页面不做任何检索与抓取，只生成显式触发用的提问文案。
     */
    fun requestSourceSearch() {
        val title =
            uiState.value.subject
                ?.displayName
                ?.ifBlank { "本条目" } ?: "本条目"
        val episode = uiState.value.episode
        val target =
            if (episode == null) {
                "《$title》"
            } else {
                "《$title》 ${episode.guideLabel}"
            }
        viewModelScope.launch {
            _events.send(EpisodeDetailUiEvent.OpenSourceSearch("帮我找${target}的可播放资源，直接给我能播放的地址和集数列表（Bangumi 条目号 $subjectId）"))
        }
    }

    /** 为当前分集组装播放器路由（组装逻辑见 [buildEpisodePlayerRoute]） */
    fun buildPlayerRoute(episode: Episode): PlayerRoute =
        buildEpisodePlayerRoute(
            subjectId = subjectId,
            subjectName =
                uiState.value.subject
                    ?.displayName
                    .orEmpty(),
            episode = episode,
            playlists = uiState.value.playlists,
        )

    /**
     * 刷新单集吐槽短评与分集元数据：发一条刷新意图即可，换挡与在途取消交给 flatMapLatest。
     * 判据（是否需要兜底拉取）读当前投影快照，与 UI 看到的完全一致。
     */
    fun refresh(isUserPullToRefresh: Boolean = false) {
        actionError.value = null
        val current = uiState.value
        commentsRefreshIntents.tryEmit(
            CommentsRefreshIntent(
                isUserPullToRefresh = isUserPullToRefresh,
                shouldFetchEpisodes = current.episode == null || isUserPullToRefresh,
                shouldFetchSubject = current.subject == null || isUserPullToRefresh,
            ),
        )
    }

    /** 重新加载单集吐槽短评（在吐槽加载失败卡片中触发） */
    fun retryLoadComments() {
        refresh(isUserPullToRefresh = false)
    }

    /**
     * 切换当前分集观看打卡状态（未登录时拦截弹窗）。
     *
     * 规范第 5 条：命令密集型乐观会话，数据侧仍投影化——打卡是显式命令层，但 collection/isWatched
     * 由 getCollectionStream 投影派生，旧实现的手写乐观 copy 与回滚是同一份数据的第二事实源
     * （双写冲突），故收敛为"写仓 + 流回读"：仓库本地优先写库后流自重发，UI 即时反馈成立；
     * 失败时流保持原值即天然回滚，仅需置 [actionError] 提示。
     */
    fun toggleWatched(
        episode: Episode,
        isWatched: Boolean,
    ) {
        if (!isLoggedIn.value) {
            loginPromptVisible.value = true
            return
        }
        viewModelScope.launch {
            val result =
                withContext(NonCancellable) {
                    collectionRepository.updateEpisodeStatus(
                        subjectId = subjectId,
                        episodeId = episode.id,
                        isWatched = isWatched,
                        epNumber = episode.episodeInt,
                    )
                }
            result.onError { _, message ->
                actionError.value = message
            }
        }
    }

    /**
     * 批量标记观看进度至目标话数（看到本集，未登录时拦截弹窗）。
     *
     * 同 [toggleWatched]：写仓后经仓库流回读，无手写乐观收藏（双写冲突，收敛为投影）。
     */
    fun markWatchedUpTo(targetEpisode: Episode) {
        if (!isLoggedIn.value) {
            loginPromptVisible.value = true
            return
        }
        val targetEpNumber = targetEpisode.episodeInt
        val targetEpisodeIds =
            uiState.value.allEpisodes
                .filter { ep -> ep.episodeInt in 1..targetEpNumber }
                .map { it.id }
        viewModelScope.launch {
            val result =
                withContext(NonCancellable) {
                    collectionRepository.markEpisodesWatchedUpTo(
                        subjectId = subjectId,
                        epNumber = targetEpNumber,
                        episodeIds = targetEpisodeIds,
                    )
                }
            result.onError { _, message ->
                actionError.value = message
            }
        }
    }

    /**
     * 切换自己对某条吐槽某条表情表态的状态：
     * 未登录提示登录；已在该类型表态过 → 取消；否则以该表情类型加入。
     *
     * 规范第 5 条：命令密集型乐观会话，数据侧仍投影化——comments 是一次性拉取结果而非仓库流，
     * 保留本地乐观回显（写回 commentsUi 会话容器，经状态流声明式下发），提交失败按原列表回滚；
     * 成功不整流刷新。
     */
    fun toggleCommentReaction(
        comment: EpisodeComment,
        reaction: CommentReaction,
    ) {
        val userId = uiState.value.currentUserId
        if (userId == null) {
            _events.trySend(EpisodeDetailUiEvent.ShowSnackbar("请先登录后再表态"))
            return
        }
        val reacted = reaction.users.any { it.id == userId }
        val previousComments = commentsUi.value.comments
        commentsUi.update { state ->
            state.copy(
                comments =
                    state.comments.map { c ->
                        if (c.id == comment.id) {
                            c.copy(reactions = adjustReactions(c.reactions, reaction.value, userId, removing = reacted))
                        } else {
                            c
                        }
                    },
            )
        }
        viewModelScope.launch {
            val result =
                withContext(NonCancellable) {
                    if (reacted) {
                        communityRepository.removeLike(CommunityLikeTarget.EPISODE_COMMENT, comment.id)
                    } else {
                        communityRepository.setLike(CommunityLikeTarget.EPISODE_COMMENT, comment.id, reaction.value)
                    }
                }
            when (result) {
                is AppResult.Success -> {
                    _events.trySend(EpisodeDetailUiEvent.ShowSnackbar(if (reacted) "已取消表态" else "已表态"))
                }
                is AppResult.Error -> {
                    commentsUi.update { it.copy(comments = previousComments) }
                    _events.trySend(EpisodeDetailUiEvent.ShowSnackbar(result.message.ifBlank { "表态失败" }))
                }
                is AppResult.Loading -> Unit
            }
        }
    }

    fun dismissLoginPrompt() {
        loginPromptVisible.value = false
    }
}
