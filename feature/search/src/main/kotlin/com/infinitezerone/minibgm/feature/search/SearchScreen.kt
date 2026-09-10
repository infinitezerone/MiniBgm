package com.infinitezerone.minibgm.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import com.infinitezerone.minibgm.feature.search.components.SearchCategoryTabs
import com.infinitezerone.minibgm.feature.search.components.SearchErrorState
import com.infinitezerone.minibgm.feature.search.components.SearchIdleView
import com.infinitezerone.minibgm.feature.search.components.SearchLoginDialog
import com.infinitezerone.minibgm.feature.search.components.SearchNoResultsState
import com.infinitezerone.minibgm.feature.search.components.SearchResultsList
import com.infinitezerone.minibgm.feature.search.components.SearchSkeletonLoading
import com.infinitezerone.minibgm.feature.search.components.SearchTopHeader
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 搜索页主界面：
 * - 顶部一体化现代搜索栏（支持软键盘响应、清空图标与显式“搜索”按钮）；
 * - 5 大分类图标过滤胶囊（全部/动画/书籍/游戏/音乐）；
 * - 空态/初始态多维发现矩阵：
 *   1. 历史搜索（FlowRow 胶囊、去重置顶、支持单项删除与一键清空）；
 * - 搜索结果卡片：74dp x 104dp 高清封面、双语标题、Bangumi Rank 排名角标、社区标签流、金色评分与在看热度；
 * - 骨架屏加载与友好无结果重试引导。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onSubjectClick: (SubjectDetailRoute) -> Unit,
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
                    context.launchWebUrl(authorizeUrl, isAuth = true)
                }
            },
        )
    }
}
