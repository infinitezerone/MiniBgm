package com.infinitezerone.minibgm.core.network.oauth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket

class LocalOAuthProxyServerTest {
    private val mockClient =
        HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    respond(
                        content = "<html>Hello Bgm</html>",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
                    )
                }
            }
        }

    @Test
    fun lifecycle_startAndStopManageSocket() {
        val server = LocalOAuthProxyServer(mockClient)
        assertFalse(server.isRunning)
        assertNull(server.port)
        assertNull(server.sessionCookie())

        val port = server.start("bgm.tv")
        assertTrue(port > 0)
        assertTrue(server.isRunning)
        assertEquals(port, server.port)

        val cookie = server.sessionCookie()
        assertNotNull(cookie)
        assertEquals("minibgm_inapp_web", cookie?.first)
        assertTrue(cookie?.second?.isNotEmpty() == true)

        // Starting with same host returns existing port
        val samePort = server.start("bgm.tv")
        assertEquals(port, samePort)

        server.stop()
        assertFalse(server.isRunning)
        assertNull(server.port)
        assertNull(server.sessionCookie())
    }

    @Test
    fun toLoopbackUrl_rewritesMatchingUpstreamHost() {
        val server = LocalOAuthProxyServer(mockClient)
        val port = server.start("bgm.tv")

        val loopback = server.toLoopbackUrl("https://bgm.tv/oauth/authorize?client_id=123")
        assertEquals("http://127.0.0.1:$port/oauth/authorize?client_id=123", loopback)

        // Non-matching host returns null
        assertNull(server.toLoopbackUrl("https://google.com/search"))

        server.stop()
    }

    @Test
    fun handleSocket_forwardsGetRequestSuccessfully() {
        val server = LocalOAuthProxyServer(mockClient)
        val port = server.start("bgm.tv")
        val token = server.sessionCookie()!!.second

        val socket = Socket("127.0.0.1", port)
        val request =
            "GET http://bgm.tv/test HTTP/1.1\r\n" +
                "Host: 127.0.0.1:$port\r\n" +
                "Cookie: minibgm_inapp_web=$token\r\n" +
                "\r\n"
        socket.getOutputStream().write(request.toByteArray(Charsets.ISO_8859_1))
        socket.getOutputStream().flush()

        val responseLine = OAuthHttpCodec.readLine(socket.getInputStream())
        assertTrue(responseLine?.startsWith("HTTP/1.1 200") == true)

        socket.close()
        server.stop()
    }

    @Test
    fun handleSocket_rejectsUnauthorizedRequest() {
        val server = LocalOAuthProxyServer(mockClient)
        val port = server.start("bgm.tv")

        val socket = Socket("127.0.0.1", port)
        val request =
            "GET http://bgm.tv/secret HTTP/1.1\r\n" +
                "Host: 127.0.0.1:$port\r\n" +
                "\r\n"
        socket.getOutputStream().write(request.toByteArray(Charsets.ISO_8859_1))
        socket.getOutputStream().flush()

        val responseLine = OAuthHttpCodec.readLine(socket.getInputStream())
        assertTrue(responseLine?.startsWith("HTTP/1.1 403") == true)

        socket.close()
        server.stop()
    }
}
