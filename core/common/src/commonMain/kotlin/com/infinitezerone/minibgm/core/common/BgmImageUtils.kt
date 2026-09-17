package com.infinitezerone.minibgm.core.common

/**
 * Bangumi 图片地址规范化与 CDN 加速工具：
 * 1. 将所有 http:// 图片地址升轨至安全的 https://，避免 Android Cleartext 限制与 301 重定向额外开销；
 * 2. 将未经压缩的 Bangumi 原始扫图（/pic/cover/l/、/pic/crt/l/、/pic/user/l/）透明重写为
 *    Bangumi 官方 CDN 400px 压缩规格（/r/400/...），节约 ~75% 移动端带宽并收拢统一 Coil 缓存键；
 * 3. 保持已有 CDN 规格（如 /r/400/、/r/200/、/r/100/ 等）的幂等性。
 */
object BgmImageUtils {
    const val BGM_IMAGE_HOST = "lain.bgm.tv"
    const val CDN_PREFIX_R400 = "/r/400/"
    const val CDN_PREFIX_R200 = "/r/200/"
    const val CDN_PREFIX_R100 = "/r/100/"

    private val CDN_PREFIXES =
        listOf(
            CDN_PREFIX_R400,
            CDN_PREFIX_R200,
            CDN_PREFIX_R100,
        )

    private val UNCOMPRESSED_PATH_SEGMENTS =
        listOf(
            "/pic/cover/l/",
            "/pic/crt/l/",
            "/pic/user/l/",
        )

    private val LEGACY_COVER_PATH_MAPPINGS =
        listOf(
            "/pic/cover/c/" to "${CDN_PREFIX_R400}pic/cover/l/",
            "/pic/cover/m/" to "${CDN_PREFIX_R400}pic/cover/l/",
        )

    /**
     * 将给定的 URL 升轨为安全的 https:// 协议
     */
    fun toSecureUrl(url: String): String {
        if (url.startsWith("http://", ignoreCase = true)) {
            return "https://" + url.substring(7)
        }
        return url
    }

    /**
     * 将 Bangumi 图片地址优化为 CDN 400px 压缩图。
     * 若非 Bangumi 图片或已有 CDN 规格，则仅做安全升轨处理。
     */
    fun optimizeBgmImageUrl(url: String): String {
        if (url.isBlank()) return ""
        val secure = toSecureUrl(url)
        if (!secure.contains(BGM_IMAGE_HOST)) return secure

        // 已包含 CDN 缩放规格，避免重复包装
        if (CDN_PREFIXES.any { secure.contains(it) }) {
            return secure
        }

        for ((legacy, modern) in LEGACY_COVER_PATH_MAPPINGS) {
            if (secure.contains(legacy)) {
                return secure.replace(legacy, modern)
            }
        }

        for (segment in UNCOMPRESSED_PATH_SEGMENTS) {
            if (secure.contains(segment)) {
                return secure.replace(segment, "$CDN_PREFIX_R400${segment.removePrefix("/")}")
            }
        }

        return secure
    }
}
