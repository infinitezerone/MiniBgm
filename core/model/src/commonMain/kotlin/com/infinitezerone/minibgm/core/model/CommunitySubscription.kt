package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 社区开源订阅与动态发现的播放源规则。
 *
 * @property name 站点或播放源名称
 * @property urlTemplate 播放/搜索 URL 模板
 * @property description 站点描述与适用范围说明
 * @property latencyMs 测速延迟（毫秒），0 表示未测速
 * @property isAlive 是否通过连通性与健康度探活
 */
@Serializable
data class DiscoveredSource(
    val name: String,
    val urlTemplate: String,
    val description: String = "",
    val latencyMs: Long = 0L,
    val isAlive: Boolean = true,
) {
    /**
     * 转换为本地标准 [PlaybackSourceRule]。
     */
    fun toPlaybackSourceRule(id: String): PlaybackSourceRule =
        PlaybackSourceRule(
            id = id,
            name = name,
            urlTemplate = urlTemplate,
            isEnabled = true,
            description = description,
        )
}

/**
 * 订阅校验与连通性探活报告。
 */
@Serializable
data class SubscriptionValidationReport(
    val isHealthy: Boolean,
    val subscriptionUrl: String,
    val totalRules: Int = 0,
    val aliveRules: Int = 0,
    val averageLatencyMs: Long = 0L,
    val sources: List<DiscoveredSource> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * 社区开源 TVBox 订阅结构兼容定义。
 */
@Serializable
data class TvBoxConfig(
    val sites: List<TvBoxSite> = emptyList(),
)

/**
 * TVBox 站点条目。
 */
@Serializable
data class TvBoxSite(
    val key: String? = null,
    val name: String = "",
    val type: Int? = null,
    val api: String? = null,
    val ext: String? = null,
    val searchable: Int? = 1,
) {
    /**
     * 将 TVBox 站点转换为 MiniBgm 规范的 [DiscoveredSource]。
     */
    fun toDiscoveredSource(): DiscoveredSource? {
        val siteName = resolveSiteName() ?: return null
        val targetUrl = resolveTargetUrl() ?: return null
        val template = resolveUrlTemplate(targetUrl)
        val desc = resolveDescription()

        return DiscoveredSource(
            name = siteName,
            urlTemplate = template,
            description = desc,
        )
    }

    private fun resolveSiteName(): String? {
        val n = name.trim()
        if (n.isNotBlank()) return n
        val k = key?.trim().orEmpty()
        return k.ifBlank { null }
    }

    private fun resolveTargetUrl(): String? {
        val primary = api?.trim().orEmpty()
        val candidate = primary.ifBlank { ext?.trim().orEmpty() }
        val isHttp =
            candidate.startsWith("http://", ignoreCase = true) ||
                candidate.startsWith("https://", ignoreCase = true)
        return if (isHttp) candidate else null
    }

    private fun resolveUrlTemplate(targetUrl: String): String =
        when {
            type == 1 -> if (targetUrl.contains("?")) "$targetUrl&ac=detail&wd={title}" else "$targetUrl?ac=detail&wd={title}"
            targetUrl.contains("{wd}", ignoreCase = true) -> targetUrl.replace("{wd}", "{title}", ignoreCase = true)
            targetUrl.contains("{title}", ignoreCase = true) -> targetUrl
            targetUrl.contains("?") -> "$targetUrl&wd={title}"
            else -> "$targetUrl/search?query={title}"
        }

    private fun resolveDescription(): String =
        when (type) {
            1 -> "TVBox MacCMS 采集源"
            3 -> "TVBox 爬虫/扩展源"
            else -> "TVBox 动漫源"
        }
}
