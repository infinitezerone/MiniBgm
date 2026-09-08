package com.infinitezerone.minibgm.feature.subject.di

import com.infinitezerone.minibgm.feature.subject.SubjectDetailViewModel
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
            )
        }
    }
