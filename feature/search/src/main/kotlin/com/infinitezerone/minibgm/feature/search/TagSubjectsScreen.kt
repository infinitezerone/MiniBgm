package com.infinitezerone.minibgm.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.feature.search.components.SearchCategoryTabs
import com.infinitezerone.minibgm.feature.search.components.SearchErrorState
import com.infinitezerone.minibgm.feature.search.components.SearchNoResultsState
import com.infinitezerone.minibgm.feature.search.components.SearchResultsList
import com.infinitezerone.minibgm.feature.search.components.SearchSkeletonLoading
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 标签专题条目展示界面：
 * - 专为从作品详情页点击热门标签进入而设计（如 #搞笑、#芳文社、#科幻）；
 * - 顶部简洁标题栏展示标签名与返回按钮；
 * - 支持分类切换胶囊（全部/动画/书籍/游戏/音乐）；
 * - 支持列表/网格双视图切换、多维排序与上滑触底无限加载；
 * - 在分栏大屏模式下作为 extraPane 独立展现在右侧，不破坏左侧主列表状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSubjectsScreen(
    tag: String,
    onBackClick: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    initialType: Int = 0,
    viewModel: TagSubjectsViewModel = koinViewModel(parameters = { parametersOf(tag, initialType) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "#${uiState.tag}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
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
                onTypeSelect = viewModel::onSelectType,
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState.isLoading && uiState.subjects.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                uiState.isLoading && uiState.subjects.isEmpty() -> {
                    SearchSkeletonLoading(modifier = Modifier.fillMaxSize())
                }

                uiState.errorMessage != null && uiState.subjects.isEmpty() -> {
                    SearchErrorState(
                        errorMessage = uiState.errorMessage ?: "获取标签作品失败",
                        onRetry = viewModel::retry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                !uiState.isLoading && uiState.subjects.isEmpty() -> {
                    SearchNoResultsState(
                        query = uiState.tag,
                        selectedType = uiState.selectedType,
                        onResetCategory = { viewModel.onSelectType(0) },
                        onClearQuery = onBackClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    SearchResultsList(
                        results = uiState.subjects,
                        userCollections = uiState.userCollections,
                        selectedType = uiState.selectedType,
                        selectedSort = uiState.selectedSort,
                        viewMode = uiState.viewMode,
                        query = uiState.tag,
                        totalCount = uiState.subjects.size,
                        hasMore = uiState.hasMore,
                        isLoading = uiState.isLoading,
                        isLoadingMore = uiState.isLoadingMore,
                        onSortChange = viewModel::onSelectSort,
                        onViewModeToggle = {
                            viewModel.setViewMode(
                                if (uiState.viewMode == SearchViewMode.LIST) {
                                    SearchViewMode.GRID
                                } else {
                                    SearchViewMode.LIST
                                },
                            )
                        },
                        onToggleCollection = viewModel::updateCollection,
                        onLoadMore = viewModel::loadMore,
                        onSubjectClick = { route -> onSubjectClick(route.subjectId) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
