package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * 现代 Compose 原生 [dropShadow] 扩展封装。
 *
 * 直接调用 Compose 1.9+ 原生 [androidx.compose.ui.draw.dropShadow] 与 [Shadow]：
 * - [color]: 阴影基色；
 * - [alpha]: 阴影透明度；
 * - [blur]: 高斯模糊半径；
 * - [offsetY] / [offsetX]: 纵向与横向偏移；
 * - [spread]: 外扩尺寸。
 */
fun Modifier.diffuseDropShadow(
    shape: Shape = CircleShape,
    color: Color = Color.Black,
    alpha: Float = 0.08f,
    blur: Dp = 14.dp,
    offsetY: Dp = 4.dp,
    offsetX: Dp = 0.dp,
    spread: Dp = 0.dp,
): Modifier =
    this.dropShadow(
        shape = shape,
        shadow =
            Shadow(
                radius = blur,
                spread = spread,
                color = color,
                alpha = alpha,
                offset = DpOffset(offsetX, offsetY),
            ),
    )

/**
 * ChatGPT / ColorOS 15 级双层复合弥散晕染阴影 (Aquamorphic Diffuse Shadow)。
 *
 * 基于 Compose 原生 [dropShadow] 构建的双层叠加：
 * 第一层：大半径环境漫反射 (Ambient Bloom)；
 * 第二层：近身柔和承托 (Key Soft Shadow)。
 */
fun Modifier.aquamorphicShadow(
    shape: Shape = CircleShape,
    color: Color = Color.Black,
    isDark: Boolean = false,
): Modifier {
    val ambientAlpha = if (isDark) 0.32f else 0.08f
    val ambientBlur = if (isDark) 16.dp else 14.dp
    val ambientOffset = if (isDark) 5.dp else 4.dp

    val keyAlpha = if (isDark) 0.22f else 0.05f
    val keyBlur = if (isDark) 6.dp else 5.dp
    val keyOffset = if (isDark) 2.dp else 1.5.dp

    return this
        .diffuseDropShadow(
            shape = shape,
            color = color,
            alpha = ambientAlpha,
            blur = ambientBlur,
            offsetY = ambientOffset,
            spread = 1.dp,
        ).diffuseDropShadow(
            shape = shape,
            color = color,
            alpha = keyAlpha,
            blur = keyBlur,
            offsetY = keyOffset,
            spread = 0.dp,
        )
}
