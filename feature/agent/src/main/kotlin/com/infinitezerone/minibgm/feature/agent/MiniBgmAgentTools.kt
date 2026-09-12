package com.infinitezerone.minibgm.feature.agent

import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.model.CollectionType
import com.miniagent.agentloop.AgentTool
import com.miniagent.agentloop.ToolResult
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * MiniBgm 域工具集：把已验证的 repository 能力包装为 [AgentTool]，让端侧/云端模型
 * 以自然语言查询时刻表、搜索条目与即将开播。首版全部只读，写操作后续按需追加。
 */
class MiniBgmAgentTools(
    private val scheduleRepository: ScheduleRepository,
    private val searchRepository: SearchRepository,
    private val collectionRepository: CollectionRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun create(): List<AgentTool> =
        listOf(
            todayScheduleTool(),
            searchSubjectsTool(),
            upcomingAiringsTool(),
        )

    private fun argsInt(
        argumentsJson: String,
        key: String,
    ): Int? =
        runCatching {
            (json.parseToJsonElement(argumentsJson) as? JsonObject)
                ?.get(key)
                ?.jsonPrimitive
                ?.intOrNull
        }.getOrNull()

    private fun argsText(
        argumentsJson: String,
        key: String,
    ): String? =
        runCatching {
            (json.parseToJsonElement(argumentsJson) as? JsonObject)
                ?.get(key)
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()

    /** 今日时刻表：按当前 CST 周几取分桶名单 */
    private fun todayScheduleTool(): AgentTool =
        object : AgentTool {
            override val name = "get_today_schedule"
            override val description = "获取今日（按北京时间周几）的番剧放送时刻表，返回条目名、话数与播出时间"
            override val parametersJsonSchema = """{"type":"object","properties":{}}"""

            override suspend fun execute(argumentsJson: String): ToolResult =
                try {
                    val weekday = TimeUtils.cstWeekdayOfEpoch(TimeUtils.nowEpochMillis())
                    val schedules = scheduleRepository.getSchedulesByWeekday(weekday).first()
                    if (schedules.isEmpty()) {
                        ToolResult.success("今日暂无放送条目")
                    } else {
                        val lines =
                            schedules.take(15).map { schedule ->
                                val time = schedule.timeCst.ifBlank { "--:--" }
                                val episode =
                                    schedule.nextEpisodeNumber
                                        .takeIf { it > 0 }
                                        ?.let { "第 $it 话" }
                                        ?: ""
                                "《${schedule.titleCn.ifBlank { schedule.title }}》$episode $time".trim()
                            }
                        ToolResult.success(lines.joinToString("\n"))
                    }
                } catch (e: Exception) {
                    ToolResult.failure("获取今日时刻表失败：${e.message}")
                }
        }

    /** 条目搜索：按关键词查询 Bangumi */
    private fun searchSubjectsTool(): AgentTool =
        object : AgentTool {
            override val name = "search_subjects"
            override val description = "按关键词搜索 Bangumi 条目，返回条目名、年份与评分"
            override val parametersJsonSchema =
                """{"type":"object","properties":{"query":{"type":"string","description":"搜索关键词"}},"required":["query"]}"""

            override suspend fun execute(argumentsJson: String): ToolResult {
                val query = argsText(argumentsJson, "query")
                if (query.isNullOrBlank()) return ToolResult.failure("缺少 query 参数")
                return try {
                    val result = searchRepository.searchSubjects(query = query, limit = 10)
                    when (result) {
                        is com.infinitezerone.minibgm.core.common.AppResult.Success -> {
                            val list = result.data.list
                            if (list.isEmpty()) {
                                ToolResult.success("没有找到匹配「$query」的条目")
                            } else {
                                ToolResult.success(
                                    list.joinToString("\n") { subject ->
                                        val score =
                                            subject.rating
                                                ?.score
                                                ?.takeIf { it > 0.0 }
                                                ?.let { " 评分 $it" } ?: ""
                                        "《${subject.nameCn.ifBlank { subject.name }}》(${subject.id})$score"
                                    },
                                )
                            }
                        }

                        is com.infinitezerone.minibgm.core.common.AppResult.Error ->
                            ToolResult.failure("搜索失败：${result.message}")

                        com.infinitezerone.minibgm.core.common.AppResult.Loading -> ToolResult.failure("搜索仍在进行中")
                    }
                } catch (e: Exception) {
                    ToolResult.failure("搜索失败：${e.message}")
                }
            }
        }

    /** 即将开播：用户在看条目未来 N 小时内的逐话更新 */
    private fun upcomingAiringsTool(): AgentTool =
        object : AgentTool {
            override val name = "get_upcoming_airings"
            override val description = "获取「我追的」番剧未来 N 小时内（默认 24）将要播出的剧集"
            override val parametersJsonSchema =
                """{"type":"object","properties":{"hoursAhead":{"type":"integer","description":"向前看多少小时，默认 24"}}}"""

            override suspend fun execute(argumentsJson: String): ToolResult {
                val hours = argsInt(argumentsJson, "hoursAhead") ?: 24
                return try {
                    val trackedIds =
                        collectionRepository
                            .getCollectionsByTypeStream(CollectionType.DOING)
                            .first()
                            .map { it.subjectId }
                    if (trackedIds.isEmpty()) return ToolResult.success("你当前没有在看中的条目")
                    val airings =
                        scheduleRepository.getUpcomingAiringForSubjects(
                            subjectIds = trackedIds,
                            hoursAhead = hours.toLong(),
                            lookbackHours = 6,
                        )
                    if (airings.isEmpty()) {
                        ToolResult.success("未来 $hours 小时内没有将要播出的剧集")
                    } else {
                        ToolResult.success(
                            airings.take(15).joinToString("\n") { airing ->
                                "《${airing.titleCn.ifBlank { airing.title }}》第 ${airing.episode} 话 · ${airing.airAtUtc}"
                            },
                        )
                    }
                } catch (e: Exception) {
                    ToolResult.failure("获取即将开播失败：${e.message}")
                }
            }
        }
}
