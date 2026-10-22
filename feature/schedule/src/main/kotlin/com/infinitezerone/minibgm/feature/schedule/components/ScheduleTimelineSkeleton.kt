package com.infinitezerone.minibgm.feature.schedule.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
        items(5) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 时间标头占位
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(
                        modifier = Modifier.width(48.dp).height(16.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                    SkeletonBox(
                        modifier = Modifier.width(42.dp).height(14.dp),
                        shape = RoundedCornerShape(4.dp),
                        state = skeletonState,
                    )
                }

                // 全幅通透卡片占位
                Card(
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 海报占位
                        SkeletonBox(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(0.7f),
                            shape = RoundedCornerShape(8.dp),
                            state = skeletonState,
                        )

                        // 文本信息占位
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SkeletonBox(
                                    modifier = Modifier.fillMaxWidth(0.7f).height(16.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    state = skeletonState,
                                )
                                SkeletonBox(
                                    modifier = Modifier.fillMaxWidth(0.45f).height(12.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    state = skeletonState,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SkeletonBox(
                                    modifier = Modifier.width(48.dp).height(16.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    state = skeletonState,
                                )
                                SkeletonBox(
                                    modifier = Modifier.width(40.dp).height(16.dp),
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
