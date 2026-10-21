package com.infinitezerone.minibgm.core.data.repository.resolver.parse

import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.TvBoxParse
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TvBoxParseResolverTest {
    private class FakePageFetchService(
        private val pages: Map<String, String> = emptyMap(),
    ) : PageFetchService {
        override suspend fun fetchHtml(
            url: String,
            requestHeaders: Map<String, String>,
        ): FetchedPage? {
            val content = pages[url] ?: return null
            return FetchedPage(url = url, html = content)
        }

        override suspend fun postForm(
            url: String,
            formData: Map<String, String>,
            requestHeaders: Map<String, String>,
        ): FetchedPage? = null
    }

    @Test
    fun `resolveParse_目标为直链时直接返回无需请求解析器`() =
        runTest {
            val resolver = TvBoxParseResolverImpl(pageFetchService = FakePageFetchService())
            val directUrl = "https://cdn.example.com/anime/1.m3u8"

            val sources =
                resolver.resolveParse(
                    targetUrl = directUrl,
                    epNumber = 1f,
                    siteName = "直链站",
                    candidateParses = listOf(TvBoxParse(name = "备用", type = 1, url = "https://jx.com/?url=")),
                )

            assertEquals(1, sources.size)
            assertEquals(directUrl, sources.first().url)
            assertEquals(PlaylistEntryKind.DIRECT, sources.first().kind)
        }

    @Test
    fun `resolveParse_type1_JSON解析接口成功获取直链与请求头`() =
        runTest {
            val targetPage = "https://v.qq.com/x/cover/m4v12345.html"
            val jxUrl = "https://api.jx.com/parse?url=https://v.qq.com/x/cover/m4v12345.html"
            val jsonResponse =
                """
                {
                    "code": 200,
                    "msg": "success",
                    "url": "https://stream.jx.com/live/ep01.m3u8",
                    "header": {
                        "User-Agent": "MiniBgmSpider/1.0",
                        "Referer": "https://v.qq.com/"
                    }
                }
                """.trimIndent()

            val resolver =
                TvBoxParseResolverImpl(
                    pageFetchService = FakePageFetchService(mapOf(jxUrl to jsonResponse)),
                )

            val parses =
                listOf(
                    TvBoxParse(
                        name = "默认JSON解析",
                        type = 1,
                        url = "https://api.jx.com/parse?url=",
                        header = mapOf("Origin" to "https://api.jx.com"),
                    ),
                )

            val sources =
                resolver.resolveParse(
                    targetUrl = targetPage,
                    epNumber = 1f,
                    siteName = "腾讯视频",
                    candidateParses = parses,
                )

            assertEquals(1, sources.size)
            val direct = sources.first()
            assertEquals("https://stream.jx.com/live/ep01.m3u8", direct.url)
            assertEquals(PlaylistEntryKind.DIRECT, direct.kind)
            assertEquals("MiniBgmSpider/1.0", direct.headers["User-Agent"])
            assertEquals("https://v.qq.com/", direct.headers["Referer"])
            assertEquals("https://api.jx.com", direct.headers["Origin"])
        }

    @Test
    fun `resolveParse_type0_网页解析静态秒提视频直链`() =
        runTest {
            val targetPage = "https://bilibili.com/bangumi/play/ep123"
            val jxPageUrl = "https://jx.example.com/?url=https://bilibili.com/bangumi/play/ep123"
            val html =
                """
                <html>
                    <video src="https://play.example.com/bili/ep123.mp4"></video>
                </html>
                """.trimIndent()

            val resolver =
                TvBoxParseResolverImpl(
                    pageFetchService = FakePageFetchService(mapOf(jxPageUrl to html)),
                )

            val parses =
                listOf(
                    TvBoxParse(
                        name = "网页嗅探解析",
                        type = 0,
                        url = "https://jx.example.com/?url=",
                    ),
                )

            val sources =
                resolver.resolveParse(
                    targetUrl = targetPage,
                    epNumber = 1f,
                    siteName = "B站番剧",
                    candidateParses = parses,
                )

            assertEquals(1, sources.size)
            assertEquals("https://play.example.com/bili/ep123.mp4", sources.first().url)
            assertEquals(PlaylistEntryKind.DIRECT, sources.first().kind)
        }

    @Test
    fun `resolveParse_第一条解析器失效时自动优雅轮巡至下一条`() =
        runTest {
            val targetPage = "https://v.example.com/play/1"
            val failingUrl = "https://failing-jx.com/?url=https://v.example.com/play/1"
            val workingUrl = "https://working-jx.com/?url=https://v.example.com/play/1"

            val resolver =
                TvBoxParseResolverImpl(
                    pageFetchService =
                        FakePageFetchService(
                            mapOf(
                                failingUrl to """{"code":500,"msg":"error"}""",
                                workingUrl to """{"url": "https://stream.working.com/ep1.m3u8"}""",
                            ),
                        ),
                )

            val parses =
                listOf(
                    TvBoxParse(name = "坏解析", type = 1, url = "https://failing-jx.com/?url="),
                    TvBoxParse(name = "好解析", type = 1, url = "https://working-jx.com/?url="),
                )

            val sources =
                resolver.resolveParse(
                    targetUrl = targetPage,
                    epNumber = 1f,
                    siteName = "番剧站",
                    candidateParses = parses,
                )

            assertEquals(1, sources.size)
            assertEquals("https://stream.working.com/ep1.m3u8", sources.first().url)
            assertEquals(PlaylistEntryKind.DIRECT, sources.first().kind)
        }

    @Test
    fun `resolveParse_extHeader合并至请求头与产出流`() =
        runTest {
            val targetPage = "https://v.qq.com/play/1"
            val jxUrl = "https://api.jx.com/parse?url=https://v.qq.com/play/1"
            var capturedRequestHeaders: Map<String, String>? = null

            val fakeFetcher =
                object : PageFetchService {
                    override suspend fun fetchHtml(
                        url: String,
                        requestHeaders: Map<String, String>,
                    ): FetchedPage? {
                        capturedRequestHeaders = requestHeaders
                        return FetchedPage(
                            url = url,
                            html = """{"url": "https://stream.jx.com/video.m3u8"}""",
                        )
                    }

                    override suspend fun postForm(
                        url: String,
                        formData: Map<String, String>,
                        requestHeaders: Map<String, String>,
                    ): FetchedPage? = null
                }

            val resolver = TvBoxParseResolverImpl(pageFetchService = fakeFetcher)
            val parse =
                TvBoxParse(
                    name = "Header测试",
                    type = 1,
                    url = "https://api.jx.com/parse?url=",
                    header = mapOf("Origin" to "https://api.jx.com"),
                    ext = Json.parseToJsonElement("""{"header": {"User-Agent": "CustomSpider/2.0", "Cookie": "token=abc"}}"""),
                )

            val sources =
                resolver.resolveParse(
                    targetUrl = targetPage,
                    epNumber = 1f,
                    siteName = "腾讯",
                    candidateParses = listOf(parse),
                )

            assertEquals(1, sources.size)
            val direct = sources.first()
            assertEquals("https://stream.jx.com/video.m3u8", direct.url)
            assertEquals("https://api.jx.com", capturedRequestHeaders?.get("Origin"))
            assertEquals("CustomSpider/2.0", capturedRequestHeaders?.get("User-Agent"))
            assertEquals("token=abc", capturedRequestHeaders?.get("Cookie"))
            assertEquals("CustomSpider/2.0", direct.headers["User-Agent"])
            assertEquals("token=abc", direct.headers["Cookie"])
        }

    @Test
    fun `resolveParse_extFlag过滤与匹配优先原则`() =
        runTest {
            val qqTarget = "https://v.qq.com/x/cover/ep1.html"
            val requestedUrls = mutableListOf<String>()

            val fakeFetcher =
                object : PageFetchService {
                    override suspend fun fetchHtml(
                        url: String,
                        requestHeaders: Map<String, String>,
                    ): FetchedPage? {
                        requestedUrls.add(url)
                        return FetchedPage(
                            url = url,
                            html = """{"url": "https://stream.qq-jx.com/ep1.m3u8"}""",
                        )
                    }

                    override suspend fun postForm(
                        url: String,
                        formData: Map<String, String>,
                        requestHeaders: Map<String, String>,
                    ): FetchedPage? = null
                }

            val resolver = TvBoxParseResolverImpl(pageFetchService = fakeFetcher)
            val youkuParse =
                TvBoxParse(
                    name = "优酷专解",
                    type = 1,
                    url = "https://youku.jx.com/?url=",
                    ext = Json.parseToJsonElement("""{"flag": ["youku"]}"""),
                )
            val qqParse =
                TvBoxParse(
                    name = "腾讯专解",
                    type = 1,
                    url = "https://qq.jx.com/?url=",
                    ext = Json.parseToJsonElement("""{"flag": ["qq"]}"""),
                )
            val universalParse =
                TvBoxParse(
                    name = "通用解析",
                    type = 1,
                    url = "https://universal.jx.com/?url=",
                )

            val sources =
                resolver.resolveParse(
                    targetUrl = qqTarget,
                    epNumber = 1f,
                    siteName = "腾讯视频",
                    candidateParses = listOf(youkuParse, universalParse, qqParse),
                )

            assertEquals(1, sources.size)
            assertEquals("https://stream.qq-jx.com/ep1.m3u8", sources.first().url)
            // 命中 qq flag 的解析器排在最前并直接胜出，未请求优酷专解
            assertEquals(listOf("https://qq.jx.com/?url=https://v.qq.com/x/cover/ep1.html"), requestedUrls)
        }

    @Test
    fun `resolveParse_type4_并发组合子解析器并由首个完成者胜出`() =
        runTest {
            val targetPage = "https://v.example.com/play/ep1"
            val slowUrl = "https://slow.jx.com/?url=https://v.example.com/play/ep1"
            val fastUrl = "https://fast.jx.com/?url=https://v.example.com/play/ep1"

            val fakeFetcher =
                object : PageFetchService {
                    override suspend fun fetchHtml(
                        url: String,
                        requestHeaders: Map<String, String>,
                    ): FetchedPage? {
                        return when (url) {
                            slowUrl -> {
                                delay(200)
                                FetchedPage(url, """{"url": "https://stream.slow.com/video.m3u8"}""")
                            }
                            fastUrl -> {
                                FetchedPage(url, """{"url": "https://stream.fast.com/video.m3u8"}""")
                            }
                            else -> null
                        }
                    }

                    override suspend fun postForm(
                        url: String,
                        formData: Map<String, String>,
                        requestHeaders: Map<String, String>,
                    ): FetchedPage? = null
                }

            val resolver = TvBoxParseResolverImpl(pageFetchService = fakeFetcher)
            val aggregateParse =
                TvBoxParse(
                    name = "聚合解析",
                    type = 4,
                    url = "",
                )
            val slowParse = TvBoxParse(name = "慢速解析", type = 1, url = "https://slow.jx.com/?url=")
            val fastParse = TvBoxParse(name = "快速解析", type = 1, url = "https://fast.jx.com/?url=")

            val sources =
                resolver.resolveParse(
                    targetUrl = targetPage,
                    epNumber = 1f,
                    siteName = "动漫",
                    candidateParses = listOf(aggregateParse, slowParse, fastParse),
                )

            assertEquals(1, sources.size)
            assertEquals("https://stream.fast.com/video.m3u8", sources.first().url)
        }
}
