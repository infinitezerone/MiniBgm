package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.AppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing
import com.infinitezerone.minibgm.core.designsystem.theme.LocalAppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.LocalLuminousTokens

/**
 * 顶部导航与状态栏背后的渐隐遮罩层。
 *
 * 对齐现代旗舰系统 App 的流体沉浸式遮蔽体验：
 * 位于内容层之上、顶栏控件之下；
 * - 顶部安全区（solidHeight，默认覆盖状态栏+顶栏高度）保持 100% 表面纯色，确保顶栏标题、操作图标与状态栏时钟
 *   具有绝对的对比度与清晰度，严禁背景穿透文字与标题重叠；
 * - 安全区下方衔接平滑的多段衰减渐隐，使向上滚动的列表与卡片能够像滑入晨雾中一样“慢慢被遮挡消融”，
 *   与顶层 chrome 实现无缝浑然一体的柔和过渡。
 */
@Composable
fun BgmTopChromeScrim(
    modifier: Modifier = Modifier,
    height: Dp? = null,
    solidHeight: Dp? = null,
) {
    val uiStyle = LocalAppUiStyle.current
    val tokens = LocalLuminousTokens.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarHeight = BgmSpacing.TopBar.height

    val defaultSolidHeight = statusBarHeight + topBarHeight
    val resolvedSolidHeight = solidHeight ?: defaultSolidHeight
    val transitionThickness = 28.dp
    val resolvedTotalHeight = height ?: (resolvedSolidHeight + transitionThickness)

    val topColor =
        if (uiStyle == AppUiStyle.Luminous) {
            tokens.backdropTop
        } else {
            MaterialTheme.colorScheme.background
        }

    val solidRatio =
        if (resolvedTotalHeight > 0.dp) {
            (resolvedSolidHeight / resolvedTotalHeight).coerceIn(0f, 0.95f)
        } else {
            0.7f
        }

    val colorStops =
        arrayOf(
            0.0f to topColor,
            solidRatio to topColor,
            solidRatio + (1f - solidRatio) * 0.35f to topColor.copy(alpha = 0.72f),
            solidRatio + (1f - solidRatio) * 0.70f to topColor.copy(alpha = 0.28f),
            1.0f to Color.Transparent,
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(resolvedTotalHeight)
                .background(Brush.verticalGradient(colorStops = colorStops)),
    )
}
