package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.data.repository.RatingInsights
import com.infinitezerone.minibgm.core.designsystem.component.bounceClickable
import com.infinitezerone.minibgm.core.designsystem.component.rememberBounceOnClick
import kotlin.math.round

/**
 * 评分洞察卡：均分大字 + 1..10 分布柱图。
 *
 * 与头部统计条刻意保持**形态差异**——统计条是三等分数字格，这里是「数字 + 图表」，
 * 避免同一页出现两排同形数字行（主流个人页的「数字带」与「分布图」也是分开的两种形态）。
 *
 * 数据缺失（未评分 / 拉取失败）时整卡不渲染；加载中且无历史数据时退回等几何骨架，避免首帧跳动。
 */
@Composable
internal fun RatingInsightsCard(
    insights: RatingInsights?,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val averageRate = insights?.averageRate
    if (averageRate == null || insights.ratedCount <= 0) {
        if (isLoading) RatingInsightsSkeleton(modifier = modifier)
        return
    }

    val shape = MaterialTheme.shapes.extraLarge
    val bounceState = rememberBounceOnClick(pressedScale = 0.98f)
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .bounceClickable(state = bounceState, onClickLabel = "查看评分过的条目") { onClick() },
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "评分分布",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "共 ${insights.ratedCount} 部已评分",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.width(72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = formatAverageRating(averageRate),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "均分",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                RatingDistributionChart(
                    distribution = insights.distribution,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 1..10 分档柱图：柱高按最高分档归一化；空分档保留极细基底，避免与「有数据」混淆 */
@Composable
private fun RatingDistributionChart(
    distribution: List<Int>,
    modifier: Modifier = Modifier,
) {
    val maxCount = (MIN_RATE..MAX_RATE).maxOf { distribution.getOrElse(it) { 0 } }
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (rate in MIN_RATE..MAX_RATE) {
                val count = distribution.getOrElse(rate) { 0 }
                val fraction =
                    if (count > 0 && maxCount > 0) {
                        (count.toFloat() / maxCount).coerceIn(MIN_BAR_FRACTION, 1f)
                    } else {
                        EMPTY_BAR_FRACTION
                    }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(
                                if (count > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                },
                            ),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 只标两端刻度：十档全标在手机宽度下会挤成一团
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = MIN_RATE.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = MAX_RATE.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 均分展示：保留一位小数（KMP 下避免使用平台相关的格式化 API） */
private fun formatAverageRating(value: Double): String = (round(value * 10.0) / 10.0).toString()

private const val MIN_RATE = 1
private const val MAX_RATE = 10
private const val MIN_BAR_FRACTION = 0.12f
private const val EMPTY_BAR_FRACTION = 0.04f
