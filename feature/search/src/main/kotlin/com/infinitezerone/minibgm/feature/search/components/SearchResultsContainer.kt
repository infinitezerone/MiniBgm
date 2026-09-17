package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.feature.search.SearchSort
import com.infinitezerone.minibgm.feature.search.SearchViewMode
import kotlinx.coroutines.flow.distinctUntilChanged

/** 搜索结果列表（支持多维排序、列表/网格双模切换与无限滚动触底加载） */
@Composable
fun SearchResultsList(
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
    onSubjectClick: (SubjectDetailRoute) -> Unit,
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
                LaunchedEffect(listState, results.size, hasMore) {
                    if (!hasMore) return@LaunchedEffect
                    snapshotFlow {
                        val total = listState.layoutInfo.totalItemsCount
                        val lastVisible =
                            listState.layoutInfo.visibleItemsInfo
                                .lastOrNull()
                                ?.index ?: 0
                        total > 0 && lastVisible >= total - 4
                    }.distinctUntilChanged().collect { shouldLoad ->
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
                LaunchedEffect(gridState, results.size, hasMore) {
                    if (!hasMore) return@LaunchedEffect
                    snapshotFlow {
                        val total = gridState.layoutInfo.totalItemsCount
                        val lastVisible =
                            gridState.layoutInfo.visibleItemsInfo
                                .lastOrNull()
                                ?.index ?: 0
                        total > 0 && lastVisible >= total - 6
                    }.distinctUntilChanged().collect { shouldLoad ->
                        if (shouldLoad && hasMore && !isLoadingMore) {
                            onLoadMore()
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
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
fun SearchSortFilterBar(
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
fun SearchResultsFooter(
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
