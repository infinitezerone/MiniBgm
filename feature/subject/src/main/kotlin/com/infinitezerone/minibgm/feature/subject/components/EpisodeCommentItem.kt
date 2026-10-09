package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.designsystem.component.bbcode.BgmBbCodeParser
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.feature.subject.R

/**
 * 单条分集吐槽列表项：
 * 采用主流社交产品标准非卡片式（Unboxed）列表流布局：
 * - 左侧：用户圆形头像；
 * - 右侧：顶部昵称与签名、中间评论正文与楼中楼回复容器；
 * - 底部：左下角相对时间与楼层序号、右下角表情表态 Chip 栏与添加按钮；
 * - 底栏：细分割线隔开各评论。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EpisodeCommentItem(
    comment: EpisodeComment,
    modifier: Modifier = Modifier,
    floorNumber: Int? = null,
    onUrlClick: (String) -> Unit = {},
    onUserClick: ((String) -> Unit)? = null,
    onCopyComment: ((String) -> Unit)? = null,
    currentUserId: Long? = null,
    onReactionClick: ((CommentReaction) -> Unit)? = null,
    onAddReaction: ((Int) -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    var isRepliesExpanded by rememberSaveable(comment.id) { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            if (onCopyComment != null && comment.content.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCopyComment(comment.content)
                            }
                        },
                    ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 左列：用户头像
            val username = comment.user?.username
            val avatarClickable = onUserClick != null && !username.isNullOrBlank()
            AsyncImage(
                model = comment.user?.avatar?.bestAvatar,
                contentDescription = comment.user?.displayName,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .then(
                            if (avatarClickable) {
                                Modifier.clickable { onUserClick(username.orEmpty()) }
                            } else {
                                Modifier
                            },
                        ),
            )

            // 右列：昵称、正文、楼中楼、底栏（日期/楼层 + 表态栏）
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 顶部：昵称与签名
                Column(
                    modifier =
                        if (avatarClickable) {
                            Modifier.clickable { onUserClick(username.orEmpty()) }
                        } else {
                            Modifier
                        },
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = comment.user?.displayName ?: stringResource(R.string.feature_subject_comment_user_default),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!comment.user?.sign.isNullOrBlank()) {
                        Text(
                            text = comment.user!!.sign,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 评论正文
                ExpandableCommentContent(
                    content = comment.content,
                    onUrlClick = onUrlClick,
                    collapsedMaxLines = 5,
                    key = comment.id,
                )

                // 楼中楼回复容器
                if (comment.replies.isNotEmpty()) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(8.dp),
                                ).padding(8.dp)
                                .animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val visibleReplies =
                            if (isRepliesExpanded || comment.replies.size <= 2) {
                                comment.replies
                            } else {
                                comment.replies.take(2)
                            }

                        visibleReplies.forEach { reply ->
                            val replyUsername = reply.user?.username
                            val subAvatarClickable = onUserClick != null && !replyUsername.isNullOrBlank()
                            val parsedReply = remember(reply.content) { BgmBbCodeParser.parseSubReply(reply.content) }
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                if (onCopyComment != null && parsedReply.content.isNotBlank()) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onCopyComment(parsedReply.content)
                                                }
                                            },
                                        ),
                            ) {
                                AsyncImage(
                                    model =
                                        reply.user
                                            ?.avatar
                                            ?.bestAvatar
                                            .orEmpty(),
                                    contentDescription = reply.user?.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .then(
                                                if (subAvatarClickable) {
                                                    Modifier.clickable { onUserClick(replyUsername.orEmpty()) }
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = reply.user?.displayName ?: stringResource(R.string.feature_subject_comment_action_reply),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier =
                                                if (subAvatarClickable) {
                                                    Modifier.clickable { onUserClick(replyUsername.orEmpty()) }
                                                } else {
                                                    Modifier
                                                },
                                        )
                                        val replyToUser = parsedReply.replyToUser
                                        if (!replyToUser.isNullOrBlank()) {
                                            Text(
                                                text = stringResource(R.string.feature_subject_comment_action_reply),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            )
                                            Text(
                                                text = "@$replyToUser",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier =
                                                    if (onUserClick != null) {
                                                        Modifier.clickable { onUserClick(replyToUser) }
                                                    } else {
                                                        Modifier
                                                    },
                                            )
                                        }
                                        if (reply.createdAt > 0) {
                                            Text(
                                                text = "· ${TimeUtils.formatRelativeTime(reply.createdAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            )
                                        }
                                    }
                                    ExpandableCommentContent(
                                        content = parsedReply.content,
                                        onUrlClick = onUrlClick,
                                        collapsedMaxLines = 3,
                                        key = reply.id,
                                    )
                                }
                            }
                        }

                        if (comment.replies.size > 2) {
                            val remainingCount = comment.replies.size - 2
                            Row(
                                modifier =
                                    Modifier
                                        .clickable { isRepliesExpanded = !isRepliesExpanded }
                                        .padding(top = 4.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(16.dp)
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                )
                                Text(
                                    text =
                                        if (isRepliesExpanded) {
                                            stringResource(R.string.feature_subject_collapse)
                                        } else {
                                            stringResource(R.string.feature_subject_comment_expand_replies, remainingCount)
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium,
                                )
                                Icon(
                                    imageVector =
                                        if (isRepliesExpanded) {
                                            BgmIcons.KeyboardArrowUp
                                        } else {
                                            BgmIcons.KeyboardArrowDown
                                        },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }

                // 底部栏：左侧日期与楼层，右侧表情表态 Chip 栏与添加按钮
                CommentReactionsBar(
                    reactions = comment.reactions,
                    currentUserId = currentUserId,
                    onReactionClick = { reaction -> onReactionClick?.invoke(reaction) },
                    onAddReaction = { value -> onAddReaction?.invoke(value) },
                    leadingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (comment.createdAt > 0) {
                                Text(
                                    text = TimeUtils.formatRelativeTime(comment.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                            if (floorNumber != null) {
                                Text(
                                    text = stringResource(R.string.feature_subject_comment_floor_format, floorNumber),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    },
                )
            }
        }

        // 分割线
        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )
    }
}
