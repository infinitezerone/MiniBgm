package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
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
import com.infinitezerone.minibgm.core.designsystem.component.bounceClickable
import com.infinitezerone.minibgm.core.designsystem.component.rememberBounceOnClick
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.StatusCollect
import com.infinitezerone.minibgm.core.designsystem.theme.StatusDoing
import com.infinitezerone.minibgm.core.designsystem.theme.StatusDropped
import com.infinitezerone.minibgm.core.designsystem.theme.StatusOnHold
import com.infinitezerone.minibgm.core.designsystem.theme.StatusWish
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusDoingContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusWishContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusDoingContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusWishContainerColor
import com.infinitezerone.minibgm.core.model.CollectionType

@Composable
internal fun CollectionOverviewCard(
    isLoggedIn: Boolean,
    collectionCounts: Map<CollectionType, Int>,
    isCountsLoading: Boolean,
    onCollectionClick: (CollectionType) -> Unit,
    onLogin: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    fun formatCount(type: CollectionType): String {
        if (!isLoggedIn) return "--"
        val count = collectionCounts[type]
        return when {
            count != null -> count.toString()
            isCountsLoading -> "…"
            else -> "0"
        }
    }

    val handleItemClick: (CollectionType) -> Unit = { type ->
        if (isLoggedIn) {
            onCollectionClick(type)
        } else {
            onLogin()
        }
    }

    val total = collectionCounts.values.sum()
    val showShareBar = isLoggedIn && !isCountsLoading && total > 0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // 头部：标题与收藏总量（未登录时仅保留登录入口）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "我的收藏",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (showShareBar) {
                        Text(
                            text = "共 $total 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!isLoggedIn) {
                    // 收藏列表页自带五类型 Tab，任一瓦片都能直达全量列表，
                    // 已登录时无需再铺一个重复的「完整列表」入口
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onLogin),
                    ) {
                        Text(
                            text = "未登录 · 点击登录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 核心主区：三大活跃收藏状态（在看、想看、看过），数字为主视觉
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PrimaryCollectionHeroItem(
                    label = "在看",
                    count = formatCount(CollectionType.DOING),
                    icon = Icons.Filled.PlayCircleOutline,
                    containerColor = statusDoingContainerColor(),
                    contentColor = onStatusDoingContainerColor(),
                    onClick = { handleItemClick(CollectionType.DOING) },
                    modifier = Modifier.weight(1f),
                )
                PrimaryCollectionHeroItem(
                    label = "想看",
                    count = formatCount(CollectionType.WISH),
                    icon = Icons.Filled.BookmarkBorder,
                    containerColor = statusWishContainerColor(),
                    contentColor = onStatusWishContainerColor(),
                    onClick = { handleItemClick(CollectionType.WISH) },
                    modifier = Modifier.weight(1f),
                )
                PrimaryCollectionHeroItem(
                    label = "看过",
                    count = formatCount(CollectionType.COLLECT),
                    icon = Icons.Filled.CheckCircleOutline,
                    containerColor = statusCollectContainerColor(),
                    contentColor = onStatusCollectContainerColor(),
                    onClick = { handleItemClick(CollectionType.COLLECT) },
                    modifier = Modifier.weight(1f),
                )
            }

            if (showShareBar) {
                Spacer(modifier = Modifier.height(12.dp))
                // 五维占比条：一条微缩数据全景，颜色与下方归档胶囊一一对应
                CollectionShareBar(
                    collectionCounts = collectionCounts,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 次级归档区：两大归档状态（搁置、抛弃）轻量胶囊栏
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ArchiveCapsuleItem(
                    label = "搁置",
                    count = formatCount(CollectionType.ON_HOLD),
                    icon = Icons.Filled.PauseCircleOutline,
                    accentColor = StatusOnHold,
                    onClick = { handleItemClick(CollectionType.ON_HOLD) },
                    modifier = Modifier.weight(1f),
                )
                ArchiveCapsuleItem(
                    label = "抛弃",
                    count = formatCount(CollectionType.DROPPED),
                    icon = Icons.Filled.Cancel,
                    accentColor = StatusDropped,
                    onClick = { handleItemClick(CollectionType.DROPPED) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 五维收藏占比条：按各状态条目数等比分段，零计数分段自动隐藏。
 */
@Composable
private fun CollectionShareBar(
    collectionCounts: Map<CollectionType, Int>,
    modifier: Modifier = Modifier,
) {
    val segments =
        listOf(
            StatusDoing to collectionCounts[CollectionType.DOING].orZero(),
            StatusWish to collectionCounts[CollectionType.WISH].orZero(),
            StatusCollect to collectionCounts[CollectionType.COLLECT].orZero(),
            StatusOnHold to collectionCounts[CollectionType.ON_HOLD].orZero(),
            StatusDropped to collectionCounts[CollectionType.DROPPED].orZero(),
        )
    Row(
        modifier =
            modifier
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
    ) {
        segments.forEach { (color, count) ->
            if (count > 0) {
                Box(
                    modifier =
                        Modifier
                            .weight(count.toFloat())
                            .fillMaxHeight()
                            .background(color),
                )
            }
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0

/**
 * 核心收藏状态数据看板（在看、想看、看过）：紧凑单卡，数字即主视觉，点击带弹性反馈。
 */
@Composable
private fun PrimaryCollectionHeroItem(
    label: String,
    count: String,
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
                text = count,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 归档状态轻量胶囊（搁置、抛弃）：图标采用状态本色，与占比条颜色呼应。
 */
@Composable
private fun ArchiveCapsuleItem(
    label: String,
    count: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier =
            modifier
                .clip(shape)
                .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun CollectionOverviewCardLoggedInPreview() {
    MiniBgmTheme {
        CollectionOverviewCard(
            isLoggedIn = true,
            collectionCounts =
                mapOf(
                    CollectionType.DOING to 8,
                    CollectionType.WISH to 24,
                    CollectionType.COLLECT to 142,
                    CollectionType.ON_HOLD to 3,
                    CollectionType.DROPPED to 1,
                ),
            isCountsLoading = false,
            onCollectionClick = {},
            onLogin = {},
        )
    }
}

@ThemePreviews
@Composable
private fun CollectionOverviewCardUnauthenticatedPreview() {
    MiniBgmTheme {
        CollectionOverviewCard(
            isLoggedIn = false,
            collectionCounts = emptyMap(),
            isCountsLoading = false,
            onCollectionClick = {},
            onLogin = {},
        )
    }
}
