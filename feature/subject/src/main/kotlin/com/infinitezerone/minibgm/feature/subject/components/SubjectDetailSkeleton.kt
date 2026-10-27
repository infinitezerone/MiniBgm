package com.infinitezerone.minibgm.feature.subject.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonBox
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonState
import com.infinitezerone.minibgm.core.designsystem.component.rememberSkeletonState
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_POSTER_ASPECT_RATIO
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes

/**
 * 条目详情完整首屏骨架屏（用于深层直达无前序缓存时的秒级流光加载态）。
 * 与真实 SubjectDetailContent 首屏几何尺寸 1:1 严格同构，消除布局跳动（CLS）。
 */
@Composable
fun SubjectDetailFullSkeleton(
    modifier: Modifier = Modifier,
    topPadding: Dp = 12.dp,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false,
    ) {
        // 1. 顶部 Hero 卡片骨架
        item(key = "skeleton_header") {
            SubjectHeaderSkeleton(skeletonState = skeletonState)
        }

        // 2. 吸顶二级 Tab 栏骨架
        item(key = "skeleton_tab_bar") {
            SubjectDetailTabBarSkeleton(skeletonState = skeletonState)
        }

        // 3. 详情主体骨架段（评分口碑直方图、简介、角色、制作团队）
        item(key = "skeleton_body") {
            SubjectDetailBodySkeleton(skeletonState = skeletonState)
        }
    }
}

/**
 * 头部 Hero 骨架（海报 104dp、标题、副标题、评分徽标、元信息胶囊与全宽操作栏）。
 */
