package com.infinitezerone.minibgm.core.common

import kotlin.jvm.JvmName

private const val BGM_IMAGE_HOST = "lain.bgm.tv"
private const val CDN_PREFIX_R400 = "/r/400/"
private val CDN_PREFIXES = listOf("/r/400/", "/r/200/", "/r/100/")

private val LEGACY_COVER_PATTERN =
    Regex("""^(https?://[^/]+/pic/cover/)[cm]/(.+)$""", RegexOption.IGNORE_CASE)

private val UNCOMPRESSED_PATTERN =
    Regex("""^(https?://[^/]+)/pic/(cover|crt|user)/l/(.+)$""", RegexOption.IGNORE_CASE)

/**
 * 判断域名或完整 URL 是否属于 Bangumi 官方域（如 bgm.tv / bangumi.tv / chii.in 及其子域）
 */
fun isBgmDomain(hostOrUrl: String): Boolean {
    if (hostOrUrl.isBlank()) return false
    val host =
        if (hostOrUrl.contains("://")) {
            hostOrUrl.substringAfter("://").substringBefore('/').substringBefore(':')
        } else {
            hostOrUrl.substringBefore('/').substringBefore(':')
        }.trim().lowercase()

    return host == "bgm.tv" || host.endsWith(".bgm.tv") ||
        host == "bangumi.tv" || host.endsWith(".bangumi.tv") ||
        host == "chii.in" || host.endsWith(".chii.in")
}

/** 属性化访问糖 */
@get:JvmName("isBgmDomainProp")
val String.isBgmDomain: Boolean
    get() = isBgmDomain(this)

/**
 * 将给定的 URL 升轨为安全的 https:// 协议（针对 Bangumi 官方域名生效，避免破坏不支持 HTTPS 的特定外部自建源）
 */
fun String.toSecureUrl(): String {
    if (startsWith("http://", ignoreCase = true) && isBgmDomain(this)) {
        return "https://" + substring(7)
    }
    return this
}

/**
 * 将 Bangumi 图片地址优化为 CDN 400px 压缩图。
 * 若非 Bangumi 官方图片或已有 CDN 规格，则保持幂等。
 */
fun String.toBgmCdnUrl(): String {
    if (isBlank()) return ""
    val secure = toSecureUrl()
    if (!secure.contains(BGM_IMAGE_HOST, ignoreCase = true)) return secure

    // 已包含 CDN 缩放规格，避免重复包装
    if (CDN_PREFIXES.any { secure.contains(it, ignoreCase = true) }) {
        return secure
    }

    val legacyMatch = LEGACY_COVER_PATTERN.matchEntire(secure)
    if (legacyMatch != null) {
        val prefix = legacyMatch.groupValues[1]
        val remainder = legacyMatch.groupValues[2]
        return prefix.replace("/pic/cover/", "${CDN_PREFIX_R400}pic/cover/l/") + remainder
    }

    val uncompressedMatch = UNCOMPRESSED_PATTERN.matchEntire(secure)
    if (uncompressedMatch != null) {
        val origin = uncompressedMatch.groupValues[1]
        val category = uncompressedMatch.groupValues[2]
        val remainder = uncompressedMatch.groupValues[3]
        return "$origin${CDN_PREFIX_R400}pic/$category/l/$remainder"
    }

    return secure
}
