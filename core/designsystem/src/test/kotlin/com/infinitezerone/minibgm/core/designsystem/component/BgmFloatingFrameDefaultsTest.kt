package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BgmFloatingFrameDefaultsTest {
    @Test
    fun floatingFrame_usesSolidContentGeometryInsteadOfGlass() {
        assertEquals(BgmShapes.extraLarge, BgmFloatingFrameDefaults.Shape)
        assertEquals(16.dp, BgmFloatingFrameDefaults.ContentPadding)
        assertEquals(12.dp, BgmFloatingFrameDefaults.CompactContentPadding)
        assertEquals(2.dp, BgmFloatingFrameDefaults.Elevation)
        assertTrue(BgmFloatingFrameDefaults.BorderAlpha in 0.2f..0.5f)
    }
}
