package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.search.R

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
    seasonalGenres: List<Pair<String, Int>> = emptyList(),
    excludedTags: Set<String> = emptySet(),
    onIncludeTag: (String) -> Unit = onToggleTag,
    onExcludeTag: (String) -> Unit = onToggleTag,
    onRemoveTag: (String) -> Unit = onToggleTag,
    customFilterTags: List<String> = emptyList(),
    onAddCustomTag: (String) -> Unit = onToggleTag,
    onRemoveCustomTag: (String) -> Unit = onToggleTag,
    onToggleFavoriteTag: (String) -> Unit = {},
) {
    var inputText by remember { mutableStateOf("") }
    val trimmedQuery = inputText.trim()
    val isFiltering = trimmedQuery.isNotBlank()

    val displayFavoriteTags =
        remember(customFilterTags, trimmedQuery) {
            if (trimmedQuery.isBlank()) {
                customFilterTags
            } else {
                customFilterTags.filter { it.contains(trimmedQuery, ignoreCase = true) }
            }
        }

    val displayGenres =
        remember(seasonalGenres, trimmedQuery) {
            if (trimmedQuery.isBlank()) {
                seasonalGenres
            } else {
                seasonalGenres.filter { it.first.contains(trimmedQuery, ignoreCase = true) }
            }
        }

    val displayHotTags =
        remember(seasonalHotTags, trimmedQuery) {
            if (trimmedQuery.isBlank()) {
                seasonalHotTags
            } else {
                seasonalHotTags.filter { it.first.contains(trimmedQuery, ignoreCase = true) }
            }
        }

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
                        text = stringResource(R.string.feature_search_tag_sheet_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.feature_search_tag_sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (totalActiveCount > 0) {
                    TextButton(onClick = onClearSelectedTags) {
                        Text(
                            text = stringResource(R.string.feature_search_action_clear_all_count, totalActiveCount),
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
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Section 1: 搜索框（置顶即时过滤 + 自填标签直接应用）
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
                                text = stringResource(R.string.feature_search_tag_search_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = BgmIcons.Search,
                                contentDescription = stringResource(R.string.feature_search_cd_search_tags),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            if (inputText.isNotBlank()) {
                                IconButton(onClick = { inputText = "" }) {
                                    Icon(
                                        imageVector = BgmIcons.Close,
                                        contentDescription = stringResource(R.string.feature_search_cd_clear_search),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
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
                        Text(stringResource(R.string.feature_search_action_include))
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
                        Text(stringResource(R.string.feature_search_action_exclude))
                    }
                }

                // Section 2: 当前已生效筛选（包含与排除）
                if (totalActiveCount > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.feature_search_active_filters_title),
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
                                            contentDescription = stringResource(R.string.feature_search_cd_cancel_include),
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
                                            contentDescription = stringResource(R.string.feature_search_cd_cancel_exclude),
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: ★ 我的常用偏好（持久化跨季收藏）
                if (displayFavoriteTags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.feature_search_favorite_prefs_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (isFiltering) {
                                Text(
                                    text = "(${displayFavoriteTags.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            displayFavoriteTags.forEach { tag ->
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
                                    isFavorite = true,
                                    onToggleFavorite = { onToggleFavoriteTag(tag) },
                                )
                            }
                        }
                    }
                }

                // Section 4: 核心题材分类（Genres 大类，数量精简明确）
                if (displayGenres.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.feature_search_genres_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (isFiltering) {
                                Text(
                                    text = "(${displayGenres.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            displayGenres.forEach { (genre, count) ->
                                val chipState =
                                    when {
                                        genre in selectedTags -> SeasonalTagFilterState.INCLUDED
                                        genre in excludedTags -> SeasonalTagFilterState.EXCLUDED
                                        else -> SeasonalTagFilterState.NEUTRAL
                                    }
                                SeasonalGuideTriStateFilterChip(
                                    label = genre,
                                    state = chipState,
                                    onClick = { onToggleTag(genre) },
                                    trailingCount = count,
                                    isFavorite = genre in customFilterTags,
                                    onToggleFavorite = { onToggleFavoriteTag(genre) },
                                )
                            }
                        }
                    }
                }

                // Section 5: 特色微观标签（Tags 小类，设定/元素细节）
                if (displayHotTags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.feature_search_hot_tags_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (isFiltering) {
                                Text(
                                    text = "(${displayHotTags.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            displayHotTags.forEach { (tag, count) ->
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
                                    isFavorite = tag in customFilterTags,
                                    onToggleFavorite = { onToggleFavoriteTag(tag) },
                                )
                            }
                        }
                    }
                }

                // 搜索无匹配提示
                if (isFiltering && displayFavoriteTags.isEmpty() && displayGenres.isEmpty() && displayHotTags.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.feature_search_tag_no_match),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
