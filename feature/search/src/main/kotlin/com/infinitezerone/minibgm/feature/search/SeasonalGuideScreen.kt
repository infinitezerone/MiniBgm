package com.infinitezerone.minibgm.feature.search

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmLoginPromptDialog
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.ObserveAsEvents
import com.infinitezerone.minibgm.core.designsystem.component.TagActionBottomSheet
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.LocalWindowAdaptiveInfo
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.feature.search.components.SeasonPickerBottomSheet
import com.infinitezerone.minibgm.feature.search.components.SeasonalAnimeCard
import com.infinitezerone.minibgm.feature.search.components.SeasonalAnimeRow
import com.infinitezerone.minibgm.feature.search.components.SeasonalFilterBar
import com.infinitezerone.minibgm.feature.search.components.SeasonalGuideEmptyState
import com.infinitezerone.minibgm.feature.search.components.SeasonalGuideErrorState
import com.infinitezerone.minibgm.feature.search.components.SeasonalGuideSkeletonGrid
import com.infinitezerone.minibgm.feature.search.components.SeasonalGuideSkeletonList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 季度片单界面（独立二级页容器）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonalGuideScreen(
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialYear: Int = 0,
    initialSeasonMonth: Int = 0,
    onLoginRequest: () -> Unit = {},
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
                        text = "季度片单",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = BgmIcons.ArrowBack,
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
            onLoginRequest = onLoginRequest,
        )
    }
}

