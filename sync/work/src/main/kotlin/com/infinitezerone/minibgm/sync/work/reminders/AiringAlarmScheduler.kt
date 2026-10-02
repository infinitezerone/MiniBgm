package com.infinitezerone.minibgm.sync.work.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.CollectionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 开播提醒定时调度器：基于 Android [AlarmManager.setAndAllowWhileIdle] 实现低功耗、准时的
 * 单集临近开播与每日汇总唤醒调度（Next-Up 链式预约），杜绝 WorkManager 频繁轮询与息屏延误。
 */
class AiringAlarmScheduler(
    private val context: Context,
    private val scheduleRepository: ScheduleRepository,
    private val collectionRepository: CollectionRepository,
    private val userPreferences: UserPreferencesDataSource,
    private val tokenProvider: TokenProvider,
) {
    private val log = bgmLogger("Bgm/Alarm/Scheduler")
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * 重新核准并预约所有提醒（单集开播 + 每日汇总）。
     * 在数据同步完成、用户收藏更新、设置项变动或开机重启时调用。
     */
    suspend fun rescheduleAll() {
        scheduleNextPreAir()
        scheduleDailySummary()
    }

    /**
     * 预约离现在最近的一部单集开播提醒（Next-Up）。
     */
    suspend fun scheduleNextPreAir() {
        if (alarmManager == null) return
        val prefs = userPreferences.userPreferences.firstOrNull() ?: return
        val isLoggedIn = tokenProvider.activeUserId.first() != null

        // 开关未开或未登录，直接取消已注册的单集闹钟
        if (!prefs.airingReminderEnabled || !prefs.airingPreAirEnabled || !isLoggedIn) {
            cancelPreAirAlarm()
            return
        }

        val trackedSubjectIds =
            collectionRepository
                .getCollectionsByTypeStream(CollectionType.DOING)
                .firstOrNull()
                .orEmpty()
                .map { it.subjectId }

        if (trackedSubjectIds.isEmpty()) {
            cancelPreAirAlarm()
            return
        }

        // 检索未来 14 天（336 小时）内的开播事件；保持 Next-Up 链式预约跨周连贯，
        // 杜绝追番间隔超过 48 小时导致闹钟被清空、直到用户手动开 App 才能复苏的断链隐患。
        // 回看窗口必须覆盖容错区间，否则「刚错过几分钟」的剧集会在进入
        // [AiringReminderPlanner.nextAiringSchedule] 的迟到判定前就被查询本身排除，
        // 导致重新核准（冷启动 / 重装 / 开机 / 改时区）无法补发
        val upcoming =
            scheduleRepository.getUpcomingAiringForSubjects(
                subjectIds = trackedSubjectIds,
                hoursAhead = PRE_AIR_LOOKAHEAD_HOURS,
                lookbackHours = AiringReminderPlanner.preAirLookbackHours(prefs.airDelayOffsetMinutes.toLong()),
            )

        val nextDecision =
            AiringReminderPlanner.nextAiringSchedule(
                notifiedKeys = prefs.airingReminderNotifiedKeys,
                nowEpochMillis = TimeUtils.nowEpochMillis(),
                leadMinutes = prefs.notifyBeforeAirMinutes.toLong(),
                airDelayOffsetMinutes = prefs.airDelayOffsetMinutes.toLong(),
                bingeSubjectIds = prefs.bingeSubjectIds,
                bingeFinaleEnabled = prefs.airingBingeFinaleEnabled,
                upcoming = upcoming,
            )

        if (nextDecision == null) {
            log.d { "[ALARM_SCHEDULER:PRE_AIR] no imminent episodes in window, alarm cleared" }
            cancelPreAirAlarm()
            return
        }

        val (item, triggerMillis) = nextDecision
        val now = TimeUtils.nowEpochMillis()

        // 迟到/已到点补偿机制：如果剧集已经到了开播或提前提醒时刻（系统调度推迟或冷启动检测到漏报），
        // 立即向广播接收器发送意图执行补偿推送与链式调度，无需再经历 AlarmManager 唤醒往返
        if (triggerMillis <= now) {
            log.i {
                "[ALARM_SCHEDULER:PRE_AIR] immediate catch-up for 《${item.displayName}》ep${item.episode}"
            }
            context.sendBroadcast(
                Intent(context, AiringAlarmReceiver::class.java).apply {
                    action = ACTION_AIRING_PRE_AIR
                },
            )
            return
        }

        val pendingIntent = createPreAirPendingIntent()

        runCatching {
            setExactOrInexactAlarm(triggerMillis, pendingIntent)
            log.i {
                "[ALARM_SCHEDULER:PRE_AIR] scheduled for 《${item.displayName}》ep${item.episode} at " +
                    TimeUtils.isoUtcFromEpochMillis(triggerMillis)
            }
        }.onFailure { e ->
            log.e(e) { "[ALARM_SCHEDULER:PRE_AIR:FAILED] failed to set alarm" }
        }
    }

    /**
     * 预约每日追番汇总提醒（每天在用户设定的小时触发一次）。
     */
    suspend fun scheduleDailySummary() {
        if (alarmManager == null) return
        val prefs = userPreferences.userPreferences.firstOrNull() ?: return
        val isLoggedIn = tokenProvider.activeUserId.first() != null

        if (!prefs.airingReminderEnabled || !prefs.airingDailySummaryEnabled || !isLoggedIn) {
            cancelDailySummaryAlarm()
            return
        }

        val now = LocalDateTime.now()
        val todayStr = now.toLocalDate().toString()
        var targetDateTime =
            now
                .withHour(prefs.airingReminderHour.coerceIn(0, 23))
                .withMinute(0)
                .withSecond(0)
                .withNano(0)

        // 若今天该时刻已过，或者今天已经推送过汇总，预约明天同一时刻
        if (now.isAfter(targetDateTime) || prefs.airingReminderLastNotifiedDate == todayStr) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        val triggerMillis =
            targetDateTime
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

        val pendingIntent = createDailySummaryPendingIntent()

        runCatching {
            setExactOrInexactAlarm(triggerMillis, pendingIntent)
            log.i {
                "[ALARM_SCHEDULER:DAILY] scheduled daily summary at " +
                    TimeUtils.isoUtcFromEpochMillis(triggerMillis)
            }
        }.onFailure { e ->
            log.e(e) { "[ALARM_SCHEDULER:DAILY:FAILED] failed to set daily summary alarm" }
        }
    }

    private fun setExactOrInexactAlarm(
        triggerMillis: Long,
        pendingIntent: PendingIntent,
    ) {
        val am = alarmManager ?: return
        val canExact =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.canScheduleExactAlarms()
            } else {
                true
            }
        val exactSuccess =
            if (canExact) {
                runCatching {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent,
                    )
                }.isSuccess
            } else {
                false
            }
        if (!exactSuccess) {
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent,
            )
        }
    }

    fun cancelAll() {
        cancelPreAirAlarm()
        cancelDailySummaryAlarm()
    }

    fun cancelPreAirAlarm() {
        alarmManager?.cancel(createPreAirPendingIntent())
    }

    fun cancelDailySummaryAlarm() {
        alarmManager?.cancel(createDailySummaryPendingIntent())
    }

    private fun createPreAirPendingIntent(): PendingIntent {
        val intent =
            Intent(context, AiringAlarmReceiver::class.java).apply {
                action = ACTION_AIRING_PRE_AIR
            }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PRE_AIR,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createDailySummaryPendingIntent(): PendingIntent {
        val intent =
            Intent(context, AiringAlarmReceiver::class.java).apply {
                action = ACTION_AIRING_DAILY_SUMMARY
            }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY_SUMMARY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_AIRING_PRE_AIR = "com.infinitezerone.minibgm.sync.work.action.AIRING_PRE_AIR"
        const val ACTION_AIRING_DAILY_SUMMARY = "com.infinitezerone.minibgm.sync.work.action.AIRING_DAILY_SUMMARY"

        private const val REQUEST_CODE_PRE_AIR = 47101
        private const val REQUEST_CODE_DAILY_SUMMARY = 47102

        /** 单集开播检索窗口（14 天 = 336 小时），覆盖跨周番剧与周番停播/合集等空档，保证 Next-Up 链式不中断 */
        const val PRE_AIR_LOOKAHEAD_HOURS = 14L * 24L
    }
}
