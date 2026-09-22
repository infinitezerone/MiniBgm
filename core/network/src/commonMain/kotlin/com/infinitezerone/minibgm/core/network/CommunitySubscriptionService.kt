package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.model.DiscoveredSource
import com.infinitezerone.minibgm.core.model.SubscriptionValidationReport
import com.infinitezerone.minibgm.core.model.TvBoxConfig
import com.infinitezerone.minibgm.core.model.TvBoxSiteConversion
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.measureTimedValue

/**
 * 一次解析的产出：可用规则 + 被跳过的条目分类。
 *
 * 跳过必须分类上报，调用方才能对用户说清"收下几条、跳过几条、为什么"。
 */
internal data class ParsedSourceBatch(
    val sources: List<DiscoveredSource> = emptyList(),
    val skippedUnsupported: Int = 0,
    val skippedMalformed: Int = 0,
)

/**
 * 社区开源订阅响应实体
 */
@Serializable
data class CommunitySubscriptionPackage(
    val version: Int = 1,
    val description: String = "",
    val sources: List<DiscoveredSource> = emptyList(),
)

/**
 * 社区订阅解析与端侧测速服务。
 *
 * 只处理「调用方明确给出」的目标（订阅地址 / 单站地址 / 规则 JSON）——
 * 没有任何内置站点清单，也不代客户端检索社区。目录属社区、机制属 App。
 */
interface CommunitySubscriptionService {
    /**
     * 对指定订阅地址或规则 JSON 文本进行拉取/解析，并对其中的规则进行端侧并发连通性与测速探活。
     */
    suspend fun validateAndTestSubscription(target: String): SubscriptionValidationReport
}

class CommunitySubscriptionServiceImpl(
    private val client: HttpClient,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        },
) : CommunitySubscriptionService {
    override suspend fun validateAndTestSubscription(target: String): SubscriptionValidationReport {
        val trimmed = target.trim()
        if (trimmed.isBlank()) {
            return SubscriptionValidationReport(
                isHealthy = false,
                subscriptionUrl = target,
                errorMessage = "订阅地址或规则内容为空",
            )
        }

        val batch = resolveSources(trimmed)
        val rawSources = batch.sources
        if (rawSources.isEmpty()) {
            val skipped = batch.skippedUnsupported + batch.skippedMalformed
            return SubscriptionValidationReport(
                isHealthy = false,
                subscriptionUrl = trimmed,
                errorMessage =
                    if (skipped > 0) {
                        "订阅里有 $skipped 个站点但均无法使用：" +
                            "${batch.skippedUnsupported} 个为爬虫/扩展源（本应用不支持），" +
                            "${batch.skippedMalformed} 个条目信息不完整"
                    } else {
                        "未解析到有效播放源规则，请检查网络连接或规则 JSON 格式"
                    },
                skippedUnsupportedSites = batch.skippedUnsupported,
                skippedMalformedSites = batch.skippedMalformed,
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
            skippedUnsupportedSites = batch.skippedUnsupported,
            skippedMalformedSites = batch.skippedMalformed,
        )
    }

    private suspend fun resolveSources(target: String): ParsedSourceBatch {
        if (target.startsWith("[") || target.startsWith("{")) {
            return parseSourcesFromText(target)
        }
        return fetchSourcesFromUrl(target)
    }

    private fun parseSourcesFromText(
        rawText: String,
        pageUrl: String = "",
    ): ParsedSourceBatch {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return ParsedSourceBatch()

        // 1. 直接解析 MiniBgm 规则数组
        if (trimmed.startsWith("[")) {
            val directList = runCatching { json.decodeFromString<List<DiscoveredSource>>(trimmed) }.getOrNull()
            if (!directList.isNullOrEmpty()) return ParsedSourceBatch(sources = directList)
        }

        // 2. 解析 MiniBgm 标准订阅包
        val pkg = runCatching { json.decodeFromString<CommunitySubscriptionPackage>(trimmed) }.getOrNull()
        if (pkg != null && pkg.sources.isNotEmpty()) {
            return ParsedSourceBatch(sources = pkg.sources)
        }

        // 3. 解析 TVBox 标准配置包 {"sites": [...]}：逐条分类，跳过数必须能带回调用方
        val tvBox = runCatching { json.decodeFromString<TvBoxConfig>(trimmed) }.getOrNull()
        if (tvBox != null && tvBox.sites.isNotEmpty()) {
            val conversions = tvBox.sites.map { it.convert() }
            // 即使一条都转换不出来也要在此返回：落到下面的 HTML 兜底会把跳过原因吞掉，
            // 调用方就只能笼统说"没解析到"，分不清是不支持还是条目残缺
            return ParsedSourceBatch(
                sources = conversions.filterIsInstance<TvBoxSiteConversion.Supported>().map { it.source },
                skippedUnsupported = conversions.count { it is TvBoxSiteConversion.Unsupported },
                skippedMalformed = conversions.count { it is TvBoxSiteConversion.Malformed },
            )
        }

        // 4. 纯 HTML 文本兜底提取（单站搜索模板，属页面规则）
        extractSourceFromHtml(trimmed, pageUrl)?.let { return ParsedSourceBatch(sources = listOf(it)) }

        return ParsedSourceBatch()
    }

    private suspend fun fetchSourcesFromUrl(url: String): ParsedSourceBatch {
        return try {
            val response: HttpResponse =
                client.get(url) {
                    timeout {
                        requestTimeoutMillis = 6000
                        connectTimeoutMillis = 3000
                    }
                }
            if (!response.status.isSuccess()) return ParsedSourceBatch()

            val rawText: String = response.body()
            parseSourcesFromText(rawText, url)
        } catch (_: Throwable) {
            ParsedSourceBatch()
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
