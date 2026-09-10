package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.feature.search.DEFAULT_SEASONS
import com.infinitezerone.minibgm.feature.search.ExploreCategory
import com.infinitezerone.minibgm.feature.search.ExploreSort
import com.infinitezerone.minibgm.feature.search.SeasonOption
import com.infinitezerone.minibgm.feature.search.TAG_GROUPS
import com.infinitezerone.minibgm.feature.search.TimeCategory

/**
 * 高级多维筛选半屏抽屉（Material 3 ModalBottomSheet）：
 * - 支持多标签自由组合过滤（如 科幻 + 悬疑、京阿尼 + 日常）；
 * - 标签采用多行 FlowRow 自然排布，分类清晰；
 * - 包含条目分类、播出时间/年代、题材/厂牌/受众三组多维标签矩阵与自定义输入；
 * - 顶部提供已选标签清单与一键清除。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreFilterBottomSheet(
    sheetState: SheetState,
    selectedSeason: SeasonOption,
    onSeasonSelect: (SeasonOption) -> Unit,
    selectedCategory: ExploreCategory,
    onCategorySelect: (ExploreCategory) -> Unit,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit,
    onClearAllTags: () -> Unit,
    onCustomTagSubmit: (String) -> Unit,
    selectedSort: ExploreSort,
    onSortSelect: (ExploreSort) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var customTagText by remember { mutableStateOf("") }
    var selectedTimeCategory by remember { mutableStateOf(selectedSeason.category) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = 600.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
        ) {
            // 1. 顶部标题栏与重置按钮
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🎯 多维深度筛选",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                TextButton(onClick = onResetAll) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重置筛选", style = MaterialTheme.typography.labelMedium)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 2. 筛选内容滚动列表
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Section A: 排序规则
                FilterSection(title = "↕️ 排序规则") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExploreSort.entries.forEach { sort ->
                            FilterChip(
                                selected = selectedSort == sort,
                                onClick = { onSortSelect(sort) },
                                label = { Text(text = sort.label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                            )
                        }
                    }
                }

                // Section B: 媒介分类
                FilterSection(title = "📁 媒介分类") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExploreCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { onCategorySelect(category) },
                                label = { Text(text = category.label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }

                // Section C: 播出时间与年代范围
                FilterSection(title = "📅 播出时间与年代") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 时间分类切换药丸
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TimeCategory.entries.forEach { cat ->
                                val isSelected = selectedTimeCategory == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTimeCategory = cat
                                        val firstOfCategory = DEFAULT_SEASONS.firstOrNull { it.category == cat }
                                        if (firstOfCategory != null) {
                                            onSeasonSelect(firstOfCategory)
                                        }
                                    },
                                    label = { Text(text = cat.label, style = MaterialTheme.typography.labelSmall) },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        ),
                                )
                            }
                        }

                        // 对应分类下的具体时间选项
                        val visibleSeasons = DEFAULT_SEASONS.filter { it.category == selectedTimeCategory }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            visibleSeasons.forEach { season ->
                                FilterChip(
                                    selected = selectedSeason.id == season.id,
                                    onClick = { onSeasonSelect(season) },
                                    label = { Text(text = season.label, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                }

                // Section D: 已生效标签总览与快捷清除
                if (selectedTags.isNotEmpty()) {
                    FilterSection(
                        title = "🏷️ 已选组合标签 (${selectedTags.size})",
                        trailing = {
                            TextButton(onClick = onClearAllTags) {
                                Text("清空标签", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            selectedTags.forEach { tag ->
                                Surface(
                                    onClick = { onTagToggle(tag) },
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "移除",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section E: 标签多维矩阵（支持多选组合）
                TAG_GROUPS.forEach { group ->
                    FilterSection(title = group.name) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            group.tags.forEach { tag ->
                                val isSelected = tag in selectedTags
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onTagToggle(tag) },
                                    label = { Text(text = tag, style = MaterialTheme.typography.labelSmall) },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                )
                            }
                        }
                    }
                }

                // Section F: 自定义标签精准输入
                FilterSection(title = "➕ 自定义特色标签") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = customTagText,
                            onValueChange = { customTagText = it },
                            placeholder = {
                                Text(
                                    "输入任意 Bangumi 标签（如 赛博朋克、机娘、芳文社）",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions =
                                KeyboardActions(
                                    onDone = {
                                        if (customTagText.isNotBlank()) {
                                            onCustomTagSubmit(customTagText)
                                            customTagText = ""
                                        }
                                    },
                                ),
                            modifier = Modifier.weight(1f),
                        )

                        Button(
                            onClick = {
                                if (customTagText.isNotBlank()) {
                                    onCustomTagSubmit(customTagText)
                                    customTagText = ""
                                }
                            },
                            enabled = customTagText.isNotBlank(),
                        ) {
                            Text("添加")
                        }
                    }
                }
            }

            // 3. 底部完成按钮
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) {
                Text("查看发现结果")
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            trailing?.invoke()
        }
        content()
    }
}
