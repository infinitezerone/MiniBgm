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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmCircleActionButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmFloatingPageScaffold
import com.infinitezerone.minibgm.core.designsystem.component.BgmFocusCard
import com.infinitezerone.minibgm.core.designsystem.component.BgmLoginPromptDialog
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmPillButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopBarMode
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
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
    val totalTopChromeHeight = statusBarTop + BgmSpacing.TopBar.height + 56.dp

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
        topScrimHeight = totalTopChromeHeight + 36.dp,
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            FeatureGate(capability = { it.isAiAssistantEnabled && onAssistantClick != null }) {
                                BgmCircleActionButton(
                                    onClick = onAssistantClick ?: {},
                                    icon = BgmIcons.Assistant,
                                    contentDescription = stringResource(R.string.feature_schedule_cd_ai_assistant),
                                )
                            }

                            BgmCircleActionButton(
                                onClick = onSearchClick,
                                icon = BgmIcons.Search,
                                contentDescription = stringResource(R.string.feature_schedule_cd_search),
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
                    modifier = Modifier.fillMaxWidth(),
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
                            topPadding = totalTopChromeHeight + 6.dp,
                            onPromptLogin = viewModel::promptLogin,
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
    onPromptLogin: () -> Unit = {},
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

    // 首次进入今天页时定位：有「接着看」Hero 时保留在首屏，无 Hero 时自动滑向当前时段
    val hasAnchoredToNow = remember { mutableStateOf(false) }
    LaunchedEffect(isTodayPage, timeGrouped, uiState.latestPlaybackHistory) {
        if (!isTodayPage || timeGrouped.isEmpty()) return@LaunchedEffect
        if (uiState.latestPlaybackHistory != null) {
            if (!hasAnchoredToNow.value) {
                listState.scrollToItem(0)
                hasAnchoredToNow.value = true
            }
            return@LaunchedEffect
        }
        if (!hasAnchoredToNow.value && nowSlotIndex > 2) {
            val headerItemCount = (if (uiState.isOfflineCache) 1 else 0) + 1
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
            contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = topPadding, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(TIMELINE_SLOT_SPACING),
            modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
        ) {
            if (isTodayPage && uiState.latestPlaybackHistory != null) {
                val history = uiState.latestPlaybackHistory
                item(key = "schedule_hero_next_up") {
                    val subtitleText =
                        if (history.episodeName.isNotBlank()) {
                            stringResource(
                                R.string.feature_schedule_hero_episode_format,
                                history.episodeSort.toInt().coerceAtLeast(1),
                                history.episodeName,
                            )
                        } else {
                            stringResource(
                                R.string.feature_schedule_hero_episode_only,
                                history.episodeSort.toInt().coerceAtLeast(1),
                            )
                        }
                    val statusText =
                        if (history.isCompleted) {
                            stringResource(R.string.feature_schedule_hero_completed)
                        } else {
                            stringResource(R.string.feature_schedule_hero_next_up)
                        }
                    val progressLabel =
                        if (history.durationMs > 0L) {
                            "${formatHistoryDuration(history.positionMs)} / ${formatHistoryDuration(history.durationMs)}"
                        } else {
                            null
                        }

                    BgmFocusCard(
                        title = history.subjectName,
                        subtitle = subtitleText,
                        onClick = {
                            onSubjectClick(
                                SubjectDetailRoute(
                                    subjectId = history.subjectId,
                                    initialName = history.subjectName,
                                    initialCoverUrl = history.coverUrl,
                                    source = "schedule_hero",
                                ),
                            )
                        },
                        cover = {
                            CoverImage(
                                url = history.coverUrl,
                                contentDescription = history.subjectName,
                                cornerRadius = 0.dp,
                                aspectRatio = 16f / 9.5f,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        statusLabel = statusText,
                        progress = history.progress.takeIf { history.durationMs > 0L },
                        progressLabel = progressLabel,
                        action = {
                            BgmPillButton(
                                text = stringResource(R.string.feature_schedule_hero_resume_cta),
                                icon = BgmIcons.PlayArrow,
                                onClick = {
                                    val route =
                                        PlayerRoute(
                                            subjectId = history.subjectId,
                                            episodeId = history.episodeId,
                                            streamUrl = history.streamUrl,
                                            episodeName = history.episodeName,
                                            subjectName = history.subjectName,
                                            episodeSort = history.episodeSort,
                                            initialRuleId = history.sourceId,
                                            initialSourceName = history.sourceName,
                                            coverUrl = history.coverUrl,
                                        )
                                    if (onPlayClick != null) {
                                        onPlayClick(route)
                                    } else {
                                        onSubjectClick(
                                            SubjectDetailRoute(
                                                subjectId = history.subjectId,
                                                initialName = history.subjectName,
                                                initialCoverUrl = history.coverUrl,
                                                source = "schedule_hero",
                                            ),
                                        )
                                    }
                                },
                            )
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            item(key = "filter_bar_$pageIndex") {
                FilterAndMetaBar(
                    totalCount = uiState.getTotalCountForPage(pageIndex),
                    watchingCount = uiState.getWatchingCountForPage(pageIndex),
                    onlyWatching = uiState.onlyWatching,
                    onToggleOnlyWatching = onSwitchToAll,
                    isLoggedIn = uiState.isLoggedIn,
                    onPromptLogin = onPromptLogin,
                )
            }

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

private fun formatHistoryDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
