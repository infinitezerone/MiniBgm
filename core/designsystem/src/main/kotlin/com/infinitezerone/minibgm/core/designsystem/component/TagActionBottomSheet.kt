package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons

/**
 * 点击题材/分类标签时的快捷操作底栏：
 * 1. 以此标签筛选当季番剧（支持激活/反选）；
 * 2. 设为我的常用标签（添加/移除）；
 * 3. 浏览全站该标签下的所有作品。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagActionBottomSheet(
    tag: String,
    isFavorite: Boolean,
    sheetState: SheetState,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isFiltered: Boolean = false,
    onToggleFilter: (() -> Unit)? = null,
    onViewAllWithTag: (() -> Unit)? = null,
) {
    BgmModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "#$tag",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(4.dp))

            // 操作 1: 筛选当季
            if (onToggleFilter != null) {
                TagActionRow(
                    icon = BgmIcons.FilterList,
                    title = if (isFiltered) "取消在当季片单中的筛选" else "在当季片单中以此标签筛选",
                    onClick = {
                        onToggleFilter()
                        onDismiss()
                    },
                )
            }

            // 操作 2: 收藏常用（来源 2 随看随加）
            TagActionRow(
                icon = if (isFavorite) BgmIcons.Bookmark else BgmIcons.BookmarkBorder,
                title = if (isFavorite) "从常用筛选标签中移除" else "添加到我的常用筛选标签",
                subtitle = if (isFavorite) "已在您的常用标签列表中" else "添加后将在季度片单与淘番榜单常驻展示",
                onClick = {
                    onToggleFavorite()
                    onDismiss()
                },
            )

            // 操作 3: 浏览全域标签
            if (onViewAllWithTag != null) {
                TagActionRow(
                    icon = BgmIcons.SearchBorder,
                    title = "浏览「$tag」全站相关作品",
                    onClick = {
                        onViewAllWithTag()
                        onDismiss()
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TagActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
