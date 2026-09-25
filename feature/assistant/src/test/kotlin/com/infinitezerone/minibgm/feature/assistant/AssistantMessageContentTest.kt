package com.infinitezerone.minibgm.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantMessageContentTest {
    private val proposalJson = """{"status":"PENDING_CONFIRMATION","action":{"actionId":"act_1"}}"""

    @Test
    fun withoutCards_contentIsLeftUntouched() {
        // 没有卡片时，正文里的 JSON 可能就是用户要的规则，不该动它
        val content = "这是你要的规则：\n```json\n$proposalJson\n```"
        assertEquals(content, stripDuplicatedJsonBlock(content, hasActionCards = false))
    }

    @Test
    fun wholeJsonBody_withCards_becomesEmpty() {
        assertEquals("", stripDuplicatedJsonBlock(proposalJson, hasActionCards = true))
        assertEquals("", stripDuplicatedJsonBlock("  $proposalJson  ", hasActionCards = true))
    }

    @Test
    fun fencedJsonPlusProse_keepsProseOnly() {
        val content = "已为你整理好三条规则：\n\n```json\n$proposalJson\n```\n\n确认后即可导入。"
        val stripped = stripDuplicatedJsonBlock(content, hasActionCards = true)
        assertEquals("已为你整理好三条规则：\n\n\n\n确认后即可导入。", stripped)
        assertEquals(false, stripped.contains("actionId"))
    }

    @Test
    fun fencedJsonOnly_withCards_becomesEmpty() {
        val content = "```json\n$proposalJson\n```"
        assertEquals("", stripDuplicatedJsonBlock(content, hasActionCards = true))
    }

    @Test
    fun fencedNonJson_withCards_isKept() {
        // 围栏里不是 JSON（比如一段命令示例）就不该被当成提案剥掉
        val content = "试试这样：\n```bash\ncurl -s https://example.com/api\n```"
        assertEquals(content, stripDuplicatedJsonBlock(content, hasActionCards = true))
    }

    @Test
    fun unlabeledFence_isAlsoStrippedAsJson() {
        val content = "提案如下\n```\n$proposalJson\n```"
        assertEquals("提案如下", stripDuplicatedJsonBlock(content, hasActionCards = true))
    }

    @Test
    fun parseThinkingProcess_extractsThinkingAndLeavesMainContent() {
        val raw = "<think>\n考虑用户的追番偏好：推荐《葬送的芙莉莲》\n分析开播时间\n</think>\n推荐你观看《葬送的芙莉莲》，制作精良。"
        val parsed = parseThinkingProcess(raw)
        assertEquals("考虑用户的追番偏好：推荐《葬送的芙莉莲》\n分析开播时间", parsed.thinking)
        assertEquals("推荐你观看《葬送的芙莉莲》，制作精良。", parsed.mainContent)
    }

    @Test
    fun parseThinkingProcess_withoutThinkingTag_returnsOriginal() {
        val raw = "今天更新的番剧有《迷宫饭》。"
        val parsed = parseThinkingProcess(raw)
        org.junit.Assert.assertNull(parsed.thinking)
        assertEquals(raw, parsed.mainContent)
    }

    @Test
    fun parseThinkingProcess_emptyThinkingTag_returnsNullThinking() {
        val raw = "<think></think>直接回答正文"
        val parsed = parseThinkingProcess(raw)
        org.junit.Assert.assertNull(parsed.thinking)
        assertEquals("直接回答正文", parsed.mainContent)
    }
}
