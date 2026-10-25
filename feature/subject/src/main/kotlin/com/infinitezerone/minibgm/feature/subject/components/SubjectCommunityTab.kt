package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.feature.subject.SubjectDetailUiState

/** 讨论页只承载他人观点：短评与讨论帖，不重复概览页的评分与标签决策信息。 */
@Composable
internal fun CommunityTabPage(
    uiState: SubjectDetailUiState,
    onLoadMoreComments: () -> Unit,
    onLinkClick: (String) -> Unit,
    onTopicClick: (Long, String) -> Unit,
    onCopyComment: ((String) -> Unit)?,
    topPadding: Dp = 12.dp,
    bottomPadding: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "community_tab_section") {
            SubjectCommunitySection(
                comments = uiState.subjectComments,
                commentTotal = uiState.subjectCommentTotal,
                isLoadingMoreComments = uiState.isLoadingMoreComments,
                hasMoreComments = uiState.hasMoreComments,
                onLoadMoreComments = onLoadMoreComments,
                topics = uiState.subjectTopics,
                onUrlClick = onLinkClick,
                onTopicClick = onTopicClick,
                onCopyComment = onCopyComment,
                isLoading = uiState.isCommunityLoading,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
