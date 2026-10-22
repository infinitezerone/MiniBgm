package com.infinitezerone.minibgm.ui

import com.infinitezerone.minibgm.core.navigation.AssistantRoute
import com.infinitezerone.minibgm.core.navigation.InAppLoginRoute
import com.infinitezerone.minibgm.core.navigation.InAppWebRoute
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.ScheduleRoute
import com.infinitezerone.minibgm.core.navigation.SettingsRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgmFullScreenRouteTest {
    @Test
    fun onlyImmersiveRoutesRequireFullScreenOnWideWindows() {
        assertTrue(PlayerRoute(subjectId = 1L, episodeId = 2L).isFullScreenBgmRoute())
        assertTrue(InAppLoginRoute.isFullScreenBgmRoute())
        assertTrue(InAppWebRoute(url = "https://bgm.tv").isFullScreenBgmRoute())

        assertFalse(ScheduleRoute.isFullScreenBgmRoute())
        assertFalse(SubjectDetailRoute(subjectId = 1L).isFullScreenBgmRoute())
        assertFalse(SettingsRoute.isFullScreenBgmRoute())
        assertFalse(AssistantRoute().isFullScreenBgmRoute())

        assertFalse(shouldShowNavigationRail(isWideScreen = true, currentRoute = PlayerRoute(1L, 2L)))
        assertFalse(shouldShowNavigationRail(isWideScreen = true, currentRoute = InAppLoginRoute))
        assertFalse(shouldShowNavigationRail(isWideScreen = true, currentRoute = InAppWebRoute("https://bgm.tv")))
        assertTrue(shouldShowNavigationRail(isWideScreen = true, currentRoute = ScheduleRoute))
        assertFalse(shouldShowNavigationRail(isWideScreen = false, currentRoute = ScheduleRoute))
    }
}
