package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.designsystem.component.bbcode.BgmBbCodeContent
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.EpisodeComment

/** 单条分集吐槽卡片：头像/昵称/时间、BBCode 正文、表情表态与楼中楼回复 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeCommentItem(
    comment: EpisodeComment,
    onUrlClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    currentUserId: Long? = null,
    onReactionClick: ((CommentReaction) -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                        model =
                            comment.user
                                ?.avatar
                                ?.bestAvatar,
                        contentDescription = comment.user?.displayName,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    Text(
                        text = comment.user?.displayName ?: "用户",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (comment.createdAt > 0) {
                    Text(
                        text = TimeUtils.formatEpochSecondsToDate(comment.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            ExpandableCommentContent(
                content = comment.content,
                onUrlClick = onUrlClick,
                collapsedMaxLines = 5,
            )

            // 点赞反应：已表态类型高亮，点击 toggle 己方表态
            if (comment.reactions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    comment.reactions.forEach { reaction ->
                        val mine = currentUserId != null && reaction.users.any { it.id == currentUserId }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color =
                                if (mine) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            modifier =
                                if (onReactionClick != null) {
                                    Modifier.clickable { onReactionClick(reaction) }
                                } else {
                                    Modifier
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Icon(
                                    imageVector = BgmIcons.Favorite,
                                    contentDescription = null,
                                    tint =
                                        if (mine) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                        },
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = reaction.count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (mine) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }
                        }
                    }
                }
            }

            // 楼中楼回复
            if (comment.replies.isNotEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(8.dp),
                            ).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    comment.replies.forEach { reply ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AsyncImage(
                                model =
                                    reply.user
                                        ?.avatar
                                        ?.bestAvatar
                                        .orEmpty(),
                                contentDescription = reply.user?.displayName,
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = reply.user?.displayName ?: "回复",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                ExpandableCommentContent(
                                    content = reply.content,
                                    onUrlClick = onUrlClick,
                                    collapsedMaxLines = 3,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 可折叠评论正文组件：
 * 基于 Compose 原生 [maxLines] 与 [onTextLayout]（hasVisualOverflow）实现行级折叠，
 * 首帧直接按折叠目标行数排版，彻底根除物理高度裁剪造成的半截文字切割与列表滑动首帧回弹抖动。
 */
@Composable
private fun ExpandableCommentContent(
    content: String,
    onUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 5,
) {
    var isExpanded by rememberSaveable(content) { mutableStateOf(false) }
    var canExpand by remember(content) { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(),
    ) {
        BgmBbCodeContent(
            content = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (isExpanded) Int.MAX_VALUE else collapsedMaxLines,
            onOverflowChanged = { overflow ->
                if (overflow) canExpand = true
            },
            onUrlClick = onUrlClick,
        )

        if (canExpand) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.clickable { isExpanded = !isExpanded },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = if (isExpanded) "收起" else "展开全文",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                        Icon(
                            imageVector =
                                if (isExpanded) BgmIcons.KeyboardArrowUp else BgmIcons.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
