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
 * 从最顶端的状态栏底色自然向下垂向平滑渐变到完全透明，
 * 使向上滚动的列表与卡片能够像滑入晨雾中一样“慢慢被遮挡消融”，
 * 与状态栏实现无缝浑然一体的柔和过渡，同时确保顶栏标题与操作大圆盘清晰立现。
 *
 * 高度默认自适应状态栏高度（statusBar + 48dp 顶栏 + 28dp 衰减过渡），保底 108dp。
 */
@Composable
fun BgmTopChromeScrim(
    modifier: Modifier = Modifier,
    height: Dp? = null,
) {
    val uiStyle = LocalAppUiStyle.current
    val tokens = LocalLuminousTokens.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val resolvedHeight = height ?: (statusBarHeight + BgmSpacing.TopBar.height + 28.dp).coerceAtLeast(108.dp)
    val topColor =
        if (uiStyle == AppUiStyle.Luminous) {
            tokens.backdropTop
        } else {
            MaterialTheme.colorScheme.background
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(resolvedHeight)
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                topColor,
                                topColor.copy(alpha = 0.88f),
                                topColor.copy(alpha = 0.50f),
                                topColor.copy(alpha = 0.15f),
                                Color.Transparent,
                            ),
                    ),
                ),
    )
}
