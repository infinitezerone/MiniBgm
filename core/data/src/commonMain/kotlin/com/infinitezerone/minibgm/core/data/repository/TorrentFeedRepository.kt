package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.repository.resolver.TorrentRssParser
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.TorrentMediaResource
import com.infinitezerone.minibgm.core.network.PageFetchService

/**
 * 通用 P2P 种子 / 磁力 RSS 资源检索仓库接口。
 */
interface TorrentFeedRepository {
    /**
     * 根据番剧名称与分集序号，从指定的 P2P 种子/RSS 规则源拉取种子与磁力资源。
     *
     * 遵循 Fail-Open 架构原则：
     * - 网络超时、格式异常等故障返回空列表，绝不向 UI 层上抛未捕获异常；
     * - 支持分集过滤与排序；
     * - 规则完全由外部注入或用户配置，实现 100% 协议中立。
     */
    suspend fun getTorrentResources(
        title: String,
        episodeSort: Float? = null,
        alternativeTitles: List<String> = emptyList(),
        subjectId: Long = 0L,
        rule: PlaybackSourceRule? = null,
    ): List<TorrentMediaResource>
}

class TorrentFeedRepositoryImpl(
    private val pageFetchService: PageFetchService,
) : TorrentFeedRepository {
    private val logger = bgmLogger("Bgm/TorrentFeed")
    private val memoryCache = mutableMapOf<String, List<TorrentMediaResource>>()

    override suspend fun getTorrentResources(
        title: String,
        episodeSort: Float?,
        alternativeTitles: List<String>,
        subjectId: Long,
        rule: PlaybackSourceRule?,
    ): List<TorrentMediaResource> {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank() && rule == null) return emptyList()

        val activeRule = rule ?: return emptyList()
        if (!activeRule.isEnabled || activeRule.kind != PlaybackRuleKind.TORRENT) {
            return emptyList()
        }

        val epStr = episodeSort?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }.orEmpty()
        val targetUrl =
            activeRule.resolveUrl(
                title = cleanTitle,
                ep = epStr,
                subjectId = subjectId,
            )

        val cacheKey = "feed:${activeRule.id}:$targetUrl"
        val cached = memoryCache[cacheKey]
        if (cached != null) {
            return filterAndSort(cached, episodeSort)
        }

        val host =
            targetUrl
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore("/")
                .substringBefore(":")

        val fetched = pageFetchService.fetchHtml(targetUrl, activeRule.headers)
        val xmlContent = fetched?.html.orEmpty()
        if (xmlContent.isBlank()) {
            logger.d { "Empty response for torrent feed: $targetUrl" }
            return emptyList()
        }

        val parsed = TorrentRssParser.parse(xmlContent, fallbackHost = host)
        if (parsed.isNotEmpty()) {
            memoryCache[cacheKey] = parsed
        }
        return filterAndSort(parsed, episodeSort)
    }

    private fun filterAndSort(
        resources: List<TorrentMediaResource>,
        episodeSort: Float?,
    ): List<TorrentMediaResource> {
        val filtered =
            if (episodeSort != null) {
                val matched = resources.filter { it.episodeSort == episodeSort }
                if (matched.isNotEmpty()) matched else resources
            } else {
                resources
            }
        return filtered.sortedWith(
            compareByDescending<TorrentMediaResource> { it.resolution == "1080P" }
                .thenByDescending { it.resolution == "4K" }
                .thenByDescending { it.contentLength }
        )
    }
}
