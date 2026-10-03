package com.infinitezerone.minibgm.core.network.oauth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthSecurityGuardTest {
    @Test
    fun isAllowedConnectHost_blocksPrivateAndLoopbackIps() {
        assertFalse(OAuthSecurityGuard.isAllowedConnectHost("127.0.0.1:443", "bgm.tv"))
        assertFalse(OAuthSecurityGuard.isAllowedConnectHost("localhost:8080", "bgm.tv"))
        assertFalse(OAuthSecurityGuard.isAllowedConnectHost("::1", "bgm.tv"))
        assertFalse(OAuthSecurityGuard.isAllowedConnectHost("10.0.0.1:443", "bgm.tv"))
        assertFalse(OAuthSecurityGuard.isAllowedConnectHost("192.168.1.1:443", "bgm.tv"))
        assertFalse(OAuthSecurityGuard.isAllowedConnectHost("172.16.0.1:443", "bgm.tv"))
    }

    @Test
    fun isAllowedConnectHost_allowsWhitelistedDomains() {
        assertTrue(OAuthSecurityGuard.isAllowedConnectHost("bgm.tv:443", "bgm.tv"))
        assertTrue(OAuthSecurityGuard.isAllowedConnectHost("api.bgm.tv:443", "bgm.tv"))
        assertTrue(OAuthSecurityGuard.isAllowedConnectHost("chii.in:443", "bgm.tv"))
        assertTrue(OAuthSecurityGuard.isAllowedConnectHost("challenges.cloudflare.com:443", "bgm.tv"))
        assertTrue(OAuthSecurityGuard.isAllowedConnectHost("sub.challenges.cloudflare.com:443", "bgm.tv"))

        assertFalse(OAuthSecurityGuard.isAllowedConnectHost("malicious.com:443", "bgm.tv"))
    }

    @Test
    fun isAllowedUpstreamHost_validatesTarget() {
        assertTrue(OAuthSecurityGuard.isAllowedUpstreamHost("bgm.tv", "bgm.tv"))
        assertTrue(OAuthSecurityGuard.isAllowedUpstreamHost("next.bgm.tv", "bgm.tv"))
        assertTrue(OAuthSecurityGuard.isAllowedUpstreamHost("bangumi.tv", "bgm.tv"))
        assertTrue(OAuthSecurityGuard.isAllowedUpstreamHost("chii.in", "bgm.tv"))

        assertFalse(OAuthSecurityGuard.isAllowedUpstreamHost("evil.com", "bgm.tv"))
    }

    @Test
    fun hasValidSession_permitsTokenInCookie() {
        val headers = mapOf("Cookie" to "theme=dark; minibgm_inapp_web=secret_abc123")
        assertTrue(OAuthSecurityGuard.hasValidSession(headers, "secret_abc123", "GET"))
        assertTrue(OAuthSecurityGuard.hasValidSession(headers, "secret_abc123", "POST"))

        assertFalse(OAuthSecurityGuard.hasValidSession(headers, "wrong_token", "GET"))
    }

    @Test
    fun hasValidSession_permitsSafeSubresourceRequests() {
        val headers = mapOf("Sec-Fetch-Dest" to "image")
        assertTrue(OAuthSecurityGuard.hasValidSession(headers, "secret_abc123", "GET"))
        assertTrue(OAuthSecurityGuard.hasValidSession(headers, "secret_abc123", "HEAD"))

        // Document navigation without token must be blocked
        val docHeaders = mapOf("Sec-Fetch-Dest" to "document")
        assertFalse(OAuthSecurityGuard.hasValidSession(docHeaders, "secret_abc123", "GET"))

        // Unsafe methods without token must be blocked even for subresources
        assertFalse(OAuthSecurityGuard.hasValidSession(headers, "secret_abc123", "POST"))
    }
}
