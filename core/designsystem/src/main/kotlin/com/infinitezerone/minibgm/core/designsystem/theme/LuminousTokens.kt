package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** 玻璃层级只表达视觉重量，不参与业务语义。 */
enum class BgmGlassLevel(
    internal val topAlpha: Float,
    internal val bottomAlpha: Float,
) {
    Subtle(0.68f, 0.46f),
    Regular(0.78f, 0.58f),
    Prominent(0.86f, 0.70f),
}

/**
 * Luminous 视觉层 token：chrome 材质、圆角、光晕与降级策略的唯一来源。
 *
 * 这里刻意不包含页面间距与业务颜色；页面结构重构时可以独立演进。
 */
@Immutable
data class LuminousTokens(
    val glassEnabled: Boolean,
    val glassTint: Color,
    val glassBorderStart: Color,
    val glassBorderEnd: Color,
    val glassInnerHighlight: Color,
    val glassInnerShade: Color,
    val glassGrain: Color,
    val glassGrainAlpha: Float,
    val glassAlphaScale: Float,
    val glow: Color,
    val navSelection: Color,
    val topBarTop: Color,
    val topBarBottom: Color,
    val topBarHairline: Color,
    val sheetContainer: Color,
    val sheetScrim: Color,
    val backdropTop: Color,
    val backdropMiddle: Color,
    val backdropBottom: Color,
    val glassShape: Shape,
    val sheetShape: Shape,
    val innerShape: Shape,
) {
    fun backdropBrush(): Brush =
        Brush.verticalGradient(
            colors = listOf(backdropTop, backdropMiddle, backdropBottom),
        )

    fun topBarBrush(): Brush =
        Brush.verticalGradient(
            colors =
                listOf(
                    topBarTop.copy(alpha = (topBarTop.alpha * glassAlphaScale).coerceIn(0f, 1f)),
                    topBarBottom.copy(alpha = (topBarBottom.alpha * glassAlphaScale).coerceIn(0f, 1f)),
                ),
        )

    fun glassBrush(level: BgmGlassLevel): Brush =
        if (glassEnabled) {
            val topAlpha = (level.topAlpha * glassAlphaScale).coerceIn(0f, 1f)
            val bottomAlpha = (level.bottomAlpha * glassAlphaScale).coerceIn(0f, 1f)
            Brush.verticalGradient(
                colors =
                    listOf(
                        glassTint.copy(alpha = topAlpha),
                        glassTint.copy(alpha = bottomAlpha),
                    ),
            )
        } else {
            Brush.verticalGradient(colors = listOf(glassTint, glassTint))
        }

    fun glassBorderBrush(): Brush =
        Brush.verticalGradient(
            colors = listOf(glassBorderStart, glassBorderEnd),
        )
}

private val LuminousGlassShape = RoundedCornerShape(32.dp)
private val LuminousSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val LuminousInnerShape = RoundedCornerShape(20.dp)

