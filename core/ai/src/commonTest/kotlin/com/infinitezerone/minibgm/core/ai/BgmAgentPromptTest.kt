package com.infinitezerone.minibgm.core.ai

import kotlin.test.Test
import kotlin.test.assertTrue

class BgmAgentPromptTest {
    @Test
    fun `system prompt requires tool grounded links and plain text output`() {
        assertTrue(BGM_AGENT_SYSTEM_PROMPT.contains("findWatchPages"), "找源必须显式绑定到工具")
        assertTrue(BGM_AGENT_SYSTEM_PROMPT.contains("编造"), "必须禁止凭记忆拼 URL")
        assertTrue(BGM_AGENT_SYSTEM_PROMPT.contains("禁止 Markdown"), "必须固定纯文本输出")
        assertTrue(BGM_AGENT_SYSTEM_PROMPT.contains("网页链接"), "必须重申只给页面链接的能力边界")
    }
}
