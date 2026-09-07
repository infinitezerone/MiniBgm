package com.infinitezerone.minibgm.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.infinitezerone.minibgm.core.navigation.BgmNavState
import com.infinitezerone.minibgm.core.navigation.ExploreRoute
import com.infinitezerone.minibgm.core.navigation.ScheduleRoute
import com.infinitezerone.minibgm.core.navigation.SearchRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.UserCollectionsRoute
import com.infinitezerone.minibgm.core.navigation.UserRoute
import com.infinitezerone.minibgm.feature.schedule.navigation.scheduleEntry
import com.infinitezerone.minibgm.feature.search.navigation.exploreEntry
import com.infinitezerone.minibgm.feature.search.navigation.searchEntry
import com.infinitezerone.minibgm.feature.subject.navigation.subjectEntry
import com.infinitezerone.minibgm.feature.user.navigation.userEntry
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun BgmNavHost(
    navState: BgmNavState,
    modifier: Modifier = Modifier,
) {
    val scheduleScrollToTop =
        remember(navState) {
            navState.tabReselectionEvents.filter { it == ScheduleRoute }.map { }
        }
    val exploreScrollToTop =
        remember(navState) {
            navState.tabReselectionEvents.filter { it == ExploreRoute }.map { }
        }
    val userScrollToTop =
        remember(navState) {
            navState.tabReselectionEvents.filter { it == UserRoute }.map { }
        }

    NavDisplay(
        entries =
            navState.toEntries(
                entryProvider {
                    scheduleEntry(
                        onSubjectClick = { subjectId ->
                            navState.navigateTo(SubjectDetailRoute(subjectId))
                        },
                        onSearchClick = {
                            navState.navigateTo(SearchRoute())
                        },
                        scrollToTop = scheduleScrollToTop,
                    )

                    userEntry(
                        onSubjectClick = { subjectId ->
                            navState.navigateTo(SubjectDetailRoute(subjectId))
                        },
                        onCollectionClick = { type ->
                            navState.navigateTo(UserCollectionsRoute(type.value))
                        },
                        onBackClick = { navState.goBack() },
                        scrollToTop = userScrollToTop,
                    )

                    subjectEntry(
                        onBackClick = { navState.goBack() },
                        onSubjectClick = { subjectId ->
                            navState.navigateTo(SubjectDetailRoute(subjectId))
                        },
                        onTagClick = { tag ->
                            navState.navigateTo(SearchRoute(initialQuery = tag))
                        },
                    )

                    searchEntry(
                        onSubjectClick = {
                            navState.navigateTo(SubjectDetailRoute(it))
                        },
                        onBackClick = { navState.goBack() },
                    )

                    exploreEntry(
                        onSubjectClick = { subjectId ->
                            navState.navigateTo(SubjectDetailRoute(subjectId))
                        },
                        onSearchClick = {
                            navState.navigateTo(SearchRoute())
                        },
                        scrollToTop = exploreScrollToTop,
                    )
                },
            ),
        onBack = { navState.goBack() },
        modifier = modifier,
    )
}
