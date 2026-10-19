package com.infinitezerone.minibgm.feature.subject.player

import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.DanmakuMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
        assertEquals("00:05:00", formatDuration(5 * 60 * 1000L, forceHours = true))
        assertEquals("00:00:00", formatDuration(0L, forceHours = true))
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
        // 片长 1/10 = 144s（2.4 分钟），略大于一个 OP，一次扫屏足够跳过片头
        assertEquals(144_000L, calculateSeekDeltaMs(1000f, width, totalMs))
        // 10% screen drag: 14.4s
        assertEquals(14_400L, calculateSeekDeltaMs(100f, width, totalMs))
    }

    @Test
    fun calculateSeekDeltaMs_scalesForFeatureFilms() {
        val totalMs = 90 * 60 * 1000L // 90 minutes movie
        val width = 1000f
        // 10% of 90min is 9min = 540s
        assertEquals(540_000L, calculateSeekDeltaMs(1000f, width, totalMs))
    }

    @Test
    fun seekSpanMs_isContinuousAcrossLegacyBandBoundaries() {
        val width = 1000f
        // 历史实现用 when 分段：5:00 → 5:01 一屏跨度从 60s 突跳到 180s（3 倍）。
        // 这种断层会让同样手势在不同片长上突然变灵敏，用户无法建立肌肉记忆。
        val at5 = calculateSeekDeltaMs(width, width, 5 * 60_000L)
        val justAfter5 = calculateSeekDeltaMs(width, width, 5 * 60_000L + 1_000L)
        assertTrue("5 分钟边界处一屏跨度不应跳变", abs(at5 - justAfter5) <= 1_000L)

        // 30:00 → 30:01 旧实现从 10% 突跳到 16.7%
        val at30 = calculateSeekDeltaMs(width, width, 30 * 60_000L)
        val justAfter30 = calculateSeekDeltaMs(width, width, 30 * 60_000L + 1_000L)
        assertTrue("30 分钟边界处一屏跨度不应跳变", abs(at30 - justAfter30) <= 1_000L)
    }

    @Test
    fun seekSpanMs_growsMonotonicallyWithDuration() {
        var previous = 0L
        listOf(30L, 60L, 3 * 60L, 5 * 60L, 10 * 60L, 24 * 60L, 30 * 60L, 90 * 60L, 150 * 60L, 240 * 60L)
            .map { it * 1_000L }
            .forEach { durationMs ->
                val span = seekSpanMs(durationMs)
                assertTrue("一屏跨度必须随时长单调不减（时长 $durationMs）", span >= previous)
                previous = span
            }
    }

    @Test
    fun seekSpanMs_clampsShortAndLongContent() {
        // 短片：托底 60s，保证一屏就有可感知的位移
        assertEquals(60_000L, seekSpanMs(30_000L))
        assertEquals(60_000L, seekSpanMs(3 * 60_000L))
        // 长片：封顶 15min，避免一屏扫过头
        assertEquals(900_000L, seekSpanMs(150 * 60_000L))
        assertEquals(900_000L, seekSpanMs(240 * 60_000L))
        // 中间区间：恒为片长 1/10
        assertEquals(144_000L, seekSpanMs(24 * 60_000L))
        assertEquals(540_000L, seekSpanMs(90 * 60_000L))
    }

    @Test
    fun resolveLanguageName_mapsCommonCodes() {
        assertEquals("中文 (简体)", resolveLanguageName("zh-CN"))
        assertEquals("中文 (简体)", resolveLanguageName("zh-Hans"))
        assertEquals("中文", resolveLanguageName("chi"))
        assertEquals("中文", resolveLanguageName("zh"))
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

        // 当代码为通用中文 "chi"，但 label 明确含有繁/简指示时，应准确归一
        assertEquals(
            "中文 (繁体)",
            formatTrackDisplayDetails(
                label = "TC",
                language = "chi",
                channelCount = 0,
                selectionFlags = 0,
                trackType = androidx.media3.common.C.TRACK_TYPE_TEXT,
                index = 0,
                defaultLabel = "字幕",
            ),
        )

        assertEquals(
            "中文 (简体)",
            formatTrackDisplayDetails(
                label = "SC",
                language = "chi",
                channelCount = 0,
                selectionFlags = 0,
                trackType = androidx.media3.common.C.TRACK_TYPE_TEXT,
                index = 0,
                defaultLabel = "字幕",
            ),
        )

        assertEquals(
            "中文 (繁体) (繁日双语)",
            formatTrackDisplayDetails(
                label = "繁日双语",
                language = "chi",
                channelCount = 0,
                selectionFlags = 0,
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
    fun disambiguateTrackNames_resolvesDuplicatesAndDualChinese() {
        // 双中文无 label 且同为 chi 时，按 SC_TC 惯例分别归属为简体与繁体
        val dualChinese = listOf("中文", "中文")
        assertEquals(listOf("中文 (简体)", "中文 (繁体)"), disambiguateTrackNames(dualChinese))

        // 带默认/强制标记的双中文
        val dualChineseWithDefault = listOf("中文 [默认]", "中文")
        assertEquals(listOf("中文 (简体) [默认]", "中文 (繁体)"), disambiguateTrackNames(dualChineseWithDefault))

        // 重复音轨追加序号索引
        val duplicateAudio = listOf("日语 · 双声道", "日语 · 双声道")
        assertEquals(listOf("日语 · 双声道 #1", "日语 · 双声道 #2"), disambiguateTrackNames(duplicateAudio))

        // 已区分的不做变动
        val alreadyDistinct = listOf("中文 (简体)", "中文 (繁体)")
        assertEquals(alreadyDistinct, disambiguateTrackNames(alreadyDistinct))

        // 单轨不追加任何后缀
        val singleTrack = listOf("中文")
        assertEquals(singleTrack, disambiguateTrackNames(singleTrack))
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

        // 验证 8500ms 滚动时长边界：1499ms 已过期，1500ms 仍处于最后活跃刻度
        val boundaryDanmakus =
            listOf(
                com.infinitezerone.minibgm.core.model
                    .DanmakuItem(timeMs = 1_499L, text = "Expired"),
                com.infinitezerone.minibgm.core.model
                    .DanmakuItem(timeMs = 1_500L, text = "Alive boundary"),
            )
        val boundaryActive = findActiveDanmakus(boundaryDanmakus, 10_000L)
        assertEquals(1, boundaryActive.size)
        assertEquals("Alive boundary", boundaryActive[0].text)
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

    @Test
    fun calculateProgressBarFraction_withThumbRadiusAlignsGeometry() {
        val totalWidth = 500f
        val thumbRadiusPx = 20f
        // Start of travel: touch <= thumbRadiusPx -> exactly 0f
        assertEquals(0f, calculateProgressBarFraction(0f, totalWidth, thumbRadiusPx), 0.0001f)
        assertEquals(0f, calculateProgressBarFraction(20f, totalWidth, thumbRadiusPx), 0.0001f)
        assertEquals(0f, calculateProgressBarFraction(10f, totalWidth, thumbRadiusPx), 0.0001f)

        // End of travel: touch >= totalWidth - thumbRadiusPx (480f) -> exactly 1f
        assertEquals(1f, calculateProgressBarFraction(480f, totalWidth, thumbRadiusPx), 0.0001f)
        assertEquals(1f, calculateProgressBarFraction(500f, totalWidth, thumbRadiusPx), 0.0001f)
        assertEquals(1f, calculateProgressBarFraction(520f, totalWidth, thumbRadiusPx), 0.0001f)

        // Middle of travel: touch = 250f -> exactly 0.5f
        assertEquals(0.5f, calculateProgressBarFraction(250f, totalWidth, thumbRadiusPx), 0.0001f)

        // Quarter of travel: 20 + 0.25 * 460 = 135f -> exactly 0.25f
        assertEquals(0.25f, calculateProgressBarFraction(135f, totalWidth, thumbRadiusPx), 0.0001f)
    }

    @Test
    fun isScrollTrackSafe_preventsEntryCollision() {
        val lastItem = DanmakuItem(timeMs = 1_000L, text = "Leading")
        val lastWidth = 100f
        val canvasWidth = 1000f
        val durationMs = 8_000L
        val minSpacingPx = 30f

        // 1. 同一时间戳入场：尾部尚未入屏，必须判定不安全
        val simultaneous = DanmakuItem(timeMs = 1_000L, text = "Follower")
        assertFalse(isScrollTrackSafe(lastItem, lastWidth, simultaneous, 100f, canvasWidth, durationMs, minSpacingPx))

        // 2. 尾部刚进入右边界但未留出 minSpacingPx：判定不安全
        // tailX = (1000 + 100) * (1 - deltaT / 8000)
        // 当 deltaT = 500ms 时，tailX = 1100 * (7500 / 8000) = 1031.25 > 1000 - 30 (970)
        val tooEarly = DanmakuItem(timeMs = 1_500L, text = "TooEarly")
        assertFalse(isScrollTrackSafe(lastItem, lastWidth, tooEarly, 100f, canvasWidth, durationMs, minSpacingPx))

        // 3. 尾部已行进出安全距离（deltaT = 1500ms，tailX = 1100 * 6500 / 8000 = 893.75 <= 970）：判定安全
        val safeEntry = DanmakuItem(timeMs = 2_500L, text = "SafeEntry")
        assertTrue(isScrollTrackSafe(lastItem, lastWidth, safeEntry, 100f, canvasWidth, durationMs, minSpacingPx))

        // 4. 前序弹幕已完全离开屏幕（deltaT >= durationMs）：判定安全
        val expired = DanmakuItem(timeMs = 10_000L, text = "ExpiredLeader")
        assertTrue(isScrollTrackSafe(lastItem, lastWidth, expired, 100f, canvasWidth, durationMs, minSpacingPx))
    }

    @Test
    fun isScrollTrackSafe_preventsOvertakeCollision() {
        val lastItem = DanmakuItem(timeMs = 1_000L, text = "Short")
        val lastWidth = 100f
        val canvasWidth = 1000f
        val durationMs = 8_000L
        val minSpacingPx = 30f

        // 后续弹幕文字很长（700px），速度显著快于前序弹幕（100px）
        // 即使入场安全，但在屏幕中途或离开屏幕前会追尾
        val fastFollower = DanmakuItem(timeMs = 2_000L, text = "VeryLongDanmakuTextWithHighSpeed")
        val fastWidth = 700f
        // deltaT = 1000ms:
        // tailX_last = (1000 + 100) * (7000 / 8000) = 962.5 <= 970 (入场安全通过)
        // 但追尾检查：elapsedNew = 8000 - 1000 = 7000ms
        // headX = 1000 - (7000 / 8000) * 1700 = 1000 - 1487.5 = -487.5 < 30 (追尾不安全拦截！)
        assertFalse(isScrollTrackSafe(lastItem, lastWidth, fastFollower, fastWidth, canvasWidth, durationMs, minSpacingPx))

        // 若前序与后续弹幕速度相同（宽度相同），入场安全即可保证永不追尾
        val sameSpeedFollower = DanmakuItem(timeMs = 2_000L, text = "SameSpeed")
        assertTrue(isScrollTrackSafe(lastItem, lastWidth, sameSpeedFollower, lastWidth, canvasWidth, durationMs, minSpacingPx))

        // 若后续弹幕进入时间足够晚（例如 deltaT = 4500ms），即使速度快也不会在屏幕内追尾
        // deltaT = 4500ms: elapsedNew = 3500ms
        // headX = 1000 - (3500 / 8000) * 1700 = 1000 - 743.75 = 256.25 >= 30 (追尾安全通过)
        val wellSpacedFastFollower = DanmakuItem(timeMs = 5_500L, text = "WellSpacedFast")
        assertTrue(isScrollTrackSafe(lastItem, lastWidth, wellSpacedFastFollower, fastWidth, canvasWidth, durationMs, minSpacingPx))
    }

    @Test
    fun computeDanmakuLayout_allocatesSeparateTracksForSimultaneousDanmakus() {
        // 5 条同时发出的相同弹幕（模拟“草”或者“2333”弹幕刷屏）
        val danmakus =
            (0 until 5).map {
                DanmakuItem(timeMs = 2_000L, text = "草", mode = DanmakuMode.SCROLL)
            }

        val layout =
            computeDanmakuLayout(
                danmakus = danmakus,
                canvasWidth = 1000f,
                canvasHeight = 600f,
                trackHeightPx = 40f,
                durationMs = 8_000L,
                measureTextWidth = { 50f },
            )

        assertEquals(5, layout.size)
        val trackIndices = layout.map { it.trackIndex }
        // 5 条同时发出的弹幕必须被分配到不同的弹道（0, 1, 2, 3, 4），彻底消除同屏重叠！
        assertEquals(listOf(0, 1, 2, 3, 4), trackIndices)
    }

    @Test
    fun computeDanmakuLayout_respectsSubtitleProtectionArea() {
        val canvasHeight = 1000f
        val trackHeightPx = 50f
        val subtitleRatio = 0.25f // 底部预留 25% (250px)
        val availableHeight = canvasHeight * (1f - subtitleRatio) // 750px

        // 大量滚动弹幕
        val danmakus =
            (0 until 30).map {
                DanmakuItem(timeMs = 2_000L, text = "Danmaku $it", mode = DanmakuMode.SCROLL)
            }

        val layout =
            computeDanmakuLayout(
                danmakus = danmakus,
                canvasWidth = 1000f,
                canvasHeight = canvasHeight,
                trackHeightPx = trackHeightPx,
                durationMs = 8_000L,
                measureTextWidth = { 100f },
                subtitleAreaRatio = subtitleRatio,
            )

        // 验证所有分配的滚动弹道 Y 轴均不超过 availableHeight (750px)
        for (item in layout) {
            val posY = (item.trackIndex + 1) * trackHeightPx
            assertTrue("Track Y ($posY) must not exceed available height ($availableHeight)", posY <= availableHeight)
        }
    }

    @Test
    fun computeDanmakuLayout_stacksTopAndBottomFixedDanmakusWithoutCollision() {
        val topDanmakus =
            listOf(
                DanmakuItem(timeMs = 1_000L, text = "Top 1", mode = DanmakuMode.TOP),
                DanmakuItem(timeMs = 1_500L, text = "Top 2", mode = DanmakuMode.TOP),
                DanmakuItem(timeMs = 6_000L, text = "Top 3 (After 1 expires)", mode = DanmakuMode.TOP),
            )
        val layoutTop =
            computeDanmakuLayout(
                danmakus = topDanmakus,
                canvasWidth = 1000f,
                canvasHeight = 600f,
                trackHeightPx = 40f,
                measureTextWidth = { 80f },
                fixedDurationMs = 4_000L,
            )
        assertEquals(3, layoutTop.size)
        assertEquals(0, layoutTop[0].trackIndex) // Top 1 在 track 0
        assertEquals(1, layoutTop[1].trackIndex) // Top 2 与 Top 1 时间重叠，落入 track 1
        assertEquals(0, layoutTop[2].trackIndex) // Top 3 在 6000ms，Top 1 (1000 + 4000 = 5000ms) 已过期，复用 track 0！

        val bottomDanmakus =
            listOf(
                DanmakuItem(timeMs = 1_000L, text = "Bottom 1", mode = DanmakuMode.BOTTOM),
                DanmakuItem(timeMs = 1_500L, text = "Bottom 2", mode = DanmakuMode.BOTTOM),
            )
        val layoutBottom =
            computeDanmakuLayout(
                danmakus = bottomDanmakus,
                canvasWidth = 1000f,
                canvasHeight = 600f,
                trackHeightPx = 40f,
                measureTextWidth = { 80f },
                fixedDurationMs = 4_000L,
            )
        assertEquals(2, layoutBottom.size)
        assertEquals(0, layoutBottom[0].trackIndex)
        assertEquals(1, layoutBottom[1].trackIndex)
    }

    @Test
    fun findActivePositionedDanmakus_filtersCorrectActiveWindow() {
        val items =
            listOf(
                PositionedDanmaku(DanmakuItem(timeMs = 1_000L, text = "Old expired", mode = DanmakuMode.SCROLL), 0, 100f),
                PositionedDanmaku(DanmakuItem(timeMs = 5_000L, text = "Active 1", mode = DanmakuMode.SCROLL), 1, 100f),
                PositionedDanmaku(DanmakuItem(timeMs = 8_000L, text = "Active 2", mode = DanmakuMode.SCROLL), 2, 100f),
                PositionedDanmaku(DanmakuItem(timeMs = 12_000L, text = "Future", mode = DanmakuMode.SCROLL), 0, 100f),
            )

        // currentMs = 10_000ms, duration = 8_500ms -> 范围 [1_500ms, 10_000ms]
        val active = findActivePositionedDanmakus(items, 10_000L, 8_500L)
        assertEquals(2, active.size)
        assertEquals("Active 1", active[0].item.text)
        assertEquals("Active 2", active[1].item.text)
    }
}
