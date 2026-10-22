package com.infinitezerone.minibgm.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import com.infinitezerone.minibgm.BuildConfig
import com.infinitezerone.minibgm.R
import com.infinitezerone.minibgm.core.data.repository.PlaybackHistoryRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.designsystem.theme.LocalFeatureCapabilities
import com.infinitezerone.minibgm.core.model.FeatureCapabilities
import com.infinitezerone.minibgm.core.model.PlaybackHistory
import com.infinitezerone.minibgm.core.navigation.AssistantRoute
import com.infinitezerone.minibgm.core.navigation.BgmNavState
import com.infinitezerone.minibgm.core.navigation.DownloadRoute
import com.infinitezerone.minibgm.core.navigation.EpisodeDetailRoute
import com.infinitezerone.minibgm.core.navigation.ExploreRoute
import com.infinitezerone.minibgm.core.navigation.HistoryRoute
import com.infinitezerone.minibgm.core.navigation.InAppLoginRoute
import com.infinitezerone.minibgm.core.navigation.InAppWebRoute
import com.infinitezerone.minibgm.core.navigation.LinkedSubjectRoute
import com.infinitezerone.minibgm.core.navigation.LocalSharedTransitionScope
import com.infinitezerone.minibgm.core.navigation.PlaybackRulesRoute
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.ScheduleRoute
import com.infinitezerone.minibgm.core.navigation.SearchRoute
import com.infinitezerone.minibgm.core.navigation.SeasonalGuideRoute
import com.infinitezerone.minibgm.core.navigation.SettingsRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDownloadDetailRoute
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
import com.infinitezerone.minibgm.feature.user.navigation.downloadEntry
import com.infinitezerone.minibgm.feature.user.navigation.historyEntry
import com.infinitezerone.minibgm.feature.user.navigation.inAppLoginEntry
import com.infinitezerone.minibgm.feature.user.navigation.inAppWebEntry
import com.infinitezerone.minibgm.feature.user.navigation.playbackRulesEntry
import com.infinitezerone.minibgm.feature.user.navigation.settingsEntry
import com.infinitezerone.minibgm.feature.user.navigation.subjectDownloadDetailEntry
import com.infinitezerone.minibgm.feature.user.navigation.userEntry
import com.infinitezerone.minibgm.ui.component.BgmScheduleOverview
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
    val settingsRepository = koinInject<SettingsRepository>()
    val playbackHistoryRepository = koinInject<PlaybackHistoryRepository>()
    val featureCapabilities by settingsRepository.featureCapabilities.collectAsStateWithLifecycle(
        initialValue =
            FeatureCapabilities(
                isAiAssistantEnabled = BuildConfig.ENABLE_AI_ASSISTANT,
                isInternalPlayerEnabled = BuildConfig.ENABLE_INTERNAL_PLAYER,
            ),
    )
    val isAiAssistantEnabled = featureCapabilities.isAiAssistantEnabled
    val isInternalPlayerEnabled = featureCapabilities.isInternalPlayerEnabled
    val recentHistory by
        playbackHistoryRepository
            .observeRecentHistory(limit = 3)
            .collectAsStateWithLifecycle(initialValue = emptyList())

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
        when (navState.currentKey) {
            is ScheduleRoute ->
                BgmScheduleOverview(
                    recentHistory = recentHistory,
                    onPlayHistory =
                        if (isInternalPlayerEnabled) {
                            { history -> navState.navigateTo(history.toPlayerRoute()) }
                        } else {
                            null
                        },
                    onOpenSubject = { history ->
                        navState.navigateTo(
                            SubjectDetailRoute(
                                subjectId = history.subjectId,
                                initialName = history.subjectName,
                                initialCoverUrl = history.coverUrl,
                                source = "schedule_overview",
                            ),
                        )
                    },
                    onOpenSeasonalGuide = { navState.navigateTo(SeasonalGuideRoute()) },
                    onOpenSearch = { navState.navigateTo(SearchRoute()) },
                    onOpenAssistant =
                        if (isAiAssistantEnabled) {
                            { navState.navigateTo(AssistantRoute()) }
                        } else {
                            null
                        },
                )

            else -> Unit
        }
    }

    SharedTransitionLayout(modifier = modifier) {
        val sharedScope = if (isSplitMode) null else this
        CompositionLocalProvider(
            LocalSharedTransitionScope provides sharedScope,
            LocalFeatureCapabilities provides featureCapabilities,
        ) {
            NavDisplay(
                sharedTransitionScope = sharedScope,
                entries =
                    navState.toEntries(
                        entryProvider {
                            scheduleEntry(
                                onSubjectClick = { route -> navState.navigateTo(route) },
                                onSearchClick = { navState.navigateTo(SearchRoute()) },
                                onAssistantClick =
                                    if (isAiAssistantEnabled) {
                                        { navState.navigateTo(AssistantRoute()) }
                                    } else {
                                        null
                                    },
                                onSourceSearch =
                                    if (isAiAssistantEnabled) {
                                        { prompt -> navState.navigateTo(AssistantRoute(prefillPrompt = prompt)) }
                                    } else {
                                        null
                                    },
                                onPlayClick =
                                    if (isInternalPlayerEnabled) {
                                        { route -> navState.navigateTo(route) }
                                    } else {
                                        null
                                    },
                                onLoginRequest = openLogin,
                                scrollToTop = scheduleScrollToTop,
                                metadata = bgmTopLevelPaneMetadata(ScheduleRoute, detailPlaceholder) + bgmTopLevelTransitionMetadata,
                            )

                            if (isAiAssistantEnabled) {
                                assistantEntry(
                                    onSubjectClick = { route -> navState.navigateTo(route) },
                                    onPlaySource =
                                        if (isInternalPlayerEnabled) {
                                            { route -> navState.navigateTo(route) }
                                        } else {
                                            { _ -> }
                                        },
                                    onBackClick = { navState.goBack() },
                                )
                            }

                            exploreEntry(
                                onSubjectClick = { route -> navState.navigateTo(route) },
                                onSearchClick = { navState.navigateTo(SearchRoute()) },
                                onLoginRequest = openLogin,
                                onOpenSeasonalGuide = { navState.navigateTo(SeasonalGuideRoute()) },
                                scrollToTop = exploreScrollToTop,
                                metadata = bgmTopLevelPaneMetadata(ExploreRoute, detailPlaceholder) + bgmTopLevelTransitionMetadata,
                            )

                            seasonalGuideEntry(
                                onSubjectClick = { route -> navState.navigateTo(route) },
                                onBackClick = { navState.goBack() },
                                onLoginRequest = openLogin,
                                metadata = emptyMap(),
                            )

                            searchEntry(
                                onSubjectClick = { route -> navState.navigateTo(route) },
                                onBackClick = { navState.goBack() },
                                onLoginRequest = openLogin,
                                metadata = emptyMap(),
                            )

                            userEntry(
                                onSubjectClick = { route -> navState.navigateTo(route) },
                                onSettingsClick = { navState.navigateTo(SettingsRoute) },
                                onDownloadsClick = { navState.navigateTo(DownloadRoute) },
                                onHistoryClick = { navState.navigateTo(HistoryRoute) },
                                onLoginRequest = openLogin,
                                onOpenTokenPage = openTokenPage,
                                scrollToTop = userScrollToTop,
                                metadata = bgmTopLevelPaneMetadata(UserRoute, detailPlaceholder) + bgmTopLevelTransitionMetadata,
                            )

                            downloadEntry(
                                onBackClick = { navState.goBack() },
                                onSubjectClick = { subjectId, subjectName ->
                                    navState.navigateTo(SubjectDownloadDetailRoute(subjectId = subjectId, subjectName = subjectName))
                                },
                                metadata = bgmDetailPane(),
                            )

                            if (isInternalPlayerEnabled) {
                                historyEntry(
                                    onBackClick = { navState.goBack() },
                                    onPlayHistory = { route -> navState.navigateTo(route) },
                                    onSubjectClick = { route -> navState.navigateTo(route) },
                                    metadata = bgmDetailPane(),
                                )
                            }

                            subjectDownloadDetailEntry(
                                onBackClick = { navState.goBack() },
                                onPlayEpisode =
                                    if (isInternalPlayerEnabled) {
                                        { route -> navState.navigateTo(route) }
                                    } else {
                                        null
                                    },
                                metadata = bgmDetailPane(),
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
                                    if (isInternalPlayerEnabled) {
                                        { navState.navigateTo(PlaybackRulesRoute) }
                                    } else {
                                        null
                                    },
                                onDownloadsClick = { navState.navigateTo(DownloadRoute) },
                                enableAiConfig = isAiAssistantEnabled,
                                onClearCache = {
                                    withContext(Dispatchers.IO) {
                                        imageLoader.memoryCache?.clear()
                                        imageLoader.diskCache?.clear()
                                    }
                                },
                            )

                            if (isInternalPlayerEnabled) {
                                playbackRulesEntry(
                                    onBackClick = { navState.goBack() },
                                    onAiSourceSearch =
                                        if (isAiAssistantEnabled) {
                                            { prompt -> navState.navigateTo(AssistantRoute(prefillPrompt = prompt)) }
                                        } else {
                                            null
                                        },
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
                                    if (isInternalPlayerEnabled) {
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
                                    if (isInternalPlayerEnabled) {
                                        { navState.navigateTo(PlaybackRulesRoute) }
                                    } else {
                                        null
                                    },
                                onSourceSearch =
                                    if (isAiAssistantEnabled) {
                                        { prompt -> navState.navigateTo(AssistantRoute(prefillPrompt = prompt)) }
                                    } else {
                                        null
                                    },
                                onDownloadsClick = { navState.navigateTo(DownloadRoute) },
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
                                    if (isInternalPlayerEnabled) {
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
                                    if (isInternalPlayerEnabled) {
                                        { navState.navigateTo(PlaybackRulesRoute) }
                                    } else {
                                        null
                                    },
                                onSourceSearch =
                                    if (isAiAssistantEnabled) {
                                        { prompt -> navState.navigateTo(AssistantRoute(prefillPrompt = prompt)) }
                                    } else {
                                        null
                                    },
                                onDownloadsClick = { navState.navigateTo(DownloadRoute) },
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
                                    if (isInternalPlayerEnabled) {
                                        { route -> navState.navigateTo(route) }
                                    } else {
                                        null
                                    },
                                onSourceSearch =
                                    if (isAiAssistantEnabled) {
                                        { prompt -> navState.navigateTo(AssistantRoute(prefillPrompt = prompt)) }
                                    } else {
                                        null
                                    },
                                onManageRules =
                                    if (isInternalPlayerEnabled) {
                                        { navState.navigateTo(PlaybackRulesRoute) }
                                    } else {
                                        null
                                    },
                                onDownloadsClick = { navState.navigateTo(DownloadRoute) },
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

                            if (isInternalPlayerEnabled) {
                                playerEntry(
                                    onBackClick = { navState.goBack() },
                                    onSubjectClick = { subjectId ->
                                        navState.navigateTo(SubjectDetailRoute(subjectId))
                                    },
                                    onEpisodeDetailClick = { subjectId, episodeId ->
                                        navState.navigateTo(EpisodeDetailRoute(subjectId = subjectId, episodeId = episodeId))
                                    },
                                    onRequestOpenSources =
                                        if (isAiAssistantEnabled) {
                                            { route ->
                                                val title =
                                                    route.subjectName.ifBlank {
                                                        context.getString(
                                                            R.string.app_subject_fallback_title,
                                                        )
                                                    }
                                                navState.navigateTo(
                                                    AssistantRoute(
                                                        prefillPrompt =
                                                            context.getString(R.string.app_ai_search_prompt, title, route.subjectId),
                                                    ),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    onManageRules = { navState.navigateTo(PlaybackRulesRoute) },
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

private fun PlaybackHistory.toPlayerRoute(): PlayerRoute =
    PlayerRoute(
        subjectId = subjectId,
        episodeId = episodeId,
        streamUrl = streamUrl,
        episodeName = episodeName,
        subjectName = subjectName,
        episodeSort = episodeSort,
        initialRuleId = sourceId,
        initialSourceName = sourceName,
        coverUrl = coverUrl,
    )
