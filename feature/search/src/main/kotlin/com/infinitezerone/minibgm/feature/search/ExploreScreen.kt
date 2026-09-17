package com.infinitezerone.minibgm.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import com.infinitezerone.minibgm.feature.search.components.ActiveFilterPillRow
import com.infinitezerone.minibgm.feature.search.components.ExploreEmptyState
import com.infinitezerone.minibgm.feature.search.components.ExploreErrorState
import com.infinitezerone.minibgm.feature.search.components.ExploreFilterBottomSheet
import com.infinitezerone.minibgm.feature.search.components.ExploreSkeletonLoading
import com.infinitezerone.minibgm.feature.search.components.MoodFilterRow
import com.infinitezerone.minibgm.feature.search.components.WaterfallGridList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 统一探索发现中心界面：
 * - 顶部双 Tab：【新番导视】(当季大盘、年份/四季切换、黄金比例海报、1-Tap快捷追番) + 【淘番榜单】(殿堂神作、高分口碑、心境/题材漫游、多维筛选瀑布流)；
 * - 消除探索与导视的重复冗余，打造一站式二次元发现大中心；
 * - 支持下拉刷新、触底无限加载、高级多维筛选半屏抽屉与未登录拦截。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onSeasonalGuideClick: () -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
    exploreViewModel: ExploreViewModel = koinViewModel(),
    seasonalGuideViewModel: SeasonalGuideViewModel = koinViewModel(),
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = remember { listOf("新番导视", "淘番榜单") }

    val exploreUiState by exploreViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showFilterBottomSheet by remember { mutableStateOf(false) }
    val filterSheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val exploreGridState = rememberLazyStaggeredGridState()

    val isFilterActive =
        exploreUiState.selectedSeason != ALL_TIME_SEASON ||
            exploreUiState.selectedTags.isNotEmpty() ||
            exploreUiState.selectedCategory != ExploreCategory.ANIME ||
            exploreUiState.selectedSort != ExploreSort.RANK

    LaunchedEffect(scrollToTop) {
        scrollToTop?.collect {
            if (selectedTabIndex == 1) {
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
                            if (selectedTabIndex == 1) {
                                IconButton(onClick = { showFilterBottomSheet = true }) {
                                    BadgedBox(
                                        badge = {
                                            if (isFilterActive) {
                                                Badge(containerColor = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.FilterList,
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
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "搜索",
                                )
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                    )

                    // 发现中心双 Tab 切换栏
                    PrimaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        },
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                if (selectedTabIndex == 0) {
                    // Tab 0: 季度新番导视大盘
                    SeasonalGuideContent(
                        onSubjectClick = onSubjectClick,
                        modifier = Modifier.fillMaxSize(),
                        viewModel = seasonalGuideViewModel,
                        scrollToTop = if (selectedTabIndex == 0) scrollToTop else null,
                    )
                } else {
                    // Tab 1: 淘番漫游与榜单瀑布流
                    PullToRefreshBox(
                        isRefreshing = exploreUiState.isRefreshing,
                        onRefresh = exploreViewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 1. 顶部场景/心境胶囊导航行（即点即查）
                            MoodFilterRow(
                                selectedMood = exploreUiState.selectedMood,
                                onMoodSelect = exploreViewModel::onMoodSelect,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // 2. 当前生效中的自定义条件胶囊展示（支持单项删除与重置）
                            if (isFilterActive) {
                                ActiveFilterPillRow(
                                    selectedSeason = exploreUiState.selectedSeason,
                                    selectedCategory = exploreUiState.selectedCategory,
                                    selectedTags = exploreUiState.selectedTags,
                                    selectedSort = exploreUiState.selectedSort,
                                    onClearSeason = { exploreViewModel.onSeasonSelect(ALL_TIME_SEASON) },
                                    onClearCategory = { exploreViewModel.onCategorySelect(ExploreCategory.ANIME) },
                                    onTagToggle = exploreViewModel::onTagToggle,
                                    onClearSort = { exploreViewModel.onSortSelect(ExploreSort.RANK) },
                                    onResetAll = { exploreViewModel.onMoodSelect(ExploreMood.MASTERPIECE) },
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
                onResetAll = { exploreViewModel.onMoodSelect(ExploreMood.MASTERPIECE) },
                onDismiss = { showFilterBottomSheet = false },
            )
        }

        // 未登录引导弹窗
        if (exploreUiState.showLoginPromptDialog) {
            val context = LocalContext.current
            AlertDialog(
                onDismissRequest = exploreViewModel::dismissLoginPrompt,
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
                        text = "请先登录 Bangumi 账号",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(
                        text = "一键「想看 / 追番」需要同步至您的 Bangumi 账号，登录后即可随手收藏、打卡并同步进度。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val authUrl = exploreViewModel.beginLogin()
                                context.launchWebUrl(authUrl, isAuth = true)
                            }
                        },
                    ) {
                        Text("立即登录")
                    }
                },
                dismissButton = {
                    TextButton(onClick = exploreViewModel::dismissLoginPrompt) {
                        Text("稍后再说")
                    }
                },
            )
        }
    }
}
