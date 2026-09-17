package com.infinitezerone.minibgm.sync.work.status

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.infinitezerone.minibgm.core.data.util.SyncManager
import com.infinitezerone.minibgm.sync.work.initializers.SyncConstraints
import com.infinitezerone.minibgm.sync.work.workers.BgmSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map

/**
 * 基于 WorkManager 实现的 SyncManager：
 * 监听统一 Tag 状态流以获得 isSyncing 状态，并支持即时触发单次同步。
 */
class WorkManagerSyncManager(
    private val context: Context,
) : SyncManager {
    override val isSyncing: Flow<Boolean> =
        WorkManager
            .getInstance(context)
            .getWorkInfosByTagFlow(BgmSyncWorker.TAG)
            .map { list -> list.any { it.state == WorkInfo.State.RUNNING } }
            .conflate()

    override fun requestSync() {
        val workRequest =
            OneTimeWorkRequestBuilder<BgmSyncWorker>()
                .setConstraints(SyncConstraints)
                .addTag(BgmSyncWorker.TAG)
                .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                BgmSyncWorker.MANUAL_SYNC_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest,
            )
    }
}
