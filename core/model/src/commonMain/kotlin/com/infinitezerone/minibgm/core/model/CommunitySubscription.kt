package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
    val parses: List<TvBoxParse> = emptyList(),
    val errorMessage: String? = null,
    /** 本应用没有执行路径的源类型（TVBox 爬虫/扩展源、脚本端点） */
    val skippedUnsupportedSites: Int = 0,
    /** 站点条目自身不完整（缺名称、缺接口地址、地址非 http） */
    val skippedMalformedSites: Int = 0,
)

/**
 * TVBox 聚合解析接口模型。
 *
 * @property name 解析名称
 * @property type 解析类型：0=网页嗅探/iframe, 1=JSON 直链接口, 2=深度嗅探, 3=脚本扩展
 * @property url 解析地址（通常包含 `?url=`）
 * @property ext 额外参数（支持字符串或 JSON 对象）
 * @property header 自定义请求头
 */
@Serializable
data class TvBoxParse(
    val name: String = "",
    val type: Int = 0,
    val url: String = "",
    val ext: JsonElement? = null,
    val header: Map<String, String> = emptyMap(),
)

/**
 * 社区开源 TVBox 订阅结构兼容定义。
 */
@Serializable
data class TvBoxConfig(
    val sites: List<TvBoxSite> = emptyList(),
    val parses: List<TvBoxParse> = emptyList(),
    val flags: List<String> = emptyList(),
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
        // type 3 是爬虫/扩展源，若提供了可运行的 JS 脚本地址，映射为 SPIDER 解析器
        if (type == TYPE_SPIDER) {
            val scriptUrl = resolveSpiderScriptUrl()
            if (scriptUrl != null) {
                return TvBoxSiteConversion.Supported(
                    DiscoveredSource(
                        name = siteName,
                        urlTemplate = scriptUrl,
                        description = resolveDescription(),
                        kind = PlaybackRuleKind.SOURCE,
                        parserType = RuleParserType.SPIDER,
                    ),
                )
            }
            return TvBoxSiteConversion.Unsupported
        }

        val targetUrl = resolveTargetUrl() ?: return TvBoxSiteConversion.Malformed
        if (targetUrl.endsWith(".js", ignoreCase = true)) {
            return TvBoxSiteConversion.Supported(
                DiscoveredSource(
                    name = siteName,
                    urlTemplate = targetUrl,
                    description = resolveDescription(),
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.SPIDER,
                ),
            )
        }
        if (isScriptEndpoint(targetUrl)) return TvBoxSiteConversion.Unsupported

        val hasCustomPlaceholder = targetUrl.contains("{title}", ignoreCase = true) || targetUrl.contains("{wd}", ignoreCase = true)
        val parserType =
            when {
                type == TYPE_MACCMS -> RuleParserType.MACCMS
                type == TYPE_XML && !hasCustomPlaceholder -> RuleParserType.MACCMS
                else -> RuleParserType.AUTO
            }

        return TvBoxSiteConversion.Supported(
            DiscoveredSource(
                name = siteName,
                urlTemplate = resolveUrlTemplate(targetUrl),
                description = resolveDescription(hasCustomPlaceholder),
                kind = PlaybackRuleKind.SOURCE,
                parserType = parserType,
            ),
        )
    }

    private fun resolveSpiderScriptUrl(): String? {
        val primary = api?.trim().orEmpty()
        val secondary = ext?.trim().orEmpty()
        return when {
            primary.endsWith(".js", ignoreCase = true) -> primary
            secondary.endsWith(".js", ignoreCase = true) -> secondary
            isHttpUrl(primary) && !primary.endsWith(".jar", ignoreCase = true) -> primary
            isHttpUrl(secondary) && !secondary.endsWith(".jar", ignoreCase = true) -> secondary
            else -> null
        }
    }

    private fun isHttpUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

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
            targetUrl.contains("{wd}", ignoreCase = true) -> targetUrl.replace("{wd}", "{title}", ignoreCase = true)
            targetUrl.contains("{title}", ignoreCase = true) -> targetUrl
            type == TYPE_MACCMS -> if (targetUrl.contains("?")) "$targetUrl&ac=detail&wd={title}" else "$targetUrl?ac=detail&wd={title}"
            type == TYPE_XML -> if (targetUrl.contains("?")) "$targetUrl&ac=videolist&wd={title}" else "$targetUrl?ac=videolist&wd={title}"
            targetUrl.contains("?") -> "$targetUrl&wd={title}"
            else -> "$targetUrl/search?query={title}"
        }

    private fun resolveDescription(hasCustomPlaceholder: Boolean = false): String =
        when {
            type == TYPE_XML && !hasCustomPlaceholder -> "TVBox XML 采集源"
            type == TYPE_MACCMS -> "TVBox MacCMS 采集源"
            type == TYPE_SPIDER -> "TVBox 爬虫/扩展源"
            else -> "TVBox 动漫源"
        }

    companion object {
        /** TVBox 约定：XML 采集接口（苹果 CMS V8 等） */
        private const val TYPE_XML = 0

        /** TVBox 约定：苹果 CMS V10 JSON 接口 */
        private const val TYPE_MACCMS = 1

        /** TVBox 约定：需要 jar/js 爬虫实现的扩展源 */
        private const val TYPE_SPIDER = 3

        private val SCRIPT_SUFFIXES = listOf(".js", ".jar", ".css")
    }
}

