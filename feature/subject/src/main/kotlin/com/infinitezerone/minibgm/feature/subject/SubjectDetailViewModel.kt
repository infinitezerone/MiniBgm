package com.infinitezerone.minibgm.feature.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 条目详情页 UI 状态 */
data class SubjectDetailUiState(
    val isLoading: Boolean = true,
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
)

class SubjectDetailViewModel(
    private val subjectRepository: SubjectRepository,
    private val subjectId: Long,
    private val collectionRepository: CollectionRepository,
    private val communityRepository: CommunityRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SubjectDetailUiState())
    val uiState: StateFlow<SubjectDetailUiState> = _uiState.asStateFlow()

    init {
        // 先拉取条目详情与章节（写入本地库）；错误仅转为文案，不中断流程
        refresh()
        // 订阅本地库流：fetch 写库后由 combine 单一流合并进 UiState（单一数据源，防止分散并发派发导致重组风暴）
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
                    )
                }
            }
        }
    }

    private var detailsLoaded = false
    private var communityLoaded = false
    private var refreshJob: Job? = null
    private var detailsJob: Job? = null
    private var communityJob: Job? = null

    /** 刷新/重新拉取条目、分集与收藏数据（首屏核心三要素） */
    fun refresh() {
        refreshJob?.cancel()
        detailsLoaded = false
        communityLoaded = false
        refreshJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }

                // 核心首屏数据平滑有序拉取：条目详情 -> 分集列表 -> 收藏状态（串行平滑，杜绝并发冲击）
                val subjectResult = subjectRepository.fetchSubjectDetail(subjectId)
                val episodesResult = subjectRepository.fetchEpisodes(subjectId)
                val collectionResult = collectionRepository.fetchCollection(subjectId)

                subjectResult.onError { _, message -> _uiState.update { it.copy(error = message) } }
                episodesResult.onError { _, message -> _uiState.update { it.copy(error = message) } }
                collectionResult.onError { _, message -> _uiState.update { it.copy(error = message) } }

                val remoteCollection = (collectionResult as? AppResult.Success)?.data

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        subject = (subjectResult as? AppResult.Success)?.data ?: current.subject,
                        collection = remoteCollection ?: current.collection,
                    )
                }
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

                _uiState.update { current ->
                    current.copy(
                        isDetailsLoading = false,
                        characters = (charactersResult as? AppResult.Success)?.data ?: current.characters,
                        persons = (personsResult as? AppResult.Success)?.data ?: current.persons,
                        relations = (relationsResult as? AppResult.Success)?.data ?: current.relations,
                    )
                }
                detailsLoaded = true
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
                communityLoaded = true
            }
    }

    /** 更新条目收藏状态（想看/在看/看过等，支持 0ms 本地即时乐观更新与失败回滚） */
    fun updateCollectionStatus(
        type: CollectionType,
        rate: Int? = null,
        comment: String? = null,
        private: Boolean = false,
    ) {
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
            }
        }
    }

    /** 1-tap 快捷追番/移出在看（支持 0ms 本地即时乐观更新与失败回滚） */
    fun toggleWatching() {
        val current = _uiState.value.collection
        val nextType = if (current?.type == CollectionType.DOING.value) CollectionType.DROPPED else CollectionType.DOING
        updateCollectionStatus(nextType)
    }

    /** 单集观看状态打卡（支持即时乐观更新） */
    fun toggleEpisodeWatched(
        episodeId: Long,
        isWatched: Boolean,
        epNumber: Int = 1,
    ) {
        val previousCollection = _uiState.value.collection
        val currentEp = previousCollection?.epStatus ?: 0
        // 乐观更新 UI 状态中的 collection.epStatus
        val newEpStatus =
            if (isWatched) {
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

        viewModelScope.launch {
            val result =
                collectionRepository.updateEpisodeStatus(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    isWatched = isWatched,
                    epNumber = epNumber,
                )
            result.onError { _, message ->
                // 回滚
                _uiState.update { it.copy(collection = previousCollection, error = message) }
            }
        }
    }

    /**
     * 批量标记观看进度至目标话数（看到本集）。
     * 将本集及之前的所有常规单集批量打卡，并更新条目观看进度与收藏状态。
     */
    fun markWatchedUpTo(targetEpisode: Episode) {
        val targetEpNumber = if (targetEpisode.ep > 0f) targetEpisode.ep.toInt() else targetEpisode.sort.toInt()
        val previousCollection = _uiState.value.collection
        val currentEp = previousCollection?.epStatus ?: 0
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
                    val num = if (ep.ep > 0f) ep.ep.toInt() else ep.sort.toInt()
                    num in 1..targetEpNumber
                }.map { it.id }

        viewModelScope.launch {
            val result =
                collectionRepository.markEpisodesWatchedUpTo(
                    subjectId = subjectId,
                    epNumber = targetEpNumber,
                    episodeIds = targetEpisodeIds,
                )
            result.onError { _, message ->
                _uiState.update { it.copy(collection = previousCollection, error = message) }
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
                val comments = (result as? AppResult.Success)?.data.orEmpty()
                state.copy(
                    isEpisodeCommentsLoading = false,
                    episodeComments = state.episodeComments + (episodeId to comments),
                )
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
                    selectedCharacterWorks = (worksResult as? AppResult.Success)?.data.orEmpty(),
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
                )
            }
            val detailResult = subjectRepository.fetchPersonDetail(personId)
            val worksResult = subjectRepository.fetchPersonSubjects(personId)

            _uiState.update { state ->
                state.copy(
                    isLoadingEntityDetail = false,
                    selectedPersonDetail = (detailResult as? AppResult.Success)?.data,
                    selectedPersonWorks = (worksResult as? AppResult.Success)?.data.orEmpty(),
                )
            }
        }
    }

    /** 关闭角色或人物详情底栏并清空状态 */
    fun clearEntityDetail() {
        _uiState.update {
            it.copy(
                selectedCharacterDetail = null,
                selectedCharacterWorks = emptyList(),
                selectedPersonDetail = null,
                selectedPersonWorks = emptyList(),
                isLoadingEntityDetail = false,
            )
        }
    }
}
