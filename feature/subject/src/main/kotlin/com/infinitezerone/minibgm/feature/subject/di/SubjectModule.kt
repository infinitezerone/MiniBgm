package com.infinitezerone.minibgm.feature.subject.di

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
            )
        }

        viewModel { params ->
            val topicId = runCatching { params.get<Long>(0) }.getOrDefault(0L)
            val type = runCatching { params.get<String>(1) }.getOrDefault("subject")
            TopicDetailViewModel(
                topicId = topicId,
                type = type,
                communityRepository = get(),
            )
        }

        viewModel { (subjectId: Long, episodeId: Long, streamUrl: String) ->
            PlayerViewModel(
                subjectId = subjectId,
                episodeId = episodeId,
                initialStreamUrl = streamUrl,
                collectionRepository = get(),
                authRepository = get(),
            )
        }
    }
