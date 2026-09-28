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
import androidx.compose.material3.Surface
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
                                .height(24.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        state = skeletonState,
                    )
                    // 身份元信息条骨架（@用户名 · UID）
                    SkeletonBox(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.40f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        state = skeletonState,
                    )
                    // 会员徽章胶囊骨架
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkeletonBox(
                            modifier =
                                Modifier
                                    .size(width = 84.dp, height = 20.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            state = skeletonState,
                        )
                        SkeletonBox(
                            modifier =
                                Modifier
                                    .size(width = 84.dp, height = 20.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            state = skeletonState,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 签名行骨架：与真实卡片的细线签名排版等高
            SkeletonBox(
                modifier =
                    Modifier
                        .fillMaxWidth(0.75f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp)),
                state = skeletonState,
            )

            // 追番统计条骨架：真实卡片在有追番足迹时追加 14dp 间距 + 三等分数字格，
            // 骨架按「有足迹」这一常见形态对齐，避免加载完成时头部突然长出一行。
            // 格子高度对 titleMedium 数字行与 labelSmall 标签行——行高由它决定（尾部「最近打卡」是同行内联，不影响高度）。
            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            SkeletonBox(
                                modifier =
                                    Modifier
                                        .size(width = 34.dp, height = 19.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                state = skeletonState,
                            )
                            SkeletonBox(
                                modifier =
                                    Modifier
                                        .size(width = 44.dp, height = 11.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                state = skeletonState,
                            )
                        }
                    }
                }
            }
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
            // 头部标题与收藏总量骨架（终态：标题 + 「共 N 条」副标，无右上入口）
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SkeletonBox(
                    modifier =
                        Modifier
                            .size(width = 88.dp, height = 20.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    state = skeletonState,
                )
                SkeletonBox(
                    modifier =
                        Modifier
                            .size(width = 52.dp, height = 12.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    state = skeletonState,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 核心主区三大状态（在看、想看、看过）数字看板骨架
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(3) {
                    SkeletonBox(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(80.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        state = skeletonState,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 五维占比条骨架
            SkeletonBox(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                state = skeletonState,
            )

            Spacer(modifier = Modifier.height(12.dp))

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
