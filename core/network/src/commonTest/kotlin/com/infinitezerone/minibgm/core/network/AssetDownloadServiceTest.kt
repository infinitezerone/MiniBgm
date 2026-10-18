package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 回环流判定回归测试。
 *
 * 该判定决定本地 MP4 流走纯流式 CIO 引擎还是 ECH 引擎，误判会让 GB 级本地视频
 * 走全量内存缓冲的 ECH 通道（见 [AssetDownloadServiceImpl.streamAsset]）。
 */
class AssetDownloadServiceTest {
    @Test
    fun `回环地址被识别为本地流`() {
        assertTrue(isLoopbackHttpUrl("http://127.0.0.1:8899/stream?hash=abc"))
        assertTrue(isLoopbackHttpUrl("http://127.0.0.1/stream"))
        assertTrue(isLoopbackHttpUrl("http://localhost:18080/stream/0"))
        assertTrue(isLoopbackHttpUrl("http://localhost/stream"))
        assertTrue(isLoopbackHttpUrl("http://[::1]:8899/stream"))
        assertTrue(isLoopbackHttpUrl("http://[::1]/stream"))
    }

    @Test
    fun `大小写与首尾空白被归一化`() {
        assertTrue(isLoopbackHttpUrl("  HTTP://127.0.0.1:8899/STREAM  "))
        assertTrue(isLoopbackHttpUrl("Http://LocalHost:8080/x"))
    }

    @Test
    fun `外部地址不被识别为本地流`() {
        assertFalse(isLoopbackHttpUrl("https://example.com/stream"))
        assertFalse(isLoopbackHttpUrl("http://example.com/stream"))
        assertFalse(isLoopbackHttpUrl("http://10.0.0.1:8899/stream"))
        assertFalse(isLoopbackHttpUrl("http://127.1.1.1:8899/stream"))
        assertFalse(isLoopbackHttpUrl("http://localhost.evil.com:8899/stream"))
    }

    @Test
    fun `缺少端口与路径分隔符的裸地址或空串不判定为本地流`() {
        assertFalse(isLoopbackHttpUrl(""))
        assertFalse(isLoopbackHttpUrl("http://127.0.0.1"))
        assertFalse(isLoopbackHttpUrl("http://localhost"))
    }

    /**
     * 下载请求必须把请求预算显式标记为无限：Ktor 会把 per-request 的 null 回填成
     * client 级默认值（这里故意设成 50ms），而 EchHttpClientEngine 会把这个预算
     * 套在响应体读取上，大文件会在默认预算处被掐断。
     *
     * 在引擎边界断言能力值，而不是用慢流"碰运气"：MockEngine 不模拟引擎自己的
     * 读超时，慢流测试抓不住这个 bug。
     */
    @Test
    fun `下载请求把请求预算标记为无限`() =
        runTest {
            var budget: Long? = null
            val engine =
                MockEngine { request ->
                    budget = request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
                    respond(ByteArray(4), HttpStatusCode.OK)
                }
            val client =
                HttpClient(engine) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 50
                    }
                }
            val service = AssetDownloadServiceImpl(client)

            val total = service.streamAsset("https://example.com/a.bin") { _, _, _, _ -> }

            assertEquals(4L, total)
            assertEquals(
                HttpTimeoutConfig.INFINITE_TIMEOUT_MS,
                budget,
                "下载必须显式关闭请求预算，否则会被 client 级默认值覆盖，长下载中途被掐断",
            )
        }

    @Test
    fun `回环地址由注入 client 处理而不是另起 client`() =
        runTest {
            var calls = 0
            val engine =
                MockEngine {
                    calls++
                    respond(ByteArray(4), HttpStatusCode.OK)
                }
            val service = AssetDownloadServiceImpl(HttpClient(engine))

            val total = service.streamAsset("http://127.0.0.1:8899/stream") { _, _, _, _ -> }

            assertEquals(4L, total)
            assertEquals(1, calls)
        }
}
