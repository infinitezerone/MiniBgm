package com.infinitezerone.minibgm.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BgmUrlsTest {
    @Test
    fun isBgmDomain_correctlyIdentifiesDomains() {
        assertTrue("bgm.tv".isBgmDomain)
        assertTrue("lain.bgm.tv".isBgmDomain)
        assertTrue("api.bgm.tv".isBgmDomain)
        assertTrue("bangumi.tv".isBgmDomain)
        assertTrue("chii.in".isBgmDomain)
        assertTrue("https://lain.bgm.tv/pic/cover/l/sample.jpg".isBgmDomain)

        assertFalse("example.com".isBgmDomain)
        assertFalse("i0.hdslb.com".isBgmDomain)
        assertFalse("wx1.sinaimg.cn".isBgmDomain)
        assertFalse("notbgm.tv.attacker.com".isBgmDomain)
        assertFalse("".isBgmDomain)
    }

    @Test
    fun toSecureUrl_upgradesHttpToHttpsForBgm() {
        assertEquals(
            "https://lain.bgm.tv/pic/cover/l/sample.jpg",
            "http://lain.bgm.tv/pic/cover/l/sample.jpg".toSecureUrl(),
        )
        assertEquals(
            "https://lain.bgm.tv/pic/cover/l/sample.jpg",
            "https://lain.bgm.tv/pic/cover/l/sample.jpg".toSecureUrl(),
        )
        // 外部第三方图片不属于 Bangumi 域名，不得盲目升轨为 https，避免破坏不支持 HTTPS 的外部图床
        assertEquals(
            "http://example.com/pic/cover/l/test.jpg",
            "http://example.com/pic/cover/l/test.jpg".toSecureUrl(),
        )
    }

    @Test
    fun toBgmCdnUrl_optimizesUncompressedCover() {
        val original = "http://lain.bgm.tv/pic/cover/l/ca/27/12105_jp.jpg"
        val expected = "https://lain.bgm.tv/r/400/pic/cover/l/ca/27/12105_jp.jpg"
        assertEquals(expected, original.toBgmCdnUrl())
    }

    @Test
    fun toBgmCdnUrl_optimizesUncompressedCharacterAndPerson() {
        val original = "https://lain.bgm.tv/pic/crt/l/56/78/sample.jpg"
        val expected = "https://lain.bgm.tv/r/400/pic/crt/l/56/78/sample.jpg"
        assertEquals(expected, original.toBgmCdnUrl())
    }

    @Test
    fun toBgmCdnUrl_optimizesUncompressedUserAvatar() {
        val original = "https://lain.bgm.tv/pic/user/l/000/00/00/1.jpg"
        val expected = "https://lain.bgm.tv/r/400/pic/user/l/000/00/00/1.jpg"
        assertEquals(expected, original.toBgmCdnUrl())
    }

    @Test
    fun toBgmCdnUrl_isIdempotentForExistingCdnUrls() {
        val cdn400 = "https://lain.bgm.tv/r/400/pic/cover/l/sample.jpg"
        assertEquals(cdn400, cdn400.toBgmCdnUrl())

        val cdn200 = "https://lain.bgm.tv/r/200/pic/cover/l/sample.jpg"
        assertEquals(cdn200, cdn200.toBgmCdnUrl())
    }

    @Test
    fun toBgmCdnUrl_normalizesLegacyCroppedAndMediumUrls() {
        val cropped = "https://lain.bgm.tv/pic/cover/c/sample.jpg"
        assertEquals("https://lain.bgm.tv/r/400/pic/cover/l/sample.jpg", cropped.toBgmCdnUrl())

        val medium = "https://lain.bgm.tv/pic/cover/m/sample.jpg"
        assertEquals("https://lain.bgm.tv/r/400/pic/cover/l/sample.jpg", medium.toBgmCdnUrl())
    }

    @Test
    fun toBgmCdnUrl_handlesExternalAndBlankUrls() {
        assertEquals("", "".toBgmCdnUrl())
        assertEquals(
            "http://example.com/pic/cover/l/test.jpg",
            "http://example.com/pic/cover/l/test.jpg".toBgmCdnUrl(),
        )
        assertEquals(
            "https://example.com/pic/cover/l/test.jpg",
            "https://example.com/pic/cover/l/test.jpg".toBgmCdnUrl(),
        )
    }
}
