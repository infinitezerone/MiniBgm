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
}
