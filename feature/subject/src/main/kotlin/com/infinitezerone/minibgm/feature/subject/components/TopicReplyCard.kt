package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.designsystem.component.bbcode.BgmBbCodeParser
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.TopicReply
import com.infinitezerone.minibgm.feature.subject.R

/**
 * 讨论帖楼层回帖项（流式无边框排版，对齐主流现代移动端社区标准）：
 * - 头像 + 昵称 + 楼主标识 + 真实楼层号
 * - BBCode 富文本解析、长帖纯文本展开/收起、长按复制
 * - 小红书式楼中楼（前 2 条平铺 + 细线展开剩余回复 + 去除嵌套引用大框）
 * - 底部左侧相对时间、右侧表情表态 Chip 栏与添加选择器
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopicReplyCard(
    reply: TopicReply,
    floorNumber: Int,
    onUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isOp: Boolean = false,
    onUserClick: ((String) -> Unit)? = null,
    onCopyContent: ((String) -> Unit)? = null,
    currentUserId: Long? = null,
    onReactionClick: ((CommentReaction) -> Unit)? = null,
    onAddReaction: ((Int) -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val username = reply.creator?.username
    val avatarClickable = onUserClick != null && !username.isNullOrBlank()
    var isRepliesExpanded by rememberSaveable(reply.id) { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 用户头像
            AsyncImage(
                model =
                    reply.creator
                        ?.avatar
                        ?.bestAvatar
                        .orEmpty(),
                contentDescription = reply.creator?.displayName,
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

            // 右侧内容主体
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 顶部信息：用户名、楼主 Badge、楼层号
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = reply.creator?.displayName ?: stringResource(R.string.feature_subject_comment_user_default),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier =
                                if (avatarClickable) {
                                    Modifier.clickable { onUserClick(username.orEmpty()) }
                                } else {
                                    Modifier
                                },
                        )
                        if (isOp) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = stringResource(R.string.feature_subject_topic_original_poster),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.feature_subject_comment_floor_format, floorNumber),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium,
                    )
                }

                // 回帖正文（支持长按复制与行级展开）
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    if (onCopyContent != null && reply.content.isNotBlank()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onCopyContent(reply.content)
                                    }
                                },
                            ),
                ) {
                    ExpandableCommentContent(
                        content = reply.content,
                        onUrlClick = onUrlClick,
                        collapsedMaxLines = 8,
                        key = reply.id,
                    )
                }

                // 楼中楼回复容器 (Sub-replies，小红书平铺风格)
                if (reply.replies.isNotEmpty()) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(8.dp),
                                ).padding(horizontal = 10.dp, vertical = 10.dp)
                                .animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val visibleReplies =
                            if (isRepliesExpanded || reply.replies.size <= 2) {
                                reply.replies
                            } else {
                                reply.replies.take(2)
                            }

                        visibleReplies.forEach { subReply ->
                            val subUsername = subReply.creator?.username
                            val subAvatarClickable = onUserClick != null && !subUsername.isNullOrBlank()
                            val parsedReply = remember(subReply.content) { BgmBbCodeParser.parseSubReply(subReply.content) }
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                if (onCopyContent != null && parsedReply.content.isNotBlank()) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onCopyContent(parsedReply.content)
                                                }
                                            },
                                        ).padding(vertical = 1.dp),
                            ) {
                                AsyncImage(
                                    model =
                                        subReply.creator
                                            ?.avatar
                                            ?.bestAvatar
                                            .orEmpty(),
                                    contentDescription = subReply.creator?.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .then(
                                                if (subAvatarClickable) {
                                                    Modifier.clickable { onUserClick(subUsername.orEmpty()) }
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
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text =
                                                subReply.creator?.displayName
                                                    ?: stringResource(R.string.feature_subject_comment_action_reply),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier =
                                                if (subAvatarClickable) {
                                                    Modifier.clickable { onUserClick(subUsername.orEmpty()) }
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
                                        if (subReply.createdAt > 0) {
                                            Text(
                                                text = "· ${TimeUtils.formatRelativeTime(subReply.createdAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            )
                                        }
                                    }
                                    ExpandableCommentContent(
                                        content = parsedReply.content,
                                        onUrlClick = onUrlClick,
                                        collapsedMaxLines = 3,
                                        key = subReply.id,
                                    )
                                }
                            }
                        }

                        // 小红书式平铺展开细线栏（缩进 34dp 避让上方头像，避免误触）
                        if (reply.replies.size > 2) {
                            val remainingCount = reply.replies.size - 2
                            Row(
                                modifier =
                                    Modifier
                                        .padding(top = 4.dp, start = 34.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { isRepliesExpanded = !isRepliesExpanded }
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
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
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Icon(
                                    imageVector =
                                        if (isRepliesExpanded) {
                                            BgmIcons.KeyboardArrowUp
                                        } else {
                                            BgmIcons.KeyboardArrowDown
                                        },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                // 底部栏：左侧日期，右侧表情表态栏
                CommentReactionsBar(
                    reactions = reply.reactions,
                    currentUserId = currentUserId,
                    onReactionClick = { reaction -> onReactionClick?.invoke(reaction) },
                    onAddReaction = { value -> onAddReaction?.invoke(value) },
                    leadingContent = {
                        if (reply.createdAt > 0) {
                            Text(
                                text = TimeUtils.formatRelativeTime(reply.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
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
