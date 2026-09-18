package com.infinitezerone.minibgm.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackSourceRuleTest {
    @Test
    fun resolveUrl_replacesAllPlaceholdersCorrectly() {
        val rule =
            PlaybackSourceRule(
                id = "rule-1",
                name = "测试播放源",
                urlTemplate = "https://example.com/play?title={title}&ep={ep}&sId={subjectId}&eId={episodeId}",
            )

        val resolved =
            rule.resolveUrl(
                title = "葬送的芙莉莲",
                ep = "5",
                subjectId = 412435L,
                episodeId = 1005L,
            )

        assertTrue(resolved.contains("title=%E8%91%AC%E9%80%81%E7%9A%84%E8%8A%99%E8%8E%89%E8%8E%B2"))
        assertTrue(resolved.contains("ep=5"))
        assertTrue(resolved.contains("sId=412435"))
        assertTrue(resolved.contains("eId=1005"))
    }

    @Test
    fun resolveUrl_encodesSpecialCharactersInParameters() {
        val rule =
            PlaybackSourceRule(
                id = "rule-2",
                name = "特殊字符源",
                urlTemplate = "https://api.example.com/v1/{title}/{ep}.m3u8",
            )

        val resolved =
            rule.resolveUrl(
                title = "命运/待夜 [Fate/stay night]",
                ep = "01",
            )

        assertTrue(resolved.contains("Fate"))
        assertTrue(resolved.contains("%2F")) // slash encoded
        assertTrue(resolved.endsWith("01.m3u8"))
    }

    @Test
    fun encodeParam_preservesUnreservedCharacters() {
        val unreserved = "abc-ABC_123.test~"
        val encoded = PlaybackSourceRule.encodeParam(unreserved)
        assertEquals(unreserved, encoded)
    }

    @Test
    fun encodeParam_encodesSpacesAsPercent20() {
        val withSpace = "Hello World"
        val encoded = PlaybackSourceRule.encodeParam(withSpace)
        assertEquals("Hello%20World", encoded)
    }
}
