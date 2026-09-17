package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

        // Light Surfaces
        assertEquals(Color(0xFFF8F9FA), light.background)
        assertEquals(Color(0xFF191C1E), light.onBackground)
        assertEquals(Color(0xFFFFFFFF), light.surface)
        assertEquals(Color(0xFF191C1E), light.onSurface)
        assertEquals(Color(0xFF524346), light.onSurfaceVariant)
        assertEquals(Color(0xFFFFFFFF), light.surfaceContainerLowest)
        assertEquals(Color(0xFFFFFFFF), light.surfaceContainerLow)
        assertEquals(Color(0xFFF1F3F5), light.surfaceContainer)
        assertEquals(Color(0xFFE9ECEF), light.surfaceContainerHigh)
        assertEquals(Color(0xFFDEE2E6), light.surfaceContainerHighest)
        assertEquals(Color(0xFF847376), light.outline)
        assertEquals(Color(0x0F000000), light.outlineVariant)

        // Primary Brand (Sakura Pink Tone 40)
        assertEquals(PrimaryLight, light.primary)
        assertEquals(OnPrimaryLight, light.onPrimary)
        assertEquals(PrimaryContainerLight, light.primaryContainer)
        assertEquals(OnPrimaryContainerLight, light.onPrimaryContainer)
    }

    @Test
    fun semanticColors_acgStatuses_useCalibratedGentleTones() {
        // Layer 3: Radix / Perception-calibrated ACG Status Colors
        assertEquals(Color(0xFF818CF8), StatusWish)
        assertEquals(Color(0xFF34D399), StatusDoing)
        assertEquals(Color(0xFFF472B6), StatusCollect)
        assertEquals(Color(0xFFFBBF24), StatusOnHold)
        assertEquals(Color(0xFF94A3B8), StatusDropped)

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
    }
}
