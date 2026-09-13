package com.infinitezerone.minibgm.feature.agent.di

import com.infinitezerone.minibgm.feature.agent.AgentChatViewModel
import com.infinitezerone.minibgm.feature.agent.MiniBgmAgentTools
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val agentModule =
    module {
        single { MiniBgmAgentTools(scheduleRepository = get(), searchRepository = get(), collectionRepository = get()) }
        viewModel {
            AgentChatViewModel(
                toolsFactory = get(),
                configRepository = get(),
                // 加密 checkpoint 存储由 :app 提供（依赖 Context 的私有目录），测试可缺省关闭持久化
                checkpointStorage = getOrNull(),
            )
        }
    }
