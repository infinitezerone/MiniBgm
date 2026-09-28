package com.infinitezerone.minibgm.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/** 追番足迹用 TimeUtils 助手：月份前缀与 ISO 时刻距今天数（CST 日历日口径） */
class TimeUtilsFootprintTest {
    @Test
    fun currentCstMonthPrefix_matchesYearMonthPair() {
        val (year, month) = TimeUtils.currentCstYearMonth()
        assertEquals("%04d-%02d".format(year, month), TimeUtils.currentCstMonthPrefix())
    }

    @Test
    fun daysSinceIsoUtc_countsCalendarDays() {
        val now = Clock.System.now()
        val threeDaysAgoIso =
            Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 3L * 24 * 60 * 60 * 1000).toString()
        assertEquals(3, TimeUtils.daysSinceIsoUtc(threeDaysAgoIso))
    }

    @Test
    fun daysSinceIsoUtc_returnsZeroForNow() {
        assertEquals(0, TimeUtils.daysSinceIsoUtc(TimeUtils.isoUtcFromEpochMillis(TimeUtils.nowEpochMillis())))
    }

    @Test
    fun daysSinceIsoUtc_returnsNullForGarbage() {
        assertNull(TimeUtils.daysSinceIsoUtc("not-a-date"))
    }
}
