package com.infinitezerone.minibgm.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 纯 JVM 单测：直接构造 BgmNavState（NavBackStack 底层为 SnapshotStateList，无需 Compose 场景），
 * 覆盖顶层 Tab 历史、子返回栈、single-top 与 exit-through-home 语义
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BgmNavStateTest {
    private fun newState(): BgmNavState =
        BgmNavState(
            startRoute = ScheduleRoute,
            topLevelStack = NavBackStack<NavKey>(ScheduleRoute),
            subStacks =
                setOf(ScheduleRoute, ExploreRoute, UserRoute).associateWith { key ->
                    NavBackStack<NavKey>(key)
                },
        )

    @Test
    fun navigateToDetail_pushesOntoCurrentSubStack() {
        val state = newState()

        state.navigateTo(SubjectDetailRoute(subjectId = 1L))

        assertEquals(SubjectDetailRoute(1L), state.currentKey)
    }

    @Test
    fun navigateToSameDetailTwice_isSingleTop() {
        val state = newState()
        state.navigateTo(SubjectDetailRoute(1L))

        state.navigateTo(SubjectDetailRoute(1L))

        assertEquals(
            listOf<NavKey>(ScheduleRoute, SubjectDetailRoute(1L)),
            state.currentSubStack.toList(),
        )
    }

    @Test
    fun navigateToDifferentDetail_replacesExistingDetailInsteadOfStacking() {
        val state = newState()
        state.navigateTo(SubjectDetailRoute(1L))
        state.navigateTo(SubjectDetailRoute(2L))

        assertEquals(
            listOf<NavKey>(ScheduleRoute, SubjectDetailRoute(2L)),
            state.currentSubStack.toList(),
        )

        state.goBack()

        assertEquals(ScheduleRoute, state.currentKey)
        assertEquals(listOf<NavKey>(ScheduleRoute), state.currentSubStack.toList())
    }

    @Test
    fun navigateToDetail_replacesExistingLinkedSubjectAndEpisodeDetail() {
        val state = newState()
        state.navigateTo(SubjectDetailRoute(1L))
        state.navigateTo(LinkedSubjectRoute(10L))
        state.navigateTo(EpisodeDetailRoute(episodeId = 100L, subjectId = 10L))

        state.navigateTo(SubjectDetailRoute(2L))

        assertEquals(
            listOf<NavKey>(ScheduleRoute, SubjectDetailRoute(2L)),
            state.currentSubStack.toList(),
        )
    }

    @Test
    fun navigateToSearchRoute_clearsExistingDetail() {
        val state = newState()
        state.navigateTo(SubjectDetailRoute(1L))
        state.navigateTo(SearchRoute())

        assertEquals(
            listOf<NavKey>(ScheduleRoute, SearchRoute()),
            state.currentSubStack.toList(),
        )
    }

    @Test
    fun navigateToOtherTab_recordsTopLevelHistory() {
        val state = newState()

        state.navigateTo(ExploreRoute)

        assertEquals(ExploreRoute, state.currentKey)
        assertEquals(listOf<NavKey>(ScheduleRoute, ExploreRoute), state.topLevelStack.toList())
    }

    @Test
    fun reselectCurrentTab_resetsItsSubStackToRoot() {
        val state = newState()
        state.navigateTo(SubjectDetailRoute(1L))

        state.navigateTo(ScheduleRoute)

        assertEquals(ScheduleRoute, state.currentKey)
        assertEquals(listOf<NavKey>(ScheduleRoute), state.currentSubStack.toList())
    }

    @Test
    fun reselectCurrentTabAtRoot_emitsReselectionEvent() =
        runTest {
            val state = newState()
            var eventReceived: NavKey? = null
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                state.tabReselectionEvents.collect { eventReceived = it }
            }

            state.navigateTo(ScheduleRoute)

            assertEquals(ScheduleRoute, eventReceived)
        }

    @Test
    fun scrollToTopFor_emitsSignalOnMatchingTabReselection() =
        runTest {
            val state = newState()
            var triggered = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                state.scrollToTopFor(ScheduleRoute).collect { triggered = true }
            }

            state.navigateTo(ScheduleRoute)

            assertEquals(true, triggered)
        }

    @Test
    fun goBackFromDetail_popsDetail() {
        val state = newState()
        state.navigateTo(SubjectDetailRoute(1L))

        state.goBack()

        assertEquals(ScheduleRoute, state.currentKey)
    }

    @Test
    fun goBackAtTabBase_returnsToPreviousTabViaHistory() {
        val state = newState()
        state.navigateTo(ExploreRoute)

        state.goBack()

        assertEquals(ScheduleRoute, state.currentKey)
        assertEquals(listOf<NavKey>(ScheduleRoute), state.topLevelStack.toList())
    }

    @Test
    fun navigateBackToStart_clearsTopLevelHistory() {
        val state = newState()
        state.navigateTo(ExploreRoute)

        state.navigateTo(ScheduleRoute)

        assertEquals(listOf<NavKey>(ScheduleRoute), state.topLevelStack.toList())
        assertEquals(ScheduleRoute, state.currentTopLevelKey)
    }

    @Test
    fun goBackAtStartBase_throws_startRouteIsTheAppExit() {
        val state = newState()

        assertThrows(IllegalStateException::class.java) { state.goBack() }
    }
}
