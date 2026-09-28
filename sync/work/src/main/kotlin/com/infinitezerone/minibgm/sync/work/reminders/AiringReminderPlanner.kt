package com.infinitezerone.minibgm.sync.work.reminders

import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import java.time.LocalDate

/**
 * 开播提醒的纯决策逻辑：给定偏好与数据，决定本次要提醒哪些更新以及下一次唤醒点。
 * 独立于 Android 通知与 Alarm API，保持 100% 可测。
 */
object AiringReminderPlanner {
    /**
     * 单集提醒的迟到容错窗口（分钟）。
     *
     * 必须与 Android 对不定时闹钟的调度窗口对齐：`setAndAllowWhileIdle` 在 API 31+
     * 上由系统给出最长 1 小时的投递窗口（实测 `dumpsys alarm` 的 `windowLength=3600000`），
     * 即唤醒时刻可在预约点之后任意漂移。若容错窗口小于该值，系统稍晚唤醒就会让提醒
     * 在 [pickPreAir] / [nextAiringSchedule] 里被判定为过期而静默丢弃——宁可晚到，
     * 不可漏报。
     */
    const val PRE_AIR_GRACE_MINUTES = 60L

    /**
     * 拉取候选开播事件所需的回看时长（小时，向上取整）。
     *
     * 保留判据为 `now <= 开播时刻 + 源延迟偏移 + 容错窗口`，因此查询下界必须一并覆盖
     * 源延迟与容错窗口。否则条目在进入判定之前就被 SQL 的 `airAtUtc >= fromIso`
     * 过滤掉，容错窗口形同虚设——这正是「App 在开播后重新核准（冷启动 / 重装 / 开机 /
     * 改时区）时无法补发刚错过的提醒」的成因。
     */
    fun preAirLookbackHours(airDelayOffsetMinutes: Long): Long {
        val neededMinutes = PRE_AIR_GRACE_MINUTES + airDelayOffsetMinutes.coerceAtLeast(0L)
        return (neededMinutes + 59L) / 60L
    }

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
        graceMinutes: Long = PRE_AIR_GRACE_MINUTES,
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
        graceMinutes: Long = PRE_AIR_GRACE_MINUTES,
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

    /**
     * 裁剪历史去重键，只保留最近 [retainDays] 个自然日写入的键。
     *
     * 容错窗口与源延迟偏移叠加后最长可达数小时，临近午夜开播的剧集可能在日期翻转之后
     * 才被补发；若只保留当期键，同一集会在跨日后被重新判定为「未通知」而重复推送。
     * 日期解析失败时回退为只保留当期键——宁可多留，不可误删导致漏报。
     */
    fun pruneNotifiedKeys(
        existing: List<String>,
        today: String,
        retainDays: Int = NOTIFIED_KEY_RETAIN_DAYS,
    ): List<String> {
        val prefixes =
            runCatching {
                val base = LocalDate.parse(today)
                (0 until retainDays).map { base.minusDays(it.toLong()).toString() + ":" }
            }.getOrElse { listOf("$today:") }
        return existing.filter { key -> prefixes.any(key::startsWith) }
    }

    /** 去重键保留天数：覆盖容错窗口与源延迟叠加后的最大跨日跨度 */
    const val NOTIFIED_KEY_RETAIN_DAYS = 2

    /** 跨日稳定的去重内容键：同一集（含同话数重播的不同时刻）只提醒一次 */
    fun preAirContentKey(item: UpcomingAiring): String = "${item.subjectId}:${item.episode}:${item.airAtUtc}"
}
