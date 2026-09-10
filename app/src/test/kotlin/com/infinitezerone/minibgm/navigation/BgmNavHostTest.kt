package com.infinitezerone.minibgm.navigation

import com.infinitezerone.minibgm.core.navigation.ExploreRoute
import com.infinitezerone.minibgm.core.navigation.ScheduleRoute
import com.infinitezerone.minibgm.core.navigation.SearchRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.UserCollectionsRoute
import com.infinitezerone.minibgm.core.navigation.UserRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgmNavHostTest {
    @Test
    fun isTopLevelRoute_returnsTrue_forDirectTopLevelRoutes() {
        assertTrue(isTopLevelRoute(ScheduleRoute))
        assertTrue(isTopLevelRoute(ExploreRoute))
        assertTrue(isTopLevelRoute(UserRoute))
    }

    @Test
    fun isTopLevelRoute_returnsTrue_forDecoratedPairTopLevelRoutes() {
        assertTrue(isTopLevelRoute(Pair("decorator_scope", ScheduleRoute)))
        assertTrue(isTopLevelRoute(Pair("decorator_scope", ExploreRoute)))
        assertTrue(isTopLevelRoute(Pair("decorator_scope", UserRoute)))
    }

    @Test
    fun isTopLevelRoute_returnsTrue_forMultipleNestedDecoratedPairRoutes() {
        val doublyDecorated = Pair("outer_decorator", Pair("inner_decorator", ScheduleRoute))
        assertTrue(isTopLevelRoute(doublyDecorated))
    }

    @Test
    fun isTopLevelRoute_returnsFalse_forSecondaryRoutes() {
        assertFalse(isTopLevelRoute(SubjectDetailRoute(1001L)))
        assertFalse(isTopLevelRoute(SearchRoute("test")))
        assertFalse(isTopLevelRoute(UserCollectionsRoute(3)))
        assertFalse(isTopLevelRoute(Pair("decorator_scope", SubjectDetailRoute(1001L))))
    }

    @Test
    fun isTopLevelRoute_returnsFalse_forNullOrArbitraryObjects() {
        assertFalse(isTopLevelRoute(null))
        assertFalse(isTopLevelRoute("ScheduleRoute"))
        assertFalse(isTopLevelRoute(12345))
        assertFalse(isTopLevelRoute(Pair("decorator_scope", "NotARoute")))
    }
}
