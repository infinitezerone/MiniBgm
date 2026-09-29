package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.model.Tag
import com.infinitezerone.minibgm.core.model.UserCollection
import kotlin.math.roundToInt

/** 收藏状态 BottomSheet：单选状态、章节进度步进器、1~10 评分器、自定义与热门标签、私密开关、短评输入 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CollectionStatusBottomSheet(
    currentCollection: UserCollection?,
    subjectType: SubjectType,
    totalEpisodes: Int = 0,
    popularTags: List<Tag> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        type: CollectionType,
        rate: Int?,
        comment: String?,
        private: Boolean,
        epStatus: Int?,
        tags: List<String>?,
    ) -> Unit,
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    var selectedType by rememberSaveable {
        mutableStateOf(
            currentCollection?.type?.let { CollectionType.fromValue(it) } ?: CollectionType.DOING,
        )
    }
    var epStatus by rememberSaveable { mutableIntStateOf(currentCollection?.epStatus ?: 0) }
    var rating by rememberSaveable { mutableIntStateOf(currentCollection?.rate ?: 0) }
    var comment by rememberSaveable { mutableStateOf(currentCollection?.comment.orEmpty()) }
    var tagsText by rememberSaveable {
        mutableStateOf(currentCollection?.tags?.joinToString(" ").orEmpty())
    }
    var isPrivate by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    BgmModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "标记条目状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // 1. 收藏状态单选
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "收藏类型",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CollectionType.entries.forEach { type ->
                        val isSelected = selectedType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedType = type
                                if (type == CollectionType.COLLECT && totalEpisodes > 0 && epStatus == 0) {
                                    epStatus = totalEpisodes
                                }
                            },
                            label = { Text(text = type.getVerb(subjectType)) },
                            border = null,
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            leadingIcon =
                                if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                        )
                    }
                }
            }

            // 2. 章节进度步进调节器 (仅对有剧集/章节的条目展示)
            if (subjectType == SubjectType.ANIME ||
                subjectType == SubjectType.REAL ||
                subjectType == SubjectType.BOOK ||
                totalEpisodes > 0
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (subjectType == SubjectType.BOOK) "阅读进度" else "收看进度",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (totalEpisodes > 0 && epStatus < totalEpisodes) {
                            TextButton(
                                onClick = { epStatus = totalEpisodes },
                                modifier = Modifier.padding(0.dp),
                            ) {
                                Text("全看完了", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        IconButton(
                            onClick = { if (epStatus > 0) epStatus-- },
                            enabled = epStatus > 0,
                        ) {
                            Icon(imageVector = Icons.Filled.Remove, contentDescription = "减一集")
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "第 $epStatus 话",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (totalEpisodes > 0) {
                                Text(
                                    text = "全 $totalEpisodes 话",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = {
                                if (totalEpisodes <= 0 || epStatus < totalEpisodes) epStatus++
                            },
                            enabled = totalEpisodes <= 0 || epStatus < totalEpisodes,
                        ) {
                            Icon(imageVector = Icons.Filled.Add, contentDescription = "加一集")
                        }
                    }
                }
            }

            // 3. 评分打分器 (1~10 分)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "我的评分",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (rating == 0) "不评分" else "$rating 分 · ${getScoreLabel(rating)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (rating > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 快捷 1~10 星打分器
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    for (star in 1..10) {
                        IconButton(
                            onClick = { rating = if (rating == star) 0 else star },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "$star 分",
                                tint = if (star <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                Slider(
                    value = rating.toFloat(),
                    onValueChange = { rating = it.roundToInt() },
                    valueRange = 0f..10f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 4. 我的标签与热门标签
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "我的标签",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("标签 (空格分隔)") },
                    placeholder = { Text("例如：热血 奇幻 MAPPA") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (popularTags.isNotEmpty()) {
                    val activeTags = tagsText.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        popularTags.take(8).forEach { tag ->
                            val isSelected = tag.name in activeTags
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val updated =
                                        if (isSelected) {
                                            activeTags - tag.name
                                        } else {
                                            activeTags + tag.name
                                        }
                                    tagsText = updated.joinToString(" ")
                                },
                                label = { Text(text = tag.name, style = MaterialTheme.typography.labelSmall) },
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
                    }
                }
            }

            // 5. 私密收藏开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "仅自己可见 (私密收藏)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = isPrivate,
                    onCheckedChange = { isPrivate = it },
                )
            }

            // 6. 短评输入框
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("简评 / 吐槽") },
                placeholder = { Text("写下你的追番感想或评价...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            // 7. 底部操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "取消")
                }
                Button(
                    onClick = {
                        val parsedTags =
                            tagsText
                                .split("\\s+".toRegex())
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .takeIf { it.isNotEmpty() }
                        onSave(
                            selectedType,
                            if (rating > 0) rating else null,
                            comment.ifBlank { null },
                            isPrivate,
                            epStatus,
                            parsedTags,
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "保存")
                }
            }
        }
    }
}
