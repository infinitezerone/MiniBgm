package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BgmFloatingPageScaffoldDefaultsTest {
    @Test
    fun floatingPageScaffold_keepsChromeSinkAndContentClearanceSeparate() {
        assertEquals(12.dp, BgmFloatingPageScaffoldDefaults.FloatingChromeSink)
        assertEquals(76.dp, BgmFloatingPageScaffoldDefaults.ContentBottomClearance)
        assertEquals(56.dp, BgmFloatingPageScaffoldDefaults.ContentTopClearance)
        assertTrue(
            BgmFloatingPageScaffoldDefaults.ContentBottomClearance >
                BgmFloatingPageScaffoldDefaults.FloatingChromeSink,
        )
    }

    @Test
    fun topBarModes_haveExpectedVariants() {
        val modes = BgmTopBarMode.entries
        assertEquals(2, modes.size)
        assertTrue(modes.contains(BgmTopBarMode.Immersive))
        assertTrue(modes.contains(BgmTopBarMode.Solid))
    }
}
