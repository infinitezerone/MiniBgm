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
import com.infinitezerone.minibgm.core.model.CharacterDetail
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.PersonDetail
import com.infinitezerone.minibgm.core.model.RelatedWork
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectComment
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectRelation
import com.infinitezerone.minibgm.core.model.SubjectTopic
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.model.aggregateBySubject
import com.infinitezerone.minibgm.feature.subject.components.episodeGuideLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    /** 把找源请求交接给 AI 助手会话（[prefillPrompt] 即助手首条提问，仅返回可观看页面链接） */
    data class OpenSourceSearch(
        val prefillPrompt: String,
    ) : SubjectDetailUiEvent
}

/** 条目详情页二级分栏枚举 */
enum class SubjectDetailTab(
    val label: String,
) {
    EPISODES("📺 章节打卡"),
    DETAILS("📖 资料与演职员"),
    COMMUNITY("💬 社区吐槽"),
}

/** 条目详情页 UI 状态 */
data class SubjectDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoggedIn: Boolean = false,
    val selectedTab: SubjectDetailTab = SubjectDetailTab.EPISODES,
    val isEpisodeGridView: Boolean = true,
    val selectedEpisodeForDetail: Episode? = null,
    val activeCharacter: SubjectCharacter? = null,
    val activePerson: SubjectPerson? = null,
    val showCollectionSheet: Boolean = false,
    val showLoginPromptDialog: Boolean = false,
    val error: String? = null,
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
)

