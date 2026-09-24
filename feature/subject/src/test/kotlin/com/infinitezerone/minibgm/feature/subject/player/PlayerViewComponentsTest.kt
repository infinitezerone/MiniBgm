package com.infinitezerone.minibgm.feature.subject.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerViewComponentsTest {
    @Test
    fun formatDuration_handlesZeroAndNegative() {
        assertEquals("00:00", formatDuration(0L))
        assertEquals("00:00", formatDuration(-1000L))
    }

    @Test
    fun formatDuration_formatsMinutesAndSeconds() {
        assertEquals("00:05", formatDuration(5_000L))
        assertEquals("01:05", formatDuration(65_000L))
        assertEquals("24:00", formatDuration(24 * 60 * 1000L))
    }

    @Test
    fun formatDuration_formatsHoursCorrectly() {
        assertEquals("01:00:00", formatDuration(3600 * 1000L))
        assertEquals("01:01:05", formatDuration((3600 + 65) * 1000L))
    }

    @Test
    fun playerResizeMode_hasExpectedLabels() {
        assertEquals("适应", PlayerResizeMode.FIT.label)
        assertEquals("裁剪", PlayerResizeMode.ZOOM.label)
        assertEquals("拉伸", PlayerResizeMode.FILL.label)
    }

    @Test
    fun episodeChunking_splitsCorrectly() {
        val episodes = (1..75).map { PlayerEpisodeItem(id = it.toLong(), sort = it.toFloat()) }
        val chunkSize = 30
        val chunks = episodes.chunked(chunkSize)
        assertEquals(3, chunks.size)
        assertEquals(30, chunks[0].size)
        assertEquals(1, chunks[0].first().sort.toInt())
        assertEquals(30, chunks[0].last().sort.toInt())
        assertEquals(30, chunks[1].size)
        assertEquals(31, chunks[1].first().sort.toInt())
        assertEquals(60, chunks[1].last().sort.toInt())
        assertEquals(15, chunks[2].size)
        assertEquals(61, chunks[2].first().sort.toInt())
        assertEquals(75, chunks[2].last().sort.toInt())
    }
}
