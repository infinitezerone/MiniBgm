package com.infinitezerone.minibgm.feature.search

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
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
 * 刷番探索发现界面：
 * - 纯粹高信息密度的双列瀑布流（小红书 / 小黑盒形态）；
 * - 支持下拉刷新（Pull-to-Refresh）与触底静默无限加载（Load-More）；
 * - 高级多维筛选半屏浮层抽屉（ModalBottomSheet），底层列表平稳不抖动；
 * - 标签采用多行自适应流式排布（FlowRow），支持自定义标签精准过滤与一键清除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
    viewModel: ExploreViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showFilterBottomSheet by remember { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gridState = rememberLazyStaggeredGridState()

    LaunchedEffect(scrollToTop) {
        scrollToTop?.collect {
            gridState.animateScrollToItem(0)
        }
    }

    val isFilterActive =
        uiState.selectedSeason != CURRENT_SEASON ||
            uiState.selectedTags.isNotEmpty() ||
            uiState.selectedCategory != ExploreCategory.ANIME ||
            uiState.selectedSort != ExploreSort.HEAT

    LaunchedEffect(uiState.userMessage) {
        val msg = uiState.userMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                BgmTopAppBar(
                    title = {
                        Text(
                            text = "探索发现",
                        )
                    },
                    actions = {
                        // 展开高级多维筛选抽屉
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
            },
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. 顶部场景/心境胶囊导航行（即点即查）
                    MoodFilterRow(
                        selectedMood = uiState.selectedMood,
                        onMoodSelect = viewModel::onMoodSelect,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 2. 当前生效中的自定义条件胶囊展示（支持单项删除与重置）
                    if (isFilterActive) {
                        ActiveFilterPillRow(
                            selectedSeason = uiState.selectedSeason,
                            selectedCategory = uiState.selectedCategory,
                            selectedTags = uiState.selectedTags,
                            selectedSort = uiState.selectedSort,
                            onClearSeason = { viewModel.onSeasonSelect(CURRENT_SEASON) },
                            onClearCategory = { viewModel.onCategorySelect(ExploreCategory.ANIME) },
                            onTagToggle = viewModel::onTagToggle,
                            onClearSort = { viewModel.onSortSelect(ExploreSort.HEAT) },
                            onResetAll = { viewModel.onMoodSelect(ExploreMood.TRENDING) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // 加载进度条（静默加载或分页时展示）
                    if (uiState.isLoading && uiState.subjects.isNotEmpty()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    // 3. 内容区主状态机
                    when {
                        // 首次全屏骨架屏
                        uiState.isLoading && uiState.subjects.isEmpty() -> {
                            ExploreSkeletonLoading(modifier = Modifier.fillMaxSize())
                        }

                        // 错误重试态
                        uiState.error != null && uiState.subjects.isEmpty() -> {
                            ExploreErrorState(
                                errorMessage = uiState.error ?: "未知网络异常",
                                onRetry = viewModel::refresh,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        // 空数据提示态
                        uiState.subjects.isEmpty() -> {
                            ExploreEmptyState(
                                onReset = { viewModel.onMoodSelect(ExploreMood.TRENDING) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        // 双列瀑布流真实数据
                        else -> {
                            WaterfallGridList(
                                subjects = uiState.subjects,
                                wishedSubjectIds = uiState.wishedSubjectIds,
                                hotComments = uiState.hotComments,
                                selectedTags = uiState.selectedTags,
                                onTagClick = viewModel::onTagToggle,
                                hasMore = uiState.hasMore,
                                isLoadingMore = uiState.isLoadingMore,
                                onLoadMore = viewModel::loadMore,
                                onSubjectClick = onSubjectClick,
                                onToggleWish = viewModel::toggleWish,
                                gridState = gridState,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        // 高级多维筛选半屏抽屉 (ModalBottomSheet)
        if (showFilterBottomSheet) {
            ExploreFilterBottomSheet(
                sheetState = filterSheetState,
                selectedSeason = uiState.selectedSeason,
                onSeasonSelect = viewModel::onSeasonSelect,
                selectedCategory = uiState.selectedCategory,
                onCategorySelect = viewModel::onCategorySelect,
                selectedTags = uiState.selectedTags,
                onTagToggle = viewModel::onTagToggle,
                onClearAllTags = viewModel::onClearAllTags,
                onCustomTagSubmit = viewModel::onCustomTagSubmit,
                selectedSort = uiState.selectedSort,
                onSortSelect = viewModel::onSortSelect,
                onResetAll = { viewModel.onMoodSelect(ExploreMood.TRENDING) },
                onDismiss = { showFilterBottomSheet = false },
            )
        }

        // 未登录想看拦截提示弹窗
        if (uiState.showLoginPromptDialog) {
            val context = LocalContext.current
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
                        text = "一键「想看 / 追番」需要同步至您的 Bangumi 账号，登录后即可随手收藏、打卡并同步进度。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val authorizeUrl = viewModel.beginLogin()
                                CustomTabsIntent
                                    .Builder()
                                    .setEphemeralBrowsingEnabled(true)
                                    .build()
                                    .launchUrl(context, Uri.parse(authorizeUrl))
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

        // 统一悬浮 Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}
