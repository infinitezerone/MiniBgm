package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons

/**
 * 季度片单题材与标签筛选抽屉：
 * 数据 100% 依当季数据动态呈现，客户端不硬编码任何静态题材与敏感分类标签。
 * 支持三态筛选（包含 ✓ / 排除避雷 ✕ / 不限）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddSeasonalTagBottomSheet(
    sheetState: SheetState,
    selectedTags: Set<String>,
    seasonalHotTags: List<Pair<String, Int>>,
    onToggleTag: (String) -> Unit,
    onClearSelectedTags: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    excludedTags: Set<String> = emptySet(),
    onIncludeTag: (String) -> Unit = onToggleTag,
    onExcludeTag: (String) -> Unit = onToggleTag,
    onRemoveTag: (String) -> Unit = onToggleTag,
    customFilterTags: List<String> = emptyList(),
    onAddCustomTag: (String) -> Unit = onToggleTag,
    onRemoveCustomTag: (String) -> Unit = onToggleTag,
) {
    var inputText by remember { mutableStateOf("") }
    val totalActiveCount = selectedTags.size + excludedTags.size

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
        ) {
            // 标题栏与清空操作
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "题材与标签筛选",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "点击切换：未选 → 包含(✓) → 排除(✕) → 未选",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (totalActiveCount > 0) {
                    TextButton(onClick = onClearSelectedTags) {
                        Text(
                            text = "清空全部 ($totalActiveCount)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Section 1: 当前已生效筛选（包含与排除）
                if (totalActiveCount > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "当前已生效筛选",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // 包含标签
                            selectedTags.forEach { tag ->
                                Surface(
                                    onClick = { onRemoveTag(tag) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "✓ #$tag",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Icon(
                                            imageVector = BgmIcons.Close,
                                            contentDescription = "取消包含",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }

                            // 排除标签（避雷）
                            excludedTags.forEach { tag ->
                                Surface(
                                    onClick = { onRemoveTag(tag) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "✕ #$tag",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Icon(
                                            imageVector = BgmIcons.Close,
                                            contentDescription = "取消排除",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 2: 当季高频题材与标签（由当季数据动态统计聚合，零硬编码）
                if (seasonalHotTags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "本季热门题材与标签",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            seasonalHotTags.forEach { (tag, count) ->
                                val chipState =
                                    when {
                                        tag in selectedTags -> SeasonalTagFilterState.INCLUDED
                                        tag in excludedTags -> SeasonalTagFilterState.EXCLUDED
                                        else -> SeasonalTagFilterState.NEUTRAL
                                    }
                                SeasonalGuideTriStateFilterChip(
                                    label = tag,
                                    state = chipState,
                                    onClick = { onToggleTag(tag) },
                                    trailingCount = count,
                                )
                            }
                        }
                    }
                }

                // Section 3: 自填标签检索与输入
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "自填标签检索",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = "输入标签检索词",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions =
                                KeyboardActions(
                                    onDone = {
                                        val trimmed = inputText.trim()
                                        if (trimmed.isNotBlank()) {
                                            onIncludeTag(trimmed)
                                            inputText = ""
                                        }
                                    },
                                ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        )

                        Button(
                            onClick = {
                                val trimmed = inputText.trim()
                                if (trimmed.isNotBlank()) {
                                    onIncludeTag(trimmed)
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("包含")
                        }

                        OutlinedButton(
                            onClick = {
                                val trimmed = inputText.trim()
                                if (trimmed.isNotBlank()) {
                                    onExcludeTag(trimmed)
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("排除")
                        }
                    }
                }
            }
        }
    }
}
