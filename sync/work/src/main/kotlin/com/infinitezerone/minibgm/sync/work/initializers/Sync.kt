package com.infinitezerone.minibgm.sync.work.initializers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.sync.work.reminders.AiringAlarmScheduler
import com.infinitezerone.minibgm.sync.work.reminders.AiringReminderNotifier
import com.infinitezerone.minibgm.sync.work.workers.AiringReminderWorker
import com.infinitezerone.minibgm.sync.work.workers.BgmSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

val SyncConstraints =
    Constraints
        .Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

/**
 * 统一后台调度启动器（对标 NiA Sync.initialize）：
 * 1. 启动时入队一次性后台同步任务，并在网络就绪时执行首次静默同步；
 * 2. 预注册通知渠道与分组（解决系统设置懒加载缺陷）；
 * 3. 彻底注销旧版 WorkManager 15 分钟轮询，交由 [AiringAlarmScheduler] 定点精确调度；
 * 4. 持续监听偏好变动动态调谐后台同步节拍与开播提醒闹钟。
 */
object Sync {
    fun enqueueStartupSync(context: Context) {
        val workManager = WorkManager.getInstance(context)

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
        AiringReminderNotifier.createNotificationChannels(context)
        // 彻底注销旧版 WorkManager 周期任务
        WorkManager.getInstance(context).cancelUniqueWork(AiringReminderWorker.PERIODIC_WORK_NAME)
        enqueueStartupSync(context)
        reconfigure(context, interval)
    }

    /** 结合用户偏好执行智能启动同步（节流与未同步检测），预注册通知渠道并动态维护 AlarmManager 闹钟 */
    fun initialize(
        context: Context,
        userPreferences: UserPreferencesDataSource,
        scope: CoroutineScope,
    ) {
        // 1. 预注册系统通知渠道与分组（开箱即见）
        AiringReminderNotifier.createNotificationChannels(context)

        // 2. 彻底注销旧版 WorkManager 周期任务（消除历史升级残留）
        WorkManager.getInstance(context).cancelUniqueWork(AiringReminderWorker.PERIODIC_WORK_NAME)

        // 3. 启动即刻核准一次开播提醒闹钟
        val scheduler = runCatching { GlobalContext.get().get<AiringAlarmScheduler>() }.getOrNull()
        scope.launch {
            scheduler?.rescheduleAll()
        }

        // 4. 监听开播提醒偏好或同步时间戳变动，动态核准 Alarm
        scope.launch {
            userPreferences.userPreferences
                .map {
                    listOf(
                        it.airingReminderEnabled,
                        it.airingDailySummaryEnabled,
                        it.airingPreAirEnabled,
                        it.airingReminderHour,
                        it.notifyBeforeAirMinutes,
                        it.airDelayOffsetMinutes,
                        it.bangumiDataLastSyncTimestamp,
                    )
                }.distinctUntilChanged()
                .collect {
                    scheduler?.rescheduleAll()
                }
        }

        // 5. 数据同步检查与监听
        scope.launch {
            val initialPrefs = userPreferences.userPreferences.first()
            val isNeverSynced = initialPrefs.bangumiDataLastSyncTimestamp == 0L
            val isAutoSyncEnabled = initialPrefs.syncInterval != SyncInterval.MANUAL_ONLY
            val intervalMillis = TimeUnit.HOURS.toMillis(initialPrefs.syncInterval.hours)
            val isExpired = (TimeUtils.nowEpochMillis() - initialPrefs.bangumiDataLastSyncTimestamp) > intervalMillis

            if (isAutoSyncEnabled && (isNeverSynced || isExpired)) {
                enqueueStartupSync(context)
            }

            userPreferences.userPreferences
                .map { it.syncInterval }
                .distinctUntilChanged()
                .collect { syncInterval ->
                    reconfigure(context, syncInterval)
                }
        }
    }

    fun reconfigure(
        context: Context,
        interval: SyncInterval,
    ) {
        val workManager = WorkManager.getInstance(context)

        if (interval == SyncInterval.MANUAL_ONLY) {
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
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicSyncWork,
        )
    }
}
