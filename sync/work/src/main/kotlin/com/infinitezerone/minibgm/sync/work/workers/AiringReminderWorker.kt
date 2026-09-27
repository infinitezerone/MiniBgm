package com.infinitezerone.minibgm.sync.work.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 历史遗留 Worker（已由 [com.infinitezerone.minibgm.sync.work.reminders.AiringAlarmScheduler] 取代）。
 * 保留此类仅作为升级防崩安全壳，启动时会被自动注销。
 */
@Deprecated("Replaced by AiringAlarmScheduler")
class AiringReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = Result.success()

    companion object {
        const val TAG = "AiringReminderWorker"
        const val PERIODIC_WORK_NAME = "BgmAiringReminderPeriodicWork"
    }
}
