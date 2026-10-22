package com.infinitezerone.minibgm.feature.schedule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmCircleActionButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmFloatingPageScaffold
import com.infinitezerone.minibgm.core.designsystem.component.BgmLoginPromptDialog
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopBarMode
import com.infinitezerone.minibgm.core.designsystem.component.ObserveAsEvents
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.feature.schedule.R
import com.infinitezerone.minibgm.feature.schedule.components.ModernDateCapsuleStrip
import com.infinitezerone.minibgm.feature.schedule.components.OfflineCacheBanner
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleDayEmptyNote
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleErrorState
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleNowIndicator
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleTimelineSkeleton
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleUntimedSection
import com.infinitezerone.minibgm.feature.schedule.components.TIMELINE_SLOT_SPACING
import com.infinitezerone.minibgm.feature.schedule.components.TimelineSlotRow
import com.infinitezerone.minibgm.feature.schedule.components.getCurrentMinutesCst
import com.infinitezerone.minibgm.feature.schedule.components.parseTimeMinutesCst
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onAssistantClick: (() -> Unit)? = null,
    onSourceSearch: ((String) -> Unit)? = null,
    onPlayClick: ((PlayerRoute) -> Unit)? = null,
    onLoginRequest: () -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
    viewModel: ScheduleViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val overlayHostState = rememberOverlayHostState()
    val snackbarHostState = remember { SnackbarHostState() }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val weekStripHeight = 48.dp
    val totalTopChromeHeight = statusBarTop + BgmSpacing.TopBar.height + weekStripHeight

    // 13天平滑滑动的 Pager，初始定位到今天（索引 6）
    val pagerState =
        rememberPagerState(
            initialPage = ScheduleViewModel.TODAY_PAGE_INDEX,
            pageCount = { ScheduleViewModel.TOTAL_SCHEDULE_DAYS },
        )

    val pageListStates = List(ScheduleViewModel.TOTAL_SCHEDULE_DAYS) { rememberLazyListState() }

    // 监听底栏「放送」Tab 再次点击回顶（非今天先平滑滚回今天，已经在今天则滚回列表顶部）
    if (scrollToTop != null) {
        ObserveAsEvents(scrollToTop) {
            if (pagerState.currentPage != ScheduleViewModel.TODAY_PAGE_INDEX) {
                coroutineScope.launch { pagerState.animateScrollToPage(ScheduleViewModel.TODAY_PAGE_INDEX) }
            } else {
                coroutineScope.launch {
                    pageListStates.getOrNull(ScheduleViewModel.TODAY_PAGE_INDEX)?.animateScrollToItem(0)
                }
            }
        }
    }

    // 监听 ViewModel 提示消息（如快捷追番或标记已看反馈）
    ObserveAsEvents(viewModel.userMessage) { message ->
        snackbarHostState.showSnackbar(message)
    }

    // 滑动 Pager 时，双向同步选中的天索引
    LaunchedEffect(pagerState.settledPage) {
        if (uiState.selectedPageIndex != pagerState.settledPage) {
            viewModel.selectPage(pagerState.settledPage)
        }
    }

    BgmFloatingPageScaffold(
        topBarMode = BgmTopBarMode.Immersive,
        topScrimHeight = totalTopChromeHeight + 24.dp,
        topScrimSolidHeight = totalTopChromeHeight,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BgmTopAppBar(
                    mode = BgmTopBarMode.Immersive,
                    title = {
                        Text(
                            text = stringResource(R.string.feature_schedule_title),
                        )
                    },
                    actions = {
                        val currentWatchingCount = uiState.getWatchingCountForPage(pagerState.currentPage)
                        val isOnlyWatching = uiState.onlyWatching

                        BadgedBox(
                            badge = {
                                if (uiState.isLoggedIn && currentWatchingCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ) {
                                        Text(
                                            text = currentWatchingCount.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            },
                        ) {
                            BgmCircleActionButton(
                                onClick = viewModel::toggleOnlyWatching,
                                icon = if (isOnlyWatching) BgmIcons.Bookmark else BgmIcons.BookmarkBorder,
                                contentDescription =
                                    if (isOnlyWatching) {
                                        stringResource(R.string.feature_schedule_filter_all_broadcast)
                                    } else {
                                        stringResource(R.string.feature_schedule_filter_my_watchlist)
                                    },
                                containerColor =
                                    if (isOnlyWatching) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                contentColor =
                                    if (isOnlyWatching) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                    },
                )

                val watchingCountMap =
                    remember(uiState.daySchedules, uiState.watchingSubjectIds) {
                        (0 until ScheduleViewModel.TOTAL_SCHEDULE_DAYS).associateWith {
                            uiState.getWatchingCountForPage(it)
                        }
                    }

                // 顶部星期胶囊导航（指示器 + 快速点击锚点）
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ModernDateCapsuleStrip(
                        dateItems = uiState.dateItems,
                        selectedPage = pagerState.currentPage,
                        pagerState = pagerState,
                        onSelectPage = { page ->
                            if (page == pagerState.currentPage) {
                                coroutineScope.launch {
                                    pageListStates.getOrNull(page)?.animateScrollToItem(0)
                                }
                            } else {
                                viewModel.selectPage(page)
                                coroutineScope.launch {
                                    pagerState.scrollToPage(page)
                                }
                            }
                        },
                        watchingCountMap = watchingCountMap,
                        modifier = Modifier.widthIn(max = 840.dp),
                    )
                }
            }
        },
        snackbarHost = {
            BgmSnackbarHost(
                hostState = snackbarHostState,
                isTopLevel = true,
            )
        },
        modifier = modifier,
    ) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing || (!uiState.hasSchedules && uiState.isLoading),
            onRefresh = { viewModel.refresh(force = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                !uiState.hasSchedules && uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(top = totalTopChromeHeight),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        ScheduleTimelineSkeleton(
                            modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
                        )
                    }
                }

                !uiState.hasSchedules && uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(top = totalTopChromeHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        ScheduleErrorState(
                            errorMessage = uiState.error ?: stringResource(R.string.feature_schedule_error_network),
                            onRetry = viewModel::refresh,
                            modifier = Modifier.widthIn(max = 840.dp),
                        )
                    }
                }

                else -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { page -> page },
                    ) { page ->
                        val isTodayPage = page == ScheduleViewModel.TODAY_PAGE_INDEX
                        val timeGrouped =
                            remember(uiState.daySchedules, page, uiState.onlyWatching, uiState.watchingSubjectIds) {
                                uiState.getTimeGroupedSchedulesForPage(page)
                            }
                        val allDaySchedules =
                            remember(uiState.daySchedules, page, uiState.onlyWatching, uiState.watchingSubjectIds) {
                                uiState.getAllDaySchedulesForPage(page)
                            }
                        val listState = pageListStates.getOrElse(page) { rememberLazyListState() }

                        DayScheduleList(
                            pageIndex = page,
                            isTodayPage = isTodayPage,
                            timeGrouped = timeGrouped,
                            allDaySchedules = allDaySchedules,
                            uiState = uiState,
                            onSubjectClick = onSubjectClick,
                            onPlayClick = onPlayClick,
                            onSwitchToAll = viewModel::toggleOnlyWatching,
                            listState = listState,
                            topPadding = totalTopChromeHeight + 12.dp,
                        )
                    }
                }
            }
        }
    }

    BgmOverlayHost(hostState = overlayHostState) {
        confirmDialog()
    }

    if (uiState.showLoginPromptDialog) {
        BgmLoginPromptDialog(
            description = stringResource(R.string.feature_schedule_login_prompt_desc),
            onLogin = {
                viewModel.dismissLoginPrompt()
                onLoginRequest()
            },
            onDismiss = viewModel::dismissLoginPrompt,
        )
    }
}

