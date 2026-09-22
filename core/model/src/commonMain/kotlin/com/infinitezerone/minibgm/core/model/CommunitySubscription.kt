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
 * @property kind 规则用途。**接口端点必须标 [PlaybackRuleKind.SOURCE]**：解析器分发只发生在取源侧，
 *   标成 PAGE 会让 [parserType] 在播放时被丢掉、静默降级成嗅探（见 [PlaybackSourceRule.isResolvable] 的说明）
 * @property parserType 与网页搜索模板相对；接口端点按响应形态选解析器
 */
@Serializable
data class DiscoveredSource(
    val name: String,
    val urlTemplate: String,
    val description: String = "",
    val latencyMs: Long = 0L,
    val isAlive: Boolean = true,
    val kind: PlaybackRuleKind = PlaybackRuleKind.PAGE,
    val parserType: RuleParserType = RuleParserType.AUTO,
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
            kind = kind,
            parserType = parserType,
        )
}

/**
 * 订阅校验与连通性探活报告。
 *
 * [skippedUnsupportedSites] / [skippedMalformedSites] 用于**如实报数**：订阅包里的站点被跳过后，
 * 调用方必须能告诉用户"收下几条、跳过几条、为什么"——否则用户会以为导了一堆源其实全是废的。
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
    /** 本应用没有执行路径的源类型（TVBox 爬虫/扩展源、脚本端点） */
    val skippedUnsupportedSites: Int = 0,
    /** 站点条目自身不完整（缺名称、缺接口地址、地址非 http） */
    val skippedMalformedSites: Int = 0,
)

/**
 * 社区开源 TVBox 订阅结构兼容定义。
 */
@Serializable
data class TvBoxConfig(
    val sites: List<TvBoxSite> = emptyList(),
)

/**
 * TVBox 站点转换结果。
 *
 * 之所以不是「可空返回值」：跳过必须能分类，否则调用方只能笼统报"没解析到"，
 * 分不清是自己不支持（爬虫源）还是条目本身残缺。
 */
sealed interface TvBoxSiteConversion {
    /** 可转换：接口地址与名称齐备，且本应用有对应的抽取路径 */
    data class Supported(
        val source: DiscoveredSource,
    ) : TvBoxSiteConversion

    /** 本应用没有执行路径：爬虫/扩展源，或端点指向脚本资源 */
    data object Unsupported : TvBoxSiteConversion

    /** 条目自身不完整：缺名称、缺接口地址、地址非 http(s) */
    data object Malformed : TvBoxSiteConversion
}

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
     *
     * 产出的规则一律是 [PlaybackRuleKind.SOURCE]：TVBox 的 `api` 是**接口端点**不是页面，
     * 标成 PAGE 会让播放时走嗅探并丢掉 [RuleParserType]（专用 MacCMS 抽取器就用不上了）。
     * `type == 1` 是苹果 CMS V10 约定，直接声明 [RuleParserType.MACCMS]；其余交给 AUTO 按响应形态识别。
     */
    fun convert(): TvBoxSiteConversion {
        val siteName = resolveSiteName() ?: return TvBoxSiteConversion.Malformed
        // type 3 是需要 jar/js 爬虫实现的扩展源，本应用没有执行路径，收下只会变成废规则
        if (type == TYPE_SPIDER) return TvBoxSiteConversion.Unsupported

        val targetUrl = resolveTargetUrl() ?: return TvBoxSiteConversion.Malformed
        if (isScriptEndpoint(targetUrl)) return TvBoxSiteConversion.Unsupported

        return TvBoxSiteConversion.Supported(
            DiscoveredSource(
                name = siteName,
                urlTemplate = resolveUrlTemplate(targetUrl),
                description = resolveDescription(),
                kind = PlaybackRuleKind.SOURCE,
                parserType = if (type == TYPE_MACCMS) RuleParserType.MACCMS else RuleParserType.AUTO,
            ),
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

    private fun isScriptEndpoint(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        return SCRIPT_SUFFIXES.any { path.endsWith(it, ignoreCase = true) }
    }

    private fun resolveUrlTemplate(targetUrl: String): String =
        when {
            type == TYPE_MACCMS -> if (targetUrl.contains("?")) "$targetUrl&ac=detail&wd={title}" else "$targetUrl?ac=detail&wd={title}"
            targetUrl.contains("{wd}", ignoreCase = true) -> targetUrl.replace("{wd}", "{title}", ignoreCase = true)
            targetUrl.contains("{title}", ignoreCase = true) -> targetUrl
            targetUrl.contains("?") -> "$targetUrl&wd={title}"
            else -> "$targetUrl/search?query={title}"
        }

    private fun resolveDescription(): String =
        when (type) {
            TYPE_MACCMS -> "TVBox MacCMS 采集源"
            TYPE_SPIDER -> "TVBox 爬虫/扩展源"
            else -> "TVBox 动漫源"
        }

    companion object {
        /** TVBox 约定：苹果 CMS V10 JSON 接口 */
        private const val TYPE_MACCMS = 1

        /** TVBox 约定：需要 jar/js 爬虫实现的扩展源 */
        private const val TYPE_SPIDER = 3

        private val SCRIPT_SUFFIXES = listOf(".js", ".jar", ".css")
    }
}
