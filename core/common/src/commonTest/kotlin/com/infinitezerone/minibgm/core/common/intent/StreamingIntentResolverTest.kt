package com.infinitezerone.minibgm.core.common.intent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamingIntentResolverTest {
    @Test
    fun resolve_bilibiliSeasonUrl_returnsCorrectDeepLink() {
        val url = "https://www.bilibili.com/bangumi/play/ss28770"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertEquals("bilibili", target.siteName)
        assertEquals("哔哩哔哩", target.appName)
        assertEquals("bilibili://bangumi/season/28770", target.deepLinkUri)
        assertTrue(target.packageNames.contains("tv.danmaku.bili"))
    }

    @Test
    fun resolve_bilibiliEpisodeUrl_returnsEpisodeDeepLink() {
        val url = "https://www.bilibili.com/bangumi/play/ep315891"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertEquals("bilibili://bangumi/season/ep/315891", target.deepLinkUri)
    }

    @Test
    fun resolve_bilibiliVideoBvUrl_returnsVideoDeepLink() {
        val url = "https://www.bilibili.com/video/BV1xx411c7mD"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertEquals("bilibili://video/BV1xx411c7mD", target.deepLinkUri)
    }

    @Test
    fun resolve_bilibiliShortUrl_returnsDeepLink() {
        val ssUrl = "https://b23.tv/ss12345"
        val ssTarget = StreamingIntentResolver.resolve(ssUrl)
        assertNotNull(ssTarget)
        assertEquals("bilibili://bangumi/season/12345", ssTarget.deepLinkUri)

        val bvUrl = "https://b23.tv/BV1yy411c7mE"
        val bvTarget = StreamingIntentResolver.resolve(bvUrl)
        assertNotNull(bvTarget)
        assertEquals("bilibili://video/BV1yy411c7mE", bvTarget.deepLinkUri)
    }

    @Test
    fun resolve_bahamutVideoUrl_returnsGamerDeepLink() {
        val url = "https://ani.gamer.com.tw/animeVideo.php?sn=38521"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertEquals("gamer", target.siteName)
        assertEquals("巴哈姆特動畫瘋", target.appName)
        assertEquals("animegamer://animedetail?sn=38521", target.deepLinkUri)
        assertTrue(target.packageNames.contains("tw.com.gamer.android.animad"))
    }

    @Test
    fun resolve_bahamutRefUrl_returnsGamerDeepLink() {
        val url = "https://ani.gamer.com.tw/animeRef.php?sn=12345"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertEquals("animegamer://animedetail?sn=12345", target.deepLinkUri)
    }

    @Test
    fun resolve_iqiyiUrl_returnsTargetWithPackage() {
        val url = "https://www.iqiyi.com/a_19rrh8.html"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertEquals("iqiyi", target.siteName)
        assertTrue(target.packageNames.contains("com.qiyi.video"))
    }

    @Test
    fun resolve_unknownUrl_returnsNull() {
        val url = "https://example.com/anime"
        val target = StreamingIntentResolver.resolve(url)

        assertNull(target)
    }

    @Test
    fun resolve_emptyUrl_returnsNull() {
        assertNull(StreamingIntentResolver.resolve(""))
        assertNull(StreamingIntentResolver.resolve("   "))
    }
}
