package com.infinitezerone.minibgm.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** 时间表小组件的统一调度入口：30 分钟节拍 + 午夜跨天刷新 */
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

    /** 跨天保障：在下一个 0 点安排一次一次性刷新。30 分钟心跳意味着日期翻转最长滞后 30 分钟，此处补齐边界 */
    fun enqueueMidnightUpdate(context: Context) {
        val now = ZonedDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val midnightUpdateWork =
            OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInitialDelay(Duration.between(now, nextMidnight))
                .addTag(WidgetUpdateWorker.TAG)
                .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                WidgetUpdateWorker.MIDNIGHT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                midnightUpdateWork,
            )
    }

    /** 注销周期更新任务（当用户移除桌面上的全部小组件时调用） */
    fun cancelAll(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(WidgetUpdateWorker.PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(WidgetUpdateWorker.MIDNIGHT_WORK_NAME)
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
