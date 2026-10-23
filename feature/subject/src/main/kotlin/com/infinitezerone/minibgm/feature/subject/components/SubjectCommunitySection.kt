package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.designsystem.component.BgmSegmentedControl
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.model.SubjectComment
import com.infinitezerone.minibgm.core.model.SubjectTopic
import com.infinitezerone.minibgm.feature.subject.R

internal const val BGM_BASE_URL = "https://bgm.tv"

private enum class CommunityPane { SHORT_REVIEWS, FORUM }

/** 社区标签页 / 讨论模块：原生展示短评精选与小组讨论帖子 */
@Composable
fun SubjectCommunitySection(
    comments: List<SubjectComment>,
    commentTotal: Int,
    isLoadingMoreComments: Boolean,
    hasMoreComments: Boolean,
    onLoadMoreComments: () -> Unit,
    topics: List<SubjectTopic>,
    onUrlClick: (String) -> Unit = {},
    onTopicClick: (Long, String) -> Unit = { _, _ -> },
    onCopyComment: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    val context = LocalContext.current
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedPane by rememberSaveable { mutableStateOf(CommunityPane.SHORT_REVIEWS) }
    val displayComments = if (isExpanded) comments else comments.take(5)
    val reviewCount = if (commentTotal > 0) commentTotal else comments.size

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BgmSegmentedControl(
            items = CommunityPane.entries,
            selectedItem = selectedPane,
            onItemSelected = { selectedPane = it },
            itemContent = { pane, isSelected ->
                Text(
                    text =
                        when (pane) {
                            CommunityPane.SHORT_REVIEWS ->
                                if (reviewCount > 0) {
                                    stringResource(R.string.feature_subject_community_pane_reviews_count, reviewCount)
                                } else {
                                    stringResource(R.string.feature_subject_community_pane_reviews)
                                }
                            CommunityPane.FORUM ->
                                if (topics.isNotEmpty()) {
                                    stringResource(R.string.feature_subject_community_pane_forum_count, topics.size)
                                } else {
                                    stringResource(R.string.feature_subject_community_pane_forum)
                                }
                        },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            },
        )

        // 1. 全网即时短评
        if (selectedPane == CommunityPane.SHORT_REVIEWS) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (comments.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = stringResource(R.string.feature_subject_community_short_reviews_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            displayComments.forEachIndexed { index, comment ->
                                SubjectCommentItem(
                                    comment = comment,
                                    onUrlClick = onUrlClick,
                                    onCopyComment = onCopyComment,
                                )
                                if (index < displayComments.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                }
                            }

                            if (!isExpanded && (comments.size > 5 || commentTotal > 5)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                ShortReviewsFooterAction(
                                    text = stringResource(R.string.feature_subject_community_view_more_short_reviews, commentTotal),
                                    onClick = { isExpanded = true },
                                    icon = BgmIcons.KeyboardArrowDown,
                                )
                            } else if (isExpanded) {
                                // 展开态只留一条分隔线收口：三种动作行统一由 ShortReviewsFooterAction 渲染，
                                // 不再各自一套字号 / 内边距 / 箭头尺寸，也不再出现「禁用后仍是主色」的假可点
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                if (hasMoreComments) {
                                    ShortReviewsFooterAction(
                                        text =
                                            if (isLoadingMoreComments) {
                                                stringResource(R.string.feature_subject_community_short_reviews_loading)
                                            } else {
                                                stringResource(
                                                    R.string.feature_subject_community_load_more_short_reviews,
                                                    comments.size,
                                                    commentTotal,
                                                )
                                            },
                                        onClick = onLoadMoreComments,
                                        icon = BgmIcons.KeyboardArrowDown,
                                        isLoading = isLoadingMoreComments,
                                    )
                                } else if (comments.size >= commentTotal && commentTotal > 5) {
                                    ShortReviewsFooterAction(
                                        text =
                                            stringResource(
                                                R.string.feature_subject_community_all_short_reviews_shown,
                                                commentTotal,
                                            ),
                                        onClick = {},
                                        emphasized = false,
                                        enabled = false,
                                    )
                                }
                                ShortReviewsFooterAction(
                                    text = stringResource(R.string.feature_subject_community_collapse_short_reviews),
                                    onClick = { isExpanded = false },
                                    icon = BgmIcons.KeyboardArrowUp,
                                    emphasized = false,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. 讨论版话题
        if (selectedPane == CommunityPane.FORUM) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (topics.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.feature_subject_community_forum_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            topics.take(6).forEachIndexed { index, topic ->
                                SubjectTopicItem(
                                    topic = topic,
                                    onClick = { onTopicClick(topic.id, topic.title) },
                                )
                                if (index < topics.take(6).lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialTheme.labelMediumSmallOrFallback() = typography.labelSmall

/** 单条短评卡片组件 */
@Composable
fun SubjectCommentItem(
    comment: SubjectComment,
    onUrlClick: (String) -> Unit = {},
    onCopyComment: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsyncImage(
                    model = comment.user?.avatar?.bestAvatar,
                    contentDescription = comment.user?.displayName,
                    modifier = Modifier.size(28.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    text = comment.user?.displayName.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (comment.rate > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = RatingGold.copy(alpha = 0.15f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.Star,
                                contentDescription = null,
                                tint = RatingGold,
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                text = "${comment.rate}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = RatingGold,
                            )
                        }
                    }
                }
            }

            if (comment.updatedAt > 0) {
                Text(
                    text = TimeUtils.formatEpochSecondsToDate(comment.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }

        if (comment.comment.isNotBlank()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                if (onCopyComment != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCopyComment(comment.comment)
                                }
                            },
                        ),
            ) {
                ExpandableCommentContent(
                    content = comment.comment,
                    onUrlClick = onUrlClick,
                    collapsedMaxLines = 5,
                    key = comment.user?.username ?: comment.updatedAt,
                )
            }
        }
    }
}

/** 讨论帖条目组件 */
@Composable
fun SubjectTopicItem(
    topic: SubjectTopic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = topic.creator?.displayName.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (topic.createdAt > 0) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = TimeUtils.formatEpochSecondsToDate(topic.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (topic.replyCount > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = stringResource(R.string.feature_subject_community_replies_count, topic.replyCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** 短评底部动作行的最小热区：对齐无障碍推荐值，避免「加载更多」只有一行字那么高 */
private const val SHORT_REVIEW_FOOTER_MIN_TOUCH_DP = 44

/**
 * 短评列表底部的统一动作行（展开更多 / 加载更多 / 已全部显示 / 收起）。
 *
 * 此前三种状态各写一套：加载更多是 labelMedium + 8dp 内边距 + primary 加粗，收起却是
 * labelMediumSmall + 4dp 内边距 + onSurfaceVariant，箭头还一大一小——展开与收起看起来像两个组件；
 * 加载中又把主色文字换成灰色、行高跟着跳变，禁用后仍留着主色让人以为还能点。
 * 统一后：同一字号与间距、≥44dp 热区、同样 16dp 箭头；加载/禁用态老实降级为次要色。
 */
@Composable
private fun ShortReviewsFooterAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    emphasized: Boolean = true,
    enabled: Boolean = true,
) {
    val contentColor =
        when {
            isLoading -> MaterialTheme.colorScheme.onSurfaceVariant
            emphasized -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BgmShapes.small)
                .heightIn(min = SHORT_REVIEW_FOOTER_MIN_TOUCH_DP.dp)
                .clickable(enabled = enabled && !isLoading, onClick = onClick)
                .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (emphasized && !isLoading) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (icon != null && !isLoading) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
