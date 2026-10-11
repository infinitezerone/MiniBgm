package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.repository.resolver.DanmakuParser
import com.infinitezerone.minibgm.core.model.DanmakuConfig
import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.PageFetchService
import com.infinitezerone.minibgm.core.network.sha256
import kotlinx.coroutines.flow.first
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
    private val settingsRepository: SettingsRepository? = null,
) : DanmakuRepository {
    private val logger = bgmLogger("Bgm/Danmaku")
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
        if (cleanTitle.isBlank() || episodeSort <= 0f) {
            logger.w { "忽略无效弹幕请求: title='$title', episodeSort=$episodeSort" }
            return emptyList()
        }

        val config = runCatching { settingsRepository?.danmakuConfig?.first() }.getOrNull()
        val effectiveAppId = appId?.trim()?.ifBlank { null } ?: config?.appId?.trim()?.ifBlank { null }
        val effectiveAppSecret = appSecret?.trim()?.ifBlank { null } ?: config?.appSecret?.trim()?.ifBlank { null }
        val rawEndpoint =
            apiEndpoint?.trim()?.ifBlank { null }
                ?: config?.effectiveEndpoint
                ?: DanmakuConfig.DEFAULT_ENDPOINT
        val endpoint = rawEndpoint.removeSuffix("/")

        val epInt = episodeSort.toInt()
        val cacheKey = "${endpoint}_${cleanTitle.lowercase()}_$epInt"
        memoryCache[cacheKey]?.let {
            logger.d { "命中内存缓存弹幕: key=$cacheKey, count=${it.size}" }
            return it
        }

        val hasAuth = !effectiveAppId.isNullOrBlank() && !effectiveAppSecret.isNullOrBlank()
        logger.i {
            "开始拉取弹幕: anime='$cleanTitle', ep=$epInt, endpoint=$endpoint, " +
                "鉴权状态=${if (hasAuth) "已配置签名(AppId=$effectiveAppId)" else "免签模式/未配置"}"
        }

        val searchPath = "/api/v2/search/episodes"
        val encodedTitle = PlaybackSourceRule.encodeParam(cleanTitle)
        val searchUrl = "$endpoint$searchPath?anime=$encodedTitle&episode=$epInt"

        val searchHeaders = buildAuthHeaders(effectiveAppId, effectiveAppSecret, searchPath)
        val searchPage = pageFetchService.fetchHtml(searchUrl, searchHeaders)
        if (searchPage == null) {
            logger.w { "搜索分集接口失败或不可达: url=$searchUrl" }
            return emptyList()
        }

        if (searchPage.html.contains("Missing Authentication Headers", ignoreCase = true) ||
            searchPage.html.contains("403 Forbidden", ignoreCase = true)
        ) {
            logger.w {
                "弹弹play官方拦截 (403 Forbidden): 缺少认证请求头。如需直连官方请在「设置 -> 弹幕设置」配置 AppId/AppSecret，" +
                    "或使用免签自建/代理端点。"
            }
            return emptyList()
        }

        val searchResponse =
            runCatching {
                BgmHttpClient.jsonConfig.decodeFromString<DandanSearchEpisodesResponse>(searchPage.html)
            }.onFailure {
                logger.w { "解析分集搜索响应失败: ${it.message}, body=${searchPage.html.take(200)}" }
            }.getOrNull() ?: return emptyList()

        val matchingAnime = searchResponse.animes.firstOrNull { it.episodes.isNotEmpty() }
        val episodeId = matchingAnime?.episodes?.firstOrNull()?.episodeId
        if (episodeId == null || episodeId <= 0L) {
            logger.w { "未在弹幕库中找到匹配剧集: anime='$cleanTitle', ep=$epInt (返回 animeCount=${searchResponse.animes.size})" }
            return emptyList()
        }
        logger.i { "成功匹配弹弹play剧集: animeId=${matchingAnime.animeId} (${matchingAnime.animeTitle}), episodeId=$episodeId" }

        val commentPath = "/api/v2/comment/$episodeId"
        val commentUrl = "$endpoint$commentPath?withRelated=true"
        val commentHeaders = buildAuthHeaders(effectiveAppId, effectiveAppSecret, commentPath)
        val commentPage = pageFetchService.fetchHtml(commentUrl, commentHeaders)
        if (commentPage == null) {
            logger.w { "拉取弹幕详情网络失败: url=$commentUrl" }
            return emptyList()
        }

        val items = DanmakuParser.parseDandanJson(commentPage.html)
        logger.i { "成功获取并解析弹幕: ${items.size} 条 (anime='$cleanTitle', ep=$epInt, episodeId=$episodeId)" }
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
