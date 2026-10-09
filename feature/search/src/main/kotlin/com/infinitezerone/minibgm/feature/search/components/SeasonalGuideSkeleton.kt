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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmStatusState
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonBox
import com.infinitezerone.minibgm.core.designsystem.component.rememberSkeletonState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.search.R
import com.infinitezerone.minibgm.feature.search.SeasonQuarter
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

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
    BgmStatusState(
        message = errorMessage,
        modifier = modifier.padding(24.dp),
        icon = BgmIcons.RefreshBorder,
        iconTint = MaterialTheme.colorScheme.error,
        actionLabel = stringResource(DesignSystemR.string.core_designsystem_action_retry),
        onAction = onRetry,
    )
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
                text =
                    stringResource(
                        R.string.feature_search_seasonal_empty_title,
                        selectedYear,
                        stringResource(selectedQuarter.displayLabelRes),
                    ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.feature_search_seasonal_empty_hint),
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

/** 导视标签三态：默认未选 (NEUTRAL) / 包含 (INCLUDED) / 排除避雷 (EXCLUDED) */
enum class SeasonalTagFilterState {
    NEUTRAL,
    INCLUDED,
    EXCLUDED,
}

/** 导视三态筛选 chip：支持未选、包含（主色）、排除避雷（红底），可选偏好收藏星标 */
@Composable
fun SeasonalGuideTriStateFilterChip(
    label: String,
    state: SeasonalTagFilterState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingCount: Int? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val (containerColor, labelColor, prefix) =
        when (state) {
            SeasonalTagFilterState.INCLUDED ->
                Triple(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    "✓ ",
                )
            SeasonalTagFilterState.EXCLUDED ->
                Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    "✕ ",
                )
            SeasonalTagFilterState.NEUTRAL ->
                Triple(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    "",
                )
        }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = modifier.height(34.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = if (onToggleFavorite != null) 4.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "$prefix$label${if (trailingCount != null) " · $trailingCount" else ""}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (state != SeasonalTagFilterState.NEUTRAL) FontWeight.Bold else FontWeight.Normal,
                color = labelColor,
            )
            if (onToggleFavorite != null) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = if (isFavorite) BgmIcons.Star else BgmIcons.StarBorder,
                        contentDescription =
                            stringResource(
                                if (isFavorite) {
                                    R.string.feature_search_cd_remove_favorite
                                } else {
                                    R.string.feature_search_cd_add_favorite
                                },
                            ),
                        tint =
                            if (isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                labelColor.copy(alpha = 0.5f)
                            },
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}
