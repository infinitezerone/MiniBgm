package com.infinitezerone.minibgm.core.designsystem.component

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.R
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.model.Tag
import com.infinitezerone.minibgm.core.model.UserCollection
import kotlin.math.roundToInt

/** Bangumi 评分说明文案（1~10 分；0 分表示未评分） */
@Composable
private fun getScoreLabel(score: Int): String =
    when (score) {
        1 -> stringResource(R.string.core_designsystem_score_1)
        2 -> stringResource(R.string.core_designsystem_score_2)
        3 -> stringResource(R.string.core_designsystem_score_3)
        4 -> stringResource(R.string.core_designsystem_score_4)
        5 -> stringResource(R.string.core_designsystem_score_5)
        6 -> stringResource(R.string.core_designsystem_score_6)
        7 -> stringResource(R.string.core_designsystem_score_7)
        8 -> stringResource(R.string.core_designsystem_score_8)
        9 -> stringResource(R.string.core_designsystem_score_9)
        10 -> stringResource(R.string.core_designsystem_score_10)
        else -> stringResource(R.string.core_designsystem_collection_unrated)
    }

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
                text = stringResource(R.string.core_designsystem_collection_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // 1. 收藏状态单选
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.core_designsystem_collection_type_header),
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
                                            imageVector = BgmIcons.Check,
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
                            text =
                                if (subjectType == SubjectType.BOOK) {
                                    stringResource(R.string.core_designsystem_collection_progress_book)
                                } else {
                                    stringResource(R.string.core_designsystem_collection_progress_watch)
                                },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (totalEpisodes > 0 && epStatus < totalEpisodes) {
                            TextButton(
                                onClick = { epStatus = totalEpisodes },
                                modifier = Modifier.padding(0.dp),
                            ) {
                                Text(
                                    stringResource(R.string.core_designsystem_collection_action_all_watched),
                                    style = MaterialTheme.typography.labelSmall,
                                )
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
                            Icon(
                                imageVector = BgmIcons.Remove,
                                contentDescription = stringResource(R.string.core_designsystem_collection_action_dec_ep),
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.core_designsystem_collection_current_ep, epStatus),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (totalEpisodes > 0) {
                                Text(
                                    text = stringResource(R.string.core_designsystem_collection_total_ep, totalEpisodes),
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
                            Icon(
                                imageVector = BgmIcons.Add,
                                contentDescription = stringResource(R.string.core_designsystem_collection_action_inc_ep),
                            )
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
                        text = stringResource(R.string.core_designsystem_collection_my_rating),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            if (rating == 0) {
                                stringResource(R.string.core_designsystem_collection_unrated)
                            } else {
                                stringResource(R.string.core_designsystem_collection_rating_format, rating, getScoreLabel(rating))
                            },
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
                                imageVector = if (star <= rating) BgmIcons.Star else BgmIcons.StarBorder,
                                contentDescription = stringResource(R.string.core_designsystem_collection_star_cd, star),
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
                    text = stringResource(R.string.core_designsystem_collection_my_tags),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text(stringResource(R.string.core_designsystem_collection_tags_label)) },
                    placeholder = { Text(stringResource(R.string.core_designsystem_collection_tags_placeholder)) },
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
                        imageVector = BgmIcons.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.core_designsystem_collection_privacy_toggle),
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
                label = { Text(stringResource(R.string.core_designsystem_collection_comment_label)) },
                placeholder = { Text(stringResource(R.string.core_designsystem_collection_comment_placeholder)) },
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
                    Text(text = stringResource(R.string.core_designsystem_action_cancel))
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
                    Text(text = stringResource(R.string.core_designsystem_action_save))
                }
            }
        }
    }
}
