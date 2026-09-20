package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondRedirect
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageFetchServiceTest {
    private val capturedHeaders = mutableListOf<Headers>()

    private fun service(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "x",
    ): PageFetchService =
        PageFetchServiceImpl(
            HttpClient(
                MockEngine { request ->
                    capturedHeaders.add(request.headers)
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType to listOf("text/html")),
                    )
                },
            ) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            },
        )

    @Test
    fun `取回页面正文并保留请求地址`() =
        runTest {
            val page = service(body = "<html>ok</html>").fetchHtml("https://play.example.com/e/1")

            assertEquals("https://play.example.com/e/1", page?.url)
            assertEquals("<html>ok</html>", page?.html)
        }

    @Test
    fun `发出规则自带的请求头`() =
        runTest {
            service().fetchHtml(
                "https://api.example.com/p",
                mapOf("Referer" to "https://api.example.com/", "X-Key" to "abc"),
            )

            val headers = capturedHeaders.single()
            assertEquals("https://api.example.com/", headers["Referer"])
            assertEquals("abc", headers["X-Key"])
        }

    @Test
    fun `含换行的头被丢弃而不是注入`() =
        runTest {
            service().fetchHtml(
                "https://api.example.com/p",
                mapOf("X-A" to "1\r\nX-Injected: 2", "X-B" to "2"),
            )

            val headers = capturedHeaders.single()
            assertNull(headers["X-A"])
            assertEquals("2", headers["X-B"])
            assertTrue(headers.names().none { it.equals("X-Injected", ignoreCase = true) })
        }

    @Test
    fun `非 http 地址与失败响应都降级为空`() =
        runTest {
            assertNull(service().fetchHtml("file:///etc/passwd"))
            assertNull(service(status = HttpStatusCode.NotFound, body = "").fetchHtml("https://gone.example.com/x"))
        }

    @Test
    fun `正文超过上限时截断`() =
        runTest {
            val page = service(body = "a".repeat(MAX_PAGE_BYTES + 4096)).fetchHtml("https://big.example.com/page")

            assertEquals(MAX_PAGE_BYTES, page?.html?.length)
        }

    @Test
    fun `重定向后返回最终地址供 Referer 回填`() =
        runTest {
            var calls = 0
            val impl =
                PageFetchServiceImpl(
                    HttpClient(
                        MockEngine { request ->
                            calls += 1
                            if (calls == 1) {
                                respondRedirect("https://final.example.com/e/1")
                            } else {
                                respond(
                                    content = "<html>moved</html>",
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType to listOf("text/html")),
                                )
                            }
                        },
                    ),
                )

            val page = impl.fetchHtml("https://origin.example.com/e/1")

            assertEquals("https://final.example.com/e/1", page?.url)
            assertEquals("<html>moved</html>", page?.html)
        }

    @Test
    fun `postForm 正常发送表单并聚合 SetCookie 为 Cookie 响应头`() =
        runTest {
            var capturedParamD: String? = null
            val impl =
                PageFetchServiceImpl(
                    HttpClient(
                        MockEngine { request ->
                            capturedHeaders.add(request.headers)
                            val form = request.body as? io.ktor.client.request.forms.FormDataContent
                            capturedParamD = form?.formData?.get("d")
                            respond(
                                content = """{"status":"ok"}""",
                                status = HttpStatusCode.OK,
                                headers =
                                    headersOf(
                                        HttpHeaders.ContentType to listOf("application/json"),
                                        HttpHeaders.SetCookie to
                                            listOf(
                                                "e=123; path=/; domain=.example.com",
                                                "p=abc; path=/",
                                            ),
                                    ),
                            )
                        },
                    ),
                )

            val page =
                impl.postForm(
                    url = "https://api.example.com/stream",
                    formData = mapOf("d" to "payload123"),
                    requestHeaders = mapOf("Referer" to "https://site.example.com/"),
                )

            assertEquals("https://api.example.com/stream", page?.url)
            assertEquals("""{"status":"ok"}""", page?.html)
            assertEquals("e=123; p=abc", page?.responseHeaders?.get("Cookie"))
            assertEquals("payload123", capturedParamD)
        }
}