fun buildLuminousTokens(
    darkTheme: Boolean,
    amoledDark: Boolean,
    colorScheme: ColorScheme,
    reducedTransparency: Boolean = false,
): LuminousTokens {
    val base =
        LuminousTokens(
            glassEnabled = false,
            glassTint = Color.Transparent,
            glassBorderStart = Color.Transparent,
            glassBorderEnd = Color.Transparent,
            glassInnerHighlight = Color.Transparent,
            glassInnerShade = Color.Transparent,
            glassGrain = Color.Transparent,
            glassGrainAlpha = 0f,
            glassAlphaScale = 1f,
            glow = Color.Transparent,
            navSelection = Color.Transparent,
            topBarTop = Color.Transparent,
            topBarBottom = Color.Transparent,
            topBarHairline = Color.Transparent,
            sheetContainer = Color.Transparent,
            sheetScrim = Color.Transparent,
            backdropTop = Color.Transparent,
            backdropMiddle = Color.Transparent,
            backdropBottom = Color.Transparent,
            glassShape = LuminousGlassShape,
            sheetShape = LuminousSheetShape,
            innerShape = LuminousInnerShape,
        )

    val resolved =
        when {
            amoledDark ->
                base.copy(
                    glassEnabled = false,
                    glassTint = Color(0xFF16181C),
                    glassBorderStart = Color.White.copy(alpha = 0.32f),
                    glassBorderEnd = Color.White.copy(alpha = 0.06f),
                    glassInnerHighlight = Color.White.copy(alpha = 0.20f),
                    glassInnerShade = Color.Black.copy(alpha = 0.55f),
                    glassGrain = Color.White,
                    glassGrainAlpha = 0f,
                    glassAlphaScale = 1f,
                    glow = colorScheme.primary.copy(alpha = 0.20f),
                    navSelection = colorScheme.primary.copy(alpha = 0.24f),
                    topBarTop = Color(0xFF0D0D10),
                    topBarBottom = Color.Black,
                    topBarHairline = Color.White.copy(alpha = 0.16f),
                    sheetContainer = Color(0xFF0A0A0C),
                    sheetScrim = Color.Black.copy(alpha = 0.76f),
                    backdropTop = Color(0xFF0A0A0C),
                    backdropMiddle = Color.Black,
                    backdropBottom = Color.Black,
                )
            darkTheme ->
                base.copy(
                    glassEnabled = true,
                    glassTint = Color(0xFF15181E),
                    glassBorderStart = Color.White.copy(alpha = 0.34f),
                    glassBorderEnd = Color.White.copy(alpha = 0.06f),
                    glassInnerHighlight = Color.White.copy(alpha = 0.24f),
                    glassInnerShade = Color.Black.copy(alpha = 0.38f),
                    glassGrain = Color.White,
                    glassGrainAlpha = 0.045f,
                    glassAlphaScale = 1f,
                    glow = colorScheme.primary.copy(alpha = 0.22f),
                    navSelection = colorScheme.primary.copy(alpha = 0.22f),
                    topBarTop = Color(0xFF12141B).copy(alpha = 0.90f),
                    topBarBottom = Color(0xFF12141B).copy(alpha = 0.68f),
                    topBarHairline = Color.White.copy(alpha = 0.16f),
                    sheetContainer = Color(0xFF15181E).copy(alpha = 0.96f),
                    sheetScrim = Color.Black.copy(alpha = 0.62f),
                    backdropTop = Color(0xFF18121D),
                    backdropMiddle = Color(0xFF0E1015),
                    backdropBottom = Color(0xFF0A1317),
                )
            else ->
                base.copy(
                    glassEnabled = true,
                    glassTint = Color.White,
                    glassBorderStart = Color.White.copy(alpha = 0.96f),
                    glassBorderEnd = colorScheme.outline.copy(alpha = 0.20f),
                    glassInnerHighlight = Color.White.copy(alpha = 0.90f),
                    glassInnerShade = Color(0xFF0B1220).copy(alpha = 0.10f),
                    glassGrain = Color(0xFF0B1220),
                    glassGrainAlpha = 0.030f,
                    glassAlphaScale = 0.72f,
                    glow = colorScheme.primary.copy(alpha = 0.16f),
                    navSelection = colorScheme.primary.copy(alpha = 0.16f),
                    topBarTop = Color.White.copy(alpha = 0.92f),
                    topBarBottom = Color.White.copy(alpha = 0.70f),
                    topBarHairline = colorScheme.outline.copy(alpha = 0.18f),
                    sheetContainer = Color.White.copy(alpha = 0.96f),
                    sheetScrim = Color.Black.copy(alpha = 0.30f),
                    backdropTop = Color(0xFFFDEEF3),
                    backdropMiddle = Color(0xFFF1F4F8),
                    backdropBottom = Color(0xFFEBF3FA),
                )
        }

    if (!reducedTransparency) return resolved

    return resolved.copy(
        glassEnabled = false,
        glassTint = colorScheme.surface,
        glassGrainAlpha = 0f,
        glassAlphaScale = 1f,
        topBarTop = colorScheme.surface,
        topBarBottom = colorScheme.surface,
        sheetContainer = colorScheme.surface,
        backdropTop = colorScheme.background,
        backdropMiddle = colorScheme.background,
        backdropBottom = colorScheme.background,
    )
}

val LocalLuminousTokens =
    staticCompositionLocalOf {
        buildLuminousTokens(
            darkTheme = false,
            amoledDark = false,
            colorScheme = MiniBgmLightColors,
        )
    }
