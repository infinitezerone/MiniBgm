package com.infinitezerone.minibgm.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.SiteLink
import com.infinitezerone.minibgm.core.model.sortedBySitePriority
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WatchPageDto(
    val siteName: String,
    val displayName: String,
    val url: String,
    val note: String,
)

/**
 * AI 找源 Koog 工具集。
 *
 * 能力边界（ROADMAP 第 5 节）：只返回用户可自行打开的**页面链接**——来源站番剧页、Bangumi 条目/分集页、
 * 站点搜索页。不解析、不输出媒体直链，也不做批量或自动检索（一律由用户在会话中显式触发）。
 */
class SourceSearchTools(
    private val scheduleRepository: ScheduleRepository,
    private val subjectRepository: SubjectRepository,
    private val json: Json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        },
) : ToolSet {
    @Tool
    @LLMDescription(
        "Find watchable WEBPAGE links for an anime: the streaming-site pages recorded for the entry, " +
            "the Bangumi entry and episode pages, " +
            "and site search pages when no recorded page exists. Results are ordinary web pages for the user to open manually; " +
            "they are NEVER direct video file/stream URLs. " +
            "Use this whenever the user asks where to watch something.",
    )
    suspend fun findWatchPages(
        @LLMDescription("Bangumi subject ID")
        subjectId: Long,
        @LLMDescription("Episode number, 0 when the whole series is meant or the number is unknown")
        epNumber: Int = 0,
    ): String {
        if (subjectId <= 0L) {
            return "Invalid subject ID: $subjectId. Subject ID must be a positive integer."
        }
        val schedule = scheduleRepository.getAllSchedulesStream().first().firstOrNull { it.bgmId == subjectId }
        val title = resolveTitle(schedule, subjectId)
        val pages = buildWatchPages(subjectId, epNumber, title, schedule?.siteLinks.orEmpty())
        if (pages.isEmpty()) {
            return "No watchable page link found for subject ID $subjectId."
        }
        return json.encodeToString(pages)
    }

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

    private fun buildWatchPages(
        subjectId: Long,
        epNumber: Int,
        title: String,
        siteLinks: List<SiteLink>,
    ): List<WatchPageDto> {
        val episodeHint = if (epNumber > 0) "打开后请自行定位第 $epNumber 话" else "来源站番剧页"
        val pages =
            mutableListOf(
                WatchPageDto(
                    siteName = "bangumi",
                    displayName = "Bangumi 条目页",
                    url = "https://bgm.tv/subject/$subjectId",
                    note = "条目资料、讨论与官方外链索引",
                ),
            )
        if (epNumber > 0) {
            pages +=
                WatchPageDto(
                    siteName = "bangumi",
                    displayName = "Bangumi 分集页",
                    url = "https://bgm.tv/subject/$subjectId/ep",
                    note = "分集列表，可确认第 $epNumber 话标题与播出日期",
                )
        }
        siteLinks
            .filter { it.playUrl.isNotBlank() }
            .sortedBySitePriority()
            .forEach { pages += WatchPageDto(it.siteName, it.displayName, it.playUrl, episodeHint) }

        if (title.isNotBlank()) {
            val keyword = if (epNumber > 0) "$title 第${epNumber}话" else title
            if (siteLinks.none { it.siteName.equals(SITE_BILIBILI, ignoreCase = true) }) {
                pages +=
                    WatchPageDto(
                        siteName = SITE_BILIBILI,
                        displayName = "哔哩哔哩搜索页",
                        url = StreamingIntentResolver.buildBilibiliSearchTarget(keyword).webFallbackUrl,
                        note = "搜索结果需自行核对是否为本番剧",
                    )
            }
            if (siteLinks.none { it.siteName.equals(SITE_MIKAN, ignoreCase = true) }) {
                pages +=
                    WatchPageDto(
                        siteName = SITE_MIKAN,
                        displayName = "蜜柑计划搜索页",
                        url = StreamingIntentResolver.buildMikanUrl(keyword = keyword),
                        note = "字幕组 BT/在线资源页，需自行核对话数",
                    )
            }
        }
        return pages.distinctBy { it.url }
    }

    companion object {
        private const val SITE_BILIBILI = "bilibili"
        private const val SITE_MIKAN = "mikan"
    }
}