@Composable
fun SubjectHeaderSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 顶部图文混排：左侧海报 + 右侧标题与元数据
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // 左侧海报占位（104dp 宽，3:4 比例，16dp 圆角）
            Box(
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                SkeletonBox(
                    modifier =
                        Modifier
                            .width(104.dp)
                            .aspectRatio(BGM_POSTER_ASPECT_RATIO),
                    shape = RoundedCornerShape(16.dp),
                    state = skeletonState,
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 右侧信息栏
            Column(
                modifier = Modifier.weight(1f),
            ) {
                // 大标题占位
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.88f).height(20.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 原名 / 副标题占位
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.55f).height(13.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 评分金色徽章与 Rank 占位
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(
                        modifier = Modifier.size(width = 52.dp, height = 20.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                    SkeletonBox(
                        modifier = Modifier.size(width = 62.dp, height = 20.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 元数据胶囊行（类型、开播年份、集数）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(
                        modifier = Modifier.size(width = 38.dp, height = 18.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                    SkeletonBox(
                        modifier = Modifier.size(width = 64.dp, height = 18.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                    SkeletonBox(
                        modifier = Modifier.size(width = 44.dp, height = 18.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                }
            }
        }

        // 全宽横排操作区骨架：左侧主播放 CTA 胶囊（40dp 高） + 右侧 3 个 40dp 实体微操作圆盘
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 主 CTA 胶囊
            SkeletonBox(
                modifier = Modifier.weight(1f).height(40.dp),
                shape = CircleShape,
                state = skeletonState,
            )

            // 收藏圆盘 (40dp)
            SkeletonBox(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                state = skeletonState,
            )

            // 选集圆盘 (40dp)
            SkeletonBox(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                state = skeletonState,
            )

            // 分享圆盘 (40dp)
            SkeletonBox(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                state = skeletonState,
            )
        }
    }
}

/**
 * 二级 Tab 栏骨架（概览 / 讨论），44dp 高度，与真实 SubjectDetailTabBar 完全贴合。
 */
@Composable
fun SubjectDetailTabBarSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 选中态 Tab 胶囊骨架
        SkeletonBox(
            modifier = Modifier.size(width = 72.dp, height = 32.dp),
            shape = RoundedCornerShape(16.dp),
            state = skeletonState,
        )

        // 未选中态 Tab 胶囊骨架
        SkeletonBox(
            modifier = Modifier.size(width = 72.dp, height = 32.dp),
            shape = RoundedCornerShape(16.dp),
            state = skeletonState,
        )
    }
}

/**
 * 条目详情正文骨架组件：
 * 纯净自然流，包含评分口碑分布、剧情简介、核心角色与制作团队骨架。
 */
@Composable
fun SubjectDetailBodySkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 1. 评分口碑与标签分布直方图骨架
        RatingDistributionSkeleton(skeletonState = skeletonState)

        // 2. 剧情简介折叠段落骨架
        SubjectSummarySkeleton(skeletonState = skeletonState)

        // 3. 登场角色与声优骨架
        SubjectCharactersSkeleton(skeletonState = skeletonState)

        // 4. 演职制作团队骨架
        SubjectStaffSkeleton(skeletonState = skeletonState)
    }
}

/**
 * 评分口碑分布骨架（影评级口碑总览卡，左侧评分大号，右侧 10 档柱状图与标签）。
 */
@Composable
fun RatingDistributionSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：大号评分与口碑指标
            Column(
                modifier = Modifier.widthIn(min = 100.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SkeletonBox(
                    modifier = Modifier.size(width = 68.dp, height = 28.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
                SkeletonBox(
                    modifier = Modifier.size(width = 76.dp, height = 12.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
                SkeletonBox(
                    modifier = Modifier.size(width = 52.dp, height = 16.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
            }

            // 右侧：10 档经典垂直波峰柱状图占位
            Row(
                modifier = Modifier.weight(1f).height(64.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                val heights = listOf(14.dp, 20.dp, 28.dp, 40.dp, 56.dp, 48.dp, 36.dp, 26.dp, 18.dp, 12.dp)
                heights.forEach { h ->
                    SkeletonBox(
                        modifier = Modifier.width(8.dp).height(h),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                }
            }
        }

        // 标签胶囊行
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tagWidths = listOf(56.dp, 68.dp, 48.dp, 60.dp, 52.dp)
            tagWidths.forEach { w ->
                SkeletonBox(
                    modifier = Modifier.size(width = w, height = 24.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
            }
        }
    }
}

/**
 * 剧情简介段落骨架（标题 + 3 行自然流文本）。
 */
@Composable
fun SubjectSummarySkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonBox(
            modifier = Modifier.size(width = 68.dp, height = 18.dp),
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

/**
 * 登场角色与声优骨架（标题 + 2x2 规整双列微卡片，与 CharacterSummaryTile 1:1 同构）。
 */
@Composable
fun SubjectCharactersSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(
                modifier = Modifier.size(width = 80.dp, height = 18.dp),
                shape = RoundedCornerShape(4.dp),
                state = skeletonState,
            )
            SkeletonBox(
                modifier = Modifier.size(width = 40.dp, height = 14.dp),
                shape = RoundedCornerShape(4.dp),
                state = skeletonState,
            )
        }

        // 2x2 微卡片
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(2) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                            modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(8.dp)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // 角色立绘占位 (36dp x 46dp)
                                SkeletonBox(
                                    modifier = Modifier.size(width = 36.dp, height = 46.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    state = skeletonState,
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    SkeletonBox(
                                        modifier = Modifier.fillMaxWidth(0.75f).height(13.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        state = skeletonState,
                                    )
                                    SkeletonBox(
                                        modifier = Modifier.fillMaxWidth(0.55f).height(11.dp),
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
    }
}

/**
 * 制作团队（Staff）骨架（标题 + 2x2 精致职人铭牌网格）。
 */
@Composable
fun SubjectStaffSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(
                modifier = Modifier.size(width = 68.dp, height = 18.dp),
                shape = RoundedCornerShape(4.dp),
                state = skeletonState,
            )
            SkeletonBox(
                modifier = Modifier.size(width = 36.dp, height = 14.dp),
                shape = RoundedCornerShape(4.dp),
                state = skeletonState,
            )
        }

        // 2x2 职人铭牌网格
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(2) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                            modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(8.dp)),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                SkeletonBox(
                                    modifier = Modifier.size(width = 38.dp, height = 11.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    state = skeletonState,
                                )
                                SkeletonBox(
                                    modifier = Modifier.fillMaxWidth(0.7f).height(13.dp),
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
}

/**
 * 关联作品区域骨架（横向作品海报卡）。
 */
@Composable
fun SubjectRelationsSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkeletonBox(
            modifier = Modifier.size(width = 68.dp, height = 18.dp),
            shape = RoundedCornerShape(4.dp),
            state = skeletonState,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(3) {
                Column(
                    modifier = Modifier.width(96.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SkeletonBox(
                        modifier = Modifier.width(96.dp).aspectRatio(BGM_POSTER_ASPECT_RATIO),
                        shape = RoundedCornerShape(8.dp),
                        state = skeletonState,
                    )
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth(0.85f).height(12.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                }
            }
        }
    }
}

/**
 * 社区短评列表骨架（纯净流光，替代突兀的圆圈 Loading）。
 */
@Composable
fun SubjectCommentListSkeleton(
    count: Int = 3,
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(count) { index ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SkeletonBox(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            state = skeletonState,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SkeletonBox(
                                modifier = Modifier.size(width = 80.dp, height = 14.dp),
                                shape = RoundedCornerShape(4.dp),
                                state = skeletonState,
                            )
                            SkeletonBox(
                                modifier = Modifier.size(width = 54.dp, height = 11.dp),
                                shape = RoundedCornerShape(4.dp),
                                state = skeletonState,
                            )
                        }
                    }

                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth(0.92f).height(13.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth(0.68f).height(13.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                }
                if (index < count - 1) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    )
                }
            }
        }
    }
}

/**
 * 社区讨论版话题列表骨架。
 */
@Composable
fun SubjectTopicListSkeleton(
    count: Int = 3,
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(count) { index ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth(0.85f).height(16.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkeletonBox(
                            modifier = Modifier.size(width = 60.dp, height = 12.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                        SkeletonBox(
                            modifier = Modifier.size(width = 44.dp, height = 12.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                    }
                }
                if (index < count - 1) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    )
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
