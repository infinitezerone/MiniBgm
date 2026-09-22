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

    @Test
    fun `有响应样本时提示照样本写正则并带上样本`() {
        val trace =
            NetworkAuditTrace(
                pageUrl = "https://s.example.tv/ep/1",
                calls = listOf(CapturedNetworkCall(url = "https://api.example.tv/vod?wd=x", isApi = true)),
            )
        val samples =
            listOf(
                ApiResponseSample(url = "https://api.example.tv/vod?wd=x", ok = true, bodyExcerpt = """{"vod_play_url":"…"}"""),
            )

        val draft =
            PlaybackRuleRecorder.recordFromTrace(trace, ruleName = "示例站", title = "某番", ep = "1", apiSamples = samples)

        assertEquals(samples, draft.apiSamples)
        assertTrue(
            draft.notes.any { it.contains("照着片段") },
            "有样本就该要求照样本写，而不是继续留着'审计看不到响应正文'那句",
        )
        assertTrue(draft.notes.none { it.contains("没有可用响应样本") })
    }

    @Test
    fun `样本全部重放失败时说明原因而不是让调用方重试`() {
        val trace =
            NetworkAuditTrace(
                pageUrl = "https://s.example.tv/ep/1",
                calls = listOf(CapturedNetworkCall(url = "https://api.example.tv/vod?wd=x", isApi = true)),
            )
        val samples =
            listOf(ApiResponseSample(url = "https://api.example.tv/vod?wd=x", ok = false, note = "被拒绝"))

        val draft =
            PlaybackRuleRecorder.recordFromTrace(trace, ruleName = "示例站", title = "某番", ep = "1", apiSamples = samples)

        assertTrue(draft.notes.any { it.contains("没取到响应") })
        assertTrue(draft.notes.none { it.contains("照着片段") })
    }

    @Test
    fun `静态页直读抽到直链时归纳骨架并附源码上下文`() {
        val mediaUrl = "https://cdn.example.tv/hls/1.m3u8"
        val html =
            """<html><body><div class="player" data-url="$mediaUrl" data-referer="1"></div><script>var p="ok";</script></body></html>"""

        val draft =
            PlaybackRuleRecorder.recordFromStaticPage(
                pageUrl = "https://s.example.tv/ep/1",
                html = html,
                ruleName = "示例站",
                title = "某番",
                ep = "1",
            )

        assertEquals(mediaUrl, draft.mediaUrl)
        assertEquals(2, draft.steps.size, "页面抓取与抽取各一步")
        assertEquals(StepAction.EXTRACT_STREAM, draft.steps[1].action)
        assertEquals("https://s.example.tv/ep/1", draft.steps[1].headers["Referer"], "静态路径没有捕获头，Referer 用播放页地址")
        assertTrue(
            draft.notes.any { it.contains("源码上下文") && it.contains("data-url") },
            "直链的页面上下文要原样交给调用方，正则才可能照真实源码写",
        )
        assertTrue(draft.apiSamples.isEmpty(), "静态路径正文在手，不需要 apiSamples")
    }

    @Test
    fun `静态页带 MacCMS 特征时建议直接用标准解析器`() {
        val html = """<script>var player_aaaa = {"url":"https://cdn.example.tv/1.m3u8"};</script>"""

        val draft =
            PlaybackRuleRecorder.recordFromStaticPage(
                pageUrl = "https://s.example.tv/ep/1",
                html = html,
                ruleName = "示例站",
                title = "某番",
            )

        assertTrue(
            draft.notes.any { it.contains("parserType=MACCMS") },
            "引擎已有 MacCMS 标准解析，pipeline 是绕远路",
        )
    }

    @Test
    fun `静态页直链是百分号编码时解码后再归纳`() {
        val plain = "https://play.xfvod.pro:8088/Z/01.mp4"
        val html = """<script>var player_aaaa = {"url":"${PlaybackSourceRule.encodeParam(plain)}"};</script>"""

        val draft =
            PlaybackRuleRecorder.recordFromStaticPage(
                pageUrl = "https://s.example.tv/ep/1",
                html = html,
                ruleName = "示例站",
                title = "某番",
            )

        assertEquals(plain, draft.mediaUrl, "MacCMS 播放器配置的编码直链解码后应能命中（实测 moonci 类站点的形态）")
        assertTrue(draft.notes.any { it.contains("百分号编码") }, "要说明直链来自解码，正则才照明文形态写")
    }

    @Test
    fun `静态页没有直链时明确指向网络审计而不是让调用方重试`() {
        val html = """<html><body><div id="app"></div><script src="/app.js"></script></body></html>"""

        val draft =
            PlaybackRuleRecorder.recordFromStaticPage(
                pageUrl = "https://s.example.tv/ep/1",
                html = html,
                ruleName = "示例站",
                title = "某番",
            )

        assertEquals(null, draft.mediaUrl)
        assertTrue(
            draft.notes.any { it.contains("traceNetworkTraffic") },
            "JS 渲染站是静态路径的结构性边界，要说清出路在哪",
        )
    }

    @Test
    fun `静态页内嵌 iframe 时提示跟进播放器页`() {
        val html =
            """<html><body><iframe src="https://p.example.tv/embed/9"></iframe></body></html>"""

        val draft =
            PlaybackRuleRecorder.recordFromStaticPage(
                pageUrl = "https://s.example.tv/ep/1",
                html = html,
                ruleName = "示例站",
                title = "某番",
            )

        assertTrue(
            draft.notes.any { it.contains("https://p.example.tv/embed/9") },
            "iframe 地址要原样列出，调用方才能对它再走一次直读",
        )
    }

    @Test
    fun `静态页直读同样把片名参数化并要求自行替换集数`() {
        val title = "葬送的芙莉莲"
        val encoded = PlaybackSourceRule.encodeParam(title)
        val html = """<html><body>ok</body></html>"""

        val draft =
            PlaybackRuleRecorder.recordFromStaticPage(
                pageUrl = "https://s.example.tv/search?q=$encoded",
                html = html,
                ruleName = "示例站",
                title = title,
                ep = "2",
            )

        assertTrue(draft.steps[0].urlTemplate.contains("{title}"))
        assertTrue(draft.notes.any { it.contains("{ep}") })
    }
}
