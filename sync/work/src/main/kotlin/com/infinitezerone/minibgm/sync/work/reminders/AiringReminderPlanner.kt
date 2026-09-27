package com.infinitezerone.minibgm.sync.work.reminders

import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.model.UpcomingAiring

/**
 * 开播提醒的纯决策逻辑：给定偏好与数据，决定本次要提醒哪些更新以及下一次唤醒点。
 * 独立于 Android 通知与 Alarm API，保持 100% 可测。
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
        // 未到用户设定的提醒时刻
        if (currentHour < reminderHour) return emptyList()
        // 每日去重：同一天只提醒一次
        if (lastNotifiedDate == today) return emptyList()
        return upcoming
    }

    /**
     * 开播前提醒：挑选已进入有效窗口且尚未提醒过的单集。
     * 支持播放源延迟偏移与超时容错区间，杜绝因系统休眠调度抖动导致的漏报。
     */
    fun pickPreAir(
        enabled: Boolean,
        isLoggedIn: Boolean,
        notifiedKeys: List<String>,
        today: String,
        nowEpochMillis: Long,
        leadMinutes: Long,
        airDelayOffsetMinutes: Long = 0L,
        graceMinutes: Long = 10L,
        upcoming: List<UpcomingAiring>,
    ): List<UpcomingAiring> {
        if (!enabled) return emptyList()
        if (!isLoggedIn) return emptyList()
        val notifiedContent = notifiedKeys.map { it.substringAfter(':') }.toSet()
        return upcoming.filter { item ->
            val airAt = runCatching { TimeUtils.epochMillisOfIso(item.airAtUtc) }.getOrNull()
            val effectiveAir = airAt?.let { it + airDelayOffsetMinutes * 60_000L }
            val deltaMillis = effectiveAir?.let { it - nowEpochMillis } ?: Long.MIN_VALUE
            deltaMillis in (-graceMinutes * 60_000L)..(leadMinutes * 60_000L) &&
                preAirContentKey(item) !in notifiedContent
        }
    }

    /** 判断某剧集在当前时刻是否已过实际播出点 */
    fun isAlreadyStarted(
        item: UpcomingAiring,
        nowEpochMillis: Long,
        airDelayOffsetMinutes: Long = 0L,
    ): Boolean {
        val airAt = runCatching { TimeUtils.epochMillisOfIso(item.airAtUtc) }.getOrNull() ?: return false
        val effectiveAir = airAt + airDelayOffsetMinutes * 60_000L
        return nowEpochMillis >= effectiveAir
    }

    /**
     * 计算下一个需要为其设置定时闹钟的条目及触发时刻（Next-Up 决策）。
     * 忽略已通知过或已超过容错窗口的剧集。
     *
     * @return Pair<条目, 触发绝对毫秒>，若无即将开播的剧集则返回 null。
     */
    fun nextAiringSchedule(
        notifiedKeys: List<String>,
        nowEpochMillis: Long,
        leadMinutes: Long,
        airDelayOffsetMinutes: Long = 0L,
        graceMinutes: Long = 10L,
        upcoming: List<UpcomingAiring>,
    ): Pair<UpcomingAiring, Long>? {
        val notifiedContent = notifiedKeys.map { it.substringAfter(':') }.toSet()
        return upcoming
            .asSequence()
            .filter { preAirContentKey(it) !in notifiedContent }
            .mapNotNull { item ->
                val airAt = runCatching { TimeUtils.epochMillisOfIso(item.airAtUtc) }.getOrNull() ?: return@mapNotNull null
                val effectiveAir = airAt + airDelayOffsetMinutes * 60_000L
                val targetTrigger = effectiveAir - leadMinutes * 60_000L
                // 若已经超过容错窗口（例如晚了半小时以上），判定为已过期忽略
                if (nowEpochMillis > effectiveAir + graceMinutes * 60_000L) {
                    null
                } else {
                    item to maxOf(nowEpochMillis, targetTrigger)
                }
            }.minByOrNull { it.second }
    }

    /** 逐集提醒存储键；日期前缀让读取侧可以天然裁剪掉过期键 */
    fun preAirKey(
        today: String,
        item: UpcomingAiring,
    ): String = "$today:${preAirContentKey(item)}"

    /** 跨日稳定的去重内容键：同一集（含同话数重播的不同时刻）只提醒一次 */
    fun preAirContentKey(item: UpcomingAiring): String = "${item.subjectId}:${item.episode}:${item.airAtUtc}"
}
