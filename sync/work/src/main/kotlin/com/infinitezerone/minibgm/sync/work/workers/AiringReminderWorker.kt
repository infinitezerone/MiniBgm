package com.infinitezerone.minibgm.sync.work.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import com.infinitezerone.minibgm.sync.work.reminders.AiringReminderNotifier
import com.infinitezerone.minibgm.sync.work.reminders.AiringReminderPlanner
import kotlinx.coroutines.flow.firstOrNull

/**
 * 开播提醒 Worker：
 * 查询"我追的"条目在窗口内（默认 24 小时）的即将播出事件，
 * 经 [AiringReminderPlanner] 在设定时刻后每日一次决策，通过汇总通知提醒当日更新。
 * 纯本地查询，不强制网络约束；通知权限未授予时静默跳过。
 */
class AiringReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val scheduleRepository: ScheduleRepository,
    private val collectionRepository: CollectionRepository,
    private val userPreferences: UserPreferencesDataSource,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        Log.d(TAG, "AiringReminderWorker starting doWork...")
        val prefs = userPreferences.userPreferences.firstOrNull() ?: return Result.success()
        val today: String =
            java.time.LocalDate
                .now()
                .toString()

        val trackedSubjectIds =
            collectionRepository
                .getCollectionsByTypeStream(CollectionType.DOING)
                .firstOrNull()
                .orEmpty()
                .map { it.subjectId }

        val upcoming: List<UpcomingAiring> =
            scheduleRepository.getUpcomingAiringForSubjects(
                subjectIds = trackedSubjectIds,
                hoursAhead = WINDOW_HOURS,
            )

        val toNotify =
            AiringReminderPlanner.plan(
                enabled = prefs.airingReminderEnabled,
                isLoggedIn = prefs.isLoggedIn,
                lastNotifiedDate = prefs.airingReminderLastNotifiedDate,
                today = today,
                currentHour =
                    java.time.LocalTime
                        .now()
                        .hour,
                reminderHour = prefs.airingReminderHour,
                upcoming = upcoming,
            )

        if (toNotify.isEmpty()) {
            Log.d(TAG, "AiringReminderWorker: nothing to notify")
            return Result.success()
        }

        val notifier = AiringReminderNotifier(applicationContext)
        if (!notifier.notificationsEnabled()) {
            Log.d(TAG, "AiringReminderWorker: notifications disabled by user")
            return Result.success()
        }

        return runCatching {
            notifier.notify(toNotify)
            userPreferences.setAiringReminderLastNotifiedDate(today.toString())
        }.fold(
            onSuccess = {
                Log.d(TAG, "AiringReminderWorker notified ${toNotify.size} updates")
                Result.success()
            },
            onFailure = { e ->
                Log.e(TAG, "AiringReminderWorker failed to notify", e)
                Result.success()
            },
        )
    }

    companion object {
        const val TAG = "AiringReminderWorker"
        const val PERIODIC_WORK_NAME = "BgmAiringReminderPeriodicWork"
        const val WINDOW_HOURS = 24L
    }
}
