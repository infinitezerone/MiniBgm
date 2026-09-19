package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(
        primary = PrimaryDark,
        onPrimary = OnPrimaryDark,
        primaryContainer = PrimaryContainerDark,
        onPrimaryContainer = OnPrimaryContainerDark,
        inversePrimary = InversePrimaryDark,
        secondary = SecondaryDark,
        onSecondary = OnSecondaryDark,
        secondaryContainer = SecondaryContainerDark,
        onSecondaryContainer = OnSecondaryContainerDark,
        tertiary = TertiaryDark,
        onTertiary = OnTertiaryDark,
        tertiaryContainer = TertiaryContainerDark,
        onTertiaryContainer = OnTertiaryContainerDark,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        surfaceTint = PrimaryDark,
        inverseSurface = InverseSurfaceDark,
        inverseOnSurface = InverseOnSurfaceDark,
        error = ErrorDark,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark,
        scrim = ScrimDark,
        surfaceBright = SurfaceBrightDark,
        surfaceDim = SurfaceDimDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        surfaceContainerHighest = SurfaceContainerHighestDark,
        surfaceContainerLow = SurfaceContainerLowDark,
        surfaceContainerLowest = SurfaceContainerLowestDark,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = PrimaryLight,
        onPrimary = OnPrimaryLight,
        primaryContainer = PrimaryContainerLight,
        onPrimaryContainer = OnPrimaryContainerLight,
        inversePrimary = InversePrimaryLight,
        secondary = SecondaryLight,
        onSecondary = OnSecondaryLight,
        secondaryContainer = SecondaryContainerLight,
        onSecondaryContainer = OnSecondaryContainerLight,
        tertiary = TertiaryLight,
        onTertiary = OnTertiaryLight,
        tertiaryContainer = TertiaryContainerLight,
        onTertiaryContainer = OnTertiaryContainerLight,
        background = BackgroundLight,
        onBackground = OnBackgroundLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        surfaceTint = PrimaryLight,
        inverseSurface = InverseSurfaceLight,
        inverseOnSurface = InverseOnSurfaceLight,
        error = ErrorLight,
        onError = OnErrorLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
        outline = OutlineLight,
        outlineVariant = OutlineVariantLight,
        scrim = ScrimLight,
        surfaceBright = SurfaceBrightLight,
        surfaceDim = SurfaceDimLight,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        surfaceContainerHighest = SurfaceContainerHighestLight,
        surfaceContainerLow = SurfaceContainerLowLight,
        surfaceContainerLowest = SurfaceContainerLowestLight,
    )

/**
 * AMOLED 纯黑档：品牌/文本角色与深灰档完全一致，仅表面与容器阶梯沉到纯黑或近纯黑
 * （见 Color.kt Layer 3）。仅应在深色模式下启用。
 */
private val AmoledDarkColorScheme =
    DarkColorScheme.copy(
        background = BackgroundAmoledDark,
        surface = SurfaceAmoledDark,
        surfaceVariant = SurfaceVariantAmoledDark,
        surfaceDim = SurfaceDimAmoledDark,
        surfaceBright = SurfaceBrightAmoledDark,
        surfaceContainerLowest = SurfaceContainerLowestAmoledDark,
        surfaceContainerLow = SurfaceContainerLowAmoledDark,
        surfaceContainer = SurfaceContainerAmoledDark,
        surfaceContainerHigh = SurfaceContainerHighAmoledDark,
        surfaceContainerHighest = SurfaceContainerHighestAmoledDark,
        inverseOnSurface = InverseOnSurfaceAmoledDark,
    )

/** 供 Glance 小组件等无 Material You 动态取色能力的宿主桥接品牌色 */
val MiniBgmLightColors: ColorScheme = LightColorScheme
val MiniBgmDarkColors: ColorScheme = DarkColorScheme
val MiniBgmAmoledDarkColors: ColorScheme = AmoledDarkColorScheme

@Composable
fun MiniBgmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledDark: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // minSdk 31 起 Material You 动态取色恒可用，无需再判 SDK_INT
    val colorScheme =
        when {
            // AMOLED 纯黑档是用户显式选择，优先级高于动态取色的深色方案（动态取色本身无 AMOLED 概念）
            darkTheme && amoledDark -> AmoledDarkColorScheme
            dynamicColor -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    androidx.compose.material3.MaterialTheme(
        colorScheme = colorScheme,
        typography = BgmTypography,
        shapes = BgmShapes,
        content = content,
    )
}
