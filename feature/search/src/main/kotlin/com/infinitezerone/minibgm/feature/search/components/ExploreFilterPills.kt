package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.feature.search.CURRENT_SEASON
import com.infinitezerone.minibgm.feature.search.ExploreCategory
import com.infinitezerone.minibgm.feature.search.ExploreMood
import com.infinitezerone.minibgm.feature.search.ExploreSort
import com.infinitezerone.minibgm.feature.search.SeasonOption

/** 心境/场景快捷胶囊筛选栏 */
@Composable
fun MoodFilterRow(
    selectedMood: ExploreMood?,
    onMoodSelect: (ExploreMood) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(ExploreMood.entries) { mood ->
            FilterChip(
                selected = selectedMood == mood,
                onClick = { onMoodSelect(mood) },
                label = {
                    Text(
                        text = mood.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedMood == mood) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
    }
}

/** 生效中的筛选条件快捷展示与一键清除栏 */
@Composable
fun ActiveFilterPillRow(
    selectedSeason: SeasonOption,
    selectedCategory: ExploreCategory,
    selectedTags: Set<String>,
    selectedSort: ExploreSort,
    onClearSeason: () -> Unit,
    onClearCategory: () -> Unit,
    onTagToggle: (String) -> Unit,
    onClearSort: () -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (selectedSeason != CURRENT_SEASON) {
            item {
                ActiveFilterChip(
                    text = selectedSeason.label,
                    onClear = onClearSeason,
                )
            }
        }

        selectedTags.forEach { tag ->
            item(key = tag) {
                ActiveFilterChip(
                    text = "#$tag",
                    onClear = { onTagToggle(tag) },
                )
            }
        }

        if (selectedCategory != ExploreCategory.ANIME) {
            item {
                ActiveFilterChip(
                    text = selectedCategory.label,
                    onClear = onClearCategory,
                )
            }
        }

        if (selectedSort != ExploreSort.HEAT) {
            item {
                ActiveFilterChip(
                    text = selectedSort.label,
                    onClear = onClearSort,
                )
            }
        }

        item {
            TextButton(
                onClick = onResetAll,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = "清除全部",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun ActiveFilterChip(
    text: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClear,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "移除",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
