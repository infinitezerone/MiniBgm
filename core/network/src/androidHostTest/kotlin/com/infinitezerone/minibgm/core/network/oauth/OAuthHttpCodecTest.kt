package com.infinitezerone.minibgm.core.network.oauth

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class OAuthHttpCodecTest {
    @Test
    fun readLine_parsesCrLfAndLfCorrectly() {
        val input = "GET /index HTTP/1.1\r\nHost: bgm.tv\n\n".byteInputStream()
        assertEquals("GET /index HTTP/1.1", OAuthHttpCodec.readLine(input))
        assertEquals("Host: bgm.tv", OAuthHttpCodec.readLine(input))
        assertEquals("", OAuthHttpCodec.readLine(input))
        assertNull(OAuthHttpCodec.readLine(input))
    }

    @Test
    fun parseRequestLine_extractsMethodAndTarget() {
        val parsed = OAuthHttpCodec.parseRequestLine("GET /oauth/authorize?client_id=123 HTTP/1.1")
        assertEquals("GET", parsed?.first)
        assertEquals("/oauth/authorize?client_id=123", parsed?.second)

        assertNull(OAuthHttpCodec.parseRequestLine("INVALID"))
    }

    @Test
    fun resolveTarget_parsesAbsoluteAndOriginForms() {
        // Proxy absolute form
        val (host1, path1) = OAuthHttpCodec.resolveTarget("http://next.bgm.tv/subject/100?sp=1", "bgm.tv")!!
        assertEquals("next.bgm.tv", host1)
        assertEquals("/subject/100?sp=1", path1)

        // Origin form
        val (host2, path2) = OAuthHttpCodec.resolveTarget("/subject/200", "bgm.tv")!!
        assertEquals("bgm.tv", host2)
        assertEquals("/subject/200", path2)

        // Malformed
        assertNull(OAuthHttpCodec.resolveTarget("http://", "bgm.tv"))
    }

    @Test
    fun parseHeaders_andReadBody() {
        val raw =
            "Host: bgm.tv\r\n" +
                "Content-Length: 11\r\n" +
                "Content-Type: application/x-www-form-urlencoded\r\n" +
                "\r\n" +
                "hello=world"
        val stream = raw.toByteArray(Charsets.ISO_8859_1).inputStream()
        val (headers, contentLength) = OAuthHttpCodec.parseHeaders(stream)

        assertEquals("bgm.tv", headers["Host"])
        assertEquals(11, contentLength)
        assertEquals(ContentType.Application.FormUrlEncoded, OAuthHttpCodec.parseContentType(headers))

        val body = OAuthHttpCodec.readBody(stream, contentLength)
        assertEquals("hello=world", body.decodeToString())
    }

    @Test
    fun rewriteSecureLinks_downgradesHttpsToHttpForBgmDomains() {
        val input =
            """<a href="https://bgm.tv/login">Login</a> """ +
                """<a href="https://chii.in/group">Group</a> <a href="https://google.com">Google</a>"""
        val rewritten = OAuthHttpCodec.rewriteSecureLinks(input)
        assertEquals(
            """<a href="http://bgm.tv/login">Login</a> """ +
                """<a href="http://chii.in/group">Group</a> <a href="https://google.com">Google</a>""",
            rewritten,
        )
    }

    @Test
    fun restoreSecureScheme_upgradesHttpToHttpsForBgmDomains() {
        val input = "http://bgm.tv/login"
        assertEquals("https://bgm.tv/login", OAuthHttpCodec.restoreSecureScheme(input))

        val nonBgm = "http://google.com"
        assertEquals("http://google.com", OAuthHttpCodec.restoreSecureScheme(nonBgm))
    }

    @Test
    fun filterInboundHeaders_processesExcludedAndRewrittenHeaders() {
        val inbound =
            mapOf(
                "Host" to "127.0.0.1:8080",
                "Connection" to "keep-alive",
                "Cookie" to "theme=dark; minibgm_inapp_web=secret123",
                "Referer" to "http://bgm.tv/login",
                "Origin" to "http://bgm.tv",
                "User-Agent" to "MiniBgm",
            )
        val filtered = OAuthHttpCodec.filterInboundHeaders(inbound).toMap()

        assertFalse(filtered.containsKey("Host"))
        assertFalse(filtered.containsKey("Connection"))
        assertEquals("theme=dark", filtered["Cookie"])
        assertEquals("https://bgm.tv/login", filtered["Referer"])
        assertEquals("https://bgm.tv", filtered["Origin"])
        assertEquals("MiniBgm", filtered["User-Agent"])
    }

    @Test
    fun filterOutboundHeaders_rewritesLocationAndSanitizesCookies() {
        val headers =
            headersOf(
                "Location" to listOf("https://bgm.tv/callback?code=123"),
                "Set-Cookie" to listOf("chii_sid=xyz; Domain=.bgm.tv; Path=/; Secure; HttpOnly"),
                "Transfer-Encoding" to listOf("chunked"),
                "Custom-Header" to listOf("Value"),
            )
        val filtered = OAuthHttpCodec.filterOutboundHeaders(headers)

        val location = filtered.firstOrNull { it.first.equals("Location", ignoreCase = true) }?.second
        assertEquals("http://bgm.tv/callback?code=123", location)

        val setCookie = filtered.firstOrNull { it.first.equals("Set-Cookie", ignoreCase = true) }?.second
        assertEquals("chii_sid=xyz; Path=/; HttpOnly", setCookie)

        assertFalse(filtered.any { it.first.equals("Transfer-Encoding", ignoreCase = true) })
        assertEquals("Value", filtered.firstOrNull { it.first == "Custom-Header" }?.second)
    }

    @Test
    fun writeResponse_formatsCompleteHttpMessage() {
        val out = ByteArrayOutputStream()
        val headers =
            headersOf(
                HttpHeaders.ContentType to listOf("text/html; charset=utf-8"),
                "Custom" to listOf("Test"),
            )
        val body = """<a href="https://bgm.tv/subject/1">Subject</a>""".toByteArray(Charsets.UTF_8)

        OAuthHttpCodec.writeResponse(out, HttpStatusCode.OK, headers, body)
        val responseText = out.toString(Charsets.ISO_8859_1.name())

        assertTrue(responseText.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(responseText.contains("Custom: Test\r\n"))
        assertTrue(responseText.contains("http://bgm.tv/subject/1"))
    }

    @Test
    fun sanitizeSetCookie_stripsDomainAndSecure() {
        val cookie = "chii_auth=abc123xyz; Domain=.bgm.tv; Path=/; Secure; HttpOnly; SameSite=Lax"
        val sanitized = OAuthHttpCodec.sanitizeSetCookie(cookie)
        assertEquals("chii_auth=abc123xyz; Path=/; HttpOnly; SameSite=Lax", sanitized)
    }

    @Test
    fun stripSessionCookie_removesInternalToken() {
        val cookie = "chii_auth=123; minibgm_inapp_web=secret_token; theme=dark"
        val stripped = OAuthHttpCodec.stripSessionCookie(cookie, "minibgm_inapp_web")
        assertEquals("chii_auth=123; theme=dark", stripped)
    }

    @Test
    fun isTextOrHtml_identifiesMimeTypes() {
        assertTrue(OAuthHttpCodec.isTextOrHtml("text/html; charset=utf-8"))
        assertTrue(OAuthHttpCodec.isTextOrHtml("application/json"))
        assertTrue(OAuthHttpCodec.isTextOrHtml("application/javascript"))
        assertFalse(OAuthHttpCodec.isTextOrHtml("image/png"))
        assertFalse(OAuthHttpCodec.isTextOrHtml("video/mp4"))
    }
}
