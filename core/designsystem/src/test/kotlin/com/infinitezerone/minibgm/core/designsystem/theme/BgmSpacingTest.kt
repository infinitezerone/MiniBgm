package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BgmSpacingTest {
    @Test
    fun baseScale_staysOnTheProjectFourDpGrid() {
        assertEquals(
            listOf(0.dp, 1.dp, 2.dp, 4.dp, 8.dp, 12.dp, 16.dp, 20.dp, 24.dp, 32.dp),
            listOf(
                BgmSpacing.none,
                BgmSpacing.hairline,
                BgmSpacing.xxs,
                BgmSpacing.xs,
                BgmSpacing.sm,
                BgmSpacing.md,
                BgmSpacing.lg,
                BgmSpacing.xl,
                BgmSpacing.xxl,
                BgmSpacing.xxxl,
            ),
        )
        listOf(
            BgmSpacing.xs,
            BgmSpacing.sm,
            BgmSpacing.md,
            BgmSpacing.lg,
            BgmSpacing.xl,
            BgmSpacing.xxl,
            BgmSpacing.xxxl,
        ).forEach { spacing -> assertEquals(0f, spacing.value % 4f) }
    }

    @Test
    fun topBar_keepsCompactHeightAndTouchSafeInsets() {
        assertEquals(48.dp, BgmSpacing.TopBar.height)
        assertEquals(4.dp, BgmSpacing.TopBar.horizontal)
        assertEquals(8.dp, BgmSpacing.TopBar.titleHorizontal)
        assertEquals(1.dp, BgmSpacing.TopBar.hairline)
    }

    @Test
    fun floatingNav_keepsMeasuredPillGeometry() {
        assertEquals(220.dp, BgmSpacing.FloatingNav.minWidth)
        assertEquals(380.dp, BgmSpacing.FloatingNav.maxWidth)
        assertEquals(10.dp, BgmSpacing.FloatingNav.horizontalPadding)
        assertEquals(6.dp, BgmSpacing.FloatingNav.verticalPadding)
        assertEquals(6.dp, BgmSpacing.FloatingNav.itemGap)
        assertEquals(64.dp, BgmSpacing.FloatingNav.itemMinWidth)
    }

    @Test
    fun snackbarClearance_matchesFloatingNavHeightPlusSinkAndBreatheRoom() {
        val measuredFloatingNavHeight = 68.dp
        val barSinkMargin = 12.dp
        val breatheRoom = 8.dp

        assertEquals(
            measuredFloatingNavHeight + barSinkMargin + breatheRoom,
            BgmSpacing.Snackbar.floatingBarClearance,
        )
        assertTrue(BgmSpacing.Snackbar.floatingBarClearance > measuredFloatingNavHeight)
    }
}
