package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.repository.resolver.DanmakuParser
import com.infinitezerone.minibgm.core.data.repository.resolver.episodeNumberFromLabel
import com.infinitezerone.minibgm.core.database.dao.DanmakuOverrideDao
import com.infinitezerone.minibgm.core.database.entity.DanmakuEpisodeOverrideEntity
import com.infinitezerone.minibgm.core.model.DanmakuConfig
import com.infinitezerone.minibgm.core.model.DanmakuEpisodeCandidate
import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.DanmakuMatch
import com.infinitezerone.minibgm.core.model.DanmakuMatchState
import com.infinitezerone.minibgm.core.model.DanmakuResult
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.PageFetchService
import com.infinitezerone.minibgm.core.network.sha256
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs

interface DanmakuRepository {
    /**
     * 根据番剧名称与分集号获取弹弹play聚合弹幕，并透出分集匹配元信息。
     * 支持传入自定义或配置的 appId/appSecret 进行官方签名验证；若未传入则尝试无签名/兼容端点。
     * Fail-Open 架构：网络故障或找不到弹幕时返回空列表，绝不抛出未捕获异常。
     *
     * @param bangumiEpisodeId bangumi.tv 分集 id，用户纠正覆盖（[saveDanmakuOverride]）的持久化主键
     * @param episodeName bangumi 分集标题，仅作为匹配评分的次级信号
     */
    suspend fun getDanmaku(
        bangumiEpisodeId: Long,
        subjectId: Long,
        title: String,
        episodeSort: Float,
        episodeName: String? = null,
        appId: String? = null,
        appSecret: String? = null,
        apiEndpoint: String? = null,
    ): DanmakuResult

    /**
     * 用户手动挑选弹幕分集：落库为该 bangumi 分集的覆盖（真值最高来源），
     * 之后的 [getDanmaku] 调用优先按此覆盖取数。
     */
    suspend fun saveDanmakuOverride(
        bangumiEpisodeId: Long,
        subjectId: Long,
        candidate: DanmakuEpisodeCandidate,
    )

