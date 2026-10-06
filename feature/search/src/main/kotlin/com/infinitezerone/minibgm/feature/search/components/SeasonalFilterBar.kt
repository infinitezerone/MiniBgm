package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.search.SeasonAiringScope
import com.infinitezerone.minibgm.feature.search.SeasonFormFilter
import com.infinitezerone.minibgm.feature.search.SeasonOriginFilter
import com.infinitezerone.minibgm.feature.search.SeasonSortOption
import com.infinitezerone.minibgm.feature.search.SeasonalGuideUiState
import com.infinitezerone.minibgm.feature.search.SeasonalViewMode

/**
 * 季度片单顶部常驻与可展开筛选条：
 * 档期胶囊 + 当前筛选摘要（展开产地/形式/排序） + 视图形态切换（紧凑列表/海报网格） + 常用标签横滑栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonalFilterBar(
    uiState: SeasonalGuideUiState,
    filterExpanded: Boolean,
    onToggleFilterExpanded: () -> Unit,
    onOpenSeasonPicker: () -> Unit,
    onToggleViewMode: () -> Unit,
    onSelectOrigin: (SeasonOriginFilter) -> Unit,
    onSelectForm: (SeasonFormFilter) -> Unit,
    onSelectSort: (SeasonSortOption) -> Unit,
    onToggleTag: (String) -> Unit,
    onClearSelectedTags: () -> Unit,
    onAddCustomTag: (String) -> Unit,
    onRemoveCustomTag: (String) -> Unit,
    onToggleFavoriteTag: (String) -> Unit = {},
    onTogglePurifyContent: () -> Unit = {},
    onSelectAiringScope: (SeasonAiringScope) -> Unit = {},
    onIncludeTag: (String) -> Unit = onToggleTag,
    onExcludeTag: (String) -> Unit = onToggleTag,
    onRemoveTag: (String) -> Unit = onToggleTag,
    modifier: Modifier = Modifier,
) {
    var showAddTagSheet by remember { mutableStateOf(false) }
    val addTagSheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 1. 复合档期选择胶囊 [ 2026 · 10月秋 ▾ ]
            Surface(
                onClick = onOpenSeasonPicker,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.height(36.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.CalendarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "${uiState.selectedYear} · ${uiState.selectedQuarter.displayLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = BgmIcons.KeyboardArrowDown,
                        contentDescription = "选择档期",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 2. 筛选摘要：收起时以一行文字交代"现在筛的是什么"，点它展开二级 chips
            Surface(
                onClick = onToggleFilterExpanded,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier =
                    Modifier
                        .height(36.dp)
                        .weight(1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = uiState.filterSummary,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector =
                            if (filterExpanded) {
                                BgmIcons.KeyboardArrowUp
                            } else {
                                BgmIcons.KeyboardArrowDown
                            },
                        contentDescription = if (filterExpanded) "收起筛选" else "展开筛选",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 3. 视图形态切换（紧凑列表 ↔ 海报网格）
            IconButton(
                onClick = onToggleViewMode,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector =
                        if (uiState.viewMode == SeasonalViewMode.LIST) {
                            BgmIcons.GridView
                        } else {
                            BgmIcons.ViewList
                        },
                    contentDescription =
                        if (uiState.viewMode == SeasonalViewMode.LIST) {
                            "切换为海报网格"
                        } else {
                            "切换为紧凑列表"
                        },
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }

        // 4. AniList 核心题材与热门标签横滑选择条（常驻展示，点击即下推筛选）
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 全部 胶囊
            SeasonalGuideFilterChip(
                label = "全部",
                selected = uiState.selectedTags.isEmpty() && uiState.excludedTags.isEmpty(),
                onClick = onClearSelectedTags,
            )

            // 全部标签与即时搜索展开入口（前置常驻，免去向右滑动到底的痛点）
            val activeTagsCount = uiState.selectedTags.size + uiState.excludedTags.size
            Surface(
                onClick = { showAddTagSheet = true },
                shape = RoundedCornerShape(8.dp),
                color =
                    if (activeTagsCount > 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                modifier = Modifier.height(34.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.FilterList,
                        contentDescription = "全部题材与标签筛选",
                        modifier = Modifier.size(15.dp),
                        tint =
                            if (activeTagsCount > 0) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                    Text(
                        text = if (activeTagsCount > 0) "全部标签 ($activeTagsCount)" else "全部标签",
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (activeTagsCount > 0) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // 1. 常驻展示用户的偏好标签（若有）
            uiState.customFilterTags.forEach { tag ->
                val chipState =
                    when {
                        tag in uiState.selectedTags -> SeasonalTagFilterState.INCLUDED
                        tag in uiState.excludedTags -> SeasonalTagFilterState.EXCLUDED
                        else -> SeasonalTagFilterState.NEUTRAL
                    }
                SeasonalGuideTriStateFilterChip(
                    label = "★ $tag",
                    state = chipState,
                    onClick = { onToggleTag(tag) },
                )
            }

            // 2. 动态展示本季高频热门题材（优先核心大类，前 8 个）
            val topQuickTags =
                (uiState.seasonalGenres.map { it.first } + uiState.seasonalHotTags.map { it.first })
                    .filter { it !in uiState.customFilterTags }
                    .distinct()
                    .take(8)
            topQuickTags.forEach { tag ->
                val chipState =
                    when {
                        tag in uiState.selectedTags -> SeasonalTagFilterState.INCLUDED
                        tag in uiState.excludedTags -> SeasonalTagFilterState.EXCLUDED
                        else -> SeasonalTagFilterState.NEUTRAL
                    }
                SeasonalGuideTriStateFilterChip(
                    label = tag,
                    state = chipState,
                    onClick = { onToggleTag(tag) },
                )
            }

            // 3. 当前已激活但不在上述快捷项中的其它已选标签（包含或排除）
            val extraActiveTags =
                (uiState.selectedTags + uiState.excludedTags)
                    .filter { it !in uiState.customFilterTags && it !in topQuickTags }
            extraActiveTags.forEach { tag ->
                val chipState =
                    when {
                        tag in uiState.selectedTags -> SeasonalTagFilterState.INCLUDED
                        tag in uiState.excludedTags -> SeasonalTagFilterState.EXCLUDED
                        else -> SeasonalTagFilterState.NEUTRAL
                    }
                SeasonalGuideTriStateFilterChip(
                    label = tag,
                    state = chipState,
                    onClick = { onToggleTag(tag) },
                )
            }

            // ＋更多题材/标签
            Surface(
                onClick = { showAddTagSheet = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.height(32.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.Add,
                        contentDescription = "更多题材与标签",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "更多",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        AnimatedVisibility(visible = filterExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 当季放送范围：全部在播 / 仅首播新番 / 仅跨季续播（仅在当季生效展示）
                if (uiState.isCurrentSeason) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SeasonAiringScope.entries.forEach { scope ->
                            SeasonalGuideFilterChip(
                                label = scope.label,
                                selected = uiState.selectedAiringScope == scope,
                                onClick = { onSelectAiringScope(scope) },
                            )
                        }
                    }
                }

                // 一级筛选：产地（单选）
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SeasonOriginFilter.entries.forEach { origin ->
                        SeasonalGuideFilterChip(
                            label = origin.label,
                            selected = uiState.selectedOrigin == origin,
                            onClick = { onSelectOrigin(origin) },
                        )
                    }
                }

                // 二级筛选：放送形式（全部 / 剧场版）
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SeasonFormFilter.entries.forEach { form ->
                        SeasonalGuideFilterChip(
                            label = form.label,
                            selected = uiState.selectedForm == form,
                            onClick = { onSelectForm(form) },
                        )
                    }
                }

                // 排序（单选）
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SeasonSortOption.entries.forEach { sort ->
                        SeasonalGuideFilterChip(
                            label = sort.label,
                            selected = uiState.selectedSort == sort,
                            onClick = { onSelectSort(sort) },
                        )
                    }
                }

                // 内容净化（折叠短片、MV、泡面番、动态漫等）
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SeasonalGuideFilterChip(
                        label = if (uiState.purifyContent) "净化已开启（折叠短片/泡面）" else "内容净化（全部平铺）",
                        selected = uiState.purifyContent,
                        onClick = onTogglePurifyContent,
                    )
                }
            }
        }
    }

    if (showAddTagSheet) {
        AddSeasonalTagBottomSheet(
            sheetState = addTagSheetState,
            selectedTags = uiState.selectedTags,
            excludedTags = uiState.excludedTags,
            seasonalGenres = uiState.seasonalGenres,
            seasonalHotTags = uiState.seasonalHotTags,
            customFilterTags = uiState.customFilterTags,
            onToggleTag = onToggleTag,
            onIncludeTag = onIncludeTag,
            onExcludeTag = onExcludeTag,
            onRemoveTag = onRemoveTag,
            onClearSelectedTags = onClearSelectedTags,
            onAddCustomTag = onAddCustomTag,
            onRemoveCustomTag = onRemoveCustomTag,
            onToggleFavoriteTag = onToggleFavoriteTag,
            onDismiss = { showAddTagSheet = false },
        )
    }
}
