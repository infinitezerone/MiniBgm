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
     * 开播前提醒：挑选已进入提前窗口（0 < 距开播 ≤ leadMinutes）且尚未提醒过的单集。
     * 不受每日提醒时刻约束——临近开播的时间敏感提醒需要实时发出。
     *
     * 去重按内容键（subjectId:episode:airAtUtc）跨日生效：临近午夜开播的剧集在日期
     * 翻转前后各进入一次窗口时不会重复提醒；日期前缀仅用于存储侧按天裁剪过期键。
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
        val notifiedContent = notifiedKeys.map { it.substringAfter(':') }.toSet()
        return upcoming.filter { item ->
            val airAt = runCatching { TimeUtils.epochMillisOfIso(item.airAtUtc) }.getOrNull()
            val deltaMillis = airAt?.let { it - nowEpochMillis } ?: 0L
            deltaMillis in 1..leadMinutes * 60_000L &&
                preAirContentKey(item) !in notifiedContent
        }
    }

    /** 逐集提醒存储键；日期前缀让读取侧可以天然裁剪掉过期键 */
    fun preAirKey(
        today: String,
        item: UpcomingAiring,
    ): String = "$today:${preAirContentKey(item)}"

    /** 跨日稳定的去重内容键：同一集（含同话数重播的不同时刻）只提醒一次 */
    fun preAirContentKey(item: UpcomingAiring): String = "${item.subjectId}:${item.episode}:${item.airAtUtc}"
}
