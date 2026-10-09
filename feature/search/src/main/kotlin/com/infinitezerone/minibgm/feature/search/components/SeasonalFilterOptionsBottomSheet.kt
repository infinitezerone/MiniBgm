package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.search.R
import com.infinitezerone.minibgm.feature.search.SeasonAiringScope
import com.infinitezerone.minibgm.feature.search.SeasonFormFilter
import com.infinitezerone.minibgm.feature.search.SeasonOriginFilter
import com.infinitezerone.minibgm.feature.search.SeasonSortOption
import com.infinitezerone.minibgm.feature.search.SeasonalGuideUiState

/**
 * 季度片单筛选半屏抽屉：
 * 收纳放送范围 / 产地 / 形式 / 排序 / 内容净化与题材标签入口，
 * 让常驻筛选条收敛为一行，把纵向空间还给片单本体。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonalFilterOptionsBottomSheet(
    uiState: SeasonalGuideUiState,
    onSelectAiringScope: (SeasonAiringScope) -> Unit,
    onSelectOrigin: (SeasonOriginFilter) -> Unit,
    onSelectForm: (SeasonFormFilter) -> Unit,
    onSelectSort: (SeasonSortOption) -> Unit,
    onTogglePurifyContent: () -> Unit,
    onOpenTagManager: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)

    BgmModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.feature_search_filter_sheet_short_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.feature_search_action_done),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // 当季放送范围：全部在播 / 仅首播新番 / 仅跨季续播（仅在当季生效展示）
            if (uiState.isCurrentSeason) {
                FilterOptionSection(label = stringResource(R.string.feature_search_filter_section_airing_scope)) {
                    SeasonAiringScope.entries.forEach { scope ->
                        SeasonalGuideFilterChip(
                            label = stringResource(scope.labelRes),
                            selected = uiState.selectedAiringScope == scope,
                            onClick = { onSelectAiringScope(scope) },
                        )
                    }
                }
            }

            FilterOptionSection(label = stringResource(R.string.feature_search_filter_section_origin)) {
                SeasonOriginFilter.entries.forEach { origin ->
                    SeasonalGuideFilterChip(
                        label = stringResource(origin.labelRes),
                        selected = uiState.selectedOrigin == origin,
                        onClick = { onSelectOrigin(origin) },
                    )
                }
            }

            FilterOptionSection(label = stringResource(R.string.feature_search_filter_section_form)) {
                SeasonFormFilter.entries.forEach { form ->
                    SeasonalGuideFilterChip(
                        label = stringResource(form.labelRes),
                        selected = uiState.selectedForm == form,
                        onClick = { onSelectForm(form) },
                    )
                }
            }

            FilterOptionSection(label = stringResource(R.string.feature_search_filter_section_sort)) {
                SeasonSortOption.entries.forEach { sort ->
                    SeasonalGuideFilterChip(
                        label = stringResource(sort.labelRes),
                        selected = uiState.selectedSort == sort,
                        onClick = { onSelectSort(sort) },
                    )
                }
            }

            FilterOptionSection(label = stringResource(R.string.feature_search_filter_section_browse)) {
                SeasonalGuideFilterChip(
                    label =
                        stringResource(
                            if (uiState.purifyContent) {
                                R.string.feature_search_purify_on
                            } else {
                                R.string.feature_search_purify_off
                            },
                        ),
                    selected = uiState.purifyContent,
                    onClick = onTogglePurifyContent,
                )
            }

            // 题材与标签管理入口：跳转既有标签抽屉（支持搜索与三态包含/排除）
            Surface(
                onClick = onOpenTagManager,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.feature_search_tags_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val activeCount = uiState.selectedTags.size + uiState.excludedTags.size
                        Text(
                            text =
                                if (activeCount > 0) {
                                    stringResource(
                                        R.string.feature_search_tags_summary,
                                        uiState.selectedTags.size,
                                        uiState.excludedTags.size,
                                    )
                                } else {
                                    stringResource(R.string.feature_search_tags_summary_empty)
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = BgmIcons.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.feature_search_cd_open_tag_manager),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterOptionSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}
