package com.infinitezerone.minibgm.core.ai

import kotlin.test.Test
import kotlin.test.assertTrue

class BgmAgentPromptTest {
    @Test
    fun `system prompt requires tool grounded playback data`() {
        assertTrue(BGM_AGENT_SYSTEM_PROMPT.contains("findPlayableSources"), "找源必须显式绑定到工具")
        assertTrue(BGM_AGENT_SYSTEM_PROMPT.contains("编造"), "必须禁止凭记忆拼 URL")
        assertTrue(BGM_AGENT_SYSTEM_PROMPT.contains("原样"), "工具 JSON 必须原样转交客户端渲染")
        assertTrue(BGM_AGENT_SYSTEM_PROMPT.contains("禁止 Markdown"), "必须固定纯文本输出")
    }
}
