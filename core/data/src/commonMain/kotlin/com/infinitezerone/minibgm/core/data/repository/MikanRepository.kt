package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver
import com.infinitezerone.minibgm.core.data.repository.resolver.MikanRssParser
import com.infinitezerone.minibgm.core.database.dao.AniListMappingDao
import com.infinitezerone.minibgm.core.database.entity.AniListBgmMappingEntity
import com.infinitezerone.minibgm.core.model.MikanTorrentResource
import com.infinitezerone.minibgm.core.model.SiteLink
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.PageFetchService

private const val MIKAN_SITE = "mikan"
private const val MIKAN_HOST = "mikanani.me"

interface MikanRepository {
    /**
     * 根据关键字（番剧名）、Bangumi 条目 ID 与可选的蜜柑 ID 或集数检索原画字幕组 BT 种子与磁力资源。
     *
     * 遵循 Fail-Open 架构原则：
     * - 网络超时、格式异常等故障返回空列表，绝不向 UI 层上抛未捕获异常；
     * - 优先通过本地 `bangumi-data` 映射桥（及关联前传 ID）直达 Mikan 番剧专属 RSS；
     * - 未收录新番自动通过核心词块探测 Mikan 番剧词条并校验 `bgm.tv/subject/{id}` 反向链接，成功后持久化落库，后续零搜索直达；
     * - 分集过滤支持总话数（`episodeSort`）与本季相对话数（`alternativeEpisodeSort`）双轨匹配，解决分割放送番剧话数错位。
     */
    suspend fun getTorrentResources(
        title: String,
        mikanId: String? = null,
        episodeSort: Float? = null,
        alternativeTitles: List<String> = emptyList(),
        subjectId: Long = 0L,
        relatedSubjectIds: List<Long> = emptyList(),
        alternativeEpisodeSort: Float? = null,
    ): List<MikanTorrentResource>
}

