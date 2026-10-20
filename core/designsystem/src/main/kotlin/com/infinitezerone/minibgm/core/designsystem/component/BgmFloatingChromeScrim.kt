package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.AppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.LocalAppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.LocalLuminousTokens

/**
 * 悬浮导航背后的底部渐隐层。
 *
 * 位于内容层之上、悬浮底栏之下；
 * 从下方的页面底色平滑向上过渡为完全透明，使向下穿行的列表内容自然慢慢被遮挡，
 * 同时温润托起悬浮在最前方的无边框实体 Dock 药丸。
 */
@Composable
fun BgmFloatingChromeScrim(
    modifier: Modifier = Modifier,
    height: Dp = 136.dp,
) {
    val uiStyle = LocalAppUiStyle.current
    val tokens = LocalLuminousTokens.current
    val bottomColor =
        if (uiStyle == AppUiStyle.Luminous) {
            tokens.backdropBottom
        } else {
            MaterialTheme.colorScheme.background
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                bottomColor.copy(alpha = 0.18f),
                                bottomColor.copy(alpha = 0.58f),
                                bottomColor.copy(alpha = 0.90f),
                                bottomColor,
                            ),
                    ),
                ),
    )
}
