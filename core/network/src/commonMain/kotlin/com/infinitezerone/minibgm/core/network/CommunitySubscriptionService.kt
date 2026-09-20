package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.model.DiscoveredSource
import com.infinitezerone.minibgm.core.model.DiscoveredSubscriptionCandidate
import com.infinitezerone.minibgm.core.model.SubscriptionValidationReport
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
    suspend fun searchSubscriptions(keywords: String = "minibgm-rules"): List<DiscoveredSubscriptionCandidate>

    /**
     * 对指定订阅地址进行拉取、格式校验，并对其中的规则进行并发连通性与测速探活。
     */
    suspend fun validateAndTestSubscription(url: String): SubscriptionValidationReport

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
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val report = validateAndTestSubscription(trimmed)
            return if (report.isHealthy) {
                listOf(
                    DiscoveredSubscriptionCandidate(
                        name = "自定义规则订阅",
                        subscriptionUrl = trimmed,
                        description = "来自指定链接的规则订阅",
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

        val query = trimmed.ifBlank { "minibgm-rules" }
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

    override suspend fun validateAndTestSubscription(url: String): SubscriptionValidationReport {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            return SubscriptionValidationReport(
                isHealthy = false,
                subscriptionUrl = url,
                errorMessage = "订阅地址为空",
            )
        }

        val rawSources = fetchSourcesFromUrl(trimmedUrl)
        if (rawSources.isEmpty()) {
            return SubscriptionValidationReport(
                isHealthy = false,
                subscriptionUrl = trimmedUrl,
                errorMessage = "无法从该地址获取到有效规则，请检查网络或 JSON 格式",
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
            subscriptionUrl = trimmedUrl,
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

        val candidates = searchSubscriptions("minibgm-rules")
        val best = candidates.maxByOrNull { it.aliveCount } ?: return emptyList()
        return validateAndTestSubscription(best.subscriptionUrl).sources
    }

    private suspend fun inspectRepoCandidate(item: GitHubRepoItem): DiscoveredSubscriptionCandidate? {
        val candidateUrls =
            listOf(
                "https://raw.githubusercontent.com/${item.full_name}/${item.default_branch}/anime-rules.json",
                "https://raw.githubusercontent.com/${item.full_name}/${item.default_branch}/rules.json",
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
            val trimmed = rawText.trim()
            if (trimmed.startsWith("[")) {
                json.decodeFromString<List<DiscoveredSource>>(trimmed)
            } else {
                json.decodeFromString<CommunitySubscriptionPackage>(trimmed).sources
            }
        } catch (_: Throwable) {
            emptyList()
        }
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
