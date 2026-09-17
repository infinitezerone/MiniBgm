package com.infinitezerone.minibgm.sync.work.di

import com.infinitezerone.minibgm.core.data.util.SyncManager
import com.infinitezerone.minibgm.sync.work.status.WorkManagerSyncManager
import com.infinitezerone.minibgm.sync.work.workers.AiringReminderWorker
import com.infinitezerone.minibgm.sync.work.workers.BgmSyncWorker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

val syncWorkModule =
    module {
        workerOf(::BgmSyncWorker)
        workerOf(::AiringReminderWorker)
        single<SyncManager> { WorkManagerSyncManager(context = get()) }
    }
