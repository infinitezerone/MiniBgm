package com.infinitezerone.minibgm.sync.work.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.CollectionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import java.time.ZoneId

/**
 * 开播提醒精确闹钟接收器：到点由 Android 系统精确唤醒，执行发通知与链式预约下一部番剧。
 */
class AiringAlarmReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val log = bgmLogger("Bgm/Alarm/Receiver")
    private val scheduleRepository: ScheduleRepository by inject()
    private val collectionRepository: CollectionRepository by inject()
    private val userPreferences: UserPreferencesDataSource by inject()
    private val tokenProvider: TokenProvider by inject()
    private val scheduler: AiringAlarmScheduler by inject()

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        val action = intent?.action ?: return
        log.d { "[ALARM_RECEIVER:TRIGGERED] action=$action" }

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                when (action) {
                    AiringAlarmScheduler.ACTION_AIRING_PRE_AIR -> handlePreAir(context)
                    AiringAlarmScheduler.ACTION_AIRING_DAILY_SUMMARY -> handleDailySummary(context)
                }
            } catch (e: Throwable) {
                log.e(e) { "[ALARM_RECEIVER:ERROR] failed processing alarm action: $action" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handlePreAir(context: Context) {
        val prefs = userPreferences.userPreferences.firstOrNull() ?: return
        val isLoggedIn = tokenProvider.activeUserId.first() != null
        if (!prefs.airingReminderEnabled || !prefs.airingPreAirEnabled || !isLoggedIn) {
            scheduler.cancelPreAirAlarm()
            return
        }

        val today = LocalDate.now().toString()
        val trackedSubjectIds =
            collectionRepository
                .getCollectionsByTypeStream(CollectionType.DOING)
                .firstOrNull()
                .orEmpty()
                .map { it.subjectId }

        // 回看窗口与 [AiringReminderPlanner.PRE_AIR_GRACE_MINUTES] 同源：闹钟可能被系统
        // 晚投递至多 1 小时，窗口若窄于容错区间会让本该补发的提醒查不出来
        val upcoming =
            scheduleRepository.getUpcomingAiringForSubjects(
                subjectIds = trackedSubjectIds,
                hoursAhead = 4L,
                lookbackHours = AiringReminderPlanner.preAirLookbackHours(prefs.airDelayOffsetMinutes.toLong()),
            )

        val notifier = AiringReminderNotifier(context)
        if (!notifier.notificationsEnabled()) {
            log.d { "[ALARM_RECEIVER:PRE_AIR:SKIP] notifications disabled in system" }
            scheduler.scheduleNextPreAir()
            return
        }

        val nowEpoch = TimeUtils.nowEpochMillis()
        val preAir =
            AiringReminderPlanner.pickPreAir(
                enabled = prefs.airingReminderEnabled,
                isLoggedIn = isLoggedIn,
                notifiedKeys = prefs.airingReminderNotifiedKeys,
                today = today,
                nowEpochMillis = nowEpoch,
                leadMinutes = prefs.notifyBeforeAirMinutes.toLong(),
                airDelayOffsetMinutes = prefs.airDelayOffsetMinutes.toLong(),
                upcoming = upcoming,
            )

        if (preAir.isNotEmpty()) {
            for (item in preAir) {
                val isStarted =
                    AiringReminderPlanner.isAlreadyStarted(
                        item = item,
                        nowEpochMillis = nowEpoch,
                        airDelayOffsetMinutes = prefs.airDelayOffsetMinutes.toLong(),
                    )
                runCatching {
                    notifier.notifyImminent(listOf(item), isAlreadyStarted = isStarted)
                }.onSuccess {
                    log.i { "[ALARM_RECEIVER:PRE_AIR:SUCCESS] notified 《${item.displayName}》ep${item.episode}" }
                }.onFailure { e ->
                    log.e(e) { "[ALARM_RECEIVER:PRE_AIR:FAILED] failed notifying 《${item.displayName}》" }
                }
            }

            // 更新已通知去重键。裁剪放宽到「今天 + 昨天」：容错窗口放大到 1 小时后，
            // 临近午夜开播的剧集可能在日期翻转之后才被补发，若只保留当日键，
            // 同一集会在跨日后被重新判定为未通知而重复推送。
            val keptKeys =
                AiringReminderPlanner
                    .pruneNotifiedKeys(prefs.airingReminderNotifiedKeys, today)
                    .toSet() + preAir.map { AiringReminderPlanner.preAirKey(today, it) }
            userPreferences.setAiringReminderNotifiedKeys(keptKeys.toList())
        }

        // 链式预约再下一部番剧
        scheduler.scheduleNextPreAir()
    }

    private suspend fun handleDailySummary(context: Context) {
        val prefs = userPreferences.userPreferences.firstOrNull() ?: return
        val isLoggedIn = tokenProvider.activeUserId.first() != null
        if (!prefs.airingReminderEnabled || !prefs.airingDailySummaryEnabled || !isLoggedIn) {
            scheduler.cancelDailySummaryAlarm()
            return
        }

        val todayDate = LocalDate.now()
        val todayStr = todayDate.toString()
        if (prefs.airingReminderLastNotifiedDate == todayStr) {
            log.d { "[ALARM_RECEIVER:DAILY:SKIP] already notified today ($todayStr)" }
            scheduler.scheduleDailySummary()
            return
        }

        val trackedSubjectIds =
            collectionRepository
                .getCollectionsByTypeStream(CollectionType.DOING)
                .firstOrNull()
                .orEmpty()
                .map { it.subjectId }

        val startOfDayMillis =
            todayDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val endOfDayMillis =
            todayDate
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli() - 1

        val nowMillis = TimeUtils.nowEpochMillis()
        val lookbackHours = maxOf(0L, (nowMillis - startOfDayMillis) / 3_600_000L + 1)
        val hoursAhead = maxOf(0L, (endOfDayMillis - nowMillis) / 3_600_000L + 1)

        val upcomingCandidates =
            scheduleRepository.getUpcomingAiringForSubjects(
                subjectIds = trackedSubjectIds,
                hoursAhead = hoursAhead,
                lookbackHours = lookbackHours,
            )

        // 严格以用户设备当前时区自然日（00:00:00 ~ 23:59:59）过滤当天的更新条目
        val todayItems =
            upcomingCandidates.filter { item ->
                val airMillis = runCatching { TimeUtils.epochMillisOfIso(item.airAtUtc) }.getOrNull()
                airMillis != null && airMillis in startOfDayMillis..endOfDayMillis
            }

        val notifier = AiringReminderNotifier(context)
        if (todayItems.isNotEmpty() && notifier.notificationsEnabled()) {
            runCatching {
                notifier.notify(todayItems)
            }.onSuccess {
                log.i { "[ALARM_RECEIVER:DAILY:SUCCESS] notified ${todayItems.size} daily updates" }
                userPreferences.setAiringReminderLastNotifiedDate(todayStr)
            }.onFailure { e ->
                log.e(e) { "[ALARM_RECEIVER:DAILY:FAILED] failed to notify daily summary" }
            }
        }

        // 预约明天的每日汇总
        scheduler.scheduleDailySummary()
    }
}
