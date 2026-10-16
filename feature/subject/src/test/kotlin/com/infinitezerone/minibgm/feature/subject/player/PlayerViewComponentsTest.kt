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

    @Test
    fun formatQualityLabel_resolvesStandardResolutions() {
        assertEquals("4K", formatQualityLabel(3840, 2160))
        assertEquals("2K", formatQualityLabel(2560, 1440))
        assertEquals("1080P", formatQualityLabel(1920, 1080))
        assertEquals("720P", formatQualityLabel(1280, 720))
        assertEquals("480P", formatQualityLabel(854, 480))
        assertEquals("360P", formatQualityLabel(640, 360))
        assertEquals("240P", formatQualityLabel(320, 240))
        assertEquals("未知画质", formatQualityLabel(-1, -1))
    }

    @Test
    fun formatQualityDetail_formatsResolutionAndBitrate() {
        assertEquals("1920×1080 · 3.5 Mbps", formatQualityDetail(1920, 1080, 3_500_000))
        assertEquals("1280×720 · 800 kbps", formatQualityDetail(1280, 720, 800_000))
        assertEquals("1920×1080", formatQualityDetail(1920, 1080, -1))
        assertEquals("3.5 Mbps", formatQualityDetail(-1, -1, 3_500_000))
        assertEquals(null, formatQualityDetail(-1, -1, -1))
    }

    @Test
    fun playerTracksSnapshot_resolvesCurrentQualityDisplayLabel() {
        // 多清晰度 - AUTO 档且有探测到的具体画质
        val multiAuto =
            PlayerTracksSnapshot(
                qualityTracks =
                    listOf(
                        PlayerQualityOption("1", "1080P", "1920x1080", 1920, 1080, 3000, true),
                        PlayerQualityOption("2", "720P", "1280x720", 1280, 720, 1500, false),
                    ),
                qualityMode = QualityMode.AUTO,
                currentAutoQualityName = "1080P",
            )
        assertEquals("自动 (1080P)", multiAuto.currentQualityDisplayLabel)

        // 多清晰度 - 手动选中 720P
        val multiSpecific =
            PlayerTracksSnapshot(
                qualityTracks =
                    listOf(
                        PlayerQualityOption("1", "1080P", "1920x1080", 1920, 1080, 3000, false),
                        PlayerQualityOption("2", "720P", "1280x720", 1280, 720, 1500, true),
                    ),
                qualityMode = QualityMode.SPECIFIC,
            )
        assertEquals("720P", multiSpecific.currentQualityDisplayLabel)

        // 单清晰度片源 - 直接显示该清晰度
        val singleTrack =
            PlayerTracksSnapshot(
                qualityTracks =
                    listOf(
                        PlayerQualityOption("1", "1080P", "1920x1080", 1920, 1080, 3000, true),
                    ),
            )
        assertEquals("1080P", singleTrack.currentQualityDisplayLabel)

        // 无 TrackGroup 但有解码器画面尺寸
        val videoSizeOnly =
            PlayerTracksSnapshot(
                currentVideoSizeQualityName = "720P",
            )
        assertEquals("720P", videoSizeOnly.currentQualityDisplayLabel)

        // 未解析出画面尺寸
        val emptySnapshot = PlayerTracksSnapshot()
        assertEquals(null, emptySnapshot.currentQualityDisplayLabel)
    }

    @Test
    fun findActiveDanmakus_filtersCorrectlyByTimeWindow() {
        val danmakus =
            listOf(
                com.infinitezerone.minibgm.core.model.DanmakuItem(
                    timeMs = 1_000L,
                    text = "Old scroll",
                    mode = com.infinitezerone.minibgm.core.model.DanmakuMode.SCROLL,
                ),
                com.infinitezerone.minibgm.core.model.DanmakuItem(
                    timeMs = 4_000L,
                    text = "Active scroll 1",
                    mode = com.infinitezerone.minibgm.core.model.DanmakuMode.SCROLL,
                ),
                com.infinitezerone.minibgm.core.model.DanmakuItem(
                    timeMs = 6_500L,
                    text = "Active top",
                    mode = com.infinitezerone.minibgm.core.model.DanmakuMode.TOP,
                ),
                com.infinitezerone.minibgm.core.model.DanmakuItem(
                    timeMs = 9_000L,
                    text = "Active scroll 2",
                    mode = com.infinitezerone.minibgm.core.model.DanmakuMode.SCROLL,
                ),
                com.infinitezerone.minibgm.core.model.DanmakuItem(
                    timeMs = 11_000L,
                    text = "Future danmaku",
                    mode = com.infinitezerone.minibgm.core.model.DanmakuMode.SCROLL,
                ),
            )

        val active = findActiveDanmakus(danmakus, 10_000L)
        assertEquals(3, active.size)
        assertEquals("Active scroll 1", active[0].text)
        assertEquals("Active top", active[1].text)
        assertEquals("Active scroll 2", active[2].text)
    }

    @Test
    fun findActiveDanmakus_handlesEmptyAndBoundary() {
        assertEquals(emptyList<com.infinitezerone.minibgm.core.model.DanmakuItem>(), findActiveDanmakus(emptyList(), 10_000L))
        assertEquals(
            emptyList<com.infinitezerone.minibgm.core.model.DanmakuItem>(),
            findActiveDanmakus(
                listOf(
                    com.infinitezerone.minibgm.core.model
                        .DanmakuItem(1000L, "test"),
                ),
                -100L,
            ),
        )
    }

    @Test
    fun calculateProgressBarFraction_handlesBoundariesAndCoercion() {
        assertEquals(0f, calculateProgressBarFraction(0f, 1000f), 0.0001f)
        assertEquals(0f, calculateProgressBarFraction(-50f, 1000f), 0.0001f)
        assertEquals(1f, calculateProgressBarFraction(1000f, 1000f), 0.0001f)
        assertEquals(1f, calculateProgressBarFraction(1200f, 1000f), 0.0001f)
        assertEquals(0.5f, calculateProgressBarFraction(500f, 1000f), 0.0001f)
        assertEquals(0f, calculateProgressBarFraction(500f, 0f), 0.0001f)
        assertEquals(0f, calculateProgressBarFraction(500f, -100f), 0.0001f)
    }
}