@Composable
private fun DayScheduleList(
    pageIndex: Int,
    isTodayPage: Boolean,
    timeGrouped: Map<String, List<AirSchedule>>,
    allDaySchedules: List<AirSchedule>,
    uiState: ScheduleUiState,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onPlayClick: ((PlayerRoute) -> Unit)?,
    onSwitchToAll: () -> Unit,
    listState: LazyListState,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    // 当前时刻（按东八区），每 30 秒心跳一次，驱动「现在」指示与待播倒计时保持实时
    val nowMinutes by produceState(initialValue = getCurrentMinutesCst()) {
        while (isActive) {
            delay(30_000)
            value = getCurrentMinutesCst()
        }
    }
    val nowSlotIndex =
        if (isTodayPage) {
            timeGrouped.entries.count { (parseTimeMinutesCst(it.key) ?: Int.MIN_VALUE) <= nowMinutes }
        } else {
            -1
        }

    // 首次进入今天页时定位：自动滑向当前时段
    val hasAnchoredToNow = remember { mutableStateOf(false) }
    LaunchedEffect(isTodayPage, timeGrouped) {
        if (!isTodayPage || timeGrouped.isEmpty()) return@LaunchedEffect
        if (!hasAnchoredToNow.value && nowSlotIndex > 2) {
            val headerItemCount = if (uiState.isOfflineCache) 1 else 0
            listState.scrollToItem(headerItemCount + nowSlotIndex)
            hasAnchoredToNow.value = true
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(TIMELINE_SLOT_SPACING),
            modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
        ) {
            if (uiState.isOfflineCache) {
                item(key = "offline_cache_banner") {
                    OfflineCacheBanner(onRetry = {})
                }
            }

            // 如果当天完全没有排播
            if (timeGrouped.isEmpty() && allDaySchedules.isEmpty()) {
                item(key = "empty_day_$pageIndex") {
                    ScheduleDayEmptyNote(
                        onlyWatching = uiState.onlyWatching,
                        onSwitchToAll = onSwitchToAll,
                    )
                }
            } else {
                // ==================== 时间线排播节点（时间醒目 + 聚合防冗余） ====================
                var emittedNowLine = false
                timeGrouped.entries.forEachIndexed { slotIndex, entry ->
                    if (slotIndex == nowSlotIndex) {
                        item(key = "timeslot_with_now_${pageIndex}_${entry.key}") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ScheduleNowIndicator(nowMinutesCst = nowMinutes)
                                TimelineSlotRow(
                                    time = entry.key,
                                    schedules = entry.value,
                                    isToday = isTodayPage,
                                    watchingSubjectIds = uiState.watchingSubjectIds,
                                    onSubjectClick = onSubjectClick,
                                    currentMinutesCst = nowMinutes,
                                )
                            }
                        }
                        emittedNowLine = true
                    } else {
                        item(key = "timeslot_${pageIndex}_${entry.key}") {
                            TimelineSlotRow(
                                time = entry.key,
                                schedules = entry.value,
                                isToday = isTodayPage,
                                watchingSubjectIds = uiState.watchingSubjectIds,
                                onSubjectClick = onSubjectClick,
                                currentMinutesCst = nowMinutes,
                            )
                        }
                    }
                }
                // 已过全天所有时段时，指示线自然收尾到最后一个时段之后
                if (isTodayPage && !emittedNowLine && timeGrouped.isNotEmpty()) {
                    item(key = "now_line_$pageIndex") {
                        ScheduleNowIndicator(nowMinutesCst = nowMinutes)
                    }
                }

                // ==================== 全天 / 网络独播待定番剧自然收容 ====================
                if (allDaySchedules.isNotEmpty()) {
                    item(key = "untimed_section_$pageIndex") {
                        ScheduleUntimedSection(
                            schedules = allDaySchedules,
                            watchingSubjectIds = uiState.watchingSubjectIds,
                            onSubjectClick = onSubjectClick,
                        )
                    }
                }
            }
        }
    }
}
