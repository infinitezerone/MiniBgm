package com.infinitezerone.minibgm.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.infinitezerone.minibgm.core.navigation.BgmNavState
import com.infinitezerone.minibgm.core.navigation.ExploreRoute
import com.infinitezerone.minibgm.core.navigation.LocalSharedTransitionScope
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

@OptIn(ExperimentalSharedTransitionApi::class)
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

    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
        ) {
            NavDisplay(
                sharedTransitionScope = this,
                entries =
                    navState.toEntries(
                        entryProvider {
                            scheduleEntry(
                                onSubjectClick = { route ->
                                    navState.navigateTo(route)
                                },
                                onSearchClick = {
                                    navState.navigateTo(SearchRoute())
                                },
                                scrollToTop = scheduleScrollToTop,
                            )

                            userEntry(
                                onSubjectClick = { route ->
                                    navState.navigateTo(route)
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
                                onSubjectClick = { route ->
                                    navState.navigateTo(route)
                                },
                                onBackClick = { navState.goBack() },
                            )

                            exploreEntry(
                                onSubjectClick = { route ->
                                    navState.navigateTo(route)
                                },
                                onSearchClick = {
                                    navState.navigateTo(SearchRoute())
                                },
                                scrollToTop = exploreScrollToTop,
                            )
                        },
                    ),
                onBack = { navState.goBack() },
                transitionSpec = {
                    val fromKey = initialState.key
                    val toKey = targetState.key
                    if (isTopLevelRoute(fromKey) && isTopLevelRoute(toKey)) {
                        // 底栏顶层 Tab 间平滑淡入淡出，避免横向滑动导致视觉干扰
                        fadeIn(
                            animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
                        ) togetherWith
                            fadeOut(
                                animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing),
                            )
                    } else {
                        // 推进二级页面：新页面从右侧滑入伴随轻微淡入，旧页面轻微向左视差退避并淡出
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        ) +
                            fadeIn(
                                animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing),
                            ) togetherWith
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -fullWidth / 4 },
                                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            ) +
                            fadeOut(
                                animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing),
                            )
                    }
                },
                popTransitionSpec = {
                    val fromKey = initialState.key
                    val toKey = targetState.key
                    if (isTopLevelRoute(fromKey) && isTopLevelRoute(toKey)) {
                        fadeIn(
                            animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
                        ) togetherWith
                            fadeOut(
                                animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing),
                            )
                    } else {
                        // 返回上一级：上级页面从左侧视差位滑回并淡入，当前页面向右滑出并淡出
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> -fullWidth / 4 },
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        ) +
                            fadeIn(
                                animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing),
                            ) togetherWith
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> fullWidth },
                                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            ) +
                            fadeOut(
                                animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing),
                            )
                    }
                },
                predictivePopTransitionSpec = { _ ->
                    // 预测性返回手势：跟随手势拖拽自然滑出
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth / 4 },
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    ) +
                        fadeIn(
                            animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing),
                        ) togetherWith
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        ) +
                        fadeOut(
                            animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing),
                        )
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun isTopLevelRoute(contentKey: Any?): Boolean {
    var route = contentKey
    while (route is Pair<*, *>) {
        route = route.second
    }
    return route is ScheduleRoute || route is ExploreRoute || route is UserRoute
}
