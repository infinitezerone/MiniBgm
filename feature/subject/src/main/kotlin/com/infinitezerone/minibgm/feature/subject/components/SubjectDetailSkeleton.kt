package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonBox
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonState
import com.infinitezerone.minibgm.core.designsystem.component.rememberSkeletonState

/**
 * 条目详情完整首屏骨架屏（用于深层直达无前序缓存时的秒级加载态）。
 */
@Composable
fun SubjectDetailFullSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
    ) {
        // 1. 顶部 Header 骨架卡片（与真实 SubjectHeaderCard 几何尺寸严格同构）
        item(key = "skeleton_header") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // 海报封面占位（严格保持 108.dp 宽，0.7f 比例）
                    SkeletonBox(
                        modifier =
                            Modifier
                                .width(108.dp)
                                .aspectRatio(0.7f),
                        shape = RoundedCornerShape(10.dp),
                        state = skeletonState,
                    )

                    // 右侧元数据占位
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.9f).height(20.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.6f).height(14.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // 评分栏占位
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SkeletonBox(
                                modifier = Modifier.size(width = 44.dp, height = 24.dp),
                                shape = RoundedCornerShape(6.dp),
                                state = skeletonState,
                            )
                            SkeletonBox(
                                modifier = Modifier.size(width = 68.dp, height = 14.dp),
                                shape = RoundedCornerShape(4.dp),
                                state = skeletonState,
                            )
                        }

                        // 标签胶囊占位
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SkeletonBox(
                                modifier = Modifier.size(width = 42.dp, height = 20.dp),
                                shape = RoundedCornerShape(6.dp),
                                state = skeletonState,
                            )
                            SkeletonBox(
                                modifier = Modifier.size(width = 50.dp, height = 20.dp),
                                shape = RoundedCornerShape(6.dp),
                                state = skeletonState,
                            )
                            SkeletonBox(
                                modifier = Modifier.size(width = 38.dp, height = 20.dp),
                                shape = RoundedCornerShape(6.dp),
                                state = skeletonState,
                            )
                        }
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
                                shape = RoundedCornerShape(27.dp),
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
