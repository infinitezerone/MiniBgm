package com.infinitezerone.minibgm.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** 时间表小组件的统一调度入口：30 分钟节拍触发刷新 */
object WidgetSync {
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
}
