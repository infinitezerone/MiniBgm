package com.infinitezerone.minibgm.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import com.infinitezerone.minibgm.BuildConfig
import com.infinitezerone.minibgm.R
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.navigation.AssistantRoute
import com.infinitezerone.minibgm.core.navigation.BgmNavState
import com.infinitezerone.minibgm.core.navigation.EpisodeDetailRoute
import com.infinitezerone.minibgm.core.navigation.ExploreRoute
import com.infinitezerone.minibgm.core.navigation.InAppLoginRoute
import com.infinitezerone.minibgm.core.navigation.InAppWebRoute
import com.infinitezerone.minibgm.core.navigation.LinkedSubjectRoute
import com.infinitezerone.minibgm.core.navigation.LocalSharedTransitionScope
import com.infinitezerone.minibgm.core.navigation.PlaybackRulesRoute
import com.infinitezerone.minibgm.core.navigation.ScheduleRoute
import com.infinitezerone.minibgm.core.navigation.SearchRoute
import com.infinitezerone.minibgm.core.navigation.SeasonalGuideRoute
import com.infinitezerone.minibgm.core.navigation.SettingsRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.TagSubjectsRoute
import com.infinitezerone.minibgm.core.navigation.TopicDetailRoute
import com.infinitezerone.minibgm.core.navigation.UserRoute
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import com.infinitezerone.minibgm.feature.assistant.navigation.assistantEntry
import com.infinitezerone.minibgm.feature.schedule.navigation.scheduleEntry
import com.infinitezerone.minibgm.feature.search.navigation.exploreEntry
import com.infinitezerone.minibgm.feature.search.navigation.searchEntry
import com.infinitezerone.minibgm.feature.search.navigation.seasonalGuideEntry
import com.infinitezerone.minibgm.feature.search.navigation.tagSubjectsEntry
import com.infinitezerone.minibgm.feature.subject.navigation.episodeDetailEntry
import com.infinitezerone.minibgm.feature.subject.navigation.linkedSubjectEntry
import com.infinitezerone.minibgm.feature.subject.navigation.playerEntry
import com.infinitezerone.minibgm.feature.subject.navigation.subjectEntry
import com.infinitezerone.minibgm.feature.subject.navigation.topicDetailEntry
import com.infinitezerone.minibgm.feature.user.navigation.inAppLoginEntry
import com.infinitezerone.minibgm.feature.user.navigation.inAppWebEntry
import com.infinitezerone.minibgm.feature.user.navigation.playbackRulesEntry
import com.infinitezerone.minibgm.feature.user.navigation.settingsEntry
import com.infinitezerone.minibgm.feature.user.navigation.userEntry
import com.infinitezerone.minibgm.ui.component.BgmDetailPlaceholder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

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
    val imageLoader = koinInject<ImageLoader>()

    val directive = rememberBgmPaneDirective()
    val isSplitMode = directive.isSplitLayout
    val listDetailStrategy = rememberBgmListDetailStrategy(directive = directive)

    val context = LocalContext.current
    // 登录与 bgm 网页统一走应用内接管页：环回代理 + 原生 ECH 通道，
    // Custom Tabs 用的是系统网络栈，用不了 ECH（强阻断网络下打不开）
    val openLogin: () -> Unit = { navState.navigateTo(InAppLoginRoute) }
    val openTokenPage: (String) -> Unit = { url ->
        navState.navigateTo(InAppWebRoute(url = url, title = context.getString(R.string.app_in_app_web_title_token)))
    }
    val openInAppWebInBrowser: (String) -> Unit = { url -> context.launchWebUrl(url) }
    val openLoginInBrowser: (String) -> Unit = { url -> context.launchWebUrl(url, isAuth = true) }

    val detailPlaceholder: @Composable ThreePaneScaffoldScope.() -> Unit = {
        if (navState.currentKey is SearchRoute) {
            BgmDetailPlaceholder(
                icon = BgmIcons.SearchBorder,
                title = stringResource(R.string.app_detail_placeholder_title_search),
                subtitle = stringResource(R.string.app_detail_placeholder_subtitle_search),
            )
        } else {
            BgmDetailPlaceholder(
                icon = BgmIcons.TvBorder,
                title = stringResource(R.string.app_detail_placeholder_title_default),
                subtitle = stringResource(R.string.app_detail_placeholder_subtitle_default),
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
                                onAssistantClick =
                                    if (BuildConfig.ENABLE_AI_ASSISTANT) {
                                        { navState.navigateTo(AssistantRoute()) }
                                    } else {
                                        null
                                    },
                                onSourceSearch = { prompt -> navState.navigateTo(AssistantRoute(prefillPrompt = prompt)) },
                                onPlayClick =
                                    if (BuildConfig.ENABLE_INTERNAL_PLAYER) {
                                        { route -> navState.navigateTo(route) }
                                    } else {
                                        null
                                    },
                                onLoginRequest = openLogin,
                                scrollToTop = scheduleScrollToTop,
                                metadata = bgmListPane(detailPlaceholder) + bgmTopLevelTransitionMetadata,
                            )

                            if (BuildConfig.ENABLE_AI_ASSISTANT) {
                                assistantEntry(
                                    onSubjectClick = { route -> navState.navigateTo(route) },
                                    onPlaySource =
                                        if (BuildConfig.ENABLE_INTERNAL_PLAYER) {
                                            { route -> navState.navigateTo(route) }
                                        } else {
                                            { _ -> }
                                        },
                                    onBackClick = { navState.goBack() },
                                    metadata = bgmListPane(detailPlaceholder),
                                )
                            }

                            exploreEntry(
                                onSubjectClick = { route -> navState.navigateTo(route) },
                                onSearchClick = { navState.navigateTo(SearchRoute()) },
                                onLoginRequest = openLogin,
                                onOpenSeasonalGuide = { navState.navigateTo(SeasonalGuideRoute()) },
                                scrollToTop = exploreScrollToTop,
                                metadata = bgmListPane(detailPlaceholder) + bgmTopLevelTransitionMetadata,
                            )

                            seasonalGuideEntry(
                                onSubjectClick = { route -> navState.navigateTo(route) },
                                onBackClick = { navState.goBack() },
                                onLoginRequest = openLogin,
                                metadata = bgmListPane(detailPlaceholder),
                            )

                            searchEntry(
                                onSubjectClick = { route -> navState.navigateTo(route) },
                                onBackClick = { navState.goBack() },
                                onLoginRequest = openLogin,
                                metadata = bgmListPane(detailPlaceholder),
                            )

                            userEntry(
                                onSubjectClick = { route -> navState.navigateTo(route) },
                                onSettingsClick = { navState.navigateTo(SettingsRoute) },
                                onLoginRequest = openLogin,
                                onOpenTokenPage = openTokenPage,
                                scrollToTop = userScrollToTop,
                                metadata = bgmListPane(detailPlaceholder) + bgmTopLevelTransitionMetadata,
                            )

                            // 登录接管页与 bgm 网页浏览页：全屏场景（不参与分栏策略）
                            inAppLoginEntry(
                                onBackClick = { navState.goBack() },
                                onOpenInBrowser = openLoginInBrowser,
                            )

                            inAppWebEntry(
                                onBackClick = { navState.goBack() },
                                onOpenInBrowser = openInAppWebInBrowser,
                            )

                            settingsEntry(
                                onBackClick = { navState.goBack() },
                                onPlaybackRulesClick =
                                    if (BuildConfig.ENABLE_INTERNAL_PLAYER) {
                                        { navState.navigateTo(PlaybackRulesRoute) }
                                    } else {
                                        null
                                    },
                                enableAiConfig = BuildConfig.ENABLE_AI_ASSISTANT,
                                onClearCache = {
                                    withContext(Dispatchers.IO) {
                                        imageLoader.memoryCache?.clear()
                                        imageLoader.diskCache?.clear()
                                    }
                                },
                                metadata = bgmListPane(detailPlaceholder),
                            )

                            if (BuildConfig.ENABLE_INTERNAL_PLAYER) {
                                playbackRulesEntry(
                                    onBackClick = { navState.goBack() },
                                    onAiSourceSearch = { prompt -> navState.navigateTo(AssistantRoute(prefillPrompt = prompt)) },
                                    metadata = bgmListPane(detailPlaceholder),
                                )
                            }

                            subjectEntry(
                                onBackClick = { navState.goBack() },
                                onLoginRequest = openLogin,
                                onSubjectClick = { subjectId ->
                                    navState.navigateTo(LinkedSubjectRoute(subjectId))
                                },
                                onEpisodeClick = { route -> navState.navigateTo(route) },
                                onPlayClick =
                                    if (BuildConfig.ENABLE_INTERNAL_PLAYER) {
                                        { route -> navState.navigateTo(route) }
                                    } else {
                                        null
                                    },
                                onTagClick = { tag ->
                                    navState.navigateTo(TagSubjectsRoute(tag = tag))
                                },
                                onTopicClick = { topicId, title ->
                                    navState.navigateTo(TopicDetailRoute(topicId = topicId, initialTitle = title))
                                },
                                onManageRules =
                                    if (BuildConfig.ENABLE_INTERNAL_PLAYER) {
                                        { navState.navigateTo(PlaybackRulesRoute) }
                                    } else {
                                        null
                                    },
                                onSourceSearch =
                                    if (BuildConfig.ENABLE_AI_ASSISTANT) {
                                        { prompt -> navState.navigateTo(AssistantRoute(prefillPrompt = prompt)) }
                                    } else {
                                        null
                                    },
                                metadata = bgmDetailPane(),
                            )

                            linkedSubjectEntry(
                                onBackClick = { navState.goBack() },
                                onLoginRequest = openLogin,
                                onSubjectClick = { subjectId ->
                                    navState.navigateTo(LinkedSubjectRoute(subjectId))
                                },
                                onEpisodeClick = { route -> navState.navigateTo(route) },
                                onPlayClick =
                                    if (BuildConfig.ENABLE_INTERNAL_PLAYER) {
                                        { route -> navState.navigateTo(route) }
                                    } else {
                                        null
                                    },
                                onTagClick = { tag ->
                                    navState.navigateTo(TagSubjectsRoute(tag = tag))
                                },
                                onTopicClick = { topicId, title ->
                                    navState.navigateTo(TopicDetailRoute(topicId = topicId, initialTitle = title))
                                },
                                onManageRules =
                                    if (BuildConfig.ENABLE_INTERNAL_PLAYER) {
                                        { navState.navigateTo(PlaybackRulesRoute) }
                                    } else {
                                        null
                                    },
                                onSourceSearch =
                                    if (BuildConfig.ENABLE_AI_ASSISTANT) {
                                        { prompt -> navState.navigateTo(AssistantRoute(prefillPrompt = prompt)) }
                                    } else {
                                        null
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
                                onLoginRequest = openLogin,
                                onSubjectClick = { subjectId ->
                                    navState.navigateTo(LinkedSubjectRoute(subjectId))
                                },
                                onEpisodeClick = { route -> navState.navigateTo(route) },
                                onTopicClick = { topicId, title ->
                                    navState.navigateTo(TopicDetailRoute(topicId = topicId, initialTitle = title))
                                },
                                onPlayClick =
                                    if (BuildConfig.ENABLE_INTERNAL_PLAYER) {
                                        { route -> navState.navigateTo(route) }
                                    } else {
                                        null
                                    },
                                onSourceSearch =
                                    if (BuildConfig.ENABLE_AI_ASSISTANT) {
                                        { prompt -> navState.navigateTo(AssistantRoute(prefillPrompt = prompt)) }
                                    } else {
                                        null
                                    },
                                onManageRules =
                                    if (BuildConfig.ENABLE_INTERNAL_PLAYER) {
                                        { navState.navigateTo(PlaybackRulesRoute) }
                                    } else {
                                        null
                                    },
                                metadata = bgmExtraPane(),
                            )

                            topicDetailEntry(
                                onBackClick = { navState.goBack() },
                                onSubjectClick = { subjectId ->
                                    navState.navigateTo(LinkedSubjectRoute(subjectId))
                                },
                                onTopicClick = { topicId, title ->
                                    navState.navigateTo(TopicDetailRoute(topicId = topicId, initialTitle = title))
                                },
                                metadata = bgmExtraPane(),
                            )

                            if (BuildConfig.ENABLE_INTERNAL_PLAYER) {
                                playerEntry(
                                    onBackClick = { navState.goBack() },
                                    onSubjectClick = { subjectId ->
                                        navState.navigateTo(SubjectDetailRoute(subjectId))
                                    },
                                    onEpisodeDetailClick = { subjectId, episodeId ->
                                        navState.navigateTo(EpisodeDetailRoute(subjectId = subjectId, episodeId = episodeId))
                                    },
                                    onRequestOpenSources = { route ->
                                        val title = route.subjectName.ifBlank { context.getString(R.string.app_subject_fallback_title) }
                                        navState.navigateTo(
                                            AssistantRoute(
                                                prefillPrompt =
                                                    context.getString(R.string.app_ai_search_prompt, title, route.subjectId),
                                            ),
                                        )
                                    },
                                    onManageRules = { navState.navigateTo(PlaybackRulesRoute) },
                                    metadata = bgmExtraPane(),
                                )
                            }
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
