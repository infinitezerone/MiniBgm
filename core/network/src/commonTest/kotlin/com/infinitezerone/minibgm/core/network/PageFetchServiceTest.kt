package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondRedirect
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
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
    fun `正文超过默认上限时按页面预算截断`() =
        runTest {
            val page = service(body = "a".repeat(MAX_PAGE_BYTES + 4096)).fetchHtml("https://big.example.com/page")

            assertEquals(MAX_PAGE_BYTES, page?.html?.length)
        }

    @Test
    fun `协议正文按调用方预算取全而不是沿用页面预算`() =
        runTest {
            // 弹幕正文动辄数百 KB：调用方声明自己的预算后，必须拿到完整正文，
            // 而不是被页面默认的 MAX_PAGE_BYTES 截断成不可解析的 JSON
            val payload = "{\"count\":1,\"comments\":[" + " ".repeat(MAX_PAGE_BYTES + 4096) + "]}"
            val page =
                service(body = payload).fetchHtml(
                    "https://api.example.com/comment/1",
                    emptyMap(),
                    maxBytes = 8 * 1024 * 1024,
                )

            assertEquals(payload.length, page?.html?.length)
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
    fun `重定向到私网时立即停止且不发出第二跳`() =
        runTest {
            var calls = 0
            val untrusted =
                HttpClient(
                    MockEngine { _ ->
                        calls += 1
                        respondRedirect("http://127.0.0.1:8080/admin")
                    },
                ) {
                    followRedirects = false
                }
            val impl =
                PageFetchServiceImpl(untrustedClient = untrusted)

            assertNull(impl.fetchHtml("https://origin.example.com/e/1"))
            assertEquals(1, calls, "私网跳转必须在发出第二跳前被拦下")
        }

    @Test
    fun `no-redirect client 下公网跳转仍逐跳跟随到最终页`() =
        runTest {
            var calls = 0
            val untrusted =
                HttpClient(
                    MockEngine { _ ->
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
                ) {
                    followRedirects = false
                }
            val impl =
                PageFetchServiceImpl(untrustedClient = untrusted)

            val page = impl.fetchHtml("https://origin.example.com/e/1")

            assertEquals("https://final.example.com/e/1", page?.url)
            assertEquals("<html>moved</html>", page?.html)
            assertEquals(2, calls)
        }

    @Test
    fun `postForm 303 重定向按浏览器惯例降级为 GET`() =
        runTest {
            val methods = mutableListOf<HttpMethod>()
            val untrusted =
                HttpClient(
                    MockEngine { request ->
                        methods += request.method
                        if (methods.size == 1) {
                            respond(
                                content = "",
                                status = HttpStatusCode.SeeOther,
                                headers = headersOf(HttpHeaders.Location to listOf("https://api.example.com/result")),
                            )
                        } else {
                            respond(
                                content = """{"ok":true}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                            )
                        }
                    },
                ) {
                    followRedirects = false
                }
            val impl =
                PageFetchServiceImpl(untrustedClient = untrusted)

            val page = impl.postForm("https://api.example.com/form", mapOf("d" to "payload"))

            assertEquals(listOf(HttpMethod.Post, HttpMethod.Get), methods)
            assertEquals("https://api.example.com/result", page?.url)
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

    @Test
    fun `probeStream 只取状态码与去掉 charset 的 Content-Type`() =
        runTest {
            val impl =
                PageFetchServiceImpl(
                    HttpClient(
                        MockEngine { request ->
                            capturedHeaders.add(request.headers)
                            respond(
                                content = "binary",
                                status = HttpStatusCode.PartialContent,
                                headers = headersOf(HttpHeaders.ContentType to listOf("video/mp4; charset=utf-8")),
                            )
                        },
                    ),
                )

            val probe = impl.probeStream("https://cdn.example.com/v/1.mp4?sign=x")

            assertEquals(StreamProbe.Responded(status = 206, contentType = "video/mp4"), probe)
            val headers = capturedHeaders.single()
            assertEquals("bytes=0-2047", headers[HttpHeaders.Range])
            // 探测媒体不能沿用 text/html，否则 CDN 可能回一个错误页
            assertEquals("*/*", headers[HttpHeaders.Accept])
        }

    @Test
    fun `probeStream 对非 http 地址与请求异常都返回失败而不是抛出`() =
        runTest {
            assertTrue(PageFetchServiceImpl(HttpClient(MockEngine { respond("") })).probeStream("file:///etc/passwd") is StreamProbe.Failed)

            val broken = PageFetchServiceImpl(HttpClient(MockEngine { throw RuntimeException("boom") }))
            assertTrue(broken.probeStream("https://cdn.example.com/v.mp4") is StreamProbe.Failed)
        }

    @Test
    fun `probeStream 丢弃含换行的头`() =
        runTest {
            PageFetchServiceImpl(
                HttpClient(
                    MockEngine { request ->
                        capturedHeaders.add(request.headers)
                        respond("", status = HttpStatusCode.OK, headers = headersOf())
                    },
                ),
            ).probeStream(
                "https://cdn.example.com/v.mp4",
                mapOf("X-A" to "1\r\nX-Injected: 2", "X-B" to "2"),
            )

            val headers = capturedHeaders.single()
            assertNull(headers["X-A"])
            assertEquals("2", headers["X-B"])
            assertTrue(headers.names().none { it.equals("X-Injected", ignoreCase = true) })
        }

    @Test
    fun `fetchHtml 与 postForm 拒绝本机与私网目标且不发出请求`() =
        runTest {
            var requests = 0
            val impl =
                PageFetchServiceImpl(
                    HttpClient(
                        MockEngine {
                            requests++
                            respond("x")
                        },
                    ),
                )

            assertNull(impl.fetchHtml("http://127.0.0.1:8080/admin"))
            assertNull(impl.postForm("http://192.168.1.1/", emptyMap(), emptyMap()))

            assertEquals(0, requests)
        }

    @Test
    fun `跨 host 跳转剥掉凭据头但保留普通头`() =
        runTest {
            val seen = mutableListOf<Headers>()
            val untrusted =
                HttpClient(
                    MockEngine { request ->
                        seen += request.headers
                        if (seen.size == 1) {
                            respondRedirect("https://cdn.example.net/e/1")
                        } else {
                            respond(
                                content = "<html>ok</html>",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType to listOf("text/html")),
                            )
                        }
                    },
                ) {
                    followRedirects = false
                }

            val page =
                PageFetchServiceImpl(untrustedClient = untrusted).fetchHtml(
                    "https://origin.example.com/e/1",
                    mapOf(
                        "Authorization" to "Bearer secret",
                        "Cookie" to "sid=1",
                        "Referer" to "https://origin.example.com/",
                    ),
                )

            assertEquals("https://cdn.example.net/e/1", page?.url)
            assertEquals("Bearer secret", seen[0][HttpHeaders.Authorization])
            assertNull(seen[1][HttpHeaders.Authorization], "跨 host 不能再带 Authorization")
            assertNull(seen[1][HttpHeaders.Cookie], "跨 host 不能再带 Cookie")
            assertEquals("https://origin.example.com/", seen[1][HttpHeaders.Referrer])
        }

    @Test
    fun `同 host 跳转保留凭据头`() =
        runTest {
            val seen = mutableListOf<Headers>()
            val untrusted =
                HttpClient(
                    MockEngine { request ->
                        seen += request.headers
                        if (seen.size == 1) {
                            respondRedirect("https://origin.example.com/e/2")
                        } else {
                            respond(
                                content = "<html>ok</html>",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType to listOf("text/html")),
                            )
                        }
                    },
                ) {
                    followRedirects = false
                }

            PageFetchServiceImpl(untrustedClient = untrusted).fetchHtml(
                "https://origin.example.com/e/1",
                mapOf("Authorization" to "Bearer secret"),
            )

            assertEquals("Bearer secret", seen[1][HttpHeaders.Authorization])
        }

    @Test
    fun `重定向链超过总预算时按抓取失败降级`() =
        runTest {
            val untrusted =
                HttpClient(
                    MockEngine {
                        delay(2_000)
                        respondRedirect("https://next.example.com/x")
                    },
                ) {
                    followRedirects = false
                }

            val impl =
                PageFetchServiceImpl(
                    untrustedClient = untrusted,
                    redirectChainMillis = 50,
                )

            assertNull(impl.fetchHtml("https://origin.example.com/e/1"))
        }

    @Test
    fun `probeStream 逐跳跟随并跨 host 剥凭据头`() =
        runTest {
            val seen = mutableListOf<Headers>()
            val untrusted =
                HttpClient(
                    MockEngine { request ->
                        seen += request.headers
                        if (seen.size == 1) {
                            respond(
                                content = "",
                                status = HttpStatusCode.Found,
                                headers = headersOf(HttpHeaders.Location to listOf("https://cdn.example.net/v.mp4")),
                            )
                        } else {
                            respond(
                                content = "",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType to listOf("video/mp4")),
                            )
                        }
                    },
                ) {
                    followRedirects = false
                }

            val probe =
                PageFetchServiceImpl(untrustedClient = untrusted).probeStream(
                    "https://origin.example.com/v.mp4",
                    mapOf("Authorization" to "Bearer secret"),
                )

            assertEquals(StreamProbe.Responded(status = 200, contentType = "video/mp4"), probe)
            assertNull(seen[1][HttpHeaders.Authorization])
        }

    @Test
    fun `probeStream 仍允许局域网媒体直链`() =
        runTest {
            val impl =
                PageFetchServiceImpl(
                    HttpClient(
                        MockEngine {
                            respond(
                                content = "",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType to listOf("video/mp4")),
                            )
                        },
                    ),
                )

            val probe = impl.probeStream("http://192.168.1.5:8080/video.mp4")

            assertEquals(StreamProbe.Responded(status = 200, contentType = "video/mp4"), probe)
        }
}
