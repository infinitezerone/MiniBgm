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
    fun resolve_bilibiliSearchDeepLink_returnsSearchTarget() {
        val url = "bilibili://search?keyword=%E8%91%AC%E9%80%81%E7%9A%84%E8%8A%99%E8%8E%89%E8%8E%B2"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertEquals("bilibili", target.siteName)
        assertEquals("哔哩哔哩", target.appName)
        assertEquals(url, target.deepLinkUri)
        assertEquals(
            "https://search.bilibili.com/all?keyword=%E8%91%AC%E9%80%81%E7%9A%84%E8%8A%99%E8%8E%89%E8%8E%B2",
            target.webFallbackUrl,
        )
        assertTrue(target.packageNames.contains("tv.danmaku.bili"))
    }

    @Test
    fun resolve_bilibiliSearchWebUrl_returnsSearchTargetWithDeepLink() {
        val url = "https://search.bilibili.com/all?keyword=%E8%91%AC%E9%80%81%E7%9A%84%E8%8A%99%E8%8E%89%E8%8E%B2"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertEquals("bilibili", target.siteName)
        assertEquals("bilibili://search?keyword=%E8%91%AC%E9%80%81%E7%9A%84%E8%8A%99%E8%8E%89%E8%8E%B2", target.deepLinkUri)
        assertEquals(url, target.webFallbackUrl)
    }

    @Test
    fun buildBilibiliSearchTarget_encodesKeywordProperly() {
        val target = StreamingIntentResolver.buildBilibiliSearchTarget("葬送的芙莉莲")
        assertEquals("bilibili", target.siteName)
        assertEquals("bilibili://search?keyword=%E8%91%AC%E9%80%81%E7%9A%84%E8%8A%99%E8%8E%89%E8%8E%B2", target.deepLinkUri)
        assertEquals(
            "https://search.bilibili.com/all?keyword=%E8%91%AC%E9%80%81%E7%9A%84%E8%8A%99%E8%8E%89%E8%8E%B2",
            target.webFallbackUrl,
        )
    }

    @Test
    fun buildMikanUrl_withDirectId_returnsBangumiPage() {
        val url = StreamingIntentResolver.buildMikanUrl(mikanId = "3233")
        assertEquals("https://mikanani.me/Home/Bangumi/3233", url)
    }

    @Test
    fun buildMikanUrl_withKeywordFallback_returnsSearchPage() {
        val url = StreamingIntentResolver.buildMikanUrl(mikanId = null, keyword = "葬送的芙莉莲")
        assertEquals("https://mikanani.me/Home/Search?searchstr=%E8%91%AC%E9%80%81%E7%9A%84%E8%8A%99%E8%8E%89%E8%8E%B2", url)
    }

    @Test
    fun buildBilibiliSearchTarget_withSpacesPunctuationAndJapanese() {
        val target = StreamingIntentResolver.buildBilibiliSearchTarget("SPY×FAMILY 第2期")
        assertTrue(target.isSearch)
        assertEquals("bilibili", target.siteName)
        assertTrue(target.packageNames.contains("tv.danmaku.bilibilihd"))
        assertTrue(target.packageNames.contains("com.bilibili.app.blue"))
        // Check deepLink and fallback contain encoded keyword
        val expectedKeyword = StreamingIntentResolver.encodeQueryParameter("SPY×FAMILY 第2期")
        assertEquals("bilibili://search?keyword=$expectedKeyword", target.deepLinkUri)
        assertEquals("https://search.bilibili.com/all?keyword=$expectedKeyword", target.webFallbackUrl)
    }

    @Test
    fun buildBilibiliSearchTarget_emptyKeyword_handlesGracefully() {
        val target = StreamingIntentResolver.buildBilibiliSearchTarget("   ")
        assertTrue(target.isSearch)
        assertEquals("bilibili://search", target.deepLinkUri)
        assertEquals("https://search.bilibili.com", target.webFallbackUrl)
    }

    @Test
    fun resolve_bilibiliSearchDeepLink_withExtraParameters_extractsKeywordCleanly() {
        val url = "bilibili://search?from_source=web&keyword=%E9%AC%BC%E7%81%AD&extra_tag=anime"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertTrue(target.isSearch)
        assertEquals("bilibili", target.siteName)
        assertEquals(url, target.deepLinkUri)
        assertEquals("https://search.bilibili.com/all?keyword=%E9%AC%BC%E7%81%AD", target.webFallbackUrl)
    }

    @Test
    fun resolve_bilibiliSearchDeepLink_withoutKeyword_fallsBackToSearchRoot() {
        val url = "bilibili://search"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertTrue(target.isSearch)
        assertEquals(url, target.deepLinkUri)
        assertEquals("https://search.bilibili.com", target.webFallbackUrl)
    }

    @Test
    fun resolve_bilibiliSearchWebUrl_withQueryParamBeforeKeyword_resolvesCorrectly() {
        val url =
            "https://search.bilibili.com/all?from_source=web_search&keyword=%E8%91%AC%E9%80%81%E7%9A%84%E8%8A%99%E8%8E%89%E8%8E%B2#results"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertTrue(target.isSearch)
        val expectedDeepLink = "bilibili://search?keyword=%E8%91%AC%E9%80%81%E7%9A%84%E8%8A%99%E8%8E%89%E8%8E%B2"
        assertEquals(expectedDeepLink, target.deepLinkUri)
        assertEquals(url, target.webFallbackUrl)
    }

    @Test
    fun encodeQueryParameter_unreservedAndSpecialCharacters() {
        assertEquals("abcXYZ0129-_.~", StreamingIntentResolver.encodeQueryParameter("abcXYZ0129-_.~"))
        assertEquals("%20", StreamingIntentResolver.encodeQueryParameter(" "))
        assertEquals("%26%3D%3F%2B%2F%23", StreamingIntentResolver.encodeQueryParameter("&=?+/#"))
        assertEquals("", StreamingIntentResolver.encodeQueryParameter(""))
    }

    @Test
    fun buildMikanUrl_withHttpPrefixedId_returnsDirectUrl() {
        val url = StreamingIntentResolver.buildMikanUrl(mikanId = "https://mikanani.me/Home/Bangumi/3233")
        assertEquals("https://mikanani.me/Home/Bangumi/3233", url)
    }

    @Test
    fun resolve_bilibiliVideoAvUrl_returnsVideoAvDeepLink() {
        val url = "https://www.bilibili.com/video/av170001"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertEquals("bilibili://video/av170001", target.deepLinkUri)
    }

    @Test
    fun resolve_bilibiliGeneralCustomScheme_returnsTarget() {
        val url = "bilibili://live/12345"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertEquals(url, target.deepLinkUri)
        assertEquals("https://www.bilibili.com", target.webFallbackUrl)
    }

    @Test
    fun resolve_bilibiliGeneralWeb_returnsTargetWithNullDeepLink() {
        val url = "https://www.bilibili.com/read/cv12345"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertNull(target.deepLinkUri)
        assertEquals(url, target.webFallbackUrl)
    }

    @Test
    fun resolve_gamerGeneralWeb_returnsTargetWithNullDeepLink() {
        val url = "https://ani.gamer.com.tw/index.php"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertNull(target.deepLinkUri)
        assertEquals("gamer", target.siteName)
    }

    @Test
    fun resolve_tencentUrl_returnsTargetWithPackage() {
        val url = "https://v.qq.com/x/cover/m44101.html"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertEquals("qq", target.siteName)
        assertTrue(target.packageNames.contains("com.tencent.qqlive"))
    }

    @Test
    fun resolve_youkuUrl_returnsTargetWithPackage() {
        val url = "https://v.youku.com/v_show/id_XMTI3.html"
        val target = StreamingIntentResolver.resolve(url)

        assertNotNull(target)
        assertEquals("youku", target.siteName)
        assertTrue(target.packageNames.contains("com.youku.phone"))
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

    @Test
    fun buildExternalPlayerTargets_httpUrl_returnsMpvAndVlcTargets() {
        val url = "https://example.com/anime/ep1.m3u8?token=abc"
        val targets = StreamingIntentResolver.buildExternalPlayerTargets(url)

        assertEquals(2, targets.size)

        val mpv = targets.first { it.packageName == "is.xyz.mpv" }
        assertEquals("mpv", mpv.appName)
        assertEquals(StreamingIntentResolver.MPV_PACKAGE_NAME, mpv.packageName)
        assertEquals(url, mpv.videoUrl)
        assertEquals("video/*", mpv.mimeType)
        assertEquals("android.intent.action.VIEW", mpv.action)

        val vlc = targets.first { it.packageName == "org.videolan.vlc" }
        assertEquals("VLC", vlc.appName)
        assertEquals(StreamingIntentResolver.VLC_PACKAGE_NAME, vlc.packageName)
        assertEquals(url, vlc.videoUrl)
        assertEquals("video/*", vlc.mimeType)
        assertEquals(ExternalPlayerTarget.ACTION_VIEW, vlc.action)
    }

    @Test
    fun buildExternalPlayerTargets_httpUrl_passesUrlThroughUnmodified() {
        val url = "http://192.168.1.10:8080/stream/%E8%AF%95%E9%AA%8C.ts"
        val targets = StreamingIntentResolver.buildExternalPlayerTargets(url)
        assertTrue(targets.all { it.videoUrl == url })
    }

    @Test
    fun buildExternalPlayerTargets_nonHttpUrl_returnsEmpty() {
        assertTrue(StreamingIntentResolver.buildExternalPlayerTargets("bilibili://bangumi/season/28770").isEmpty())
        assertTrue(StreamingIntentResolver.buildExternalPlayerTargets("rtsp://example.com/stream").isEmpty())
        assertTrue(StreamingIntentResolver.buildExternalPlayerTargets("/storage/emulated/0/video.mp4").isEmpty())
    }

    @Test
    fun buildExternalPlayerTargets_blankUrl_returnsEmpty() {
        assertTrue(StreamingIntentResolver.buildExternalPlayerTargets("").isEmpty())
        assertTrue(StreamingIntentResolver.buildExternalPlayerTargets("   ").isEmpty())
    }

    @Test
    fun isHttpStreamUrl_distinguishesHttpSchemes() {
        assertTrue(StreamingIntentResolver.isHttpStreamUrl("https://example.com/video.m3u8"))
        assertTrue(StreamingIntentResolver.isHttpStreamUrl("  http://example.com/video.mp4  "))
        assertTrue(StreamingIntentResolver.isHttpStreamUrl("HTTPS://EXAMPLE.COM/VIDEO.MP4"))
        assertTrue(!StreamingIntentResolver.isHttpStreamUrl("bilibili://bangumi/season/28770"))
        assertTrue(!StreamingIntentResolver.isHttpStreamUrl("ftp://example.com/video.mp4"))
        assertTrue(!StreamingIntentResolver.isHttpStreamUrl("https:/example.com/video.mp4"))
        assertTrue(!StreamingIntentResolver.isHttpStreamUrl(""))
    }
}
