package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeTest {
    @Test
    fun darkColorScheme_fulfillsDarkImmersionAndCompleteRoles() {
        val dark = MiniBgmDarkColors

        // Layer 2: Streamer / ACG Dark Immersion surfaces
        assertEquals(Color(0xFF101012), dark.background)
        assertEquals(Color(0xFF101012), dark.surface)
        assertEquals(Color(0xFF38383F), dark.surfaceVariant)
        assertEquals(Color(0xFF101012), dark.surfaceDim)
        assertEquals(Color(0xFF44444C), dark.surfaceBright)
        assertEquals(Color(0xFF0A0A0C), dark.surfaceContainerLowest)
        assertEquals(Color(0xFF26262B), dark.surfaceContainerLow)
        assertEquals(Color(0xFF2E2E33), dark.surfaceContainer)
        assertEquals(Color(0xFF38383F), dark.surfaceContainerHigh)
        assertEquals(Color(0xFF44444C), dark.surfaceContainerHighest)

        // Text & Outlines
        assertEquals(Color(0xFFF1F3F5), dark.onBackground)
        assertEquals(Color(0xFFF1F3F5), dark.onSurface)
        assertEquals(Color(0xFF9E9EA8), dark.onSurfaceVariant)
        assertEquals(Color(0x14FFFFFF), dark.outlineVariant)

        // Primary Brand (Sakura Pink Tone 80)
        assertEquals(PrimaryDark, dark.primary)
        assertEquals(OnPrimaryDark, dark.onPrimary)
        assertEquals(PrimaryContainerDark, dark.primaryContainer)
        assertEquals(OnPrimaryContainerDark, dark.onPrimaryContainer)
    }

    @Test
    fun lightColorScheme_fulfillsCleanLightSurfacesAndCompleteRoles() {
        val light = MiniBgmLightColors

        // Light Surfaces - Clear gradation between canvas and floating cards
        assertEquals(Color(0xFFF6F6F7), light.background)
        assertEquals(Color(0xFF18181B), light.onBackground)
        assertEquals(Color(0xFFFFFFFF), light.surface)
        assertEquals(Color(0xFFEBEBEF), light.surfaceVariant)
        assertEquals(Color(0xFFD6D6DC), light.surfaceDim)
        assertEquals(Color(0xFFFFFFFF), light.surfaceBright)
        assertEquals(Color(0xFF18181B), light.onSurface)
        assertEquals(Color(0xFF524346), light.onSurfaceVariant)
        assertEquals(Color(0xFFFFFFFF), light.surfaceContainerLowest)
        assertEquals(Color(0xFFFFFFFF), light.surfaceContainerLow)
        assertEquals(Color(0xFFF4F4F6), light.surfaceContainer)
        assertEquals(Color(0xFFECECEF), light.surfaceContainerHigh)
        assertEquals(Color(0xFFE2E2E6), light.surfaceContainerHighest)
        assertEquals(Color(0xFF8E8E93), light.outline)
        assertEquals(Color(0x14000000), light.outlineVariant)

        // Primary Brand (Sakura Pink Tone 40 with high vibrancy and WCAG AA compliance)
        assertEquals(PrimaryLight, light.primary)
        assertEquals(OnPrimaryLight, light.onPrimary)
        assertEquals(PrimaryContainerLight, light.primaryContainer)
        assertEquals(OnPrimaryContainerLight, light.onPrimaryContainer)
    }

    @Test
    fun semanticColors_acgStatuses_useCalibratedGentleTonesAndPairedContainers() {
        // Layer 3: Radix / Perception-calibrated ACG Status Colors
        assertEquals(Color(0xFF818CF8), StatusWish)
        assertEquals(Color(0xFF34D399), StatusDoing)
        assertEquals(Color(0xFFF472B6), StatusCollect)
        assertEquals(Color(0xFFFBBF24), StatusOnHold)
        assertEquals(Color(0xFF94A3B8), StatusDropped)

        // Paired Containers & OnContainers (Light)
        assertEquals(Color(0xFFEEF2FF), StatusWishContainer)
        assertEquals(Color(0xFF3730A3), OnStatusWishContainer)
        assertEquals(Color(0xFFECFDF5), StatusDoingContainer)
        assertEquals(Color(0xFF065F46), OnStatusDoingContainer)
        assertEquals(Color(0xFFFDF2F8), StatusCollectContainer)
        assertEquals(Color(0xFF9D174D), OnStatusCollectContainer)
        assertEquals(Color(0xFFFFFBEB), StatusOnHoldContainer)
        assertEquals(Color(0xFF92400E), OnStatusOnHoldContainer)
        assertEquals(Color(0xFFF1F5F9), StatusDroppedContainer)
        assertEquals(Color(0xFF334155), OnStatusDroppedContainer)

        // Paired Containers & OnContainers (Dark)
        assertEquals(Color(0xFF262A56), StatusWishContainerDark)
        assertEquals(Color(0xFFC7D2FE), OnStatusWishContainerDark)
        assertEquals(Color(0xFF0F392B), StatusDoingContainerDark)
        assertEquals(Color(0xFFA7F3D0), OnStatusDoingContainerDark)
        assertEquals(Color(0xFF4C1D36), StatusCollectContainerDark)
        assertEquals(Color(0xFFFBCFE8), OnStatusCollectContainerDark)
        assertEquals(Color(0xFF422006), StatusOnHoldContainerDark)
        assertEquals(Color(0xFFFDE68A), OnStatusOnHoldContainerDark)
        assertEquals(Color(0xFF1E293B), StatusDroppedContainerDark)
        assertEquals(Color(0xFFCBD5E1), OnStatusDroppedContainerDark)

        // Airing (Tailwind Orange family)
        assertEquals(Color(0xFFEA580C), StatusAiring)
        assertEquals(Color(0xFFFFF7ED), StatusAiringContainer)
        assertEquals(Color(0xFFC2410C), OnStatusAiringContainer)
        assertEquals(Color(0xFF451A03), StatusAiringContainerDark)
        assertEquals(Color(0xFFFDBA74), OnStatusAiringContainerDark)

        // Type palette (Tailwind family, aligned with status colors)
        assertEquals(Color(0xFFFFF7ED), TypeBookContainer)
        assertEquals(Color(0xFFC2410C), OnTypeBook)
        assertEquals(Color(0xFF4A2210), TypeBookContainerDark)
        assertEquals(Color(0xFFFDBA74), OnTypeBookDark)
        assertEquals(Color(0xFFEFF6FF), TypeAnimeContainer)
        assertEquals(Color(0xFF1D4ED8), OnTypeAnime)
        assertEquals(Color(0xFF17335C), TypeAnimeContainerDark)
        assertEquals(Color(0xFF93C5FD), OnTypeAnimeDark)
        assertEquals(Color(0xFFFAF5FF), TypeMusicContainer)
        assertEquals(Color(0xFF7E22CE), OnTypeMusic)
        assertEquals(Color(0xFF3B1A5E), TypeMusicContainerDark)
        assertEquals(Color(0xFFD8B4FE), OnTypeMusicDark)
        assertEquals(Color(0xFFF0FDF4), TypeGameContainer)
        assertEquals(Color(0xFF15803D), OnTypeGame)
        assertEquals(Color(0xFF123B24), TypeGameContainerDark)
        assertEquals(Color(0xFF86EFAC), OnTypeGameDark)
        assertEquals(Color(0xFFFDF2F8), TypeRealContainer)
        assertEquals(Color(0xFFBE185D), OnTypeReal)
        assertEquals(Color(0xFF4C1D3C), TypeRealContainerDark)
        assertEquals(Color(0xFFF9A8D4), OnTypeRealDark)

        // Classic badge (Tailwind Indigo-600)
        assertEquals(Color(0xFF4F46E5), BadgeClassic)

        // Rating
        assertEquals(Color(0xFFF59E0B), RatingGold)
        assertEquals(Color(0xFFFCD34D), RatingGoldBright)

        // Platform brand colors
        assertEquals(Color(0xFFFB7299), BrandBilibili)
        assertEquals(Color(0xFFE0567E), OnBrandBilibili)
        assertEquals(Color(0xFF009688), BrandMikan)
        assertEquals(Color(0xFF00796B), OnBrandMikan)

        // Backward compatibility mappings
        assertEquals(StatusWish, ActionWish)
        assertEquals(StatusDoing, ActionDoing)
        assertEquals(StatusCollect, ActionCollect)
        assertEquals(StatusOnHold, ActionOnHold)
        assertEquals(StatusDropped, ActionDropped)
        assertEquals(StatusOnHold, WishOrange)
    }

    @Test
    fun theme_contrastVerification_ensuresLegibility() {
        val dark = MiniBgmDarkColors
        val light = MiniBgmLightColors

        // Dark text on dark surfaces should be high luminance
        assertTrue(dark.onBackground.luminance() > 0.8f)
        assertTrue(dark.onSurface.luminance() > 0.8f)
        assertTrue(dark.background.luminance() < 0.02f)
        assertTrue(dark.surface.luminance() < 0.02f)

        // Light text on light surfaces should be low luminance
        assertTrue(light.onBackground.luminance() < 0.1f)
        assertTrue(light.onSurface.luminance() < 0.1f)
        assertTrue(light.background.luminance() > 0.9f)

        // Primary brand color satisfies WCAG AA (>= 4.5:1) against white
        val primaryContrastAgainstWhite = (1.0f + 0.05f) / (light.primary.luminance() + 0.05f)
        assertTrue(primaryContrastAgainstWhite >= 4.5f, "PrimaryLight contrast ($primaryContrastAgainstWhite) must be >= 4.5:1")
    }

    /**
     * 全角色 WCAG 对比度断言（对齐 WCAG 1.4.3 / 1.4.11）：
     * 正文与主操作前景/背景配对 ≥4.5:1；次要文本与描边等图形配对 ≥3:1。
     * 配色改动若拉低任一配对，此处先于人工审查失败。
     */
    @Test
    fun theme_colorPairs_meetWcagContrastTiers() {
        listOf(MiniBgmLightColors, MiniBgmDarkColors).forEach { scheme ->
            val bodyPairs =
                listOf(
                    scheme.onBackground to scheme.background,
                    scheme.onSurface to scheme.surface,
                    scheme.onSurface to scheme.surfaceContainerLow,
                    scheme.onSurface to scheme.surfaceContainer,
                    scheme.onSurface to scheme.surfaceContainerHigh,
                    scheme.onSurfaceVariant to scheme.surface,
                    scheme.onSurfaceVariant to scheme.surfaceContainerLow,
                    scheme.onSurfaceVariant to scheme.surfaceContainer,
                    scheme.onPrimary to scheme.primary,
                    scheme.onPrimaryContainer to scheme.primaryContainer,
                    scheme.onSecondaryContainer to scheme.secondaryContainer,
                    scheme.onTertiaryContainer to scheme.tertiaryContainer,
                    scheme.onError to scheme.error,
                    scheme.onErrorContainer to scheme.errorContainer,
                    scheme.inverseOnSurface to scheme.inverseSurface,
                )
            bodyPairs.forEach { (fg, bg) ->
                val ratio = contrastRatio(fg, bg)
                assertTrue(ratio >= 4.5f, "正文级配对 $fg on $bg 对比度 $ratio 低于 WCAG AA 4.5:1")
            }
            val graphicPairs =
                listOf(
                    scheme.onSurfaceVariant to scheme.surfaceContainerHighest,
                    scheme.outline to scheme.surface,
                )
            graphicPairs.forEach { (fg, bg) ->
                val ratio = contrastRatio(fg, bg)
                assertTrue(ratio >= 3f, "图形/次要配对 $fg on $bg 对比度 $ratio 低于 WCAG 3:1")
            }
        }
    }

    private fun contrastRatio(
        fg: Color,
        bg: Color,
    ): Float {
        val lf = fg.luminance()
        val lb = bg.luminance()
        return (maxOf(lf, lb) + 0.05f) / (minOf(lf, lb) + 0.05f)
    }

    @Test
    fun theme_designTokens_typographyAndShapesAreConfigured() {
        // Assert typography hierarchy and shapes are properly initialized
        assertEquals(57.sp, BgmTypography.displayLarge.fontSize)
        assertEquals(16.sp, BgmTypography.titleMedium.fontSize)
        assertEquals(14.sp, BgmTypography.bodyMedium.fontSize)
        assertEquals(11.sp, BgmTypography.labelSmall.fontSize)

        assertTrue(BgmShapes.extraSmall is RoundedCornerShape)
        assertTrue(BgmShapes.medium is RoundedCornerShape)
        assertTrue(BgmShapes.large is RoundedCornerShape)
    }
}
