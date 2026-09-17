package com.infinitezerone.minibgm.feature.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 讨论帖详情 ViewModel
 */
class TopicDetailViewModel(
    val topicId: Long,
    val type: String = "subject",
    private val communityRepository: CommunityRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TopicDetailUiState())
    val uiState: StateFlow<TopicDetailUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
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
}