    /** 撤销该 bangumi 分集的用户覆盖，恢复自动匹配 */
    suspend fun clearDanmakuOverride(bangumiEpisodeId: Long)
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
    private val danmakuOverrideDao: DanmakuOverrideDao? = null,
    private val settingsRepository: SettingsRepository? = null,
) : DanmakuRepository {
    private val logger = bgmLogger("Bgm/Danmaku")

    /** 注释弹幕正文按"已解析出的 dandanplay 分集 id"缓存——覆盖切换换 id 即换键，天然隔离 */
    private val memoryCache = mutableMapOf<String, List<DanmakuItem>>()

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun getDanmaku(
        bangumiEpisodeId: Long,
        subjectId: Long,
        title: String,
        episodeSort: Float,
        episodeName: String?,
        appId: String?,
        appSecret: String?,
        apiEndpoint: String?,
    ): DanmakuResult {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank() || episodeSort <= 0f) {
            logger.w { "忽略无效弹幕请求: title='$title', episodeSort=$episodeSort" }
            return DanmakuResult(emptyList(), null)
        }

        val config = runCatching { settingsRepository?.danmakuConfig?.first() }.getOrNull()
        val effectiveAppId = appId?.trim()?.ifBlank { null } ?: config?.appId?.trim()?.ifBlank { null }
        val effectiveAppSecret = appSecret?.trim()?.ifBlank { null } ?: config?.appSecret?.trim()?.ifBlank { null }
        val rawEndpoint =
            apiEndpoint?.trim()?.ifBlank { null }
                ?: config?.effectiveEndpoint
                ?: DanmakuConfig.DEFAULT_ENDPOINT
        val endpoint = rawEndpoint.removeSuffix("/")

        val hasAuth = !effectiveAppId.isNullOrBlank() && !effectiveAppSecret.isNullOrBlank()
        logger.i {
            "开始拉取弹幕: anime='$cleanTitle', ep=${episodeSort.toInt()}, endpoint=$endpoint, " +
                "鉴权状态=${if (hasAuth) "已配置签名(AppId=$effectiveAppId)" else "免签模式/未配置"}"
        }

        val searchPath = "/api/v2/search/episodes"
        val encodedTitle = PlaybackSourceRule.encodeParam(cleanTitle)
        val searchUrl = "$endpoint$searchPath?anime=$encodedTitle&episode=${episodeSort.toInt()}"

        val searchHeaders = buildAuthHeaders(effectiveAppId, effectiveAppSecret, searchPath)
        val searchPage = pageFetchService.fetchHtml(searchUrl, searchHeaders)
        if (searchPage == null) {
            logger.w { "搜索分集接口失败或不可达: url=$searchUrl" }
            return DanmakuResult(emptyList(), null)
        }

        if (searchPage.html.contains("Missing Authentication Headers", ignoreCase = true) ||
            searchPage.html.contains("403 Forbidden", ignoreCase = true)
        ) {
            logger.w {
                "弹弹play官方拦截 (403 Forbidden): 缺少认证请求头。如需直连官方请在「设置 -> 弹幕设置」配置 AppId/AppSecret，" +
                    "或使用免签自建/代理端点。"
            }
            return DanmakuResult(emptyList(), null)
        }

        val searchResponse =
            runCatching {
                BgmHttpClient.jsonConfig.decodeFromString<DandanSearchEpisodesResponse>(searchPage.html)
            }.onFailure {
                logger.w { "解析分集搜索响应失败: ${it.message}, body=${searchPage.html.take(200)}" }
            }.getOrNull() ?: return DanmakuResult(emptyList(), null)

        val candidates =
            searchResponse.animes
                .filter { it.episodes.isNotEmpty() }
                .flatMap { anime ->
                    anime.episodes.map { episode ->
                        DanmakuEpisodeCandidate(
                            animeId = anime.animeId,
                            animeTitle = anime.animeTitle,
                            episodeId = episode.episodeId,
                            episodeTitle = episode.episodeTitle,
                        )
                    }
                }

        val override = runCatching { danmakuOverrideDao?.getOverride(bangumiEpisodeId) }.getOrNull()
        val match = resolveMatch(candidates, override, cleanTitle, episodeSort, episodeName)
        val episodeId = match?.chosen?.episodeId
        if (episodeId == null || episodeId <= 0L) {
            logger.w {
                "未在弹幕库中找到匹配剧集: anime='$cleanTitle', ep=${episodeSort.toInt()} (返回 animeCount=${searchResponse.animes.size})"
            }
            return DanmakuResult(emptyList(), match)
        }
        logger.i {
            "弹幕分集匹配完成: state=${match.state}, animeTitle='${match.chosen?.animeTitle}', " +
                "episodeTitle='${match.chosen?.episodeTitle}', episodeId=$episodeId, 候选数=${candidates.size}"
        }

        val cacheKey = "${endpoint}_ep$episodeId"
        memoryCache[cacheKey]?.let {
            logger.d { "命中内存缓存弹幕: key=$cacheKey, count=${it.size}" }
            return DanmakuResult(it, match)
        }

        val commentPath = "/api/v2/comment/$episodeId"
        val commentUrl = "$endpoint$commentPath?withRelated=true"
        val commentHeaders = buildAuthHeaders(effectiveAppId, effectiveAppSecret, commentPath)
        val commentPage = pageFetchService.fetchHtml(commentUrl, commentHeaders)
        if (commentPage == null) {
            logger.w { "拉取弹幕详情网络失败: url=$commentUrl" }
            return DanmakuResult(emptyList(), match)
        }

        val items = DanmakuParser.parseDandanJson(commentPage.html)
        logger.i { "成功获取并解析弹幕: ${items.size} 条 (anime='$cleanTitle', ep=${episodeSort.toInt()}, episodeId=$episodeId)" }
        if (items.isNotEmpty()) {
            memoryCache[cacheKey] = items
        }
        return DanmakuResult(items, match)
    }

    override suspend fun saveDanmakuOverride(
        bangumiEpisodeId: Long,
        subjectId: Long,
        candidate: DanmakuEpisodeCandidate,
    ) {
        runCatching {
            danmakuOverrideDao?.upsertOverride(
                DanmakuEpisodeOverrideEntity(
                    episodeId = bangumiEpisodeId,
                    subjectId = subjectId,
                    dandanAnimeId = candidate.animeId,
                    dandanEpisodeId = candidate.episodeId,
                    dandanAnimeTitle = candidate.animeTitle,
                    dandanEpisodeTitle = candidate.episodeTitle,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }.onFailure {
            logger.w { "保存弹幕分集覆盖失败: episodeId=$bangumiEpisodeId, ${it.message}" }
        }
    }

    override suspend fun clearDanmakuOverride(bangumiEpisodeId: Long) {
        runCatching { danmakuOverrideDao?.deleteOverride(bangumiEpisodeId) }
            .onFailure { logger.w { "清除弹幕分集覆盖失败: episodeId=$bangumiEpisodeId, ${it.message}" } }
    }

    /**
     * 分集匹配裁决。优先级：用户覆盖 > 高置信自动匹配 > 回退默认取值。
     * 覆盖命中的分集即使不在候选列表里（搜索结果已变化）也照用——用户确认过的选择是真值。
     */
    private fun resolveMatch(
        candidates: List<DanmakuEpisodeCandidate>,
        override: DanmakuEpisodeOverrideEntity?,
        subjectTitle: String,
        episodeSort: Float,
        episodeName: String?,
    ): DanmakuMatch? {
        if (candidates.isEmpty() && override == null) return null
        if (override != null) {
            val overrideCandidate =
                DanmakuEpisodeCandidate(
                    animeId = override.dandanAnimeId,
                    animeTitle = override.dandanAnimeTitle,
                    episodeId = override.dandanEpisodeId,
                    episodeTitle = override.dandanEpisodeTitle,
                )
            return DanmakuMatch(
                chosen = candidates.firstOrNull { it.episodeId == override.dandanEpisodeId } ?: overrideCandidate,
                candidates = candidates,
                state = DanmakuMatchState.USER_OVERRIDE,
            )
        }
        val auto = pickAutoMatch(candidates, subjectTitle, episodeSort, episodeName)
        if (auto != null) {
            return DanmakuMatch(chosen = auto, candidates = candidates, state = DanmakuMatchState.AUTO_MATCHED)
        }
        // 无法置信匹配：回退历史行为（首个含分集条目的第一集），候选透出给用户纠正
        val fallback = candidates.firstOrNull()
        return DanmakuMatch(chosen = fallback, candidates = candidates, state = DanmakuMatchState.FALLBACK)
    }

    /**
     * 自动匹配：分集号精确对上（dandanplay 分集标题解析出的集数 == bangumi episodeSort）才够格；
     * 多个候选同分时用番剧标题相似度与分集名包含关系破平，破不平（并列第一）则放弃自动匹配。
     */
    private fun pickAutoMatch(
        candidates: List<DanmakuEpisodeCandidate>,
        subjectTitle: String,
        episodeSort: Float,
        episodeName: String?,
    ): DanmakuEpisodeCandidate? {
        val exact =
            candidates.filter { candidate ->
                val parsed = episodeNumberFromLabel(candidate.episodeTitle)
                parsed != null && abs(parsed - episodeSort) < EPISODE_MATCH_EPSILON
            }
        if (exact.isEmpty()) return null
        if (exact.size == 1) return exact.first()
        val scored =
            exact
                .map { it to candidateScore(it, subjectTitle, episodeName) }
                .sortedByDescending { it.second }
        return if (scored[0].second > scored[1].second) scored[0].first else null
    }

    private fun candidateScore(
        candidate: DanmakuEpisodeCandidate,
        subjectTitle: String,
        episodeName: String?,
    ): Double = titleSimilarity(candidate.animeTitle, subjectTitle) + episodeNameBonus(candidate.episodeTitle, episodeName)

    /**
     * 番剧标题相似度：归一化后互为包含得满分，否则按字符集重合度粗略估。
     * 只用于多个集数相同候选之间的破平，不追求语义级精确。
     */
    private fun titleSimilarity(
        a: String,
        b: String,
    ): Double {
        val left = a.normalizeForMatch()
        val right = b.normalizeForMatch()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left == right || left.contains(right) || right.contains(left)) return 1.0
        val leftChars = left.toSet()
        val rightChars = right.toSet()
        return leftChars.intersect(rightChars).size.toDouble() / maxOf(leftChars.size, rightChars.size)
    }

    private fun episodeNameBonus(
        episodeTitle: String,
        episodeName: String?,
    ): Double {
        val name = episodeName?.trim()?.normalizeForMatch().orEmpty()
        if (name.length < MIN_NAME_BONUS_LENGTH) return 0.0
        return if (episodeTitle.normalizeForMatch().contains(name)) EPISODE_NAME_BONUS else 0.0
    }

    private fun String.normalizeForMatch(): String =
        lowercase()
            .replace(WHITESPACE_OR_PUNCTUATION, "")

    private companion object {
        const val EPISODE_MATCH_EPSILON = 0.01f
        const val MIN_NAME_BONUS_LENGTH = 2
        const val EPISODE_NAME_BONUS = 0.5
        val WHITESPACE_OR_PUNCTUATION = Regex("""[\s\p{Punct}]+""")
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
