package com.infinitezerone.minibgm.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlSafetyTest {
    @Test
    fun `公网 http 与 https 放行`() {
        assertTrue(UrlSafety.isAllowedRemoteUrl("https://bgm.tv/subject/1"))
        assertTrue(UrlSafety.isAllowedRemoteUrl("http://example.com/play"))
        assertTrue(UrlSafety.isAllowedRemoteUrl("https://8.8.8.8/"))
    }

    @Test
    fun `本机与私网字面量一律拒绝`() {
        listOf(
            "http://localhost:8080/admin",
            "http://127.0.0.1:8899/stream",
            "http://10.0.0.1/",
            "http://172.16.0.1/",
            "http://192.168.1.5/",
            "http://169.254.169.254/latest/meta-data",
            "http://100.64.0.1/",
            "http://0.0.0.0/",
            "http://[::1]:8080/",
            "http://[fc00::1]/",
            "http://[fe80::1]/",
            "http://[::ffff:127.0.0.1]/",
            // 系统解析器会把它们都解释成 127.0.0.1
            "http://2130706433/",
            "http://0x7f000001/",
            "http://0177.0.0.1/",
            "http://[::ffff:8.8.8.8]/",
        ).forEach { url ->
            assertFalse(UrlSafety.isAllowedRemoteUrl(url), "应拒绝私网/回环目标: $url")
        }
    }

    @Test
    fun `非 http 协议与畸形地址拒绝`() {
        assertFalse(UrlSafety.isAllowedRemoteUrl("file:///etc/passwd"))
        assertFalse(UrlSafety.isAllowedRemoteUrl("ftp://example.com/a"))
        assertFalse(UrlSafety.isAllowedRemoteUrl("not a url"))
        assertFalse(UrlSafety.isAllowedRemoteUrl(""))
    }
}
