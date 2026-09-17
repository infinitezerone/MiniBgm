package com.infinitezerone.minibgm.feature.widget

import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ScheduleWidgetPlannerTest {
    private val zoneId = ZoneId.of("Asia/Shanghai") // UTC+8

    // 基准时间：2026-09-07 12:00:00 CST (周一) -> 2026-09-07T04:00:00Z
    private val nowEpochMillis = 1788753600000L

    private val todayEpochDay = LocalDate.of(2026, 9, 7).toEpochDay()

    @Test
    fun plan_notLoggedIn_emptyCalendar_reflectsState() {
        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = false,
                upcoming = emptyList(),
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        assertFalse(state.isLoggedIn)
        assertTrue(state.orderedItems.isEmpty())
        assertEquals(todayEpochDay, state.todayEpochDay)
    }

    @Test
    fun plan_splitsSectionsAndBuildsStatusLines() {
        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = true,
                hasTrackedSubjects = true,
                upcoming =
                    listOf(
                        UpcomingAiring(
                            subjectId = 1001L,
                            title = "葬送のフリーレン",
                            titleCn = "葬送的芙莉莲",
                            episode = 28,
                            airAtUtc = "2026-09-07T04:30:00Z", // 12:30 CST (+30m，今天)
                            kind = AirEventKind.SCHEDULED,
                            coverUrl = "https://example.com/cover1.jpg",
                        ),
                        UpcomingAiring(
                            subjectId = 1002L,
                            title = "Re:ゼロから始める異世界生活",
                            titleCn = "Re:从零开始的异世界生活",
                            episode = 4,
                            airAtUtc = "2026-09-07T07:00:00Z", // 15:00 CST (+3h，今天)
                            kind = AirEventKind.PREDICTED,
                        ),
                        UpcomingAiring(
                            subjectId = 1003L,
                            title = "ダンジョン饭",
                            titleCn = "迷宫饭",
                            episode = 12,
                            airAtUtc = "2026-09-07T17:30:00Z", // 2026-09-08 01:30 CST (明天)
                            kind = AirEventKind.ACTUAL,
                        ),
                    ),
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        // 今日待播区：按时间升序，状态为本地时刻 + 原始可信度
        assertEquals(listOf(1001L, 1002L), state.upcomingToday.map { it.subjectId })
        val item1 = state.upcomingToday[0]
        assertEquals(AirStatusLine.TodayAt("12:30"), item1.status)
        assertFalse(item1.isWatchable)
        assertEquals(AirEventKind.SCHEDULED, item1.airKind)
        assertEquals("https://example.com/cover1.jpg", item1.coverUrl)
        assertEquals(AirStatusLine.TodayAt("15:00"), state.upcomingToday[1].status)
        assertEquals(AirEventKind.PREDICTED, state.upcomingToday[1].airKind)

        // 明天归入「接下来」
        val item3 = state.later.single()
        assertEquals(1003L, item3.subjectId)
        assertEquals(AirStatusLine.Upcoming(AirDay.Tomorrow, "01:30"), item3.status)
        assertEquals(AirEventKind.ACTUAL, item3.airKind)

        // 展示优先级：可看 -> 今日待播 -> 之后
        assertEquals(listOf(1001L, 1002L, 1003L), state.orderedItems.map { it.subjectId })
        assertEquals(2, state.totalToday)
        assertEquals(3, state.totalWeek)
        assertEquals(todayEpochDay, state.todayEpochDay)
    }

    @Test
    fun plan_airedToday_isWatchableWithAiredTodayStatus() {
        // 12:00 CST，剧集在 09:00 CST 已播（-3小时，今天）
        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = true,
                hasTrackedSubjects = true,
                upcoming =
                    listOf(
                        UpcomingAiring(
                            subjectId = 2001L,
                            title = "早间动画",
                            titleCn = "早间动画",
                            episode = 10,
                            airAtUtc = "2026-09-07T01:00:00Z", // 09:00 CST
                            kind = AirEventKind.ACTUAL,
                        ),
                    ),
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        val item = state.watchable.single()
        assertTrue(item.isWatchable)
        assertEquals(AirStatusLine.AiredToday, item.status)
        assertEquals(1, state.totalToday)
    }

    @Test
    fun plan_multipleAired_mostRecentFirst() {
        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = true,
                hasTrackedSubjects = true,
                upcoming =
                    listOf(
                        UpcomingAiring(
                            subjectId = 1L,
                            title = "A",
                            titleCn = "A",
                            episode = 1,
                            airAtUtc = "2026-09-06T23:00:00Z", // 07:00 CST，3 小时前
                            kind = AirEventKind.ACTUAL,
                        ),
                        UpcomingAiring(
                            subjectId = 2L,
                            title = "B",
                            titleCn = "B",
                            episode = 2,
                            airAtUtc = "2026-09-07T01:00:00Z", // 09:00 CST，1 小时前
                            kind = AirEventKind.ACTUAL,
                        ),
                    ),
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        assertEquals(listOf(2L, 1L), state.watchable.map { it.subjectId })
    }

    @Test
    fun plan_airedYesterdayWithinLookback_isWatchableWithYesterdayStatus() {
        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = true,
                hasTrackedSubjects = true,
                upcoming =
                    listOf(
                        UpcomingAiring(
                            subjectId = 3001L,
                            title = "昨晚动画",
                            titleCn = "昨晚动画",
                            episode = 5,
                            airAtUtc = "2026-09-06T12:00:00Z", // 昨天 20:00 CST（-16h，在 18h lookback 内）
                            kind = AirEventKind.ACTUAL,
                        ),
                    ),
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        // lookback 窗口内已播出的都算「现在可看」，跨天临界保留「昨天」指代
        val item = state.watchable.single()
        assertTrue(item.isWatchable)
        assertEquals(AirStatusLine.Aired(AirDay.Yesterday, "20:00"), item.status)
    }

    @Test
    fun plan_sectionPriorities_watchableFirst() {
        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = true,
                hasTrackedSubjects = true,
                upcoming =
                    listOf(
                        // 今天早晨已播 (09:00 CST)
                        UpcomingAiring(
                            subjectId = 1L,
                            title = "早间已播",
                            titleCn = "早间已播",
                            episode = 1,
                            airAtUtc = "2026-09-07T01:00:00Z",
                            kind = AirEventKind.ACTUAL,
                        ),
                        // 今天下午待播 (15:00 CST)
                        UpcomingAiring(
                            subjectId = 2L,
                            title = "下午待播",
                            titleCn = "下午待播",
                            episode = 2,
                            airAtUtc = "2026-09-07T07:00:00Z",
                            kind = AirEventKind.ACTUAL,
                        ),
                        // 明天凌晨 (01:00 CST)
                        UpcomingAiring(
                            subjectId = 3L,
                            title = "明天凌晨",
                            titleCn = "明天凌晨",
                            episode = 3,
                            airAtUtc = "2026-09-07T17:00:00Z",
                            kind = AirEventKind.ACTUAL,
                        ),
                    ),
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        assertEquals(listOf(1L), state.watchable.map { it.subjectId })
        assertEquals(listOf(2L), state.upcomingToday.map { it.subjectId })
        assertEquals(listOf(3L), state.later.map { it.subjectId })
        assertEquals(listOf(1L, 2L, 3L), state.orderedItems.map { it.subjectId })
    }

    @Test
    fun plan_loggedInWithTracked_doesNotPolluteWithPublicCalendar() {
        val tracked =
            listOf(
                UpcomingAiring(
                    subjectId = 101L,
                    title = "在看番",
                    titleCn = "在看番",
                    episode = 5,
                    airAtUtc = "2026-09-07T06:00:00Z", // 14:00 CST
                    kind = AirEventKind.SCHEDULED,
                ),
            )
        val todaySchedules =
            listOf(
                AirSchedule(bgmId = 101L, title = "在看番", titleCn = "在看番", timeCst = "14:00"),
                AirSchedule(bgmId = 102L, title = "热门新番A", titleCn = "热门新番A", timeCst = "18:00", nextEpisodeNumber = 6),
                AirSchedule(bgmId = 103L, title = "热门新番B", titleCn = "热门新番B", timeCst = "23:00", nextEpisodeNumber = 8),
            )

        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = true,
                hasTrackedSubjects = true,
                upcoming = tracked,
                todaySchedules = todaySchedules,
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        // 核心原则：登录用户坚决只展示自己的追番（1 部），不掺公共日历里的陌生番剧
        assertEquals(1, state.orderedItems.size)
        assertEquals(101L, state.orderedItems[0].subjectId)
        assertTrue(state.orderedItems[0].isTracked)
        assertEquals(AirStatusLine.TodayAt("14:00"), state.orderedItems[0].status)
    }

    @Test
    fun plan_loggedInWithoutTracked_showsEmptyInsteadOfPublicCalendar() {
        val todaySchedules =
            listOf(
                AirSchedule(bgmId = 102L, title = "热门新番A", titleCn = "热门新番A", timeCst = "18:00", nextEpisodeNumber = 6),
            )

        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = true,
                hasTrackedSubjects = false,
                upcoming = emptyList(),
                todaySchedules = todaySchedules,
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        // 登录用户视野内绝不掺入陌生番剧：无追番更新时返回空态，由占位视图引导
        assertTrue(state.orderedItems.isEmpty())
        assertFalse(state.hasTrackedSubjects)
    }

    @Test
    fun plan_loggedInTrackedOnlyLaterWeek_answersNextAiring() {
        // 唯一追番在 3 天后（周三 11:00 CST）：widget 应跨天回答「下一部」而不是落入空态
        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = true,
                hasTrackedSubjects = true,
                upcoming =
                    listOf(
                        UpcomingAiring(
                            subjectId = 301L,
                            title = "周四番",
                            titleCn = "周四番",
                            episode = 7,
                            airAtUtc = "2026-09-09T03:00:00Z", // 2026-09-09 11:00 CST（周三，距现在 47 小时）
                            kind = AirEventKind.SCHEDULED,
                        ),
                    ),
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        val item = state.later.single()
        assertEquals(301L, item.subjectId)
        assertEquals(AirStatusLine.Upcoming(AirDay.Weekday(3), "11:00"), item.status)
        assertFalse(item.isWatchable)
        assertTrue(state.hasTrackedSubjects)
    }

    @Test
    fun plan_unknownAirTime_trackedFallsBackToWatching_andSortsLast() {
        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = true,
                hasTrackedSubjects = true,
                upcoming =
                    listOf(
                        UpcomingAiring(
                            subjectId = 401L,
                            title = "无排期番",
                            titleCn = "无排期番",
                            episode = 3,
                            airAtUtc = "not-a-time",
                            kind = AirEventKind.PREDICTED,
                        ),
                        UpcomingAiring(
                            subjectId = 402L,
                            title = "明天番",
                            titleCn = "明天番",
                            episode = 4,
                            airAtUtc = "2026-09-07T17:30:00Z", // 明天 01:30 CST
                            kind = AirEventKind.ACTUAL,
                        ),
                    ),
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        // 无精确时刻的「在看」条目垫底，排在有明确排期的条目之后
        assertEquals(listOf(402L, 401L), state.later.map { it.subjectId })
        assertEquals(AirStatusLine.Watching, state.later[1].status)
        assertFalse(state.later[1].isWatchable)
    }

    @Test
    fun plan_notLoggedIn_showsTodaySchedules() {
        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = false,
                upcoming = emptyList(),
                todaySchedules =
                    listOf(
                        AirSchedule(bgmId = 501L, title = "新番一", titleCn = "新番一", timeCst = "20:00", nextEpisodeNumber = 1),
                    ),
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        val item = state.upcomingToday.single()
        assertEquals(501L, item.subjectId)
        assertFalse(item.isTracked)
        assertEquals(AirStatusLine.TodayAt("20:00"), item.status)
    }

    @Test
    fun plan_notLoggedIn_passedTimeCst_isWatchable() {
        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = false,
                upcoming = emptyList(),
                todaySchedules =
                    listOf(
                        AirSchedule(bgmId = 601L, title = "晨间新番", titleCn = "晨间新番", timeCst = "09:00", nextEpisodeNumber = 2),
                    ),
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        val item = state.watchable.single()
        assertEquals(601L, item.subjectId)
        assertTrue(item.isWatchable)
        assertEquals(AirStatusLine.AiredToday, item.status)
    }

    @Test
    fun plan_notLoggedIn_unknownTime_fallsBackToOnAir() {
        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = false,
                upcoming = emptyList(),
                todaySchedules =
                    listOf(
                        AirSchedule(bgmId = 701L, title = "时刻未知", titleCn = "时刻未知", timeCst = "", nextEpisodeNumber = 0),
                    ),
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        val item = state.later.single()
        assertEquals(701L, item.subjectId)
        assertEquals(AirStatusLine.OnAir, item.status)
    }
}
