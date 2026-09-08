package com.infinitezerone.minibgm.feature.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 小组件刷新 Worker：仅触发 Glance 重建，数据由 [ScheduleWidget.provideGlance]
 * 自行从本地派生（零网络）。作为系统 updatePeriodMillis 心跳之外的补充分发路径，
 * 两者任一先到都能刷新。
 */
class WidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result =
        runCatching {
            ScheduleWidget().updateAll(applicationContext)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { e ->
                // updateAll 失败通常是瞬时 IPC/系统异常：返回 retry 让 WorkManager
                // 按退避策略重试，而不是伪装成功导致小组件停留在过期数据上
                Log.e(TAG, "Widget update failed, scheduling retry", e)
                Result.retry()
            },
        )

    companion object {
        const val TAG = "WidgetUpdateWorker"
        const val PERIODIC_WORK_NAME = "BgmWidgetUpdatePeriodicWork"
    }
}
