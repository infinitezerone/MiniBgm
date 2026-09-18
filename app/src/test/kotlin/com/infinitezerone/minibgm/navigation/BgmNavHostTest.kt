package com.infinitezerone.minibgm.navigation

import androidx.navigation3.runtime.get
import androidx.navigation3.ui.NavDisplay
import com.infinitezerone.minibgm.core.navigation.AssistantRoute
import com.infinitezerone.minibgm.core.navigation.EpisodeDetailRoute
import com.infinitezerone.minibgm.core.navigation.ExploreRoute
import com.infinitezerone.minibgm.core.navigation.LinkedSubjectRoute
import com.infinitezerone.minibgm.core.navigation.ScheduleRoute
import com.infinitezerone.minibgm.core.navigation.SearchRoute
import com.infinitezerone.minibgm.core.navigation.SeasonalGuideRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.TagSubjectsRoute
import com.infinitezerone.minibgm.core.navigation.TopicDetailRoute
import com.infinitezerone.minibgm.core.navigation.UserCollectionsRoute
import com.infinitezerone.minibgm.core.navigation.UserRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun isTopLevelRoute_returnsFalse_forSecondaryAndDetailRoutes() {
        assertFalse(isTopLevelRoute(SubjectDetailRoute(1001L)))
        assertFalse(isTopLevelRoute(SearchRoute("test")))
        assertFalse(isTopLevelRoute(UserCollectionsRoute(3)))
        assertFalse(isTopLevelRoute(AssistantRoute))
        assertFalse(isTopLevelRoute(SeasonalGuideRoute()))
        assertFalse(isTopLevelRoute(LinkedSubjectRoute(1002L)))
        assertFalse(isTopLevelRoute(EpisodeDetailRoute(1L, 1001L)))
        assertFalse(isTopLevelRoute(TagSubjectsRoute("anime")))
        assertFalse(isTopLevelRoute(TopicDetailRoute(100L)))
    }

    @Test
    fun isTopLevelRoute_returnsFalse_forNullOrArbitraryObjects() {
        assertFalse(isTopLevelRoute(null))
        assertFalse(isTopLevelRoute("ScheduleRoute"))
        assertFalse(isTopLevelRoute("ExploreRoute"))
        assertFalse(isTopLevelRoute("UserRoute"))
        assertFalse(isTopLevelRoute(12345))
        assertFalse(isTopLevelRoute(Pair("decorator_scope", ScheduleRoute)))
    }

    @Test
    fun bgmTopLevelTransitionMetadata_containsAllNavigation3TransitionKeys() {
        assertNotNull(bgmTopLevelTransitionMetadata.get(NavDisplay.TransitionKey))
        assertNotNull(bgmTopLevelTransitionMetadata.get(NavDisplay.PopTransitionKey))
        assertNotNull(bgmTopLevelTransitionMetadata.get(NavDisplay.PredictivePopTransitionKey))
        assertNotNull(bgmTopLevelTransitionMetadata[NavDisplay.TransitionKey.toString()])
        assertNotNull(bgmTopLevelTransitionMetadata[NavDisplay.PopTransitionKey.toString()])
        assertNotNull(bgmTopLevelTransitionMetadata[NavDisplay.PredictivePopTransitionKey.toString()])
    }
}
