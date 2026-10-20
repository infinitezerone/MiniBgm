package com.infinitezerone.minibgm.core.designsystem.component

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.AppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.BgmElevation
import com.infinitezerone.minibgm.core.designsystem.theme.BgmGlassLevel
import com.infinitezerone.minibgm.core.designsystem.theme.LocalAppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.LocalLuminousTokens

/**
 * 固定种子的 96px 噪点贴图。
 *
 * 真实玻璃/亚克力材质需要极细的颗粒来打散大面积渐变，避免色带与“塑料膜”感；
 * 固定种子保证截图测试和每次启动的纹理完全一致。
 */
private val GlassNoiseBitmap: ImageBitmap by lazy {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    var state = 0x9E3779B9.toInt()
    for (index in pixels.indices) {
        state = state * 1664525 + 1013904223
        val value = (state ushr 16) and 0xFF
        pixels[index] = (value shl 24) or 0xFFFFFF
    }
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    bitmap.asImageBitmap()
}

private val GlassNoiseShader: Shader by lazy {
    ImageShader(GlassNoiseBitmap, TileMode.Repeated, TileMode.Repeated)
}

/**
 * 统一玻璃材质容器。
 *
 * 材质由四层组成：半透明渐变、顶部内高光、底部内阴影、细噪点，不调用真实背景模糊；
 * AMOLED 或低端设备可无感降级为实体面板。
 *
 * 这对应 2026 年主流厂商的共同取舍：Apple Liquid Glass 的镜面高光、Microsoft Acrylic 的噪点、
 * ColorOS 17 Liquid Acrylic 的预烘焙半透明层，都在避免实时折射的 GPU 成本。
 * 适用范围仅限系统 chrome 与瞬态浮层（悬浮导航、Snackbar、临时面板）；
 * 内容卡片与容器统一使用 BgmFloatingFrame，不要把玻璃当内容背景。
 * 调用方负责提供背景内容，没有背景可透的界面不要使用玻璃，避免变成“半透明色块”。
 */
@Composable
fun BgmGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    level: BgmGlassLevel = BgmGlassLevel.Regular,
    shadowElevation: Dp = BgmElevation.glassChrome,
    content: @Composable () -> Unit,
) {
    val uiStyle = LocalAppUiStyle.current
    val tokens = LocalLuminousTokens.current
    val resolvedShape =
        shape ?: if (uiStyle == AppUiStyle.Luminous) tokens.glassShape else MaterialTheme.shapes.extraLarge

    if (uiStyle != AppUiStyle.Luminous) {
        Surface(
            modifier = modifier,
            shape = resolvedShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = shadowElevation,
            content = content,
        )
        return
    }

    val glassModifier =
        modifier
            .shadow(
                elevation = shadowElevation,
                shape = resolvedShape,
                clip = false,
                ambientColor = tokens.glow,
                spotColor = tokens.glow,
            ).clip(resolvedShape)
            .background(brush = tokens.glassBrush(level), shape = resolvedShape)
            .border(width = 1.dp, brush = tokens.glassBorderBrush(), shape = resolvedShape)

    Box(modifier = glassModifier) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val stroke = 1.dp.toPx()
                        val horizontalInset = 14.dp.toPx()
                        val highlightBrush =
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        tokens.glassInnerHighlight,
                                        tokens.glassInnerHighlight,
                                        Color.Transparent,
                                    ),
                                startX = horizontalInset,
                                endX = size.width - horizontalInset,
                            )
                        val shadeBrush =
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        tokens.glassInnerShade,
                                        tokens.glassInnerShade,
                                        Color.Transparent,
                                    ),
                                startX = horizontalInset,
                                endX = size.width - horizontalInset,
                            )
                        val grainBrush = ShaderBrush(GlassNoiseShader)
                        onDrawBehind {
                            if (tokens.glassGrainAlpha > 0f) {
                                drawRect(
                                    brush = grainBrush,
                                    alpha = tokens.glassGrainAlpha,
                                    colorFilter = ColorFilter.tint(tokens.glassGrain),
                                )
                            }
                            drawLine(
                                brush = highlightBrush,
                                start = Offset(horizontalInset, stroke / 2f),
                                end = Offset(size.width - horizontalInset, stroke / 2f),
                                strokeWidth = stroke,
                            )
                            drawLine(
                                brush = shadeBrush,
                                start = Offset(horizontalInset, size.height - stroke / 2f),
                                end = Offset(size.width - horizontalInset, size.height - stroke / 2f),
                                strokeWidth = stroke,
                            )
                        }
                    },
        )
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            content = content,
        )
    }
}
