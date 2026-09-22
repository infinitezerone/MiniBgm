package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.CapturedNetworkCall
import com.infinitezerone.minibgm.core.model.NetworkAuditTrace
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeFetch(
    private val responses: Map<String, String>,
) : PageFetchService {
    val requested = mutableListOf<String>()
    val sentHeaders = mutableMapOf<String, Map<String, String>>()

    override suspend fun fetchHtml(
        url: String,
        requestHeaders: Map<String, String>,
    ): FetchedPage? {
        requested += url
        sentHeaders[url] = requestHeaders
        return responses[url]?.let { FetchedPage(url = url, html = it) }
    }

    override suspend fun postForm(
        url: String,
        formData: Map<String, String>,
        requestHeaders: Map<String, String>,
    ): FetchedPage? = null
}

class PlaybackRuleSampleReplayerTest {
    private val pageUrl = "https://site.example.tv/play/1"

    private fun trace(vararg calls: CapturedNetworkCall) = NetworkAuditTrace(pageUrl = pageUrl, finalUrl = pageUrl, calls = calls.toList())

    private fun replayer(responses: Map<String, String>) = PlaybackRuleSampleReplayerImpl(pageFetchService = FakeFetch(responses))

    @Test
    fun `重放同站 GET 接口并带回响应片段`() =
        runTest {
            val apiUrl = "https://api.example.tv/vod?ac=detail&wd=x"
            val fetch = FakeFetch(mapOf(apiUrl to """{"list":[{"vod_play_url":"第1集${'$'}http://cdn/x.m3u8"}]}"""))
            val replayer = PlaybackRuleSampleReplayerImpl(pageFetchService = fetch)

            val samples =
                replayer.replayApiSamples(
                    trace(
                        CapturedNetworkCall(
                            url = apiUrl,
                            isApi = true,
                            requestHeaders = mapOf("Referer" to "https://site.example.tv/", "Accept" to "*/*"),
                        ),
                    ),
                )

            assertEquals(1, samples.size)
            assertTrue(samples[0].ok)
            assertTrue(samples[0].bodyExcerpt.contains("vod_play_url"), "样本必须带回真实字段名")
            // 只带可重放的头：站点常靠 Referer 判来源，Accept 由抓取层决定
            assertEquals(mapOf("Referer" to "https://site.example.tv/"), fetch.sentHeaders[apiUrl])
        }

    @Test
    fun `跨站接口不重放，也不发出请求`() =
        runTest {
            val fetch = FakeFetch(emptyMap())
            val replayer = PlaybackRuleSampleReplayerImpl(pageFetchService = fetch)

            val samples =
                replayer.replayApiSamples(
                    trace(CapturedNetworkCall(url = "https://evil.example.net/steal?x=1", isApi = true)),
                )

            assertEquals(1, samples.size)
            assertFalse(samples[0].ok)
            assertTrue(samples[0].bodyExcerpt.isEmpty())
            // 关键断言：拒绝之后真的没有发出去，而不是"发了但结果没用"
            assertTrue(fetch.requested.isEmpty())
        }

    @Test
    fun `内网地址不重放`() =
        runTest {
            val fetch = FakeFetch(emptyMap())
            val replayer = PlaybackRuleSampleReplayerImpl(pageFetchService = fetch)
            val internalTrace =
                NetworkAuditTrace(
                    pageUrl = "http://192.168.1.10/admin",
                    finalUrl = "http://192.168.1.10/admin",
                    calls = listOf(CapturedNetworkCall(url = "http://192.168.1.10/api?d=1", isApi = true)),
                )

            val samples = replayer.replayApiSamples(internalTrace)

            assertEquals(1, samples.size)
            assertFalse(samples[0].ok)
            assertTrue(fetch.requested.isEmpty(), "内网地址一次都不许发出去")
        }

    @Test
    fun `POST 接口不重放`() =
        runTest {
            val fetch = FakeFetch(emptyMap())
            val replayer = PlaybackRuleSampleReplayerImpl(pageFetchService = fetch)

            val samples =
                replayer.replayApiSamples(
                    trace(
                        CapturedNetworkCall(
                            url = "https://api.example.tv/post",
                            method = "POST",
                            isApi = true,
                        ),
                    ),
                )

            assertTrue(samples.isEmpty(), "请求体重放不了，列出来只会误导")
            assertTrue(fetch.requested.isEmpty())
        }

    @Test
    fun `响应过长按上限截断`() =
        runTest {
            val apiUrl = "https://api.example.tv/long"
            val fetch = FakeFetch(mapOf(apiUrl to "x".repeat(MAX_SAMPLE_CHARS + 500)))
            val replayer = PlaybackRuleSampleReplayerImpl(pageFetchService = fetch)

            val samples = replayer.replayApiSamples(trace(CapturedNetworkCall(url = apiUrl, isApi = true)))

            assertTrue(samples[0].ok)
            assertTrue(samples[0].bodyExcerpt.contains("已截断"))
            assertTrue(samples[0].bodyExcerpt.length < MAX_SAMPLE_CHARS + 100)
        }

    @Test
    fun `重放条数有上限`() =
        runTest {
            val urls = (1..5).map { "https://api.example.tv/v$it" }
            val fetch = FakeFetch(urls.associateWith { "{}" })
            val replayer = PlaybackRuleSampleReplayerImpl(pageFetchService = fetch)

            val samples = replayer.replayApiSamples(trace(*urls.map { CapturedNetworkCall(url = it, isApi = true) }.toTypedArray()))

            assertEquals(MAX_REPLAY_CALLS, samples.size)
            assertEquals(MAX_REPLAY_CALLS, fetch.requested.size)
        }

    @Test
    fun `同站判定只认相同主机、子域或同注册域`() {
        assertTrue(isSameSite("api.example.tv", "example.tv"))
        assertTrue(isSameSite("example.tv", "api.example.tv"))
        assertTrue(isSameSite("example.tv", "example.tv"))
        // 站点把接口放独立子域很常见：页面 www、接口 api，这不是跨站
        assertTrue(isSameSite("api.example.tv", "www.example.tv"))
        assertFalse(isSameSite("example.tv", "example.net"))
        // 后缀相近但不同站，不能靠 endsWith 的字符串比较放过
        assertFalse(isSameSite("notexample.tv", "example.tv"))
    }

    @Test
    fun `内网与非法 IP 形态一律判为私网`() {
        assertTrue(isPrivateHost("192.168.1.1"))
        assertTrue(isPrivateHost("10.0.0.1"))
        assertTrue(isPrivateHost("172.20.0.1"))
        assertTrue(isPrivateHost("127.0.0.1"))
        assertTrue(isPrivateHost("169.254.1.1"))
        assertTrue(isPrivateHost("100.64.0.1"))
        assertTrue(isPrivateHost("localhost"))
        assertTrue(isPrivateHost("nas.local"))
        assertTrue(isPrivateHost("router"))
        // 段数不对的全数字形态：不同解析器折算结果不一致，直接拒绝
        assertTrue(isPrivateHost("192.168.1"))
        assertFalse(isPrivateHost("api.example.tv"))
        assertFalse(isPrivateHost("8.8.8.8"))
        assertFalse(isPrivateHost("172.32.0.1"))
    }

    @Test
    fun `host 解析挑出端口与 userinfo`() {
        assertEquals("api.example.tv", hostOf("https://api.example.tv:8443/x?y=1"))
        assertEquals("api.example.tv", hostOf("https://user:pw@api.example.tv/x"))
        assertEquals(null, hostOf("ftp://api.example.tv/x"))
        assertEquals(null, hostOf("http://[::1]:8080/x"))
    }
}
