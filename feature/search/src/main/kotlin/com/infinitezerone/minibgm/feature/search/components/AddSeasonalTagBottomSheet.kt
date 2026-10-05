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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons

/**
 * 经典阳光题材题材库（零敏感词、纯正向二次元分类）。
 */
val CLASSIC_GENRE_WHITELIST =
    listOf(
        "奇幻",
        "战斗",
        "热血",
        "恋爱",
        "日常",
        "科幻",
        "悬疑",
        "治愈",
        "搞笑",
        "校园",
        "冒险",
        "百合",
        "运动",
        "机战",
        "美食",
        "推理",
        "音乐",
        "原创",
        "漫画改",
        "轻小说改",
    )

/**
 * 常用筛选标签管理半屏抽屉：
 * 1. 自定义文本输入（自由敲词，敲回车或点击添加即存为常用）；
 * 2. 本季热门推荐（来自当季条目动态提取）；
 * 3. 经典题材推荐库（一键勾选直接存入常用）；
 * 4. 已保存常用标签列表（支持单项删除）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddSeasonalTagBottomSheet(
    sheetState: SheetState,
    customFilterTags: List<String>,
    seasonalHotTags: List<Pair<String, Int>>,
    onAddCustomTag: (String) -> Unit,
    onRemoveCustomTag: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var inputText by remember { mutableStateOf("") }

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
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "常用筛选标签管理",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
                // Section 1: 自由输入框
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "自填标签",
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
                                    text = "输入任意标签（如 百合、机战、芳文社）",
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
                                            onAddCustomTag(trimmed)
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
                                    onAddCustomTag(trimmed)
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("添加")
                        }
                    }
                }

                // Section 2: 当前已保存的常用标签
                if (customFilterTags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "已保存的常用标签 (${customFilterTags.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            customFilterTags.forEach { tag ->
                                Surface(
                                    onClick = { onRemoveCustomTag(tag) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Icon(
                                            imageVector = BgmIcons.Close,
                                            contentDescription = "移除",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: 当季热门推荐（动态提取）
                if (seasonalHotTags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "本季热门题材（点击直接加入常用）",
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
                                val alreadyAdded = customFilterTags.contains(tag)
                                FilterChip(
                                    selected = alreadyAdded,
                                    onClick = {
                                        if (alreadyAdded) {
                                            onRemoveCustomTag(tag)
                                        } else {
                                            onAddCustomTag(tag)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "$tag · $count",
                                            style = MaterialTheme.typography.labelSmall,
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
                                    shape = RoundedCornerShape(8.dp),
                                )
                            }
                        }
                    }
                }

                // Section 4: 经典题材白名单
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "经典题材分类（点击直接加入常用）",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CLASSIC_GENRE_WHITELIST.forEach { tag ->
                            val alreadyAdded = customFilterTags.contains(tag)
                            FilterChip(
                                selected = alreadyAdded,
                                onClick = {
                                    if (alreadyAdded) {
                                        onRemoveCustomTag(tag)
                                    } else {
                                        onAddCustomTag(tag)
                                    }
                                },
                                label = {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
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
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