class MikanRepositoryImpl(
    private val pageFetchService: PageFetchService,
    private val anilistMappingDao: AniListMappingDao? = null,
) : MikanRepository {
    private val logger = bgmLogger("Bgm/Mikan")
    private val memoryCache = mutableMapOf<String, List<MikanTorrentResource>>()
    private val resolvedMikanIdCache = mutableMapOf<Long, String>()

    override suspend fun getTorrentResources(
        title: String,
        mikanId: String?,
        episodeSort: Float?,
        alternativeTitles: List<String>,
        subjectId: Long,
        relatedSubjectIds: List<Long>,
        alternativeEpisodeSort: Float?,
    ): List<MikanTorrentResource> {
        val startMs = TimeUtils.nowEpochMillis()
        val cleanTitle = title.trim()
        val explicitMikanId = mikanId?.trim().orEmpty()
        val cleanAltTitles =
            alternativeTitles
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.equals(cleanTitle, ignoreCase = true) }
                .distinct()

        val bridgeStartMs = TimeUtils.nowEpochMillis()
        val effectiveMikanId =
            explicitMikanId.ifBlank {
                resolveMikanIdFromLocalBridge(subjectId, relatedSubjectIds).orEmpty()
            }
        val bridgeElapsedMs = TimeUtils.nowEpochMillis() - bridgeStartMs

        if (cleanTitle.isBlank() && effectiveMikanId.isBlank() && cleanAltTitles.isEmpty()) {
            return emptyList()
        }

        logger.d {
            "[Trace:Start] subjectId=$subjectId, relatedIds=$relatedSubjectIds, title='$cleanTitle', " +
                "localMikanId='${effectiveMikanId.ifBlank { "MISS" }}'(${bridgeElapsedMs}ms), " +
                "ep=$episodeSort(alt=$alternativeEpisodeSort), altTitles=${cleanAltTitles.size}"
        }

        val cacheKey =
            if (effectiveMikanId.isNotBlank()) {
                "mikan:$effectiveMikanId"
            } else if (subjectId > 0L) {
                "bgm:${subjectId}_${cleanTitle.lowercase()}"
            } else {
                "_${cleanTitle.lowercase()}"
            }

        val cached = memoryCache[cacheKey]
        val allResources =
            if (cached != null && isCacheSufficientForEpisode(cached, episodeSort, alternativeEpisodeSort)) {
                logger.d { "[Trace:MemoryCache] HIT key='$cacheKey', total=${cached.size}" }
                cached
            } else {
                val fetched =
                    fetchTorrents(
                        title = cleanTitle,
                        mikanId = effectiveMikanId.takeIf { it.isNotBlank() },
                        alternativeTitles = cleanAltTitles,
                        subjectId = subjectId,
                        relatedSubjectIds = relatedSubjectIds,
                        targetEpisodeSort = episodeSort,
                        alternativeEpisodeSort = alternativeEpisodeSort,
                    )
                if (fetched.isNotEmpty()) {
                    val finalMikanId = resolvedMikanIdCache[subjectId] ?: effectiveMikanId
                    val finalKey = if (finalMikanId.isNotBlank()) "mikan:$finalMikanId" else cacheKey
                    memoryCache[finalKey] = fetched
                    memoryCache[cacheKey] = fetched
                }
                fetched
            }

        val totalElapsedMs = TimeUtils.nowEpochMillis() - startMs
        if (episodeSort == null && alternativeEpisodeSort == null) {
            logger.i { "[Trace:Done] mode=SUBJECT_ALL, returned=${allResources.size}, totalElapsed=${totalElapsedMs}ms" }
            return allResources
        }

        val (matched, matchMode) = matchEpisodeResourcesWithMode(allResources, episodeSort, alternativeEpisodeSort)
        val batches = allResources.filter { it.episodeSort == null }
        val filtered = (matched + batches).distinctBy { it.infoHash.ifBlank { it.title } }
        logger.i {
            "[Trace:Done] mode=$matchMode, ep=$episodeSort(alt=$alternativeEpisodeSort), " +
                "matchedSingle=${matched.size}, batches=${batches.size}, returned=${filtered.size}/${allResources.size}, " +
                "totalElapsed=${totalElapsedMs}ms"
        }
        return filtered
    }

    private fun isCacheSufficientForEpisode(
        resources: List<MikanTorrentResource>,
        episodeSort: Float?,
        alternativeEpisodeSort: Float?,
    ): Boolean {
        if (resources.isEmpty()) return false
        if (episodeSort == null && alternativeEpisodeSort == null) return true
        return matchEpisodeResourcesWithMode(resources, episodeSort, alternativeEpisodeSort).first.isNotEmpty()
    }

    /**
     * 双轨分集对齐并返回命中轨道标识（供监控日志追踪）：
     * 1. `PRIMARY_SORT`：命中总话数 [primaryEp]（分割季最常用的连续编号）；
     * 2. `ALT_EP`：主话数未命中时回退命中季内相对话数 [secondaryEp]；
     * 3. `MISS`：单集均未命中。
     */
    private fun matchEpisodeResourcesWithMode(
        allResources: List<MikanTorrentResource>,
        primaryEp: Float?,
        secondaryEp: Float?,
    ): Pair<List<MikanTorrentResource>, String> {
        if (primaryEp != null) {
            val primaryMatched = allResources.filter { it.episodeSort == primaryEp }
            if (primaryMatched.isNotEmpty()) return primaryMatched to "PRIMARY_SORT($primaryEp)"
        }
        if (secondaryEp != null && secondaryEp != primaryEp) {
            val secondaryMatched = allResources.filter { it.episodeSort == secondaryEp }
            if (secondaryMatched.isNotEmpty()) return secondaryMatched to "ALT_EP($secondaryEp)"
        }
        return emptyList<MikanTorrentResource>() to "MISS"
    }

    private suspend fun fetchTorrents(
        title: String,
        mikanId: String?,
        alternativeTitles: List<String>,
        subjectId: Long,
        relatedSubjectIds: List<Long>,
        targetEpisodeSort: Float?,
        alternativeEpisodeSort: Float?,
    ): List<MikanTorrentResource> {
        // 策略 1：精准 Mikan ID 直达 RSS（来自显式传入或本地 bangumi-data 映射桥）
        if (!mikanId.isNullOrBlank()) {
            val stepStart = TimeUtils.nowEpochMillis()
            val rssUrl = "https://mikanani.me/RSS/Bangumi?bangumiId=$mikanId"
            val parsed = fetchAndParseRss(rssUrl)
            val elapsed = TimeUtils.nowEpochMillis() - stepStart
            if (parsed.isNotEmpty()) {
                logger.i { "[Trace:Strategy1_DirectId] HIT mikanId=$mikanId, items=${parsed.size}, elapsed=${elapsed}ms" }
                return parsed
            }
            logger.w { "[Trace:Strategy1_DirectId] EMPTY mikanId=$mikanId, elapsed=${elapsed}ms" }
        }

        // 策略 1.5：对 bangumi-data 尚未收录的新番，通过核心词块探测 Mikan 番剧词条并校验 bgm.tv 反向链接，成功后持久化落库
        if (subjectId > 0L) {
            val discoveredMikanId =
                discoverAndPersistMikanId(
                    title = title,
                    alternativeTitles = alternativeTitles,
                    subjectId = subjectId,
                    relatedSubjectIds = relatedSubjectIds,
                )
            if (!discoveredMikanId.isNullOrBlank()) {
                val rssStart = TimeUtils.nowEpochMillis()
                val rssUrl = "https://mikanani.me/RSS/Bangumi?bangumiId=$discoveredMikanId"
                val parsed = fetchAndParseRss(rssUrl)
                val rssElapsed = TimeUtils.nowEpochMillis() - rssStart
                if (parsed.isNotEmpty()) {
                    logger.i {
                        "[Trace:Strategy1.5_DiscoveredRss] HIT mikanId=$discoveredMikanId, items=${parsed.size}, rssElapsed=${rssElapsed}ms"
                    }
                    return parsed
                }
            }
        }

        // 策略 2 & 3：渐进式 Token 退火联合检索
        val candidateQueries = buildProgressiveQueries(title, alternativeTitles)
        val mergedResults = LinkedHashMap<String, MikanTorrentResource>()

        for (query in candidateQueries) {
            val qStart = TimeUtils.nowEpochMillis()
            val searchUrl = "https://mikanani.me/RSS/Search?searchstr=${StreamingIntentResolver.encodeQueryParameter(query)}"
            val parsed = fetchAndParseRss(searchUrl)
            val qElapsed = TimeUtils.nowEpochMillis() - qStart
            logger.d { "[Trace:Strategy2_RssSearch] query='$query', fetched=${parsed.size}, elapsed=${qElapsed}ms" }
            if (parsed.isNotEmpty()) {
                for (item in parsed) {
                    val key = item.infoHash.ifBlank { item.title }
                    mergedResults.putIfAbsent(key, item)
                }
                val currentList = mergedResults.values.toList()
                val hasTargetEpisode =
                    isCacheSufficientForEpisode(currentList, targetEpisodeSort, alternativeEpisodeSort)
                if (hasTargetEpisode && currentList.size >= MIN_SUFFICIENT_RSS_RESULTS) {
                    logger.i { "[Trace:Strategy2_RssSearch] SUFFICIENT after query='$query', totalMerged=${currentList.size}" }
                    return currentList
                }
            }
        }

        if (mergedResults.isNotEmpty()) {
            logger.i { "[Trace:Strategy2_RssSearch] MERGED total=${mergedResults.size}" }
            return mergedResults.values.toList()
        }

        logger.w { "[Trace:Miss] All strategies exhausted (title='$title', subjectId=$subjectId)" }
        return emptyList()
    }

    /**
     * 一级：从本地 `anilist_bgm_mapping` 表的 `sitesJson` 中提取 `mikan` 站点 ID。
     * 若当前条目（如分割季第二赛段）未直接绑定，顺着 [relatedSubjectIds]（如第一赛段前传）查找共享的 Mikan 词条。
     */
    private suspend fun resolveMikanIdFromLocalBridge(
        subjectId: Long,
        relatedSubjectIds: List<Long>,
    ): String? {
        if (subjectId <= 0L && relatedSubjectIds.isEmpty()) return null
        val candidateIds = (listOf(subjectId).filter { it > 0L } + relatedSubjectIds.filter { it > 0L }).distinct()
        for (id in candidateIds) {
            resolvedMikanIdCache[id]?.let { cachedId ->
                logger.d { "[Trace:LocalBridge] Memory HIT bgmId=$id -> mikanId=$cachedId" }
                return cachedId
            }
            val mappings = runCatching { anilistMappingDao?.getMappingsByBgmId(id).orEmpty() }.getOrDefault(emptyList())
            for (mapping in mappings) {
                val links =
                    runCatching {
                        BgmHttpClient.jsonConfig.decodeFromString<List<SiteLink>>(mapping.sitesJson)
                    }.getOrNull() ?: continue
                val mikanLink =
                    links.firstOrNull {
                        it.siteName.equals(MIKAN_SITE, ignoreCase = true) ||
                            it.playUrl.contains("$MIKAN_HOST/Home/Bangumi/", ignoreCase = true)
                    } ?: continue
                val extracted = mikanIdFromUrl(mikanLink.playUrl)
                if (!extracted.isNullOrBlank()) {
                    resolvedMikanIdCache[subjectId] = extracted
                    val via = if (id == subjectId) "SELF" else "PREQUEL($id)"
                    logger.d { "[Trace:LocalBridge] DB HIT via=$via -> mikanId=$extracted" }
                    return extracted
                }
            }
        }
        return null
    }

    /**
     * 二级：当本地 `bangumi-data` 未收录时，用核心词块请求 `/Home/Search` 提取候选 `/Home/Bangumi/{id}`，
     * 并检查其详情页中的 `bgm.tv/subject/{bgmId}` 是否匹配当前条目或关联前传条目。
     * 验证通过后直接持久化写入 `anilist_bgm_mapping`，后续进入零搜索直达。
     */
    private suspend fun discoverAndPersistMikanId(
        title: String,
        alternativeTitles: List<String>,
        subjectId: Long,
        relatedSubjectIds: List<Long>,
    ): String? {
        val searchTokens = buildDiscoverySearchTerms(title, alternativeTitles)
        if (searchTokens.isEmpty()) return null

        val validBgmIds = (setOf(subjectId) + relatedSubjectIds).filter { it > 0L }.toSet()
        val checkedMikanIds = mutableSetOf<String>()

        for (term in searchTokens.take(2)) {
            val termStart = TimeUtils.nowEpochMillis()
            val searchUrl = "https://mikanani.me/Home/Search?searchstr=${StreamingIntentResolver.encodeQueryParameter(term)}"
            val searchPage = pageFetchService.fetchHtml(searchUrl) ?: continue
            val candidateMikanIds = MikanRssParser.extractBangumiIdsFromSearchHtml(searchPage.html)
            val searchElapsed = TimeUtils.nowEpochMillis() - termStart
            logger.d {
                "[Trace:DiscoverySearch] term='$term', candidates=$candidateMikanIds, elapsed=${searchElapsed}ms"
            }

            for (candidateId in candidateMikanIds.take(3)) {
                if (!checkedMikanIds.add(candidateId)) continue
                val verifyStart = TimeUtils.nowEpochMillis()
                val bangumiUrl = "https://mikanani.me/Home/Bangumi/$candidateId"
                val bangumiPage = pageFetchService.fetchHtml(bangumiUrl) ?: continue
                val linkedBgmId = MikanRssParser.extractBgmSubjectIdFromBangumiHtml(bangumiPage.html)
                val verifyElapsed = TimeUtils.nowEpochMillis() - verifyStart
                val isMatch = linkedBgmId != null && linkedBgmId in validBgmIds
                logger.d {
                    "[Trace:DiscoveryVerify] mikanId=$candidateId -> linkedBgmId=$linkedBgmId " +
                        "(expected in $validBgmIds), match=$isMatch, elapsed=${verifyElapsed}ms"
                }
                if (isMatch) {
                    resolvedMikanIdCache[subjectId] = candidateId
                    persistDiscoveredMikanId(subjectId, title, candidateId)
                    return candidateId
                }
            }
        }
        return null
    }

    private suspend fun persistDiscoveredMikanId(
        subjectId: Long,
        title: String,
        mikanId: String,
    ) {
        val dao = anilistMappingDao ?: return
        runCatching {
            val mikanSiteLink =
                SiteLink(
                    siteName = MIKAN_SITE,
                    displayName = "蜜柑计划",
                    playUrl = "https://mikanani.me/Home/Bangumi/$mikanId",
                )
            val existingList = dao.getMappingsByBgmId(subjectId)
            val nowMillis = TimeUtils.nowEpochMillis()
            if (existingList.isNotEmpty()) {
                val updated =
                    existingList.map { row ->
                        val currentLinks =
                            runCatching {
                                BgmHttpClient.jsonConfig.decodeFromString<List<SiteLink>>(row.sitesJson)
                            }.getOrDefault(emptyList())
                        val mergedLinks =
                            currentLinks.filterNot { it.siteName.equals(MIKAN_SITE, ignoreCase = true) } + mikanSiteLink
                        row.copy(
                            sitesJson = BgmHttpClient.jsonConfig.encodeToString(mergedLinks),
                            updatedAt = nowMillis,
                        )
                    }
                dao.upsertMappings(updated)
                logger.d { "[Trace:Persist] Updated ${updated.size} existing mapping rows for bgmId=$subjectId with mikanId=$mikanId" }
            } else {
                // 使用负数合成主键 (-subjectId) 存储自发现的站点桥，避免与真实正数 anilistId 冲突
                val syntheticRow =
                    AniListBgmMappingEntity(
                        anilistId = -subjectId,
                        bgmId = subjectId,
                        sitesJson = BgmHttpClient.jsonConfig.encodeToString(listOf(mikanSiteLink)),
                        title = title,
                        titleCn = title,
                        beginIso = "",
                        endIso = "",
                        monthKey = "",
                        updatedAt = nowMillis,
                    )
                dao.upsertMappings(listOf(syntheticRow))
                logger.d {
                    "[Trace:Persist] Inserted synthetic mapping row (anilistId=${-subjectId}) for bgmId=$subjectId with mikanId=$mikanId"
                }
            }
        }
    }

    private fun mikanIdFromUrl(url: String): String? {
        val tail =
            url
                .substringBefore('?')
                .substringBefore('#')
                .trimEnd('/')
                .substringAfterLast('/')
        return tail.takeIf { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }
    }

    /**
     * 提取按空格/分隔符自然退火的核心候选词序列（Progressive Token Backoff）：
     * - 仅使用完整标题与去掉最后一个修饰 Token 的核心词（前提是至少含 2 个词块），
     *   绝不退化为拿单个短词去搜 RSS/Search（否则搜“JOJO的奇妙冒险”会把第 1~6 部全混进来），
     *   也无需维护硬编码的“第X季/赛段/阶段/Stage”正则黑名单。
     */
    internal fun buildProgressiveQueries(
        title: String,
        alternativeTitles: List<String>,
    ): List<String> {
        val queries = mutableListOf<String>()
        if (title.isNotBlank()) {
            queries += title
            val prefixWithoutLast = dropLastTokenIfMultiWord(title)
            if (prefixWithoutLast != null) {
                queries += prefixWithoutLast
            }
        }

        for (alt in alternativeTitles) {
            queries += alt
            val prefixWithoutLast = dropLastTokenIfMultiWord(alt)
            if (prefixWithoutLast != null) {
                queries += prefixWithoutLast
            }
        }
        return queries.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    /**
     * 提取用于 `/Home/Search` 探测番剧词条的核心检索词：
     * Mikan 的 `/Home/Search` 番剧卡片匹配不支持带空格的多词分词（仅匹配连续子串），
     * 且后续有 `/Home/Bangumi/{id}` 的 `bgm.tv/subject/{bgmId}` 强校验兜底，
     * 因此优先使用首个独立词块（如 `"飙马野郎"`）以一次命中番剧卡片，避免多词带空格请求白跑。
     */
    internal fun buildDiscoverySearchTerms(
        title: String,
        alternativeTitles: List<String>,
    ): List<String> {
        val results = mutableListOf<String>()
        val tokens = title.split(TOKEN_SPLIT_REGEX).filter { it.isNotBlank() }
        if (tokens.isNotEmpty() && tokens.first().length >= 2) {
            results += tokens.first()
        }
        dropLastTokenIfMultiWord(title)?.let { results += it }
        if (title.isNotBlank()) {
            results += title
        }
        for (alt in alternativeTitles) {
            val altTokens = alt.split(TOKEN_SPLIT_REGEX).filter { it.isNotBlank() }
            if (altTokens.isNotEmpty() && altTokens.first().length >= 2) {
                results += altTokens.first()
            }
            dropLastTokenIfMultiWord(alt)?.let { results += it }
            results += alt
        }
        return results.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    private fun dropLastTokenIfMultiWord(text: String): String? {
        val tokens = text.trim().split(TOKEN_SPLIT_REGEX).filter { it.isNotBlank() }
        if (tokens.size < 2) return null
        val dropped = tokens.dropLast(1).joinToString(" ").trim()
        return dropped.takeIf { it.length >= 2 && it != text.trim() }
    }

    private suspend fun fetchAndParseRss(url: String): List<MikanTorrentResource> {
        val page = pageFetchService.fetchHtml(url)
        if (page == null || page.html.isBlank()) {
            logger.w { "抓取 Mikan RSS 返回空或失败: $url" }
            return emptyList()
        }
        val parsed = MikanRssParser.parse(page.html)
        if (parsed.isEmpty()) {
            logger.d { "Mikan RSS 内容解析结果为空: $url (htmlLen=${page.html.length})" }
        }
        return parsed
    }

    companion object {
        private val TOKEN_SPLIT_REGEX = Regex("""[\s/／:：\-—~～]+""")
        private const val MIN_SUFFICIENT_RSS_RESULTS = 4
    }
}
