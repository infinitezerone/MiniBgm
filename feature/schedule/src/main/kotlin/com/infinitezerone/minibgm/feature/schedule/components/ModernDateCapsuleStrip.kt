package com.infinitezerone.minibgm.feature.schedule.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmSegmentedControl
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGoldBright
import com.infinitezerone.minibgm.core.designsystem.theme.WishOrange
import com.infinitezerone.minibgm.feature.schedule.R
import com.infinitezerone.minibgm.feature.schedule.WeekdayDateItem
import kotlin.math.roundToInt

@Composable
fun ModernDateCapsuleStrip(
    dateItems: List<WeekdayDateItem>,
    selectedPage: Int,
    pagerState: PagerState,
    onSelectPage: (Int) -> Unit,
    watchingCountMap: Map<Int, Int>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableIntStateOf(0) }

    val itemWidthDp = 44.dp
    val spacingDp = 8.dp
    val horizontalPaddingDp = 12.dp
    val stridePx = with(density) { (itemWidthDp + spacingDp).toPx() }
    val itemWidthPx = with(density) { itemWidthDp.toPx() }
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
                val centerPx = paddingPx + pageFraction * stridePx + itemWidthPx / 2f
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
        val centerPx = paddingPx + pagerState.currentPage * stridePx + itemWidthPx / 2f
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
                .padding(horizontal = horizontalPaddingDp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(spacingDp),
    ) {
        val activePage = pagerState.currentPage
        dateItems.forEachIndexed { index, item ->
            val isSelected = index == activePage
            val watchingCount = watchingCountMap[index] ?: 0

            DateCapsule(
                item = item,
                isSelected = isSelected,
                watchingCount = watchingCount,
                onClick = { onSelectPage(index) },
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
    val weekdayText =
        if (item.isToday) {
            stringResource(R.string.feature_schedule_weekday_short_today)
        } else {
            when (item.weekday) {
                1 -> stringResource(R.string.feature_schedule_weekday_short_mon)
                2 -> stringResource(R.string.feature_schedule_weekday_short_tue)
                3 -> stringResource(R.string.feature_schedule_weekday_short_wed)
                4 -> stringResource(R.string.feature_schedule_weekday_short_thu)
                5 -> stringResource(R.string.feature_schedule_weekday_short_fri)
                6 -> stringResource(R.string.feature_schedule_weekday_short_sat)
                7 -> stringResource(R.string.feature_schedule_weekday_short_sun)
                else -> item.weekdayLabel.takeLast(1)
            }
        }

    val dayNumber = item.dateLabel.substringAfter('/', item.dateLabel)

    val weekdayTextColor =
        when {
            isSelected -> MaterialTheme.colorScheme.primary
            item.isToday -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        }

    val circleColor =
        when {
            isSelected -> MaterialTheme.colorScheme.primary
            item.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else -> Color.Transparent
        }

    val dateTextColor =
        when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            item.isToday -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }

    val circleBorder =
        when {
            isSelected -> null
            item.isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            else -> null
        }

    Column(
        modifier =
            modifier
                .width(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = weekdayText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected || item.isToday) FontWeight.Bold else FontWeight.Medium,
            color = weekdayTextColor,
        )

        Surface(
            shape = CircleShape,
            color = circleColor,
            border = circleBorder,
            modifier = Modifier.size(32.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(
                    text = dayNumber,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected || item.isToday) FontWeight.Bold else FontWeight.Medium,
                    color = dateTextColor,
                )
            }
        }

        // 追番小指示器（小圆点）
        Box(
            modifier = Modifier.height(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (watchingCount > 0) {
                Box(
                    modifier =
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) RatingGoldBright else WishOrange),
                )
            }
        }
    }
}

enum class ScheduleFilterType {
    WATCHING,
    ALL,
}

@Composable
fun FilterAndMetaBar(
    totalCount: Int,
    watchingCount: Int,
    onlyWatching: Boolean,
    onToggleOnlyWatching: () -> Unit,
    modifier: Modifier = Modifier,
    isLoggedIn: Boolean = true,
    onPromptLogin: () -> Unit = {},
) {
    val currentTab = if (onlyWatching) ScheduleFilterType.WATCHING else ScheduleFilterType.ALL

    BgmSegmentedControl(
        items = listOf(ScheduleFilterType.WATCHING, ScheduleFilterType.ALL),
        selectedItem = currentTab,
        onItemSelected = { tab ->
            when (tab) {
                ScheduleFilterType.WATCHING -> {
                    if (!isLoggedIn) {
                        onPromptLogin()
                    } else if (!onlyWatching) {
                        onToggleOnlyWatching()
                    }
                }
                ScheduleFilterType.ALL -> {
                    if (onlyWatching) {
                        onToggleOnlyWatching()
                    }
                }
            }
        },
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        height = 36.dp,
        itemContent = { tab, isSelected ->
            val labelText =
                when (tab) {
                    ScheduleFilterType.WATCHING -> {
                        if (!isLoggedIn) {
                            stringResource(R.string.feature_schedule_filter_my_watchlist_not_logged_in)
                        } else {
                            stringResource(R.string.feature_schedule_filter_my_watchlist_count, watchingCount)
                        }
                    }
                    ScheduleFilterType.ALL -> {
                        stringResource(R.string.feature_schedule_filter_all_broadcast_count, totalCount)
                    }
                }
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        },
    )
}
