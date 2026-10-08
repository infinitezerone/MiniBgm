package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.feature.subject.components.bgmReactionStickerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentReactionStickerTest {
    @Test
    fun officialReactionValuesMapToCorrectTvSmiles() {
        assertEquals("https://lain.bgm.tv/img/smiles/tv/44.gif", bgmReactionStickerUrl(0))
        assertEquals("https://lain.bgm.tv/img/smiles/tv/101.gif", bgmReactionStickerUrl(140))
        assertEquals("https://lain.bgm.tv/img/smiles/tv/102.gif", bgmReactionStickerUrl(141))
        assertEquals("https://lain.bgm.tv/img/smiles/tv/40.gif", bgmReactionStickerUrl(79))
        assertEquals("https://lain.bgm.tv/img/smiles/tv/15.gif", bgmReactionStickerUrl(54))
    }

    @Test
    fun standardSmileyValuesFallbackToPaddedNumber() {
        assertEquals("https://lain.bgm.tv/img/smiles/tv/01.gif", bgmReactionStickerUrl(1))
        assertEquals("https://lain.bgm.tv/img/smiles/tv/38.gif", bgmReactionStickerUrl(38))
    }

    @Test
    fun invalidValuesReturnNull() {
        assertNull(bgmReactionStickerUrl(-1))
        assertNull(bgmReactionStickerUrl(9999))
    }
}
