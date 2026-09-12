package com.infinitezerone.minibgm.feature.agent.di

import com.infinitezerone.minibgm.feature.agent.AgentChatViewModel
import com.infinitezerone.minibgm.feature.agent.MiniBgmAgentTools
import com.miniagent.provider.cloud.OpenAiCompatibleProvider
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val agentModule =
    module {
        single { MiniBgmAgentTools(scheduleRepository = get(), searchRepository = get(), collectionRepository = get()) }
        viewModel {
            AgentChatViewModel(
                toolsFactory = get(),
                providerFactory = ::OpenAiCompatibleProvider,
            )
        }
    }
