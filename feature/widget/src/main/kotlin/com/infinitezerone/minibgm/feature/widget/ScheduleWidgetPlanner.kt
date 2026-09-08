package com.infinitezerone.minibgm.feature.widget

import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 把播出事件规划成「状态分区」的小组件展示态。
 *
 * 信息主体是状态而非时刻表：WATCHABLE（现在可看）-> UPCOMING_TODAY（今天待播）-> LATER（未来 7 天），
 * 直接回答「我现在有什么可看」。分区列表不截断，计数即真实值，展示条数由 UI 层按尺寸自行取舍。
 * 本类零用户文案：状态输出结构化 [AirStatusLine]，文案由 UI 按语言资源渲染。
 */
object ScheduleWidgetPlanner {
    /** HH:mm 数据格式（非文案） */
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private data class RankedEntry(
        val model: ScheduleWidgetItemUiModel,
        val section: ScheduleWidgetSection,
        val diffMillis: Long,
    )

    fun plan(
        isLoggedIn: Boolean,
        hasTrackedSubjects: Boolean = false,
        upcoming: List<UpcomingAiring>,
        todaySchedules: List<AirSchedule> = emptyList(),
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ScheduleWidgetUiState {
        val nowZoned = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId)
        val today = nowZoned.toLocalDate()
        val tomorrow = today.plusDays(1)

        val entries =
            if (isLoggedIn) {
                // 内容模型：widget 回答「我追的怎么样了」——登录用户只看追番，绝不掺入公共日历的陌生番剧
                upcoming.map { item ->
                    buildEntry(
                        subjectId = item.subjectId,
                        title = item.displayName,
                        episode = item.episode,
                        airKind = item.kind,
                        isTracked = true,
                        coverUrl = item.coverUrl,
                        airAtUtc = item.airAtUtc,
                        fallbackTimeCst = "",
                        unknownTimeStatus = AirStatusLine.Watching,
                        nowEpochMillis = nowEpochMillis,
                        today = today,
                        tomorrow = tomorrow,
                        zoneId = zoneId,
                    )
                }
            } else {
                // 未登录的获客面：今日公共新番日历
                todaySchedules.map { schedule ->
                    buildEntry(
                        subjectId = schedule.bgmId,
                        title = schedule.titleCn.ifBlank { schedule.title },
                        episode = schedule.nextEpisodeNumber,
                        airKind = schedule.nextEpisodeKind,
                        isTracked = false,
                        coverUrl = schedule.coverUrl,
                        airAtUtc = schedule.nextEpisodeAtUtc,
                        fallbackTimeCst = schedule.timeCst,
                        unknownTimeStatus = AirStatusLine.OnAir,
                        nowEpochMillis = nowEpochMillis,
                        today = today,
                        tomorrow = tomorrow,
                        zoneId = zoneId,
                    )
                }
            }

        val watchable =
            entries
                .filter { it.section == ScheduleWidgetSection.WATCHABLE }
                .sortedByDescending { it.diffMillis }
        val upcomingToday =
            entries
                .filter { it.section == ScheduleWidgetSection.UPCOMING_TODAY }
                .sortedBy { it.diffMillis }
        val later =
            entries
                .filter { it.section == ScheduleWidgetSection.LATER }
                .sortedWith(compareBy({ it.diffMillis == Long.MAX_VALUE }, { it.diffMillis }))

        return ScheduleWidgetUiState(
            isLoggedIn = isLoggedIn,
            hasTrackedSubjects = hasTrackedSubjects,
            todayEpochDay = today.toEpochDay(),
            watchable = watchable.map { it.model },
            upcomingToday = upcomingToday.map { it.model },
            later = later.map { it.model },
        )
    }

    private fun buildEntry(
        subjectId: Long,
        title: String,
        episode: Int,
        airKind: String?,
        isTracked: Boolean,
        coverUrl: String,
        airAtUtc: String,
        fallbackTimeCst: String,
        unknownTimeStatus: AirStatusLine,
        nowEpochMillis: Long,
        today: LocalDate,
        tomorrow: LocalDate,
        zoneId: ZoneId,
    ): RankedEntry {
        fun entry(
            status: AirStatusLine,
            section: ScheduleWidgetSection,
            diffMillis: Long,
        ) = RankedEntry(
            ScheduleWidgetItemUiModel(
                subjectId = subjectId,
                title = title,
                episode = episode,
                status = status,
                airKind = airKind,
                coverUrl = coverUrl,
                isTracked = isTracked,
            ),
            section,
            diffMillis,
        )

        val airInstant = runCatching { Instant.parse(airAtUtc) }.getOrNull()
        if (airInstant == null) {
            // 无精确时刻：未登录条目回退到日历表定时间；追番条目标记「在看」垫底
            val parsedTime = runCatching { LocalTime.parse(fallbackTimeCst) }.getOrNull()
            return if (parsedTime != null) {
                val diff =
                    today
                        .atTime(parsedTime)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli() - nowEpochMillis
                if (diff <= 0) {
                    entry(AirStatusLine.AiredToday, ScheduleWidgetSection.WATCHABLE, diff)
                } else {
                    entry(AirStatusLine.TodayAt(fallbackTimeCst), ScheduleWidgetSection.UPCOMING_TODAY, diff)
                }
            } else {
                entry(unknownTimeStatus, ScheduleWidgetSection.LATER, Long.MAX_VALUE)
            }
        }

        val airZoned = airInstant.atZone(zoneId)
        val diff = airInstant.toEpochMilli() - nowEpochMillis
        val isToday = airZoned.toLocalDate() == today
        val isYesterday = airZoned.toLocalDate() == today.minusDays(1)
        val timeStr = airZoned.format(timeFormatter)
        return when {
            // lookback 窗口内已播出的都算「现在可看」，含跨天临界（昨天深夜播的仍是最新可看话数）
            diff <= 0 ->
                entry(
                    status =
                        when {
                            isToday -> AirStatusLine.AiredToday
                            isYesterday -> AirStatusLine.Aired(AirDay.Yesterday, timeStr)
                            else -> AirStatusLine.Aired(AirDay.Weekday(airZoned.dayOfWeek.value), timeStr)
                        },
                    section = ScheduleWidgetSection.WATCHABLE,
                    diffMillis = diff,
                )
            isToday -> entry(AirStatusLine.TodayAt(timeStr), ScheduleWidgetSection.UPCOMING_TODAY, diff)
            airZoned.toLocalDate() == tomorrow ->
                entry(AirStatusLine.Upcoming(AirDay.Tomorrow, timeStr), ScheduleWidgetSection.LATER, diff)
            else ->
                entry(
                    AirStatusLine.Upcoming(AirDay.Weekday(airZoned.dayOfWeek.value), timeStr),
                    ScheduleWidgetSection.LATER,
                    diff,
                )
        }
    }
}
