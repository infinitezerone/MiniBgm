package com.infinitezerone.minibgm.core.data.repository.resolver

import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.serialization.Serializable

/**
 * 将番剧名称映射为 Kitsu 词条 ID（Stremio Addon 体系标准动漫标识）。
 */
interface KitsuMappingResolver {
    /**
     * 根据番剧名称（原名、译名或英文名）查询 Kitsu ID。
     * 返回对应条目的纯数字 ID 字符串（如 "46474"），未找到或网络异常返回 null。
     */
    suspend fun resolveKitsuId(title: String): String?
}

@Serializable
internal data class KitsuSearchAttributes(
    val canonicalTitle: String = "",
    val titles: Map<String, String?> = emptyMap(),
    val subtype: String = "",
    val episodeCount: Int? = null,
    val startDate: String? = null,
)

@Serializable
internal data class KitsuSearchData(
    val id: String = "",
    val type: String = "",
    val attributes: KitsuSearchAttributes? = null,
)

@Serializable
internal data class KitsuSearchResponse(
    val data: List<KitsuSearchData> = emptyList(),
)

class KitsuMappingResolverImpl(
    private val pageFetchService: PageFetchService,
) : KitsuMappingResolver {
    private val memoryCache = mutableMapOf<String, String>()

    override suspend fun resolveKitsuId(title: String): String? {
        val clean = title.trim()
        if (clean.isBlank()) return null
        val cacheKey = clean.lowercase()
        memoryCache[cacheKey]?.let { return it }

        val encodedTitle = PlaybackSourceRule.encodeParam(clean)
        val url = "https://kitsu.io/api/edge/anime?filter%5Btext%5D=$encodedTitle&page%5Blimit%5D=5"
        val fetched =
            pageFetchService.fetchHtml(
                url = url,
                requestHeaders =
                    mapOf(
                        "Accept" to "application/vnd.api+json",
                        "User-Agent" to "MiniBgm/1.0",
                    ),
            ) ?: return null

        val response =
            runCatching {
                BgmHttpClient.jsonConfig.decodeFromString<KitsuSearchResponse>(fetched.html)
            }.getOrNull() ?: return null

        val items = response.data
        if (items.isEmpty()) return null

        // 优先完全匹配 canonicalTitle 或 titles 中任意语言别名（日文、中文、英文）
        val exact =
            items.firstOrNull { item ->
                val attrs = item.attributes ?: return@firstOrNull false
                if (attrs.canonicalTitle.equals(clean, ignoreCase = true)) return@firstOrNull true
                attrs.titles.values.any { it?.equals(clean, ignoreCase = true) == true }
            }

        val chosen = exact ?: items.first()
        val id = chosen.id.trim()
        if (id.isBlank()) return null
        memoryCache[cacheKey] = id
        return id
    }
}
