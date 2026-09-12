package com.infinitezerone.minibgm.feature.agent.di

import com.infinitezerone.minibgm.feature.agent.AgentChatViewModel
import com.infinitezerone.minibgm.feature.agent.MiniBgmAgentTools
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val agentModule =
    module {
        single { MiniBgmAgentTools(scheduleRepository = get(), searchRepository = get(), collectionRepository = get()) }
        viewModelOf(::AgentChatViewModel)
    }
