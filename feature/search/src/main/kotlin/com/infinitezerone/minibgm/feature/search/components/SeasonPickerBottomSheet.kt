package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.feature.search.SeasonQuarter

/**
 * 档期选择半屏抽屉：横向年份轴 + 2x2 季度网格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonPickerBottomSheet(
    selectedYear: Int,
    selectedQuarter: SeasonQuarter,
    currentYear: Int,
    currentQuarter: SeasonQuarter,
    availableYears: List<Int>,
    onSelectSeason: (year: Int, quarter: SeasonQuarter) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    var tempYear by remember(selectedYear) { mutableIntStateOf(selectedYear) }

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
                    .navigationBarsPadding(),
        ) {
            // 标题栏与回到当季快捷键
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "选择新番档期",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = {
                        onSelectSeason(currentYear, currentQuarter)
                    },
                ) {
                    Text(
                        text = "回到当前季 ($currentYear ${currentQuarter.displayLabel})",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // 1. 年份选择（横向滚动 Chip）
            Text(
                text = "年份",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                items(availableYears, key = { it }) { year ->
                    val isYearSelected = tempYear == year
                    FilterChip(
                        selected = isYearSelected,
                        onClick = { tempYear = year },
                        label = {
                            Text(
                                text = "${year}年",
                                fontWeight = if (isYearSelected) FontWeight.Bold else FontWeight.Normal,
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
                    )
                }
            }

            // 2. 季度选择卡片（2x2 网格，点击即选中并确认）
            Text(
                text = "季度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                listOf(SeasonQuarter.WINTER, SeasonQuarter.SPRING).forEach { quarter ->
                    SeasonQuarterCard(
                        quarter = quarter,
                        isSelected = selectedQuarter == quarter && selectedYear == tempYear,
                        isCurrent = currentQuarter == quarter && currentYear == tempYear,
                        onClick = { onSelectSeason(tempYear, quarter) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) {
                listOf(SeasonQuarter.SUMMER, SeasonQuarter.AUTUMN).forEach { quarter ->
                    SeasonQuarterCard(
                        quarter = quarter,
                        isSelected = selectedQuarter == quarter && selectedYear == tempYear,
                        isCurrent = currentQuarter == quarter && currentYear == tempYear,
                        onClick = { onSelectSeason(tempYear, quarter) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonQuarterCard(
    quarter: SeasonQuarter,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateSpan = quarter.airDateLabel

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        border =
            if (isCurrent && !isSelected) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            } else {
                null
            },
        modifier = modifier.height(64.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = quarter.displayLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                if (isCurrent) {
                    Text(
                        text = "当季",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = dateSpan,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
