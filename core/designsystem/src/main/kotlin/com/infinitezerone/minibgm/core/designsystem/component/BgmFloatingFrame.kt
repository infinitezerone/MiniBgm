package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.BgmElevation
import com.infinitezerone.minibgm.core.designsystem.theme.BgmOpacity
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing

/**
 * 应用内容层的统一悬浮框架（Floating Frame）。
 *
 * 2026 年 ColorOS 17 / HyperOS 4 的系统应用并没有把玻璃铺到内容层：玻璃属于系统 UI
 * （状态栏、快捷开关、悬浮 Dock），应用本身用实体的大圆角悬浮框建立层级。
 * 因此这里刻意不提供透明度、模糊或噪点参数：
 * - 实体 `surfaceContainerLow` + 1dp `outlineVariant` 细边 + 2dp 阴影；
 * - 20dp 圆角、16dp 内边距，与 BgmShapes 的圆角阶梯保持一致；
 * - 可点击时走 `Surface(onClick)`，保留涟漪与无障碍语义。
 *
 * 玻璃材质只允许出现在 [BgmGlassSurface]（悬浮导航、Snackbar 等瞬态 chrome）。
 */
object BgmFloatingFrameDefaults {
    val Shape: Shape = BgmShapes.extraLarge
    val ContentPadding: Dp = BgmSpacing.lg
    val CompactContentPadding: Dp = BgmSpacing.md
    val Elevation: Dp = BgmElevation.content
    val BorderAlpha: Float = BgmOpacity.border
}

@Composable
fun BgmFloatingFrame(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = BgmFloatingFrameDefaults.Shape,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = contentColorFor(containerColor),
    border: BorderStroke? =
        BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = BgmOpacity.border),
        ),
    shadowElevation: Dp = BgmFloatingFrameDefaults.Elevation,
    contentPadding: PaddingValues = PaddingValues(BgmFloatingFrameDefaults.ContentPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            shadowElevation = shadowElevation,
            border = border,
            content = body,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            shadowElevation = shadowElevation,
            border = border,
            content = body,
        )
    }
}
