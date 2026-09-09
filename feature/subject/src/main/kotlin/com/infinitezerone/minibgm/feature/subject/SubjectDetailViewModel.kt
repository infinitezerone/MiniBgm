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
import kotlinx.coroutines.async
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
    val subjectComments: List<SubjectComment> = emptyList(),
    val subjectCommentTotal: Int = 0,
    val isLoadingMoreComments: Boolean = false,
    val hasMoreComments: Boolean = true,
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

    /** 刷新/重新拉取条目、分集、角色、制作团队、关联作品与收藏数据 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 1. 核心首屏数据优先拉取：条目详情、分集列表、收藏状态（保障用户以最快速度看到主体内容）
            val subjectDeferred = async { subjectRepository.fetchSubjectDetail(subjectId) }
            val episodesDeferred = async { subjectRepository.fetchEpisodes(subjectId) }
            val collectionDeferred = async { collectionRepository.fetchCollection(subjectId) }

            val subjectResult = subjectDeferred.await()
            val episodesResult = episodesDeferred.await()
            val collectionResult = collectionDeferred.await()

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

            // 2. 次级与社区数据并行拉取：角色、人员、关联作品、评论、讨论（削峰防 429 限流）
            val charactersDeferred = async { subjectRepository.fetchCharacters(subjectId) }
            val personsDeferred = async { subjectRepository.fetchPersons(subjectId) }
            val relationsDeferred = async { subjectRepository.fetchRelations(subjectId) }
            val subjectCommentsDeferred = async { communityRepository.getSubjectComments(subjectId, limit = 15) }
            val subjectTopicsDeferred = async { communityRepository.getSubjectTopics(subjectId, limit = 5) }

            val charactersResult = charactersDeferred.await()
            val personsResult = personsDeferred.await()
            val relationsResult = relationsDeferred.await()
            val subjectCommentsResult = subjectCommentsDeferred.await()
            val subjectTopicsResult = subjectTopicsDeferred.await()

            val commentsPage = (subjectCommentsResult as? AppResult.Success)?.data
            val topics = (subjectTopicsResult as? AppResult.Success)?.data.orEmpty()

            _uiState.update { current ->
                current.copy(
                    characters = (charactersResult as? AppResult.Success)?.data ?: current.characters,
                    persons = (personsResult as? AppResult.Success)?.data ?: current.persons,
                    relations = (relationsResult as? AppResult.Success)?.data ?: current.relations,
                    subjectComments = commentsPage?.data ?: current.subjectComments,
                    subjectCommentTotal = commentsPage?.total ?: current.subjectCommentTotal,
                    hasMoreComments = (commentsPage?.data?.size ?: 0) < (commentsPage?.total ?: 0),
                    subjectTopics = if (topics.isNotEmpty()) topics else current.subjectTopics,
                )
            }
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
            val detailDeferred = async { subjectRepository.fetchCharacterDetail(characterId) }
            val worksDeferred = async { subjectRepository.fetchCharacterSubjects(characterId) }

            val detailResult = detailDeferred.await()
            val worksResult = worksDeferred.await()

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
            val detailDeferred = async { subjectRepository.fetchPersonDetail(personId) }
            val worksDeferred = async { subjectRepository.fetchPersonSubjects(personId) }

            val detailResult = detailDeferred.await()
            val worksResult = worksDeferred.await()

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
