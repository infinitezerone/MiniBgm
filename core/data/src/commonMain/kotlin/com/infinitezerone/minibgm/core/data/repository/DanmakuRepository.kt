package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.data.repository.resolver.DanmakuParser
import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.PageFetchService
import com.infinitezerone.minibgm.core.network.sha256
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface DanmakuRepository {
    /**
     * 根据番剧名称与分集号获取弹弹play聚合弹幕。
     * 支持传入自定义或配置的 appId/appSecret 进行官方签名验证；若未传入则尝试无签名/兼容端点。
     * Fail-Open 架构：网络故障或找不到弹幕时返回空列表，绝不抛出未捕获异常。
     */
    suspend fun getDanmaku(
        title: String,
        episodeSort: Float,
        appId: String? = null,
        appSecret: String? = null,
        apiEndpoint: String? = null,
    ): List<DanmakuItem>
}

@Serializable
internal data class DandanEpisodeItem(
    val episodeId: Long = 0L,
    val episodeTitle: String = "",
)

@Serializable
internal data class DandanAnimeItem(
    val animeId: Long = 0L,
    val animeTitle: String = "",
    val episodes: List<DandanEpisodeItem> = emptyList(),
)

@Serializable
internal data class DandanSearchEpisodesResponse(
    val hasMore: Boolean = false,
    val animes: List<DandanAnimeItem> = emptyList(),
)

class DanmakuRepositoryImpl(
    private val pageFetchService: PageFetchService,
) : DanmakuRepository {
    private val memoryCache = mutableMapOf<String, List<DanmakuItem>>()

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun getDanmaku(
        title: String,
        episodeSort: Float,
        appId: String?,
        appSecret: String?,
        apiEndpoint: String?,
    ): List<DanmakuItem> {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank() || episodeSort <= 0f) return emptyList()

        val endpoint = (apiEndpoint?.trim()?.ifBlank { null } ?: "https://api.dandanplay.net").removeSuffix("/")
        val epInt = episodeSort.toInt()
        val cacheKey = "${endpoint}_${cleanTitle.lowercase()}_$epInt"
        memoryCache[cacheKey]?.let { return it }

        val searchPath = "/api/v2/search/episodes"
        val encodedTitle = PlaybackSourceRule.encodeParam(cleanTitle)
        val searchUrl = "$endpoint$searchPath?anime=$encodedTitle&episode=$epInt"

        val searchHeaders = buildAuthHeaders(appId, appSecret, searchPath)
        val searchPage = pageFetchService.fetchHtml(searchUrl, searchHeaders) ?: return emptyList()

        val searchResponse =
            runCatching {
                BgmHttpClient.jsonConfig.decodeFromString<DandanSearchEpisodesResponse>(searchPage.html)
            }.getOrNull() ?: return emptyList()

        val episodeId =
            searchResponse.animes
                .flatMap { it.episodes }
                .firstOrNull()
                ?.episodeId ?: return emptyList()

        val commentPath = "/api/v2/comment/$episodeId"
        val commentUrl = "$endpoint$commentPath?withRelated=true"
        val commentHeaders = buildAuthHeaders(appId, appSecret, commentPath)
        val commentPage = pageFetchService.fetchHtml(commentUrl, commentHeaders) ?: return emptyList()

        val items = DanmakuParser.parseDandanJson(commentPage.html)
        if (items.isNotEmpty()) {
            memoryCache[cacheKey] = items
        }
        return items
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun buildAuthHeaders(
        appId: String?,
        appSecret: String?,
        path: String,
    ): Map<String, String> {
        val id = appId?.trim().orEmpty()
        val secret = appSecret?.trim().orEmpty()
        val baseHeaders =
            mutableMapOf(
                "Accept" to "application/json",
                "User-Agent" to "MiniBgm/1.0",
            )
        if (id.isNotBlank() && secret.isNotBlank()) {
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val raw = "$id$timestamp$path$secret"
            val signature = Base64.Default.encode(sha256(raw.encodeToByteArray()))
            baseHeaders["X-AppId"] = id
            baseHeaders["X-Timestamp"] = timestamp
            baseHeaders["X-Signature"] = signature
        }
        return baseHeaders
    }
}
