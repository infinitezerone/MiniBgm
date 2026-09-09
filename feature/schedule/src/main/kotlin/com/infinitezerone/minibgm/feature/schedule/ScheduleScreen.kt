package com.infinitezerone.minibgm.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.feature.schedule.components.FilterAndMetaBar
import com.infinitezerone.minibgm.feature.schedule.components.ModernDateCapsuleStrip
import com.infinitezerone.minibgm.feature.schedule.components.OfflineCacheBanner
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleCatchupSection
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleDayEmptyNote
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleErrorState
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleSourcesBottomSheet
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleUntimedSection
import com.infinitezerone.minibgm.feature.schedule.components.TimelineSlotRow
import com.infinitezerone.minibgm.feature.schedule.components.openWebUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
) {
    val context = LocalContext.current
    val viewModel: ScheduleViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
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
                            pagerState.scrollToPage(weekday - 1)
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

                    uiState.error != null && uiState.weeklySchedules.isEmpty() -> {
                        ScheduleErrorState(
                            errorMessage = uiState.error ?: "网络连接异常",
                            onRetry = viewModel::refresh,
                        )
                    }

                    else -> {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            key = { page -> page },
                        ) { page ->
                            val weekday = page + 1
                            val isTodayPage = weekday == uiState.todayWeekday
                            val timeGrouped = uiState.getTimeGroupedSchedulesForWeekday(weekday)
                            val allDaySchedules = uiState.getAllDaySchedulesForWeekday(weekday)
                            val listState = weekdayListStates[weekday] ?: rememberLazyListState()

                            DayScheduleList(
                                weekday = weekday,
                                isTodayPage = isTodayPage,
                                timeGrouped = timeGrouped,
                                allDaySchedules = allDaySchedules,
                                uiState = uiState,
                                onSubjectClick = onSubjectClick,
                                onToggleWatching = viewModel::toggleWatching,
                                onMarkEpisodeWatched = viewModel::markEpisodeWatched,
                                onShowSources = { selectedScheduleForSources = it },
                                listState = listState,
                            )
                        }
                    }
                }
            }
        }
    }

    selectedScheduleForSources?.let { schedule ->
        ScheduleSourcesBottomSheet(
            schedule = schedule,
            onDismissRequest = { selectedScheduleForSources = null },
            onOpenUrl = { openWebUrl(context, it) },
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
