package com.infinitezerone.minibgm.sync.work.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import com.infinitezerone.minibgm.sync.work.reminders.AiringReminderNotifier
import com.infinitezerone.minibgm.sync.work.reminders.AiringReminderPlanner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

/**
 * 开播提醒 Worker（15 分钟节拍，纯本地查询，不强制网络约束）：
 * 1. 每日汇总——设定时刻后当天首次触发，汇总「我追的」条目在窗口内（默认 24 小时）的更新；
 * 2. 开播前提醒——单集临近开播（提前 [UserPreferences.notifyBeforeAirMinutes] 分钟）时实时通知，
 *    经逐集去重键防止重复提醒。
 * 判定逻辑在 [AiringReminderPlanner]；通知权限未授予时静默跳过。
 */
class AiringReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val scheduleRepository: ScheduleRepository,
    private val collectionRepository: CollectionRepository,
    private val userPreferences: UserPreferencesDataSource,
    private val tokenProvider: TokenProvider,
) : CoroutineWorker(appContext, workerParams) {
    private val log = bgmLogger("Bgm/Worker/Reminder")

    override suspend fun doWork(): Result {
        log.d { "[REMINDER_WORKER:START] checking reminders..." }
        val prefs = userPreferences.userPreferences.firstOrNull() ?: return Result.success()
        // 会话事实来自凭据库（与各 Repository 同一判据）
        val isLoggedIn = tokenProvider.activeUserId.first() != null
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

        val notifier = AiringReminderNotifier(applicationContext)
        if (!notifier.notificationsEnabled()) {
            log.d { "[REMINDER_WORKER:SKIP] notifications disabled by user" }
            return Result.success()
        }

        // 流程一：每日汇总（设定时刻后当天首次）
        val dailySummary =
            AiringReminderPlanner.plan(
                enabled = prefs.airingReminderEnabled,
                isLoggedIn = isLoggedIn,
                lastNotifiedDate = prefs.airingReminderLastNotifiedDate,
                today = today,
                currentHour =
                    java.time.LocalTime
                        .now()
                        .hour,
                reminderHour = prefs.airingReminderHour,
                upcoming = upcoming,
            )
        if (dailySummary.isNotEmpty()) {
            runCatching { notifier.notify(dailySummary) }.fold(
                onSuccess = {
                    log.i { "[REMINDER_DAILY:SUCCESS] notified ${dailySummary.size} daily updates" }
                    userPreferences.setAiringReminderLastNotifiedDate(today)
                },
                onFailure = { e -> log.e(e) { "[REMINDER_DAILY:FAILED] failed to notify daily summary" } },
            )
        }

        // 流程二：开播前提醒（临近单集，逐集去重，不受每日提醒时刻约束）
        val preAir =
            AiringReminderPlanner.pickPreAir(
                enabled = prefs.airingReminderEnabled,
                isLoggedIn = isLoggedIn,
                notifiedKeys = prefs.airingReminderNotifiedKeys,
                today = today,
                nowEpochMillis = TimeUtils.nowEpochMillis(),
                leadMinutes = prefs.notifyBeforeAirMinutes.toLong(),
                upcoming = upcoming,
            )
        if (preAir.isNotEmpty()) {
            runCatching { notifier.notifyImminent(preAir) }.fold(
                onSuccess = {
                    log.i { "[REMINDER_PRE_AIR:SUCCESS] notified ${preAir.size} imminent episodes" }
                    val keptKeys =
                        prefs.airingReminderNotifiedKeys
                            .filter { it.startsWith("$today:") }
                            .toSet() + preAir.map { AiringReminderPlanner.preAirKey(today, it) }
                    userPreferences.setAiringReminderNotifiedKeys(keptKeys.toList())
                },
                onFailure = { e -> log.e(e) { "[REMINDER_PRE_AIR:FAILED] failed to notify pre-air" } },
            )
        }

        return Result.success()
    }

    companion object {
        const val TAG = "AiringReminderWorker"
        const val PERIODIC_WORK_NAME = "BgmAiringReminderPeriodicWork"
        const val WINDOW_HOURS = 24L
    }
}
