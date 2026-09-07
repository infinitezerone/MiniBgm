package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UserCollection
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun UserCollectionsScreen(
    initialType: CollectionType = CollectionType.DOING,
    onSubjectClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserCollectionsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialType) {
        viewModel.setInitialType(initialType)
    }

    UserCollectionsContent(
        uiState = uiState,
        onTypeSelect = viewModel::selectType,
        onFilterSelect = viewModel::selectSubjectFilter,
        onRefresh = viewModel::refresh,
        onSubjectClick = onSubjectClick,
        onBackClick = onBackClick,
        onIncrementProgress = viewModel::incrementEpisodeProgress,
        modifier = modifier,
    )
}

private val COLLECTION_TYPES =
    listOf(
        CollectionType.DOING,
        CollectionType.WISH,
        CollectionType.COLLECT,
        CollectionType.ON_HOLD,
        CollectionType.DROPPED,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserCollectionsContent(
    uiState: UserCollectionsUiState,
    onTypeSelect: (CollectionType) -> Unit,
    onFilterSelect: (CollectionSubjectFilter) -> Unit,
    onRefresh: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    onIncrementProgress: (UserCollection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState =
        rememberPagerState(
            initialPage = COLLECTION_TYPES.indexOf(uiState.selectedType).coerceAtLeast(0),
        ) {
            COLLECTION_TYPES.size
        }
    val coroutineScope = rememberCoroutineScope()
    val listStates = List(COLLECTION_TYPES.size) { rememberLazyListState() }

    // 左右滑动手势翻页时，通知外层加载新分类数据
    LaunchedEffect(pagerState.currentPage) {
        val targetType = COLLECTION_TYPES[pagerState.currentPage]
        if (uiState.selectedType != targetType) {
            onTypeSelect(targetType)
        }
    }

    // 外部传入或点击时同步平滑滚动 Pager
    LaunchedEffect(uiState.selectedType) {
        val targetIndex = COLLECTION_TYPES.indexOf(uiState.selectedType).coerceAtLeast(0)
        if (pagerState.currentPage != targetIndex) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = {
                    Text(
                        text = "我的收藏",
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
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            CollectionTypeTabs(
                selectedType = uiState.selectedType,
                selectedIndex = pagerState.currentPage,
                onSelectType = { type ->
                    val targetIndex = COLLECTION_TYPES.indexOf(type)
                    if (targetIndex >= 0) {
                        if (pagerState.currentPage != targetIndex) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetIndex)
                            }
                        } else {
                            coroutineScope.launch {
                                listStates.getOrNull(targetIndex)?.animateScrollToItem(0)
                            }
                        }
                    }
                    onTypeSelect(type)
                },
            )

            SubjectFilterRow(
                selectedFilter = uiState.selectedSubjectFilter,
                onSelectFilter = onFilterSelect,
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val pageType = COLLECTION_TYPES[page]
                val pageCollections = uiState.collectionsByType[pageType].orEmpty()
                val isPageLoading = uiState.loadingTypes.contains(pageType)
                val isPageLoaded = uiState.collectionsByType.containsKey(pageType)
                val pageError = uiState.errorByType[pageType] ?: uiState.error

                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing && uiState.selectedType == pageType,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        isPageLoading && !isPageLoaded -> {
                            CollectionLoadingView()
                        }

                        pageError != null && pageCollections.isEmpty() -> {
                            ErrorCollectionsView(
                                errorMessage = pageError,
                                onRetry = onRefresh,
                            )
                        }

                        !isPageLoading && isPageLoaded && pageCollections.isEmpty() -> {
                            EmptyCollectionsView(
                                onRefresh = onRefresh,
                            )
                        }

                        else -> {
                            LazyColumn(
                                state = listStates[page],
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(
                                    items = pageCollections,
                                    key = { it.subjectId },
                                ) { item ->
                                    UserCollectionCard(
                                        collection = item,
                                        isUpdating = uiState.updatingSubjectIds.contains(item.subjectId),
                                        onSubjectClick = onSubjectClick,
                                        onIncrementProgress = { onIncrementProgress(item) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionTypeTabs(
    selectedType: CollectionType,
    selectedIndex: Int = COLLECTION_TYPES.indexOf(selectedType).coerceAtLeast(0),
    onSelectType: (CollectionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
    ) {
        COLLECTION_TYPES.forEachIndexed { index, type ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelectType(type) },
                text = {
                    Text(type.label)
                },
            )
        }
    }
}

@Composable
private fun CollectionLoadingView(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            CollectionSkeletonCard()
        }
    }
}

@Composable
private fun CollectionSkeletonCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(width = 64.dp, height = 88.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.65f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.35f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.85f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)),
                )
            }
        }
    }
}

@Composable
private fun SubjectFilterRow(
    selectedFilter: CollectionSubjectFilter,
    onSelectFilter: (CollectionSubjectFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(CollectionSubjectFilter.entries) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onSelectFilter(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun UserCollectionCard(
    collection: UserCollection,
    isUpdating: Boolean,
    onSubjectClick: (Long) -> Unit,
    onIncrementProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject = collection.subject
    val title = subject?.displayName ?: "条目 #${collection.subjectId}"
    val coverUrl = subject?.images?.bestImage.orEmpty()
    val eps = subject?.eps ?: 0
    val totalEps = subject?.totalEpisodes?.takeIf { it > 0 } ?: eps
    val epStatus = collection.epStatus
    val canIncrement = totalEps == 0 || epStatus < totalEps

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onSubjectClick(collection.subjectId) },
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CoverImage(
                url = coverUrl,
                contentDescription = title,
                modifier = Modifier.width(76.dp),
                cornerRadius = 10.dp,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val typeName = getSubjectTypeName(subject?.type ?: collection.subjectType)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = typeName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    if (collection.rate > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "${collection.rate} 分",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val progressText =
                        if (totalEps > 0) {
                            "进度: $epStatus / $totalEps 话"
                        } else {
                            "进度: $epStatus 话"
                        }
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FilledTonalIconButton(
                        onClick = onIncrementProgress,
                        enabled = !isUpdating && canIncrement,
                        modifier = Modifier.size(32.dp),
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlusOne,
                                contentDescription = "+1 话",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                if (collection.comment.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FormatQuote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = collection.comment,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCollectionsView(
    message: String = "暂无该分类收藏",
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRefresh) {
            Text("刷新")
        }
    }
}

@Composable
private fun ErrorCollectionsView(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "加载收藏失败",
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
            Text("重试")
        }
    }
}

private fun getSubjectTypeName(type: Int): String =
    when (type) {
        1 -> "书籍"
        2 -> "动画"
        3 -> "音乐"
        4 -> "游戏"
        6 -> "三次元"
        else -> "条目"
    }
