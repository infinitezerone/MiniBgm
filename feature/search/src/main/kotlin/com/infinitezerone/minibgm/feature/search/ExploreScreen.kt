package com.infinitezerone.minibgm.feature.search

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ExploreOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectComment
import com.infinitezerone.minibgm.feature.search.components.ExploreFilterBottomSheet
import com.infinitezerone.minibgm.feature.search.components.ExploreSpotlightCard
import com.infinitezerone.minibgm.feature.search.components.WaterfallSubjectCard
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
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }
                        }

                        // 搜索入口
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
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                // 心境/场景快捷筛选胶囊栏
                MoodFilterRow(
                    selectedMood = uiState.selectedMood,
                    onMoodSelect = viewModel::onMoodSelect,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 如果当前激活了非默认筛选条件，显示快捷标签展示与一键清除栏
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

                if (uiState.isLoading && uiState.subjects.isNotEmpty()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // 瀑布流内容（支持下拉刷新与触底分页加载）
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        uiState.isLoading && uiState.subjects.isEmpty() -> {
                            ExploreSkeletonLoading(modifier = Modifier.fillMaxSize())
                        }

                        uiState.error != null && uiState.subjects.isEmpty() -> {
                            ExploreErrorState(
                                errorMessage = uiState.error ?: "加载探索内容失败",
                                onRetry = viewModel::retry,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        uiState.subjects.isEmpty() -> {
                            ExploreEmptyState(
                                onReset = { viewModel.onMoodSelect(ExploreMood.TRENDING) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

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

/** 心境/场景快捷胶囊筛选栏 */
@Composable
private fun MoodFilterRow(
    selectedMood: ExploreMood?,
    onMoodSelect: (ExploreMood) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(ExploreMood.entries) { mood ->
            FilterChip(
                selected = selectedMood == mood,
                onClick = { onMoodSelect(mood) },
                label = {
                    Text(
                        text = mood.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedMood == mood) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
    }
}

/** 生效中的筛选条件快捷展示与一键清除栏 */
@Composable
private fun ActiveFilterPillRow(
    selectedSeason: SeasonOption,
    selectedCategory: ExploreCategory,
    selectedTags: Set<String>,
    selectedSort: ExploreSort,
    onClearSeason: () -> Unit,
    onClearCategory: () -> Unit,
    onTagToggle: (String) -> Unit,
    onClearSort: () -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (selectedSeason != CURRENT_SEASON) {
            item {
                ActiveFilterChip(
                    text = selectedSeason.label,
                    onClear = onClearSeason,
                )
            }
        }

        selectedTags.forEach { tag ->
            item(key = tag) {
                ActiveFilterChip(
                    text = "#$tag",
                    onClear = { onTagToggle(tag) },
                )
            }
        }

        if (selectedCategory != ExploreCategory.ANIME) {
            item {
                ActiveFilterChip(
                    text = selectedCategory.label,
                    onClear = onClearCategory,
                )
            }
        }

        if (selectedSort != ExploreSort.HEAT) {
            item {
                ActiveFilterChip(
                    text = selectedSort.label,
                    onClear = onClearSort,
                )
            }
        }

        item {
            TextButton(
                onClick = onResetAll,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = "清除全部",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ActiveFilterChip(
    text: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClear,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "移除",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** 双列瀑布流列表（支持上滑触底自动分页加载） */
@Composable
private fun WaterfallGridList(
    subjects: List<Subject>,
    wishedSubjectIds: Set<Long>,
    hotComments: Map<Long, SubjectComment>,
    selectedTags: Set<String>,
    onTagClick: (String) -> Unit,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    onToggleWish: (Long) -> Unit,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    modifier: Modifier = Modifier,
) {
    // 监听触底自动触发加载下一页
    LaunchedEffect(gridState, subjects.size, hasMore, isLoadingMore) {
        snapshotFlow {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible =
                gridState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            total > 0 && lastVisible >= total - 6
        }.collect { shouldLoad ->
            if (shouldLoad && hasMore && !isLoadingMore) {
                onLoadMore()
            }
        }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
        modifier = modifier,
    ) {
        // 1. 顶部焦点力荐大卡（打破千篇一律的网格货架，赋予视觉落脚点与情绪安利）
        if (subjects.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                val featured = subjects.first()
                ExploreSpotlightCard(
                    subject = featured,
                    isWished = wishedSubjectIds.contains(featured.id),
                    hotComment = hotComments[featured.id],
                    selectedTags = selectedTags,
                    onTagClick = onTagClick,
                    onSubjectClick = onSubjectClick,
                    onToggleWish = onToggleWish,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }

        // 2. 双列瀑布流卡片（展示其余条目）
        val remainingSubjects = if (subjects.size > 1) subjects.drop(1) else emptyList()
        items(remainingSubjects, key = { it.id }) { subject ->
            WaterfallSubjectCard(
                subject = subject,
                isWished = wishedSubjectIds.contains(subject.id),
                hotComment = hotComments[subject.id],
                selectedTags = selectedTags,
                onTagClick = onTagClick,
                onSubjectClick = onSubjectClick,
                onToggleWish = onToggleWish,
            )
        }

        // 底部加载状态提示
        item(span = StaggeredGridItemSpan.FullLine) {
            if (isLoadingMore) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "正在探索更多番剧...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (!hasMore && subjects.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✨ 已经到底啦，共发现 ${subjects.size} 部条目",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** 探索页双列瀑布流与焦点大卡骨架屏加载状态 */
@Composable
private fun ExploreSkeletonLoading(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "skeletonAlpha",
    )
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
        userScrollEnabled = false,
        modifier = modifier,
    ) {
        // 1. 顶部焦点大卡骨架
        item(span = StaggeredGridItemSpan.FullLine) {
            SpotlightSkeletonCard(
                placeholderColor = placeholderColor,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        // 2. 双列瀑布流骨架卡片（模拟不同高度的参差节奏）
        val variations =
            listOf(
                WaterfallCardVariation(hookLines = 2),
                WaterfallCardVariation(hookLines = 1),
                WaterfallCardVariation(hookLines = 3),
                WaterfallCardVariation(hookLines = 0),
                WaterfallCardVariation(hookLines = 2),
                WaterfallCardVariation(hookLines = 1),
            )
        items(variations.size) { index ->
            WaterfallSkeletonCard(
                variation = variations[index],
                placeholderColor = placeholderColor,
            )
        }
    }
}

@Composable
private fun SpotlightSkeletonCard(
    placeholderColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .height(220.dp),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(14.dp),
        ) {
            // 左上角徽章与热度占位
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 64.dp, height = 20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(placeholderColor),
                )
                Box(
                    modifier =
                        Modifier
                            .size(width = 46.dp, height = 20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(placeholderColor),
                )
            }

            // 底部标题、标签、剧情钩子安利占位
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 标题占位
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholderColor),
                )

                // 标签占位行
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .size(width = 44.dp, height = 18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(placeholderColor),
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(width = 52.dp, height = 18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(placeholderColor),
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(width = 40.dp, height = 18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(placeholderColor),
                    )
                }

                // 剧情钩子引言占位
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.92f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholderColor),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.58f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholderColor),
                )
            }
        }
    }
}

private data class WaterfallCardVariation(
    val hookLines: Int = 1,
)

@Composable
private fun WaterfallSkeletonCard(
    variation: WaterfallCardVariation,
    placeholderColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 封面海报占位
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(placeholderColor),
            ) {
                // 左上角评分角标占位
                Box(
                    modifier =
                        Modifier
                            .padding(6.dp)
                            .size(width = 38.dp, height = 18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                )
            }

            // 文本区域占位
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 标题占位
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.82f)
                            .height(15.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholderColor),
                )

                // 标签占位行
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .size(width = 36.dp, height = 14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(placeholderColor),
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(width = 44.dp, height = 14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(placeholderColor),
                    )
                }

                // 参差安利文案行
                if (variation.hookLines >= 1) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.92f)
                                .height(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(placeholderColor),
                    )
                }
                if (variation.hookLines >= 2) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.68f)
                                .height(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(placeholderColor),
                    )
                }
                if (variation.hookLines >= 3) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.46f)
                                .height(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(placeholderColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreEmptyState(
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ExploreOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无匹配条目",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "当前筛选条件下未发现条目，可尝试重置标签或切换其他场景",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onReset) {
            Text("重置为本季热门")
        }
    }
}

@Composable
private fun ExploreErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "探索加载失败",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}
