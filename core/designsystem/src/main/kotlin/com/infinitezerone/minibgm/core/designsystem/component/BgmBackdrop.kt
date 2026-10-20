package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.infinitezerone.minibgm.core.designsystem.theme.AppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.LocalAppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.LocalLuminousTokens

/**
 * 页面背景层：为实体悬浮框架与玻璃 chrome 提供统一的底色和层级参照。
 *
 * 使用普通渐变而非模糊图层，避免额外 RenderEffect、离屏纹理与低端机掉帧。
 */
@Composable
fun BgmBackdrop(
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable BoxScope.() -> Unit,
) {
    val uiStyle = LocalAppUiStyle.current
    val tokens = LocalLuminousTokens.current
    val brush =
        if (uiStyle == AppUiStyle.Luminous) {
            tokens.backdropBrush()
        } else {
            null
        }

    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
        Box(
            modifier =
                if (brush != null) {
                    modifier.background(brush)
                } else {
                    modifier.background(MaterialTheme.colorScheme.background)
                },
            content = content,
        )
    }
}
