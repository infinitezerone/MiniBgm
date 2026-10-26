package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.cache.TorrentFeedDiskCache
import com.infinitezerone.minibgm.core.data.repository.resolver.TorrentRssParser
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.TorrentMediaResource
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString

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
        forceRefresh: Boolean = false,
        candidateEpisodeSorts: List<Float> = emptyList(),
    ): List<TorrentMediaResource>
}

class TorrentFeedRepositoryImpl(
    private val pageFetchService: PageFetchService,
    private val diskCache: TorrentFeedDiskCache? = null,
) : TorrentFeedRepository {
    private val logger = bgmLogger("Bgm/TorrentFeed")

    private sealed interface FeedFetchResult {
        data class Success(
            val resources: List<TorrentMediaResource>,
        ) : FeedFetchResult

        data object NetworkError : FeedFetchResult
    }

    /** 详情页与分集页会并发检索同一 Feed，裸 `MutableMap` 的并发结构修改会损坏桶链。 */
    private val cacheMutex = Mutex()

    /** 插入序有界缓存：键含规则 ID 与解析后 URL，无上限时每次改关键词都会永久驻留。 */
    private val memoryCache = LinkedHashMap<String, List<TorrentMediaResource>>()

    private sealed interface SearchStepOutcome {
        data class Found(
            val resources: List<TorrentMediaResource>,
        ) : SearchStepOutcome

        data object CircuitBreak : SearchStepOutcome

        data object Continue : SearchStepOutcome
    }

    override suspend fun getTorrentResources(
        title: String,
        episodeSort: Float?,
        alternativeTitles: List<String>,
        subjectId: Long,
        rule: PlaybackSourceRule?,
        forceRefresh: Boolean,
        candidateEpisodeSorts: List<Float>,
    ): List<TorrentMediaResource> {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank() && rule == null) return emptyList()

        val activeRule = rule ?: return emptyList()
        if (!activeRule.isEnabled || activeRule.kind != PlaybackRuleKind.TORRENT) {
            return emptyList()
        }

        val effectiveCandidateSorts =
            candidateEpisodeSorts
                .filter { it > 0f }
                .distinct()
                .ifEmpty { listOfNotNull(episodeSort) }

        val primaryEpSort = episodeSort ?: effectiveCandidateSorts.firstOrNull()
        val (epStr, epPadded) = formatEpisodeQueryStrings(primaryEpSort)

        // 主标题优先，其后是按声明顺序去重后的别名；标题为空时保留一个空查询，
        // 兼容不依赖 {title} 占位符的固定 Feed 规则。
        val baseTitles =
            (listOf(cleanTitle) + alternativeTitles)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .ifEmpty { listOf("") }

        // 当用户指定了分集且规则模板不包含 {ep} 占位符时（如大多数 Mikan/DMHY/Nyaa/Bangumi.moe RSS 规则），
        // 站点 RSS 搜索默认只返回最新 20 条。若仅搜番剧名，较早的分集会被挤出第一页。
        // 因此针对每个候选标题，优先尝试合成带集数关键字的查询（如 "标题 03"、"标题 3"），
        // 最后回退到纯番剧名。
        val ruleHasEpPlaceholder = activeRule.urlTemplate.contains("{ep}")
        val triedUrls = mutableSetOf<String>()
        val titlesWithNonEmptyFeed = mutableListOf<String>()

        // 阶段 1：优先按原标题及别名直接检索（对齐别名回退契约与单次往返命中）
        for (queryTitle in baseTitles) {
            when (
                val outcome =
                    trySearchFeed(
                        activeRule = activeRule,
                        queryTitle = queryTitle,
                        epStr = epStr,
                        subjectId = subjectId,
                        triedUrls = triedUrls,
                        forceRefresh = forceRefresh,
                        candidateSorts = effectiveCandidateSorts,
                        onFeedNonEmpty = { titlesWithNonEmptyFeed.add(queryTitle) },
                    )
            ) {
                is SearchStepOutcome.Found -> return outcome.resources
                SearchStepOutcome.CircuitBreak -> return emptyList()
                SearchStepOutcome.Continue -> Unit
            }
        }

        // 阶段 2：若用户指定了分集、规则模板未包含 {ep}（如 Mikan/DMHY 等单页只返回最新 20 条的 RSS），
        // 且基础标题均未命中指定分集（说明较早的分集已被挤出第一页），
        // 则针对确实存在资源的标题追加带集数关键字定向检索（如 "标题 03"、"标题 3"）
        if (effectiveCandidateSorts.isNotEmpty() && !ruleHasEpPlaceholder && titlesWithNonEmptyFeed.isNotEmpty()) {
            val epSpecificTitles = buildEpSpecificTitles(titlesWithNonEmptyFeed, effectiveCandidateSorts)
            for (queryTitle in epSpecificTitles) {
                when (
                    val outcome =
                        trySearchFeed(
                            activeRule = activeRule,
                            queryTitle = queryTitle,
                            epStr = epStr,
                            subjectId = subjectId,
                            triedUrls = triedUrls,
                            forceRefresh = forceRefresh,
                            candidateSorts = effectiveCandidateSorts,
                        )
                ) {
                    is SearchStepOutcome.Found -> return outcome.resources
                    SearchStepOutcome.CircuitBreak -> return emptyList()
                    SearchStepOutcome.Continue -> Unit
                }
            }
        }

        return emptyList()
    }

    private fun formatEpisodeQueryStrings(episodeSort: Float?): Pair<String, String> {
        val epStr =
            episodeSort?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }.orEmpty()
        val epPadded =
            episodeSort
                ?.let {
                    if (it % 1f == 0f) {
                        val epInt = it.toInt()
                        if (epInt in 0..9) "0$epInt" else epInt.toString()
                    } else {
                        it.toString()
                    }
                }.orEmpty()
        return Pair(epStr, epPadded)
    }

    private fun buildEpSpecificTitles(
        titlesWithNonEmptyFeed: List<String>,
        candidateSorts: List<Float>,
    ): List<String> {
        val queryPairs = candidateSorts.map { formatEpisodeQueryStrings(it) }
        return titlesWithNonEmptyFeed
            .flatMap { base ->
                queryPairs.flatMap { (epStr, epPadded) ->
                    listOfNotNull(
                        "$base $epPadded",
                        if (epPadded != epStr) "$base $epStr" else null,
                    )
                }
            }.distinct()
    }

    private suspend fun trySearchFeed(
        activeRule: PlaybackSourceRule,
        queryTitle: String,
        epStr: String,
        subjectId: Long,
        triedUrls: MutableSet<String>,
        forceRefresh: Boolean,
        candidateSorts: List<Float>,
        onFeedNonEmpty: (() -> Unit)? = null,
    ): SearchStepOutcome {
        val targetUrl =
            activeRule.resolveUrl(title = queryTitle, ep = epStr, subjectId = subjectId)
        if (!triedUrls.add(targetUrl)) return SearchStepOutcome.Continue

        return when (val result = fetchFeed(targetUrl, activeRule, forceRefresh)) {
            is FeedFetchResult.NetworkError -> {
                logger.w { "Network failure for $targetUrl; circuit breaking for rule ${activeRule.id}" }
                SearchStepOutcome.CircuitBreak
            }
            is FeedFetchResult.Success -> {
                if (result.resources.isNotEmpty()) {
                    onFeedNonEmpty?.invoke()
                    val filtered = filterAndSort(result.resources, candidateSorts)
                    if (filtered.isNotEmpty()) {
                        SearchStepOutcome.Found(filtered)
                    } else {
                        SearchStepOutcome.Continue
                    }
                } else {
                    SearchStepOutcome.Continue
                }
            }
        }
    }

    /** 读取单个 Feed：命中缓存直接返回，否则拉取 + 解析，仅缓存非空结果。 */
    private suspend fun fetchFeed(
        targetUrl: String,
        rule: PlaybackSourceRule,
        forceRefresh: Boolean = false,
    ): FeedFetchResult {
        val cacheKey = "feed:${rule.id}:$targetUrl"
        if (!forceRefresh) {
            readCache(cacheKey)?.let { return FeedFetchResult.Success(it) }
        }

        val host =
            targetUrl
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore("/")
                .substringBefore(":")

        val fetched =
            try {
                withTimeoutOrNull(8_000L) {
                    pageFetchService.fetchHtml(targetUrl, rule.headers)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                null
            }

        if (fetched == null) {
            logger.d { "Network failure or timeout for torrent feed: $targetUrl" }
            return FeedFetchResult.NetworkError
        }

        val xmlContent = fetched.html
        if (xmlContent.isBlank()) {
            logger.d { "Empty response for torrent feed: $targetUrl" }
            writeCache(cacheKey, emptyList())
            return FeedFetchResult.Success(emptyList())
        }

        val parsed = TorrentRssParser.parse(xmlContent, fallbackHost = host)
        writeCache(cacheKey, parsed)
        return FeedFetchResult.Success(parsed)
    }

    private suspend fun readCache(key: String): List<TorrentMediaResource>? {
        cacheMutex.withLock {
            memoryCache[key]?.let { return it }
        }
        val diskJson = diskCache?.get(key) ?: return null
        val decoded =
            runCatching {
                BgmHttpClient.jsonConfig.decodeFromString<List<TorrentMediaResource>>(diskJson)
            }.getOrNull()
        if (decoded != null) {
            cacheMutex.withLock {
                memoryCache[key] = decoded
            }
            return decoded
        }
        return null
    }

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
        val targetDiskCache = diskCache
        if (targetDiskCache != null) {
            runCatching {
                val encoded = BgmHttpClient.jsonConfig.encodeToString(value)
                targetDiskCache.put(key, encoded)
            }
        }
    }

    private fun filterAndSort(
        resources: List<TorrentMediaResource>,
        candidateSorts: List<Float>,
    ): List<TorrentMediaResource> {
        val filtered =
            if (candidateSorts.isNotEmpty()) {
                val matched =
                    resources.filter { res ->
                        res.episodeSort != null && res.episodeSort in candidateSorts
                    }
                // 分集未命中时只回退到合集/BD 整包项（episodeSort == null），绝不回退整份 Feed：
                // 否则请求第 5 集会看到第 1-4 集，点单文件资源还会被 Rust 侧「单一候选即选中」
                // 静默播成第 1 集，用户全程无提示。
                val batchItems = resources.filter { it.episodeSort == null }
                matched.ifEmpty { batchItems.filter { isExplicitBatch(it.title) } }
            } else {
                resources
            }
        return filtered.sortedWith(
            compareByDescending<TorrentMediaResource> { it.resolution == "1080P" }
                .thenByDescending { it.resolution == "4K" }
                .thenByDescending { it.contentLength },
        )
    }

    private fun isExplicitBatch(title: String): Boolean {
        val batchKeywords = listOf("合集", "全集", "Fin]", "Fin)", "【Fin】", "[Fin]", "Complete")
        val batchEpRangeRegex =
            Regex(
                """(?i)(?:\[\s*(?:ep)?\s*\d{1,3}\s*[-~至到]\s*\d{1,3}\s*\]|\b(?:ep|e)\s*\d{1,3}\s*[-~至到]\s*\d{1,3}\b|0\d\s*[-~至到]\s*\d{1,3})""",
            )
        return batchKeywords.any { title.contains(it, ignoreCase = true) } ||
            batchEpRangeRegex.containsMatchIn(title)
    }

    private companion object {
        /** 缓存条目上限：每个 (规则, 解析后 URL) 组合一条，按插入序淘汰最旧条目。 */
        const val MAX_CACHE_ENTRIES = 32
    }
}
