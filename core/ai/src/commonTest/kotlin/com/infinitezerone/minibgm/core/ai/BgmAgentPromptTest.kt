package com.infinitezerone.minibgm.core.ai

import kotlin.test.Test
import kotlin.test.assertTrue

class BgmAgentPromptTest {
    @Test
    fun `system prompt requires tool grounded playback data`() {
        assertTrue(BGM_AGENT_SYSTEM_PROMPT.contains("findPlayableSources"), "找源必须显式绑定到工具")
        assertTrue(BGM_AGENT_SYSTEM_PROMPT.contains("编造"), "必须禁止凭记忆拼 URL")
    }

    /**
     * 站点逆向 SOP 允许模型自己给出探测 URL，作为交换，规则落库必须经过用户确认提案；
     * 少了这一条，探测工具就等于把"写用户配置"的权限交给了模型。
     */
    @Test
    fun `system prompt gates rule writes behind user confirmation`() {
        assertTrue(
            BGM_AGENT_SYSTEM_PROMPT.contains("proposePlaybackRule"),
            "自测通过的规则只能经 proposePlaybackRule 生成待确认提案，不得直接落库",
        )
        assertTrue(
            BGM_AGENT_SYSTEM_PROMPT.contains("PENDING_CONFIRMATION"),
            "提示词必须交代待确认提案的交付方式",
        )
    }

    /**
     * SOP 必须先静态直读再动态审计：动态渲染一次 30 秒级成本，
     * 静态站（模板站大头）不该为它买单；写进测试防止后续改提示词时把顺序改回去。
     */
    @Test
    fun `system prompt instructs static-first recording before dynamic audit`() {
        val prompt = BGM_AGENT_SYSTEM_PROMPT
        val staticPos = prompt.indexOf("recordPlaybackRuleFromStaticPage")
        val tracePos = prompt.indexOf("traceNetworkTraffic")
        assertTrue(staticPos in 0 until tracePos, "SOP 必须先静态直读（recordPlaybackRuleFromStaticPage），动态审计（traceNetworkTraffic）只是兜底")
    }
}
