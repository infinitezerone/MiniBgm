package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.HorizontalDivider
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
 * 个人页首帧加载骨架屏：
 * 在冷启动或首次切换进入「我的」Tab 且凭据库尚未返回有效会话判定前展示。
 *
 * 几何严格对齐四层结构的真实排版——身份头部（无卡片外壳）→ 通栏数字带 → 吸顶分区 Tab → 收藏内容流，
 * 避免首帧突兀闪烁「未登录引导卡片」，也避免加载完成时整页跳版。
 */
@Composable
internal fun UserScreenSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = 96.dp),
    ) {
        UserProfileHeroSkeleton(skeletonState = skeletonState)
        TrackingStatsSkeleton(skeletonState = skeletonState)
        CollectionTabsSkeleton(skeletonState = skeletonState)
        Spacer(modifier = Modifier.height(12.dp))
        CollectionListSkeleton(skeletonState = skeletonState)
    }
}

/** 第一层身份头部骨架：与 [UserProfileHero] 1:1 几何对齐（无卡片外壳，头像 88dp） */
@Composable
private fun UserProfileHeroSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SkeletonBox(
                modifier =
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape),
                state = skeletonState,
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 昵称（headlineSmall）
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    state = skeletonState,
                )
                // 身份元信息（@用户名 · UID）
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.40f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    state = skeletonState,
                )
                // 入站年限
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.30f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    state = skeletonState,
                )
                Spacer(modifier = Modifier.height(2.dp))
                // 会员 / 账号胶囊
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

        Spacer(modifier = Modifier.height(16.dp))

        // 签名行
        SkeletonBox(
            modifier =
                Modifier
                    .fillMaxWidth(0.75f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp)),
            state = skeletonState,
        )
    }
}

/** 第二层通栏数字带骨架：等分三格 + 上下细分隔线 */
@Composable
private fun TrackingStatsSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
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
                                .size(width = 34.dp, height = 22.dp)
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
        HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
        SkeletonBox(
            modifier =
                Modifier
                    .padding(top = 7.dp)
                    .size(width = 96.dp, height = 11.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .align(Alignment.CenterHorizontally),
            state = skeletonState,
        )
    }
}

/** 第三层吸顶分区 Tab 骨架：五等分 */
@Composable
private fun CollectionTabsSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(5) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SkeletonBox(
                        modifier =
                            Modifier
                                .size(width = 46.dp, height = 14.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        state = skeletonState,
                    )
                }
            }
        }
    }
}

/** 第四层收藏内容流骨架：三张与 [UserCollectionCard] 同高的占位 */
@Composable
private fun CollectionListSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(
                        modifier = Modifier.size(width = 64.dp, height = 88.dp),
                        shape = RoundedCornerShape(8.dp),
                        state = skeletonState,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SkeletonBox(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.65f)
                                    .height(16.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                        SkeletonBox(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.35f)
                                    .height(12.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SkeletonBox(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                    }
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun UserScreenSkeletonPreview() {
    MiniBgmTheme {
        UserScreenSkeleton(modifier = Modifier.fillMaxSize())
    }
}
