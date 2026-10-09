package com.infinitezerone.minibgm.feature.search.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.search.R
import com.infinitezerone.minibgm.feature.search.SeasonAiringScope
import com.infinitezerone.minibgm.feature.search.SeasonFormFilter
import com.infinitezerone.minibgm.feature.search.SeasonOriginFilter
import com.infinitezerone.minibgm.feature.search.SeasonSortOption
import com.infinitezerone.minibgm.feature.search.SeasonalGuideUiState
import com.infinitezerone.minibgm.feature.search.SeasonalViewMode
import com.infinitezerone.minibgm.feature.search.filterSummary

/**
 * 季度片单顶部常驻筛选条（单行）：
 * 档期胶囊 + 当前筛选摘要（点开筛选抽屉） + 视图形态切换（紧凑列表/海报网格）。
 * 全部次要维度（放送范围/产地/形式/排序/净化/题材标签）收纳进半屏抽屉，把纵向空间还给片单本体。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonalFilterBar(
    uiState: SeasonalGuideUiState,
    onOpenSeasonPicker: () -> Unit,
    onToggleViewMode: () -> Unit,
    onSelectOrigin: (SeasonOriginFilter) -> Unit,
    onSelectForm: (SeasonFormFilter) -> Unit,
    onSelectSort: (SeasonSortOption) -> Unit,
    onToggleTag: (String) -> Unit,
    onIncludeTag: (String) -> Unit = onToggleTag,
    onExcludeTag: (String) -> Unit = onToggleTag,
    onRemoveTag: (String) -> Unit = onToggleTag,
    onClearSelectedTags: () -> Unit,
    onAddCustomTag: (String) -> Unit,
    onRemoveCustomTag: (String) -> Unit,
    onToggleFavoriteTag: (String) -> Unit = {},
    onTogglePurifyContent: () -> Unit = {},
    onSelectAiringScope: (SeasonAiringScope) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    var showAddTagSheet by remember { mutableStateOf(false) }
    val filterSheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
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
                        text =
                            stringResource(
                                R.string.feature_search_season_pill_format,
                                uiState.selectedYear,
                                stringResource(uiState.selectedQuarter.displayLabelRes),
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = BgmIcons.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.feature_search_cd_select_season),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 2. 筛选摘要：一行交代"现在筛的是什么"，点它打开筛选抽屉
            Surface(
                onClick = { showFilterSheet = true },
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
                        text = uiState.filterSummary(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = BgmIcons.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.feature_search_cd_open_filter_sheet),
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
                        stringResource(
                            if (uiState.viewMode == SeasonalViewMode.LIST) {
                                R.string.feature_search_cd_switch_to_grid
                            } else {
                                R.string.feature_search_cd_switch_to_compact_list
                            },
                        ),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }

    if (showFilterSheet) {
        SeasonalFilterOptionsBottomSheet(
            uiState = uiState,
            onSelectAiringScope = onSelectAiringScope,
            onSelectOrigin = onSelectOrigin,
            onSelectForm = onSelectForm,
            onSelectSort = onSelectSort,
            onTogglePurifyContent = onTogglePurifyContent,
            onOpenTagManager = {
                showFilterSheet = false
                showAddTagSheet = true
            },
            onDismiss = { showFilterSheet = false },
        )
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
