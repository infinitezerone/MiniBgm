package com.infinitezerone.minibgm.feature.assistant.di

import com.infinitezerone.minibgm.feature.assistant.AssistantViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val assistantModule =
    module {
        viewModelOf(::AssistantViewModel)
    }
