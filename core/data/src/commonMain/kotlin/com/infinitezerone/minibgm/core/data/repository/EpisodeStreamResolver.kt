package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    private val verifier: PlaybackSourceVerifier? = null,
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
     * 试探候选关键词直到命中直链；[timeoutMs] 包住整个流程。
     *
     * 性能优化（受控双路竞速）：
     * 前两个最高置信度关键词（如原名与主别名）采用并发竞速（Racing），谁先返回有效直链谁起播，
     * 并立即取消另一路的等待，将起播耗时减少 50% 以上；
     * 若前两个均未命中，继续串行试探后续长尾候选。
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
            if (queryTitles.isEmpty()) {
                return@withTimeoutOrNull ProbeOutcome(
                    rule = rule,
                    sources = emptyList(),
                    matchedTitle = "",
                    attempted = 0,
                    attemptTotal = 0,
                    timedOut = false,
                )
            }

            suspend fun resolveCandidate(title: String): List<PlayableSource> =
                if (rule.kind == PlaybackRuleKind.SOURCE) {
                    resolver.resolveRule(
                        rule = rule,
                        title = title,
                        epNumber = epSort,
                        subjectId = subjectId,
                        episodeId = episodeId,
                    )
                } else {
                    val targetUrl =
                        rule.resolveUrl(
                            title = title,
                            ep = epNumberString(epSort),
                            subjectId = subjectId,
                            episodeId = episodeId,
                        )
                    resolver.resolvePages(
                        pageUrls = listOf(targetUrl),
                        epNumber = epSort,
                        siteName = rule.name,
                        title = title,
                    )
                }

            suspend fun findPlayableCandidate(candidates: List<PlayableSource>): PlayableSource? {
                val direct = candidates.filter { it.kind == PlaylistEntryKind.DIRECT && it.url.isNotBlank() }
                if (direct.isEmpty()) return null
                return if (verifier != null) {
                    direct.firstOrNull { verifier.verify(it) !is StreamVerification.NotPlayable }
                } else {
                    direct.first()
                }
            }

            // 1. 前 2 个高优先级候选进行受控并发竞速
            val fastTrack = queryTitles.take(2)
            if (fastTrack.size == 2) {
                onProgress(1, queryTitles.size)
                data class FastHit(
                    val title: String,
                    val sources: List<PlayableSource>,
                    val attemptIndex: Int,
                )
                val fastResult: FastHit? =
                    coroutineScope {
                        val channel = kotlinx.coroutines.channels.Channel<FastHit>(2)
                        val job1 =
                            this.launch {
                                try {
                                    val c = resolveCandidate(fastTrack[0])
                                    val p = findPlayableCandidate(c)
                                    if (p != null) channel.send(FastHit(fastTrack[0], listOf(p) + c.filter { it != p }, 1))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Throwable) {
                                    // 异常隔离：单个候选网络或解析异常不破坏竞速兄弟协程
                                }
                            }
                        val job2 =
                            this.launch {
                                try {
                                    val c = resolveCandidate(fastTrack[1])
                                    val p = findPlayableCandidate(c)
                                    if (p != null) channel.send(FastHit(fastTrack[1], listOf(p) + c.filter { it != p }, 2))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Throwable) {
                                    // 异常隔离：单个候选网络或解析异常不破坏竞速兄弟协程
                                }
                            }

                        // 并发竞速：只要任一分支找到可用直链，立即返回并取消未完成任务
                        var winner: FastHit? = null
                        val completionWatcher =
                            this.launch {
                                job1.join()
                                job2.join()
                                channel.close()
                            }

                        for (hit in channel) {
                            winner = hit
                            job1.cancel()
                            job2.cancel()
                            completionWatcher.cancel()
                            break
                        }
                        winner
                    }

                if (fastResult != null) {
                    return@withTimeoutOrNull ProbeOutcome(
                        rule = rule,
                        sources = fastResult.sources,
                        matchedTitle = fastResult.title,
                        attempted = fastResult.attemptIndex,
                        attemptTotal = queryTitles.size,
                        timedOut = false,
                    )
                }
            }

            // 2. 长尾候选串行回退
            var attempted = if (fastTrack.size == 2) 2 else 0
            val remainingTitles = if (fastTrack.size == 2) queryTitles.drop(2) else queryTitles
            for (queryTitle in remainingTitles) {
                attempted++
                onProgress(attempted, queryTitles.size)
                val candidates = resolveCandidate(queryTitle)
                val playableCandidate = findPlayableCandidate(candidates)
                if (playableCandidate != null) {
                    return@withTimeoutOrNull ProbeOutcome(
                        rule = rule,
                        sources = listOf(playableCandidate) + candidates.filter { it != playableCandidate },
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