class SubjectDetailViewModel(
    private val subjectRepository: SubjectRepository,
    private val subjectId: Long,
    private val collectionRepository: CollectionRepository,
    private val communityRepository: CommunityRepository,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository? = null,
    private val failureStore: PlaybackFailureStore? = null,
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
     * 把找源请求交接给 AI 助手会话：本页面不做任何检索，只生成显式触发用的提问文案。
     * 能力边界见 ROADMAP 第 5 节——助手只返回可观看页面链接，不产出媒体直链。
     */
    fun requestSourceSearch(episode: Episode? = null) {
        val title =
            _uiState.value.subject
                ?.displayName
                ?.ifBlank { "本条目" } ?: "本条目"
        val target = if (episode == null) "《$title》" else "《$title》 ${episodeGuideLabel(episode)}"
        val prompt = "帮我找${target}的在线观看页面，只给我可以打开观看的网页链接（Bangumi 条目号 $subjectId）"
        viewModelScope.launch {
            _uiEvents.send(SubjectDetailUiEvent.OpenSourceSearch(prompt))
        }
    }

    private val isLoggedIn =
        authRepository.isLoggedIn
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
            }
        }
        // 先订阅本地库/内存缓存流：如果仓库中已有缓存，立刻合成进入 UiState，秒开无白屏
        viewModelScope.launch {
            combine(
                subjectRepository.getSubjectStream(subjectId),
                subjectRepository.getEpisodesStream(subjectId),
                collectionRepository.getCollectionStream(subjectId),
            ) { subject, episodes, localCollection ->
                Triple(subject, episodes, localCollection)
            }.collect { (subject, episodes, localCollection) ->
                _uiState.update { state ->
                    val mergedCollection =
                        if (localCollection == null) {
                            null
                        } else {
                            state.collection?.copy(
                                type = localCollection.type,
                                epStatus = localCollection.epStatus,
                            ) ?: localCollection
                        }
                    state.copy(
                        subject = subject ?: state.subject,
                        episodes = episodes,
                        collection = mergedCollection,
                        isLoading = if (subject != null || state.subject != null) false else state.isLoading,
                    )
                }
            }
        }
        if (settingsRepository != null) {
            viewModelScope.launch {
                settingsRepository.playbackRules.collect { rules ->
                    _uiState.update { it.copy(playbackRules = rules) }
                }
            }
            viewModelScope.launch {
                settingsRepository.playlists.collect { playlists ->
                    _uiState.update { it.copy(playlists = playlists) }
                }
            }
        }
        if (failureStore != null) {
            viewModelScope.launch {
                failureStore.recentFailures.collect { failures ->
                    _uiState.update { it.copy(failedSourceReasons = failures) }
                }
            }
        }
        // 拉取条目详情与章节（写入本地库）；错误仅转为文案，不中断流程
        refresh(isUserPullToRefresh = false)
    }

    private var detailsLoaded = false
    private var communityLoaded = false
    private var refreshJob: Job? = null
    private var detailsJob: Job? = null
    private var communityJob: Job? = null

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
                        error = null,
                    )
                }

                // 核心首屏数据平滑有序拉取：条目详情 -> 分集列表 -> 收藏状态（串行平滑，杜绝并发冲击）
                val subjectResult = subjectRepository.fetchSubjectDetail(subjectId)
                val episodesResult = subjectRepository.fetchEpisodes(subjectId)
                val collectionResult = collectionRepository.fetchCollection(subjectId)

                subjectResult.onError { _, message ->
                    if (_uiState.value.subject == null) {
                        _uiState.update { it.copy(error = message) }
                    }
                }
                episodesResult.onError { _, message ->
                    if (_uiState.value.subject == null) {
                        _uiState.update { it.copy(error = message) }
                    }
                }
                collectionResult.onError { _, message ->
                    if (_uiState.value.subject == null) {
                        _uiState.update { it.copy(error = message) }
                    }
                }

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        isRefreshing = false,
                        subject = (subjectResult as? AppResult.Success)?.data ?: current.subject,
                        collection =
                            if (collectionResult is AppResult.Success) {
                                collectionResult.data
                            } else {
                                current.collection
                            },
                    )
                }
            }
    }

    /** 切换详情页二级 Tab，并自动按需加载对应数据 */
    fun selectTab(tab: SubjectDetailTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            SubjectDetailTab.EPISODES -> Unit
            SubjectDetailTab.DETAILS -> loadDetailsTabIfNeeded()
            SubjectDetailTab.COMMUNITY -> loadCommunityTabIfNeeded()
        }
    }

    /** 切换分集列表的宫格视图/详细列表视图 */
    fun setEpisodeGridView(isGrid: Boolean) {
        _uiState.update { it.copy(isEpisodeGridView = isGrid) }
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
     * 仅在用户主动切到「资料与演职员」Tab 时触发，已加载过则不再重复请求。
     */
    fun loadDetailsTabIfNeeded(force: Boolean = false) {
        if (!force && (detailsLoaded || detailsJob?.isActive == true)) return
        detailsJob?.cancel()
        detailsJob =
            viewModelScope.launch {
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
            }
    }

    /**
     * 按需懒加载社区吐槽与讨论版 Tab 数据（短评 -> 讨论）。
     * 仅在用户主动切到「社区吐槽」Tab 时触发，已加载过则不再重复请求。
     */
    fun loadCommunityTabIfNeeded(force: Boolean = false) {
        if (!force && (communityLoaded || communityJob?.isActive == true)) return
        communityJob?.cancel()
        communityJob =
            viewModelScope.launch {
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
            }
    }

    /** 更新条目收藏状态（想看/在看/看过等，支持 0ms 本地即时乐观更新与失败回滚，未登录时拦截弹窗） */
    fun updateCollectionStatus(
        type: CollectionType,
        rate: Int? = null,
        comment: String? = null,
        private: Boolean = false,
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
        val optimisticCollection =
            previousCollection?.copy(
                type = type.value,
                rate = rate ?: previousCollection.rate,
                comment = comment ?: previousCollection.comment,
                subjectType = resolvedSubjectType,
            ) ?: UserCollection(
                userId = 0L,
                subjectId = subjectId,
                subjectType = resolvedSubjectType,
                rate = rate ?: 0,
                type = type.value,
                comment = comment.orEmpty(),
                epStatus = 0,
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
                    subjectType = resolvedSubjectType,
                )
            result.onError { _, message ->
                // 2. 失败回滚为原状态并提示错误
                _uiState.update { it.copy(collection = previousCollection, error = message) }
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage(message.ifBlank { "更新收藏状态失败，请确认是否已登录账号" }))
            }
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

    /** 单集观看状态打卡（支持即时乐观更新，未登录时拦截弹窗） */
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
     */
    fun markWatchedUpTo(targetEpisode: Episode) {
        if (!isLoggedIn.value) {
            _uiState.update { it.copy(showLoginPromptDialog = true) }
            viewModelScope.launch {
                _uiEvents.send(SubjectDetailUiEvent.ShowMessage("请先登录 Bangumi 账号"))
            }
            return
        }
        val targetEpNumber = if (targetEpisode.ep > 0f) targetEpisode.ep.toInt() else targetEpisode.sort.toInt()
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
                    ep.type == 0 && (if (ep.ep > 0f) ep.ep.toInt() else ep.sort.toInt()) in 1..targetEpNumber
                }.map { it.id }

        val newlyMarkedIds =
            _uiState.value.episodes
                .filter { ep ->
                    ep.type == 0 &&
                        (if (ep.ep > 0f) ep.ep.toInt() else ep.sort.toInt()) in (previousEpStatus + 1)..targetEpNumber
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

    /** 开始 OAuth 授权流程，隐藏提示弹窗并生成授权 URL */
    suspend fun beginLogin(): String {
        _uiState.update { it.copy(showLoginPromptDialog = false) }
        return authRepository.beginLogin()
    }

    fun dismissLoginPrompt() {
        _uiState.update { it.copy(showLoginPromptDialog = false) }
    }
}
