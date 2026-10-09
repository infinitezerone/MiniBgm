package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.feature.subject.R

/**
 * Bangumi 官方表情表态 ID 到 tv 表情贴图文件名的映射表（对齐 next.bgm.tv 官方映射表）。
 */
private val BANGUMI_REACTION_MAP: Map<Int, String> =
    mapOf(
        0 to "44",
        79 to "40",
        54 to "15",
        140 to "101",
        62 to "23",
        122 to "83",
        104 to "65",
        80 to "41",
        141 to "102",
        88 to "49",
        85 to "46",
        90 to "51",
    )

/**
 * Bangumi 官方表情包贴图地址解析（tv 系列常用经典表情）。
 */
internal fun bgmReactionStickerUrl(reactionValue: Int): String? {
    val mappedId =
        BANGUMI_REACTION_MAP[reactionValue]
            ?: if (reactionValue in 1..100) reactionValue.toString().padStart(2, '0') else null
    return mappedId?.let { "https://lain.bgm.tv/img/smiles/tv/$it.gif" }
}

/** 快捷常用表态表情集合（涵盖 Bangumi 官方全部可用表态） */
internal data class QuickReactionEmoji(
    val value: Int,
    val name: String,
)

internal val POPULAR_REACTION_EMOJIS =
    listOf(
        QuickReactionEmoji(0, "点赞"),
        QuickReactionEmoji(140, "大拇指"),
        QuickReactionEmoji(141, "双拇指"),
        QuickReactionEmoji(79, "打call"),
        QuickReactionEmoji(54, "庆祝"),
        QuickReactionEmoji(62, "荧光棒"),
        QuickReactionEmoji(122, "期待"),
        QuickReactionEmoji(104, "抓拍"),
        QuickReactionEmoji(80, "期待 2"),
        QuickReactionEmoji(88, "抓拍 2"),
        QuickReactionEmoji(85, "摇铃"),
        QuickReactionEmoji(90, "开车"),
    )

/**
 * 评论反应 Chip：展示真实 Bangumi 娘/贴图表情图标、计数及本人表态高亮状态。
 */
@Composable
fun CommentReactionChip(
    reaction: CommentReaction,
    isMine: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val stickerUrl = remember(reaction.value) { bgmReactionStickerUrl(reaction.value) }
    var isLoadError by remember(reaction.value) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color =
            if (isMine) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        modifier =
            if (onClick != null) {
                modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick)
            } else {
                modifier
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            if (stickerUrl != null && !isLoadError) {
                AsyncImage(
                    model = stickerUrl,
                    contentDescription = "表态表情",
                    modifier = Modifier.size(16.dp),
                    contentScale = ContentScale.Fit,
                    onError = { isLoadError = true },
                )
            } else {
                Icon(
                    imageVector = BgmIcons.Favorite,
                    contentDescription = null,
                    tint =
                        if (isMine) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        },
                    modifier = Modifier.size(12.dp),
                )
            }

            Text(
                text = reaction.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isMine) FontWeight.Bold else FontWeight.Medium,
                color =
                    if (isMine) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

/**
 * 评论表态区：包含左侧内容（如时间/楼层）、右侧已有反应 Chip 与快捷添加表态按钮，以及展开的表情选择面板。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommentReactionsBar(
    reactions: List<CommentReaction>,
    currentUserId: Long?,
    onReactionClick: (CommentReaction) -> Unit,
    onAddReaction: (Int) -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.weight(1f, fill = false),
                contentAlignment = Alignment.CenterStart,
            ) {
                leadingContent?.invoke()
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                reactions.forEach { reaction ->
                    val isMine = currentUserId != null && reaction.users.any { it.id == currentUserId }
                    CommentReactionChip(
                        reaction = reaction,
                        isMine = isMine,
                        onClick = { onReactionClick(reaction) },
                    )
                }

                // 添加表情表态按钮
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color =
                        if (showPicker) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)
                        },
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showPicker = !showPicker },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.Add,
                            contentDescription = stringResource(R.string.feature_subject_reaction_add),
                            tint =
                                if (showPicker) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.size(13.dp),
                        )
                        if (reactions.isEmpty()) {
                            Text(
                                text = stringResource(R.string.feature_subject_reaction_cd),
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (showPicker) {
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

        // 快捷表情选择栏
        AnimatedVisibility(
            visible = showPicker,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    POPULAR_REACTION_EMOJIS.forEach { emoji ->
                        val stickerUrl = bgmReactionStickerUrl(emoji.value)
                        Box(
                            modifier =
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable {
                                        showPicker = false
                                        onAddReaction(emoji.value)
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (stickerUrl != null) {
                                AsyncImage(
                                    model = stickerUrl,
                                    contentDescription = emoji.name,
                                    modifier = Modifier.size(20.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
