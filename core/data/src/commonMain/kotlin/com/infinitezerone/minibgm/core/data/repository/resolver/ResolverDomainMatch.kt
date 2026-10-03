package com.infinitezerone.minibgm.core.data.repository.resolver

/**
 * 判断两个地址是否属于同一个注册域（eTLD+1）。
 *
 * 用于分集/目录链接的下探选择：iframe 跟进不设域限制（内嵌播放器常跨注册域），
 * 但列表页挑链接必须留在本注册域内。
 * 视频常托管在另一个 CDN 域，卡相等域名会误杀正常站点。
 * 无法判定（拿不到 host）时放行：这一版只拦"顺着链接跑到别的站点"。
 */
internal fun sameRegistrableDomain(
    firstUrl: String,
    secondUrl: String,
): Boolean {
    val first = registrableDomainOf(firstUrl) ?: return true
    val second = registrableDomainOf(secondUrl) ?: return true
    return first == second
}

private val TWO_LEVEL_SUFFIXES =
    setOf(
        "com.cn",
        "net.cn",
        "org.cn",
        "gov.cn",
        "edu.cn",
        "com.tw",
        "com.hk",
        "com.mo",
        "co.uk",
        "org.uk",
        "ac.uk",
        "gov.uk",
        "co.jp",
        "or.jp",
        "ne.jp",
        "ac.jp",
        "co.kr",
        "com.br",
        "com.au",
        "com.ru",
        "com.ua",
        "co.in",
        "com.sg",
        "com.my",
    )

private fun registrableDomainOf(url: String): String? {
    val host =
        url
            .substringAfter("://", "")
            .substringBefore('/')
            .substringBefore(':')
            .trim('.')
            .lowercase()
    if (host.isBlank() || host.count { it == '.' } < 1) return null
    val labels = host.split('.')
    val suffixLength =
        if (labels.size >= 3 && labels.takeLast(2).joinToString(".") in TWO_LEVEL_SUFFIXES) 3 else 2
    return labels.takeLast(suffixLength).joinToString(".")
}
