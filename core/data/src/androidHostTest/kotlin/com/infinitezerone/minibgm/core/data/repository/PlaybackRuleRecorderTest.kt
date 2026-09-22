package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.CapturedNetworkCall
import com.infinitezerone.minibgm.core.model.NetworkAuditTrace
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.StepAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackRuleRecorderTest {
    @Test
    fun `归纳出页面到接口到抽取的三段骨架`() {
        val title = "葬送的芙莉莲"
        val encoded = PlaybackSourceRule.encodeParam(title)
        val trace =
            NetworkAuditTrace(
                pageUrl = "https://site.example.tv/search?q=$encoded",
                finalUrl = "https://site.example.tv/search?q=$encoded",
                calls =
                    listOf(
                        CapturedNetworkCall(
                            url = "https://api.example.tv/vod?ac=detail&wd=$encoded",
                            isApi = true,
                            requestHeaders = mapOf("Referer" to "https://site.example.tv/", "Accept" to "*/*"),
                        ),
                        CapturedNetworkCall(url = "https://cdn.example.tv/hls/1.m3u8", isMedia = true),
                    ),
            )

        val draft = PlaybackRuleRecorder.recordFromTrace(trace, ruleName = "示例站", title = title, ep = "1")

        assertEquals(3, draft.steps.size)
        assertEquals(StepAction.FETCH, draft.steps[0].action)
        assertEquals(StepAction.FETCH, draft.steps[1].action)
        assertEquals(StepAction.EXTRACT_STREAM, draft.steps[2].action)
        assertTrue(draft.steps[0].urlTemplate.contains("{title}"), "片名应被参数化，规则才能复用到别的番")
        assertTrue(draft.steps[1].urlTemplate.contains("{title}"))
        // 只留可重放的头；Accept 这类由抓取层自己决定，录下来反而会覆盖掉默认值
        assertEquals(mapOf("Referer" to "https://site.example.tv/"), draft.steps[1].headers)
        assertEquals("https://cdn.example.tv/hls/1.m3u8", draft.mediaUrl)
        assertTrue(draft.steps[2].regex.isBlank(), "正则必须留空等补齐——审计看不到响应正文")
    }

    @Test
    fun `片名的原文与编码形态都会被参数化`() {
        val title = "葬送的芙莉莲"
        val encoded = PlaybackSourceRule.encodeParam(title)

        assertEquals(
            "https://s.example.tv/a/{title}?q={title}",
            PlaybackRuleRecorder.parametrizeUrl("https://s.example.tv/a/$title?q=$encoded", title),
        )
    }

    @Test
    fun `话数不自动参数化，只在提示里要求自行替换`() {
        // 按值匹配会连 &page=1 一起改坏——这类判断交给调用方，代码不猜
        assertEquals(
            "https://s.example.tv/p/12?ep=1&page=1",
            PlaybackRuleRecorder.parametrizeUrl("https://s.example.tv/p/12?ep=1&page=1", title = ""),
        )
    }

    @Test
    fun `POST 接口会明确提示请求体录不到`() {
        val trace =
            NetworkAuditTrace(
                pageUrl = "https://s.example.tv/ep/1",
                calls =
                    listOf(
                        CapturedNetworkCall(url = "https://api.example.tv/stream", method = "POST", isApi = true),
                    ),
            )

        val draft = PlaybackRuleRecorder.recordFromTrace(trace, ruleName = "示例站", title = "某番", ep = "1")

        assertEquals("POST", draft.steps[1].method)
        assertTrue(
            draft.notes.any { it.contains("看不到请求体") },
            "请求体录不到是必须显式告知的缺口，否则模型会以为骨架已经完整",
        )
    }

    @Test
    fun `审计已抓到媒体时说明这一趟不必依赖规则`() {
        val trace =
            NetworkAuditTrace(
                pageUrl = "https://s.example.tv/ep/1",
                calls = listOf(CapturedNetworkCall(url = "https://cdn.example.tv/1.mp4", isMedia = true)),
                mediaSources = listOf(PlayableSource(url = "https://cdn.example.tv/1.mp4")),
            )

        val draft = PlaybackRuleRecorder.recordFromTrace(trace, ruleName = "示例站", title = "某番", ep = "1")

        assertEquals("https://cdn.example.tv/1.mp4", draft.mediaUrl)
        assertTrue(draft.notes.any { it.contains("直接抓到") })
    }

    @Test
    fun `抓不到任何请求时提示这类站点需要先点击`() {
        val trace = NetworkAuditTrace(pageUrl = "https://s.example.tv/ep/1")

        val draft = PlaybackRuleRecorder.recordFromTrace(trace, ruleName = "示例站", title = "某番", ep = "1")

        assertEquals(2, draft.steps.size, "至少留下页面抓取与抽取两步")
        assertEquals(null, draft.mediaUrl)
        assertTrue(
            draft.notes.any { it.contains("点击") },
            "要指向纯被动观察这个结构性限制，而不是让调用方反复重试",
        )
    }
}
