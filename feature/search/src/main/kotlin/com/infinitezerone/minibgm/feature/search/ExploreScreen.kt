package com.infinitezerone.minibgm.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.infinitezerone.minibgm.feature.search.components.SeasonalFocusRow
import com.infinitezerone.minibgm.feature.search.components.WaterfallGridList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel

/**
 * 统一探索发现界面（Netflix / B站首页式单流分区块 feed）：
 * - 顶部「本季新番」横滑区（当季片单精华，更多进入季度片单页）；
 * - 其下为淘番漫游：心境/场景胶囊或自定义筛选摘要条 + 焦点大卡 + 双列瀑布流，
 *   支持高级多维筛选与触底分页；
 * - 单一纵向滚动容器承载全部分区，替代原先的双 Tab 结构。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onLoginRequest: () -> Unit = {},
    onOpenSeasonalGuide: (() -> Unit)? = null,
    scrollToTop: Flow<Unit>? = null,
    exploreViewModel: ExploreViewModel = koinViewModel(),
    seasonalGuideViewModel: SeasonalGuideViewModel = koinViewModel(),
) {
    val exploreUiState by exploreViewModel.uiState.collectAsStateWithLifecycle()
    val seasonalUiState by seasonalGuideViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilterBottomSheet by remember { mutableStateOf(false) }
    val filterSheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val exploreGridState = rememberLazyStaggeredGridState()
    var customFilterExpanded by rememberSaveable { mutableStateOf(false) }

    val isFilterActive = exploreUiState.isCustomFilterActive

    LaunchedEffect(Unit) {
        exploreViewModel.loadIfNeeded()
    }

    LaunchedEffect(exploreGridState, customFilterExpanded) {
        if (!customFilterExpanded) return@LaunchedEffect
        snapshotFlow { exploreGridState.isScrollInProgress }.first { it }
        customFilterExpanded = false
    }

    if (scrollToTop != null) {
        ObserveAsEvents(scrollToTop) {
            exploreGridState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(exploreUiState.userMessage) {
        val msg = exploreUiState.userMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            exploreViewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = {
                    Text(
                        text = "探索发现",
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
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
        },
        snackbarHost = {
            BgmSnackbarHost(
                hostState = snackbarHostState,
                isTopLevel = true,
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            PullToRefreshBox(
                isRefreshing = exploreUiState.isRefreshing,
                onRefresh = exploreViewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    // 首次全屏骨架屏（本季新番区随数据就绪后并入单流）
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

                    // 单流分区块：本季新番横滑区 + 心境/筛选区 + 焦点大卡 + 瀑布流
                    else -> {
                        WaterfallGridList(
                            subjects = exploreUiState.subjects,
                            wishedSubjectIds = exploreUiState.wishedSubjectIds,
                            hasMore = exploreUiState.hasMore,
                            isLoadingMore = exploreUiState.isLoadingMore,
                            onLoadMore = exploreViewModel::loadMore,
                            onSubjectClick = onSubjectClick,
                            onToggleWish = exploreViewModel::toggleWish,
                            gridState = exploreGridState,
                            modifier = Modifier.fillMaxSize(),
                            emptyContent = {
                                ExploreEmptyState(
                                    onReset = { exploreViewModel.onMoodSelect(ExploreMood.MASTERPIECE) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            headerContent = {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    SeasonalFocusRow(
                                        year = seasonalUiState.selectedYear,
                                        quarterLabel = seasonalUiState.selectedQuarter.displayLabel,
                                        subjects = seasonalUiState.subjects,
                                        isLoading = seasonalUiState.isLoading,
                                        onSubjectClick = onSubjectClick,
                                        onOpenMore = onOpenSeasonalGuide,
                                        horizontalPadding = 4.dp,
                                    )
                                }

                                item(span = StaggeredGridItemSpan.FullLine) {
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
                                }

                                // 静默换挡加载进度条（追加翻页由底部指示器表达）
                                if (exploreUiState.isLoading && exploreUiState.subjects.isNotEmpty()) {
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // 高级多维筛选半屏抽屉
    if (showFilterBottomSheet) {
        ExploreFilterBottomSheet(
            sheetState = filterSheetState,
            selectedSeason = exploreUiState.selectedSeason,
            onSeasonSelect = exploreViewModel::onSeasonSelect,
            selectedCategory = exploreUiState.selectedCategory,
            onCategorySelect = exploreViewModel::onCategorySelect,
            selectedTags = exploreUiState.selectedTags,
            onTagToggle = exploreViewModel::onTagToggle,
            customFilterTags = exploreUiState.customFilterTags,
            onRemoveCustomTag = exploreViewModel::onRemoveCustomTag,
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