/**
 * 季度片单可复用内容组件：
 * 支持直接嵌入探索双 Tab 页面或作为独立二级页面主体。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonalGuideContent(
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeasonalGuideViewModel = koinViewModel(),
    onLoginRequest: () -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
    isTopLevel: Boolean = false,
) {
    val adaptiveInfo = LocalWindowAdaptiveInfo.current
    val isWideScreen = adaptiveInfo.isWide
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    var showSeasonPicker by remember { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(scrollToTop, uiState.viewMode) {
        scrollToTop?.collect {
            when (uiState.viewMode) {
                SeasonalViewMode.LIST -> listState.animateScrollToItem(0)
                SeasonalViewMode.POSTER -> gridState.animateScrollToItem(0)
            }
        }
    }

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

    var activeTagForAction by remember { mutableStateOf<String?>(null) }
    val tagActionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentFilterKey =
        "${uiState.selectedYear}_${uiState.selectedQuarter}_${uiState.selectedSort}_${uiState.selectedOrigin}_${uiState.selectedForm}_${uiState.selectedTags.sorted().joinToString()}"
    var previousFilterKey by rememberSaveable { mutableStateOf(currentFilterKey) }

    LaunchedEffect(currentFilterKey) {
        if (previousFilterKey != currentFilterKey) {
            previousFilterKey = currentFilterKey
            when (uiState.viewMode) {
                SeasonalViewMode.LIST -> listState.scrollToItem(0)
                SeasonalViewMode.POSTER -> gridState.scrollToItem(0)
            }
        }
    }

    ObserveAsEvents(viewModel.uiEffects) { effect ->
        when (effect) {
            is UiEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.text)
        }
    }

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
            val threshold =
                when (uiState.viewMode) {
                    SeasonalViewMode.LIST -> 1
                    SeasonalViewMode.POSTER -> 2
                }
            totalItems > 0 && lastVisible >= totalItems - threshold
        }.distinctUntilChanged().collect { shouldLoadMore ->
            val state = uiState
            if (shouldLoadMore && state.hasMore && !state.isLoadingMore && !state.isLoading) {
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
                SeasonalFilterBar(
                    uiState = uiState,
                    filterExpanded = filterExpanded,
                    onToggleFilterExpanded = { filterExpanded = !filterExpanded },
                    onOpenSeasonPicker = { showSeasonPicker = true },
                    onToggleViewMode = viewModel::toggleViewMode,
                    onSelectOrigin = { origin ->
                        filterExpanded = false
                        viewModel.selectOrigin(origin)
                    },
                    onSelectForm = { form ->
                        filterExpanded = false
                        viewModel.selectForm(form)
                    },
                    onSelectSort = { sort ->
                        filterExpanded = false
                        viewModel.selectSort(sort)
                    },
                    onToggleTag = viewModel::toggleTag,
                    onClearSelectedTags = viewModel::clearSelectedTags,
                    onAddCustomTag = viewModel::addCustomFilterTag,
                    onRemoveCustomTag = viewModel::removeCustomFilterTag,
                )

                // 仅在整体换挡重新拉取时在顶部展示进度，追加翻页由底部指示器表达
                if (uiState.isLoading && uiState.subjects.isNotEmpty()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                when {
                    uiState.isLoading && uiState.subjects.isEmpty() -> {
                        if (uiState.viewMode == SeasonalViewMode.LIST) {
                            SeasonalGuideSkeletonList(modifier = Modifier.fillMaxSize())
                        } else {
                            SeasonalGuideSkeletonGrid(modifier = Modifier.fillMaxSize())
                        }
                    }

                    uiState.error != null && uiState.subjects.isEmpty() -> {
                        SeasonalGuideErrorState(
                            errorMessage = uiState.error ?: "加载季度片单失败",
                            onRetry = viewModel::retry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    !uiState.isLoading && uiState.subjects.isEmpty() -> {
                        SeasonalGuideEmptyState(
                            selectedYear = uiState.selectedYear,
                            selectedQuarter = uiState.selectedQuarter,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

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
                                    onTagClick = { activeTagForAction = it },
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
                            } else if (uiState.error != null && uiState.hasMore) {
                                item(key = "loadMoreRetry") {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        TextButton(onClick = viewModel::loadMore) {
                                            Text(
                                                text = "加载失败，点击重试",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                }
                            } else if (!uiState.hasMore && uiState.filteredSubjects.size >= 10) {
                                item(key = "endOfList") {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 20.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "已经到底啦，共发现 ${uiState.filteredSubjects.size} 部条目",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        val gridColumns =
                            if (isWideScreen) {
                                GridCells.Adaptive(minSize = 110.dp)
                            } else {
                                GridCells.Fixed(3)
                            }
                        LazyVerticalGrid(
                            columns = gridColumns,
                            state = gridState,
                            contentPadding =
                                PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 8.dp,
                                    bottom = if (isTopLevel && !isWideScreen) 96.dp else 32.dp,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
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
                            } else if (uiState.error != null && uiState.hasMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        TextButton(onClick = viewModel::loadMore) {
                                            Text(
                                                text = "加载失败，点击重试",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                }
                            } else if (!uiState.hasMore && uiState.filteredSubjects.size >= 10) {
                                item(key = "endOfGrid", span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 20.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "已经到底啦，共发现 ${uiState.filteredSubjects.size} 部条目",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        )
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

        if (uiState.showLoginPromptDialog) {
            BgmLoginPromptDialog(
                title = "登录开启快捷追番",
                description = "登录 Bangumi 账号后，即可一键追踪当季新番，收藏状态将实时同步至云端与放送日历。",
                onLogin = {
                    viewModel.dismissLoginPrompt()
                    onLoginRequest()
                },
                onDismiss = viewModel::dismissLoginPrompt,
            )
        }
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

        activeTagForAction?.let { tag ->
            TagActionBottomSheet(
                tag = tag,
                isFavorite = uiState.customFilterTags.contains(tag),
                isFiltered = uiState.selectedTags.contains(tag),
                sheetState = tagActionSheetState,
                onToggleFilter = {
                    viewModel.toggleTag(tag)
                },
                onToggleFavorite = {
                    if (uiState.customFilterTags.contains(tag)) {
                        viewModel.removeCustomFilterTag(tag)
                    } else {
                        viewModel.addCustomFilterTag(tag)
                    }
                },
                onDismiss = { activeTagForAction = null },
            )
        }
    }
}
