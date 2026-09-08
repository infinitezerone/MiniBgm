package com.infinitezerone.minibgm.sync.work.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.BgmDispatchers
import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.util.SyncCompletionObserver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 周期性后台同步 Worker（对标 NiA SyncWorker）：
 * 在系统满足网络连通与非低电量约束时唤醒，静默刷新时刻表并执行 ETag 304 探测与本地持久化，
 * 若当前已登录则一并同步用户的「在看」追番状态，保证桌面小组件与开播提醒消费到最新数据。
 */
class BgmSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val scheduleRepository: ScheduleRepository,
    private val collectionRepository: CollectionRepository,
    private val tokenProvider: TokenProvider,
    private val dispatchers: BgmDispatchers,
    private val syncCompletionObserver: SyncCompletionObserver,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result =
        withContext(dispatchers.io) {
            Log.d(TAG, "BgmSyncWorker starting doWork... attempt: $runAttemptCount")
            val scheduleResult = scheduleRepository.syncBangumiData(force = false)
            if (tokenProvider.activeUserId.first() != null) {
                collectionRepository.syncWatchingCollections()
            }
            when (scheduleResult) {
                is AppResult.Success -> {
                    Log.d(TAG, "BgmSyncWorker succeeded!")
                    // 新排期已写入本地，通知下游（如桌面小组件）自行刷新：数据一到就上屏，
                    // 而不是等下一次心跳。具体消费方由 SyncCompletionObserver 接口解耦。
                    syncCompletionObserver.onSyncSucceeded()
                    Result.success()
                }
                is AppResult.Error -> {
                    Log.e(TAG, "BgmSyncWorker failed with error: ${scheduleResult.throwable.message}", scheduleResult.throwable)
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
                is AppResult.Loading -> Result.success()
            }
        }

    companion object {
        const val TAG = "BgmSyncWorker"
        const val STARTUP_SYNC_WORK_NAME = "BgmStartupSyncWork"
        const val PERIODIC_SYNC_WORK_NAME = "BgmPeriodicSyncWork"
        const val MANUAL_SYNC_WORK_NAME = "BgmManualSyncWork"
        const val SYNC_WORK_NAME = "BgmSyncWork"
    }
}
