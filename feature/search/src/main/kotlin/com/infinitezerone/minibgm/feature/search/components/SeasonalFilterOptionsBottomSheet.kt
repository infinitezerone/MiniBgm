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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
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
                    text = "筛选与排序",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onDismiss) {
                    Text(text = "完成", style = MaterialTheme.typography.labelMedium)
                }
            }

            // 当季放送范围：全部在播 / 仅首播新番 / 仅跨季续播（仅在当季生效展示）
            if (uiState.isCurrentSeason) {
                FilterOptionSection(label = "放送范围") {
                    SeasonAiringScope.entries.forEach { scope ->
                        SeasonalGuideFilterChip(
                            label = scope.label,
                            selected = uiState.selectedAiringScope == scope,
                            onClick = { onSelectAiringScope(scope) },
                        )
                    }
                }
            }

            FilterOptionSection(label = "产地") {
                SeasonOriginFilter.entries.forEach { origin ->
                    SeasonalGuideFilterChip(
                        label = origin.label,
                        selected = uiState.selectedOrigin == origin,
                        onClick = { onSelectOrigin(origin) },
                    )
                }
            }

            FilterOptionSection(label = "放送形式") {
                SeasonFormFilter.entries.forEach { form ->
                    SeasonalGuideFilterChip(
                        label = form.label,
                        selected = uiState.selectedForm == form,
                        onClick = { onSelectForm(form) },
                    )
                }
            }

            FilterOptionSection(label = "排序") {
                SeasonSortOption.entries.forEach { sort ->
                    SeasonalGuideFilterChip(
                        label = sort.label,
                        selected = uiState.selectedSort == sort,
                        onClick = { onSelectSort(sort) },
                    )
                }
            }

            FilterOptionSection(label = "浏览体验") {
                SeasonalGuideFilterChip(
                    label = if (uiState.purifyContent) "净化已开启（折叠短片/泡面）" else "内容净化（全部平铺）",
                    selected = uiState.purifyContent,
                    onClick = onTogglePurifyContent,
                )
            }

            // 题材与标签管理入口：跳转既有标签抽屉（支持搜索与三态包含/排除）
            Surface(
                onClick = onOpenTagManager,
                shape = RoundedCornerShape(10.dp),
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
                            text = "题材与标签",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val activeCount = uiState.selectedTags.size + uiState.excludedTags.size
                        Text(
                            text =
                                if (activeCount > 0) {
                                    "已选 ${uiState.selectedTags.size} 个 · 排除 ${uiState.excludedTags.size} 个"
                                } else {
                                    "按题材与特色标签筛选（支持包含/排除）"
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = BgmIcons.KeyboardArrowDown,
                        contentDescription = "打开标签管理",
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
