package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSearchServiceTest {
    private fun createService(
        html: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): WebSearchService =
        WebSearchServiceImpl(
            HttpClient(
                MockEngine { _ ->
                    respond(
                        content = html,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType to listOf("text/html")),
                    )
                },
            ),
        )

    @Test
    fun search_emptyQuery_returnsEmptyList() =
        runTest {
            val service = createService("<html></html>")
            val results = service.search("")
            assertTrue(results.isEmpty())
        }

    @Test
    fun search_parsesBingAlgoItems() =
        runTest {
            val sampleHtml =
                """
                <ol id="b_results">
                    <li class="b_algo">
                        <h2><a target="_blank" href="https://example.com/anime/1"><strong>葬送的芙莉莲</strong> 在线播放 - 示例站</a></h2>
                        <div class="b_caption"><p>这是关于葬送的芙莉莲的剧情简介与在线观看说明&ensp;&amp;&ensp;更多信息</p></div>
                    </li>
                    <li class="b_algo">
                        <h2><a target="_blank" href="https://anime.tv/watch/2">第二部动漫 - 番组</a></h2>
                        <div class="b_caption"><p>第二部动漫的摘要描述</p></div>
                    </li>
                </ol>
                """.trimIndent()

            val service = createService(sampleHtml)
            val results = service.search("葬送的芙莉莲", limit = 10)

            assertEquals(2, results.size)
            assertEquals("葬送的芙莉莲 在线播放 - 示例站", results[0].title)
            assertEquals("https://example.com/anime/1", results[0].url)
            assertTrue(results[0].snippet.contains("这是关于葬送的芙莉莲的剧情简介"))
            assertTrue(results[0].snippet.contains("&"))

            assertEquals("第二部动漫 - 番组", results[1].title)
            assertEquals("https://anime.tv/watch/2", results[1].url)
        }

    @Test
    fun search_httpError_returnsEmptyList() =
        runTest {
            val service = createService("", status = HttpStatusCode.InternalServerError)
            val results = service.search("test")
            assertTrue(results.isEmpty())
        }

    @Test
    fun search_captchaOrChallengePage_triggersWatchdog_andReturnsEmptyList() =
        runTest {
            val captchaHtml =
                """
                <!DOCTYPE html>
                <html>
                <head><title>Bot Detection / Challenge</title></head>
                <body>
                    <div id="captcha-container">
                        <p>Our systems have detected unusual traffic from your computer network.</p>
                        <form id="challenge-form">Please complete the security verification</form>
                    </div>
                </body>
                </html>
                """.trimIndent()
            val service = createService(captchaHtml)
            val results = service.search("芙莉莲")
            assertTrue(results.isEmpty())
        }

    @Test
    fun search_domLayoutMismatch_triggersWatchdog_andReturnsEmptyList() =
        runTest {
            val mismatchedDomHtml =
                buildString {
                    append("<!DOCTYPE html><html><head><title>Bing Search</title></head><body>")
                    append("<div class=\"new_redesigned_search_container\">")
                    repeat(100) {
                        append("<div class=\"new_item_class\"><h3>Some Title $it</h3><p>Description $it</p></div>")
                    }
                    append("</div></body></html>")
                }
            val service = createService(mismatchedDomHtml)
            val results = service.search("芙莉莲")
            assertTrue(results.isEmpty())
        }

    @Test
    fun search_gitHubTargetedQuery_returnsParsedGitHubResults() =
        runTest {
            val ghJson =
                """
                {
                    "total_count": 2,
                    "items": [
                        {
                            "name": "iptv-api",
                            "full_name": "Guovin/iptv-api",
                            "html_url": "https://github.com/Guovin/iptv-api",
                            "description": "高质量直播与点播源配置",
                            "stargazers_count": 25281
                        },
                        {
                            "name": "bug",
                            "full_name": "liu673cn/bug",
                            "html_url": "https://github.com/liu673cn/bug",
                            "description": "TVBox 接口与配置",
                            "stargazers_count": 10347
                        }
                    ]
                }
                """.trimIndent()

            val service =
                WebSearchServiceImpl(
                    HttpClient(
                        MockEngine { request ->
                            if (request.url.host == "api.github.com") {
                                respond(
                                    content = ghJson,
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                                )
                            } else {
                                respond(
                                    content = "<html></html>",
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType to listOf("text/html")),
                                )
                            }
                        },
                    ),
                )

            val results = service.search("tvbox 源", limit = 5)
            assertEquals(2, results.size)
            assertEquals("Guovin/iptv-api (⭐ 25281)", results[0].title)
            assertEquals("https://github.com/Guovin/iptv-api", results[0].url)
            assertTrue(results[0].snippet.contains("高质量直播与点播源配置"))
            assertTrue(results[0].snippet.contains("25281"))

            assertEquals("liu673cn/bug (⭐ 10347)", results[1].title)
            assertEquals("https://github.com/liu673cn/bug", results[1].url)
        }

    @Test
    fun search_gitHubApiFails_fallsBackToBing() =
        runTest {
            val bingHtml =
                """
                <ol id="b_results">
                    <li class="b_algo">
                        <h2><a target="_blank" href="https://github.com/fallback/repo">Fallback Repo - Bing</a></h2>
                        <div class="b_caption"><p>Fallback description</p></div>
                    </li>
                </ol>
                """.trimIndent()

            val service =
                WebSearchServiceImpl(
                    HttpClient(
                        MockEngine { request ->
                            if (request.url.host == "api.github.com") {
                                respond(
                                    content = """{"message": "API rate limit exceeded"}""",
                                    status = HttpStatusCode.Forbidden,
                                    headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                                )
                            } else {
                                respond(
                                    content = bingHtml,
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType to listOf("text/html")),
                                )
                            }
                        },
                    ),
                )

            val results = service.search("tvbox 动漫源")
            assertEquals(1, results.size)
            assertEquals("Fallback Repo - Bing", results[0].title)
            assertEquals("https://github.com/fallback/repo", results[0].url)
        }

    @Test
    fun isGitHubTargeted_and_extractGitHubQuery() {
        val service = WebSearchServiceImpl(HttpClient(MockEngine { respond("") }))

        assertTrue(service.isGitHubTargeted("site:github.com tvbox 动漫"))
        assertTrue(service.isGitHubTargeted("tvbox 源"))
        assertTrue(service.isGitHubTargeted("高质量动漫源"))
        assertTrue(service.isGitHubTargeted("动漫 订阅源"))
        assertTrue(service.isGitHubTargeted("影视仓 接口"))
        assertTrue(service.isGitHubTargeted("github 动漫"))

        assertEquals("tvbox 动漫", service.extractGitHubQuery("site:github.com tvbox 动漫"))
        assertEquals("tvbox 源", service.extractGitHubQuery("site:github.com tvbox 源"))
        assertEquals("tvbox", service.extractGitHubQuery("site:github.com"))
        assertEquals("tvbox 推荐", service.extractGitHubQuery("github tvbox 推荐"))
    }
}
