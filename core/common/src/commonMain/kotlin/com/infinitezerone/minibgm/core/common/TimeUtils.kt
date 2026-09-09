package com.infinitezerone.minibgm.core.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

object TimeUtils {
    private val timeZoneCst = TimeZone.of("Asia/Shanghai")
    private val timeZoneJst = TimeZone.of("Asia/Tokyo")

    fun formatToCstTime(isoUtcString: String): String =
        try {
            val instant = Instant.parse(isoUtcString)
            val local = instant.toLocalDateTime(timeZoneCst)
            val hourStr = local.hour.toString().padStart(2, '0')
            val minStr = local.minute.toString().padStart(2, '0')
            "$hourStr:$minStr"
        } catch (_: Exception) {
            ""
        }

    fun formatToJstTime(isoUtcString: String): String =
        try {
            val instant = Instant.parse(isoUtcString)
            val local = instant.toLocalDateTime(timeZoneJst)
            val hourStr = local.hour.toString().padStart(2, '0')
            val minStr = local.minute.toString().padStart(2, '0')
            "$hourStr:$minStr"
        } catch (_: Exception) {
            ""
        }

    /** epoch 毫秒 → 日本时区的星期（1=周一 … 7=周日）；越界异常值回退为当前时刻的星期 */
    fun jstWeekdayOfEpoch(millis: Long): Int = weekdayOfEpoch(millis, timeZoneJst)

    /** epoch 毫秒 → 中国时区的星期（1=周一 … 7=周日）；越界异常值回退为当前时刻的星期 */
    fun cstWeekdayOfEpoch(millis: Long): Int = weekdayOfEpoch(millis, timeZoneCst)

    private fun weekdayOfEpoch(
        millis: Long,
        timeZone: TimeZone,
    ): Int {
        val instant =
            try {
                Instant.fromEpochMilliseconds(millis)
            } catch (_: Exception) {
                // 回退到当前时刻而非固定值，避免解析失败被误读为"周一"
                Instant.fromEpochMilliseconds(nowEpochMillis())
            }
        return instant.toLocalDateTime(timeZone).dayOfWeek.ordinal + 1
    }

    /** epoch 毫秒 → UTC ISO-8601 字符串 */
    fun isoUtcFromEpochMillis(millis: Long): String = Instant.fromEpochMilliseconds(millis).toString()

    /** 解析 UTC ISO-8601 字符串为 epoch 毫秒，失败返回 null */
    fun epochMillisOfIso(isoUtcString: String): Long? =
        try {
            Instant.parse(isoUtcString).toEpochMilliseconds()
        } catch (_: Exception) {
            null
        }

    /**
     * 解析 bangumi-data 的周期播出规则（ISO 8601 重复区间，如 "R/2026-08-12T14:00:00.000Z/P7D"）。
     * 支持 R[n]/起始时刻/周期的形式，周期单位支持 D（天）与 W（周）。
     * @return (起始时刻 epoch 毫秒, 周期毫秒)，无法解析时返回 null
     */
    fun parseBroadcastRule(rule: String): Pair<Long, Long>? {
        if (rule.isBlank()) return null
        return try {
            val segments = rule.split("/")
            if (segments.size < 3) return null
            val startMillis = Instant.parse(segments[1]).toEpochMilliseconds()
            val periodPart = segments.last().removePrefix("P").uppercase()
            val days =
                Regex("(\\d+)D")
                    .find(periodPart)
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull()
            val weeks =
                Regex("(\\d+)W")
                    .find(periodPart)
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull()
            val dayMillis = 24L * 60 * 60 * 1000
            val periodMillis =
                when {
                    days != null -> days * dayMillis
                    weeks != null -> weeks * 7 * dayMillis
                    else -> return null
                }
            if (periodMillis <= 0L) null else startMillis to periodMillis
        } catch (_: Exception) {
            null
        }
    }

    fun nowEpochMillis(): Long =
        Clock.System
            .now()
            .toEpochMilliseconds()

    /**
     * 根据开播日期（如 "2026-07-02" 或 "2026-07-02T15:00:00.000Z"）计算当前当周所播话数
     */
    fun calculateCurrentEpisode(startDateStr: String): Int {
        if (startDateStr.isBlank()) return 0
        return try {
            val dateStr = startDateStr.substringBefore("T").trim()
            val startDate = LocalDate.parse(dateStr)
            val today =
                Clock.System
                    .now()
                    .toLocalDateTime(timeZoneCst)
                    .date
            val days = startDate.daysUntil(today)
            if (days < 0) {
                1
            } else {
                (days / 7) + 1
            }
        } catch (_: Exception) {
            0
        }
    }

    /**
     * 将秒级时间戳格式化为本地日期字符串 (yyyy-MM-dd)
     */
    fun formatEpochSecondsToDate(epochSeconds: Long): String {
        if (epochSeconds <= 0) return ""
        return try {
            val instant = Instant.fromEpochSeconds(epochSeconds)
            val local = instant.toLocalDateTime(timeZoneCst)
            @Suppress("DEPRECATION")
            "${local.year}-${local.monthNumber.toString().padStart(2, '0')}-${local.dayOfMonth.toString().padStart(2, '0')}"
        } catch (_: Exception) {
            ""
        }
    }

    fun currentCstYearMonth(): Pair<Int, Int> {
        val local = Clock.System.now().toLocalDateTime(timeZoneCst)
        return local.year to local.monthNumber
    }
}
