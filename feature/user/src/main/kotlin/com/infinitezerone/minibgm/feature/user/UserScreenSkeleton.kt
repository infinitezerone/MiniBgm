package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonBox
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonState
import com.infinitezerone.minibgm.core.designsystem.component.rememberSkeletonState
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews

/**
 * 个人中心首帧加载骨架屏：
 * 在冷启动或首次切换进入「我的」Tab 且凭据库尚未返回有效会话判定前展示，
 * 严格对齐真实主页卡片的内外边距与几何形态，避免首帧突兀闪烁「未登录引导卡片」。
 */
@Composable
internal fun UserScreenSkeleton(
    isWideScreen: Boolean,
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    if (isWideScreen) {
        Row(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(0.45f)
                        .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UserProfileHeaderSkeleton(skeletonState = skeletonState)
            }
            Column(
                modifier =
                    Modifier
                        .weight(0.55f)
                        .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CollectionOverviewSkeleton(skeletonState = skeletonState)
            }
        }
    } else {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UserProfileHeaderSkeleton(skeletonState = skeletonState)
            CollectionOverviewSkeleton(skeletonState = skeletonState)
        }
    }
}

/** 个人资料概览卡片骨架：与 [UserProfileHeaderCard] 1:1 几何对齐 */
@Composable
internal fun UserProfileHeaderSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 圆形头像骨架
                SkeletonBox(
                    modifier =
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape),
                    state = skeletonState,
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 昵称条骨架
                    SkeletonBox(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.55f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        state = skeletonState,
                    )
                    // 用户名条骨架
                    SkeletonBox(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.35f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        state = skeletonState,
                    )
                    // UID 与会员标签胶囊骨架
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkeletonBox(
                            modifier =
                                Modifier
                                    .size(width = 64.dp, height = 18.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            state = skeletonState,
                        )
                        SkeletonBox(
                            modifier =
                                Modifier
                                    .size(width = 72.dp, height = 18.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            state = skeletonState,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 签名条骨架
            SkeletonBox(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp)),
                state = skeletonState,
            )
        }
    }
}

/** 收藏概览全景看板骨架：与 [CollectionOverviewCard] 1:1 几何对齐 */
@Composable
internal fun CollectionOverviewSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // 头部标题与快捷入口骨架
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(
                    modifier =
                        Modifier
                            .size(width = 110.dp, height = 20.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    state = skeletonState,
                )
                SkeletonBox(
                    modifier =
                        Modifier
                            .size(width = 60.dp, height = 16.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    state = skeletonState,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 核心主区三大状态（在看、想看、看过）等宽数据看板骨架
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(3) {
                    SkeletonBox(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(76.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        state = skeletonState,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 次级归档区两大状态（搁置、抛弃）骨架
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(2) {
                    SkeletonBox(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        state = skeletonState,
                    )
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun UserScreenSkeletonPreview() {
    MiniBgmTheme {
        UserScreenSkeleton(
            isWideScreen = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
