package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectComment
import kotlinx.coroutines.flow.distinctUntilChanged

/** 双列瀑布流列表（支持上滑触底自动分页加载） */
@Composable
fun WaterfallGridList(
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
    LaunchedEffect(gridState, subjects.size, hasMore) {
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
