package com.infinitezerone.minibgm.feature.schedule

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmLoginPromptDialog
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.ObserveAsEvents
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing
import com.infinitezerone.minibgm.core.designsystem.theme.FeatureGate
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.feature.schedule.R
import com.infinitezerone.minibgm.feature.schedule.components.FilterAndMetaBar
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/** 筛选条折叠状态锚点 */
private enum class FilterBarValue {
    EXPANDED,
    COLLAPSED,
}

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
    val density = LocalDensity.current
    val overlayHostState = rememberOverlayHostState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 13天平滑滑动的 Pager，初始定位到今天（索引 6）
    val pagerState =
        rememberPagerState(
            initialPage = ScheduleViewModel.TODAY_PAGE_INDEX,
            pageCount = { ScheduleViewModel.TOTAL_SCHEDULE_DAYS },
        )

    val pageListStates = List(ScheduleViewModel.TOTAL_SCHEDULE_DAYS) { rememberLazyListState() }

    val initialFilterBarHeightPx = with(density) { 44.dp.toPx() }
    val filterBarState =
        remember {
            AnchoredDraggableState(
                initialValue = FilterBarValue.EXPANDED,
                anchors =
                    DraggableAnchors {
                        FilterBarValue.EXPANDED at 0f
                        FilterBarValue.COLLAPSED at -initialFilterBarHeightPx
                    },
            )
        }

    val filterBarFlingBehavior =
        AnchoredDraggableDefaults.flingBehavior(
            state = filterBarState,
            positionalThreshold = { totalDistance -> totalDistance * 0.5f },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        )

    var animationJob by remember { mutableStateOf<Job?>(null) }

    val filterNestedScrollConnection =
        remember(filterBarState, filterBarFlingBehavior) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    // 当用户手指开始滑动触控输入时，若存在正在运行的动画或沉降，立即取消以无缝接管
                    if (source == NestedScrollSource.UserInput && animationJob?.isActive == true) {
                        animationJob?.cancel()
                        animationJob = null
                    }
                    val deltaY = available.y
                    val minPos = filterBarState.anchors.positionOf(FilterBarValue.COLLAPSED)
                    val maxPos = filterBarState.anchors.positionOf(FilterBarValue.EXPANDED)
                    val currentOffset = filterBarState.offset

                    val consumedY =
                        when {
                            deltaY < 0f && currentOffset > minPos -> filterBarState.dispatchRawDelta(deltaY)
                            deltaY > 0f && currentOffset < maxPos -> filterBarState.dispatchRawDelta(deltaY)
                            else -> 0f
                        }
                    return Offset(0f, consumedY)
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    val minPos = filterBarState.anchors.positionOf(FilterBarValue.COLLAPSED)
                    val maxPos = filterBarState.anchors.positionOf(FilterBarValue.EXPANDED)
                    val currentOffset = filterBarState.offset

                    val isMidway = currentOffset > (minPos + 0.5f) && currentOffset < (maxPos - 0.5f)
                    if (isMidway) {
                        animationJob?.cancel()
                        animationJob =
                            coroutineScope.launch {
                                filterBarState.settle(spring(stiffness = Spring.StiffnessMediumLow))
                            }
                    }
                    return super.onPostFling(consumed, available)
                }
            }
        }

    // 监听底栏「放送」Tab 再次点击回顶（非今天先平滑滚回今天，已经在今天则滚回列表顶部）
    if (scrollToTop != null) {
        ObserveAsEvents(scrollToTop) {
            animationJob?.cancel()
            animationJob = coroutineScope.launch { filterBarState.animateTo(FilterBarValue.EXPANDED) }
            if (pagerState.currentPage != ScheduleViewModel.TODAY_PAGE_INDEX) {
                pagerState.animateScrollToPage(ScheduleViewModel.TODAY_PAGE_INDEX)
            } else {
                pageListStates.getOrNull(ScheduleViewModel.TODAY_PAGE_INDEX)?.animateScrollToItem(0)
            }
        }
    }

    // 监听 ViewModel 提示消息（如快捷追番或标记已看反馈）
    ObserveAsEvents(viewModel.userMessage) { message ->
        snackbarHostState.showSnackbar(message)
    }

    // 滑动 Pager 时，双向同步选中的天索引；切天完成时平滑复位展开筛选条
    LaunchedEffect(pagerState.settledPage) {
        if (uiState.selectedPageIndex != pagerState.settledPage) {
            viewModel.selectPage(pagerState.settledPage)
        }
        if (filterBarState.currentValue != FilterBarValue.EXPANDED) {
            animationJob?.cancel()
            animationJob = launch { filterBarState.animateTo(FilterBarValue.EXPANDED) }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            BgmSnackbarHost(
                hostState = snackbarHostState,
                isTopLevel = true,
            )
        },
        modifier = modifier,
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.systemBars.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                            ),
                        ).padding(top = BgmSpacing.TopBar.height)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                if (animationJob?.isActive == true) {
                                    animationJob?.cancel()
                                    animationJob = null
                                }
                            }
                        }.nestedScroll(filterNestedScrollConnection),
            ) {
                val watchingCountMap =
                    remember(uiState.daySchedules, uiState.watchingSubjectIds) {
                        (0 until ScheduleViewModel.TOTAL_SCHEDULE_DAYS).associateWith {
                            uiState.getWatchingCountForPage(it)
                        }
                    }

                // 顶部星期胶囊导航（指示器 + 快速点击锚点）
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    ModernDateCapsuleStrip(
                        dateItems = uiState.dateItems,
                        selectedPage = pagerState.currentPage,
                        pagerState = pagerState,
                        onSelectPage = { page ->
                            if (page == pagerState.currentPage) {
                                animationJob?.cancel()
                                coroutineScope.launch {
                                    animationJob = launch { filterBarState.animateTo(FilterBarValue.EXPANDED) }
                                    pageListStates.getOrNull(page)?.animateScrollToItem(0)
                                }
                            } else {
                                viewModel.selectPage(page)
                                animationJob?.cancel()
                                coroutineScope.launch {
                                    animationJob = launch { filterBarState.animateTo(FilterBarValue.EXPANDED) }
                                    pagerState.scrollToPage(page)
                                }
                            }
                        },
                        watchingCountMap = watchingCountMap,
                        modifier = Modifier.widthIn(max = 840.dp),
                    )
                }

                val currentPageTotal = uiState.getTotalCountForPage(pagerState.currentPage)
                val currentPageWatching = uiState.getWatchingCountForPage(pagerState.currentPage)

                var filterBarHeightPx by remember { mutableFloatStateOf(initialFilterBarHeightPx) }

                // 内容视口：纯 GPU 摆放阶段平移（offset {}），完全无需 layout {} 重测与重组
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clipToBounds(),
                ) {
                    // 主体：左右手势丝滑翻页的 HorizontalPager，通过 offset {} 跟随筛选条折叠平滑上移
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .offset {
                                    val currentOffset = filterBarState.offset.takeIf { !it.isNaN() } ?: 0f
                                    IntOffset(0, (filterBarHeightPx + currentOffset).roundToInt())
                                },
                    ) {
                        PullToRefreshBox(
                            isRefreshing = uiState.isRefreshing || (!uiState.hasSchedules && uiState.isLoading),
                            onRefresh = { viewModel.refresh(force = true) },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            when {
                                !uiState.hasSchedules && uiState.isLoading -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.TopCenter,
                                    ) {
                                        ScheduleTimelineSkeleton(
                                            modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
                                        )
                                    }
                                }

                                !uiState.hasSchedules && uiState.error != null -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
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
                                            onSwitchToAll = viewModel::toggleOnlyWatching,
                                            listState = listState,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 筛选条：自然尺寸测量，仅在尺寸变化时更新锚点；仅在 placement 阶段平移
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .onSizeChanged { size ->
                                    val height = size.height.toFloat()
                                    if (height > 0f && height != filterBarHeightPx) {
                                        filterBarHeightPx = height
                                        filterBarState.updateAnchors(
                                            DraggableAnchors {
                                                FilterBarValue.EXPANDED at 0f
                                                FilterBarValue.COLLAPSED at -height
                                            },
                                        )
                                    }
                                }.offset {
                                    val currentOffset = filterBarState.offset.takeIf { !it.isNaN() } ?: 0f
                                    IntOffset(0, currentOffset.roundToInt())
                                }.anchoredDraggable(
                                    state = filterBarState,
                                    orientation = Orientation.Vertical,
                                    flingBehavior = filterBarFlingBehavior,
                                ),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        FilterAndMetaBar(
                            totalCount = currentPageTotal,
                            watchingCount = currentPageWatching,
                            onlyWatching = uiState.onlyWatching,
                            onToggleOnlyWatching = viewModel::toggleOnlyWatching,
                            isLoggedIn = uiState.isLoggedIn,
                            onPromptLogin = viewModel::promptLogin,
                            modifier = Modifier.widthIn(max = 840.dp),
                        )
                    }
                }
            }

            BgmTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.feature_schedule_title),
                    )
                },
                actions = {
                    FeatureGate(capability = { it.isAiAssistantEnabled && onAssistantClick != null }) {
                        IconButton(onClick = onAssistantClick ?: {}) {
                            Icon(
                                imageVector = BgmIcons.Assistant,
                                contentDescription = stringResource(R.string.feature_schedule_cd_ai_assistant),
                            )
                        }
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = BgmIcons.Search,
                            contentDescription = stringResource(R.string.feature_schedule_cd_search),
                        )
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
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
    onSwitchToAll: () -> Unit,
    listState: LazyListState,
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

    // 首次进入今天页时直接定位到「现在」附近，而不是永远停在第 0 个时段
    val hasAnchoredToNow = remember { mutableStateOf(false) }
    LaunchedEffect(isTodayPage, timeGrouped) {
        if (!isTodayPage || hasAnchoredToNow.value || timeGrouped.isEmpty()) return@LaunchedEffect
        val headerItemCount = if (uiState.isOfflineCache) 1 else 0
        listState.scrollToItem(headerItemCount + nowSlotIndex)
        hasAnchoredToNow.value = true
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
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
                        item(key = "now_line_$pageIndex") {
                            ScheduleNowIndicator(nowMinutesCst = nowMinutes)
                        }
                        emittedNowLine = true
                    }
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
