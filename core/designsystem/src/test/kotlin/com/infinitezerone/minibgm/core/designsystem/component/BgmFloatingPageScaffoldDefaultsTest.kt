package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BgmFloatingPageScaffoldDefaultsTest {
    @Test
    fun floatingPageScaffold_keepsChromeSinkAndContentClearanceSeparate() {
        assertEquals(12.dp, BgmFloatingPageScaffoldDefaults.FloatingChromeSink)
        assertEquals(88.dp, BgmFloatingPageScaffoldDefaults.ContentBottomClearance)
        assertTrue(
            BgmFloatingPageScaffoldDefaults.ContentBottomClearance >
                BgmFloatingPageScaffoldDefaults.FloatingChromeSink,
        )
    }
}
