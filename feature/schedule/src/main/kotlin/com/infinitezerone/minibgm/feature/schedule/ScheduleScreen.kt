package com.infinitezerone.minibgm.feature.schedule

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGoldBright
import com.infinitezerone.minibgm.core.designsystem.theme.StatusAiring
import com.infinitezerone.minibgm.core.designsystem.theme.WishOrange
import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.SiteLink
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleSourcesBottomSheet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
) {
    val viewModel: ScheduleViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedScheduleForSources by remember { mutableStateOf<AirSchedule?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 7天平滑滑动的 Pager，初始定位到今天
    val initialPage = (uiState.todayWeekday - 1).coerceIn(0, 6)
    val pagerState =
        rememberPagerState(
            initialPage = initialPage,
            pageCount = { 7 },
        )

    val stateMon = rememberLazyListState()
    val stateTue = rememberLazyListState()
    val stateWed = rememberLazyListState()
    val stateThu = rememberLazyListState()
    val stateFri = rememberLazyListState()
    val stateSat = rememberLazyListState()
    val stateSun = rememberLazyListState()
    val weekdayListStates =
        remember(stateMon, stateTue, stateWed, stateThu, stateFri, stateSat, stateSun) {
            mapOf(
                1 to stateMon,
                2 to stateTue,
                3 to stateWed,
                4 to stateThu,
                5 to stateFri,
                6 to stateSat,
                7 to stateSun,
            )
        }

    // 监听底栏「放送」Tab 再次点击回顶
    LaunchedEffect(scrollToTop) {
        scrollToTop?.collect {
            val currentWeekday = pagerState.currentPage + 1
            weekdayListStates[currentWeekday]?.animateScrollToItem(0)
        }
    }

    // 监听 ViewModel 提示消息（如快捷追番或标记已看反馈）
    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // 滑动 Pager 时，双向同步选中的星期
    LaunchedEffect(pagerState.currentPage) {
        val targetWeekday = pagerState.currentPage + 1
        if (uiState.selectedWeekday != targetWeekday) {
            viewModel.selectWeekday(targetWeekday)
        }
    }

    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = {
                    Text(
                        text = "📅 放送时刻表",
                    )
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "搜索条目",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            // 顶部星期胶囊导航（指示器 + 快速点击锚点）
            ModernDateCapsuleStrip(
                dateItems = uiState.dateItems,
                selectedWeekday = uiState.selectedWeekday,
                pagerState = pagerState,
                onSelectWeekday = { weekday ->
                    if (weekday == uiState.selectedWeekday) {
                        coroutineScope.launch {
                            weekdayListStates[weekday]?.animateScrollToItem(0)
                        }
                    } else {
                        viewModel.selectWeekday(weekday)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(weekday - 1)
                        }
                    }
                },
                watchingCountMap = (1..7).associateWith { uiState.getWatchingCountForWeekday(it) },
            )

            val currentWeekdayTotal = uiState.getTotalCountForWeekday(uiState.selectedWeekday)
            val currentWeekdayWatching = uiState.getWatchingCountForWeekday(uiState.selectedWeekday)

            FilterAndMetaBar(
                totalCount = currentWeekdayTotal,
                watchingCount = currentWeekdayWatching,
                onlyWatching = uiState.onlyWatching,
                onToggleOnlyWatching = viewModel::toggleOnlyWatching,
            )

            // 主体：左右手势丝滑翻页的 HorizontalPager
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.isLoading && uiState.weeklySchedules.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.weeklySchedules.values.all { it.isEmpty() } && uiState.error != null -> {
                        ScheduleErrorState(
                            errorMessage = uiState.error.orEmpty(),
                            onRetry = viewModel::refresh,
                        )
                    }

                    else -> {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { pageIndex ->
                            val pageWeekday = pageIndex + 1
                            val isTodayPage = pageWeekday == uiState.todayWeekday
                            val timeGrouped =
                                remember(uiState.weeklySchedules, uiState.onlyWatching, uiState.watchingSubjectIds, pageWeekday) {
                                    uiState.getTimeGroupedSchedulesForWeekday(pageWeekday)
                                }
                            val allDaySchedules =
                                remember(uiState.weeklySchedules, uiState.onlyWatching, uiState.watchingSubjectIds, pageWeekday) {
                                    uiState.getAllDaySchedulesForWeekday(pageWeekday)
                                }

                            DayScheduleList(
                                weekday = pageWeekday,
                                isTodayPage = isTodayPage,
                                timeGrouped = timeGrouped,
                                allDaySchedules = allDaySchedules,
                                uiState = uiState,
                                onSubjectClick = onSubjectClick,
                                onToggleWatching = viewModel::toggleWatching,
                                onMarkEpisodeWatched = viewModel::markEpisodeWatched,
                                onShowSources = { selectedScheduleForSources = it },
                                listState = weekdayListStates[pageWeekday] ?: rememberLazyListState(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedScheduleForSources != null) {
        ScheduleSourcesBottomSheet(
            schedule = selectedScheduleForSources!!,
            onDismissRequest = { selectedScheduleForSources = null },
            onOpenUrl = { url -> openWebUrl(context, url) },
        )
    }
}

@Composable
private fun DayScheduleList(
    weekday: Int,
    isTodayPage: Boolean,
    timeGrouped: Map<String, List<AirSchedule>>,
    allDaySchedules: List<AirSchedule>,
    uiState: ScheduleUiState,
    onSubjectClick: (Long) -> Unit,
    onToggleWatching: (Long) -> Unit,
    onMarkEpisodeWatched: (Long, Int) -> Unit,
    onShowSources: (AirSchedule) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (uiState.isOfflineCache) {
            item(key = "offline_cache_banner") {
                OfflineCacheBanner(onRetry = {})
            }
        }

        // ==================== 今日视图专属首屏：待补更新 ====================
        if (isTodayPage && !uiState.onlyWatching && uiState.catchupItems.isNotEmpty()) {
            item(key = "today_catchup_feed") {
                ScheduleCatchupSection(
                    catchupItems = uiState.catchupItems,
                    onSubjectClick = onSubjectClick,
                    onMarkEpisodeWatched = onMarkEpisodeWatched,
                )
            }
        }

        // 如果当天完全没有排播
        if (timeGrouped.isEmpty() && allDaySchedules.isEmpty()) {
            item(key = "empty_day_$weekday") {
                ScheduleDayEmptyNote(onlyWatching = uiState.onlyWatching)
            }
        } else {
            // ==================== 时间线排播节点（时间醒目 + 聚合防冗余） ====================
            timeGrouped.forEach { (time, animeList) ->
                item(key = "timeslot_${weekday}_$time") {
                    TimelineSlotRow(
                        time = time,
                        schedules = animeList,
                        isToday = isTodayPage,
                        watchingSubjectIds = uiState.watchingSubjectIds,
                        onSubjectClick = onSubjectClick,
                        onToggleWatching = onToggleWatching,
                        onShowSources = onShowSources,
                    )
                }
            }

            // ==================== 全天 / 网络独播待定番剧自然收容 ====================
            if (allDaySchedules.isNotEmpty()) {
                item(key = "untimed_section_$weekday") {
                    ScheduleUntimedSection(
                        schedules = allDaySchedules,
                        watchingSubjectIds = uiState.watchingSubjectIds,
                        onSubjectClick = onSubjectClick,
                        onToggleWatching = onToggleWatching,
                        onShowSources = onShowSources,
                    )
                }
            }
        }
    }
}

/** 时间线单行插槽：左侧醒目时间轴轨道 + 右侧番剧卡片/聚合卡片 */
@Composable
private fun TimelineSlotRow(
    time: String,
    schedules: List<AirSchedule>,
    isToday: Boolean,
    watchingSubjectIds: Set<Long>,
    onSubjectClick: (Long) -> Unit,
    onToggleWatching: (Long) -> Unit,
    onShowSources: (AirSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val airStatus = getAirStatus(time, isToday = isToday)
    val jstTime = schedules.firstOrNull()?.timeJst

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimelineTrackRail(
            time = time,
            airStatus = airStatus,
            jstTime = jstTime,
            count = schedules.size,
            modifier = Modifier.width(56.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            schedules.forEach { singleSchedule ->
                ScheduleTimelineSingleCard(
                    schedule = singleSchedule,
                    isWatching = watchingSubjectIds.contains(singleSchedule.bgmId),
                    onSubjectClick = onSubjectClick,
                    onToggleWatching = onToggleWatching,
                    onShowSources = onShowSources,
                )
            }
        }
    }
}

/** 垂直时间线轨道：醒目的时间数值、状态标识、连接线与节点 */
@Composable
private fun TimelineTrackRail(
    time: String,
    airStatus: AirStatus,
    jstTime: String?,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val statusColor =
        when (airStatus) {
            AirStatus.AIRING -> StatusAiring
            AirStatus.UPCOMING -> MaterialTheme.colorScheme.primary
            AirStatus.AIRED -> MaterialTheme.colorScheme.outline
            AirStatus.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 左列：时间数值与状态标签
        Column(
            horizontalAlignment = Alignment.End,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(top = 2.dp),
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color =
                    when (airStatus) {
                        AirStatus.AIRING -> StatusAiring
                        AirStatus.UPCOMING -> MaterialTheme.colorScheme.primary
                        AirStatus.AIRED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        AirStatus.NORMAL -> MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 1,
            )

            if (airStatus != AirStatus.NORMAL) {
                Text(
                    text =
                        when (airStatus) {
                            AirStatus.AIRED -> "已播"
                            AirStatus.AIRING -> "热播"
                            AirStatus.UPCOMING -> "待播"
                            AirStatus.NORMAL -> ""
                        },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.82f,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )
            }

            if (count > 1) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        text = "${count}部",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.75f,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp),
                    )
                }
            }

            if (!jstTime.isNullOrBlank() && jstTime != time) {
                Text(
                    text = jstTime,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.7f,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }

        // 右列：贯穿时间线与节点
        Box(
            modifier =
                Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        val centerX = size.width / 2
                        val dotCenterY = 9.dp.toPx()

                        // 垂直轨道连线（向下延伸连接到下一个 item 的 spacing）
                        drawLine(
                            color = outlineVariant,
                            start = Offset(centerX, 0f),
                            end = Offset(centerX, size.height + 12.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                        )

                        // 状态节点
                        when (airStatus) {
                            AirStatus.AIRING -> {
                                drawCircle(
                                    color = StatusAiring.copy(alpha = 0.25f),
                                    radius = 6.5.dp.toPx(),
                                    center = Offset(centerX, dotCenterY),
                                )
                                drawCircle(
                                    color = StatusAiring,
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(centerX, dotCenterY),
                                )
                            }
                            AirStatus.UPCOMING -> {
                                drawCircle(
                                    color = statusColor.copy(alpha = 0.2f),
                                    radius = 5.5.dp.toPx(),
                                    center = Offset(centerX, dotCenterY),
                                )
                                drawCircle(
                                    color = statusColor,
                                    radius = 3.dp.toPx(),
                                    center = Offset(centerX, dotCenterY),
                                )
                            }
                            else -> {
                                drawCircle(
                                    color = outlineVariant,
                                    radius = 3.dp.toPx(),
                                    center = Offset(centerX, dotCenterY),
                                )
                            }
                        }
                    },
        )
    }
}

/** 单番时间线卡片（左侧有醒目时间轨，右侧卡片遵循古腾堡阅读动线与无遮挡海报） */
@Composable
private fun ScheduleTimelineSingleCard(
    schedule: AirSchedule,
    isWatching: Boolean,
    onSubjectClick: (Long) -> Unit,
    onToggleWatching: (Long) -> Unit,
    onShowSources: (AirSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val displayName = schedule.titleCn.ifBlank { schedule.title }
    val originalTitle = schedule.title.takeIf { it.isNotBlank() && it != displayName }

    Card(
        onClick = { onSubjectClick(schedule.bgmId) },
        shape = RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isWatching) {
                        MaterialTheme.colorScheme.surfaceContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ),
        border =
            if (isWatching) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            } else {
                null
            },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 纯净封面：无任何盖脸黑标，保证视觉艺术完整性
            Box(
                modifier =
                    Modifier
                        .width(58.dp)
                        .height(82.dp)
                        .clip(RoundedCornerShape(8.dp)),
            ) {
                CoverImage(
                    url = schedule.coverUrl,
                    contentDescription = displayName,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 内容区：自上而下的自然阅读动线
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 1. 顶层：标题独享 100% 水平宽度，支持长标题两行舒展
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (originalTitle != null) {
                        Text(
                            text = originalTitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.88f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 2. 中间层：话数胶囊 + ★ 金色评分
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        if (schedule.nextEpisodeNumber > 0) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            ) {
                                Text(
                                    text =
                                        if (schedule.nextEpisodeKind == AirEventKind.PREDICTED) {
                                            "第 ${schedule.nextEpisodeNumber} 话 · 预计"
                                        } else {
                                            "第 ${schedule.nextEpisodeNumber} 话"
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }

                        if (schedule.ratingScore > 0.0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = RatingGold,
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = schedule.ratingScore.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = RatingGold,
                                )
                            }
                        }
                    }
                }

                // 3. 底部终端区（Terminal Area）：左侧播放源 + 右侧行动召唤追番
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    if (schedule.siteLinks.isNotEmpty()) {
                        SiteLinksRow(
                            links = schedule.siteLinks,
                            onOpenUrl = { openWebUrl(context, it) },
                            onShowMoreSources = { onShowSources(schedule) },
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    BookmarkChip(
                        isWatching = isWatching,
                        onToggle = { onToggleWatching(schedule.bgmId) },
                    )
                }
            }
        }
    }
}

/** 待补更新聚合卡片（昨日·前天已播但用户未打卡的番） */
@Composable
private fun ScheduleCatchupSection(
    catchupItems: List<CatchupScheduleItem>,
    onSubjectClick: (Long) -> Unit,
    onMarkEpisodeWatched: (Long, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
            ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ElectricBolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "待补更新 (近期在追)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "${catchupItems.size} 部未看",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                catchupItems.forEach { item ->
                    val displayName = item.schedule.titleCn.ifBlank { item.schedule.title }
                    Surface(
                        onClick = { onSubjectClick(item.schedule.bgmId) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(width = 44.dp, height = 60.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                            ) {
                                CoverImage(
                                    url = item.schedule.coverUrl,
                                    contentDescription = displayName,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(bottomEnd = 4.dp),
                                    modifier = Modifier.align(Alignment.TopStart),
                                ) {
                                    Text(
                                        text = item.dayLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "已更新至第 ${item.targetEp} 话 · 当前打卡第 ${item.epStatus} 话",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Button(
                                onClick = { onMarkEpisodeWatched(item.schedule.bgmId, item.targetEp) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                modifier = Modifier.height(30.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "标为看过",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 全天 / 时间待定番剧自然收容折叠区 */
@Composable
private fun ScheduleUntimedSection(
    schedules: List<AirSchedule>,
    watchingSubjectIds: Set<Long>,
    onSubjectClick: (Long) -> Unit,
    onToggleWatching: (Long) -> Unit,
    onShowSources: (AirSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Surface(
                onClick = { isExpanded = !isExpanded },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "全天 / 网络独播待定",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = "${schedules.size} 部",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "折叠" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    schedules.forEach { schedule ->
                        ScheduleTimelineSingleCard(
                            schedule = schedule,
                            isWatching = watchingSubjectIds.contains(schedule.bgmId),
                            onSubjectClick = onSubjectClick,
                            onToggleWatching = onToggleWatching,
                            onShowSources = onShowSources,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkChip(
    isWatching: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(7.dp),
        color =
            if (isWatching) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        border =
            if (isWatching) {
                null
            } else {
                BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            },
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = if (isWatching) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                tint =
                    if (isWatching) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if (isWatching) "在追" else "追番",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color =
                    if (isWatching) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }
    }
}

@Composable
private fun SiteLinksRow(
    links: List<SiteLink>,
    onOpenUrl: (String) -> Unit,
    onShowMoreSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedLinks = remember(links) { sortSiteLinks(links) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier,
    ) {
        sortedLinks.firstOrNull()?.let { topLink ->
            Surface(
                onClick = { onOpenUrl(topLink.playUrl) },
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                ) {
                    Text(
                        text = topLink.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(9.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }

        if (sortedLinks.size > 1) {
            Surface(
                onClick = onShowMoreSources,
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.5.dp),
                ) {
                    Text(
                        text = "+${sortedLinks.size - 1} 更多源",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernDateCapsuleStrip(
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
private fun DateCapsule(
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
private fun FilterAndMetaBar(
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

@Composable
private fun ScheduleDayEmptyNote(
    onlyWatching: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
    ) {
        Text(
            text = if (onlyWatching) "本日暂无您在追的番剧" else "本日暂无新番排播",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 24.dp),
        )
    }
}

@Composable
private fun OfflineCacheBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "⚠️ 离线缓存数据 · 下拉或点击重试",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            IconButton(onClick = onRetry, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "重试",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ScheduleErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "放送表加载失败",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "重新加载")
            }
        }
    }
}

private enum class AirStatus {
    NORMAL,
    UPCOMING,
    AIRING,
    AIRED,
}

private fun getAirStatus(
    timeCst: String,
    isToday: Boolean,
): AirStatus {
    if (!isToday || timeCst.isBlank()) return AirStatus.NORMAL
    val parts = timeCst.split(":")
    if (parts.size < 2) return AirStatus.NORMAL
    val hour = parts[0].toIntOrNull() ?: return AirStatus.NORMAL
    val minute = parts[1].toIntOrNull() ?: return AirStatus.NORMAL

    val zoneCst = ZoneId.of("Asia/Shanghai")
    val now = ZonedDateTime.now(zoneCst)
    val today = now.toLocalDate()
    val airDateTime = today.atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).atZone(zoneCst)
    val endDateTime = airDateTime.plusMinutes(35)

    return when {
        now.isAfter(endDateTime) -> AirStatus.AIRED
        now.isAfter(airDateTime) -> AirStatus.AIRING
        else -> AirStatus.UPCOMING
    }
}

private fun sortSiteLinks(links: List<SiteLink>): List<SiteLink> {
    val priorityOrder =
        listOf(
            "bilibili",
            "gamer",
            "gamer_hk",
            "bahamut",
            "iqiyi",
            "qq",
            "youku",
            "mikan",
            "muse_tw",
            "muse_hk",
            "ani_one",
            "ani_one_asia",
            "netflix",
            "disneyplus",
            "crunchyroll",
            "abema",
            "danime",
            "unext",
            "prime",
            "nicovideo",
        )
    return links.distinctBy { it.displayName }.sortedBy { link ->
        val index = priorityOrder.indexOf(link.siteName.lowercase())
        if (index >= 0) index else 100
    }
}

private fun openWebUrl(
    context: Context,
    url: String,
) {
    if (url.isBlank()) return
    try {
        val uri = Uri.parse(url)
        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    } catch (_: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {
            // Ignore if no browser can handle
        }
    }
}
