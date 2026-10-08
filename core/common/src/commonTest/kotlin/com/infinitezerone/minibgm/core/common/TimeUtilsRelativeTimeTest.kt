package com.infinitezerone.minibgm.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeUtilsRelativeTimeTest {
    @Test
    fun invalidOrZeroEpochReturnsEmpty() {
        assertEquals("", TimeUtils.formatRelativeTime(0, nowSeconds = 1000))
        assertEquals("", TimeUtils.formatRelativeTime(-10, nowSeconds = 1000))
    }

    @Test
    fun recentSecondsReturnsJustNow() {
        val now = 1700000000L
        assertEquals("刚刚", TimeUtils.formatRelativeTime(now, nowSeconds = now))
        assertEquals("刚刚", TimeUtils.formatRelativeTime(now - 30, nowSeconds = now))
        assertEquals("刚刚", TimeUtils.formatRelativeTime(now + 10, nowSeconds = now)) // clock drift
    }

    @Test
    fun minutesAgoFormatsCorrectly() {
        val now = 1700000000L
        assertEquals("1分钟前", TimeUtils.formatRelativeTime(now - 60, nowSeconds = now))
        assertEquals("5分钟前", TimeUtils.formatRelativeTime(now - 300, nowSeconds = now))
        assertEquals("59分钟前", TimeUtils.formatRelativeTime(now - 3599, nowSeconds = now))
    }

    @Test
    fun hoursAgoFormatsCorrectly() {
        val now = 1700000000L
        assertEquals("1小时前", TimeUtils.formatRelativeTime(now - 3600, nowSeconds = now))
        assertEquals("23小时前", TimeUtils.formatRelativeTime(now - 23 * 3600, nowSeconds = now))
    }

    @Test
    fun yesterdayFormatsCorrectly() {
        val now = 1700000000L
        assertEquals("昨天", TimeUtils.formatRelativeTime(now - 25 * 3600, nowSeconds = now))
    }

    @Test
    fun daysAgoFormatsCorrectly() {
        val now = 1700000000L
        assertEquals("2天前", TimeUtils.formatRelativeTime(now - 2 * 86400, nowSeconds = now))
        assertEquals("6天前", TimeUtils.formatRelativeTime(now - 6 * 86400, nowSeconds = now))
    }

    @Test
    fun olderFallsBackToDate() {
        val now = 1700000000L
        val older = now - 10 * 86400
        assertEquals(TimeUtils.formatEpochSecondsToDate(older), TimeUtils.formatRelativeTime(older, nowSeconds = now))
    }
}
