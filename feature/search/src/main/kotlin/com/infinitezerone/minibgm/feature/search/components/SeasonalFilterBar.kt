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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.feature.search.SeasonFormFilter
import com.infinitezerone.minibgm.feature.search.SeasonOriginFilter
import com.infinitezerone.minibgm.feature.search.SeasonSortOption
import com.infinitezerone.minibgm.feature.search.SeasonalGuideUiState
import com.infinitezerone.minibgm.feature.search.SeasonalViewMode

/**
 * 季度片单顶部常驻与可展开筛选条：
 * 档期胶囊 + 当前筛选摘要（展开产地/形式/排序） + 视图形态切换（紧凑列表/海报网格）
 */
@Composable
fun SeasonalFilterBar(
    uiState: SeasonalGuideUiState,
    filterExpanded: Boolean,
    onToggleFilterExpanded: () -> Unit,
    onOpenSeasonPicker: () -> Unit,
    onToggleViewMode: () -> Unit,
    onSelectOrigin: (SeasonOriginFilter) -> Unit,
    onToggleForm: (SeasonFormFilter) -> Unit,
    onSelectSort: (SeasonSortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.height(36.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
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
                        imageVector = Icons.Filled.KeyboardArrowDown,
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
                shape = RoundedCornerShape(10.dp),
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
                        imageVector = Icons.Filled.FilterList,
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
                                Icons.Filled.KeyboardArrowUp
                            } else {
                                Icons.Filled.KeyboardArrowDown
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
                            Icons.Filled.GridView
                        } else {
                            Icons.AutoMirrored.Filled.ViewList
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

        AnimatedVisibility(visible = filterExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

                // 二级筛选：放送形式（可多选）
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SeasonFormFilter.entries.forEach { form ->
                        SeasonalGuideFilterChip(
                            label = form.label,
                            selected = form in uiState.selectedForms,
                            onClick = { onToggleForm(form) },
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
            }
        }
    }
}
