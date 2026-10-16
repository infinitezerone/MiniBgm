package com.infinitezerone.minibgm.feature.subject.di

import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.feature.subject.EpisodeDetailViewModel
import com.infinitezerone.minibgm.feature.subject.SubjectDetailViewModel
import com.infinitezerone.minibgm.feature.subject.TopicDetailViewModel
import com.infinitezerone.minibgm.feature.subject.player.PlayerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val subjectModule =
    module {
        // Koin parametersOf 模式：subjectId 由调用方经 parametersOf 传入（Koin 4 Definition 解构写法）
        viewModel { (subjectId: Long) ->
            SubjectDetailViewModel(
                subjectRepository = get(),
                subjectId = subjectId,
                collectionRepository = get(),
                communityRepository = get(),
                authRepository = get(),
                settingsRepository = get(),
                failureStore = get(),
                searchRepository = getOrNull(),
                torrentFeedRepository = getOrNull(),
            )
        }

        viewModel { (subjectId: Long, episodeId: Long) ->
            EpisodeDetailViewModel(
                subjectId = subjectId,
                episodeId = episodeId,
                subjectRepository = get(),
                collectionRepository = get(),
                communityRepository = get(),
                authRepository = get(),
                settingsRepository = get(),
                failureStore = get(),
                torrentFeedRepository = getOrNull(),
            )
        }

        viewModel { params ->
            val topicId = runCatching { params.get<Long>(0) }.getOrDefault(0L)
            val type = runCatching { params.get<String>(1) }.getOrDefault("subject")
            TopicDetailViewModel(
                topicId = topicId,
                type = type,
                communityRepository = get(),
                authRepository = get(),
                // 必填：漏传会静默退化成"回帖排序不落库"（默认值兜住、Koin 与单测都不报错）
                settingsRepository = get(),
            )
        }

        viewModel { (route: PlayerRoute) ->
            PlayerViewModel(
                route = route,
                collectionRepository = get(),
                authRepository = get(),
                settingsRepository = get(),
                failureStore = get(),
                subjectRepository = get(),
                playbackResolverRepository = get(),
                playbackSourceVerifier = getOrNull(),
                communityRepository = getOrNull(),
                danmakuRepository = getOrNull(),
                torrentStreamEngine = getOrNull(),
                // 必填：漏传会在生产里静默退化为 null，播放历史不再落库、换集/换源的"历史兜底续播"
                // 整条死掉，而 Koin 解析（默认 null 参数）与单测（自己传 fake）都不会报错
                playbackHistoryRepository = get(),
            )
        }
    }
