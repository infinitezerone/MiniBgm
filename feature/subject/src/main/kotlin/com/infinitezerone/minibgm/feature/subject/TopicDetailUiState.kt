package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.model.TopicDetail

/**
 * 讨论帖详情界面 UI 状态
 */
data class TopicDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val topicDetail: TopicDetail? = null,
    val error: String? = null,
)
