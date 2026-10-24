package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.feature.subject.SubjectDetailUiState

/** 讨论页：评论区与话题。 */
@Composable
internal fun CommunityTabPage(
    uiState: SubjectDetailUiState,
    onLoadMoreComments: () -> Unit,
    onLinkClick: (String) -> Unit,
    onTopicClick: (Long, String) -> Unit,
    onCopyComment: ((String) -> Unit)?,
    bottomPadding: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
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
