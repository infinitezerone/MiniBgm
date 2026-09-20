package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.model.DiscoveredSource
import com.infinitezerone.minibgm.core.model.DiscoveredSubscriptionCandidate
import com.infinitezerone.minibgm.core.model.SubscriptionValidationReport
import com.infinitezerone.minibgm.core.model.TvBoxConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.measureTimedValue

/**
 * 社区开源订阅响应实体
 */
@Serializable
data class CommunitySubscriptionPackage(
    val version: Int = 1,
    val description: String = "",
    val sources: List<DiscoveredSource> = emptyList(),
)

@Serializable
internal data class GitHubSearchRepoResponse(
    val items: List<GitHubRepoItem> = emptyList(),
)

@Serializable
internal data class GitHubRepoItem(
    val name: String,
    val full_name: String,
    val description: String? = null,
    val default_branch: String = "main",
)

/**
 * 社区开源播放源动态发现、测速与质检服务接口
 */
interface CommunitySubscriptionService {
    /**
     * 在公网平台（如 GitHub 开源索引）动态检索动漫播放源规则订阅，并对候选地址进行可用性初筛。
     */
    suspend fun searchSubscriptions(keywords: String = ""): List<DiscoveredSubscriptionCandidate>

    /**
     * 对指定订阅地址或规则 JSON 文本进行拉取/解析，并对其中的规则进行端侧并发连通性与测速探活。
     */
    suspend fun validateAndTestSubscription(target: String): SubscriptionValidationReport

    /**
     * 发现并测速社区开源播放源规则，按响应延迟由低到高排序。
     * 若未传入 customSubscriptionUrl，则动态从公网检索可用的订阅候选并返回测速后的播放源列表。
     */
    suspend fun fetchAndTestCommunitySources(customSubscriptionUrl: String? = null): List<DiscoveredSource>
}

