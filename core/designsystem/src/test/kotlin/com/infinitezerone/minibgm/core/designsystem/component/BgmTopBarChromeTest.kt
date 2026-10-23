package com.infinitezerone.minibgm.core.designsystem.component

import org.junit.Test
import kotlin.test.assertEquals

class BgmTopBarChromeTest {
    @Test
    fun collapsingClampsProgressAndDrivesBothChannels() {
        assertEquals(BgmTopBarChrome(surfaceProgress = 0f, titleProgress = 0f), BgmTopBarChrome.collapsing(-0.2f))
        assertEquals(BgmTopBarChrome(surfaceProgress = 0.5f, titleProgress = 0.5f), BgmTopBarChrome.collapsing(0.5f))
        assertEquals(BgmTopBarChrome(surfaceProgress = 1f, titleProgress = 1f), BgmTopBarChrome.collapsing(1.2f))
    }

    @Test
    fun normalizedClampsIndependentProgressChannels() {
        assertEquals(
            BgmTopBarChrome(surfaceProgress = 1f, titleProgress = 0f),
            BgmTopBarChrome(surfaceProgress = 4f, titleProgress = -1f).normalized(),
        )
    }
}
