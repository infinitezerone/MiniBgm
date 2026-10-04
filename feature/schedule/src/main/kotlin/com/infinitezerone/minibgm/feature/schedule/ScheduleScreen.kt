package com.infinitezerone.minibgm.feature.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmLoginPromptDialog
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.ConfirmDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.launchStreamingUrl
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import com.infinitezerone.minibgm.feature.schedule.components.FilterAndMetaBar
import com.infinitezerone.minibgm.feature.schedule.components.ModernDateCapsuleStrip
import com.infinitezerone.minibgm.feature.schedule.components.OfflineCacheBanner
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleDayEmptyNote
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleErrorState
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleSourcesBottomSheet
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleTimelineSkeleton
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleUntimedSection
import com.infinitezerone.minibgm.feature.schedule.components.TimelineSlotRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onAssistantClick: (() -> Unit)? = null,
    onSourceSearch: (String) -> Unit = {},
    onPlayClick: ((PlayerRoute) -> Unit)? = null,
    onLoginRequest: () -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
    viewModel: ScheduleViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var selectedScheduleForSources by remember { mutableStateOf<AirSchedule?>(null) }
    val overlayHostState = rememberOverlayHostState()
    val snackbarHostState = remember { SnackbarHostState() }

    val handleLaunchStreamingUrl: (String) -> Unit = { url ->
        context.launchStreamingUrl(
            url = url,
            onAppNotInstalled = { appName, webUrl ->
                coroutineScope.launch {
                    val confirmed =
                        overlayHostState.await(
                            ConfirmDialogAction(
                                title = "未安装 $appName 客户端",
                                message = "未检测到 $appName 客户端，是否在应用内使用浏览器打开该播放源？",
                                confirmText = "浏览器打开",
                            ),
                        )
                    if (confirmed) {
                        context.launchWebUrl(webUrl)
                    }
                }
            },
        )
    }

    // 13天平滑滑动的 Pager，初始定位到今天（索引 6）
    val pagerState =
        rememberPagerState(
            initialPage = ScheduleViewModel.TODAY_PAGE_INDEX,
            pageCount = { ScheduleViewModel.TOTAL_SCHEDULE_DAYS },
        )

    val pageListStates = List(ScheduleViewModel.TOTAL_SCHEDULE_DAYS) { rememberLazyListState() }

    // 登录引导横幅：核心循环（打卡/收藏/个人页）依赖登录，未登录时给一次轻量主动引导
    var loginBannerDismissed by rememberSaveable { mutableStateOf(false) }

    // 监听底栏「放送」Tab 再次点击回顶（非今天先平滑滚回今天，已经在今天则滚回列表顶部）
    LaunchedEffect(scrollToTop) {
        scrollToTop?.collect {
            if (pagerState.currentPage != ScheduleViewModel.TODAY_PAGE_INDEX) {
                pagerState.animateScrollToPage(ScheduleViewModel.TODAY_PAGE_INDEX)
            } else {
                pageListStates.getOrNull(ScheduleViewModel.TODAY_PAGE_INDEX)?.animateScrollToItem(0)
            }
        }
    }

    // 监听 ViewModel 提示消息（如快捷追番或标记已看反馈）
    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // 滑动 Pager 时，双向同步选中的天索引
    LaunchedEffect(pagerState.currentPage) {
        if (uiState.selectedPageIndex != pagerState.currentPage) {
            viewModel.selectPage(pagerState.currentPage)
        }
    }

    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = {
                    Text(
                        text = "放送时刻表",
                    )
                },
                actions = {
                    if (onAssistantClick != null) {
                        IconButton(onClick = onAssistantClick) {
                            Icon(
                                imageVector = BgmIcons.Assistant,
                                contentDescription = "AI 追番助手",
                            )
                        }
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = BgmIcons.Search,
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
        snackbarHost = {
            BgmSnackbarHost(
                hostState = snackbarHostState,
                isTopLevel = true,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            val watchingCountMap =
                remember(uiState.daySchedules, uiState.watchingSubjectIds) {
                    (0 until ScheduleViewModel.TOTAL_SCHEDULE_DAYS).associateWith {
                        uiState.getWatchingCountForPage(it)
                    }
                }

            // 登录引导横幅：核心循环（打卡/收藏/个人页）依赖登录，未登录时给一次轻量主动引导
            AnimatedVisibility(visible = !uiState.isLoggedIn && !loginBannerDismissed) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    ScheduleLoginNudgeBanner(
                        onLogin = {
                            loginBannerDismissed = true
                            viewModel.promptLogin()
                        },
                        onDismiss = { loginBannerDismissed = true },
                        modifier = Modifier.widthIn(max = 840.dp),
                    )
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

            val currentPageTotal = uiState.getTotalCountForPage(pagerState.currentPage)
            val currentPageWatching = uiState.getWatchingCountForPage(pagerState.currentPage)

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
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

            // 主体：左右手势丝滑翻页的 HorizontalPager（叠加「回到今天」浮钮）
            Box(modifier = Modifier.fillMaxSize()) {
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
                                    errorMessage = uiState.error ?: "网络连接异常",
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
                                    onShowSources = { selectedScheduleForSources = it },
                                    onSwitchToAll = viewModel::toggleOnlyWatching,
                                    listState = listState,
                                )
                            }
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
            onOpenUrl = handleLaunchStreamingUrl,
            onAiSourceSearch =
                if (onAssistantClick != null) {
                    {
                        val title = schedule.displayName
                        onSourceSearch(
                            "帮我找《$title》的可播放资源，直接给我能播放的地址和集数列表（Bangumi 条目号 ${schedule.bgmId}）",
                        )
                    }
                } else {
                    null
                },
            onInternalPlayClick =
                onPlayClick?.let { play ->
                    {
                        coroutineScope.launch {
                            play(viewModel.resolvePlayRoute(schedule))
                        }
                    }
                },
        )
    }

    BgmOverlayHost(hostState = overlayHostState) {
        confirmDialog()
    }

    if (uiState.showLoginPromptDialog) {
        BgmLoginPromptDialog(
            description = "追番与打卡需要同步至您的 Bangumi 账号，登录后即可随手收藏、打卡并同步进度。",
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
    onShowSources: (AirSchedule) -> Unit,
    onSwitchToAll: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                timeGrouped.forEach { (time, animeList) ->
                    item(key = "timeslot_${pageIndex}_$time") {
                        TimelineSlotRow(
                            time = time,
                            schedules = animeList,
                            isToday = isTodayPage,
                            watchingSubjectIds = uiState.watchingSubjectIds,
                            onSubjectClick = onSubjectClick,
                            onShowSources = onShowSources,
                        )
                    }
                }

                // ==================== 全天 / 网络独播待定番剧自然收容 ====================
                if (allDaySchedules.isNotEmpty()) {
                    item(key = "untimed_section_$pageIndex") {
                        ScheduleUntimedSection(
                            schedules = allDaySchedules,
                            watchingSubjectIds = uiState.watchingSubjectIds,
                            onSubjectClick = onSubjectClick,
                            onShowSources = onShowSources,
                        )
                    }
                }
            }
        }
    }
}

/** 登录引导横幅：低侵入的一次性引导，登录动作走既有登录弹窗与登录路由 */
@Composable
private fun ScheduleLoginNudgeBanner(
    onLogin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Text(
                text = "登录 Bangumi，开启追番打卡",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onLogin) {
                Text(text = "登录")
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = BgmIcons.Close,
                    contentDescription = "关闭引导",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
