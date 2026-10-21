package com.infinitezerone.minibgm.core.data.repository.resolver.parse

import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.TvBoxParse
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.test.runTest
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
}
