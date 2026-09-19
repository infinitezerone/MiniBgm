package com.infinitezerone.minibgm.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.PlayableEpisodeList
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
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
    private val json: Json =
        Json {
            prettyPrint = true
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
        val schedule = scheduleRepository.getAllSchedulesStream().first().firstOrNull { it.bgmId == subjectId }
        val title = resolveTitle(schedule, subjectId)
        val episodes = if (epNumber > 0) epNumber.toFloat() else 0f

        fromPlaylists(subjectId, episodes)?.let { hits ->
            return encode(subjectId, title, "自备片单", hits)
        }

        fromRuleSources(subjectId, title, epNumber)?.let { (ruleName, hits) ->
            return encode(subjectId, title, "第三方接口 · $ruleName", hits)
        }

        val pages = candidatePages(epNumber, title, schedule)
        if (pages.isEmpty()) {
            return "No playable source found for subject ID $subjectId: no imported playlist is bound to it " +
                "and no source page is recorded."
        }
        val resolved = playbackResolverRepository.resolvePages(pages, episodes)
        val playable = resolved.filter { it.kind == PlaylistEntryKind.DIRECT }
        if (playable.isEmpty()) {
            return "No playable address resolved for subject ID $subjectId after checking ${pages.size} source page(s)."
        }
        return encode(subjectId, title, "来源站解析", playable)
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
        title: String,
        epNumber: Int,
    ): Pair<String, List<PlayableSource>>? {
        val rules = settingsRepository.playbackRules.first().filter { it.isEnabled && it.kind == PlaybackRuleKind.SOURCE }
        for (rule in rules) {
            val hits =
                playbackResolverRepository.resolveTemplate(
                    url = rule.resolveUrl(title = title, ep = if (epNumber > 0) epNumber.toString() else "", subjectId = subjectId),
                    headers = rule.headers,
                    epNumber = if (epNumber > 0) epNumber.toFloat() else 0f,
                    siteName = rule.name,
                )
            if (hits.isNotEmpty()) return rule.name to hits
        }
        return null
    }

    /** 待解析页面：排期记录的来源站优先，没有记录时退到 B 站搜索页 */
    private fun candidatePages(
        epNumber: Int,
        title: String,
        schedule: AirSchedule?,
    ): List<String> {
        val recorded =
            schedule
                ?.siteLinks
                .orEmpty()
                .filter { it.playUrl.isNotBlank() }
                .sortedBySitePriority()
                .map { it.playUrl }
        if (recorded.isNotEmpty()) return recorded
        if (title.isBlank()) return emptyList()
        val keyword = if (epNumber > 0) "$title 第${epNumber}话" else title
        return listOf(StreamingIntentResolver.buildBilibiliSearchTarget(keyword).webFallbackUrl)
    }

    private fun encode(
        subjectId: Long,
        title: String,
        source: String,
        episodes: List<PlayableSource>,
    ): String =
        json.encodeToString(
            PlayableEpisodeList(
                subjectId = subjectId,
                title = title,
                source = source,
                episodes = episodes.distinctBy { it.url },
            ),
        )

    private suspend fun resolveTitle(
        schedule: AirSchedule?,
        subjectId: Long,
    ): String {
        val fromSchedule = schedule?.titleCn.orEmpty().ifBlank { schedule?.title.orEmpty() }
        if (fromSchedule.isNotBlank()) return fromSchedule
        return when (val result = subjectRepository.fetchSubjectDetail(subjectId)) {
            is AppResult.Success -> result.data.displayName
            is AppResult.Error -> ""
            is AppResult.Loading -> ""
        }
    }

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
