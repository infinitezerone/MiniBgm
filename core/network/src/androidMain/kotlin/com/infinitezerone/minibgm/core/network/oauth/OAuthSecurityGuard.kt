package com.infinitezerone.minibgm.core.network.oauth

import io.ktor.http.HttpHeaders

/**
 * 环回代理安全防御组件。
 *
 * 负责 SSRF 防护、目标域名白名单校验、以及基于 Cookie / Sec-Fetch-Dest 的会话鉴权。
 */
internal object OAuthSecurityGuard {
    const val SESSION_COOKIE_NAME: String = "minibgm_inapp_web"

    /** 代理允许转发的目标主域白名单（含其所有子域名），防止沦为任意目标的开放代理。 */
    val ALLOWED_PROXY_DOMAINS: List<String> = listOf("bgm.tv", "bangumi.tv", "chii.in")

    /** CONNECT 隧道允许连接的外部辅助域（如 Cloudflare 挑战）。 */
    val ALLOWED_CONNECT_DOMAINS: List<String> =
        listOf(
            "challenges.cloudflare.com",
        )

    /**
     * 校验 CONNECT 目标地址，杜绝内网 IP 与非白名单域名。
     */
    fun isAllowedConnectHost(
        rawTarget: String,
        upstreamHost: String,
    ): Boolean {
        val host = rawTarget.substringBeforeLast(':', rawTarget).trim().lowercase()
        // 杜绝任何环回与私有内网 IP，防止 SSRF
        if (host == "localhost" ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            host.startsWith("172.")
        ) {
            return false
        }
        if (isAllowedUpstreamHost(host, upstreamHost)) return true
        return ALLOWED_CONNECT_DOMAINS.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }

    /**
     * 校验普通 HTTP 请求的目标上游是否在白名单之内。
     */
    fun isAllowedUpstreamHost(
        targetHost: String,
        upstreamHost: String,
    ): Boolean {
        val host = targetHost.lowercase()
        if (host == upstreamHost.lowercase()) return true
        return ALLOWED_PROXY_DOMAINS.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }

    /**
     * 校验请求是否来自本应用的 WebView 会话。
     *
     * 规则（遵循 W3C Fetch Metadata 标准）：
     * 1. 携带合法会话令牌 Cookie 的请求恒放行；
     * 2. 现代 Chromium WebView 发出的子资源请求（图片/脚本/样式/字体等）带有 `Sec-Fetch-Dest` 头。
     *    当目标并非主文档（Sec-Fetch-Dest != "document"）且为只读安全方法（GET/HEAD）时放行，
     *    彻底解决跨域或子资源未附加 Cookie 导致的页面渲染残缺；
     * 3. 任何主文档页面跳转（document / navigate）或非只读操作（POST/PUT 等）未带令牌一律阻断。
     */
    fun hasValidSession(
        inboundHeaders: Map<String, String>,
        token: String,
        method: String,
    ): Boolean {
        val cookie =
            inboundHeaders.entries
                .firstOrNull { it.key.equals(HttpHeaders.Cookie, ignoreCase = true) }
                ?.value
        val hasToken = cookie?.split(';')?.any { it.trim() == "$SESSION_COOKIE_NAME=$token" } == true
        if (hasToken) return true

        val isSafeMethod = method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true)
        val fetchDest = inboundHeaders.entries.firstOrNull { it.key.equals("sec-fetch-dest", ignoreCase = true) }?.value
        val isSubResource = fetchDest != null && !fetchDest.equals("document", ignoreCase = true)
        return isSafeMethod && isSubResource
    }
}
