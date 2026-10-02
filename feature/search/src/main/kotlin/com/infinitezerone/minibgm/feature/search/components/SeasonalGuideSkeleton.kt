package com.infinitezerone.minibgm.feature.search.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonBox
import com.infinitezerone.minibgm.core.designsystem.component.rememberSkeletonState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.search.SeasonQuarter

/** 骨架屏加载列表（行式：58dp 封面块 + 三根文本条，与真实行等高） */
@Composable
fun SeasonalGuideSkeletonList(modifier: Modifier = Modifier) {
    val skeletonState = rememberSkeletonState()
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
        modifier = modifier,
    ) {
        items(6) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(
                    modifier =
                        Modifier
                            .width(58.dp)
                            .height(83.dp),
                    shape = RoundedCornerShape(8.dp),
                    state = skeletonState,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SkeletonBox(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.9f)
                                .height(16.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                    SkeletonBox(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.55f)
                                .height(14.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                    SkeletonBox(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.35f)
                                .height(12.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                }
            }
        }
    }
}

/** 骨架屏加载网格 */
@Composable
fun SeasonalGuideSkeletonGrid(modifier: Modifier = Modifier) {
    val skeletonState = rememberSkeletonState()
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600
    val gridColumns =
        if (isWideScreen) {
            GridCells.Adaptive(minSize = 110.dp)
        } else {
            GridCells.Fixed(3)
        }
    LazyVerticalGrid(
        columns = gridColumns,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
        modifier = modifier,
    ) {
        items(9) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
            ) {
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(165.dp),
                    shape = RoundedCornerShape(8.dp),
                    state = skeletonState,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.85f)
                            .height(16.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
                Spacer(modifier = Modifier.height(4.dp))
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
            }
        }
    }
}

/** 错误提示与重试状态 */
@Composable
fun SeasonalGuideErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = BgmIcons.RefreshBorder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Text("重新加载")
            }
        }
    }
}

/** 空数据提示状态 */
@Composable
fun SeasonalGuideEmptyState(
    selectedYear: Int,
    selectedQuarter: SeasonQuarter,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = BgmIcons.CalendarBorder,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = "${selectedYear}年 ${selectedQuarter.displayLabel} 暂无收录番剧",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "可尝试切换年份或季度查看其他番剧导视",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 导视筛选 chip。
 */
@Composable
fun SeasonalGuideFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        modifier = modifier.height(34.dp),
        border = null,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
    )
}
