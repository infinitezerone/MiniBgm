package com.infinitezerone.minibgm.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** 时间表小组件的统一调度入口：30 分钟节拍触发刷新 */
object WidgetSync {
    /** 检查桌面当前是否存在至少一个时间表小组件实例 */
    suspend fun hasActiveWidgets(context: Context): Boolean =
        runCatching {
            GlanceAppWidgetManager(context).getGlanceIds(ScheduleWidget::class.java).isNotEmpty()
        }.getOrDefault(false)

    fun enqueuePeriodicUpdate(context: Context) {
        val constraints =
            Constraints
                .Builder()
                .setRequiresBatteryNotLow(true)
                .build()
        val periodicUpdateWork =
            PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                repeatInterval = 30L,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            ).setConstraints(constraints)
                .addTag(WidgetUpdateWorker.TAG)
                .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(
                WidgetUpdateWorker.PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicUpdateWork,
            )
    }

    /** 注销周期更新任务（当用户移除桌面上的全部小组件时调用） */
    fun cancelPeriodicUpdate(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WidgetUpdateWorker.PERIODIC_WORK_NAME)
    }

    /** 主动即时触发小组件刷新（数据突变、收藏变更、退出前台时调用；桌面无组件时自动短路） */
    suspend fun requestUpdate(context: Context) {
        runCatching {
            if (hasActiveWidgets(context)) {
                ScheduleWidget().updateAll(context)
            }
        }
    }
}
