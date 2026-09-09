package com.infinitezerone.minibgm.feature.search.components

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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExploreOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 探索页双列瀑布流与焦点大卡骨架屏加载状态 */
@Composable
fun ExploreSkeletonLoading(modifier: Modifier = Modifier) {
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
fun SpotlightSkeletonCard(
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

data class WaterfallCardVariation(
    val hookLines: Int = 1,
)

@Composable
fun WaterfallSkeletonCard(
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
fun ExploreEmptyState(
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
fun ExploreErrorState(
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
