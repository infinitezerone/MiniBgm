package com.infinitezerone.minibgm.core.ai.di

import com.infinitezerone.minibgm.core.ai.BgmAiAgentService
import com.infinitezerone.minibgm.core.ai.DefaultBgmAiAgentService
import com.infinitezerone.minibgm.core.ai.DefaultPendingActionExecutor
import com.infinitezerone.minibgm.core.ai.PendingActionExecutor
import com.infinitezerone.minibgm.core.ai.PendingActionStore
import com.infinitezerone.minibgm.core.ai.tools.CollectionTools
import com.infinitezerone.minibgm.core.ai.tools.PlayableSourceTools
import com.infinitezerone.minibgm.core.ai.tools.ScheduleTools
import com.infinitezerone.minibgm.core.ai.tools.SubjectTools
import org.koin.dsl.module

val aiModule =
    module {
        single { PendingActionStore() }
        single { ScheduleTools(scheduleRepository = get()) }
        single { SubjectTools(searchRepository = get(), subjectRepository = get()) }
        single { CollectionTools(collectionRepository = get(), pendingActionStore = get<PendingActionStore>()) }
        single {
            PlayableSourceTools(
                scheduleRepository = get(),
                subjectRepository = get(),
                settingsRepository = get(),
                playbackResolverRepository = get(),
            )
        }
        single {
            com.infinitezerone.minibgm.core.ai.tools.CommunityTools(
                settingsRepository = get(),
                pendingActionStore = get<PendingActionStore>(),
            )
        }
        single {
            com.infinitezerone.minibgm.core.ai.tools.PlaybackRuleDiagnosticsTools(
                playbackResolverRepository = get(),
            )
        }
        single<PendingActionExecutor> {
            DefaultPendingActionExecutor(
                collectionRepository = get(),
                settingsRepository = get(),
            )
        }

        single<BgmAiAgentService> {
            DefaultBgmAiAgentService(
                settingsRepository = get(),
                scheduleTools = getOrNull(),
                subjectTools = getOrNull(),
                collectionTools = getOrNull(),
                playableSourceTools = getOrNull(),
                communityTools = getOrNull(),
                playbackRuleDiagnosticsTools = getOrNull(),
                pendingActionExecutor = getOrNull(),
                pendingActionStore = getOrNull(),
            )
        }
    }
