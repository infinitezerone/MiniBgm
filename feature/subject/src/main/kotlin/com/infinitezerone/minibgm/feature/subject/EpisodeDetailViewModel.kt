package com.infinitezerone.minibgm.feature.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.feature.subject.components.isEpisodeWatched
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val error: String? = null,
)

/** 单集详情独立 ViewModel */
class EpisodeDetailViewModel(
    val subjectId: Long,
    val episodeId: Long,
    private val subjectRepository: SubjectRepository,
    private val collectionRepository: CollectionRepository,
    private val communityRepository: CommunityRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EpisodeDetailUiState())
    val uiState: StateFlow<EpisodeDetailUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        // 订阅本地分集流与收藏状态流
        viewModelScope.launch {
            combine(
                subjectRepository.getEpisodesStream(subjectId),
                collectionRepository.getCollectionStream(subjectId),
            ) { episodes, collection ->
                val ep = episodes.firstOrNull { it.id == episodeId }
                val isWatched = ep?.let { isEpisodeWatched(it, collection?.epStatus ?: 0) } ?: false
                Triple(ep, isWatched, collection to episodes)
            }.collect { (ep, isWatched, pair) ->
                val (collection, episodes) = pair
                _uiState.update { current ->
                    current.copy(
                        episode = ep ?: current.episode,
                        isWatched = isWatched,
                        collection = collection ?: current.collection,
                        allEpisodes = if (episodes.isNotEmpty()) episodes else current.allEpisodes,
                        isLoading = if (ep != null || current.episode != null) false else current.isLoading,
                    )
                }
            }
        }

        // 初始化拉取分集吐槽短评（及兜底分集元数据）
        refresh(isUserPullToRefresh = false)
    }

    /** 刷新单集吐槽短评与分集元数据 */
    fun refresh(isUserPullToRefresh: Boolean = false) {
        // 取消在途刷新：连续下拉时避免旧请求的短评响应后到覆盖新数据
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isRefreshing = isUserPullToRefresh,
                        isCommentsLoading = true,
                        error = null,
                    )
                }

                // 若分集本地尚未载入，先同步拉取条目分集列表
                if (_uiState.value.episode == null) {
                    val epResult = subjectRepository.fetchEpisodes(subjectId)
                    if (epResult is AppResult.Success) {
                        val ep = epResult.data.firstOrNull { it.id == episodeId }
                        if (ep != null) {
                            _uiState.update { it.copy(episode = ep, isLoading = false) }
                        }
                    }
                }

                // 拉取分集吐槽短评
                val commentsResult = communityRepository.getEpisodeComments(episodeId)
                _uiState.update { current ->
                    when (commentsResult) {
                        is AppResult.Success ->
                            current.copy(
                                comments = commentsResult.data,
                                isCommentsLoading = false,
                                isRefreshing = false,
                                isLoading = false,
                            )
                        is AppResult.Error ->
                            current.copy(
                                isCommentsLoading = false,
                                isRefreshing = false,
                                isLoading = false,
                                error = if (current.comments.isEmpty()) commentsResult.message else null,
                            )
                        is AppResult.Loading -> current
                    }
                }
            }
    }

    /** 切换当前分集观看打卡状态 */
    fun toggleWatched(
        episode: Episode,
        isWatched: Boolean,
    ) {
        val epNumber = if (episode.ep > 0f) episode.ep.toInt() else episode.sort.toInt()
        val previousCollection = _uiState.value.collection
        val currentEp = previousCollection?.epStatus ?: 0
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
            val updated =
                state.collection?.copy(epStatus = newEpStatus, type = targetType)
                    ?: UserCollection(
                        userId = 0L,
                        subjectId = subjectId,
                        subjectType = 2,
                        rate = 0,
                        type = targetType,
                        comment = "",
                        epStatus = newEpStatus,
                        volStatus = 0,
                        updatedAt = "",
                    )
            state.copy(collection = updated, isWatched = isWatched)
        }

        viewModelScope.launch {
            val result =
                collectionRepository.updateEpisodeStatus(
                    subjectId = subjectId,
                    episodeId = episode.id,
                    isWatched = isWatched,
                    epNumber = epNumber,
                )
            result.onError { _, message ->
                _uiState.update {
                    it.copy(
                        collection = previousCollection,
                        isWatched = !isWatched,
                        error = message,
                    )
                }
            }
        }
    }

    /** 批量标记观看进度至目标话数（看到本集） */
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

        val targetEpisodeIds =
            _uiState.value.allEpisodes
                .filter { ep ->
                    val num = if (ep.ep > 0f) ep.ep.toInt() else ep.sort.toInt()
                    num in 1..targetEpNumber
                }.map { it.id }

        _uiState.update { state ->
            // 未收藏时与 toggleWatched 对齐：构造乐观收藏，避免"看到本集"点击后 UI 无反馈
            val updated =
                state.collection?.copy(epStatus = newEpStatus, type = targetType)
                    ?: UserCollection(
                        userId = 0L,
                        subjectId = subjectId,
                        subjectType = 2,
                        rate = 0,
                        type = targetType,
                        comment = "",
                        epStatus = newEpStatus,
                        volStatus = 0,
                        updatedAt = "",
                    )
            state.copy(collection = updated, isWatched = true)
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
            }
        }
    }
}
