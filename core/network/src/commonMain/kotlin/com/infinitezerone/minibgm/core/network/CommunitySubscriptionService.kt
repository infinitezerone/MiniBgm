package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.model.DiscoveredSource
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
 * 社区开源订阅响应实体
 */
@Serializable
data class CommunitySubscriptionPackage(
    val version: Int = 1,
    val description: String = "",
    val sources: List<DiscoveredSource> = emptyList(),
)

/**
 * 社区开源播放源发现与测速服务接口
 */
interface CommunitySubscriptionService {
    /**
     * 发现并测速社区开源播放源规则，按响应延迟由低到高排序。
     *
     * @param customSubscriptionUrl 用户可选的自定义远程订阅 JSON 地址
     */
    suspend fun fetchAndTestCommunitySources(customSubscriptionUrl: String? = null): List<DiscoveredSource>
}

class CommunitySubscriptionServiceImpl(
    private val client: HttpClient,
    private val defaultSubscriptionUrls: List<String> = DEFAULT_SUBSCRIPTION_ENDPOINTS,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        },
) : CommunitySubscriptionService {
    override suspend fun fetchAndTestCommunitySources(customSubscriptionUrl: String?): List<DiscoveredSource> {
        val candidates =
            if (!customSubscriptionUrl.isNullOrBlank()) {
                fetchFromUrl(customSubscriptionUrl)
            } else {
                fetchFromDefaultEndpoints()
            }

        if (candidates.isEmpty()) {
            return emptyList()
        }

        // 并发进行端侧测速探活
        return coroutineScope {
            val testResults =
                candidates
                    .map { source ->
                        async {
                            testSourceConnectivity(source)
                        }
                    }.awaitAll()

            // 优先按可用性排序（可用的排在前面），可用的按延迟升序排列
            testResults.sortedWith(
                compareByDescending<DiscoveredSource> { it.isAlive }
                    .thenBy { it.latencyMs },
            )
        }
    }

    private suspend fun fetchFromDefaultEndpoints(): List<DiscoveredSource> {
        for (url in defaultSubscriptionUrls) {
            try {
                val list = fetchFromUrl(url)
                if (list.isNotEmpty()) return list
            } catch (_: Throwable) {
                // 忽略单个节点异常，轮询下一个 CDN
            }
        }
        return emptyList()
    }

    private suspend fun fetchFromUrl(url: String): List<DiscoveredSource> {
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
        return runCatching {
            if (trimmed.startsWith("[")) {
                json.decodeFromString<List<DiscoveredSource>>(trimmed)
            } else {
                json.decodeFromString<CommunitySubscriptionPackage>(trimmed).sources
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun testSourceConnectivity(source: DiscoveredSource): DiscoveredSource {
        // 从 urlTemplate 提取主机根地址进行探活测试
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
            // 连接失败或超时
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

    companion object {
        /**
         * 社区公开二次元开源规则订阅托管源（多 CDN 容灾镜像）
         */
        val DEFAULT_SUBSCRIPTION_ENDPOINTS =
            listOf(
                "https://fastly.jsdelivr.net/gh/infinitezerone/BgmPlus-rules@main/anime-rules.json",
                "https://cdn.jsdelivr.net/gh/infinitezerone/BgmPlus-rules@main/anime-rules.json",
                "https://raw.githubusercontent.com/infinitezerone/BgmPlus-rules/main/anime-rules.json",
            )
    }
}
