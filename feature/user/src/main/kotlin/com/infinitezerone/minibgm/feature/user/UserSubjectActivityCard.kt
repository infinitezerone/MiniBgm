package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.designsystem.component.bounceClickable
import com.infinitezerone.minibgm.core.designsystem.component.rememberBounceOnClick
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.StatusDoing
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews

/** 在追动态条目：在追番剧的最新一条讨论（个人中心 UI 域模型） */
data class SubjectActivityItem(
    val subjectId: Long,
    val subjectName: String,
    val topicId: Long,
    val topicTitle: String,
    val replyCount: Int,
    val updatedAtMs: Long,
)

/** 在追动态卡状态：条目列表 + 首拉中标记（首拉中展示轻量占位，避免卡片闪现跳变） */
data class SubjectActivityState(
    val items: List<SubjectActivityItem> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * 在追动态卡：在追番剧的最新讨论速览。
 * 点击行进入对应番剧详情页；数据拉取失败时整行静默降级，全部失败则卡片由父级隐藏。
 */
@Composable
internal fun SubjectActivityCard(
    state: SubjectActivityState,
    onSubjectClick: (subjectId: Long, subjectName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowMillis = remember { TimeUtils.nowEpochMillis() }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Forum,
                    contentDescription = null,
                    tint = StatusDoing,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "在追动态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "在追番剧的最新讨论",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (state.isLoading) {
                SubjectActivityLoadingPlaceholder()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.items.forEach { item ->
                        SubjectActivityRow(
                            item = item,
                            nowMillis = nowMillis,
                            onClick = { onSubjectClick(item.subjectId, item.subjectName) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 在追动态行：番名 + 最新讨论标题 + 回复数与相对时间。
 * 点击进入番剧详情页（讨论详情路由暂未开放，先落到条目上下文）。
 */
@Composable
private fun SubjectActivityRow(
    item: SubjectActivityItem,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val bounceState = rememberBounceOnClick(pressedScale = 0.97f)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .bounceClickable(state = bounceState, onClickLabel = "查看讨论") { onClick() }
                .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (item.subjectName.isNotBlank()) {
                Text(
                    text = item.subjectName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StatusDoing,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = item.topicTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(
                    imageVector = Icons.Filled.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = "${item.replyCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatActivityRelativeTime(item.updatedAtMs, nowMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 首拉中的轻量占位：与真实行几何对齐，避免加载完成后卡片高度跳变 */
@Composable
private fun SubjectActivityLoadingPlaceholder() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier =
                            Modifier
                                .height(10.dp)
                                .width(72.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .padding(horizontal = 0.dp),
                    ) {}
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.8f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp)),
                    ) {}
                }
                Row(
                    modifier =
                        Modifier
                            .height(10.dp)
                            .width(40.dp)
                            .clip(RoundedCornerShape(5.dp)),
                ) {}
            }
        }
    }
}

/** epoch 毫秒 → 相对时间文案；非法值回退为空串（装饰性信息 fail-open） */
private fun formatActivityRelativeTime(
    updatedAtMs: Long,
    nowMillis: Long,
): String {
    if (updatedAtMs <= 0) return ""
    val diffMinutes = ((nowMillis - updatedAtMs) / 60_000L).coerceAtLeast(0)
    return when {
        diffMinutes < 1 -> "刚刚"
        diffMinutes < 60 -> "$diffMinutes 分钟前"
        diffMinutes < 60 * 24 -> "${diffMinutes / 60} 小时前"
        diffMinutes < 60 * 24 * 30 -> "${diffMinutes / (60 * 24)} 天前"
        else -> TimeUtils.formatEpochSecondsToDate(updatedAtMs / 1000)
    }
}

@ThemePreviews
@Composable
private fun SubjectActivityCardPreview() {
    MiniBgmTheme {
        SubjectActivityCard(
            state =
                SubjectActivityState(
                    items =
                        listOf(
                            SubjectActivityItem(
                                subjectId = 1L,
                                subjectName = "孤独摇滚！",
                                topicId = 101L,
                                topicTitle = "最终话的演出分析：波奇酱的第一次登台",
                                replyCount = 42,
                                updatedAtMs = TimeUtils.nowEpochMillis() - 30 * 60_000L,
                            ),
                            SubjectActivityItem(
                                subjectId = 2L,
                                subjectName = "葬送的芙莉莲",
                                topicId = 102L,
                                topicTitle = "大家怎么看这一话芙莉莲和欣梅尔的对话？",
                                replyCount = 17,
                                updatedAtMs = TimeUtils.nowEpochMillis() - 5 * 60 * 60_000L,
                            ),
                        ),
                ),
            onSubjectClick = { _, _ -> },
        )
    }
}

@ThemePreviews
@Composable
private fun SubjectActivityCardLoadingPreview() {
    MiniBgmTheme {
        SubjectActivityCard(
            state = SubjectActivityState(isLoading = true),
            onSubjectClick = { _, _ -> },
        )
    }
}
