package com.infinitezerone.minibgm.sync.work.reminders

import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.model.UpcomingAiring

/**
 * 开播提醒的纯决策逻辑：给定偏好与数据，决定本次要提醒哪些更新。
 * 独立于 Android 通知 API，保持可测；时间窗口过滤由仓库层负责。
 */
object AiringReminderPlanner {
    fun plan(
        enabled: Boolean,
        isLoggedIn: Boolean,
        lastNotifiedDate: String,
        today: String,
        currentHour: Int,
        reminderHour: Int,
        upcoming: List<UpcomingAiring>,
    ): List<UpcomingAiring> {
        if (!enabled) return emptyList()
        if (!isLoggedIn) return emptyList()
        // 未到用户设定的提醒时刻（Worker 每小时触发，早于设定时刻的触发静默跳过）
        if (currentHour < reminderHour) return emptyList()
        // 每日去重：同一天只提醒一次
        if (lastNotifiedDate == today) return emptyList()
        return upcoming
    }

    /**
     * 开播前提醒：挑选已进入提前窗口（0 < 距开播 ≤ leadMinutes）且当日尚未提醒过的单集。
     * 不受每日提醒时刻约束——临近开播的时间敏感提醒需要实时发出。
     */
    fun pickPreAir(
        enabled: Boolean,
        isLoggedIn: Boolean,
        notifiedKeys: List<String>,
        today: String,
        nowEpochMillis: Long,
        leadMinutes: Long,
        upcoming: List<UpcomingAiring>,
    ): List<UpcomingAiring> {
        if (!enabled) return emptyList()
        if (!isLoggedIn) return emptyList()
        val notifiedToday = notifiedKeys.filter { it.startsWith("$today:") }.toSet()
        return upcoming.filter { item ->
            val airAt = runCatching { TimeUtils.epochMillisOfIso(item.airAtUtc) }.getOrNull()
            val deltaMillis = airAt?.let { it - nowEpochMillis } ?: 0L
            deltaMillis in 1..leadMinutes * 60_000L &&
                preAirKey(today, item) !in notifiedToday
        }
    }

    /** 逐集提醒去重键；日期前缀让读取侧可以天然裁剪掉过期键 */
    fun preAirKey(
        today: String,
        item: UpcomingAiring,
    ): String = "$today:${item.subjectId}:${item.episode}"
}
