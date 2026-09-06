package com.infinitezerone.minibgm.sync.work.initializers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.sync.work.workers.AiringReminderWorker
import com.infinitezerone.minibgm.sync.work.workers.BgmSyncWorker
import java.util.concurrent.TimeUnit

val SyncConstraints =
    Constraints
        .Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

/** 开播提醒纯本地查询，不限网络；每小时探测一次窗口内的更新 */
val ReminderConstraints =
    Constraints
        .Builder()
        .setRequiresBatteryNotLow(true)
        .build()

/**
 * 统一调度启动器（对标 NiA Sync.initialize）：
 * 1. App 启动初始化时立即入队一次性后台同步任务（对标 NiA startUpSyncWork），
 *    确保新安装 / 无缓存 / 数据为空时立即发起首次静默同步。
 * 2. 根据用户偏好中的 [SyncInterval] 动态注册或注销系统后台周期任务。
 */
object Sync {
    fun enqueueStartupSync(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // 启动时入队一次性静默同步（REPLACE 保证若有残留失败或旧任务时直接覆盖重新执行）
        val startupSyncWork =
            OneTimeWorkRequestBuilder<BgmSyncWorker>()
                .setConstraints(SyncConstraints)
                .addTag(BgmSyncWorker.TAG)
                .build()

        workManager.enqueueUniqueWork(
            BgmSyncWorker.STARTUP_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            startupSyncWork,
        )
    }

    fun initialize(
        context: Context,
        interval: SyncInterval = SyncInterval.WEEKLY,
    ) {
        enqueueStartupSync(context)
        enqueueAiringReminders(context)
        reconfigure(context, interval)
    }

    /** 注册开播提醒周期任务（15 分钟本地探测：每日汇总去重 + 开播前 15 分钟逐集提醒窗口） */
    fun enqueueAiringReminders(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val periodicReminderWork =
            PeriodicWorkRequestBuilder<AiringReminderWorker>(
                repeatInterval = 15L,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            ).setConstraints(ReminderConstraints)
                .addTag(AiringReminderWorker.TAG)
                .build()

        workManager.enqueueUniquePeriodicWork(
            AiringReminderWorker.PERIODIC_WORK_NAME,
            // UPDATE 而非 KEEP：节拍从历史版本的 1 小时加密到 15 分钟后，升级用户需要同步新周期
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicReminderWork,
        )
    }

    fun reconfigure(
        context: Context,
        interval: SyncInterval,
    ) {
        val workManager = WorkManager.getInstance(context)

        if (interval == SyncInterval.MANUAL_ONLY) {
            // 彻底注销系统后台周期任务
            workManager.cancelUniqueWork(BgmSyncWorker.PERIODIC_SYNC_WORK_NAME)
            return
        }

        val periodicSyncWork =
            PeriodicWorkRequestBuilder<BgmSyncWorker>(
                repeatInterval = interval.hours,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            ).setConstraints(SyncConstraints)
                .addTag(BgmSyncWorker.TAG)
                .build()

        workManager.enqueueUniquePeriodicWork(
            BgmSyncWorker.PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            periodicSyncWork,
        )
    }
}
