package com.infinitezerone.minibgm.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun verifyTopLevelDestinations() {
        val destinations = TopLevelDestination.entries
        assertEquals(3, destinations.size)

        val schedule = TopLevelDestination.SCHEDULE
        assertEquals("放送", schedule.labelText)
        assertEquals(ScheduleRoute, schedule.route)

        val explore = TopLevelDestination.EXPLORE
        assertEquals("探索", explore.labelText)
        assertEquals(ExploreRoute, explore.route)

        val user = TopLevelDestination.USER
        assertEquals("我的", user.labelText)
        assertEquals(UserRoute, user.route)
    }

    @Test
    fun verifySubjectDetailRoute() {
        val route = SubjectDetailRoute(subjectId = 1001L, source = "schedule")
        assertEquals(1001L, route.subjectId)
        assertEquals("schedule", route.source)
        assertNotNull(route.toString())
        assertTrue(route.toString().contains("1001"))
    }

    @Test
    fun verifySharedElementKeys_scopedBySource() {
        val scheduleKey = BgmSharedElementKeys.subjectCover(1001L, "schedule")
        val exploreKey = BgmSharedElementKeys.subjectCover(1001L, "explore")
        val blankSourceKey = BgmSharedElementKeys.subjectCover(1001L, "")

        assertEquals("subject_cover_schedule_1001", scheduleKey)
        assertEquals("subject_cover_explore_1001", exploreKey)
        // Blank source returns null to avoid unintentional cross-destination matching
        assertEquals(null, blankSourceKey)
        // Keys from different sources must never collide across tabs
        assertTrue(scheduleKey != exploreKey)
    }
}
