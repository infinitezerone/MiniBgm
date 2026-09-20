package com.infinitezerone.minibgm.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.infinitezerone.minibgm.core.ai.AiToolActivity
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SubjectSearchResultDto(
    val id: Long,
    val name: String,
    val nameCn: String,
    val score: Double,
    val airDate: String,
    val eps: Int,
    val summary: String,
)

@Serializable
data class SubjectDetailDto(
    val id: Long,
    val name: String,
    val nameCn: String,
    val summary: String,
    val score: Double,
    val rank: Int,
    val totalEpisodes: Int,
    val airDate: String,
)

@Serializable
data class EpisodeDto(
    val id: Long,
    val ep: Float,
    val sort: Float,
    val name: String,
    val nameCn: String,
    val airDate: String,
)

/**
 * 条目详情与搜索相关 Koog 智能体工具。
 * 支持按关键字搜索动画条目、获取条目完整详情与剧集列表。
 */
class SubjectTools(
    private val searchRepository: SearchRepository,
    private val subjectRepository: SubjectRepository,
    private val json: Json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        },
) : ToolSet {
    @Tool
    @LLMDescription("Search anime by title or keyword")
    suspend fun searchAnime(
        @LLMDescription("Search query keyword")
        query: String,
        @LLMDescription("Maximum number of results to return (default 10)")
        limit: Int = 10,
    ): String {
        if (query.isBlank()) {
            return "Search query must not be empty."
        }
        AiToolActivity.report("搜索动画条目", "关键词：$query")
        val resolvedLimit = if (limit <= 0) 10 else limit.coerceAtMost(50)
        return when (val result = searchRepository.searchSubjects(query = query.trim(), type = 2, limit = resolvedLimit)) {
            is AppResult.Success -> {
                val items =
                    result.data.list.map {
                        SubjectSearchResultDto(
                            id = it.id,
                            name = it.name,
                            nameCn = it.nameCn,
                            score = it.rating?.score ?: 0.0,
                            airDate = it.airDate,
                            eps = it.eps.takeIf { ep -> ep > 0 } ?: it.totalEpisodes,
                            summary = it.summary.take(200),
                        )
                    }
                if (items.isEmpty()) {
                    "No anime found matching query '$query'."
                } else {
                    json.encodeToString(items)
                }
            }
            is AppResult.Error -> {
                "Search failed: ${result.throwable.message ?: "Unknown error"}"
            }
            is AppResult.Loading -> {
                "Search in progress, please retry."
            }
        }
    }

    @Tool
    @LLMDescription("Get comprehensive details for a specific anime by subject ID")
    suspend fun getSubjectDetail(
        @LLMDescription("Bangumi subject ID")
        subjectId: Long,
    ): String {
        if (subjectId <= 0) {
            return "Invalid subject ID: $subjectId. Subject ID must be a positive integer."
        }
        AiToolActivity.report("获取条目详情", "条目 ID $subjectId")
        return when (val result = subjectRepository.fetchSubjectDetail(subjectId)) {
            is AppResult.Success -> {
                val subject = result.data
                val dto =
                    SubjectDetailDto(
                        id = subject.id,
                        name = subject.name,
                        nameCn = subject.nameCn,
                        summary = subject.summary,
                        score = subject.rating?.score ?: 0.0,
                        rank = subject.rating?.rank ?: 0,
                        totalEpisodes = subject.eps.takeIf { it > 0 } ?: subject.totalEpisodes,
                        airDate = subject.airDate,
                    )
                json.encodeToString(dto)
            }
            is AppResult.Error -> {
                "Failed to get subject details for ID $subjectId: ${result.throwable.message ?: "Unknown error"}"
            }
            is AppResult.Loading -> {
                "Loading subject details for ID $subjectId, please retry."
            }
        }
    }

    @Tool
    @LLMDescription("Get episode list for a specific anime by subject ID")
    suspend fun getSubjectEpisodes(
        @LLMDescription("Bangumi subject ID")
        subjectId: Long,
    ): String {
        if (subjectId <= 0) {
            return "Invalid subject ID: $subjectId. Subject ID must be a positive integer."
        }
        AiToolActivity.report("获取剧集列表", "条目 ID $subjectId")
        return when (val result = subjectRepository.fetchEpisodes(subjectId)) {
            is AppResult.Success -> {
                val episodes =
                    result.data.map {
                        EpisodeDto(
                            id = it.id,
                            ep = it.ep,
                            sort = it.sort,
                            name = it.name,
                            nameCn = it.nameCn,
                            airDate = it.airdate,
                        )
                    }
                if (episodes.isEmpty()) {
                    "No episodes found for subject ID $subjectId."
                } else {
                    json.encodeToString(episodes)
                }
            }
            is AppResult.Error -> {
                "Failed to get episodes for subject ID $subjectId: ${result.throwable.message ?: "Unknown error"}"
            }
            is AppResult.Loading -> {
                "Loading episodes for subject ID $subjectId, please retry."
            }
        }
    }
}
