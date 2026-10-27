package com.infinitezerone.minibgm.core.data.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class M3u8AdSanitizerTest {
    @Test
    fun sanitize_stripsHeadDiscontinuityAdSegments_andUpdatesSequence() {
        val rawM3u8 =
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-DISCONTINUITY
            #EXTINF:6.000000,
            https://ad.example.com/ad_part_1.ts
            #EXTINF:6.000000,
            https://ad.example.com/ad_part_2.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:100.000000,
            https://cdn.realvod.com/real_part_1.ts
            #EXTINF:100.000000,
            https://cdn.realvod.com/real_part_2.ts
            #EXT-X-ENDLIST
            """.trimIndent()

        val sanitized = M3u8AdSanitizer.sanitize(rawM3u8)

        // 广告段已被剥离
        assertFalse(sanitized.contains("ad.example.com"))
        assertFalse(sanitized.contains("ad_part_1.ts"))

        // 正片保留
        assertTrue(sanitized.contains("https://cdn.realvod.com/real_part_1.ts"))
        assertTrue(sanitized.contains("https://cdn.realvod.com/real_part_2.ts"))
        assertTrue(sanitized.contains("#EXT-X-ENDLIST"))

        // 序号正确从 0 补偿 +2
        assertTrue(sanitized.contains("#EXT-X-MEDIA-SEQUENCE:2"))
    }

    @Test
    fun sanitize_stripsMidRollAndTailRollAds_preservingMainContent() {
        val rawM3u8 =
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:100.000000,
            https://cdn.realvod.com/anime_part_1.ts
            #EXTINF:100.000000,
            https://cdn.realvod.com/anime_part_2.ts
            #EXT-X-DISCONTINUITY
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:6.000000,
            https://ad.example.com/mid_ad_1.ts
            #EXTINF:6.000000,
            https://ad.example.com/mid_ad_2.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:100.000000,
            https://cdn.realvod.com/anime_part_3.ts
            #EXTINF:100.000000,
            https://cdn.realvod.com/anime_part_4.ts
            #EXT-X-DISCONTINUITY
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:6.000000,
            https://ad.example.com/tail_ad_1.ts
            #EXTINF:6.000000,
            https://ad.example.com/tail_ad_2.ts
            #EXT-X-ENDLIST
            """.trimIndent()

        val sanitized = M3u8AdSanitizer.sanitize(rawM3u8)

        // 中插广告与片尾广告均被剔除
        assertFalse(sanitized.contains("mid_ad_1.ts"))
        assertFalse(sanitized.contains("tail_ad_1.ts"))
        assertFalse(sanitized.contains("ad.example.com"))

        // 正片全部保留
        assertTrue(sanitized.contains("anime_part_1.ts"))
        assertTrue(sanitized.contains("anime_part_2.ts"))
        assertTrue(sanitized.contains("anime_part_3.ts"))
        assertTrue(sanitized.contains("anime_part_4.ts"))
        assertTrue(sanitized.contains("#EXT-X-ENDLIST"))

        // 首段是正片，序列号不变
        assertTrue(sanitized.contains("#EXT-X-MEDIA-SEQUENCE:0"))
    }

    @Test
    fun sanitize_preservesContentWhenDurationExceedsThreshold_preventsFalsePositive() {
        val legitimateContentM3u8 =
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-DISCONTINUITY
            #EXTINF:50.000000,
            https://cdn.realvod.com/part_1.ts
            #EXTINF:50.000000,
            https://cdn.realvod.com/part_2.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:50.000000,
            https://cdn.realvod.com/part_3.ts
            #EXT-X-ENDLIST
            """.trimIndent()

        // 100秒超过了默认 30秒阈值，不能当做广告切片
        val sanitized = M3u8AdSanitizer.sanitize(legitimateContentM3u8)

        assertEquals(legitimateContentM3u8, sanitized)
    }

    @Test
    fun sanitize_passesThroughCleanPlaylist_withoutModification() {
        val cleanM3u8 =
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:100
            #EXTINF:10.000000,
            https://cdn.realvod.com/part_1.ts
            #EXTINF:10.000000,
            https://cdn.realvod.com/part_2.ts
            #EXT-X-ENDLIST
            """.trimIndent()

        val sanitized = M3u8AdSanitizer.sanitize(cleanM3u8)

        assertEquals(cleanM3u8, sanitized)
    }

    @Test
    fun sanitize_bypassesMasterPlaylist() {
        val masterM3u8 =
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=1280x720
            https://cdn.realvod.com/720p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2560000,RESOLUTION=1920x1080
            https://cdn.realvod.com/1080p.m3u8
            """.trimIndent()

        val sanitized = M3u8AdSanitizer.sanitize(masterM3u8)

        assertEquals(masterM3u8, sanitized)
    }

    @Test
    fun sanitize_supportsCustomTvBoxRegexRules() {
        val rawM3u8 =
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-DISCONTINUITY
            #EXTINF:5.000,
            ad_banner.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.000,
            episode_ep1.ts
            #EXT-X-ENDLIST
            """.trimIndent()

        val customRule = listOf("#EXT-X-DISCONTINUITY[\\s\\S]*?#EXT-X-DISCONTINUITY")
        val sanitized = M3u8AdSanitizer.sanitize(rawM3u8, customRules = customRule)

        assertFalse(sanitized.contains("ad_banner.ts"))
        assertTrue(sanitized.contains("episode_ep1.ts"))
    }

    @Test
    fun sanitize_failOpenOnMalformedInput() {
        assertEquals("", M3u8AdSanitizer.sanitize(""))
        assertEquals("random text", M3u8AdSanitizer.sanitize("random text"))
        assertEquals("<html>404 Not Found</html>", M3u8AdSanitizer.sanitize("<html>404 Not Found</html>"))
    }
}
