package com.infinitezerone.minibgm.core.ai.di

import com.infinitezerone.minibgm.core.ai.BgmAiAgentService
import com.infinitezerone.minibgm.core.ai.DefaultBgmAiAgentService
import com.infinitezerone.minibgm.core.ai.DefaultPendingActionExecutor
import com.infinitezerone.minibgm.core.ai.PendingActionExecutor
import com.infinitezerone.minibgm.core.ai.PendingActionStore
import com.infinitezerone.minibgm.core.ai.tools.CollectionTools
import com.infinitezerone.minibgm.core.ai.tools.ScheduleTools
import com.infinitezerone.minibgm.core.ai.tools.SubjectTools
import org.koin.dsl.module

val aiModule =
    module {
        single { PendingActionStore() }
        single { ScheduleTools(scheduleRepository = get()) }
        single { SubjectTools(searchRepository = get(), subjectRepository = get()) }
        single { CollectionTools(collectionRepository = get(), pendingActionStore = get<PendingActionStore>()) }
        single<PendingActionExecutor> { DefaultPendingActionExecutor(collectionRepository = get()) }

        single<BgmAiAgentService> {
            DefaultBgmAiAgentService(
                settingsRepository = get(),
                scheduleTools = getOrNull(),
                subjectTools = getOrNull(),
                collectionTools = getOrNull(),
                pendingActionExecutor = getOrNull(),
                pendingActionStore = getOrNull(),
            )
        }
    }
