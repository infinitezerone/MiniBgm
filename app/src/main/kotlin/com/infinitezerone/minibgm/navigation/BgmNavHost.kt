package com.infinitezerone.minibgm.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.infinitezerone.minibgm.core.navigation.BgmNavState
import com.infinitezerone.minibgm.core.navigation.ExploreRoute
import com.infinitezerone.minibgm.core.navigation.LinkedSubjectRoute
import com.infinitezerone.minibgm.core.navigation.LocalSharedTransitionScope
import com.infinitezerone.minibgm.core.navigation.ScheduleRoute
import com.infinitezerone.minibgm.core.navigation.SearchRoute
import com.infinitezerone.minibgm.core.navigation.TagSubjectsRoute
import com.infinitezerone.minibgm.core.navigation.UserCollectionsRoute
import com.infinitezerone.minibgm.core.navigation.UserRoute
import com.infinitezerone.minibgm.feature.schedule.navigation.scheduleEntry
import com.infinitezerone.minibgm.feature.search.navigation.exploreEntry
import com.infinitezerone.minibgm.feature.search.navigation.searchEntry
import com.infinitezerone.minibgm.feature.search.navigation.tagSubjectsEntry
import com.infinitezerone.minibgm.feature.subject.navigation.episodeDetailEntry
import com.infinitezerone.minibgm.feature.subject.navigation.linkedSubjectEntry
import com.infinitezerone.minibgm.feature.subject.navigation.subjectEntry
import com.infinitezerone.minibgm.feature.user.navigation.userCollectionsEntry
import com.infinitezerone.minibgm.feature.user.navigation.userEntry
import com.infinitezerone.minibgm.ui.component.BgmDetailPlaceholder

/**
 * MiniBgm 应用根导航组件。
 *
 * 聚合各 Feature Entry 声明，整合 Navigation 3 自适应分栏策略与共享转场动效。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun BgmNavHost(
    navState: BgmNavState,
    modifier: Modifier = Modifier,
) {
    val scheduleScrollToTop = remember(navState) { navState.scrollToTopFor(ScheduleRoute) }
    val exploreScrollToTop = remember(navState) { navState.scrollToTopFor(ExploreRoute) }
    val userScrollToTop = remember(navState) { navState.scrollToTopFor(UserRoute) }

    val directive = rememberBgmPaneDirective()
    val isSplitMode = directive.isSplitLayout
    val listDetailStrategy = rememberBgmListDetailStrategy(directive = directive)

    val detailPlaceholder: @Composable ThreePaneScaffoldScope.() -> Unit = {
        if (navState.currentKey is SearchRoute) {
            BgmDetailPlaceholder(
                icon = Icons.Outlined.Search,
                title = "搜索并查看作品详情",
                subtitle = "在左侧输入关键词或轻点历史记录\n选择任意条目即可在此处即时展开海报与讨论",
            )
        } else {
            BgmDetailPlaceholder(
                icon = Icons.Outlined.Tv,
                title = "选择作品查看详情",
                subtitle = "在左侧列表中轻点任意条目\n右侧将原地展示专属海报、进度与社区吐槽",
            )
        }
    }

    SharedTransitionLayout(modifier = modifier) {
        val sharedScope = if (isSplitMode) null else this
        CompositionLocalProvider(LocalSharedTransitionScope provides sharedScope) {
            NavDisplay(
                sharedTransitionScope = sharedScope,
                entries =
                    navState.toEntries(
                        entryProvider {
                            scheduleEntry(
                                onSubjectClick = { route -> navState.navigateTo(route) },
                                onSearchClick = { navState.navigateTo(SearchRoute()) },
                                scrollToTop = scheduleScrollToTop,
                                metadata = bgmListPane(detailPlaceholder),
                            )

                            exploreEntry(
                                onSubjectClick = { route -> navState.navigateTo(route) },
                                onSearchClick = { navState.navigateTo(SearchRoute()) },
                                scrollToTop = exploreScrollToTop,
                                metadata = bgmListPane(detailPlaceholder),
                            )

                            searchEntry(
                                onSubjectClick = { route -> navState.navigateTo(route) },
                                onBackClick = { navState.goBack() },
                                metadata = bgmListPane(detailPlaceholder),
                            )

                            userEntry(
                                onCollectionClick = { type ->
                                    navState.navigateTo(UserCollectionsRoute(type.value))
                                },
                                scrollToTop = userScrollToTop,
                            )

                            userCollectionsEntry(
                                onSubjectClick = { route -> navState.navigateTo(route) },
                                onBackClick = { navState.goBack() },
                                metadata = bgmListPane(detailPlaceholder),
                            )

                            subjectEntry(
                                onBackClick = { navState.goBack() },
                                onSubjectClick = { subjectId ->
                                    navState.navigateTo(LinkedSubjectRoute(subjectId))
                                },
                                onEpisodeClick = { route -> navState.navigateTo(route) },
                                onTagClick = { tag ->
                                    navState.navigateTo(TagSubjectsRoute(tag = tag))
                                },
                                metadata = bgmDetailPane(),
                            )

                            linkedSubjectEntry(
                                onBackClick = { navState.goBack() },
                                onSubjectClick = { subjectId ->
                                    navState.navigateTo(LinkedSubjectRoute(subjectId))
                                },
                                onEpisodeClick = { route -> navState.navigateTo(route) },
                                onTagClick = { tag ->
                                    navState.navigateTo(TagSubjectsRoute(tag = tag))
                                },
                                metadata = bgmExtraPane(),
                            )

                            tagSubjectsEntry(
                                onBackClick = { navState.goBack() },
                                onSubjectClick = { subjectId ->
                                    navState.navigateTo(LinkedSubjectRoute(subjectId))
                                },
                                metadata = bgmExtraPane(),
                            )

                            episodeDetailEntry(
                                onBackClick = { navState.goBack() },
                                onSubjectClick = { subjectId ->
                                    navState.navigateTo(LinkedSubjectRoute(subjectId))
                                },
                                onEpisodeClick = { route -> navState.navigateTo(route) },
                                metadata = bgmExtraPane(),
                            )
                        },
                    ),
                onBack = { navState.goBack() },
                sceneStrategies = listOf(listDetailStrategy),
                transitionSpec = bgmNavTransitionSpec,
                popTransitionSpec = bgmNavPopTransitionSpec,
                predictivePopTransitionSpec = bgmNavPredictivePopTransitionSpec,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
