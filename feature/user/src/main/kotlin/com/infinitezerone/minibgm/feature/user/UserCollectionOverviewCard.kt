package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.ActionCollect
import com.infinitezerone.minibgm.core.designsystem.theme.WishOrange
import com.infinitezerone.minibgm.core.model.CollectionType

@Composable
internal fun CollectionOverviewCard(
    isLoggedIn: Boolean,
    collectionCounts: Map<CollectionType, Int>,
    isCountsLoading: Boolean,
    onCollectionClick: (CollectionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun formatCount(type: CollectionType): String {
        if (!isLoggedIn) return "-"
        val count = collectionCounts[type]
        return when {
            count != null -> count.toString()
            isCountsLoading -> "…"
            else -> "0"
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "我的追番与收藏",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

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
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            text = "未登录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 第一排：三大活跃追番状态（在看、想看、看过）
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CollectionStatusItem(
                    label = "在看",
                    tag = "追番中",
                    count = formatCount(CollectionType.DOING),
                    icon = Icons.Filled.PlayCircleOutline,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { onCollectionClick(CollectionType.DOING) },
                    modifier = Modifier.weight(1f),
                )
                CollectionStatusItem(
                    label = "想看",
                    tag = "愿望单",
                    count = formatCount(CollectionType.WISH),
                    icon = Icons.Filled.BookmarkBorder,
                    tint = WishOrange,
                    onClick = { onCollectionClick(CollectionType.WISH) },
                    modifier = Modifier.weight(1f),
                )
                CollectionStatusItem(
                    label = "看过",
                    tag = "已完成",
                    count = formatCount(CollectionType.COLLECT),
                    icon = Icons.Filled.CheckCircleOutline,
                    tint = ActionCollect,
                    onClick = { onCollectionClick(CollectionType.COLLECT) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 第二排：两大归档状态（搁置、抛弃）
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CollectionStatusItem(
                    label = "搁置",
                    tag = null,
                    count = formatCount(CollectionType.ON_HOLD),
                    icon = Icons.Filled.PauseCircleOutline,
                    tint = MaterialTheme.colorScheme.outline,
                    onClick = { onCollectionClick(CollectionType.ON_HOLD) },
                    modifier = Modifier.weight(1f),
                )
                CollectionStatusItem(
                    label = "抛弃",
                    tag = null,
                    count = formatCount(CollectionType.DROPPED),
                    icon = Icons.Filled.Cancel,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    onClick = { onCollectionClick(CollectionType.DROPPED) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 贴心功能引导（替代草稿占位文案）
            Text(
                text =
                    if (isLoggedIn) {
                        "💡 点击任意分类可直达条目列表、查看打卡进度并支持多维度筛选"
                    } else {
                        "💡 登录 Bangumi 账号后，即可一键实时同步全量在看、想看与评分记录"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun CollectionStatusItem(
    label: String,
    tag: String?,
    count: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.12f),
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tag != null) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "· $tag",
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                }
            }
        }
    }
}
