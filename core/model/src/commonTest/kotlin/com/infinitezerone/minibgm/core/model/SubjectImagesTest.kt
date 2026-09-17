package com.infinitezerone.minibgm.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SubjectImagesTest {
    @Test
    fun bestImage_normalizesLegacyCommonUrlToModernCdnUrl() {
        val images =
            SubjectImages(
                common = "http://lain.bgm.tv/pic/cover/c/01/88/899_REwVW.jpg",
            )
        assertEquals(
            "https://lain.bgm.tv/r/400/pic/cover/l/01/88/899_REwVW.jpg",
            images.bestImage,
        )
    }

    @Test
    fun bestImage_preservesModernCdnUrl() {
        val images =
            SubjectImages(
                common = "https://lain.bgm.tv/r/400/pic/cover/l/01/88/899_REwVW.jpg",
            )
        assertEquals(
            "https://lain.bgm.tv/r/400/pic/cover/l/01/88/899_REwVW.jpg",
            images.bestImage,
        )
    }

    @Test
    fun bestImage_normalizesUncompressedLargeUrl() {
        val images =
            SubjectImages(
                large = "http://lain.bgm.tv/pic/cover/l/01/88/899_REwVW.jpg",
            )
        assertEquals(
            "https://lain.bgm.tv/r/400/pic/cover/l/01/88/899_REwVW.jpg",
            images.bestImage,
        )
    }
}
