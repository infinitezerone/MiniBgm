package com.infinitezerone.minibgm.feature.schedule.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGoldBright
import com.infinitezerone.minibgm.core.designsystem.theme.WishOrange
import com.infinitezerone.minibgm.feature.schedule.WeekdayDateItem
import kotlin.math.roundToInt

@Composable
fun ModernDateCapsuleStrip(
    dateItems: List<WeekdayDateItem>,
    selectedWeekday: Int,
    pagerState: PagerState,
    onSelectWeekday: (Int) -> Unit,
    watchingCountMap: Map<Int, Int>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableIntStateOf(0) }

    val capsuleWidthDp = 56.dp
    val spacingDp = 8.dp
    val horizontalPaddingDp = 14.dp
    val stridePx = with(density) { (capsuleWidthDp + spacingDp).toPx() }
    val capsuleWidthPx = with(density) { capsuleWidthDp.toPx() }
    val paddingPx = with(density) { horizontalPaddingDp.toPx() }

    // 仅当下方的番剧列表 (Pager) 正在被手势滑动时，才像素级同步顶部的星期胶囊条
    LaunchedEffect(containerWidthPx) {
        if (containerWidthPx <= 0) return@LaunchedEffect
        snapshotFlow {
            if (pagerState.isScrollInProgress) {
                pagerState.currentPage + pagerState.currentPageOffsetFraction
            } else {
                null
            }
        }.collect { pageFraction ->
            if (pageFraction != null && scrollState.maxValue > 0 && !scrollState.isScrollInProgress) {
                val centerPx = paddingPx + pageFraction * stridePx + capsuleWidthPx / 2f
                val targetScrollPx =
                    (centerPx - containerWidthPx / 2f)
                        .coerceIn(0f, scrollState.maxValue.toFloat())
                        .roundToInt()
                scrollState.scrollTo(targetScrollPx)
            }
        }
    }

    // 当选中的天发生改变（点击胶囊或翻页结束）时，平滑滚动至居中
    LaunchedEffect(pagerState.currentPage, containerWidthPx) {
        if (containerWidthPx <= 0 || scrollState.maxValue <= 0) return@LaunchedEffect
        if (scrollState.isScrollInProgress) return@LaunchedEffect
        val centerPx = paddingPx + pagerState.currentPage * stridePx + capsuleWidthPx / 2f
        val targetScrollPx =
            (centerPx - containerWidthPx / 2f)
                .coerceIn(0f, scrollState.maxValue.toFloat())
                .roundToInt()
        scrollState.animateScrollTo(targetScrollPx)
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    containerWidthPx = coordinates.size.width
                }.horizontalScroll(scrollState)
                .padding(horizontal = horizontalPaddingDp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(spacingDp),
    ) {
        val activeWeekday = pagerState.currentPage + 1
        dateItems.forEach { item ->
            val isSelected = item.weekday == activeWeekday
            val watchingCount = watchingCountMap[item.weekday] ?: 0

            DateCapsule(
                item = item,
                isSelected = isSelected,
                watchingCount = watchingCount,
                onClick = { onSelectWeekday(item.weekday) },
            )
        }
    }
}

@Composable
fun DateCapsule(
    item: WeekdayDateItem,
    isSelected: Boolean,
    watchingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else if (item.isToday) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }

    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else if (item.isToday) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    val borderColor =
        if (item.isToday && !isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        } else {
            Color.Transparent
        }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = if (borderColor != Color.Transparent) BorderStroke(1.dp, borderColor) else null,
        modifier = modifier.width(56.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (item.isToday) "今天" else item.weekdayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected || item.isToday) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                )
                if (watchingCount > 0) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier =
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) RatingGoldBright else WishOrange),
                    )
                }
            }

            Text(
                text = item.dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
fun FilterAndMetaBar(
    totalCount: Int,
    watchingCount: Int,
    onlyWatching: Boolean,
    onToggleOnlyWatching: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !onlyWatching,
                onClick = { if (onlyWatching) onToggleOnlyWatching() },
                label = {
                    Text(
                        text = "全部 ($totalCount)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            )

            FilterChip(
                selected = onlyWatching,
                onClick = { if (!onlyWatching) onToggleOnlyWatching() },
                label = {
                    Text(
                        text = "⭐ 我追的 ($watchingCount)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (onlyWatching) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }

        Text(
            text = "北京时间 CST",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}