class CommunitySubscriptionServiceImpl(
    private val client: HttpClient,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        },
) : CommunitySubscriptionService {
    override suspend fun searchSubscriptions(keywords: String): List<DiscoveredSubscriptionCandidate> {
        val trimmed = keywords.trim()
        if (isDirectInput(trimmed)) {
            return validateDirectInput(trimmed)
        }

        val query = trimmed.ifBlank { "anime playback rules" }
        return try {
            val response: HttpResponse =
                client.get("https://api.github.com/search/repositories") {
                    parameter("q", query)
                    parameter("sort", "updated")
                    parameter("per_page", "5")
                    headers {
                        append(HttpHeaders.Accept, "application/vnd.github+json")
                        append(HttpHeaders.UserAgent, "MiniBgm-Discovery")
                    }
                    timeout {
                        requestTimeoutMillis = 6000
                        connectTimeoutMillis = 3000
                    }
                }

            if (!response.status.isSuccess()) {
                return emptyList()
            }

            val searchResult = json.decodeFromString<GitHubSearchRepoResponse>(response.body())
            coroutineScope {
                searchResult.items
                    .map { item ->
                        async {
                            inspectRepoCandidate(item)
                        }
                    }.awaitAll()
                    .filterNotNull()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun isDirectInput(input: String): Boolean =
        input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true) ||
            input.startsWith("[") ||
            input.startsWith("{")

    private suspend fun validateDirectInput(input: String): List<DiscoveredSubscriptionCandidate> {
        val report = validateAndTestSubscription(input)
        return if (report.isHealthy) {
            listOf(
                DiscoveredSubscriptionCandidate(
                    name = if (input.startsWith("http")) "自定义规则订阅" else "现场发现的播放源规则",
                    subscriptionUrl = if (input.startsWith("http")) input else "direct://custom-rules",
                    description = "经端侧网络测速探活验证的播放源规则",
                    sourceCount = report.totalRules,
                    aliveCount = report.aliveRules,
                    averageLatencyMs = report.averageLatencyMs,
                    sampleSources = report.sources.take(3).map { it.name },
                ),
            )
        } else {
            emptyList()
        }
    }

    override suspend fun validateAndTestSubscription(target: String): SubscriptionValidationReport {
        val trimmed = target.trim()
        if (trimmed.isBlank()) {
            return SubscriptionValidationReport(
                isHealthy = false,
                subscriptionUrl = target,
                errorMessage = "订阅地址或规则内容为空",
            )
        }

        val rawSources = resolveSources(trimmed)
        if (rawSources.isEmpty()) {
            return SubscriptionValidationReport(
                isHealthy = false,
                subscriptionUrl = trimmed,
                errorMessage = "未解析到有效播放源规则，请检查网络连接或规则 JSON 格式",
            )
        }

        // 并发进行端侧测速探活
        val testedSources =
            coroutineScope {
                rawSources
                    .map { source ->
                        async {
                            testSourceConnectivity(source)
                        }
                    }.awaitAll()
            }

        val sortedSources =
            testedSources.sortedWith(
                compareByDescending<DiscoveredSource> { it.isAlive }
                    .thenBy { it.latencyMs },
            )
        val aliveList = sortedSources.filter { it.isAlive }
        val avgLatency =
            if (aliveList.isNotEmpty()) {
                aliveList.map { it.latencyMs }.average().toLong()
            } else {
                0L
            }

        return SubscriptionValidationReport(
            isHealthy = aliveList.isNotEmpty(),
            subscriptionUrl = trimmed,
            totalRules = sortedSources.size,
            aliveRules = aliveList.size,
            averageLatencyMs = avgLatency,
            sources = sortedSources,
        )
    }

    override suspend fun fetchAndTestCommunitySources(customSubscriptionUrl: String?): List<DiscoveredSource> {
        if (!customSubscriptionUrl.isNullOrBlank()) {
            return validateAndTestSubscription(customSubscriptionUrl).sources
        }

        val candidates = searchSubscriptions("anime playback rules")
        val best = candidates.maxByOrNull { it.aliveCount } ?: return emptyList()
        return validateAndTestSubscription(best.subscriptionUrl).sources
    }

    private suspend fun inspectRepoCandidate(item: GitHubRepoItem): DiscoveredSubscriptionCandidate? {
        val candidateUrls =
            listOf(
                "https://raw.githubusercontent.com/${item.full_name}/${item.default_branch}/rules.json",
                "https://raw.githubusercontent.com/${item.full_name}/${item.default_branch}/tvbox.json",
                "https://raw.githubusercontent.com/${item.full_name}/${item.default_branch}/anime-rules.json",
                "https://raw.githubusercontent.com/${item.full_name}/${item.default_branch}/sources.json",
                "https://raw.githubusercontent.com/${item.full_name}/${item.default_branch}/playback.json",
                "https://raw.githubusercontent.com/${item.full_name}/${item.default_branch}/custom.json",
                "https://fastly.jsdelivr.net/gh/${item.full_name}@${item.default_branch}/rules.json",
                "https://fastly.jsdelivr.net/gh/${item.full_name}@${item.default_branch}/tvbox.json",
                "https://fastly.jsdelivr.net/gh/${item.full_name}@${item.default_branch}/anime-rules.json",
            )

        for (candidateUrl in candidateUrls) {
            val report = validateAndTestSubscription(candidateUrl)
            if (report.isHealthy && report.aliveRules > 0) {
                return DiscoveredSubscriptionCandidate(
                    name = item.name,
                    subscriptionUrl = candidateUrl,
                    description = item.description ?: "GitHub 开源社区动漫播放源规则",
                    sourceCount = report.totalRules,
                    aliveCount = report.aliveRules,
                    averageLatencyMs = report.averageLatencyMs,
                    sampleSources = report.sources.take(3).map { it.name },
                )
            }
        }
        return null
    }

    private suspend fun resolveSources(target: String): List<DiscoveredSource> {
        if (target.startsWith("[") || target.startsWith("{")) {
            return parseSourcesFromText(target)
        }
        return fetchSourcesFromUrl(target)
    }

    private fun parseSourcesFromText(
        rawText: String,
        pageUrl: String = "",
    ): List<DiscoveredSource> {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return emptyList()

        // 1. 直接解析 MiniBgm 规则数组
        if (trimmed.startsWith("[")) {
            val directList = runCatching { json.decodeFromString<List<DiscoveredSource>>(trimmed) }.getOrNull()
            if (!directList.isNullOrEmpty()) return directList
        }

        // 2. 解析 MiniBgm 标准订阅包
        val pkg = runCatching { json.decodeFromString<CommunitySubscriptionPackage>(trimmed) }.getOrNull()
        if (pkg != null && pkg.sources.isNotEmpty()) {
            return pkg.sources
        }

        // 3. 解析 TVBox 标准配置包 {"sites": [...]}
        val tvBox = runCatching { json.decodeFromString<TvBoxConfig>(trimmed) }.getOrNull()
        if (tvBox != null && tvBox.sites.isNotEmpty()) {
            val converted = tvBox.sites.mapNotNull { it.toDiscoveredSource() }
            if (converted.isNotEmpty()) return converted
        }

        // 4. 纯 HTML 文本兜底提取
        extractSourceFromHtml(trimmed, pageUrl)?.let { return listOf(it) }

        return emptyList()
    }

    private suspend fun fetchSourcesFromUrl(url: String): List<DiscoveredSource> {
        return try {
            val response: HttpResponse =
                client.get(url) {
                    timeout {
                        requestTimeoutMillis = 6000
                        connectTimeoutMillis = 3000
                    }
                }
            if (!response.status.isSuccess()) return emptyList()

            val rawText: String = response.body()
            parseSourcesFromText(rawText, url)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    internal fun extractSourceFromHtml(
        html: String,
        pageUrl: String = "",
    ): DiscoveredSource? {
        val trimmed = html.trim()
        if (!isHtmlContent(trimmed)) return null

        val cleanedTitle = extractCleanedTitle(trimmed, pageUrl)
        val template = extractSearchTemplate(trimmed, pageUrl)

        return DiscoveredSource(
            name = cleanedTitle,
            urlTemplate = template,
            description = "由客户端运行时动态解析网页提取的搜索规则",
        )
    }

    private fun isHtmlContent(trimmed: String): Boolean =
        trimmed.contains("<html", ignoreCase = true) ||
            trimmed.contains("<title", ignoreCase = true) ||
            trimmed.contains("<!DOCTYPE", ignoreCase = true)

    private fun extractCleanedTitle(
        trimmed: String,
        pageUrl: String,
    ): String {
        val titleMatch = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.IGNORE_CASE).find(trimmed)
        val rawTitle =
            titleMatch
                ?.groupValues
                ?.get(1)
                ?.trim()
                .orEmpty()
        return rawTitle
            .replace(Regex("""[-_|–\s]+(首页|在线看|在线动漫|动漫在线|高清|官方网站|最新更新|ACG|官方|APP).*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank {
                if (pageUrl.isNotBlank()) extractHostName(pageUrl) else "第三方动漫站"
            }
    }

    private fun extractSearchTemplate(
        trimmed: String,
        pageUrl: String,
    ): String {
        val cleanOrigin = if (pageUrl.isNotBlank()) extractBaseHost(pageUrl).trimEnd('/') else ""
        val formActionMatch = Regex("""<form[^>]*action=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(trimmed)
        val rawAction =
            formActionMatch
                ?.groupValues
                ?.get(1)
                ?.trim()
                .orEmpty()
        val inputNameMatch =
            Regex(
                """<input[^>]*name=["'](q|query|wd|keyword|search|s|k|word)["']""",
                RegexOption.IGNORE_CASE,
            ).find(trimmed)
        val inputName = inputNameMatch?.groupValues?.get(1)?.trim() ?: "query"

        return when {
            rawAction.startsWith("http://", ignoreCase = true) || rawAction.startsWith("https://", ignoreCase = true) -> {
                if (rawAction.contains("?")) "$rawAction&$inputName={title}" else "$rawAction?$inputName={title}"
            }
            rawAction.isNotBlank() && cleanOrigin.isNotBlank() -> {
                val cleanPath = if (rawAction.startsWith("/")) rawAction else "/$rawAction"
                if (cleanPath == "/") "$cleanOrigin/?$inputName={title}" else "$cleanOrigin$cleanPath?$inputName={title}"
            }
            cleanOrigin.isNotBlank() -> "$cleanOrigin/search?query={title}"
            else -> "/search?query={title}"
        }
    }

    private fun extractHostName(url: String): String {
        val base = extractBaseHost(url).removePrefix("http://").removePrefix("https://").substringBefore(':')
        return base.removePrefix("www.").removePrefix("m.")
    }

    private suspend fun testSourceConnectivity(source: DiscoveredSource): DiscoveredSource {
        val pingUrl = extractBaseHost(source.urlTemplate)
        if (pingUrl.isBlank()) {
            return source.copy(isAlive = true, latencyMs = 150L)
        }

        return try {
            val (response, duration) =
                measureTimedValue {
                    client.get(pingUrl) {
                        timeout {
                            requestTimeoutMillis = 2500
                            connectTimeoutMillis = 2000
                        }
                    }
                }
            val isSuccess = response.status.value in 200..399
            source.copy(
                isAlive = isSuccess,
                latencyMs = duration.inWholeMilliseconds,
            )
        } catch (_: Throwable) {
            source.copy(
                isAlive = false,
                latencyMs = 9999L,
            )
        }
    }

    private fun extractBaseHost(urlTemplate: String): String {
        return try {
            val schemeEnd = urlTemplate.indexOf("://")
            if (schemeEnd == -1) return ""
            val hostStart = schemeEnd + 3
            val pathStart = urlTemplate.indexOf('/', hostStart)
            if (pathStart == -1) {
                urlTemplate.substringBefore('?')
            } else {
                urlTemplate.substring(0, pathStart)
            }
        } catch (_: Throwable) {
            ""
        }
    }
}
