package com.infinitezerone.minibgm.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun displayModeUsesFiveWidthClassesAndHeightCompactOverride() {
        assertEquals(BgmDisplayMode.COMPACT, displayModeFor(widthDp = 599, heightDp = 800))
        assertEquals(BgmDisplayMode.MEDIUM, displayModeFor(widthDp = 600, heightDp = 800))
        assertEquals(BgmDisplayMode.EXPANDED, displayModeFor(widthDp = 840, heightDp = 800))
        assertEquals(BgmDisplayMode.LARGE, displayModeFor(widthDp = 1200, heightDp = 800))
        assertEquals(BgmDisplayMode.EXTRA_LARGE, displayModeFor(widthDp = 1600, heightDp = 800))
        assertEquals(BgmDisplayMode.COMPACT, displayModeFor(widthDp = 1600, heightDp = 479))
    }
}
