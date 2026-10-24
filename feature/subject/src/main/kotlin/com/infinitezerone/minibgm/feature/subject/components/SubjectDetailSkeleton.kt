package com.infinitezerone.minibgm.feature.subject.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonBox
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonState
import com.infinitezerone.minibgm.core.designsystem.component.rememberSkeletonState
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes

/**
 * 条目详情完整首屏骨架屏（用于深层直达无前序缓存时的秒级加载态）。
 */
@Composable
fun SubjectDetailFullSkeleton(
    modifier: Modifier = Modifier,
    topPadding: Dp = 12.dp,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
    ) {
        // 1. 顶部 Hero 骨架（与真实 SubjectHeaderCard 几何尺寸严格同构，通透无灰卡）
        item(key = "skeleton_header") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 背景弥散光晕 + 居中悬浮海报占位（对齐真实头部 246dp 光晕区，消除加载→内容跳变）
                Box(
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SkeletonBox(
                        modifier = Modifier.size(width = 176.dp, height = 246.dp),
                        shape = RoundedCornerShape(24.dp),
                        state = skeletonState,
                    )
                    SkeletonBox(
                        modifier =
                            Modifier
                                .width(140.dp)
                                .aspectRatio(0.7f),
                        shape = RoundedCornerShape(16.dp),
                        state = skeletonState,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 大标题占位
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.65f).height(24.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 副标题占位
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.42f).height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 元数据胶囊行占位
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(4) {
                        SkeletonBox(
                            modifier = Modifier.size(width = 52.dp, height = 20.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 主播放 CTA 按钮占位
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(48.dp),
                    shape = CircleShape,
                    state = skeletonState,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 次级操作行占位
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    repeat(4) {
                        SkeletonBox(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            state = skeletonState,
                        )
                    }
                }
            }
        }

        // 2. 详情主体骨架段（简介、分集、角色）
        item(key = "skeleton_body") {
            SubjectDetailBodySkeleton(
                skeletonState = skeletonState,
            )
        }
    }
}

/**
 * 条目详情正文骨架组件（用于封面已飞渡落地，下方继续流光加载完整简介与分集）。
 */
@Composable
fun SubjectDetailBodySkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. 剧情简介折叠卡片骨架
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.32f).height(18.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
                Spacer(modifier = Modifier.height(2.dp))
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.96f).height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.88f).height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.62f).height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
            }
        }

        // 2. 章节列表网格骨架
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth(0.28f).height(18.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth(0.18f).height(14.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                }

                // 8 个分集按钮占位方块
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(6) {
                        SkeletonBox(
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            state = skeletonState,
                        )
                    }
                }
            }
        }

        // 3. 演职员与角色横向滑动骨架
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.26f).height(18.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false,
                ) {
                    items(4) {
                        Column(
                            modifier = Modifier.width(68.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SkeletonBox(
                                modifier = Modifier.size(54.dp),
                                shape = RoundedCornerShape(24.dp),
                                state = skeletonState,
                            )
                            SkeletonBox(
                                modifier = Modifier.fillMaxWidth(0.8f).height(12.dp),
                                shape = RoundedCornerShape(4.dp),
                                state = skeletonState,
                            )
                            SkeletonBox(
                                modifier = Modifier.fillMaxWidth(0.6f).height(10.dp),
                                shape = RoundedCornerShape(4.dp),
                                state = skeletonState,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 章节网格加载骨架（与真实 EpisodeGrid 单元格 1:1 几何尺寸同构）
 */
@Composable
fun EpisodeGridSkeleton(
    columns: Int = 6,
    rowCount: Int = 2,
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    val columnCount = columns.coerceAtLeast(1)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(rowCount) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(columnCount) {
                    SkeletonBox(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        shape = BgmShapes.small,
                        state = skeletonState,
                    )
                }
            }
        }
    }
}

/**
 * 章节列表加载骨架（与真实 EpisodeListItem 几何尺寸同构）
 */
@Composable
fun EpisodeListSkeleton(
    itemCount: Int = 4,
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(itemCount) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = BgmShapes.large,
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 左侧集数标牌骨架
                    SkeletonBox(
                        modifier = Modifier.size(46.dp),
                        shape = BgmShapes.medium,
                        state = skeletonState,
                    )

                    // 右侧标题与副标题骨架
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.6f).height(16.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.35f).height(12.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                    }
                }
            }
        }
    }
}
