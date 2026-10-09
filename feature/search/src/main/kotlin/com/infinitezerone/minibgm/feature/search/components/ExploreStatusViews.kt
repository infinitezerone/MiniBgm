package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmStatusState
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonBox
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonState
import com.infinitezerone.minibgm.core.designsystem.component.rememberSkeletonState
import com.infinitezerone.minibgm.core.designsystem.component.skeletonNode
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.search.R
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/** 探索页双列瀑布流与焦点大卡骨架屏加载状态 */
@Composable
fun ExploreSkeletonLoading(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    val columns = StaggeredGridCells.Adaptive(minSize = 160.dp)

    LazyVerticalStaggeredGrid(
        columns = columns,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
        userScrollEnabled = false,
        modifier = modifier,
    ) {
        // 1. 顶部焦点大卡骨架
        item(span = StaggeredGridItemSpan.FullLine) {
            SpotlightSkeletonCard(
                skeletonState = skeletonState,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        // 2. 双列瀑布流骨架卡片（精简为首屏 4 张，兼顾视觉饱满度与帧率流畅性）
        val variations =
            listOf(
                WaterfallCardVariation(hookLines = 1),
                WaterfallCardVariation(hookLines = 2),
                WaterfallCardVariation(hookLines = 2),
                WaterfallCardVariation(hookLines = 1),
            )
        items(variations.size) { index ->
            WaterfallSkeletonCard(
                variation = variations[index],
                skeletonState = skeletonState,
            )
        }
    }
}

@Composable
fun SpotlightSkeletonCard(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
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
                SkeletonBox(
                    modifier = Modifier.size(width = 88.dp, height = 20.dp),
                    shape = RoundedCornerShape(8.dp),
                    state = skeletonState,
                )
            }

            // 底部标题、标签、剧情钩子安利占位
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 标题占位
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(20.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )

                // 标签占位行
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(
                        modifier = Modifier.size(width = 48.dp, height = 18.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                    SkeletonBox(
                        modifier = Modifier.size(width = 56.dp, height = 18.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                }

                // 剧情钩子引言占位
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.88f)
                            .height(13.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
            }
        }
    }
}

data class WaterfallCardVariation(
    val hookLines: Int = 1,
)

@Composable
fun WaterfallSkeletonCard(
    variation: WaterfallCardVariation,
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
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
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .skeletonNode(
                            state = skeletonState,
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        ),
            ) {
                // 左上角评分角标占位
                Box(
                    modifier =
                        Modifier
                            .padding(6.dp)
                            .size(width = 38.dp, height = 18.dp)
                            .clip(RoundedCornerShape(8.dp))
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
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.82f)
                            .height(15.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )

                // 标签占位行
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkeletonBox(
                        modifier = Modifier.size(width = 40.dp, height = 14.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                    SkeletonBox(
                        modifier = Modifier.size(width = 48.dp, height = 14.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                }

                // 参差安利文案行
                if (variation.hookLines >= 1) {
                    SkeletonBox(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.92f)
                                .height(11.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                }
                if (variation.hookLines >= 2) {
                    SkeletonBox(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.68f)
                                .height(11.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                }
            }
        }
    }
}

@Composable
fun ExploreEmptyState(
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BgmStatusState(
        message = stringResource(R.string.feature_search_explore_empty_message),
        modifier = modifier.padding(24.dp),
        title = stringResource(R.string.feature_search_explore_empty_title),
        icon = BgmIcons.ExploreOff,
        iconSize = 64.dp,
        actionLabel = stringResource(R.string.feature_search_action_reset_filter),
        onAction = onReset,
    )
}

@Composable
fun ExploreErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BgmStatusState(
        message = errorMessage,
        modifier = modifier.padding(24.dp),
        title = stringResource(R.string.feature_search_explore_error_title),
        titleColor = MaterialTheme.colorScheme.error,
        actionLabel = stringResource(DesignSystemR.string.core_designsystem_action_retry),
        onAction = onRetry,
    )
}
