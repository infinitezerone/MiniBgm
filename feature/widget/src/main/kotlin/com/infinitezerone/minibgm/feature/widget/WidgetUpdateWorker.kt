package com.infinitezerone.minibgm.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.infinitezerone.minibgm.core.common.bgmLogger

/**
 * 小组件刷新 Worker：仅触发 Glance 重建，数据由 [ScheduleWidget.provideGlance]
 * 自行从本地派生（零网络）。作为系统 updatePeriodMillis 心跳之外的补充分发路径，
 * 两者任一先到都能刷新。
 */
class WidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val log = bgmLogger("Bgm/Worker/Widget")

    override suspend fun doWork(): Result =
        runCatching {
            ScheduleWidget().updateAll(applicationContext)
        }.fold(
            onSuccess = {
                log.d { "[WIDGET_UPDATE:SUCCESS] glance widgets updated, midnight scheduled" }
                // 午夜一次性任务自续：完成后预约下一个 0 点，保证跨天日切及时（30 分钟心跳是冗余兜底）
                WidgetSync.enqueueMidnightUpdate(applicationContext)
                Result.success()
            },
            onFailure = { e ->
                // updateAll 失败通常是瞬时 IPC/系统异常：返回 retry 让 WorkManager
                // 按退避策略重试，而不是伪装成功导致小组件停留在过期数据上
                log.e(e) { "[WIDGET_UPDATE:FAILED] widget updateAll failed, scheduling retry" }
                Result.retry()
            },
        )

    companion object {
        const val TAG = "WidgetUpdateWorker"
        const val PERIODIC_WORK_NAME = "BgmWidgetUpdatePeriodicWork"
        const val MIDNIGHT_WORK_NAME = "BgmWidgetUpdateMidnightWork"
    }
}
