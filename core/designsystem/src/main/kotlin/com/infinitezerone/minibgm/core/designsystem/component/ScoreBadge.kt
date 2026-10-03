package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import java.util.Locale

/**
 * 封面角标评分徽章：黑底 + 星标 + 白字分数。
 *
 * 各卡片的评分角标统一走此组件，避免底色透明度与分数格式各自漂移；
 * [shape] 按徽章所贴的封面角落传入（如只圆化贴角一侧）。
 */
@Composable
fun ScoreBadge(
    score: Double,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    starSize: Dp = 11.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall,
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
) {
    if (score <= 0.0) return
    Surface(
        shape = shape,
        color = Color.Black.copy(alpha = 0.75f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = BgmIcons.Star,
                contentDescription = null,
                tint = RatingGold,
                modifier = Modifier.size(starSize),
            )
            Text(
                text = score.formatScore(),
                style = textStyle,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

/** 评分展示统一格式：固定一位小数（如 7.9、8.0） */
fun Double.formatScore(): String = String.format(Locale.ROOT, "%.1f", this)
