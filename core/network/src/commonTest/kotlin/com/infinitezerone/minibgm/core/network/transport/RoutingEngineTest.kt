package com.infinitezerone.minibgm.core.network.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutingEngineTest {
    @Test
    fun `https 走 ECH http 与回环走 plain`() =
        runTest {
            var echCalls = 0
            var plainCalls = 0
            val echEngine =
                MockEngine {
                    echCalls++
                    respond("ech")
                }
            val plainEngine =
                MockEngine {
                    plainCalls++
                    respond("plain")
                }
            val client = HttpClient(RoutingEngine(echEngine = echEngine, plainEngine = plainEngine))

            assertEquals("ech", client.get("https://api.bgm.tv/v0/subjects/1").bodyAsText())
            assertEquals("plain", client.get("http://example.com/page").bodyAsText())
            assertEquals("plain", client.get("http://127.0.0.1:8899/stream").bodyAsText())

            assertEquals(1, echCalls)
            assertEquals(2, plainCalls)
        }

    @Test
    fun `ECH GET 失败时回落到 plain`() =
        runTest {
            val echEngine = MockEngine { throw IOException("tls handshake eof") }
            val plainEngine = MockEngine { respond("plain") }
            val client = HttpClient(RoutingEngine(echEngine = echEngine, plainEngine = plainEngine))

            val body = client.get("https://acg.rip/").bodyAsText()

            assertEquals("plain", body)
        }
}
