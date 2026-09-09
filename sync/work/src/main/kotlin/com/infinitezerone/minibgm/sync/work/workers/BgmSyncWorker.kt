package com.infinitezerone.minibgm.sync.work.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.BgmDispatchers
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.util.SyncCompletionObserver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 后台数据同步 Worker（约束：网络已连接）。
 *
 * 刷新时刻表（全量公共数据），若用户已登录则同步个人追番收藏；全部落盘后通知观察者（如小组件）。
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
    private val log = bgmLogger("Bgm/Worker/Sync")

    override suspend fun doWork(): Result =
        withContext(dispatchers.io) {
            val startTime = TimeUtils.nowEpochMillis()
            log.d { "[SYNC_WORKER:START] attempt=$runAttemptCount" }
            val scheduleResult = scheduleRepository.syncBangumiData(force = false)
            if (tokenProvider.activeUserId.first() != null) {
                collectionRepository.syncWatchingCollections()
            }
            val duration = TimeUtils.nowEpochMillis() - startTime
            when (scheduleResult) {
                is AppResult.Success -> {
                    log.i { "[SYNC_WORKER:SUCCESS] took ${duration}ms notify syncCompletionObserver" }
                    // 新排期已写入本地，通知下游（如桌面小组件）自行刷新：数据一到就上屏，
                    // 而不是等下一次心跳。具体消费方由 SyncCompletionObserver 接口解耦。
                    syncCompletionObserver.onSyncSucceeded()
                    Result.success()
                }
                is AppResult.Error -> {
                    log.e(scheduleResult.throwable) {
                        "[SYNC_WORKER:FAILED] took ${duration}ms attempt=$runAttemptCount message=${scheduleResult.throwable.message}"
                    }
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
