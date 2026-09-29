package com.infinitezerone.minibgm.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 可见条目少于这个数就认为列表填不满一屏——此时滚不动，触底加载无从触发，需要主动补取。
 * 取两屏左右的量，既避免用户看到"总共就这几条"的错觉，也不至于每次都把整季拉完。
 */
private const val MIN_VISIBLE_ITEMS = 12

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
    // uiState 是 combine 出来的只读投影（StateFlow），collect 成值后照常按 UiState 变化重组
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    var showSeasonPicker by remember { mutableStateOf(false) }

    // 筛选栏的展开态。默认收起：产地与形式都是"设一次就不再碰"的控件，让它们常驻等于永久吃掉列表高度
    // （原来三行 128dp，在 792dp 高的机器上占 16%）。收起后只剩一行摘要，当前筛选仍然一眼可见。
    var filterExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(scrollToTop, uiState.viewMode) {
        scrollToTop?.collect {
            // 只滚动当前形态的容器：另一个容器下次切回时本就停在原位，不该被顺带重置
            when (uiState.viewMode) {
                SeasonalViewMode.LIST -> listState.animateScrollToItem(0)
                SeasonalViewMode.POSTER -> gridState.animateScrollToItem(0)
            }
        }
    }

    // 展开后一旦列表开始滚动就自动收起：展开态一直挂着，等于又回到"三行常驻"。
    // 注意程序化回顶（见下一个 effect）也会点亮 isScrollInProgress，所以选中筛选时要先收起面板，
    // 否则回顶动作会把收起的动作和点击的语义搅在一起、看不出是谁触发的。
    LaunchedEffect(filterExpanded, uiState.viewMode) {
        if (!filterExpanded) return@LaunchedEffect
        val scrollable: ScrollableState =
            when (uiState.viewMode) {
                SeasonalViewMode.LIST -> listState
                SeasonalViewMode.POSTER -> gridState
            }
        snapshotFlow { scrollable.isScrollInProgress }.first { it }
        filterExpanded = false
    }

    // 换了产地或形式，列表整体换了一批条目，停在原滚动位置没有意义
    LaunchedEffect(uiState.selectedOrigin, uiState.selectedForms) {
        when (uiState.viewMode) {
            SeasonalViewMode.LIST -> listState.scrollToItem(0)
            SeasonalViewMode.POSTER -> gridState.scrollToItem(0)
        }
    }

    // 一次性事件单独收集：Snackbar 不能放进状态流里——状态可重组、可重复读取，
    // 放进去就会在旋转屏幕 / 回到本页时把同一条提示再弹一遍，还得配一个"已读"回写来擦除。
    // Channel 消费即消失，两条通道各司其职、互不污染。
    LaunchedEffect(Unit) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                is UiEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }

    // 触底无限加载监控：列表与网格的滚动状态互相独立，必须订阅当前形态对应的那一个，
    // 否则切到列表后会继续读网格的布局信息（网格不再滚动，永远触不了底）。
    //
    // 这里刻意用 viewMode 作为唯一 key、并在 collect 内**实时**读 uiState：
    // LaunchedEffect 的闭包只在 key 变化时更新，若把 isLoading 也当成闭包变量用，
    // 首屏结束（isLoading true→false）时 key 没变、闭包不更新，判断会永远停在"正在加载"，
    // 于是翻页再也触发不了——表现就是列表停在第一批条目上不动。
    // 另外这个条件在"可见条目不足一屏"时恒为真，正好也兜住了客户端过滤把结果筛得只剩几条的情况。
    LaunchedEffect(uiState.viewMode) {
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
            val state = uiState
            if (shouldLoadMore && state.hasMore && !state.isLoadingMore && !state.isLoading) {
                viewModel.loadMore()
            }
        }
    }

    // 兜底：上面的触底检测靠 snapshotFlow 驱动，而 snapshotFlow 会去重——重新求值结果仍为 true 时
    // 不会再发射，所以"已经到底但内容不足一屏"这种情况只会触发一次。
    // 产地「欧美」、形式「全部」/「短片 / MV」这三档是客户端过滤，可能把整页整页地筛掉、只剩几条，
    // 而内容不足一屏就滚不动，触底也就无从触发。这里在可见条目填不满两屏时主动继续取，直到填满或取尽。
    LaunchedEffect(uiState.viewMode, uiState.filteredSubjects.size, uiState.hasMore, uiState.isLoading) {
        val state = uiState
        if (state.hasMore &&
            !state.isLoading &&
            !state.isLoadingMore &&
            state.filteredSubjects.size < MIN_VISIBLE_ITEMS
        ) {
            viewModel.loadMore()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 筛选栏常驻只有一行：档期 + 当前筛选摘要 + 视图切换。
                // 产地与形式收进摘要里点开才展开（展开后一滚动就自动收起）——它们都是"设一次就不再碰"的控件，
                // 让两行 chip 常驻等于永久吃掉列表高度：原来三行合计 128dp，在 792dp 高的机器上占 16%。
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 1. 复合档期选择胶囊 [ 2026 · 10月秋 ▾ ]
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

                        // 2. 筛选摘要：收起时以一行文字交代"现在筛的是什么"，点它才展开两级 chip。
                        // 用 weight(1f) 吃掉中间全部剩余宽度，而不是靠内容自适应 + 另一端加 Spacer——
                        // 那样依赖 Compose 对"带权重但 fill=false 的子项"余量再分配的实现细节，
                        // 一旦不按预期分配，视图切换按钮就贴不到右边。摘要文字自己带省略号兜底。
                        Surface(
                            onClick = { filterExpanded = !filterExpanded },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier =
                                Modifier
                                    .height(36.dp)
                                    .weight(1f),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = uiState.filterSummary,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector =
                                        if (filterExpanded) {
                                            Icons.Filled.KeyboardArrowUp
                                        } else {
                                            Icons.Filled.KeyboardArrowDown
                                        },
                                    contentDescription = if (filterExpanded) "收起筛选" else "展开筛选",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

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

                    AnimatedVisibility(visible = filterExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // 一级筛选：产地（单选）。条件下推服务端，切换需重新取数，
                            // 否则总数会是"已加载的那几十条"里的子集
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                SeasonOriginFilter.entries.forEach { origin ->
                                    SeasonalGuideFilterChip(
                                        label = origin.label,
                                        selected = uiState.selectedOrigin == origin,
                                        onClick = {
                                            filterExpanded = false
                                            viewModel.selectOrigin(origin)
                                        },
                                    )
                                }
                            }

                            // 二级筛选：放送形式（**可多选**，全不选 = 不筛）。
                            // 三档按内容形态划分（正片 / 剧场版 / 短片 · MV），而不是按 platform 字段的
                            // TV / WEB 拆——后者与一级「产地」几乎完全重合，还会组合出近乎空集。
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                SeasonFormFilter.entries.forEach { form ->
                                    SeasonalGuideFilterChip(
                                        label = form.label,
                                        selected = form in uiState.selectedForms,
                                        onClick = {
                                            filterExpanded = false
                                            viewModel.toggleForm(form)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // 加载进度条
                if (uiState.isLoading && uiState.subjects.isNotEmpty()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // 5. 内容主体状态机
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
    // 显示真实首播窗口而不是「7月 ~ 9月」：季界在每月 21 日，业界口径下秋番可从 9 月下旬开播，
    // 若副标写「10月 ~ 12月」，列表里冒出 9 月的日期会让人以为数据错了
    val dateSpan = quarter.airDateLabel

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

/**
 * 导视筛选 chip。
 *
 * 产地（单选）与形式（多选）两行共用同一形态——两行只有"能不能多选"的差别，
 * 样式分叉会让人误以为层级也不同。多选的语义靠 chip 自身的选中态表达即可。
 */
@Composable
private fun SeasonalGuideFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        modifier = modifier.height(34.dp),
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
