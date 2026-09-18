package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusDoingContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusDroppedContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusOnHoldContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusWishContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusDoingContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusDroppedContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusOnHoldContainerColor
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

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // 头部：标题与快捷入口
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "我的追番与收藏",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                if (isLoggedIn) {
                    TextButton(
                        onClick = { onCollectionClick(CollectionType.DOING) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "完整列表 ↗",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onLogin),
                    ) {
                        Text(
                            text = "未登录 · 点击登录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 核心主区：三大活跃追番状态（在看、想看、看过）等宽数据看板
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PrimaryCollectionHeroItem(
                    label = "在看",
                    tag = "追番中",
                    count = formatCount(CollectionType.DOING),
                    icon = Icons.Filled.PlayCircleOutline,
                    containerColor = statusDoingContainerColor(),
                    contentColor = onStatusDoingContainerColor(),
                    onClick = { handleItemClick(CollectionType.DOING) },
                    modifier = Modifier.weight(1f),
                )
                PrimaryCollectionHeroItem(
                    label = "想看",
                    tag = "愿望单",
                    count = formatCount(CollectionType.WISH),
                    icon = Icons.Filled.BookmarkBorder,
                    containerColor = statusWishContainerColor(),
                    contentColor = onStatusWishContainerColor(),
                    onClick = { handleItemClick(CollectionType.WISH) },
                    modifier = Modifier.weight(1f),
                )
                PrimaryCollectionHeroItem(
                    label = "看过",
                    tag = "已完成",
                    count = formatCount(CollectionType.COLLECT),
                    icon = Icons.Filled.CheckCircleOutline,
                    containerColor = statusCollectContainerColor(),
                    contentColor = onStatusCollectContainerColor(),
                    onClick = { handleItemClick(CollectionType.COLLECT) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 次级归档区：两大归档状态（搁置、抛弃）轻量胶囊栏
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ArchiveCapsuleItem(
                    label = "搁置",
                    count = formatCount(CollectionType.ON_HOLD),
                    icon = Icons.Filled.PauseCircleOutline,
                    containerColor = statusOnHoldContainerColor(),
                    contentColor = onStatusOnHoldContainerColor(),
                    onClick = { handleItemClick(CollectionType.ON_HOLD) },
                    modifier = Modifier.weight(1f),
                )
                ArchiveCapsuleItem(
                    label = "抛弃",
                    count = formatCount(CollectionType.DROPPED),
                    icon = Icons.Filled.Cancel,
                    containerColor = statusDroppedContainerColor(),
                    contentColor = onStatusDroppedContainerColor(),
                    onClick = { handleItemClick(CollectionType.DROPPED) },
                    modifier = Modifier.weight(1f),
                )
            }

            // 仅在登录状态下展示条目操作提示，未登录时去除多余的重复推销文案
            if (isLoggedIn) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "💡 点击任意分类可直达条目列表、查看打卡进度并支持多维度筛选",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/**
 * 核心追番状态数据看板（在看、想看、看过）
 */
@Composable
private fun PrimaryCollectionHeroItem(
    label: String,
    tag: String,
    count: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier =
            modifier
                .clip(shape)
                .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .background(containerColor.copy(alpha = 0.08f))
                    .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = containerColor,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = count,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.9f),
            )
        }
    }
}

/**
 * 归档状态轻量胶囊（搁置、抛弃）
 */
@Composable
private fun ArchiveCapsuleItem(
    label: String,
    count: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
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
            modifier =
                Modifier
                    .background(containerColor.copy(alpha = 0.06f))
                    .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
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
