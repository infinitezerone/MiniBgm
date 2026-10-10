package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/**
 * 评论区头部栏（标题、数量徽标、排序切换）
 */
@Composable
fun EpisodeCommentsHeader(
    commentCount: Int,
    sortOrder: CommentSortOrder,
    onSortChange: (CommentSortOrder) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.feature_subject_player_tab_discussion),
    showSortTabs: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (commentCount > 0) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = commentCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (showSortTabs && commentCount > 0) {
            CommentSortOrderTabs(
                currentOrder = sortOrder,
                onOrderSelected = { order ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSortChange(order)
                },
            )
        }
    }
}

/**
 * 可复用的单集评论列表项组（挂载在 LazyListScope 下）：
 * 统一管理头部栏、加载骨架、加载失败重试卡片、空状态、以及包含楼层/表态/复制/跳转的单条评论。
 */
fun LazyListScope.episodeCommentsSection(
    comments: List<EpisodeComment>,
    commentCount: Int,
    isLoading: Boolean,
    error: String?,
    sortOrder: CommentSortOrder,
    currentUserId: Long?,
    onSortChange: (CommentSortOrder) -> Unit,
    onRetry: () -> Unit,
    onReactionClick: (EpisodeComment, CommentReaction) -> Unit,
    onAddReaction: (EpisodeComment, Int) -> Unit,
    onCopyComment: (String) -> Unit,
    onUserClick: ((String) -> Unit)? = null,
    onUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    showHeader: Boolean = true,
) {
    if (showHeader) {
        item(key = "comments_header") {
            EpisodeCommentsHeader(
                commentCount = commentCount,
                sortOrder = sortOrder,
                onSortChange = onSortChange,
                title = title ?: stringResource(R.string.feature_subject_player_tab_discussion),
                modifier = modifier.padding(vertical = 6.dp),
            )
        }
    }

    when {
        isLoading && comments.isEmpty() -> {
            item(key = "comments_loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.feature_subject_ep_comments_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        error != null && comments.isEmpty() -> {
            item(key = "comments_error") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = stringResource(R.string.feature_subject_ep_comments_load_failed),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        FilledTonalButton(
                            onClick = onRetry,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(DesignSystemR.string.core_designsystem_action_retry))
                        }
                    }
                }
            }
        }

        comments.isEmpty() -> {
            item(key = "comments_empty") {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.feature_subject_ep_comments_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }

        else -> {
            itemsIndexed(
                items = comments,
                key = { _, it -> it.id },
                contentType = { _, _ -> "episode_comment" },
            ) { index, comment ->
                EpisodeCommentItem(
                    comment = comment,
                    floorNumber = if (comment.floor > 0) comment.floor else (index + 1),
                    onUrlClick = onUrlClick,
                    onUserClick = onUserClick,
                    onCopyComment = onCopyComment,
                    currentUserId = currentUserId,
                    onReactionClick = { reaction -> onReactionClick(comment, reaction) },
                    onAddReaction = { reactionValue -> onAddReaction(comment, reactionValue) },
                    showDivider = index < comments.lastIndex,
                )
            }
        }
    }
}
