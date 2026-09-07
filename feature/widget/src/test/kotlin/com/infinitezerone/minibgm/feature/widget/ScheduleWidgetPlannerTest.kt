package com.infinitezerone.minibgm.feature.widget

import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ScheduleWidgetPlannerTest {
    private val zoneId = ZoneId.of("Asia/Shanghai") // UTC+8

    // 基准时间：2026-09-07 12:00:00 CST -> 2026-09-07T04:00:00Z
    private val nowEpochMillis = 1788753600000L

    @Test
    fun plan_notLoggedIn_reflectsState() {
        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = false,
                upcoming = emptyList(),
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        assertFalse(state.isLoggedIn)
        assertTrue(state.items.isEmpty())
        assertEquals("12:00", state.formattedUpdateTime)
        assertEquals("周一 · 9月7日", state.headerSubtitle)
    }

    @Test
    fun plan_mapsUpcomingItemsCorrectly() {
        val items =
            listOf(
                UpcomingAiring(
                    subjectId = 1001L,
                    title = "葬送のフリーレン",
                    titleCn = "葬送的芙莉莲",
                    episode = 28,
                    airAtUtc = "2026-09-07T04:30:00Z", // 12:30 CST (+30m)
                    kind = AirEventKind.SCHEDULED,
                    coverUrl = "https://example.com/cover1.jpg",
                ),
                UpcomingAiring(
                    subjectId = 1002L,
                    title = "Re:ゼロから始める異世界生活",
                    titleCn = "Re:从零开始的异世界生活",
                    episode = 4,
                    airAtUtc = "2026-09-07T07:00:00Z", // 15:00 CST (+3h)
                    kind = AirEventKind.PREDICTED,
                    coverUrl = "https://example.com/cover2.jpg",
                ),
                UpcomingAiring(
                    subjectId = 1003L,
                    title = "ダンジョン饭",
                    titleCn = "迷宫饭",
                    episode = 12,
                    airAtUtc = "2026-09-07T17:30:00Z", // 2026-09-08 01:30 CST (明天)
                    kind = AirEventKind.ACTUAL,
                    coverUrl = "https://example.com/cover3.jpg",
                ),
            )

        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = true,
                upcoming = items,
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        assertTrue(state.isLoggedIn)
        assertEquals(3, state.items.size)

        // Item 1: 30 分钟后
        val item1 = state.items[0]
        assertEquals(1001L, item1.subjectId)
        assertEquals("葬送的芙莉莲", item1.title)
        assertEquals("第28集 · 12:30", item1.episodeSubtitle)
        assertEquals("30分钟后", item1.countdownBadge)
        assertEquals("表定", item1.kindTag)
        assertEquals("https://example.com/cover1.jpg", item1.coverUrl)

        // Item 2: 3 小时后
        val item2 = state.items[1]
        assertEquals("Re:从零开始的异世界生活", item2.title)
        assertEquals("第4集 · 15:00", item2.episodeSubtitle)
        assertEquals("3小时后", item2.countdownBadge)
        assertEquals("预估", item2.kindTag)

        // Item 3: 明天
        val item3 = state.items[2]
        assertEquals("迷宫饭", item3.title)
        assertEquals("第12集 · 明天 01:30", item3.episodeSubtitle)
        assertEquals("13小时后", item3.countdownBadge)
        assertNull(item3.kindTag)
    }

    @Test
    fun plan_airedTodayItems_remainVisibleWithBadge() {
        // 基准时间：12:00 CST，剧集在 09:00 CST 已播（-3小时，今天）
        val items =
            listOf(
                UpcomingAiring(
                    subjectId = 2001L,
                    title = "早间动画",
                    titleCn = "早间动画",
                    episode = 10,
                    airAtUtc = "2026-09-07T01:00:00Z", // 09:00 CST
                    kind = AirEventKind.ACTUAL,
                    coverUrl = "https://example.com/morning.jpg",
                ),
            )

        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = true,
                upcoming = items,
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        assertEquals(1, state.items.size)
        val item = state.items[0]
        assertTrue(item.isAiredToday)
        assertEquals("已更新", item.countdownBadge)
        assertEquals("第10集 · 09:00", item.episodeSubtitle)
    }

    @Test
    fun plan_smartOrdering_prioritizesUpcomingOverAiredAndFuture() {
        val items =
            listOf(
                // 1. 今天早晨已播 (09:00 CST)
                UpcomingAiring(
                    subjectId = 1L,
                    title = "早间已播",
                    titleCn = "早间已播",
                    episode = 1,
                    airAtUtc = "2026-09-07T01:00:00Z",
                    kind = AirEventKind.ACTUAL,
                ),
                // 2. 今天下午即将播 (15:00 CST)
                UpcomingAiring(
                    subjectId = 2L,
                    title = "下午待播",
                    titleCn = "下午待播",
                    episode = 2,
                    airAtUtc = "2026-09-07T07:00:00Z",
                    kind = AirEventKind.ACTUAL,
                ),
                // 3. 明天凌晨播 (01:00 CST)
                UpcomingAiring(
                    subjectId = 3L,
                    title = "明天凌晨",
                    titleCn = "明天凌晨",
                    episode = 3,
                    airAtUtc = "2026-09-07T17:00:00Z",
                    kind = AirEventKind.ACTUAL,
                ),
            )

        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = true,
                upcoming = items,
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        // 预期排序：今日即将播 (15:00) -> 今日已播 (09:00) -> 明天 (01:00)
        assertEquals(3, state.items.size)
        assertEquals(2L, state.items[0].subjectId) // 下午待播置顶
        assertEquals(1L, state.items[1].subjectId) // 早间已播第二
        assertEquals(3L, state.items[2].subjectId) // 明天第三
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
                com.infinitezerone.minibgm.core.model.AirSchedule(
                    bgmId = 101L,
                    title = "在看番",
                    titleCn = "在看番",
                    timeCst = "14:00",
                ),
                com.infinitezerone.minibgm.core.model.AirSchedule(
                    bgmId = 102L,
                    title = "热门新番A",
                    titleCn = "热门新番A",
                    timeCst = "18:00",
                    nextEpisodeNumber = 6,
                ),
                com.infinitezerone.minibgm.core.model.AirSchedule(
                    bgmId = 103L,
                    title = "热门新番B",
                    titleCn = "热门新番B",
                    timeCst = "23:00",
                    nextEpisodeNumber = 8,
                ),
            )

        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = true,
                upcoming = tracked,
                todaySchedules = todaySchedules,
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
                maxItems = 3,
            )

        // 核心原则：用户已登录且有追番时，坚决只展示用户自己的追番列表（1部），不掺杂公共日历中的陌生番剧
        assertEquals(1, state.items.size)
        assertEquals(101L, state.items[0].subjectId)
        assertTrue(state.items[0].isTracked)
        assertEquals("第5集 · 14:00", state.items[0].episodeSubtitle)
        assertEquals("今日追番", state.headerTitle)
    }

    @Test
    fun plan_loggedInWithoutTracked_fallsBackToTodaySchedules() {
        val todaySchedules =
            listOf(
                com.infinitezerone.minibgm.core.model.AirSchedule(
                    bgmId = 102L,
                    title = "热门新番A",
                    titleCn = "热门新番A",
                    timeCst = "18:00",
                    nextEpisodeNumber = 6,
                ),
            )

        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = true,
                upcoming = emptyList(),
                todaySchedules = todaySchedules,
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
                maxItems = 3,
            )

        assertEquals(1, state.items.size)
        assertEquals(102L, state.items[0].subjectId)
        assertFalse(state.items[0].isTracked)
        assertEquals("今日新番日历", state.headerTitle)
    }

    @Test
    fun plan_notLoggedIn_showsTodaySchedules() {
        val todaySchedules =
            listOf(
                com.infinitezerone.minibgm.core.model.AirSchedule(
                    bgmId = 501L,
                    title = "新番一",
                    titleCn = "新番一",
                    timeCst = "20:00",
                    nextEpisodeNumber = 1,
                ),
            )

        val state =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = false,
                upcoming = emptyList(),
                todaySchedules = todaySchedules,
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
            )

        assertFalse(state.isLoggedIn)
        assertEquals(1, state.items.size)
        assertEquals("今日新番日历", state.headerTitle)
        assertEquals(501L, state.items[0].subjectId)
    }

    @Test
    fun formatCountdown_coversAllRanges() {
        // 3小时前播 (非今天)
        assertEquals("已开播", ScheduleWidgetPlanner.formatCountdown(-3 * 3600 * 1000L, isToday = false))
        // 3小时前播 (今天)
        assertEquals("已更新", ScheduleWidgetPlanner.formatCountdown(-3 * 3600 * 1000L, isToday = true))
        // 10分钟前播
        assertEquals("刚刚开播", ScheduleWidgetPlanner.formatCountdown(-10 * 60 * 1000L))
        // 15分钟后
        assertEquals("15分钟后", ScheduleWidgetPlanner.formatCountdown(15 * 60 * 1000L))
        // 45秒后 (minOf 1m)
        assertEquals("1分钟后", ScheduleWidgetPlanner.formatCountdown(45 * 1000L))
        // 2小时后
        assertEquals("2小时后", ScheduleWidgetPlanner.formatCountdown(2 * 3600 * 1000L))
        // 30小时后
        assertEquals("明天", ScheduleWidgetPlanner.formatCountdown(30 * 3600 * 1000L))
    }

    @Test
    fun mapKindTag_arbitratesCorrectly() {
        assertEquals("预估", ScheduleWidgetPlanner.mapKindTag(AirEventKind.PREDICTED))
        assertEquals("表定", ScheduleWidgetPlanner.mapKindTag(AirEventKind.SCHEDULED))
        assertNull(ScheduleWidgetPlanner.mapKindTag(AirEventKind.ACTUAL))
        assertNull(ScheduleWidgetPlanner.mapKindTag("unknown"))
    }
}
