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

    @Test
    fun calculateSeekDeltaMs_handlesInvalidInputs() {
        assertEquals(0L, calculateSeekDeltaMs(100f, 0f, 24 * 60 * 1000L))
        assertEquals(0L, calculateSeekDeltaMs(100f, 1000f, 0L))
        assertEquals(0L, calculateSeekDeltaMs(100f, 1000f, -1000L))
    }

    @Test
    fun calculateSeekDeltaMs_scalesForShortVideos() {
        val totalMs = 3 * 60 * 1000L // 3 minutes
        val width = 1000f
        // Half screen forward: fraction = 0.5f, maxSpan = 60s -> 30s
        assertEquals(30_000L, calculateSeekDeltaMs(500f, width, totalMs))
        // Half screen backward: fraction = -0.5f -> -30s
        assertEquals(-30_000L, calculateSeekDeltaMs(-500f, width, totalMs))
    }

    @Test
    fun calculateSeekDeltaMs_scalesForStandardAnimeEpisodes() {
        val totalMs = 24 * 60 * 1000L // 24 minutes standard anime
        val width = 1000f
        // Full screen drag: 180s (3 minutes)
        assertEquals(180_000L, calculateSeekDeltaMs(1000f, width, totalMs))
        // 10% screen drag: 18s
        assertEquals(18_000L, calculateSeekDeltaMs(100f, width, totalMs))
    }

    @Test
    fun calculateSeekDeltaMs_scalesForFeatureFilms() {
        val totalMs = 90 * 60 * 1000L // 90 minutes movie
        val width = 1000f
        // 10% of 90min is 9min = 540s
        assertEquals(540_000L, calculateSeekDeltaMs(1000f, width, totalMs))
    }

    @Test
    fun resolveLanguageName_mapsCommonCodes() {
        assertEquals("中文 (简体)", resolveLanguageName("zh-CN"))
        assertEquals("中文 (简体)", resolveLanguageName("zh-Hans"))
        assertEquals("中文 (简体)", resolveLanguageName("chi"))
        assertEquals("中文 (繁体)", resolveLanguageName("zh-Hant"))
        assertEquals("中文 (繁体)", resolveLanguageName("cht"))
        assertEquals("日语", resolveLanguageName("ja"))
        assertEquals("日语", resolveLanguageName("jpn"))
        assertEquals("英语", resolveLanguageName("en"))
        assertEquals("韩语", resolveLanguageName("ko"))
        assertEquals(null, resolveLanguageName(null))
        assertEquals(null, resolveLanguageName(""))
        assertEquals(null, resolveLanguageName("und"))
    }

    @Test
    fun formatTrackDisplayName_formatsSubtitlesAndAudio() {
        assertEquals(
            "中文 (简体) (简日双语) [默认]",
            formatTrackDisplayDetails(
                label = "简日双语",
                language = "zh-CN",
                channelCount = 0,
                selectionFlags = androidx.media3.common.C.SELECTION_FLAG_DEFAULT,
                trackType = androidx.media3.common.C.TRACK_TYPE_TEXT,
                index = 0,
                defaultLabel = "字幕",
            ),
        )

        assertEquals(
            "日语 · 双声道",
            formatTrackDisplayDetails(
                label = null,
                language = "ja",
                channelCount = 2,
                selectionFlags = 0,
                trackType = androidx.media3.common.C.TRACK_TYPE_AUDIO,
                index = 0,
                defaultLabel = "音轨",
            ),
        )

        assertEquals(
            "日语原声 · 5.1 环绕声",
            formatTrackDisplayDetails(
                label = "日语原声",
                language = null,
                channelCount = 6,
                selectionFlags = 0,
                trackType = androidx.media3.common.C.TRACK_TYPE_AUDIO,
                index = 0,
                defaultLabel = "音轨",
            ),
        )

        assertEquals(
            "字幕 #1",
            formatTrackDisplayDetails(
                label = null,
                language = null,
                channelCount = 0,
                selectionFlags = 0,
                trackType = androidx.media3.common.C.TRACK_TYPE_TEXT,
                index = 0,
                defaultLabel = "字幕",
            ),
        )
    }
}