private val CANDIDATE_HTTP_URL_REGEX = Regex("""https?://[a-zA-Z0-9\-._~:/?#\[\]@!$&*+,;%=]+""", RegexOption.IGNORE_CASE)

/**
 * 识别并提取剪贴板文本中的播放源订阅候选链接。
 *
 * 提取准则：
 * 1. 文本中包含以 http:// 或 https:// 开头的网络地址；
 * 2. 剥离末尾常见的附带标点（如句号、括号、引号等）；
 * 3. 排除 Bangumi / B站 等本应用业务域名，避免将分享的番剧链接误判为播放源；
 * 4. 判定是否符合订阅特征：
 *    - 扩展名包含 .json、.txt、.conf；
 *    - 路径或参数包含 tvbox、maccms、provide/vod、api.php、subscribe、sub、rules 等；
 *    - 或整段文本经清理后本身就是一个单一的合法 URL（用户明确复制的单个地址）。
 */
fun extractSubscriptionCandidate(rawText: String): String? {
    val trimmed = rawText.trim()
    if (trimmed.isBlank()) return null

    val match = CANDIDATE_HTTP_URL_REGEX.find(trimmed) ?: return null
    var url = match.value.trim()
    while (url.isNotEmpty() &&
        (
            url.endsWith(".") ||
                url.endsWith(",") ||
                url.endsWith(";") ||
                url.endsWith(")") ||
                url.endsWith("]") ||
                url.endsWith("}") ||
                url.endsWith("。") ||
                url.endsWith("，") ||
                url.endsWith("；")
        )
    ) {
        url = url.dropLast(1)
    }

    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        return null
    }

    val lower = url.lowercase()

    // 排除已知非播放源订阅的常见业务域名与端点
    val ignoredHosts = listOf("bgm.tv", "bangumi.tv", "chii.in", "bilibili.com", "b23.tv")
    if (ignoredHosts.any { lower.contains("://$it") || lower.contains(".$it") }) {
        return null
    }

    val hasSubscriptionFeature =
        lower.contains(".json") ||
            lower.contains(".txt") ||
            lower.contains(".conf") ||
            lower.contains("tvbox") ||
            lower.contains("maccms") ||
            lower.contains("api.php") ||
            lower.contains("provide/vod") ||
            lower.contains("subscribe") ||
            lower.contains("sub") ||
            lower.contains("rules")

    if (hasSubscriptionFeature) {
        return url
    }

    // 若整段文本就是纯粹的单个 URL，且长度在合理范围内，同样作为候选
    if (trimmed == url && url.length in 12..512) {
        return url
    }

    return null
}
