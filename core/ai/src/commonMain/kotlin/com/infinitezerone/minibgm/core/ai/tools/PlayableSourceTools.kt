package com.infinitezerone.minibgm.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.infinitezerone.minibgm.core.ai.AiToolActivity
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.ChineseConverter
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.PlayableEpisodeList
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntry
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.forSubject
import com.infinitezerone.minibgm.core.model.matchesForEpisode
import com.infinitezerone.minibgm.core.model.sortedBySitePriority
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * AI 找源 Koog 工具集：产出可以直接喂给播放器的结构化播放数据。
 *
 * 取数顺序固定为「用户自备片单 → 用户配置的第三方取源接口 → 排期记录的来源站页面解析」，
 * 每一步都只读已有数据或按其 URL 发一次请求，不做站点遍历与批量抓取。
 * 检索一律由用户在会话中显式触发。
 */
class PlayableSourceTools(
    private val scheduleRepository: ScheduleRepository,
    private val subjectRepository: SubjectRepository,
    private val settingsRepository: SettingsRepository,
    private val playbackResolverRepository: PlaybackResolverRepository,
    private val playableSourcesStore: com.infinitezerone.minibgm.core.ai.PlayableSourcesStore? = null,
    private val json: Json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        },
) : ToolSet {
    @Tool
    @LLMDescription(
        "Resolve playable media for an anime and return structured playback data: for every episode " +
            "a stream URL together with the request headers needed to play it, plus the episode labels. " +
            "It first uses the playlists the user imported, then the third-party source APIs the user configured, " +
            "and finally the streaming-site pages recorded for the entry resolved into playable addresses. " +
            "Use this whenever the user wants to watch something or asks where to watch it. " +
            "Report only what this tool returns — never invent, guess or modify a URL, and if it finds nothing, say so.",
    )
    suspend fun findPlayableSources(
        @LLMDescription("Bangumi subject ID")
        subjectId: Long,
        @LLMDescription("Episode number, 0 when the whole series / episode list is meant or the number is unknown")
        epNumber: Int = 0,
    ): String {
        if (subjectId <= 0L) {
            return "Invalid subject ID: $subjectId. Subject ID must be a positive integer."
        }
        AiToolActivity.report("解析播放源", "条目 $subjectId，检查自备片单")
        val schedule = scheduleRepository.getAllSchedulesStream().first().firstOrNull { it.bgmId == subjectId }
        val episodes = if (epNumber > 0) epNumber.toFloat() else 0f

        // 自备片单只用条目号与集号匹配，不需要片名——不必为它多取一次条目详情
        fromPlaylists(subjectId, episodes)?.let { hits ->
            return encode(subjectId, scheduleTitle(schedule), "自备片单", hits)
        }

        AiToolActivity.report("解析播放源", "条目 $subjectId，尝试用户配置的取源接口")
        val titles = resolveTitles(schedule, subjectId)
        fromRuleSources(subjectId, titles, epNumber)?.let { (ruleName, hits) ->
            return encode(subjectId, titles.primary, "第三方接口 · $ruleName", hits)
        }

        AiToolActivity.report("解析播放源", "条目 $subjectId，解析来源站页面")
        val pages = candidatePages(schedule)
        if (pages.isEmpty()) {
            return "No playable source found for subject ID $subjectId: no imported playlist is bound to it " +
                "and no source page is recorded."
        }
        val resolved = playbackResolverRepository.resolvePages(pages, episodes, title = titles.primary)
        val playable = resolved.filter { it.kind == PlaylistEntryKind.DIRECT }
        if (playable.isEmpty()) {
            return "No playable address resolved for subject ID $subjectId after checking ${pages.size} source page(s)."
        }
        return encode(subjectId, titles.primary, "来源站解析", playable)
    }

    /** 自备片单优先：用户自己给的地址最可靠，命中就不必再去解析第三方页面 */
    private suspend fun fromPlaylists(
        subjectId: Long,
        epNumber: Float,
    ): List<PlayableSource>? {
        val playlists = settingsRepository.playlists.first()
        val matches =
            if (epNumber > 0f) {
                playlists
                    .matchesForEpisode(subjectId, epNumber)
                    .filter { it.matched }
                    .map { it.playlist.name to it.entry }
            } else {
                playlists
                    .forSubject(subjectId)
                    .flatMap { playlist -> playlist.entries.map { playlist.name to it } }
            }
        val playable =
            matches
                .filter { it.second.kind == PlaylistEntryKind.DIRECT }
                .map { (playlistName, entry) -> entry.toPlayableSource(subjectId, playlistName) }
        return playable.ifEmpty { null }
    }

    /**
     * 用户配置的第三方取源接口（`PlaybackSourceRule` 中 kind = SOURCE 的规则）。
     *
     * 按启用顺序逐条试，第一条解析到结果就返回；每条规则只按其模板发一次请求。
     */
    private suspend fun fromRuleSources(
        subjectId: Long,
        titles: ResolvedTitles,
        epNumber: Int,
    ): Pair<String, List<PlayableSource>>? {
        val rules = settingsRepository.playbackRules.first().filter { it.isEnabled }
        fromDirectSourceRules(rules, subjectId, titles, epNumber)?.let { return it }
        return fromPageRules(rules, subjectId, titles.primary, epNumber)
    }

    private suspend fun fromDirectSourceRules(
        rules: List<PlaybackSourceRule>,
        subjectId: Long,
        titles: ResolvedTitles,
        epNumber: Int,
    ): Pair<String, List<PlayableSource>>? {
        val sourceRules = rules.filter { it.kind == PlaybackRuleKind.SOURCE }
        // 港台站常只认繁体片名，简体名搜不到时会回落到无关列表，所以每条规则都要把候选试完；
        // 候选顺序（主标题优先、别名按中文字形优先、日文原名垫底）与播放器侧同源。
        val queryTitles =
            ChineseConverter
                .searchTitles(
                    primary = titles.primary,
                    aliases = titles.aliases,
                    origin = titles.origin,
                ).ifEmpty { listOf("") }
        for (rule in sourceRules) {
            for (queryTitle in queryTitles) {
                val hits =
                    playbackResolverRepository.resolveRule(
                        rule = rule,
                        title = queryTitle,
                        epNumber = if (epNumber > 0) epNumber.toFloat() else 0f,
                        subjectId = subjectId,
                    )
                if (hits.isNotEmpty()) return rule.name to hits
            }
        }
        return null
    }

    private fun fromPageRules(
        rules: List<PlaybackSourceRule>,
        subjectId: Long,
        title: String,
        epNumber: Int,
    ): Pair<String, List<PlayableSource>>? {
        if (title.isBlank()) return null
        val pageRules = rules.filter { it.kind == PlaybackRuleKind.PAGE }
        if (pageRules.isEmpty()) return null
        val epStr = if (epNumber > 0) epNumber.toString() else ""
        val epFloat = if (epNumber > 0) epNumber.toFloat() else 0f
        val sources =
            pageRules.map { rule ->
                val resolvedUrl = rule.resolveUrl(title = title, ep = epStr, subjectId = subjectId)
                PlayableSource(
                    url = resolvedUrl,
                    kind = PlaylistEntryKind.PAGE,
                    label = if (epNumber > 0) "第 $epNumber 话" else "直达播放页",
                    episodeSort = epFloat,
                    siteName = rule.name,
                    pageUrl = resolvedUrl,
                    headers = rule.headers,
                )
            }
        return "第三方动漫站点" to sources
    }

    /**
     * 待解析页面：只取排期记录的来源站。没有记录就明确返回没有结果，
     * 不做站内搜索兜底——播放站点（如 B 站）的流地址由 JS 运行时向签名接口请求，
     * 静态页面里不存在可被正则抽出的直链，抓搜索页注定空手而归。
     * 按标题找源的正解是用户配置的取源接口/源协议（`PlaybackRuleKind.SOURCE`）。
     */
    private fun candidatePages(schedule: AirSchedule?): List<String> =
        schedule
            ?.siteLinks
            .orEmpty()
            .filter { it.playUrl.isNotBlank() }
            .sortedBySitePriority()
            .map { it.playUrl }

    private fun encode(
        subjectId: Long,
        title: String,
        source: String,
        episodes: List<PlayableSource>,
    ): String {
        val list =
            PlayableEpisodeList(
                subjectId = subjectId,
                title = title,
                source = source,
                episodes = episodes.distinctBy { it.url },
            )
        playableSourcesStore?.set(list)
        return json.encodeToString(list)
    }

    /** 排期记录里的片名（优先中文名）；没有记录时为空 */
    private fun scheduleTitle(schedule: AirSchedule?): String = schedule?.titleCn.orEmpty().ifBlank { schedule?.title.orEmpty() }

    /**
     * 取源检索用的片名集合：主标题 + Bangumi infobox 别名 + 日文原名。
     *
     * 别名先在内存缓存里找（随手从详情页进来就有），没有再取一次条目详情——
     * infobox 里的台译/港译/英文名/罗马音才是采集站标题上真正写的字，
     * 只拿主标题的话只能靠简繁单字转换硬凑（实测日文原名只命中 2/12）。
     */
    private suspend fun resolveTitles(
        schedule: AirSchedule?,
        subjectId: Long,
    ): ResolvedTitles {
        val cached = subjectRepository.getSubjectStream(subjectId).first()
        val detail =
            cached
                ?: when (val result = subjectRepository.fetchSubjectDetail(subjectId)) {
                    is AppResult.Success -> result.data
                    is AppResult.Error -> null
                    is AppResult.Loading -> null
                }
        return ResolvedTitles(
            primary = scheduleTitle(schedule).ifBlank { detail?.displayName.orEmpty() },
            aliases = detail?.titleAliases.orEmpty(),
            origin = detail?.name.orEmpty(),
        )
    }

    /** 检索用的片名候选来源 */
    private data class ResolvedTitles(
        val primary: String,
        val aliases: List<String> = emptyList(),
        val origin: String = "",
    )

    private fun PlaylistEntry.toPlayableSource(
        subjectId: Long,
        playlistName: String,
    ): PlayableSource =
        PlayableSource(
            url = url,
            kind = kind,
            label = label.ifBlank { title },
            episodeSort = label.toFloatOrNull() ?: 0f,
            siteName = siteName.ifBlank { playlistName },
            pageUrl = "bgm://subject/$subjectId",
            headers = headers,
        )
}
