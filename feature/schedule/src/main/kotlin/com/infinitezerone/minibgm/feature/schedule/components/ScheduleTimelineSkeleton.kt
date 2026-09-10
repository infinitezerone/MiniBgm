package com.infinitezerone.minibgm.feature.schedule.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonBox
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonState
import com.infinitezerone.minibgm.core.designsystem.component.rememberSkeletonState

/**
 * 放送时刻表时间轴骨架屏加载状态。
 * 使用统一的 [SkeletonState] 驱动全屏对齐流光扫光。
 */
@Composable
fun ScheduleTimelineSkeleton(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val dotColor = MaterialTheme.colorScheme.surfaceContainerHighest

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
    ) {
        items(6) { index ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val trackCenterX = 45.dp.toPx()
                            val dotCenterY = 18.dp.toPx()

                            if (index < 5) {
                                drawLine(
                                    color = outlineVariant,
                                    start = Offset(trackCenterX, dotCenterY),
                                    end = Offset(trackCenterX, size.height + 16.dp.toPx()),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                            drawCircle(
                                color = dotColor,
                                radius = 4.dp.toPx(),
                                center = Offset(trackCenterX, dotCenterY),
                            )
                        },
                verticalAlignment = Alignment.Top,
            ) {
                // 左侧时间占位
                Column(
                    modifier = Modifier.width(36.dp).padding(top = 10.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    SkeletonBox(
                        modifier = Modifier.width(32.dp).height(14.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                // 右侧卡片占位
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 海报占位（保持 0.7f 比例与实际海报一致）
                        SkeletonBox(
                            modifier =
                                Modifier
                                    .width(58.dp)
                                    .aspectRatio(0.7f),
                            shape = RoundedCornerShape(8.dp),
                            state = skeletonState,
                        )

                        // 文本信息占位
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SkeletonBox(
                                modifier = Modifier.fillMaxWidth(0.78f).height(16.dp),
                                shape = RoundedCornerShape(4.dp),
                                state = skeletonState,
                            )
                            SkeletonBox(
                                modifier = Modifier.fillMaxWidth(0.48f).height(12.dp),
                                shape = RoundedCornerShape(4.dp),
                                state = skeletonState,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SkeletonBox(
                                    modifier = Modifier.size(width = 54.dp, height = 20.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    state = skeletonState,
                                )
                                SkeletonBox(
                                    modifier = Modifier.size(width = 42.dp, height = 20.dp),
                                    shape = RoundedCornerShape(6.dp),
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
