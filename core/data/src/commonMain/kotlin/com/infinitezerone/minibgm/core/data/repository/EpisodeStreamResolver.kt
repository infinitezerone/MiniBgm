package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 「单条规则 × 标题候选 → 单集直链」的统一解析编排，播放器与 AI 找源工具共用。
 *
 * 候选顺序以播放器侧实测为准（3 个采集站 × 4 部番的命中数）：
 * - 原样（简体）11/12、繁体 5/12、日文原名 2/12，其中两个站是**纯简体**（繁体 0 命中），
 *   所以「原样」必须排第一：把繁体排前面等于每轮固定白费一发请求。
 * - 别名（Bangumi infobox 的台译/港译/英文名/罗马音）是采集站标题上真正写的字，
 *   中文字形优先、拉丁字母垫后（由 [com.infinitezerone.minibgm.core.common.ChineseConverter.searchTitles] 负责）。
 * - 集号组合按规则形态分流——两者搜索语义完全不同：
 *   - SOURCE（取源接口）：`wd=` 多为**标题模糊搜索**，一次就返回整部片子（含全部分集），
 *     集号由解析器在结果里本地匹配。把集号写进关键词反而匹配不上，故纯标题优先、集号组合兜底；
 *   - PAGE（网页搜索页）：文章以「标题 集号」命名，带集号能直接命中单集页，保持集号优先。
 *
 * 命中判定统一为「结果中存在 DIRECT 且 url 非空」。
 *
 * 注意：PAGE 规则在 AI 侧语义不同——AI 找源只产出 PAGE 占位卡（不抓取，打开动作留给用户），
 * 因此 AI 侧不使用 [probeRule] 的 PAGE 分支；这是产品差异而非重复实现。
 */
class EpisodeStreamResolver(
    private val resolver: PlaybackResolverRepository,
) {
    /** 一次探测的结果：[sources] 为命中候选时该规则的全部返回（可能含整季，调用方自行过滤） */
    data class ProbeOutcome(
        val rule: PlaybackSourceRule,
        val sources: List<PlayableSource>,
        val matchedTitle: String,
        val attempted: Int,
        val attemptTotal: Int,
        val timedOut: Boolean,
    ) {
        /** 首个可直接起播的直链（kind = DIRECT 且 url 非空） */
        val directSource: PlayableSource?
            get() = sources.firstOrNull { it.kind == PlaylistEntryKind.DIRECT && it.url.isNotBlank() }
    }

    /**
     * 串行试探候选关键词直到命中直链；[timeoutMs] 包住整个循环（withTimeoutOrNull 能真正打断
     * 挂起的请求，而不是只在两次尝试之间检查时钟）。超时返回 null，试完未命中返回空 sources 的结果。
     */
    suspend fun probeRule(
        rule: PlaybackSourceRule,
        baseTitles: List<String>,
        epSort: Float,
        subjectId: Long,
        episodeId: Long = 0L,
        timeoutMs: Long = DEFAULT_PROBE_TIMEOUT_MS,
        onProgress: (attempted: Int, attemptTotal: Int) -> Unit = { _, _ -> },
    ): ProbeOutcome? =
        withTimeoutOrNull(timeoutMs) {
            val queryTitles = buildRuleQueryCandidates(rule, baseTitles, epSort)
            var attempted = 0
            for (queryTitle in queryTitles) {
                attempted++
                onProgress(attempted, queryTitles.size)
                val candidates =
                    if (rule.kind == PlaybackRuleKind.SOURCE) {
                        resolver.resolveRule(
                            rule = rule,
                            title = queryTitle,
                            epNumber = epSort,
                            subjectId = subjectId,
                            episodeId = episodeId,
                        )
                    } else {
                        val targetUrl =
                            rule.resolveUrl(
                                title = queryTitle,
                                ep = epNumberString(epSort),
                                subjectId = subjectId,
                                episodeId = episodeId,
                            )
                        resolver.resolvePages(
                            pageUrls = listOf(targetUrl),
                            epNumber = epSort,
                            siteName = rule.name,
                            title = queryTitle,
                        )
                    }
                if (candidates.any { it.kind == PlaylistEntryKind.DIRECT && it.url.isNotBlank() }) {
                    return@withTimeoutOrNull ProbeOutcome(
                        rule = rule,
                        sources = candidates,
                        matchedTitle = queryTitle,
                        attempted = attempted,
                        attemptTotal = queryTitles.size,
                        timedOut = false,
                    )
                }
            }
            ProbeOutcome(
                rule = rule,
                sources = emptyList(),
                matchedTitle = "",
                attempted = attempted,
                attemptTotal = queryTitles.size,
                timedOut = false,
            )
        }

    companion object {
        /** 8 个候选 × 单次请求 ≈16 秒，20 秒总超时与之同量级 */
        const val DEFAULT_PROBE_TIMEOUT_MS: Long = 20_000L

        /** 集号的 URL/关键词形态：整数集不带小数点，其余原样 */
        fun epNumberString(epSort: Float): String = if (epSort == epSort.toInt().toFloat()) epSort.toInt().toString() else epSort.toString()

        /**
         * 关键词候选序列：SOURCE = 纯标题优先 + 集号组合兜底；PAGE = 集号组合优先 + 纯标题兜底。
         * 集号组合仅在模板不含 `{ep}`（集号由解析器本地匹配）且 epSort > 0 时生成，含补零与原值两种后缀。
         */
        fun buildRuleQueryCandidates(
            rule: PlaybackSourceRule,
            baseTitles: List<String>,
            epSort: Float,
        ): List<String> {
            val epNumStr = epNumberString(epSort)
            val episodeQuerySuffixes =
                if (!rule.urlTemplate.contains("{ep}") && epSort > 0f) {
                    val padded = epSort.toInt().toString().padStart(2, '0')
                    if (padded != epNumStr) listOf(padded, epNumStr) else listOf(padded)
                } else {
                    emptyList()
                }
            val episodeQueries = baseTitles.flatMap { title -> episodeQuerySuffixes.map { suffix -> "$title $suffix" } }
            return (
                if (rule.kind == PlaybackRuleKind.SOURCE) {
                    baseTitles + episodeQueries
                } else {
                    episodeQueries + baseTitles
                }
            ).distinct().ifEmpty { listOf("") }
        }
    }
}
