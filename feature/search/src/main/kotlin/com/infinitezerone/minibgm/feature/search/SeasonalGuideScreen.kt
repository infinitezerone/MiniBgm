package com.infinitezerone.minibgm.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonBox
import com.infinitezerone.minibgm.core.designsystem.component.rememberSkeletonState
import com.infinitezerone.minibgm.core.designsystem.theme.LocalWindowAdaptiveInfo
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import com.infinitezerone.minibgm.feature.search.components.OngoingAnimeCard
import com.infinitezerone.minibgm.feature.search.components.SeasonalAnimeCard
import com.infinitezerone.minibgm.feature.search.components.SeasonalAnimeRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 季度新番导视大盘界面（独立二级页容器）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonalGuideScreen(
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialYear: Int = 0,
    initialSeasonMonth: Int = 0,
    viewModel: SeasonalGuideViewModel =
        koinViewModel(
            parameters = { parametersOf(initialYear, initialSeasonMonth) },
        ),
) {
    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = {
                    Text(
                        text = "新番导视",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        SeasonalGuideContent(
            onSubjectClick = onSubjectClick,
            modifier = Modifier.padding(innerPadding),
            viewModel = viewModel,
        )
    }
}

/**
 * 季度新番导视大盘可复用内容组件：
 * 支持直接嵌入探索双 Tab 页面或作为独立二级页面主体。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonalGuideContent(
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeasonalGuideViewModel = koinViewModel(),
    scrollToTop: Flow<Unit>? = null,
    isTopLevel: Boolean = false,
) {
    val adaptiveInfo = LocalWindowAdaptiveInfo.current
    val isWideScreen = adaptiveInfo.isWide
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    var showSeasonPicker by remember { mutableStateOf(false) }

    LaunchedEffect(scrollToTop, uiState.viewMode) {
        scrollToTop?.collect {
            // 只滚动当前形态的容器：另一个容器下次切回时本就停在原位，不该被顺带重置
            when (uiState.viewMode) {
                SeasonalViewMode.LIST -> listState.animateScrollToItem(0)
                SeasonalViewMode.POSTER -> gridState.animateScrollToItem(0)
            }
        }
    }

    // 弹出 Snackbar 消息提示
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // 触底无限加载监控：列表与网格的滚动状态互相独立，必须订阅当前形态对应的那一个，
    // 否则切到列表后会继续读网格的布局信息（网格不再滚动，永远触不了底）
    LaunchedEffect(uiState.viewMode, uiState.hasMore, uiState.isLoadingMore) {
        snapshotFlow {
            val (totalItems, lastVisible) =
                when (uiState.viewMode) {
                    SeasonalViewMode.LIST -> {
                        val info = listState.layoutInfo
                        info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: 0)
                    }

                    SeasonalViewMode.POSTER -> {
                        val info = gridState.layoutInfo
                        info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: 0)
                    }
                }
            totalItems > 0 && lastVisible >= totalItems - 6
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore && !uiState.isLoading) {
                viewModel.loadMore()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 单行紧凑复合过滤栏：左侧年份季度选择胶囊 + 右侧播出形式滚动过滤
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 1. 复合档期选择胶囊 [ 2026 · 4月春 ▾ ]
                    Surface(
                        onClick = { showSeasonPicker = true },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.height(36.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${uiState.selectedYear} · ${uiState.selectedQuarter.displayLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "选择档期",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 2. 播出形式分类横向滚动流 (全部 / TV动画 / 网络独播 / 剧场版/OVA)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(SeasonCategoryFilter.entries) { category ->
                            FilterChip(
                                selected = uiState.selectedCategory == category,
                                onClick = { viewModel.selectCategory(category) },
                                label = {
                                    Text(
                                        text = category.label,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                modifier = Modifier.height(36.dp),
                                border = null,
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 3. 视图形态切换（紧凑列表 ↔ 海报网格），与搜索结果页的操作条同形态
                    IconButton(
                        onClick = viewModel::toggleViewMode,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector =
                                if (uiState.viewMode == SeasonalViewMode.LIST) {
                                    Icons.Filled.GridView
                                } else {
                                    Icons.AutoMirrored.Filled.ViewList
                                },
                            contentDescription =
                                if (uiState.viewMode == SeasonalViewMode.LIST) {
                                    "切换为海报网格"
                                } else {
                                    "切换为紧凑列表"
                                },
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }

                // 加载进度条
                if (uiState.isLoading && uiState.subjects.isNotEmpty()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // 4. 内容主体状态机
                when {
                    // 首次加载全屏骨架屏（跟随当前形态，避免在列表视图下先闪一屏网格）
                    uiState.isLoading && uiState.subjects.isEmpty() -> {
                        if (uiState.viewMode == SeasonalViewMode.LIST) {
                            SeasonalGuideSkeletonList(modifier = Modifier.fillMaxSize())
                        } else {
                            SeasonalGuideSkeletonGrid(modifier = Modifier.fillMaxSize())
                        }
                    }

                    // 错误重试态
                    uiState.error != null && uiState.subjects.isEmpty() -> {
                        SeasonalGuideErrorState(
                            errorMessage = uiState.error ?: "加载新番导视失败",
                            onRetry = viewModel::retry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // 空结果态（「本季首播」与「本季连载中」两组都为空才算真的没有）
                    !uiState.isLoading &&
                        uiState.filteredSubjects.isEmpty() &&
                        uiState.filteredOngoingSubjects.isEmpty() -> {
                        SeasonalGuideEmptyState(
                            selectedYear = uiState.selectedYear,
                            selectedQuarter = uiState.selectedQuarter,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // 紧凑行式列表（默认）：一屏约 6-7 条，标题之外还能读到集数／放送电视台／题材
                    uiState.viewMode == SeasonalViewMode.LIST -> {
                        LazyColumn(
                            state = listState,
                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = if (isTopLevel && !isWideScreen) 96.dp else 32.dp,
                                ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // 「本季连载中」保持横向分组：它自成一组且条目有限，横滑一次即可扫完
                            if (uiState.filteredOngoingSubjects.isNotEmpty()) {
                                item(key = "ongoing") {
                                    OngoingAnimeSection(
                                        subjects = uiState.filteredOngoingSubjects,
                                        onSubjectClick = onSubjectClick,
                                    )
                                }
                            }

                            items(
                                items = uiState.filteredSubjects,
                                key = { it.id },
                            ) { subject ->
                                SeasonalAnimeRow(
                                    subject = subject,
                                    isWished = uiState.wishedSubjectIds.contains(subject.id),
                                    isDoing = uiState.doingSubjectIds.contains(subject.id),
                                    onSubjectClick = onSubjectClick,
                                    onToggleCollection = viewModel::toggleCollection,
                                )
                            }

                            if (uiState.isLoadingMore) {
                                item(key = "loadingMore") {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }

                    // 海报展板网格（2:3 自适应列宽，手机两列）
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            state = gridState,
                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = if (isTopLevel && !isWideScreen) 96.dp else 32.dp,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // 「本季连载中」：首播早于本季、但本季确有播出事件的长期连载番，
                            // 与下方"本季首播"网格互补，二者不会出现同一条目
                            if (uiState.filteredOngoingSubjects.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    OngoingAnimeSection(
                                        subjects = uiState.filteredOngoingSubjects,
                                        onSubjectClick = onSubjectClick,
                                    )
                                }
                            }

                            items(
                                items = uiState.filteredSubjects,
                                key = { it.id },
                            ) { subject ->
                                SeasonalAnimeCard(
                                    subject = subject,
                                    isWished = uiState.wishedSubjectIds.contains(subject.id),
                                    isDoing = uiState.doingSubjectIds.contains(subject.id),
                                    onSubjectClick = onSubjectClick,
                                    onToggleCollection = viewModel::toggleCollection,
                                )
                            }

                            if (uiState.isLoadingMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        BgmSnackbarHost(
            hostState = snackbarHostState,
            isTopLevel = isTopLevel,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 未登录引导弹窗
        if (uiState.showLoginPromptDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissLoginPrompt,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = {
                    Text(
                        text = "登录开启快捷追番",
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(
                        text = "登录 Bangumi 账号后，即可一键追踪当季新番，收藏状态将实时同步至云端与放送日历。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val authUrl = viewModel.beginLogin()
                                context.launchWebUrl(authUrl, isAuth = true)
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

        // 档期选择半屏抽屉
        if (showSeasonPicker) {
            SeasonPickerBottomSheet(
                selectedYear = uiState.selectedYear,
                selectedQuarter = uiState.selectedQuarter,
                currentYear = uiState.currentYear,
                currentQuarter = uiState.currentQuarter,
                availableYears = uiState.availableYears,
                onSelectSeason = { year, quarter ->
                    viewModel.selectSeason(year, quarter)
                    showSeasonPicker = false
                },
                onDismiss = { showSeasonPicker = false },
            )
        }
    }
}

/**
 * 「本季连载中」横向分组。
 *
 * 只在当季/未来季出现：播出事件来自滚动快照，历史季度无数据可依，
 * 与其给出"本季没有连载番"的错误结论，不如整组不显示。
 */
@Composable
private fun OngoingAnimeSection(
    subjects: List<Subject>,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "本季连载中",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "首播早于本季 · ${subjects.size} 部",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(
                items = subjects,
                key = { it.id },
            ) { subject ->
                OngoingAnimeCard(subject = subject, onClick = onSubjectClick)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

/** 骨架屏加载列表（行式：58dp 封面块 + 三根文本条，与真实行等高） */
@Composable
private fun SeasonalGuideSkeletonList(modifier: Modifier = Modifier) {
    val skeletonState = rememberSkeletonState()
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
        modifier = modifier,
    ) {
        items(6) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(
                    modifier =
                        Modifier
                            .width(58.dp)
                            .height(83.dp),
                    shape = RoundedCornerShape(8.dp),
                    state = skeletonState,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SkeletonBox(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.9f)
                                .height(16.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                    SkeletonBox(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.55f)
                                .height(14.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                    SkeletonBox(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.35f)
                                .height(12.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                }
            }
        }
    }
}

/** 骨架屏加载网格 */
@Composable
private fun SeasonalGuideSkeletonGrid(modifier: Modifier = Modifier) {
    val skeletonState = rememberSkeletonState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
        modifier = modifier,
    ) {
        items(8) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
            ) {
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    shape = RoundedCornerShape(12.dp),
                    state = skeletonState,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.85f)
                            .height(16.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
                Spacer(modifier = Modifier.height(4.dp))
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
            }
        }
    }
}

/** 错误提示与重试状态 */
@Composable
private fun SeasonalGuideErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Text("重新加载")
            }
        }
    }
}

/** 空数据提示状态 */
@Composable
private fun SeasonalGuideEmptyState(
    selectedYear: Int,
    selectedQuarter: SeasonQuarter,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = "${selectedYear}年 ${selectedQuarter.displayLabel} 暂无收录番剧",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "可尝试切换年份或季度查看其他番剧导视",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 档期选择半屏抽屉：快速切换年份与季度，支持一键回到当季
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonPickerBottomSheet(
    selectedYear: Int,
    selectedQuarter: SeasonQuarter,
    currentYear: Int,
    currentQuarter: SeasonQuarter,
    availableYears: List<Int>,
    onSelectSeason: (year: Int, quarter: SeasonQuarter) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tempYear by remember { mutableIntStateOf(selectedYear) }

    BgmModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
    val dateSpan =
        when (quarter) {
            SeasonQuarter.WINTER -> "1月 ~ 3月"
            SeasonQuarter.SPRING -> "4月 ~ 6月"
            SeasonQuarter.SUMMER -> "7月 ~ 9月"
            SeasonQuarter.AUTUMN -> "10月 ~ 12月"
        }

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
