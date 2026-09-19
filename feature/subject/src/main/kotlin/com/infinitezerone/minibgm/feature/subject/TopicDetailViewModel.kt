package com.infinitezerone.minibgm.feature.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.CommunityLikeTarget
import com.infinitezerone.minibgm.core.model.TopicReply
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 讨论帖详情一次性单发事件
 */
sealed interface TopicDetailUiEvent {
    data class ShowSnackbar(
        val message: String,
    ) : TopicDetailUiEvent
}

/**
 * 讨论帖详情 ViewModel
 */
class TopicDetailViewModel(
    val topicId: Long,
    val type: String = "subject",
    private val communityRepository: CommunityRepository,
    private val authRepository: AuthRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TopicDetailUiState())
    val uiState: StateFlow<TopicDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<TopicDetailUiEvent>(Channel.BUFFERED)
    val events: Flow<TopicDetailUiEvent> = _events.receiveAsFlow()

    private var refreshJob: Job? = null

    init {
        authRepository?.let { auth ->
            viewModelScope.launch {
                auth.activeUserId.collect { userId ->
                    _uiState.update { it.copy(currentUserId = userId) }
                }
            }
        }
        refresh(isUserPullToRefresh = false)
    }

    /** 刷新讨论帖内容与回帖流 */
    fun refresh(isUserPullToRefresh: Boolean = false) {
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isRefreshing = isUserPullToRefresh,
                        isLoading = if (it.topicDetail == null) true else it.isLoading,
                        error = null,
                    )
                }

                when (val result = communityRepository.getTopicDetail(topicId, type)) {
                    is AppResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                topicDetail = result.data,
                                error = null,
                            )
                        }
                    }
                    is AppResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = result.message.ifBlank { "加载讨论帖失败" },
                            )
                        }
                    }
                    is AppResult.Loading -> Unit
                }
            }
    }

    /**
     * 切换自己对某楼层某条表情表态的状态：
     * 未登录提示登录；已在该类型表态过 → 取消；否则以该表情类型加入。
     * 成功后刷新楼层以同步反应计数。
     */
    fun toggleReaction(
        reply: TopicReply,
        reaction: CommentReaction,
    ) {
        val userId = _uiState.value.currentUserId
        if (userId == null) {
            _events.trySend(TopicDetailUiEvent.ShowSnackbar("请先登录后再表态"))
            return
        }
        val target = if (type == "group") CommunityLikeTarget.GROUP_POST else CommunityLikeTarget.SUBJECT_POST
        val reacted = reaction.users.any { it.id == userId }
        viewModelScope.launch {
            val result =
                if (reacted) {
                    communityRepository.removeLike(target, reply.id)
                } else {
                    communityRepository.setLike(target, reply.id, reaction.value)
                }
            when (result) {
                is AppResult.Success -> {
                    _events.trySend(TopicDetailUiEvent.ShowSnackbar(if (reacted) "已取消表态" else "已表态"))
                    refresh()
                }
                is AppResult.Error -> _events.trySend(TopicDetailUiEvent.ShowSnackbar(result.message.ifBlank { "表态失败" }))
                is AppResult.Loading -> Unit
            }
        }
    }
}
