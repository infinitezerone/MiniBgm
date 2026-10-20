package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LuminousTokensTest {
    @Test
    fun lightTokens_useTranslucentGlassAndLuminousRadii() {
        val tokens =
            buildLuminousTokens(
                darkTheme = false,
                amoledDark = false,
                colorScheme = MiniBgmLightColors,
            )

        assertTrue(tokens.glassEnabled)
        assertEquals(Color.White, tokens.glassTint)
        assertEquals(RoundedCornerShape(32.dp), tokens.glassShape)
        assertEquals(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), tokens.sheetShape)
        assertEquals(RoundedCornerShape(20.dp), tokens.innerShape)
        assertTrue(tokens.sheetContainer.alpha < 1f)
        assertTrue(tokens.glow.alpha > 0f)
        assertTrue(tokens.glassInnerHighlight.alpha > tokens.glassInnerShade.alpha)
        assertTrue(tokens.glassGrainAlpha in 0f..0.08f)
        assertTrue(tokens.glassAlphaScale < 1f)
    }

    @Test
    fun darkTokens_keepReadableOpaqueScrimAndSoftHighlights() {
        val tokens =
            buildLuminousTokens(
                darkTheme = true,
                amoledDark = false,
                colorScheme = MiniBgmDarkColors,
            )

        assertTrue(tokens.glassEnabled)
        assertTrue(tokens.glassTint.alpha == 1f)
        assertTrue(tokens.sheetScrim.alpha >= 0.6f)
        assertTrue(tokens.glassBorderStart.alpha > tokens.glassBorderEnd.alpha)
        assertTrue(tokens.glassInnerHighlight.alpha > 0f)
        assertTrue(tokens.glassInnerShade.alpha > 0f)
        assertTrue(tokens.glassGrainAlpha in 0f..0.08f)
        assertEquals(1f, tokens.glassAlphaScale)
    }

    @Test
    fun amoledTokens_disableTransparencyAndFallBackToSolidPanels() {
        val tokens =
            buildLuminousTokens(
                darkTheme = true,
                amoledDark = true,
                colorScheme = MiniBgmAmoledDarkColors,
            )

        assertFalse(tokens.glassEnabled)
        assertEquals(1f, tokens.glassTint.alpha)
        assertEquals(1f, tokens.sheetContainer.alpha)
        assertEquals(1f, tokens.topBarTop.alpha)
        assertEquals(1f, tokens.topBarBottom.alpha)
        assertEquals(0f, tokens.glassGrainAlpha)
    }

    @Test
    fun reducedTransparency_fallsBackToSolidChromeWithoutGrain() {
        val tokens =
            buildLuminousTokens(
                darkTheme = false,
                amoledDark = false,
                colorScheme = MiniBgmLightColors,
                reducedTransparency = true,
            )

        assertFalse(tokens.glassEnabled)
        assertEquals(1f, tokens.glassTint.alpha)
        assertEquals(0f, tokens.glassGrainAlpha)
        assertEquals(1f, tokens.glassAlphaScale)
        assertEquals(MiniBgmLightColors.background, tokens.backdropMiddle)
    }
}
