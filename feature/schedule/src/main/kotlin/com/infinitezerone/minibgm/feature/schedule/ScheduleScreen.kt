package com.infinitezerone.minibgm.feature.schedule

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.AiringReminderPermissionDialog
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.launchStreamingUrl
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import com.infinitezerone.minibgm.feature.schedule.components.FilterAndMetaBar
import com.infinitezerone.minibgm.feature.schedule.components.ModernDateCapsuleStrip
import com.infinitezerone.minibgm.feature.schedule.components.NextUpActionCard
import com.infinitezerone.minibgm.feature.schedule.components.OfflineCacheBanner
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleCatchupSection
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
    onAssistantClick: () -> Unit = {},
    onSourceSearch: (String) -> Unit = {},
    onPlayClick: (PlayerRoute) -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
    viewModel: ScheduleViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var selectedScheduleForSources by remember { mutableStateOf<AirSchedule?>(null) }
    var appNotInstalledPrompt by remember { mutableStateOf<Pair<String, String>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    var hasDismissedAiringReminderPrompt by rememberSaveable { mutableStateOf(false) }
    var showAiringReminderPrompt by remember { mutableStateOf(false) }
    var pendingReminderSubjectTitle by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.enableAiringReminder()
            }
        }

    val handleToggleWatching: (Long) -> Unit = { subjectId ->
        val wasWatching = uiState.watchingSubjectIds.contains(subjectId)
        viewModel.toggleWatching(subjectId)
        if (!wasWatching && uiState.isLoggedIn) {
            val systemAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (!systemAllowed && !hasDismissedAiringReminderPrompt) {
                val subjectTitle =
                    uiState.weeklySchedules.values
                        .flatten()
                        .firstOrNull { it.bgmId == subjectId }
                        ?.let { it.titleCn.ifBlank { it.title } }
                pendingReminderSubjectTitle = subjectTitle
                showAiringReminderPrompt = true
            }
        }
    }

    val handleLaunchStreamingUrl: (String) -> Unit = { url ->
        context.launchStreamingUrl(
            url = url,
            onAppNotInstalled = { appName, webUrl ->
                appNotInstalledPrompt = appName to webUrl
            },
        )
    }

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
                    IconButton(onClick = onAssistantClick) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "AI 追番助手",
                        )
                    }
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
                remember(uiState.weeklySchedules, uiState.watchingSubjectIds) {
                    (1..7).associateWith { uiState.getWatchingCountForWeekday(it) }
                }

            // 顶部星期胶囊导航（指示器 + 快速点击锚点）
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
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
                    watchingCountMap = watchingCountMap,
                    modifier = Modifier.widthIn(max = 840.dp),
                )
            }

            val currentWeekdayTotal = uiState.getTotalCountForWeekday(uiState.selectedWeekday)
            val currentWeekdayWatching = uiState.getWatchingCountForWeekday(uiState.selectedWeekday)

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                FilterAndMetaBar(
                    totalCount = currentWeekdayTotal,
                    watchingCount = currentWeekdayWatching,
                    onlyWatching = uiState.onlyWatching,
                    onToggleOnlyWatching = viewModel::toggleOnlyWatching,
                    isLoggedIn = uiState.isLoggedIn,
                    onPromptLogin = viewModel::promptLogin,
                    modifier = Modifier.widthIn(max = 840.dp),
                )
            }

            // 主体：左右手势丝滑翻页的 HorizontalPager
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !uiState.hasSchedules && uiState.isLoading && uiState.weeklySchedules.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            ScheduleTimelineSkeleton(
                                modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
                            )
                        }
                    }

                    !uiState.hasSchedules && uiState.error != null && uiState.weeklySchedules.isEmpty() -> {
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
                            val weekday = page + 1
                            val isTodayPage = weekday == uiState.todayWeekday
                            val timeGrouped =
                                remember(uiState.weeklySchedules, weekday, uiState.onlyWatching, uiState.watchingSubjectIds) {
                                    uiState.getTimeGroupedSchedulesForWeekday(weekday)
                                }
                            val allDaySchedules =
                                remember(uiState.weeklySchedules, weekday, uiState.onlyWatching, uiState.watchingSubjectIds) {
                                    uiState.getAllDaySchedulesForWeekday(weekday)
                                }
                            val listState = weekdayListStates[weekday] ?: rememberLazyListState()

                            DayScheduleList(
                                weekday = weekday,
                                isTodayPage = isTodayPage,
                                timeGrouped = timeGrouped,
                                allDaySchedules = allDaySchedules,
                                uiState = uiState,
                                onSubjectClick = onSubjectClick,
                                onToggleWatching = handleToggleWatching,
                                onMarkEpisodeWatched = { subjectId, ep ->
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    viewModel.markEpisodeWatched(subjectId, ep)
                                },
                                onDismissNextUpAction = viewModel::dismissNextUpAction,
                                onShowSources = { selectedScheduleForSources = it },
                                onPlayClick = handleLaunchStreamingUrl,
                                onSwitchToAll = viewModel::toggleOnlyWatching,
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
            onOpenUrl = handleLaunchStreamingUrl,
            onAiSourceSearch = {
                val title = schedule.titleCn.ifBlank { schedule.title }
                onSourceSearch(
                    "帮我找《$title》的可播放资源，直接给我能播放的地址和集数列表（Bangumi 条目号 ${schedule.bgmId}）",
                )
            },
            onInternalPlayClick = onPlayClick,
        )
    }

    appNotInstalledPrompt?.let { (appName, webUrl) ->
        AlertDialog(
            onDismissRequest = { appNotInstalledPrompt = null },
            title = { Text("未安装 $appName 客户端") },
            text = { Text("未检测到 $appName 客户端，是否在应用内使用浏览器打开该播放源？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        appNotInstalledPrompt = null
                        context.launchWebUrl(webUrl)
                    },
                ) {
                    Text("浏览器打开")
                }
            },
            dismissButton = {
                TextButton(onClick = { appNotInstalledPrompt = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (uiState.showLoginPromptDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLoginPrompt,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            },
            title = {
                Text(
                    text = "请先登录 Bangumi 账号",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "追番与打卡需要同步至您的 Bangumi 账号，登录后即可随手收藏、打卡并同步进度。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val authorizeUrl = viewModel.beginLogin()
                            context.launchWebUrl(authorizeUrl, isAuth = true)
                        }
                    },
                ) {
                    Text("立即登录")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLoginPrompt) {
                    Text("稍后再说")
                }
            },
        )
    }

    if (showAiringReminderPrompt) {
        AiringReminderPermissionDialog(
            subjectTitle = pendingReminderSubjectTitle,
            onConfirm = {
                showAiringReminderPrompt = false
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.enableAiringReminder()
                }
            },
            onDismiss = {
                showAiringReminderPrompt = false
                hasDismissedAiringReminderPrompt = true
            },
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
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onToggleWatching: (Long) -> Unit,
    onMarkEpisodeWatched: (Long, Int) -> Unit,
    onDismissNextUpAction: () -> Unit,
    onShowSources: (AirSchedule) -> Unit,
    onPlayClick: (String) -> Unit,
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

            if (isTodayPage && uiState.nextUpAction != null && !uiState.isActionDismissed) {
                item(key = "next_up_action_card") {
                    NextUpActionCard(
                        action = uiState.nextUpAction,
                        onPlayClick = onPlayClick,
                        onMarkWatched = onMarkEpisodeWatched,
                        onDismiss = onDismissNextUpAction,
                        onClick = { onSubjectClick(SubjectDetailRoute(uiState.nextUpAction.subjectId)) },
                    )
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
                    ScheduleDayEmptyNote(
                        onlyWatching = uiState.onlyWatching,
                        onSwitchToAll = onSwitchToAll,
                    )
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
                            onOpenUrl = onPlayClick,
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
                            onOpenUrl = onPlayClick,
                        )
                    }
                }
            }
        }
    }
}
