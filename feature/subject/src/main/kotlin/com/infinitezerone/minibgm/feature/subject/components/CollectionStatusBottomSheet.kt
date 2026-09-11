package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.infinitezerone.minibgm.core.model.UserCollection
import kotlin.math.roundToInt

/** 收藏状态 BottomSheet：单选状态、1~10 评分器、私密开关、短评输入 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CollectionStatusBottomSheet(
    currentCollection: UserCollection?,
    subjectType: SubjectType,
    onDismiss: () -> Unit,
    onSave: (type: CollectionType, rate: Int?, comment: String?, private: Boolean) -> Unit,
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    var selectedType by rememberSaveable {
        mutableStateOf(
            currentCollection?.type?.let { CollectionType.fromValue(it) } ?: CollectionType.DOING,
        )
    }
    var rating by rememberSaveable { mutableIntStateOf(currentCollection?.rate ?: 0) }
    var comment by rememberSaveable { mutableStateOf(currentCollection?.comment.orEmpty()) }
    var isPrivate by rememberSaveable { mutableStateOf(false) }

    BgmModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
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
                            onClick = { selectedType = type },
                            label = { Text(text = type.getVerb(subjectType)) },
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

            // 2. 评分打分器 (1~10 分)
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

            // 3. 私密收藏开关
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

            // 4. 短评输入框
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("简评 / 吐槽") },
                placeholder = { Text("写下你的追番感想或评价...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            // 5. 底部操作按钮
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
                        onSave(
                            selectedType,
                            if (rating > 0) rating else null,
                            comment.ifBlank { null },
                            isPrivate,
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
