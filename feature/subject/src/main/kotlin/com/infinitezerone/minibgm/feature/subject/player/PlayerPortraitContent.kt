package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.feature.subject.components.CommentSortOrder
import com.infinitezerone.minibgm.feature.subject.components.episodeCommentsSection

/**
 * 竖屏播放页 - 简介 Tab 内容（分集信息与操作、选集横滑、播放源切换）
 */
@Composable
internal fun PlayerIntroContent(
    uiState: PlayerUiState,
    epLabel: String,
    onToggleWatched: () -> Unit,
    onToggleFollow: () -> Unit,
    onSelectEpisode: (PlayerEpisodeItem) -> Unit,
    onOpenAllEpisodes: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onRequestOpenSources: (() -> Unit)?,
    onManageRules: (() -> Unit)?,
    onSubjectClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLoadMoreEpisodes: () -> Unit = {},
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. 番剧标题、当前分集、打卡/追番/找源操作与剧情简介
        item(key = "episode_overview") {
            PlayerEpisodeOverviewCard(
                epLabel = epLabel,
                episodeName = uiState.episodeName,
                airDate = uiState.currentEpisodeAirdate,
                description = uiState.currentEpisodeDesc,
                isWatched = uiState.isWatched,
                collectionType = uiState.subjectCollectionType,
                onToggleWatched = onToggleWatched,
                onToggleFollow = onToggleFollow,
                onRequestOpenSources = onRequestOpenSources,
                subjectName = uiState.subjectName,
                score = uiState.subjectScore,
                onSubjectClick = onSubjectClick,
            )
        }

        // 2. 选集横滑
        if (uiState.episodes.isNotEmpty()) {
            item(key = "episodes_section") {
                BiliEpisodesSection(
                    episodes = uiState.episodes,
                    selectedEpisodeSort = uiState.episodeSort,
                    selectedEpisodeId = uiState.episodeId,
                    totalEpisodes = uiState.totalEpisodes,
                    hasMoreEpisodes = uiState.hasMoreEpisodes,
                    isLoadingMoreEpisodes = uiState.isLoadingMoreEpisodes,
                    onLoadMoreEpisodes = onLoadMoreEpisodes,
                    onSelectEpisode = onSelectEpisode,
                    onOpenAllEpisodes = onOpenAllEpisodes,
                )
            }
        }

        // 3. 播放源切换条
        if (uiState.sources.isNotEmpty()) {
            item(key = "player_sources") {
                BiliPlayerSourceBar(
                    sources = uiState.sources,
                    sourceFailureCounts = uiState.sourceFailureCounts,
                    selectedIndex = uiState.selectedSourceIndex,
                    onSelectSource = onSelectSource,
                    onRequestOpenSources = onRequestOpenSources,
                    onManageRules = onManageRules,
                    matchedVodName = uiState.matchedVodName,
                )
            }
        }
    }
}

/**
 * 竖屏播放页 - 讨论 Tab 内容（全高独立滚动视口、排序、Reaction）
 */
@Composable
internal fun PlayerDiscussionContent(
    uiState: PlayerUiState,
    onSortChange: (CommentSortOrder) -> Unit,
    onRetry: () -> Unit,
    onReactionClick: (EpisodeComment, CommentReaction) -> Unit,
    onAddReaction: (EpisodeComment, Int) -> Unit,
    onCopyComment: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val commentsListState = rememberLazyListState()

    LazyColumn(
        state = commentsListState,
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        episodeCommentsSection(
            comments = uiState.comments,
            commentCount = uiState.currentEpisodeCommentCount,
            isLoading = uiState.isCommentsLoading,
            error = uiState.commentsError,
            sortOrder = uiState.commentSortOrder,
            currentUserId = uiState.currentUserId,
            onSortChange = onSortChange,
            onRetry = onRetry,
            onReactionClick = onReactionClick,
            onAddReaction = onAddReaction,
            onCopyComment = onCopyComment,
            onUserClick = onUserClick,
            onUrlClick = onUrlClick,
            showHeader = true,
        )
    }
}
