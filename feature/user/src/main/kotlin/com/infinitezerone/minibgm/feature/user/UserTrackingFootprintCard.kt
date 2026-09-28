package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.data.repository.TrackingFootprint
import com.infinitezerone.minibgm.core.designsystem.component.bounceClickable
import com.infinitezerone.minibgm.core.designsystem.component.rememberBounceOnClick
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusDoingContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusDoingContainerColor
import com.infinitezerone.minibgm.core.model.CollectionType

/**
 * 追番足迹卡：基于本地「在看」收藏的轻量聚合（在看部数 / 累计追集 / 本月打卡 / 最近打卡时刻）。
 * 数据全部来自本地 Room 聚合查询，无网络开销；点击数字瓦片直达「在看」收藏列表。
 */
@Composable
internal fun TrackingFootprintCard(
    footprint: TrackingFootprint,
    onCollectionClick: (CollectionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // 头部：标题与最近打卡时刻
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "追番足迹",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "在看中的追番数据",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                formatLastActiveAt(footprint.lastActiveAtIso)?.let { lastActive ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = "最近打卡 · $lastActive",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 核心主区：三项足迹看板（在看部数、累计追集、本月打卡），数字为主视觉
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FootprintStatTile(
                    label = "在看部数",
                    count = footprint.watchingCount,
                    icon = Icons.Filled.Tv,
                    containerColor = statusDoingContainerColor(),
                    contentColor = onStatusDoingContainerColor(),
                    onClick = { onCollectionClick(CollectionType.DOING) },
                    modifier = Modifier.weight(1f),
                )
                FootprintStatTile(
                    label = "累计追集",
                    count = footprint.episodesWatched,
                    icon = Icons.Filled.PlayCircleOutline,
                    containerColor = statusDoingContainerColor(),
                    contentColor = onStatusDoingContainerColor(),
                    onClick = { onCollectionClick(CollectionType.DOING) },
                    modifier = Modifier.weight(1f),
                )
                FootprintStatTile(
                    label = "本月打卡",
                    count = footprint.monthActiveCount,
                    icon = Icons.Filled.LocalFireDepartment,
                    containerColor = statusDoingContainerColor(),
                    contentColor = onStatusDoingContainerColor(),
                    onClick = { onCollectionClick(CollectionType.DOING) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 足迹数据瓦片：与收藏总览的核心瓦片同构，数字即主视觉，点击带弹性反馈。
 */
@Composable
private fun FootprintStatTile(
    label: String,
    count: Int,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val bounceState = rememberBounceOnClick(pressedScale = 0.96f)
    Surface(
        modifier =
            modifier
                .clip(shape)
                .bounceClickable(state = bounceState, onClickLabel = "查看$label") { onClick() },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .background(containerColor.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = containerColor,
                    modifier = Modifier.size(20.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 最近打卡时刻 → 相对时间文案；无数据或解析失败返回 null（装饰性信息 fail-open） */
private fun formatLastActiveAt(lastActiveAtIso: String?): String? {
    if (lastActiveAtIso.isNullOrBlank()) return null
    val days = TimeUtils.daysSinceIsoUtc(lastActiveAtIso) ?: return null
    return when {
        days <= 0 -> "今天"
        days == 1 -> "昨天"
        days < 30 -> "$days 天前"
        else -> TimeUtils.formatIsoToCstDate(lastActiveAtIso)
    }
}

@ThemePreviews
@Composable
private fun TrackingFootprintCardPreview() {
    MiniBgmTheme {
        TrackingFootprintCard(
            footprint =
                TrackingFootprint(
                    watchingCount = 8,
                    episodesWatched = 96,
                    monthActiveCount = 3,
                    lastActiveAtIso = "2026-09-26T14:30:00Z",
                ),
            onCollectionClick = {},
        )
    }
}

@ThemePreviews
@Composable
private fun TrackingFootprintCardEmptyPreview() {
    MiniBgmTheme {
        TrackingFootprintCard(
            footprint =
                TrackingFootprint(
                    watchingCount = 0,
                    episodesWatched = 0,
                    monthActiveCount = 0,
                    lastActiveAtIso = null,
                ),
            onCollectionClick = {},
        )
    }
}
