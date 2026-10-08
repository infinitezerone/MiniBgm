package com.infinitezerone.minibgm.feature.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.data.playback.PlaybackFailureStore
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.model.CharacterDetail
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.PersonDetail
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.RelatedWork
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectComment
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectRelation
import com.infinitezerone.minibgm.core.model.SubjectTopic
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.model.aggregateBySubject
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.feature.subject.components.isEpisodeNextToWatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 条目详情页单次 UI 事件（Snackbar 反馈、撤销动作与状态提示） */
sealed interface SubjectDetailUiEvent {
    data class EpisodeMarked(
        val epNumber: Int,
        val episodeId: Long,
        val previousEpStatus: Int,
        val previousType: Int,
        val episodeType: Int = 0,
    ) : SubjectDetailUiEvent

    data class BatchMarked(
        val targetEpNumber: Int,
        val episodeIds: List<Long>,
        val previousEpStatus: Int,
        val previousType: Int,
    ) : SubjectDetailUiEvent

    data class ShowMessage(
        val message: String,
    ) : SubjectDetailUiEvent

    /** 把找源请求交接给 AI 助手会话（[prefillPrompt] 即助手首条提问，结果为可播放清单） */
    data class OpenSourceSearch(
        val prefillPrompt: String,
    ) : SubjectDetailUiEvent
}

/** 条目详情页二级分栏枚举 */
enum class SubjectDetailTab(
    val label: String,
) {
    OVERVIEW("概览"),
    EPISODES("分集"),
    COMMUNITY("讨论"),
}

/** 条目详情页 UI 状态 */
data class SubjectDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isEpisodesLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val selectedTab: SubjectDetailTab = SubjectDetailTab.OVERVIEW,
    val isEpisodeGridView: Boolean = true,
    /** 分集排序方向：false = 最早在前，true = 最新在前 */
    val episodeSortDescending: Boolean = false,
    /** 是否还有未加载分集（滚动加载更多） */
    val hasMoreEpisodes: Boolean = false,
    val isLoadingMoreEpisodes: Boolean = false,
    val selectedEpisodeForDetail: Episode? = null,
    val activeCharacter: SubjectCharacter? = null,
    val activePerson: SubjectPerson? = null,
    val showCollectionSheet: Boolean = false,
    val showLoginPromptDialog: Boolean = false,
    val error: String? = null,
    val episodesError: String? = null,
    val subject: Subject? = null,
    val episodes: List<Episode> = emptyList(),
    val collection: UserCollection? = null,
    val characters: List<SubjectCharacter> = emptyList(),
    val persons: List<SubjectPerson> = emptyList(),
    val relations: List<SubjectRelation> = emptyList(),
    val isDetailsLoading: Boolean = false,
    val subjectComments: List<SubjectComment> = emptyList(),
    val subjectCommentTotal: Int = 0,
    val isLoadingMoreComments: Boolean = false,
    val hasMoreComments: Boolean = true,
    val isCommunityLoading: Boolean = false,
    val selectedCharacterDetail: CharacterDetail? = null,
    val selectedCharacterWorks: List<RelatedWork> = emptyList(),
    val selectedPersonDetail: PersonDetail? = null,
    val selectedPersonWorks: List<RelatedWork> = emptyList(),
    val isLoadingEntityDetail: Boolean = false,
    val subjectTopics: List<SubjectTopic> = emptyList(),
    val episodeComments: Map<Long, List<EpisodeComment>> = emptyMap(),
    val isEpisodeCommentsLoading: Boolean = false,
    val playbackRules: List<com.infinitezerone.minibgm.core.model.PlaybackSourceRule> = emptyList(),
    val playlists: List<com.infinitezerone.minibgm.core.model.PlaybackPlaylist> = emptyList(),
    /** 近期播放失败归因：key = 播放地址，value = 可读原因（来源显示"打不开"） */
    val failedSourceReasons: Map<String, String> = emptyMap(),
    val customFilterTags: List<String> = emptyList(),
)

/** 条目/分集/收藏仓库流投影的中间态（私有于 [SubjectDetailViewModel]） */
private data class SubjectCoreData(
    val subject: Subject?,
    val episodes: List<Episode>,
    val collection: UserCollection?,
)

/** 播放规则/片单/失败归因投影的中间态（私有于 [SubjectDetailViewModel]） */
private data class SubjectPlaybackData(
    val playbackRules: List<PlaybackSourceRule>,
    val playlists: List<PlaybackPlaylist>,
    val failedSourceReasons: Map<String, String>,
)

/** 单点回读收集器的仓库快照投影（私有于 [SubjectDetailViewModel]） */
private data class RepoSnapshot(
    val core: SubjectCoreData,
    val hasMoreEpisodes: Boolean,
    val isLoggedIn: Boolean,
    val playback: SubjectPlaybackData,
    val customFilterTags: List<String>,
)

