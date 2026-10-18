package com.infinitezerone.minibgm.core.network

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.takeFrom

/**
 * 用户/规则提供的远端 URL 的安全闸。
 *
 * 只做「不该由第三方规则驱动的目标」拦截：非 http(s)、本机回环、私网、
 * 链路本地、CGNAT、IPv6 ULA/link-local。**媒体直链探测不套这道闸**——
 * 用户自己的 NAS/局域网媒体服务器是合法目标；这里只约束"抓页面/拉订阅"。
 *
 * 注意：这是服务层的第一道闸，挡不住 DNS 解析到私网或 30x 跳转；调用方
 * 还必须在拿到最终响应后复核 [Url]（见 PageFetchService / CommunitySubscriptionService）。
 */
internal object UrlSafety {
    fun isAllowedRemoteUrl(raw: String): Boolean {
        val url = runCatching { Url(raw.trim()) }.getOrNull() ?: return false
        if (url.protocol != URLProtocol.HTTP && url.protocol != URLProtocol.HTTPS) return false
        return !isBlockedHost(url.host)
    }

    /** 解析 30x 的 Location（支持相对路径）；非法地址返回 null，由调用方按"不可信"处理。 */
    fun resolveRedirect(
        currentUrl: String,
        location: String,
    ): String? = runCatching { URLBuilder(currentUrl).takeFrom(location.trim()).buildString() }.getOrNull()

    fun isBlockedHost(rawHost: String): Boolean {
        val host =
            rawHost
                .trim()
                .lowercase()
                .removeSurrounding("[", "]")
                .trimEnd('.')
        if (host.isBlank()) return true
        if (host == "localhost" || host.endsWith(".localhost")) return true
        if (host == "0.0.0.0" || host == "::" || host == "::1") return true
        // IPv4-mapped IPv6 一律拒绝：hex 形式解析成本高，正常规则页也用不到
        if (host.startsWith("::ffff:")) return true
        if (isNumericIpLiteral(host)) return true
        if (isPrivateIpv4(host)) return true
        return isPrivateIpv6(host)
    }

    /**
     * 十进制/十六进制/八进制 IP 字面量：系统解析器会把 2130706433、0x7f000001、
     * 0177.0.0.1 都解释成 127.0.0.1，按字符串当然看不出是私网。
     * 正常规则页不会用这种写法，直接拒绝。
     */
    private fun isNumericIpLiteral(host: String): Boolean {
        if (host.startsWith("0x")) return true
        if (host.all { it.isDigit() }) return true
        if (!host.all { it.isDigit() || it == '.' }) return false
        return host.split('.').any { it.length > 1 && it.startsWith('0') }
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return when {
            octets[0] == 0 -> true
            octets[0] == 10 -> true
            octets[0] == 127 -> true
            octets[0] == 169 && octets[1] == 254 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            // RFC 6598 CGNAT
            octets[0] == 100 && octets[1] in 64..127 -> true
            // 组播/保留段：不可能是正常的规则页
            octets[0] in 224..255 -> true
            else -> false
        }
    }

    private fun isPrivateIpv6(host: String): Boolean {
        if (!host.contains(':')) return false
        // fc00::/7 (ULA), fe80::/10 (link-local)
        return host.startsWith("fc") ||
            host.startsWith("fd") ||
            host.startsWith("fe8") ||
            host.startsWith("fe9") ||
            host.startsWith("fea") ||
            host.startsWith("feb")
    }
}
