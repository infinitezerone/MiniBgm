package com.infinitezerone.minibgm.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.navigation.ExploreRoute
import com.infinitezerone.minibgm.core.navigation.ScheduleRoute
import com.infinitezerone.minibgm.core.navigation.SearchRoute
import com.infinitezerone.minibgm.core.navigation.UserRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class BgmAdaptivePolicyTest {
    @Test
    fun expandedWidthWidensTheDefaultListPane() {
        assertEquals(480.dp, preferredPaneWidthFor(minWidthDp = 840, fallback = 360.dp))
        assertEquals(480.dp, preferredPaneWidthFor(minWidthDp = 1280, fallback = 360.dp))
        assertEquals(420.dp, preferredPaneWidthFor(minWidthDp = 1600, fallback = 360.dp))
        assertEquals(360.dp, preferredPaneWidthFor(minWidthDp = 839, fallback = 360.dp))
    }

    @Test
    fun onlyScheduleOwnsTheWideListDetailPane() {
        val placeholder: @androidx.compose.runtime.Composable ThreePaneScaffoldScope.() -> Unit = {}

        assertTrue(bgmTopLevelPaneMetadata(ScheduleRoute, placeholder).isNotEmpty())
        assertFalse(bgmTopLevelPaneMetadata(ExploreRoute, placeholder).isNotEmpty())
        assertFalse(bgmTopLevelPaneMetadata(SearchRoute(), placeholder).isNotEmpty())
        assertFalse(bgmTopLevelPaneMetadata(UserRoute, placeholder).isNotEmpty())
    }

    @Test
    fun splitPaneStartsOnlyWhenBothPanesCanBreathe() {
        assertFalse(shouldUseSplitPane(minWidthDp = 840))
        assertFalse(shouldUseSplitPane(minWidthDp = 1199))
        assertTrue(shouldUseSplitPane(minWidthDp = 1200))
        assertTrue(shouldUseSplitPane(minWidthDp = 1280))
    }
}
