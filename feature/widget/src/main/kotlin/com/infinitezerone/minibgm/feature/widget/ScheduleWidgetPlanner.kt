package com.infinitezerone.minibgm.feature.widget

import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ScheduleWidgetPlanner {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private data class AiringMeta(
        val subtitle: String,
        val badge: String,
        val isAiredToday: Boolean,
        val diffMillis: Long,
        val isToday: Boolean,
    )

    private data class RankedItem(
        val model: ScheduleWidgetItemUiModel,
        val diffMillis: Long,
        val isToday: Boolean,
    )

    fun plan(
        isLoggedIn: Boolean,
        upcoming: List<UpcomingAiring>,
        todaySchedules: List<AirSchedule> = emptyList(),
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        maxItems: Int = 6,
    ): ScheduleWidgetUiState {
        val nowInstant = Instant.ofEpochMilli(nowEpochMillis)
        val nowZoned = nowInstant.atZone(zoneId)
        val today = nowZoned.toLocalDate()
        val tomorrow = today.plusDays(1)

        // 1. 处理追番条目 (upcoming)
        val rankedTracked =
            upcoming.map { item ->
                val airInstant = runCatching { Instant.parse(item.airAtUtc) }.getOrNull()
                val meta =
                    if (airInstant != null) {
                        val airZoned = airInstant.atZone(zoneId)
                        val airDate = airZoned.toLocalDate()
                        val diff = airInstant.toEpochMilli() - nowEpochMillis
                        val todayFlag = (airDate == today)
                        val tomorrowFlag = (airDate == tomorrow)
                        val airedFlag = todayFlag && (diff <= 0)
                        val dayPrefix =
                            when {
                                tomorrowFlag -> "明天 "
                                else -> ""
                            }
                        val timeStr = airZoned.format(timeFormatter)
                        val epStr = if (item.episode > 0) "第${item.episode}集 · " else ""
                        val sub = "$epStr$dayPrefix$timeStr"
                        val b = formatCountdown(diff, isToday = todayFlag)
                        Triple(sub, b, timeStr) to Pair(airedFlag, diff)
                    } else {
                        val sub = if (item.episode > 0) "第${item.episode}集" else "更新中"
                        Triple(sub, "在看", "") to Pair(false, Long.MAX_VALUE)
                    }

                RankedItem(
                    model =
                        ScheduleWidgetItemUiModel(
                            subjectId = item.subjectId,
                            title = item.displayName,
                            episode = item.episode,
                            episodeSubtitle = meta.first.first,
                            countdownBadge = meta.first.second,
                            coverUrl = item.coverUrl,
                            kindTag = mapKindTag(item.kind),
                            isTracked = true,
                            isAiredToday = meta.second.first,
                            airTimeCst = meta.first.third,
                        ),
                    diffMillis = meta.second.second,
                    isToday = airInstant?.atZone(zoneId)?.toLocalDate() == today,
                )
            }

        // 智能排序：今日即将播出 (diff > 0 升序) -> 今日已播 (diff <= 0 降序，刚播的在前) -> 明天/后续 (升序)
        val upcomingToday = rankedTracked.filter { it.isToday && it.diffMillis > 0 }.sortedBy { it.diffMillis }
        val airedToday = rankedTracked.filter { it.isToday && it.diffMillis <= 0 }.sortedByDescending { it.diffMillis }
        val upcomingFuture = rankedTracked.filter { !it.isToday && it.diffMillis > 0 }.sortedBy { it.diffMillis }
        val otherTracked = rankedTracked.filter { !it.isToday && it.diffMillis <= 0 }.sortedByDescending { it.diffMillis }

        val sortedTracked = (upcomingToday + airedToday + upcomingFuture + otherTracked).map { it.model }

        // 今日全局日历备选项
        val trackedSubjectIds = sortedTracked.map { it.subjectId }.toSet()
        val fallbackItems =
            todaySchedules
                .filter { it.bgmId !in trackedSubjectIds }
                .map { schedule ->
                    val airInstant = runCatching { Instant.parse(schedule.nextEpisodeAtUtc) }.getOrNull()
                    val (sub, badge, aired, diff, timeStr) =
                        if (airInstant != null) {
                            val airZoned = airInstant.atZone(zoneId)
                            val d = airInstant.toEpochMilli() - nowEpochMillis
                            val t = airZoned.format(timeFormatter)
                            val epStr = if (schedule.nextEpisodeNumber > 0) "第${schedule.nextEpisodeNumber}集 · " else ""
                            val s = "$epStr$t"
                            val b = formatCountdown(d, isToday = true)
                            AiringQuad(s, b, d <= 0, d, t)
                        } else if (schedule.timeCst.isNotBlank()) {
                            val epStr = if (schedule.nextEpisodeNumber > 0) "第${schedule.nextEpisodeNumber}集 · " else ""
                            val s = "$epStr${schedule.timeCst}"
                            val parsedTime = runCatching { LocalTime.parse(schedule.timeCst) }.getOrNull()
                            val d =
                                if (parsedTime != null) {
                                    val scheduleZoned = today.atTime(parsedTime).atZone(zoneId)
                                    scheduleZoned.toInstant().toEpochMilli() - nowEpochMillis
                                } else {
                                    Long.MAX_VALUE
                                }
                            val b =
                                if (d != Long.MAX_VALUE) {
                                    formatCountdown(d, isToday = true)
                                } else {
                                    schedule.timeCst
                                }
                            AiringQuad(s, b, d <= 0, d, schedule.timeCst)
                        } else {
                            val epStr = if (schedule.nextEpisodeNumber > 0) "第${schedule.nextEpisodeNumber}集" else "放送中"
                            AiringQuad(epStr, "新番", false, Long.MAX_VALUE, "")
                        }

                    RankedItem(
                        model =
                            ScheduleWidgetItemUiModel(
                                subjectId = schedule.bgmId,
                                title = schedule.titleCn.ifBlank { schedule.title },
                                episode = schedule.nextEpisodeNumber,
                                episodeSubtitle = sub,
                                countdownBadge = badge,
                                coverUrl = schedule.coverUrl,
                                kindTag = mapKindTag(schedule.nextEpisodeKind),
                                isTracked = false,
                                isAiredToday = aired,
                                airTimeCst = timeStr,
                            ),
                        diffMillis = diff,
                        isToday = true,
                    )
                }.sortedWith(
                    compareBy(
                        { if (it.diffMillis > 0) 0 else 1 },
                        { if (it.diffMillis > 0) it.diffMillis else -it.diffMillis },
                    ),
                ).map { it.model }

        // 核心设计原则：用户已登录且有追番时，坚决只展示用户自己的追番列表，杜绝掺入不相干陌生番剧
        val allItems =
            if (isLoggedIn) {
                if (sortedTracked.isNotEmpty()) {
                    sortedTracked.take(maxItems)
                } else {
                    // 今日无在看更新，以今日新番日历作为参考
                    fallbackItems.take(maxItems)
                }
            } else {
                fallbackItems.take(maxItems)
            }

        val hasTracked = sortedTracked.isNotEmpty()
        val headerTitle =
            when {
                hasTracked -> "今日追番"
                isLoggedIn -> "今日新番日历"
                else -> "今日新番日历"
            }

        val weekdayCn =
            when (nowZoned.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "周一"
                java.time.DayOfWeek.TUESDAY -> "周二"
                java.time.DayOfWeek.WEDNESDAY -> "周三"
                java.time.DayOfWeek.THURSDAY -> "周四"
                java.time.DayOfWeek.FRIDAY -> "周五"
                java.time.DayOfWeek.SATURDAY -> "周六"
                java.time.DayOfWeek.SUNDAY -> "周日"
            }
        val formattedDate = "${nowZoned.monthValue}月${nowZoned.dayOfMonth}日"
        val headerSubtitle = "$weekdayCn · $formattedDate"
        val formattedTime = nowZoned.format(timeFormatter)

        return ScheduleWidgetUiState(
            isLoggedIn = isLoggedIn,
            items = allItems,
            formattedUpdateTime = formattedTime,
            headerTitle = headerTitle,
            headerSubtitle = headerSubtitle,
            hasTrackedItems = hasTracked,
        )
    }

    private data class AiringQuad(
        val sub: String,
        val badge: String,
        val aired: Boolean,
        val diff: Long,
        val time: String,
    )

    fun formatCountdown(
        diffMillis: Long,
        isToday: Boolean = false,
    ): String =
        when {
            diffMillis <= -2 * 60 * 60 * 1000L -> if (isToday) "已更新" else "已开播"
            diffMillis < 0L -> "刚刚开播"
            diffMillis < 60 * 60 * 1000L -> {
                val minutes = maxOf(1L, diffMillis / (60 * 1000L))
                "${minutes}分钟后"
            }
            diffMillis < 24 * 60 * 60 * 1000L -> {
                val hours = diffMillis / (60 * 60 * 1000L)
                if (hours < 1L) "1小时内" else "${hours}小时后"
            }
            else -> "明天"
        }

    fun mapKindTag(kind: String): String? =
        when (kind) {
            AirEventKind.PREDICTED -> "预估"
            AirEventKind.SCHEDULED -> "表定"
            else -> null
        }
}
