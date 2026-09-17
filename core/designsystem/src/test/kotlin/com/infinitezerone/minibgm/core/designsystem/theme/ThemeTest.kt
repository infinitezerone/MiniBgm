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
        assertEquals(Color(0xFF0E1015), dark.background)
        assertEquals(Color(0xFF12141B), dark.surface)
        assertEquals(Color(0xFF232732), dark.surfaceVariant)
        assertEquals(Color(0xFF0E1015), dark.surfaceDim)
        assertEquals(Color(0xFF2A2F3C), dark.surfaceBright)
        assertEquals(Color(0xFF0E1015), dark.surfaceContainerLowest)
        assertEquals(Color(0xFF15181E), dark.surfaceContainerLow)
        assertEquals(Color(0xFF1C2028), dark.surfaceContainer)
        assertEquals(Color(0xFF232732), dark.surfaceContainerHigh)
        assertEquals(Color(0xFF2A2F3C), dark.surfaceContainerHighest)

        // Text & Outlines
        assertEquals(Color(0xFFF1F3F5), dark.onBackground)
        assertEquals(Color(0xFFF1F3F5), dark.onSurface)
        assertEquals(Color(0xFF94A3B8), dark.onSurfaceVariant)
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
        assertEquals(Color(0xFFF1F4F8), light.background)
        assertEquals(Color(0xFF191C1E), light.onBackground)
        assertEquals(Color(0xFFFFFFFF), light.surface)
        assertEquals(Color(0xFFE8EDF2), light.surfaceVariant)
        assertEquals(Color(0xFFD8DEE6), light.surfaceDim)
        assertEquals(Color(0xFFFFFFFF), light.surfaceBright)
        assertEquals(Color(0xFF191C1E), light.onSurface)
        assertEquals(Color(0xFF524346), light.onSurfaceVariant)
        assertEquals(Color(0xFFFFFFFF), light.surfaceContainerLowest)
        assertEquals(Color(0xFFFFFFFF), light.surfaceContainerLow)
        assertEquals(Color(0xFFF6F8FA), light.surfaceContainer)
        assertEquals(Color(0xFFEEF2F6), light.surfaceContainerHigh)
        assertEquals(Color(0xFFE3E8EF), light.surfaceContainerHighest)
        assertEquals(Color(0xFF8A92A0), light.outline)
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

        // Airing
        assertEquals(Color(0xFFFF5722), StatusAiring)
        assertEquals(Color(0xFFFFEBE5), StatusAiringContainer)
        assertEquals(Color(0xFFC42B00), OnStatusAiringContainer)
        assertEquals(Color(0xFF4D1405), StatusAiringContainerDark)
        assertEquals(Color(0xFFFFCCBC), OnStatusAiringContainerDark)

        // Rating
        assertEquals(Color(0xFFF59E0B), RatingGold)
        assertEquals(Color(0xFFFCD34D), RatingGoldBright)

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
