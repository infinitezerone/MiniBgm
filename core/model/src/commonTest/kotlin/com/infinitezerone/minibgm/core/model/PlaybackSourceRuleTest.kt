package com.infinitezerone.minibgm.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun isResolvable_只有取源侧的专用解析器才算有效组合() {
        fun rule(
            kind: PlaybackRuleKind,
            parserType: RuleParserType,
            steps: List<PipelineStep> = emptyList(),
        ) = PlaybackSourceRule(
            id = "r",
            name = "n",
            urlTemplate = "https://example.com/s?q={title}",
            kind = kind,
            parserType = parserType,
            pipeline = steps,
        )

        val fetchStep = listOf(PipelineStep(action = StepAction.FETCH))

        // AUTO 靠智能嗅探，两种 kind 都有执行路径
        assertTrue(rule(PlaybackRuleKind.PAGE, RuleParserType.AUTO).isResolvable)
        assertTrue(rule(PlaybackRuleKind.SOURCE, RuleParserType.AUTO).isResolvable)
        // 专用解析器只在 SOURCE 下被分发
        assertTrue(rule(PlaybackRuleKind.SOURCE, RuleParserType.MACCMS).isResolvable)
        assertTrue(rule(PlaybackRuleKind.SOURCE, RuleParserType.PIPELINE, fetchStep).isResolvable)
        assertFalse(rule(PlaybackRuleKind.PAGE, RuleParserType.PIPELINE, fetchStep).isResolvable)
        assertFalse(rule(PlaybackRuleKind.PAGE, RuleParserType.STREMIO).isResolvable)
        // 给了 pipeline 却没声明 PIPELINE 解析器，步骤同样会被丢掉
        assertFalse(rule(PlaybackRuleKind.SOURCE, RuleParserType.AUTO, fetchStep).isResolvable)
    }
}
