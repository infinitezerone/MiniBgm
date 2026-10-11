package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver
import com.infinitezerone.minibgm.core.data.repository.resolver.MikanRssParser
import com.infinitezerone.minibgm.core.model.MikanTorrentResource
import com.infinitezerone.minibgm.core.network.PageFetchService

interface MikanRepository {
    /**
     * 根据关键字（番剧名）与可选的蜜柑 ID 或集数检索原画字幕组 BT 种子与磁力资源。
     *
     * 遵循 Fail-Open 架构原则：
     * 网络超时、格式异常等故障返回空列表，绝不向 UI 层上抛未捕获异常。
     * 具备内存级轻量缓存，避免同一番剧短时间内重复发起抓取。
     */
    suspend fun getTorrentResources(
        title: String,
        mikanId: String? = null,
        episodeSort: Float? = null,
    ): List<MikanTorrentResource>
}

class MikanRepositoryImpl(
    private val pageFetchService: PageFetchService,
) : MikanRepository {
    private val memoryCache = mutableMapOf<String, List<MikanTorrentResource>>()

    override suspend fun getTorrentResources(
        title: String,
        mikanId: String?,
        episodeSort: Float?,
    ): List<MikanTorrentResource> {
        val cleanTitle = title.trim()
        val cleanMikanId = mikanId?.trim().orEmpty()
        if (cleanTitle.isBlank() && cleanMikanId.isBlank()) return emptyList()

        val cacheKey = "${cleanMikanId}_${cleanTitle.lowercase()}"
        val allResources =
            memoryCache[cacheKey] ?: run {
                val fetched = fetchTorrents(cleanTitle, cleanMikanId.takeIf { it.isNotBlank() })
                if (fetched.isNotEmpty()) {
                    memoryCache[cacheKey] = fetched
                }
                fetched
            }

        if (episodeSort == null) return allResources

        // 按集数过滤/加权：匹配当前集数的排前面，全季合集紧随其后，其余排除
        val targetEp = episodeSort
        val matched = allResources.filter { it.episodeSort == targetEp }
        val batches = allResources.filter { it.episodeSort == null }
        return matched + batches
    }

    private suspend fun fetchTorrents(
        title: String,
        mikanId: String?,
    ): List<MikanTorrentResource> {
        // 策略 1：精准 Mikan ID 直达 RSS
        if (!mikanId.isNullOrBlank()) {
            val rssUrl = "https://mikanani.me/RSS/Bangumi?bangumiId=$mikanId"
            val page = pageFetchService.fetchHtml(rssUrl)
            if (page != null && page.html.isNotBlank()) {
                val parsed = MikanRssParser.parse(page.html)
                if (parsed.isNotEmpty()) return parsed
            }
        }

        // 策略 2：番剧全名搜索 RSS
        if (title.isNotBlank()) {
            val searchUrl = "https://mikanani.me/RSS/Search?searchstr=${StreamingIntentResolver.encodeQueryParameter(title)}"
            val page = pageFetchService.fetchHtml(searchUrl)
            if (page != null && page.html.isNotBlank()) {
                val parsed = MikanRssParser.parse(page.html)
                if (parsed.isNotEmpty()) return parsed
            }

            // 策略 3：去除季数后缀后简短搜索（例如 "月光下的异世界之旅 第二季" -> "月光下的异世界之旅"）
            val simplifiedTitle = simplifyTitle(title)
            if (simplifiedTitle != title && simplifiedTitle.isNotBlank()) {
                val fallbackUrl =
                    "https://mikanani.me/RSS/Search?searchstr=${StreamingIntentResolver.encodeQueryParameter(simplifiedTitle)}"
                val fallbackPage = pageFetchService.fetchHtml(fallbackUrl)
                if (fallbackPage != null && fallbackPage.html.isNotBlank()) {
                    val fallbackParsed = MikanRssParser.parse(fallbackPage.html)
                    if (fallbackParsed.isNotEmpty()) return fallbackParsed
                }
            }
        }

        return emptyList()
    }

    private fun simplifyTitle(title: String): String =
        title
            .replace(Regex("""第[一二三四五六七八九十0-9]+[季期幕]"""), "")
            .replace(Regex("""(?i)\b(?:season|s)\s*[0-9]{1,2}\b"""), "")
            .replace(Regex("""(?i)\bpart\s*[0-9]{1,2}\b"""), "")
            .replace(Regex("""[\s_\-][2-9](?:\s|$)"""), "")
            .trim()
}
