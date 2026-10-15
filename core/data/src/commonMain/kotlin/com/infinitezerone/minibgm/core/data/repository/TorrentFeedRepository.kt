package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.repository.resolver.TorrentRssParser
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.TorrentMediaResource
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
     *
     * [alternativeTitles] 是别名回退检索的输入：站点常按原名/别名索引，主标题检索为空时
     * 必须按声明顺序逐个回退，首个非空结果即返回（实现体内真实读取，严禁只留签名）。
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

    /** 详情页与分集页会并发检索同一 Feed，裸 `MutableMap` 的并发结构修改会损坏桶链。 */
    private val cacheMutex = Mutex()

    /** 插入序有界缓存：键含规则 ID 与解析后 URL，无上限时每次改关键词都会永久驻留。 */
    private val memoryCache = LinkedHashMap<String, List<TorrentMediaResource>>()

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

        val epStr =
            episodeSort?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }.orEmpty()

        // 主标题优先，其后是按声明顺序去重后的别名；标题为空时保留一个空查询，
        // 兼容不依赖 {title} 占位符的固定 Feed 规则。
        val queryTitles =
            (listOf(cleanTitle) + alternativeTitles)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .ifEmpty { listOf("") }

        // 别名回退采用「首个非空即返回」而非结果集合并：主标题命中时零额外请求，
        // 只有主标题确实检索为空（站点按原名/别名索引）时才多一次往返。
        val triedUrls = mutableSetOf<String>()
        for (queryTitle in queryTitles) {
            val targetUrl =
                activeRule.resolveUrl(title = queryTitle, ep = epStr, subjectId = subjectId)
            // 规则模板不含 {title} 时所有候选会解析成同一个 URL，去重避免重复拉取同一 Feed。
            if (!triedUrls.add(targetUrl)) continue

            val resources = fetchFeed(targetUrl, activeRule)
            if (resources.isNotEmpty()) {
                return filterAndSort(resources, episodeSort)
            }
        }
        return emptyList()
    }

    /** 读取单个 Feed：命中缓存直接返回，否则拉取 + 解析，仅缓存非空结果。 */
    private suspend fun fetchFeed(
        targetUrl: String,
        rule: PlaybackSourceRule,
    ): List<TorrentMediaResource> {
        val cacheKey = "feed:${rule.id}:$targetUrl"
        readCache(cacheKey)?.let { return it }

        val host =
            targetUrl
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore("/")
                .substringBefore(":")

        val fetched = pageFetchService.fetchHtml(targetUrl, rule.headers)
        val xmlContent = fetched?.html.orEmpty()
        if (xmlContent.isBlank()) {
            logger.d { "Empty response for torrent feed: $targetUrl" }
            return emptyList()
        }

        val parsed = TorrentRssParser.parse(xmlContent, fallbackHost = host)
        if (parsed.isNotEmpty()) {
            writeCache(cacheKey, parsed)
        }
        return parsed
    }

    private suspend fun readCache(key: String): List<TorrentMediaResource>? = cacheMutex.withLock { memoryCache[key] }

    private suspend fun writeCache(
        key: String,
        value: List<TorrentMediaResource>,
    ) {
        cacheMutex.withLock {
            memoryCache.remove(key)
            memoryCache[key] = value
            while (memoryCache.size > MAX_CACHE_ENTRIES) {
                val oldestKey = memoryCache.keys.firstOrNull() ?: break
                memoryCache.remove(oldestKey)
            }
        }
    }

    private fun filterAndSort(
        resources: List<TorrentMediaResource>,
        episodeSort: Float?,
    ): List<TorrentMediaResource> {
        val filtered =
            if (episodeSort != null) {
                val matched = resources.filter { it.episodeSort == episodeSort }
                // 分集未命中时只回退到合集/BD 整包项（episodeSort == null），绝不回退整份 Feed：
                // 否则请求第 5 集会看到第 1-4 集，点单文件资源还会被 Rust 侧「单一候选即选中」
                // 静默播成第 1 集，用户全程无提示。
                matched.ifEmpty { resources.filter { it.episodeSort == null } }
            } else {
                resources
            }
        return filtered.sortedWith(
            compareByDescending<TorrentMediaResource> { it.resolution == "1080P" }
                .thenByDescending { it.resolution == "4K" }
                .thenByDescending { it.contentLength },
        )
    }

    private companion object {
        /** 缓存条目上限：每个 (规则, 解析后 URL) 组合一条，按插入序淘汰最旧条目。 */
        const val MAX_CACHE_ENTRIES = 32
    }
}
