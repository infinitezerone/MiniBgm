package com.infinitezerone.minibgm.feature.widget.di

import com.infinitezerone.minibgm.feature.widget.WidgetUpdateWorker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

val widgetModule =
    module {
        workerOf(::WidgetUpdateWorker)
    }
