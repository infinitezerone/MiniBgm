package com.infinitezerone.minibgm.feature.search

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.theme.ActionCollect
import com.infinitezerone.minibgm.core.designsystem.theme.ActionDoing
import com.infinitezerone.minibgm.core.designsystem.theme.ActionWish
import com.infinitezerone.minibgm.core.designsystem.theme.HighlightAmber
import com.infinitezerone.minibgm.core.designsystem.theme.HighlightContainer
import com.infinitezerone.minibgm.core.designsystem.theme.OnHighlightContainer
import com.infinitezerone.minibgm.core.designsystem.theme.OnRatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.OnTypeAnime
import com.infinitezerone.minibgm.core.designsystem.theme.OnTypeBook
import com.infinitezerone.minibgm.core.designsystem.theme.OnTypeGame
import com.infinitezerone.minibgm.core.designsystem.theme.OnTypeMusic
import com.infinitezerone.minibgm.core.designsystem.theme.OnTypeReal
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGoldBright
import com.infinitezerone.minibgm.core.designsystem.theme.TypeAnimeContainer
import com.infinitezerone.minibgm.core.designsystem.theme.TypeBookContainer
import com.infinitezerone.minibgm.core.designsystem.theme.TypeGameContainer
import com.infinitezerone.minibgm.core.designsystem.theme.TypeMusicContainer
import com.infinitezerone.minibgm.core.designsystem.theme.TypeRealContainer
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectType
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 搜索页主界面：
 * - 顶部一体化现代搜索栏（支持软键盘响应、清空图标与显式“搜索”按钮）；
 * - 5 大分类图标过滤胶囊（全部/动画/书籍/游戏/音乐）；
 * - 空态/初始态多维发现矩阵：
 *   1. 历史搜索（FlowRow 胶囊、去重置顶、支持单项删除与一键清空）；
 *   2. 本季热搜推荐（带梯度排名角标的精选话题）；
 *   3. 题材流派与制作名社探索（京都动画、MAPPA、科幻、日常、治愈等）；
 * - 搜索结果卡片：74dp x 104dp 高清封面、双语标题、Bangumi Rank 排名角标、社区标签流、金色评分与在看热度；
 * - 骨架屏加载与友好无结果重试引导。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    onBackClick: (() -> Unit)? = null,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && uiState.query.isBlank()) {
            viewModel.onQueryChange(initialQuery)
            viewModel.search()
        }
    }

    Scaffold(
        topBar = {
            SearchTopHeader(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {
                    keyboardController?.hide()
                    viewModel.search()
                },
                onClear = viewModel::clearQuery,
                onBackClick = onBackClick,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
        ) {
            SearchCategoryTabs(
                selectedType = uiState.selectedType,
                onTypeSelect = viewModel::onTypeSelect,
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState.isLoading && uiState.results.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                uiState.isLoading && uiState.results.isEmpty() -> {
                    SearchSkeletonLoading(modifier = Modifier.fillMaxSize())
                }

                uiState.error != null && uiState.results.isEmpty() -> {
                    SearchErrorState(
                        errorMessage = uiState.error ?: "搜索发生错误",
                        onRetry = viewModel::search,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                uiState.query.isBlank() && uiState.results.isEmpty() -> {
                    SearchIdleView(
                        searchHistory = uiState.searchHistory,
                        onKeywordClick = { keyword ->
                            viewModel.onQueryChange(keyword)
                            viewModel.search()
                        },
                        onDeleteHistoryItem = viewModel::deleteHistoryItem,
                        onClearAllHistory = viewModel::clearAllHistory,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                uiState.results.isEmpty() -> {
                    SearchNoResultsState(
                        query = uiState.query,
                        selectedType = uiState.selectedType,
                        onResetCategory = { viewModel.onTypeSelect(0) },
                        onClearQuery = viewModel::clearQuery,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    SearchResultsList(
                        results = uiState.results,
                        userCollections = uiState.userCollections,
                        selectedType = uiState.selectedType,
                        selectedSort = uiState.selectedSort,
                        viewMode = uiState.viewMode,
                        query = uiState.query,
                        totalCount = uiState.totalCount,
                        hasMore = uiState.hasMore,
                        isLoading = uiState.isLoading,
                        isLoadingMore = uiState.isLoadingMore,
                        onSortChange = viewModel::onSortChange,
                        onViewModeToggle = viewModel::onViewModeToggle,
                        onToggleCollection = viewModel::toggleCollection,
                        onLoadMore = viewModel::loadMore,
                        onSubjectClick = onSubjectClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (uiState.showLoginPromptDialog) {
        SearchLoginDialog(
            onDismiss = viewModel::dismissLoginPrompt,
            onConfirmLogin = {
                coroutineScope.launch {
                    val authorizeUrl = viewModel.beginLogin()
                    CustomTabsIntent
                        .Builder()
                        .setEphemeralBrowsingEnabled(true)
                        .build()
                        .launchUrl(context, Uri.parse(authorizeUrl))
                }
            },
        )
    }
}

/** 顶部现代一体化搜索栏（符合 Edge-to-Edge 与 M3 TopAppBar 状态栏规范） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onBackClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    BgmTopAppBar(
        title = {
            // 胶囊搜索框
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            RoundedCornerShape(18.dp),
                        ).padding(horizontal = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索动画、原名、制作人员...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle =
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                            ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "清空输入",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        actions = {
            TextButton(
                onClick = onSearch,
                modifier = Modifier.padding(end = 4.dp),
            ) {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        windowInsets = TopAppBarDefaults.windowInsets,
        height = 48.dp,
        modifier = modifier,
    )
}

/** 分类过滤胶囊条 */
@Composable
private fun SearchCategoryTabs(
    selectedType: Int,
    onTypeSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(SearchCategory.entries) { category ->
            val isSelected = selectedType == category.type
            Surface(
                onClick = { onTypeSelect(category.type) },
                shape = RoundedCornerShape(12.dp),
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                border =
                    if (isSelected) {
                        null
                    } else {
                        BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}

/** 搜索初始/空闲状态视图（真实搜索历史与纯净搜索引导，不包含任何虚假数据） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchIdleView(
    searchHistory: List<String>,
    onKeywordClick: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (searchHistory.isNotEmpty()) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = modifier,
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "历史搜索",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        TextButton(
                            onClick = onClearAllHistory,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "清空全部",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        searchHistory.forEach { historyQuery ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                ) {
                                    Text(
                                        text = historyQuery,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier =
                                            Modifier.clickable {
                                                onKeywordClick(historyQuery)
                                            },
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { onDeleteHistoryItem(historyQuery) },
                                        modifier = Modifier.size(20.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "删除记录",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // 无历史记录时的纯净搜索引导态
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(72.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "搜索 Bangumi 条目",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "输入中文译名、日文原名或制作人员\n点击上方分类切换动画、书籍、游戏、音乐等品类",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 搜索结果列表（支持多维排序、列表/网格双模切换与无限滚动触底加载） */
@Composable
private fun SearchResultsList(
    results: List<Subject>,
    userCollections: Map<Long, CollectionType>,
    selectedType: Int,
    selectedSort: SearchSort,
    viewMode: SearchViewMode,
    query: String,
    totalCount: Int,
    hasMore: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    onSortChange: (SearchSort) -> Unit,
    onViewModeToggle: () -> Unit,
    onToggleCollection: (Subject, CollectionType) -> Unit,
    onLoadMore: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // 切换排序、分类或搜索关键词时，自动重置回到顶部
    LaunchedEffect(selectedSort, selectedType, query) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.scrollToItem(0)
        }
        if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
            gridState.scrollToItem(0)
        }
    }

    Column(modifier = modifier) {
        // 多维排序与视图切换工具栏
        SearchSortFilterBar(
            selectedSort = selectedSort,
            viewMode = viewMode,
            onSortChange = onSortChange,
            onViewModeToggle = onViewModeToggle,
        )

        val countText =
            if (isLoading) {
                "正在按「${selectedSort.label}」检索作品..."
            } else if (totalCount > 0 && totalCount > results.size) {
                "共找到 $totalCount 部作品 (已加载 ${results.size} 部)"
            } else if (totalCount > 0) {
                "共找到 $totalCount 部作品"
            } else {
                "共找到 ${results.size} 部作品"
            }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (isLoading) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    },
                fontWeight = if (isLoading) FontWeight.Bold else FontWeight.Medium,
            )
        }

        when (viewMode) {
            SearchViewMode.LIST -> {
                LaunchedEffect(listState, results.size, hasMore, isLoadingMore) {
                    snapshotFlow {
                        val total = listState.layoutInfo.totalItemsCount
                        val lastVisible =
                            listState.layoutInfo.visibleItemsInfo
                                .lastOrNull()
                                ?.index ?: 0
                        total > 0 && lastVisible >= total - 4
                    }.collect { shouldLoad ->
                        if (shouldLoad && hasMore && !isLoadingMore) {
                            onLoadMore()
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(results, key = { it.id }) { subject ->
                        SearchResultCard(
                            subject = subject,
                            currentStatus = userCollections[subject.id],
                            query = query,
                            onSubjectClick = onSubjectClick,
                            onToggleCollection = { type -> onToggleCollection(subject, type) },
                        )
                    }

                    item {
                        SearchResultsFooter(
                            isLoadingMore = isLoadingMore,
                            hasMore = hasMore,
                            totalCount = totalCount,
                            resultCount = results.size,
                        )
                    }
                }
            }

            SearchViewMode.GRID -> {
                LaunchedEffect(gridState, results.size, hasMore, isLoadingMore) {
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

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(results, key = { it.id }) { subject ->
                        SearchResultGridCard(
                            subject = subject,
                            currentStatus = userCollections[subject.id],
                            query = query,
                            onSubjectClick = onSubjectClick,
                            onToggleDoing = {
                                onToggleCollection(subject, CollectionType.DOING)
                            },
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SearchResultsFooter(
                            isLoadingMore = isLoadingMore,
                            hasMore = hasMore,
                            totalCount = totalCount,
                            resultCount = results.size,
                        )
                    }
                }
            }
        }
    }
}

/** 排序与视图切换操作条 */
@Composable
private fun SearchSortFilterBar(
    selectedSort: SearchSort,
    viewMode: SearchViewMode,
    onSortChange: (SearchSort) -> Unit,
    onViewModeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(SearchSort.entries) { sort ->
                val isSelected = sort == selectedSort
                Surface(
                    onClick = { onSortChange(sort) },
                    shape = RoundedCornerShape(8.dp),
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    border =
                        if (isSelected) {
                            null
                        } else {
                            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        },
                ) {
                    Text(
                        text = sort.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = onViewModeToggle,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector =
                    if (viewMode == SearchViewMode.LIST) {
                        Icons.Filled.GridView
                    } else {
                        Icons.AutoMirrored.Filled.ViewList
                    },
                contentDescription = if (viewMode == SearchViewMode.LIST) "切换为海报网格" else "切换为详细列表",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/** 底部加载与展示完毕提示 */
@Composable
private fun SearchResultsFooter(
    isLoadingMore: Boolean,
    hasMore: Boolean,
    totalCount: Int,
    resultCount: Int,
    modifier: Modifier = Modifier,
) {
    if (isLoadingMore) {
        Box(
            modifier = modifier.fillMaxWidth().padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "正在加载更多作品...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else if (!hasMore && resultCount > 0 && totalCount > 0) {
        Box(
            modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "已展示全部 $totalCount 部相关作品",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            )
        }
    }
}

/** 高质感详细卡片（多品类自适应徽章、关键词高亮、度量适配与 1-Tap 快捷三态打卡） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchResultCard(
    subject: Subject,
    currentStatus: CollectionType?,
    query: String,
    onSubjectClick: (Long) -> Unit,
    onToggleCollection: (CollectionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjectType = remember(subject.type) { SubjectType.fromValue(subject.type) }
    val typeTheme = remember(subjectType) { getSubjectTypeTheme(subjectType) }

    val primaryTitle = subject.displayName
    val secondaryTitle =
        if (subject.nameCn.isNotBlank() && subject.name.isNotBlank() && subject.nameCn != subject.name) {
            subject.name
        } else {
            null
        }

    val primaryTitleAnnotated =
        remember(primaryTitle, query) {
            highlightKeywords(primaryTitle, query, HighlightAmber)
        }
    val secondaryTitleAnnotated =
        remember(secondaryTitle, query) {
            secondaryTitle?.let { highlightKeywords(it, query, HighlightAmber) }
        }

    val dateText = subject.date.ifBlank { subject.airDate }
    val episodesNum = if (subject.totalEpisodes > 0) subject.totalEpisodes else subject.eps
    val metricText =
        when {
            subjectType == SubjectType.GAME -> null
            episodesNum > 0 -> "全 $episodesNum ${subjectType.unitName}"
            else -> null
        }

    val rating = subject.rating
    val rank = rating?.rank ?: 0
    val doing = subject.collection?.doing ?: 0
    val collect = subject.collection?.collect ?: 0

    val topTags =
        remember(subject.tags) {
            subject.tags
                .filter { it.name !in setOf("TV", "日本", "动画", "原创", "漫改", "轻改") && !it.name.all { c -> c.isDigit() } }
                .take(3)
        }

    Card(
        onClick = { onSubjectClick(subject.id) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 74dp x 104dp 高清封面
            Box(
                modifier =
                    Modifier
                        .width(74.dp)
                        .height(104.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            RoundedCornerShape(8.dp),
                        ),
            ) {
                CoverImage(
                    url = subject.images?.bestImage.orEmpty(),
                    contentDescription = primaryTitle,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 右侧内容区
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // 1. 中文主标题（带高亮）
                Text(
                    text = primaryTitleAnnotated,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // 2. 原名小字（带高亮）
                if (secondaryTitleAnnotated != null) {
                    Text(
                        text = secondaryTitleAnnotated,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.88f,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 3. 元数据标识行（Rank + 品类专属徽章 + 开播/发售 + 话数/卷数）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(top = 1.dp),
                ) {
                    if (rank > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = HighlightContainer,
                        ) {
                            Text(
                                text = "Rank #$rank",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                                fontWeight = FontWeight.ExtraBold,
                                color = OnHighlightContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }

                    // 品类定制色调徽章
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = typeTheme.containerColor,
                    ) {
                        Text(
                            text = "${subjectType.iconEmoji} ${subjectType.label}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                            fontWeight = FontWeight.Bold,
                            color = typeTheme.contentColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }

                    if (dateText.isNotBlank()) {
                        Text(
                            text = "${subjectType.releaseVerb}: $dateText",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (metricText != null) {
                        Text(
                            text = "· $metricText",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }

                // 4. 社区同好标签
                if (topTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(top = 1.dp),
                    ) {
                        topTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                            ) {
                                Text(
                                    text = "#${tag.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp),
                                )
                            }
                        }
                    }
                }

                // 5. 评分与 1-Tap 追番三态胶囊
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                ) {
                    // 评分
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (rating != null && rating.score > 0.0) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = RatingGold,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = rating.score.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = RatingGold,
                            )
                        } else {
                            Text(
                                text = "暂无评分",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }

                    // 1-Tap 快捷胶囊组（想看/想读/想听/想玩）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // 想看 / 想读 / 想听 / 想玩
                        QuickCapsuleButton(
                            label = subjectType.actionWish,
                            isActive = currentStatus == CollectionType.WISH,
                            activeColor = ActionWish,
                            onClick = { onToggleCollection(CollectionType.WISH) },
                        )
                        // 在看 / 在读 / 在听 / 在玩
                        QuickCapsuleButton(
                            label = subjectType.actionDoing,
                            isActive = currentStatus == CollectionType.DOING,
                            activeColor = ActionDoing,
                            onClick = { onToggleCollection(CollectionType.DOING) },
                        )
                        // 看过 / 读过 / 听过 / 玩过
                        QuickCapsuleButton(
                            label = subjectType.actionCollect,
                            isActive = currentStatus == CollectionType.COLLECT,
                            activeColor = ActionCollect,
                            onClick = { onToggleCollection(CollectionType.COLLECT) },
                        )
                    }
                }
            }
        }
    }
}

/** 3 列高密度海报网格卡片 */
@Composable
private fun SearchResultGridCard(
    subject: Subject,
    currentStatus: CollectionType?,
    query: String,
    onSubjectClick: (Long) -> Unit,
    onToggleDoing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjectType = remember(subject.type) { SubjectType.fromValue(subject.type) }
    val typeTheme = remember(subjectType) { getSubjectTypeTheme(subjectType) }
    val primaryTitle = subject.displayName
    val primaryTitleAnnotated =
        remember(primaryTitle, query) {
            highlightKeywords(primaryTitle, query, HighlightAmber)
        }
    val rank = subject.rating?.rank ?: 0
    val score = subject.rating?.score ?: 0.0

    Card(
        onClick = { onSubjectClick(subject.id) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            // 3:4 纵深海报
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                CoverImage(
                    url = subject.images?.bestImage.orEmpty(),
                    contentDescription = primaryTitle,
                    modifier = Modifier.fillMaxSize(),
                )

                // 左上角 Rank
                if (rank > 0) {
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                        color = RatingGold,
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Text(
                            text = "#$rank",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.82f,
                            fontWeight = FontWeight.ExtraBold,
                            color = OnRatingGold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }

                // 右上角品类 Emoji
                Surface(
                    shape = RoundedCornerShape(bottomStart = 6.dp),
                    color = typeTheme.containerColor.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(
                        text = subjectType.iconEmoji,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }

                // 右下角评分
                if (score > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 6.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        Text(
                            text = "★ $score",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                            fontWeight = FontWeight.Bold,
                            color = RatingGoldBright,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            // 底部内容
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = primaryTitleAnnotated,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // 快捷打卡单键（显示当前状态或一键在看/在读/在玩）
                val isDoing = currentStatus == CollectionType.DOING
                Surface(
                    onClick = onToggleDoing,
                    shape = RoundedCornerShape(6.dp),
                    color =
                        if (isDoing) {
                            ActionDoing
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            if (isDoing) {
                                "✓ ${subjectType.actionDoing}"
                            } else {
                                "+ ${subjectType.actionDoing}"
                            },
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color =
                            if (isDoing) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** 1-Tap 快捷三态打卡小胶囊 */
@Composable
private fun QuickCapsuleButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) activeColor.copy(alpha = 0.18f) else Color.Transparent,
        border =
            BorderStroke(
                0.8.dp,
                if (isActive) activeColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            ),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
        ) {
            if (isActive) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(10.dp),
                )
            }
            Text(
                text = label,
                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 登录提示引导弹窗 */
@Composable
private fun SearchLoginDialog(
    onDismiss: () -> Unit,
    onConfirmLogin: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登录以同步追番进度") },
        text = { Text("登录 Bangumi 账号后，即可一键标记在看、在读、在听、在玩，并同步至你的个人收藏库。") },
        confirmButton = {
            Button(onClick = onConfirmLogin) {
                Text("前往登录")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后再说")
            }
        },
    )
}

/** 关键词高亮辅助工具函数 */
private fun highlightKeywords(
    text: String,
    query: String,
    highlightColor: Color,
): AnnotatedString {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank() || !text.contains(trimmedQuery, ignoreCase = true)) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        var currentIndex = 0
        val lowerText = text.lowercase()
        val lowerQuery = trimmedQuery.lowercase()
        val queryLength = lowerQuery.length

        while (currentIndex < text.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, currentIndex)
            if (matchIndex < 0) {
                append(text.substring(currentIndex))
                break
            }
            if (matchIndex > currentIndex) {
                append(text.substring(currentIndex, matchIndex))
            }
            val matchedPart = text.substring(matchIndex, matchIndex + queryLength)
            val startPos = length
            append(matchedPart)
            addStyle(
                SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.ExtraBold,
                    background = highlightColor.copy(alpha = 0.16f),
                ),
                startPos,
                length,
            )
            currentIndex = matchIndex + queryLength
        }
    }
}

private data class SubjectTypeColorTheme(
    val containerColor: Color,
    val contentColor: Color,
)

private fun getSubjectTypeTheme(subjectType: SubjectType): SubjectTypeColorTheme =
    when (subjectType) {
        SubjectType.BOOK ->
            SubjectTypeColorTheme(
                containerColor = TypeBookContainer,
                contentColor = OnTypeBook,
            )
        SubjectType.ANIME ->
            SubjectTypeColorTheme(
                containerColor = TypeAnimeContainer,
                contentColor = OnTypeAnime,
            )
        SubjectType.MUSIC ->
            SubjectTypeColorTheme(
                containerColor = TypeMusicContainer,
                contentColor = OnTypeMusic,
            )
        SubjectType.GAME ->
            SubjectTypeColorTheme(
                containerColor = TypeGameContainer,
                contentColor = OnTypeGame,
            )
        SubjectType.REAL ->
            SubjectTypeColorTheme(
                containerColor = TypeRealContainer,
                contentColor = OnTypeReal,
            )
    }

/** 骨架屏加载状态 */
@Composable
private fun SearchSkeletonLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(74.dp)
                                .height(104.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                    }
                }
            }
        }
    }
}

/** 无结果引导状态 */
@Composable
private fun SearchNoResultsState(
    query: String,
    selectedType: Int,
    onResetCategory: () -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.SearchOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "未找到关于「$query」的作品",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "建议：检查关键词是否有误，尝试搜索日文原名、缩写，或切换至「全部」分类再次尝试",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selectedType != 0) {
                Button(onClick = onResetCategory) {
                    Text("切至全部分类")
                }
            }
            OutlinedButton(onClick = onClearQuery) {
                Text("清空重搜")
            }
        }
    }
}

/** 错误重试状态 */
@Composable
private fun SearchErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "搜索遇到问题",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("重新加载")
        }
    }
}

private fun getSubjectTypeName(type: Int): String = SubjectType.fromValue(type).label
