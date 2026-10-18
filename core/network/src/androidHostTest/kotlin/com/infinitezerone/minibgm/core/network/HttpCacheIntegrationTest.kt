package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 弹幕取数的缓存契约：新鲜度由服务端 `Cache-Control` 驱动，仓库不再自研 TTL。
 *
 * 这里用真实 [PageFetchServiceImpl] + 真实 Ktor [HttpCache] + 磁盘 [FileStorage] 打 MockEngine，
 * 钉住"第二次抓取不再发请求"——避免把缓存语义退回成手写 lastModified/TTL。
 */
class HttpCacheIntegrationTest {
    private val cacheDir = Files.createTempDirectory("minibgm_http_cache_test").toFile()

    @AfterTest
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `服务端允许缓存时第二次抓取命中 HTTP 缓存而不发请求`() =
        runTest {
            var calls = 0
            val client =
                HttpClient(
                    MockEngine {
                        calls += 1
                        respond(
                            content = """{"count":1,"comments":[{"cid":1,"p":"1.0,1,16777215,x","m":"hello"}]}""",
                            headers =
                                headersOf(
                                    HttpHeaders.ContentType to listOf("application/json"),
                                    HttpHeaders.CacheControl to listOf("public, max-age=60"),
                                ),
                        )
                    },
                ) {
                    followRedirects = false
                    install(HttpCache) {
                        publicStorage(FileStorage(cacheDir))
                    }
                }
            val service = PageFetchServiceImpl(untrustedClient = client)
            val url = "https://api.example.com/api/v2/comment/9876?withRelated=true"

            val first = service.fetchHtml(url, emptyMap(), 8 * 1024 * 1024)
            val second = service.fetchHtml(url, emptyMap(), 8 * 1024 * 1024)

            assertEquals(first?.html, second?.html)
            assertEquals(1, calls, "第二次抓取必须由 HTTP 缓存直接应答，不得再打网络")
        }
}
