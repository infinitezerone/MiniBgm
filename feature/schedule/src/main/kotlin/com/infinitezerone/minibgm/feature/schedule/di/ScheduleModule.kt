package com.infinitezerone.minibgm.feature.schedule.di

import com.infinitezerone.minibgm.feature.schedule.ScheduleViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val scheduleModule =
    module {
        viewModel {
            ScheduleViewModel(
                scheduleRepository = get(),
                collectionRepository = get(),
                settingsRepository = get(),
                authRepository = get(),
                subjectRepository = get(),
            )
        }
    }
