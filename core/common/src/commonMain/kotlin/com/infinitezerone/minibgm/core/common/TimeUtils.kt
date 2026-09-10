package com.infinitezerone.minibgm.core.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
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

    /** 解析 UTC ISO-8601 字符串或 yyyy-MM-dd 日期为 epoch 毫秒，失败返回 null */
    fun epochMillisOfIso(isoUtcString: String): Long? =
        try {
            Instant.parse(isoUtcString).toEpochMilliseconds()
        } catch (_: Exception) {
            try {
                val dateStr = isoUtcString.substringBefore("T").trim()
                LocalDate.parse(dateStr).atStartOfDayIn(timeZoneCst).toEpochMilliseconds()
            } catch (_: Exception) {
                null
            }
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
            instant.toLocalDateTime(timeZoneCst).date.toString()
        } catch (_: Exception) {
            ""
        }
    }

    fun currentCstYearMonth(): Pair<Int, Int> {
        val local = Clock.System.now().toLocalDateTime(timeZoneCst)
        return local.year to local.month.number
    }

    /**
     * 将 "HH:mm" 格式时间字符串转换为全天分钟数 (0..1439)；空或非法返回 9999
     */
    fun parseTimeToMinutes(timeStr: String): Int {
        if (timeStr.length != 5 || timeStr[2] != ':') return 9999
        val hour = timeStr.substring(0, 2).toIntOrNull() ?: return 9999
        val minute = timeStr.substring(3, 5).toIntOrNull() ?: return 9999
        if (hour !in 0..23 || minute !in 0..59) return 9999
        return hour * 60 + minute
    }

    /**
     * 规范化 ISO-8601 UTC 时间戳字符串（统一消除 .000 毫秒碎片，使 SQLite 文本排序与时刻绝对一致）。
     * 若解析失败则返回原字符串。
     */
    fun normalizeIsoUtc(isoString: String): String {
        if (isoString.isBlank()) return ""
        return try {
            Instant.parse(isoString).toString()
        } catch (_: Exception) {
            isoString
        }
    }
}
