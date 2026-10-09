package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.search.ALL_TIME_SEASON
import com.infinitezerone.minibgm.feature.search.ExploreCategory
import com.infinitezerone.minibgm.feature.search.ExploreMood
import com.infinitezerone.minibgm.feature.search.ExploreSort
import com.infinitezerone.minibgm.feature.search.ExploreUiState
import com.infinitezerone.minibgm.feature.search.R
import com.infinitezerone.minibgm.feature.search.SeasonOption
import com.infinitezerone.minibgm.feature.search.customFilterSummary

/** 心境/场景快捷胶囊筛选栏（嵌入瀑布流网格时水平 4dp，与网格 12dp 内边距合成 16dp 视觉对齐） */
@Composable
fun MoodFilterRow(
    selectedMood: ExploreMood?,
    onMoodSelect: (ExploreMood) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(ExploreMood.entries) { mood ->
            FilterChip(
                selected = selectedMood == mood,
                onClick = { onMoodSelect(mood) },
                label = {
                    Text(
                        text = stringResource(mood.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedMood == mood) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                border = null,
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
    }
}

/**
 * 自定义筛选整合控制条：
 * - 默认展示收起态的紧凑单行摘要胶囊与清除全部按钮，高度仅 36dp，绝不挤压瀑布流视野；
 * - 点击摘要胶囊可平滑展开详细标签 Chips，支持单项移除；
 * - 列表滑动时自动收起，体验流畅。
 */
@Composable
fun ActiveCustomFilterBar(
    uiState: ExploreUiState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onClearSeason: () -> Unit,
    onClearCategory: () -> Unit,
    onTagToggle: (String) -> Unit,
    onClearSort: () -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                onClick = onToggleExpanded,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(36.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = uiState.customFilterSummary(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector =
                            if (expanded) {
                                BgmIcons.KeyboardArrowUp
                            } else {
                                BgmIcons.KeyboardArrowDown
                            },
                        contentDescription =
                            stringResource(
                                if (expanded) {
                                    R.string.feature_search_cd_collapse_tags
                                } else {
                                    R.string.feature_search_cd_expand_tags
                                },
                            ),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TextButton(
                onClick = onResetAll,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Text(
                    text = stringResource(R.string.feature_search_action_clear_all_filter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ActiveFilterPillRow(
                selectedSeason = uiState.selectedSeason,
                selectedCategory = uiState.selectedCategory,
                selectedTags = uiState.selectedTags,
                selectedSort = uiState.selectedSort,
                onClearSeason = onClearSeason,
                onClearCategory = onClearCategory,
                onTagToggle = onTagToggle,
                onClearSort = onClearSort,
                onResetAll = onResetAll,
                showResetButton = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 生效中的筛选条件快捷展示与一键清除栏 */
@Composable
fun ActiveFilterPillRow(
    selectedSeason: SeasonOption,
    selectedCategory: ExploreCategory,
    selectedTags: Set<String>,
    selectedSort: ExploreSort,
    onClearSeason: () -> Unit,
    onClearCategory: () -> Unit,
    onTagToggle: (String) -> Unit,
    onClearSort: () -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier,
    showResetButton: Boolean = true,
) {
    val hasFilters =
        selectedSeason != ALL_TIME_SEASON ||
            selectedTags.isNotEmpty() ||
            selectedCategory != ExploreCategory.ANIME ||
            selectedSort != ExploreSort.RANK

    if (!hasFilters) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (selectedSeason != ALL_TIME_SEASON) {
            item {
                ActiveFilterChip(
                    text = stringResource(selectedSeason.labelRes, *selectedSeason.labelArgs.toTypedArray()),
                    onClear = onClearSeason,
                )
            }
        }

        selectedTags.forEach { tag ->
            item(key = tag) {
                ActiveFilterChip(
                    text = "#$tag",
                    onClear = { onTagToggle(tag) },
                )
            }
        }

        if (selectedCategory != ExploreCategory.ANIME) {
            item {
                ActiveFilterChip(
                    text = stringResource(selectedCategory.labelRes),
                    onClear = onClearCategory,
                )
            }
        }

        if (selectedSort != ExploreSort.RANK) {
            item {
                ActiveFilterChip(
                    text = stringResource(selectedSort.labelRes),
                    onClear = onClearSort,
                )
            }
        }

        if (showResetButton) {
            item {
                TextButton(
                    onClick = onResetAll,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.feature_search_action_clear_all_filter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveFilterChip(
    text: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClear,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = BgmIcons.Close,
                contentDescription = stringResource(R.string.feature_search_cd_remove),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
