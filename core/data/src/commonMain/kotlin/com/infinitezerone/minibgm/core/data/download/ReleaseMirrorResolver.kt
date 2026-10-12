package com.infinitezerone.minibgm.core.data.download

/**
 * GitHub Release 资产镜像与代理加速解析器。
 *
 * 针对国内直连 GitHub Releases 速度慢或握手超时的痛点，按优先级生成镜像候选列表：
 * 1. 用户自定义镜像前缀（若设置）
 * 2. 预设优质公共加速镜像（ghproxy 等）
 * 3. 官方直链兜底
 */
object ReleaseMirrorResolver {
    /** 默认公共加速镜像前缀列表 */
    val DEFAULT_MIRROR_PREFIXES: List<String> =
        listOf(
            "https://gh-proxy.com",
            "https://ghproxy.net",
        )

    /**
     * 将原始 GitHub 资产 URL 解析为按优先级排序的可用下载镜像列表。
     *
     * @param rawUrl 原始下载 URL
     * @param customPrefix 用户在设置中自定义的镜像前缀（如 "https://my-proxy.workers.dev"），为空则不生效
     * @return 候选下载 URL 列表，按尝试优先级升序
     */
    fun resolveCandidateUrls(
        rawUrl: String,
        customPrefix: String? = null,
    ): List<String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return emptyList()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return listOf(trimmed)
        }

        // 仅对 github.com 资产应用镜像代理规则
        if (!trimmed.contains("github.com", ignoreCase = true)) {
            return listOf(trimmed)
        }

        val candidates = mutableListOf<String>()

        // 1. 用户自定义前缀优先
        if (!customPrefix.isNullOrBlank()) {
            val cleanPrefix = customPrefix.trim().trimEnd('/')
            candidates.add(combineUrl(cleanPrefix, trimmed))
        }

        // 2. 预设优质公共镜像
        for (prefix in DEFAULT_MIRROR_PREFIXES) {
            candidates.add(combineUrl(prefix, trimmed))
        }

        // 3. 官方直链兜底
        if (!candidates.contains(trimmed)) {
            candidates.add(trimmed)
        }

        return candidates.distinct()
    }

    private fun combineUrl(
        prefix: String,
        targetUrl: String,
    ): String = "$prefix/$targetUrl"
}
