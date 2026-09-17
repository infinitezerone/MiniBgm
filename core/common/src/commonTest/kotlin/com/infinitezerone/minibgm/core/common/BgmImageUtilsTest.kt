package com.infinitezerone.minibgm.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class BgmImageUtilsTest {
    @Test
    fun toSecureUrl_upgradesHttpToHttps() {
        assertEquals(
            "https://lain.bgm.tv/pic/cover/l/sample.jpg",
            BgmImageUtils.toSecureUrl("http://lain.bgm.tv/pic/cover/l/sample.jpg"),
        )
        assertEquals(
            "https://lain.bgm.tv/pic/cover/l/sample.jpg",
            BgmImageUtils.toSecureUrl("https://lain.bgm.tv/pic/cover/l/sample.jpg"),
        )
    }

    @Test
    fun optimizeBgmImageUrl_optimizesUncompressedCover() {
        val original = "http://lain.bgm.tv/pic/cover/l/ca/27/12105_jp.jpg"
        val expected = "https://lain.bgm.tv/r/400/pic/cover/l/ca/27/12105_jp.jpg"
        assertEquals(expected, BgmImageUtils.optimizeBgmImageUrl(original))
    }

    @Test
    fun optimizeBgmImageUrl_optimizesUncompressedCharacterAndPerson() {
        val original = "https://lain.bgm.tv/pic/crt/l/56/78/sample.jpg"
        val expected = "https://lain.bgm.tv/r/400/pic/crt/l/56/78/sample.jpg"
        assertEquals(expected, BgmImageUtils.optimizeBgmImageUrl(original))
    }

    @Test
    fun optimizeBgmImageUrl_optimizesUncompressedUserAvatar() {
        val original = "https://lain.bgm.tv/pic/user/l/000/00/00/1.jpg"
        val expected = "https://lain.bgm.tv/r/400/pic/user/l/000/00/00/1.jpg"
        assertEquals(expected, BgmImageUtils.optimizeBgmImageUrl(original))
    }

    @Test
    fun optimizeBgmImageUrl_isIdempotentForExistingCdnUrls() {
        val cdn400 = "https://lain.bgm.tv/r/400/pic/cover/l/sample.jpg"
        assertEquals(cdn400, BgmImageUtils.optimizeBgmImageUrl(cdn400))

        val cdn200 = "https://lain.bgm.tv/r/200/pic/cover/l/sample.jpg"
        assertEquals(cdn200, BgmImageUtils.optimizeBgmImageUrl(cdn200))
    }

    @Test
    fun optimizeBgmImageUrl_normalizesLegacyCroppedAndMediumUrls() {
        val cropped = "https://lain.bgm.tv/pic/cover/c/sample.jpg"
        assertEquals("https://lain.bgm.tv/r/400/pic/cover/l/sample.jpg", BgmImageUtils.optimizeBgmImageUrl(cropped))

        val medium = "https://lain.bgm.tv/pic/cover/m/sample.jpg"
        assertEquals("https://lain.bgm.tv/r/400/pic/cover/l/sample.jpg", BgmImageUtils.optimizeBgmImageUrl(medium))
    }

    @Test
    fun optimizeBgmImageUrl_handlesExternalAndBlankUrls() {
        assertEquals("", BgmImageUtils.optimizeBgmImageUrl(""))
        assertEquals(
            "https://example.com/pic/cover/l/test.jpg",
            BgmImageUtils.optimizeBgmImageUrl("http://example.com/pic/cover/l/test.jpg"),
        )
    }
}
