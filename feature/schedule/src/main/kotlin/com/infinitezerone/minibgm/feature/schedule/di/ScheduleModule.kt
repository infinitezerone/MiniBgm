package com.infinitezerone.minibgm.feature.schedule.di

import com.infinitezerone.minibgm.feature.schedule.ScheduleViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val scheduleModule =
    module {
        viewModelOf(::ScheduleViewModel)
    }
