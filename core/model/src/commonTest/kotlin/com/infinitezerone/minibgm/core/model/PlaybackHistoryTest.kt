package com.infinitezerone.minibgm.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackHistoryTest {
    private fun history(
        positionMs: Long,
        durationMs: Long,
    ) = PlaybackHistory(
        episodeId = 101L,
        subjectId = 1001L,
        subjectName = "葬送的芙莉莲",
        episodeName = "第 1 话",
        positionMs = positionMs,
        durationMs = durationMs,
    )

    @Test
    fun progress_isZeroWhenDurationUnknown() {
        // 时长未知（尚未起播 / 直播流）时进度无处可算，只能是 0 而不是 NaN
        assertEquals(0f, history(positionMs = 30_000L, durationMs = 0L).progress)
    }

    @Test
    fun progress_isRatioAndClampedToOne() {
        assertEquals(0.5f, history(positionMs = 120_000L, durationMs = 240_000L).progress)
        // 引擎回报的位置偶尔会略超总时长，进度必须夹在 0..1 内，否则进度条会溢出
        assertEquals(1f, history(positionMs = 250_000L, durationMs = 240_000L).progress)
    }

    @Test
    fun isCompleted_isFalseWhenDurationUnknown() {
        assertFalse(history(positionMs = 30_000L, durationMs = 0L).isCompleted)
    }

    @Test
    fun isCompleted_isTrueAtNinetyPercent() {
        assertTrue(history(positionMs = 216_000L, durationMs = 240_000L).isCompleted)
    }

    @Test
    fun isCompleted_isTrueWhenWithinThirtySecondsOfTheEnd() {
        // 进度只有 89.6%（未达 90%），但距离结尾只剩 25 秒：仍算看完（片尾曲/下集预告不该再续播）
        val entry = history(positionMs = 215_000L, durationMs = 240_000L)
        assertTrue(entry.progress < 0.9f)
        assertTrue(entry.isCompleted)
    }

    @Test
    fun isCompleted_isFalseWhenFarFromTheEnd() {
        assertFalse(history(positionMs = 120_000L, durationMs = 240_000L).isCompleted)
    }

    @Test
    fun isCompleted_isTrueAtExactEnd() {
        // 位置正好等于总时长时已无剩余可播（remaining = 0），仍须判为看完
        assertTrue(history(positionMs = 240_000L, durationMs = 240_000L).isCompleted)
    }
}