/**
 * 条目详情 ViewModel —— **数据侧单点回读 + 显式命令层**（响应式 UDF 规范见
 * [com.infinitezerone.minibgm.feature.search.SeasonalGuideViewModel] 顶部五条，与同模块
 * [EpisodeDetailViewModel] 同型迁移）。本类状态密度高（首刷三要素 + 分集分页 + 两个懒加载 Tab +
 * 乐观收藏命令），迁移形态取舍如下：
 *
 * 1. 对外只读 [uiState]；仓库流（条目/分集/收藏/分页游标/登录态/播放规则/片单/失败归因）收敛为
 *    [RepoSnapshot] 单点投影，经 init 里**唯一**的 `_uiState.update` 收集点回读——替代旧实现
 *    5+ 路离散 collect，各字段合并规则与旧实现逐点等价（subject 只进不清、collection 流只覆盖
 *    type/epStatus 以保住命令层的乐观 rate/comment/tags）。
 * 2. 资料与社区两个懒加载 Tab 改为"意图 → flatMapLatest 换挡"：[loadDetailsTabIfNeeded] /
 *    [loadCommunityTabIfNeeded] 只发意图并同步置在途标记（替代旧 detailsJob?.isActive 判定），
 *    换挡与在途取消交给 flatMapLatest，无手动 Job；拉取结果是一次性网络数据而非仓库流，
 *    按规范第 5 条落回 [SubjectDetailUiState] 的会话字段。
 * 3. 收藏打卡 / 批量打卡 / 撤销保留为**显式命令层**（规范第 5 条）：乐观写 + 失败回滚 + undo
 *    快照回写全部原样保留，不改写为仓库流回读——undo 快照语义与仓库流时序强耦合；仓库流回读
 *    时按上述合并规则与乐观值共存，不形成双写冲突（乐观值在途期间流只覆盖 type/epStatus）。
 * 4. 一次性事件（Snackbar / 撤销交接 / 找源交接）走 Channel(BUFFERED) + receiveAsFlow()，
 *    与状态流隔离。
 * 5. [refresh] 是首刷三要素（条目 → 分集 → 收藏）的显式刷新命令：顺序取数 + 条件合并错误 +
 *    取消在途旧刷新，保留命令式实现；[nextEpisodeToWatch] / [buildPlayerRoute] 读 [uiState]
 *    投影快照，与 UI 所见一致。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubjectDetailViewModel(
    private val subjectRepository: SubjectRepository,
    private val subjectId: Long,
    private val collectionRepository: CollectionRepository,
    private val communityRepository: CommunityRepository,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository? = null,
    private val failureStore: PlaybackFailureStore? = null,
    private val searchRepository: SearchRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SubjectDetailUiState())
    val uiState: StateFlow<SubjectDetailUiState> = _uiState.asStateFlow()

    private val _uiEvents = Channel<SubjectDetailUiEvent>(Channel.BUFFERED)
    val uiEvents: Flow<SubjectDetailUiEvent> = _uiEvents.receiveAsFlow()

    /** 引导未登录用户登录 Bangumi 账号并提示 */
    fun promptLogin() {
        _uiState.update { it.copy(showLoginPromptDialog = true) }
        viewModelScope.launch {
            _uiEvents.send(SubjectDetailUiEvent.ShowMessage("请先登录 Bangumi 账号"))
        }
    }

    fun enableAiringReminder() {
        viewModelScope.launch {
            settingsRepository?.setAiringReminderEnabled(true)
        }
    }

    /**
     * 把找源请求交接给 AI 助手会话：本页面不做任何检索与抓取，只生成显式触发用的提问文案。
     * 能力边界见 ROADMAP 第 5 节——播放地址只能来自工具返回，模型不生成 URL。
     */
    fun requestSourceSearch(episode: Episode? = null) {
        val title =
            _uiState.value.subject
                ?.displayName
                ?.ifBlank { "本条目" } ?: "本条目"
        val target = if (episode == null) "《$title》" else "《$title》 ${episode.guideLabel}"
        val prompt = "帮我找${target}的可播放资源，直接给我能播放的地址和集数列表（Bangumi 条目号 $subjectId）"
        viewModelScope.launch {
            _uiEvents.send(SubjectDetailUiEvent.OpenSourceSearch(prompt))
        }
    }

    /** 计算「下一待看」分集：按打卡进度与续看位置推断，无匹配时回退第一集 */
    fun nextEpisodeToWatch(): Episode? {
        val state = _uiState.value
        return state.episodes.firstOrNull {
            isEpisodeNextToWatch(it, state.collection?.epStatus ?: 0, hasProgress = state.collection != null)
        } ?: state.episodes.firstOrNull()
    }

    /** 为指定分集组装播放器路由（组装逻辑见 [buildEpisodePlayerRoute]） */
    fun buildPlayerRoute(episode: Episode): PlayerRoute =
        buildEpisodePlayerRoute(
            subjectId = subjectId,
            subjectName =
                _uiState.value.subject
                    ?.displayName
                    .orEmpty(),
            episode = episode,
            playlists = _uiState.value.playlists,
        )

    private val isLoggedIn =
        authRepository.isLoggedIn
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 资料 Tab 拉取意图：true = 强制重取（flatMapLatest 的换挡判据） */
    private val detailsIntents =
        MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 社区 Tab 拉取意图：true = 强制重取（flatMapLatest 的换挡判据） */
    private val communityIntents =
        MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 在途标记：发意图时同步置位，会话落定/取消时清除（替代旧 detailsJob?.isActive 判定） */
    private var detailsInFlight = false
    private var communityInFlight = false

    init {
        // 数据侧单点回读：全部仓库流收敛为 RepoSnapshot 单点投影，唯一的 _uiState 写入点。
        // 合并规则与旧逐路 collect 逐点等价：subject 只进不清（流未命中保留旧值）、
        // collection 流只覆盖 type/epStatus（保住命令层的乐观 rate/comment/tags）。
        viewModelScope.launch {
            combine(
                combine(
                    subjectRepository.getSubjectStream(subjectId),
                    subjectRepository.getEpisodesStream(subjectId),
                    collectionRepository.getCollectionStream(subjectId),
                ) { subject, episodes, localCollection ->
                    SubjectCoreData(subject, episodes, localCollection)
                },
                subjectRepository.hasMoreEpisodesStream(subjectId),
                isLoggedIn,
                combine(
                    settingsRepository?.playbackRules ?: flowOf(emptyList()),
                    settingsRepository?.playlists ?: flowOf(emptyList()),
                    failureStore?.recentFailures ?: flowOf(emptyMap()),
                ) { rules, playlists, failures ->
                    SubjectPlaybackData(rules, playlists, failures)
                },
                searchRepository?.getCustomFilterTags() ?: flowOf(emptyList()),
            ) { core, hasMore, loggedIn, playback, customTags ->
                RepoSnapshot(core, hasMore, loggedIn, playback, customTags)
            }.collect { snapshot ->
                _uiState.update { state ->
                    val streamCollection = snapshot.core.collection
                    val mergedCollection =
                        if (streamCollection == null) {
                            null
                        } else {
                            state.collection?.copy(
                                type = streamCollection.type,
                                epStatus = streamCollection.epStatus,
                            ) ?: streamCollection
                        }
                    state.copy(
                        subject = snapshot.core.subject ?: state.subject,
                        episodes = snapshot.core.episodes,
                        collection = mergedCollection,
                        isLoggedIn = snapshot.isLoggedIn,
                        hasMoreEpisodes = snapshot.hasMoreEpisodes,
                        playbackRules = snapshot.playback.playbackRules,
                        playlists = snapshot.playback.playlists,
                        failedSourceReasons = snapshot.playback.failedSourceReasons,
                        customFilterTags = snapshot.customFilterTags,
                        isLoading = if (snapshot.core.subject != null || state.subject != null) false else state.isLoading,
                        isEpisodesLoading = if (snapshot.core.episodes.isNotEmpty()) false else state.isEpisodesLoading,
                        episodesError = if (snapshot.core.episodes.isNotEmpty()) null else state.episodesError,
                    )
                }
            }
        }
        // Tab 懒加载换挡链：意图一变整条取消（flatMapLatest），旧拉取响应不可能后到覆盖新数据
        detailsIntents
            .flatMapLatest(::detailsSession)
            .launchIn(viewModelScope)
        communityIntents
            .flatMapLatest(::communitySession)
            .launchIn(viewModelScope)
        // 拉取条目详情与章节（写入本地库）；错误仅转为文案，不中断流程
        refresh(isUserPullToRefresh = false)
    }

    /** 切换自定义常用筛选标签（来源 2：随看随加） */
    fun toggleCustomFilterTag(tag: String) {
        viewModelScope.launch {
            val current = _uiState.value.customFilterTags
            if (tag in current) {
                searchRepository?.removeCustomFilterTag(tag)
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage("已从常用筛选标签中移除「#$tag」"))
            } else {
                searchRepository?.addCustomFilterTag(tag)
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage("已将「#$tag」添加至常用筛选标签"))
            }
        }
    }

    private var detailsLoaded = false
    private var communityLoaded = false
    private var refreshJob: Job? = null

    /** 刷新/重新拉取条目、分集与收藏数据（首屏核心三要素） */
    fun refresh(isUserPullToRefresh: Boolean = false) {
        refreshJob?.cancel()
        detailsLoaded = false
        communityLoaded = false
        refreshJob =
            viewModelScope.launch {
                _uiState.update { current ->
                    current.copy(
                        isLoading = if (isUserPullToRefresh || current.subject == null) true else false,
                        isRefreshing = isUserPullToRefresh,
                        isEpisodesLoading = current.episodes.isEmpty(),
                        episodesError = null,
                        error = null,
                    )
                }

                try {
                    // 1. 条目基本信息
                    val subjectResult = subjectRepository.fetchSubjectDetail(subjectId)
                    subjectResult.onError { _, message ->
                        if (_uiState.value.subject == null) {
                            _uiState.update { it.copy(error = message) }
                        }
                    }
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            subject = (subjectResult as? AppResult.Success)?.data ?: current.subject,
                        )
                    }

                    // 2. 分集数据（本地已有且非用户显式下拉时优先命中缓存短路，不打扰网络）
                    val episodesResult =
                        if (isUserPullToRefresh || _uiState.value.episodes.isEmpty()) {
                            subjectRepository.loadEpisodes(subjectId, _uiState.value.episodeSortDescending)
                        } else {
                            subjectRepository.fetchEpisodes(subjectId)
                        }
                    episodesResult.onError { _, message ->
                        if (_uiState.value.episodes.isEmpty()) {
                            _uiState.update { it.copy(episodesError = message) }
                        }
                        if (_uiState.value.subject == null && _uiState.value.episodes.isEmpty()) {
                            _uiState.update { it.copy(error = message) }
                        }
                    }
                    _uiState.update { current ->
                        current.copy(
                            isEpisodesLoading = false,
                            episodesError =
                                if (episodesResult is AppResult.Success ||
                                    current.episodes.isNotEmpty()
                                ) {
                                    null
                                } else {
                                    current.episodesError
                                },
                        )
                    }

                    // 3. 收藏状态（本地优先，用户下拉时才强制向远端同步）
                    val collectionResult = collectionRepository.fetchCollection(subjectId, force = isUserPullToRefresh)
                    collectionResult.onError { _, message ->
                        if (_uiState.value.subject == null && _uiState.value.collection == null) {
                            _uiState.update { it.copy(error = message) }
                        }
                    }
                    _uiState.update { current ->
                        current.copy(
                            collection =
                                if (collectionResult is AppResult.Success) {
                                    collectionResult.data ?: current.collection
                                } else {
                                    current.collection
                                },
                        )
                    }
                } finally {
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isEpisodesLoading = false,
                        )
                    }
                }
            }
    }

    /** 重新拉取分集数据（在分集加载失败卡片中显式触发重试） */
    fun retryLoadEpisodes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isEpisodesLoading = true, episodesError = null) }
            val result = subjectRepository.loadEpisodes(subjectId, _uiState.value.episodeSortDescending)
            result.onError { _, message ->
                if (_uiState.value.episodes.isEmpty()) {
                    _uiState.update { it.copy(episodesError = message, isEpisodesLoading = false) }
                } else {
                    _uiState.update { it.copy(isEpisodesLoading = false) }
                }
            }
            if (result is AppResult.Success) {
                _uiState.update { it.copy(episodesError = null, isEpisodesLoading = false) }
            }
        }
    }

    /** 切换详情页二级 Tab，并自动按需加载对应数据 */
    fun selectTab(tab: SubjectDetailTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            SubjectDetailTab.OVERVIEW -> loadDetailsTabIfNeeded()
            SubjectDetailTab.EPISODES -> Unit
            SubjectDetailTab.COMMUNITY -> loadCommunityTabIfNeeded()
        }
    }

    /** 切换分集列表的宫格视图/详细列表视图 */
    fun setEpisodeGridView(isGrid: Boolean) {
        _uiState.update { it.copy(isEpisodeGridView = isGrid) }
    }

    /** 分集排序方向：切换后按新方向重载首屏（降序从最新一话开始） */
    fun setEpisodeSortDescending(descending: Boolean) {
        if (_uiState.value.episodeSortDescending == descending) return
        _uiState.update { it.copy(episodeSortDescending = descending, isEpisodesLoading = true, episodesError = null) }
        viewModelScope.launch {
            val result = subjectRepository.loadEpisodes(subjectId, descending)
            result.onError { _, message ->
                if (_uiState.value.episodes.isEmpty()) {
                    _uiState.update { it.copy(episodesError = message, isEpisodesLoading = false) }
                } else {
                    _uiState.update { it.copy(isEpisodesLoading = false) }
                }
            }
            if (result is AppResult.Success) {
                _uiState.update { it.copy(episodesError = null, isEpisodesLoading = false) }
            }
        }
    }

    /** 滚动到底部时续拉下一屏分集 */
    fun loadMoreEpisodes() {
        val state = _uiState.value
        if (!state.hasMoreEpisodes || state.isLoadingMoreEpisodes) return
        _uiState.update { it.copy(isLoadingMoreEpisodes = true) }
        viewModelScope.launch {
            subjectRepository.loadMoreEpisodes(subjectId, state.episodeSortDescending)
            _uiState.update { it.copy(isLoadingMoreEpisodes = false) }
        }
    }

    /** 打开分集详情底栏 */
    fun openEpisodeDetail(episode: Episode) {
        _uiState.update { it.copy(selectedEpisodeForDetail = episode) }
    }

    /** 关闭分集详情底栏 */
    fun dismissEpisodeDetail() {
        _uiState.update { it.copy(selectedEpisodeForDetail = null) }
    }

    /** 控制收藏状态底栏显隐（未登录时拦截弹窗） */
    fun setCollectionSheetVisible(visible: Boolean) {
        if (visible && !isLoggedIn.value) {
            _uiState.update { it.copy(showLoginPromptDialog = true) }
            viewModelScope.launch {
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage("请先登录 Bangumi 账号"))
            }
            return
        }
        _uiState.update { it.copy(showCollectionSheet = visible) }
    }

    /** 点击并打开角色详情底栏 */
    fun openCharacterDetail(characterId: Long) {
        val character =
            _uiState.value.characters.firstOrNull { it.id == characterId }
                ?: SubjectCharacter(id = characterId, name = "")
        _uiState.update {
            it.copy(
                activeCharacter = character,
                activePerson = null,
                selectedEpisodeForDetail = null,
            )
        }
        loadCharacterDetail(characterId)
    }

    /** 点击并打开人物/主创详情底栏 */
    fun openPersonDetail(personId: Long) {
        val person =
            _uiState.value.persons.firstOrNull { it.id == personId }
                ?: SubjectPerson(id = personId, name = "")
        _uiState.update {
            it.copy(
                activePerson = person,
                activeCharacter = null,
                selectedEpisodeForDetail = null,
            )
        }
        loadPersonDetail(personId)
    }

    /** 关闭角色或人物详情底栏并清空所有临时选中实体状态 */
    fun dismissEntityDetail() {
        _uiState.update {
            it.copy(
                activeCharacter = null,
                activePerson = null,
                selectedCharacterDetail = null,
                selectedCharacterWorks = emptyList(),
                selectedPersonDetail = null,
                selectedPersonWorks = emptyList(),
                isLoadingEntityDetail = false,
            )
        }
    }

    /**
     * 按需懒加载资料与演职员 Tab 数据（角色 -> 人员 -> 关联作品）。
     *
     * 意图换挡（规范第 2 条）：已加载或在途时跳过；force 触发整条换挡重取，在途旧会话由
     * flatMapLatest 取消——无手动 Job。仅在用户主动切到「资料与演职员」Tab 时触发，
     * 已加载过则不再重复请求。
     */
    fun loadDetailsTabIfNeeded(force: Boolean = false) {
        if (!force && (detailsLoaded || detailsInFlight)) return
        detailsInFlight = true
        detailsIntents.tryEmit(force)
    }

    /** 资料 Tab 拉取会话（flatMapLatest 的内层流）：进入即"加载中"，任一成功即落定加载标记 */
    private fun detailsSession(force: Boolean): Flow<Unit> =
        flow {
            try {
                _uiState.update { it.copy(isDetailsLoading = true) }
                val charactersResult = subjectRepository.fetchCharacters(subjectId)
                val personsResult = subjectRepository.fetchPersons(subjectId)
                val relationsResult = subjectRepository.fetchRelations(subjectId)

                val hasAnySuccess =
                    charactersResult is AppResult.Success ||
                        personsResult is AppResult.Success ||
                        relationsResult is AppResult.Success

                _uiState.update { current ->
                    current.copy(
                        isDetailsLoading = false,
                        characters = (charactersResult as? AppResult.Success)?.data ?: current.characters,
                        persons = (personsResult as? AppResult.Success)?.data ?: current.persons,
                        relations = (relationsResult as? AppResult.Success)?.data ?: current.relations,
                    )
                }
                if (hasAnySuccess) {
                    detailsLoaded = true
                }
            } finally {
                detailsInFlight = false
            }
            emit(Unit)
        }

    /**
     * 按需懒加载社区吐槽与讨论版 Tab 数据（短评 -> 讨论）。
     *
     * 意图换挡（规范第 2 条）：已加载或在途时跳过；force 触发整条换挡重取，在途旧会话由
     * flatMapLatest 取消——无手动 Job。仅在用户主动切到「社区吐槽」Tab 时触发，
     * 已加载过则不再重复请求；全部失败不落定加载标记，允许二次重试。
     */
    fun loadCommunityTabIfNeeded(force: Boolean = false) {
        if (!force && (communityLoaded || communityInFlight)) return
        communityInFlight = true
        communityIntents.tryEmit(force)
    }

    /** 社区 Tab 拉取会话（flatMapLatest 的内层流）：进入即"加载中"，短评或讨论任一成功即落定 */
    private fun communitySession(force: Boolean): Flow<Unit> =
        flow {
            try {
                _uiState.update { it.copy(isCommunityLoading = true) }
                val subjectCommentsResult = communityRepository.getSubjectComments(subjectId, limit = 15)
                val subjectTopicsResult = communityRepository.getSubjectTopics(subjectId, limit = 5)

                val commentsSuccess = subjectCommentsResult is AppResult.Success
                val topicsSuccess = subjectTopicsResult is AppResult.Success

                val commentsPage = (subjectCommentsResult as? AppResult.Success)?.data
                val topics = (subjectTopicsResult as? AppResult.Success)?.data.orEmpty()

                _uiState.update { current ->
                    current.copy(
                        isCommunityLoading = false,
                        subjectComments = commentsPage?.data ?: current.subjectComments,
                        subjectCommentTotal = commentsPage?.total ?: current.subjectCommentTotal,
                        hasMoreComments = (commentsPage?.data?.size ?: 0) < (commentsPage?.total ?: 0),
                        subjectTopics = if (topics.isNotEmpty()) topics else current.subjectTopics,
                    )
                }
                if (commentsSuccess || topicsSuccess) {
                    communityLoaded = true
                }
            } finally {
                communityInFlight = false
            }
            emit(Unit)
        }

    /**
     * 更新条目收藏状态（想看/在看/看过等，支持 0ms 本地即时乐观更新与失败回滚，未登录时拦截弹窗）。
     *
     * 显式命令层（规范第 5 条）：乐观写 → 写仓 → 失败回滚，保留命令式实现，不改写为仓库流回读
     * ——undo 快照语义与仓库流时序强耦合；仓库流回读只覆盖 type/epStatus，不冲掉乐观中的
     * rate/comment/tags（见 init 的合并规则）。
     */
    fun updateCollectionStatus(
        type: CollectionType,
        rate: Int? = null,
        comment: String? = null,
        private: Boolean = false,
        epStatus: Int? = null,
        tags: List<String>? = null,
    ) {
        if (!isLoggedIn.value) {
            _uiState.update { it.copy(showCollectionSheet = false, showLoginPromptDialog = true) }
            viewModelScope.launch {
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage("请先登录 Bangumi 账号"))
            }
            return
        }

        val previousCollection = _uiState.value.collection
        val resolvedSubjectType = _uiState.value.subject?.type ?: previousCollection?.subjectType ?: 2
        // 1. 本地立即乐观更新 UI 状态中的 collection
        val targetEpStatus = epStatus ?: previousCollection?.epStatus ?: 0
        val targetTags = tags ?: previousCollection?.tags.orEmpty()
        val optimisticCollection =
            previousCollection?.copy(
                type = type.value,
                rate = rate ?: previousCollection.rate,
                comment = comment ?: previousCollection.comment,
                epStatus = targetEpStatus,
                tags = targetTags,
                subjectType = resolvedSubjectType,
            ) ?: UserCollection(
                userId = 0L,
                subjectId = subjectId,
                subjectType = resolvedSubjectType,
                rate = rate ?: 0,
                type = type.value,
                comment = comment.orEmpty(),
                tags = targetTags,
                epStatus = targetEpStatus,
                volStatus = 0,
                updatedAt = "",
            )
        _uiState.update { it.copy(collection = optimisticCollection, error = null) }

        viewModelScope.launch {
            val result =
                collectionRepository.updateCollectionStatus(
                    subjectId = subjectId,
                    type = type,
                    rate = rate,
                    comment = comment,
                    private = private,
                    epStatus = epStatus,
                    tags = tags,
                    subjectType = resolvedSubjectType,
                )
            result.onError { _, message ->
                // 2. 失败回滚为原状态并提示错误
                _uiState.update { it.copy(collection = previousCollection, error = message) }
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage(message.ifBlank { "更新收藏状态失败，请确认是否已登录账号" }))
            }
        }
    }

    /** +1 话快捷打卡当前待看的下一集，并发送撤销事件 */
    fun incrementWatchedEpisode() {
        if (!isLoggedIn.value) {
            _uiState.update { it.copy(showLoginPromptDialog = true) }
            viewModelScope.launch {
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage("请先登录 Bangumi 账号"))
            }
            return
        }
        val currentEp = _uiState.value.collection?.epStatus ?: 0
        val nextEpNum = currentEp + 1
        val nextEpisode =
            _uiState.value.episodes.firstOrNull {
                it.isMain && it.episodeInt == nextEpNum
            }
        if (nextEpisode != null) {
            toggleEpisodeWatched(
                episodeId = nextEpisode.id,
                isWatched = true,
                epNumber = nextEpNum,
                episodeType = 0,
            )
        } else {
            markWatchedUpTo(
                Episode(
                    id = 0L,
                    sort = nextEpNum.toFloat(),
                    ep = nextEpNum.toFloat(),
                    type = 0,
                    name = "第 $nextEpNum 话",
                ),
            )
        }
    }

    /** 1-tap 快捷追番/移出在看（支持 0ms 本地即时乐观更新与失败回滚，未登录时拦截弹窗） */
    fun toggleWatching() {
        if (!isLoggedIn.value) {
            _uiState.update { it.copy(showLoginPromptDialog = true) }
            viewModelScope.launch {
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage("请先登录 Bangumi 账号"))
            }
            return
        }
        val current = _uiState.value.collection
        val nextType = if (current?.type == CollectionType.DOING.value) CollectionType.DROPPED else CollectionType.DOING
        updateCollectionStatus(nextType)
    }

    /**
     * 单集观看状态打卡（支持即时乐观更新，未登录时拦截弹窗）。
     *
     * 显式命令层（规范第 5 条）：同 [updateCollectionStatus]，乐观写 epStatus/type + 失败回滚
     * 保留为命令式实现，非主篇（SP/OP/ED）不污染正篇进度的语义在此处收口。
     */
    fun toggleEpisodeWatched(
        episodeId: Long,
        isWatched: Boolean,
        epNumber: Int = 1,
        episodeType: Int = 0,
    ) {
        if (!isLoggedIn.value) {
            _uiState.update { it.copy(showLoginPromptDialog = true) }
            viewModelScope.launch {
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage("请先登录 Bangumi 账号"))
            }
            return
        }

        val previousCollection = _uiState.value.collection
        val previousEpStatus = previousCollection?.epStatus ?: 0
        val previousType = previousCollection?.type ?: 0
        val currentEp = previousEpStatus
        val isMainEpisode = episodeType == 0
        // 乐观更新 UI 状态中的 collection.epStatus（仅正篇改变主篇进度，特别篇/OP/ED 不污染正篇进度）
        val newEpStatus =
            if (!isMainEpisode) {
                currentEp
            } else if (isWatched) {
                maxOf(currentEp, epNumber)
            } else {
                if (epNumber >= currentEp) maxOf(0, epNumber - 1) else currentEp
            }
        val targetType =
            if (isWatched &&
                (previousCollection == null || previousCollection.type == 0 || previousCollection.type == CollectionType.WISH.value)
            ) {
                CollectionType.DOING.value
            } else {
                previousCollection?.type ?: CollectionType.DOING.value
            }
        _uiState.update { state ->
            val updatedCollection =
                state.collection?.copy(
                    epStatus = newEpStatus,
                    type = targetType,
                ) ?: UserCollection(
                    userId = 0L,
                    subjectId = subjectId,
                    subjectType = _uiState.value.subject?.type ?: 2,
                    rate = 0,
                    type = targetType,
                    comment = "",
                    epStatus = newEpStatus,
                    volStatus = 0,
                    updatedAt = "",
                )
            state.copy(collection = updatedCollection, error = null)
        }

        if (isWatched) {
            viewModelScope.launch {
                _uiEvents.send(
                    SubjectDetailUiEvent.EpisodeMarked(
                        epNumber = epNumber,
                        episodeId = episodeId,
                        previousEpStatus = previousEpStatus,
                        previousType = previousType,
                        episodeType = episodeType,
                    ),
                )
            }
        }

        viewModelScope.launch {
            val result =
                collectionRepository.updateEpisodeStatus(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    isWatched = isWatched,
                    epNumber = if (isMainEpisode) epNumber else 0,
                )
            result.onError { _, message ->
                // 回滚
                _uiState.update { it.copy(collection = previousCollection, error = message) }
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage(message.ifBlank { "打卡失败，请确认是否已登录账号" }))
            }
        }
    }

    /**
     * 批量标记观看进度至目标话数（看到本集，未登录时拦截弹窗）。
     * 将本集及之前的所有常规单集批量打卡，并更新条目观看进度与收藏状态。
     *
     * 显式命令层（规范第 5 条）：同 [updateCollectionStatus]，乐观写 + 失败回滚 +
     * [SubjectDetailUiEvent.BatchMarked] 撤销快照（previousEpStatus/previousType/episodeIds）
     * 保留为命令式实现，不改写为仓库流回读。
     */
    fun markWatchedUpTo(targetEpisode: Episode) {
        if (!isLoggedIn.value) {
            _uiState.update { it.copy(showLoginPromptDialog = true) }
            viewModelScope.launch {
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage("请先登录 Bangumi 账号"))
            }
            return
        }
        val targetEpNumber = targetEpisode.episodeInt
        val previousCollection = _uiState.value.collection
        val previousEpStatus = previousCollection?.epStatus ?: 0
        val previousType = previousCollection?.type ?: 0
        val currentEp = previousEpStatus
        val newEpStatus = maxOf(currentEp, targetEpNumber)

        val targetType =
            if (previousCollection == null || previousCollection.type == 0 || previousCollection.type == CollectionType.WISH.value) {
                CollectionType.DOING.value
            } else {
                previousCollection.type
            }

        val optimisticCollection =
            previousCollection?.copy(
                epStatus = newEpStatus,
                type = targetType,
            ) ?: UserCollection(
                userId = 0L,
                subjectId = subjectId,
                subjectType = _uiState.value.subject?.type ?: 2,
                rate = 0,
                type = targetType,
                comment = "",
                epStatus = newEpStatus,
                volStatus = 0,
                updatedAt = "",
            )
        _uiState.update { it.copy(collection = optimisticCollection, error = null) }

        val targetEpisodeIds =
            _uiState.value.episodes
                .filter { ep ->
                    ep.isMain && ep.episodeInt in 1..targetEpNumber
                }.map { it.id }

        val newlyMarkedIds =
            _uiState.value.episodes
                .filter { ep ->
                    ep.isMain && ep.episodeInt in (previousEpStatus + 1)..targetEpNumber
                }.map { it.id }

        viewModelScope.launch {
            _uiEvents.send(
                SubjectDetailUiEvent.BatchMarked(
                    targetEpNumber = targetEpNumber,
                    episodeIds = newlyMarkedIds,
                    previousEpStatus = previousEpStatus,
                    previousType = previousType,
                ),
            )
        }

        viewModelScope.launch {
            val result =
                collectionRepository.markEpisodesWatchedUpTo(
                    subjectId = subjectId,
                    epNumber = targetEpNumber,
                    episodeIds = targetEpisodeIds,
                )
            result.onError { _, message ->
                _uiState.update { it.copy(collection = previousCollection, error = message) }
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage(message.ifBlank { "批量打卡失败，请确认是否已登录账号" }))
            }
        }
    }

    /**
     * 撤销批量打卡操作，立即乐观回滚本地状态并异步同步至云端。
     *
     * 显式命令层（规范第 5 条）：undo 快照回写是本函数的核心语义——先按快照乐观还原
     * （快照指向"未收藏"时直接清空 collection），失败再回滚为撤销前状态；与仓库流时序
     * 强耦合，禁止改写为仓库流回读。
     */
    fun undoMarkWatchedUpTo(
        previousEpStatus: Int,
        previousType: Int,
        undoneEpisodeIds: List<Long>,
    ) {
        if (!isLoggedIn.value) {
            _uiState.update { it.copy(showLoginPromptDialog = true) }
            viewModelScope.launch {
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage("请先登录 Bangumi 账号"))
            }
            return
        }
        val currentCollection = _uiState.value.collection
        val revertedCollection =
            if (previousType <= 0 && previousEpStatus <= 0) {
                null
            } else {
                currentCollection?.copy(
                    epStatus = previousEpStatus,
                    type = if (previousType > 0) previousType else currentCollection.type,
                )
            }
        _uiState.update { it.copy(collection = revertedCollection) }

        val previousState = currentCollection
        viewModelScope.launch {
            val targetType = if (previousType > 0) CollectionType.fromValue(previousType) else null
            val result =
                collectionRepository.revertEpisodesWatched(
                    subjectId = subjectId,
                    targetEpStatus = previousEpStatus,
                    targetType = targetType,
                    undoneEpisodeIds = undoneEpisodeIds,
                )
            result.onError { _, message ->
                _uiState.update { it.copy(collection = previousState) }
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage(message.ifBlank { "撤销失败，请确认是否已登录账号" }))
            }
        }
    }

    /** 按需加载单集吐槽（带本地内存缓存，避免重复网络请求） */
    fun loadEpisodeComments(episodeId: Long) {
        if (_uiState.value.episodeComments.containsKey(episodeId)) return
        viewModelScope.launch {
            _uiState.update { it.copy(isEpisodeCommentsLoading = true) }
            val result = communityRepository.getEpisodeComments(episodeId)
            _uiState.update { state ->
                when (result) {
                    is AppResult.Success -> {
                        state.copy(
                            isEpisodeCommentsLoading = false,
                            episodeComments = state.episodeComments + (episodeId to result.data),
                        )
                    }
                    is AppResult.Error -> {
                        state.copy(isEpisodeCommentsLoading = false)
                    }
                    is AppResult.Loading -> state
                }
            }
        }
    }

    /** 分页加载更多全网短评吐槽 */
    fun loadMoreSubjectComments() {
        val currentState = _uiState.value
        if (currentState.isLoadingMoreComments || !currentState.hasMoreComments) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreComments = true) }
            val offset = currentState.subjectComments.size
            val result = communityRepository.getSubjectComments(subjectId = subjectId, limit = 20, offset = offset)
            _uiState.update { state ->
                when (result) {
                    is AppResult.Success -> {
                        val newPage = result.data
                        val existingIds = state.subjectComments.map { c -> c.id }.toSet()
                        val uniqueNew = newPage.data.filter { c -> c.id !in existingIds }
                        val updatedList = state.subjectComments + uniqueNew
                        val total = if (newPage.total > 0) newPage.total else state.subjectCommentTotal
                        state.copy(
                            isLoadingMoreComments = false,
                            subjectComments = updatedList,
                            subjectCommentTotal = total,
                            hasMoreComments = updatedList.size < total && newPage.data.isNotEmpty(),
                        )
                    }
                    is AppResult.Error -> {
                        state.copy(isLoadingMoreComments = false)
                    }
                    is AppResult.Loading -> state
                }
            }
        }
    }

    /** 按需拉取角色详情及关联作品 */
    fun loadCharacterDetail(characterId: Long) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingEntityDetail = true,
                    selectedCharacterDetail = null,
                    selectedCharacterWorks = emptyList(),
                    selectedPersonDetail = null,
                    selectedPersonWorks = emptyList(),
                )
            }
            val detailResult = subjectRepository.fetchCharacterDetail(characterId)
            val worksResult = subjectRepository.fetchCharacterSubjects(characterId)

            _uiState.update { state ->
                state.copy(
                    isLoadingEntityDetail = false,
                    selectedCharacterDetail = (detailResult as? AppResult.Success)?.data,
                    selectedCharacterWorks = (worksResult as? AppResult.Success)?.data?.aggregateBySubject().orEmpty(),
                )
            }
        }
    }

    /** 按需拉取人物/制作人员详情及关联作品 */
    fun loadPersonDetail(personId: Long) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingEntityDetail = true,
                    selectedCharacterDetail = null,
                    selectedCharacterWorks = emptyList(),
                    selectedPersonDetail = null,
                    selectedPersonWorks = emptyList(),
                )
            }
            val detailResult = subjectRepository.fetchPersonDetail(personId)
            val worksResult = subjectRepository.fetchPersonSubjects(personId)

            _uiState.update { state ->
                state.copy(
                    isLoadingEntityDetail = false,
                    selectedPersonDetail = (detailResult as? AppResult.Success)?.data,
                    selectedPersonWorks = (worksResult as? AppResult.Success)?.data?.aggregateBySubject().orEmpty(),
                )
            }
        }
    }

    /** 关闭角色或人物详情底栏并清空状态 */
    fun clearEntityDetail() = dismissEntityDetail()

    fun dismissLoginPrompt() {
        _uiState.update { it.copy(showLoginPromptDialog = false) }
    }
}
