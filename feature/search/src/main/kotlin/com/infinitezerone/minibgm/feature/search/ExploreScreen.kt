package com.infinitezerone.minibgm.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmLoginPromptDialog
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.ObserveAsEvents
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.feature.search.components.ActiveCustomFilterBar
import com.infinitezerone.minibgm.feature.search.components.ExploreEmptyState
import com.infinitezerone.minibgm.feature.search.components.ExploreErrorState
import com.infinitezerone.minibgm.feature.search.components.ExploreFilterBottomSheet
import com.infinitezerone.minibgm.feature.search.components.ExploreSkeletonLoading
import com.infinitezerone.minibgm.feature.search.components.MoodFilterRow
import com.infinitezerone.minibgm.feature.search.components.WaterfallGridList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 统一探索发现中心界面：
 * - 顶部双 Tab：【季度片单】(季度目录、产地/形式/排序筛选、海报与行式双视图、1-Tap快捷追番) + 【淘番榜单】(殿堂神作、高分口碑、心境/题材漫游、多维筛选瀑布流)；
 * - 支持左右手势滑动切换双 Tab，并保持未显示 Tab 的界面与滚动状态；
 * - 淘番榜单支持延迟加载（滑动展示后才发起请求），避免进入探索时并发拉取。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onLoginRequest: () -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
    exploreViewModel: ExploreViewModel = koinViewModel(),
    seasonalGuideViewModel: SeasonalGuideViewModel = koinViewModel(),
) {
    val tabs = remember { listOf("季度片单", "淘番榜单") }
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    val exploreUiState by exploreViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilterBottomSheet by remember { mutableStateOf(false) }
    val filterSheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val exploreGridState = rememberLazyStaggeredGridState()
    var customFilterExpanded by rememberSaveable { mutableStateOf(false) }

    val isFilterActive = exploreUiState.isCustomFilterActive

    // 延迟加载：仅当切换/滑动至淘番榜单（Tab 1）时才触发首次网络请求
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) {
            exploreViewModel.loadIfNeeded()
        }
    }

    LaunchedEffect(exploreGridState, customFilterExpanded) {
        if (!customFilterExpanded) return@LaunchedEffect
        snapshotFlow { exploreGridState.isScrollInProgress }.first { it }
        customFilterExpanded = false
    }

    if (scrollToTop != null) {
        ObserveAsEvents(scrollToTop) {
            if (pagerState.currentPage == 1) {
                exploreGridState.animateScrollToItem(0)
            }
        }
    }

    LaunchedEffect(exploreUiState.userMessage) {
        val msg = exploreUiState.userMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            exploreViewModel.clearUserMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    BgmTopAppBar(
                        title = {
                            Text(
                                text = "探索发现",
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        actions = {
                            // 仅在淘番榜单 Tab 下展示高级筛选按钮
                            if (pagerState.currentPage == 1) {
                                IconButton(onClick = { showFilterBottomSheet = true }) {
                                    BadgedBox(
                                        badge = {
                                            if (isFilterActive) {
                                                Badge(containerColor = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = BgmIcons.FilterList,
                                            contentDescription = "高级筛选",
                                            tint =
                                                if (isFilterActive) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                        )
                                    }
                                }
                            }

                            // 进入全域深度检索
                            IconButton(onClick = onSearchClick) {
                                Icon(
                                    imageVector = BgmIcons.SearchBorder,
                                    contentDescription = "搜索",
                                )
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                    )

                    // 发现中心双 Tab 切换栏（与 HorizontalPager 双向联动）
                    PrimaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        },
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                text = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                            )
                        }
                    }
                }
            },
            snackbarHost = {
                BgmSnackbarHost(
                    hostState = snackbarHostState,
                    isTopLevel = true,
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> {
                            // Tab 0: 季度片单（保持原有状态与滚动进度）
                            SeasonalGuideContent(
                                onSubjectClick = onSubjectClick,
                                modifier = Modifier.fillMaxSize(),
                                viewModel = seasonalGuideViewModel,
                                onLoginRequest = onLoginRequest,
                                scrollToTop = if (pagerState.currentPage == 0) scrollToTop else null,
                                isTopLevel = true,
                            )
                        }

                        1 -> {
                            // Tab 1: 淘番漫游与榜单瀑布流（滑动展示后才发起请求，切换保留状态）
                            PullToRefreshBox(
                                isRefreshing = exploreUiState.isRefreshing,
                                onRefresh = exploreViewModel::refresh,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // 顶部筛选区：自定义筛选生效时呈现紧凑可折叠的摘要条；预设模式下展示场景胶囊
                                    if (isFilterActive) {
                                        ActiveCustomFilterBar(
                                            uiState = exploreUiState,
                                            expanded = customFilterExpanded,
                                            onToggleExpanded = { customFilterExpanded = !customFilterExpanded },
                                            onClearSeason = { exploreViewModel.onSeasonSelect(ALL_TIME_SEASON) },
                                            onClearCategory = { exploreViewModel.onCategorySelect(ExploreCategory.ANIME) },
                                            onTagToggle = exploreViewModel::onTagToggle,
                                            onClearSort = { exploreViewModel.onSortSelect(ExploreSort.RANK) },
                                            onResetAll = {
                                                customFilterExpanded = false
                                                exploreViewModel.onMoodSelect(ExploreMood.MASTERPIECE)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        MoodFilterRow(
                                            selectedMood = exploreUiState.selectedMood,
                                            onMoodSelect = exploreViewModel::onMoodSelect,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }

                                    // 加载进度条（静默加载或分页时展示）
                                    if (exploreUiState.isLoading && exploreUiState.subjects.isNotEmpty()) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }

                                    // 3. 内容区主状态机
                                    when {
                                        // 首次全屏骨架屏
                                        exploreUiState.isLoading && exploreUiState.subjects.isEmpty() -> {
                                            ExploreSkeletonLoading(modifier = Modifier.fillMaxSize())
                                        }

                                        // 错误重试态
                                        exploreUiState.error != null && exploreUiState.subjects.isEmpty() -> {
                                            ExploreErrorState(
                                                errorMessage = exploreUiState.error ?: "未知网络异常",
                                                onRetry = exploreViewModel::refresh,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }

                                        // 空数据提示态
                                        exploreUiState.subjects.isEmpty() -> {
                                            ExploreEmptyState(
                                                onReset = { exploreViewModel.onMoodSelect(ExploreMood.MASTERPIECE) },
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }

                                        // 双列瀑布流真实数据
                                        else -> {
                                            WaterfallGridList(
                                                subjects = exploreUiState.subjects,
                                                wishedSubjectIds = exploreUiState.wishedSubjectIds,
                                                hotComments = exploreUiState.hotComments,
                                                selectedTags = exploreUiState.selectedTags,
                                                onTagClick = exploreViewModel::onTagToggle,
                                                hasMore = exploreUiState.hasMore,
                                                isLoadingMore = exploreUiState.isLoadingMore,
                                                onLoadMore = exploreViewModel::loadMore,
                                                onSubjectClick = onSubjectClick,
                                                onToggleWish = exploreViewModel::toggleWish,
                                                gridState = exploreGridState,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 高级多维筛选半屏抽屉 (ModalBottomSheet) - 仅在淘番榜单生效
        if (showFilterBottomSheet) {
            ExploreFilterBottomSheet(
                sheetState = filterSheetState,
                selectedSeason = exploreUiState.selectedSeason,
                onSeasonSelect = exploreViewModel::onSeasonSelect,
                selectedCategory = exploreUiState.selectedCategory,
                onCategorySelect = exploreViewModel::onCategorySelect,
                selectedTags = exploreUiState.selectedTags,
                onTagToggle = exploreViewModel::onTagToggle,
                onClearAllTags = exploreViewModel::onClearAllTags,
                onCustomTagSubmit = exploreViewModel::onCustomTagSubmit,
                selectedSort = exploreUiState.selectedSort,
                onSortSelect = exploreViewModel::onSortSelect,
                onResetAll = {
                    customFilterExpanded = false
                    exploreViewModel.onMoodSelect(ExploreMood.MASTERPIECE)
                },
                onDismiss = { showFilterBottomSheet = false },
            )
        }

        // 未登录引导弹窗
        if (exploreUiState.showLoginPromptDialog) {
            BgmLoginPromptDialog(
                description = "一键「想看 / 追番」需要同步至您的 Bangumi 账号，登录后即可随手收藏、打卡并同步进度。",
                onLogin = {
                    exploreViewModel.dismissLoginPrompt()
                    onLoginRequest()
                },
                onDismiss = exploreViewModel::dismissLoginPrompt,
            )
        }
    }
}
