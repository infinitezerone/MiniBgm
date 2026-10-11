package com.infinitezerone.minibgm.core.data.repository.resolver

import com.infinitezerone.minibgm.core.database.dao.AniListMappingDao
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.SiteLink
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.serialization.Serializable

/** kitsu 站点在 bangumi-data sites 桥里的识别口径：site key 与链接宿主双信号 */
private const val KITSU_SITE = "kitsu"
private const val KITSU_HOST = "kitsu.io"

/**
 * 将番剧名称映射为 Kitsu 词条 ID（Stremio Addon 体系标准动漫标识）。
 */
interface KitsuMappingResolver {
    /**
     * 根据番剧名称（原名、译名或英文名）查询 Kitsu ID。
     * 映射优先级：本地 bangumi-data sites 桥（经 [subjectId] 确定性映射）> 标题搜索（只采信精确匹配）。
     * 返回对应条目的纯数字 ID 字符串（如 "46474"），未找到或网络异常返回 null。
     */
    suspend fun resolveKitsuId(
        title: String,
        subjectId: Long = 0L,
    ): String?
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
    private val anilistMappingDao: AniListMappingDao? = null,
) : KitsuMappingResolver {
    private val memoryCache = mutableMapOf<String, String>()

    override suspend fun resolveKitsuId(
        title: String,
        subjectId: Long,
    ): String? {
        val clean = title.trim()

        // 一级：本地 sites 桥。bgmId → kitsu 是确定性映射，标题搜索只是退路——
        // 同名番/译名差异时标题搜索会猜错或猜不到，桥上有的话直接采信
        if (subjectId > 0L) {
            val bridgeCacheKey = "bgm:$subjectId"
            memoryCache[bridgeCacheKey]?.let { return it }
            // 桥上没有（DB 异常或快照未带 kitsu 站点）不缓存负结果，留给标题搜索继续
            val bridged = runCatching { resolveFromSitesBridge(subjectId) }.getOrNull()
            if (!bridged.isNullOrBlank()) {
                memoryCache[bridgeCacheKey] = bridged
                return bridged
            }
        }

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

        // 只采信完全匹配 canonicalTitle 或 titles 中任意语言别名（日文、中文、英文）的条目。
        // 搜索排名不代表正确性：无精确匹配就取首条会静默返回别的番的 ID——
        // 那比"没有候选"更糟（调用方会拿它拼出一个合法但指向错误番剧的 stream 地址）
        val exact =
            items.firstOrNull { item ->
                val attrs = item.attributes ?: return@firstOrNull false
                if (attrs.canonicalTitle.equals(clean, ignoreCase = true)) return@firstOrNull true
                attrs.titles.values.any { it?.equals(clean, ignoreCase = true) == true }
            } ?: return null

        val id = exact.id.trim()
        if (id.isBlank()) return null
        memoryCache[cacheKey] = id
        return id
    }

    /** 从本地映射缓存行的 sitesJson 里提取 kitsu 链接对应的纯数字 ID */
    private suspend fun resolveFromSitesBridge(subjectId: Long): String? {
        val mappings = anilistMappingDao?.getMappingsByBgmId(subjectId).orEmpty()
        for (mapping in mappings) {
            val links =
                runCatching {
                    BgmHttpClient.jsonConfig.decodeFromString<List<SiteLink>>(mapping.sitesJson)
                }.getOrNull() ?: continue
            val kitsuLink =
                links.firstOrNull {
                    it.siteName.equals(KITSU_SITE, ignoreCase = true) || it.playUrl.contains(KITSU_HOST, ignoreCase = true)
                } ?: continue
            val id = kitsuIdFromUrl(kitsuLink.playUrl)
            if (!id.isNullOrBlank()) return id
        }
        return null
    }

    private fun kitsuIdFromUrl(url: String): String? {
        val tail =
            url
                .substringBefore('?')
                .substringBefore('#')
                .trimEnd('/')
                .substringAfterLast('/')
        return tail.takeIf { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }
    }
}
