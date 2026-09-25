package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.ai.AiToolActivity
import com.infinitezerone.minibgm.core.ai.tool.BgmTool
import com.infinitezerone.minibgm.core.ai.tool.bgmTool
import com.infinitezerone.minibgm.core.ai.tool.int
import com.infinitezerone.minibgm.core.ai.tool.long
import com.infinitezerone.minibgm.core.ai.tool.longList
import com.infinitezerone.minibgm.core.ai.tool.schemaObject
import com.infinitezerone.minibgm.core.ai.tool.schemaProperty
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class ScheduleItemDto(
    val bgmId: Long,
    val title: String,
    val titleCn: String,
    val timeCst: String,
    val ratingScore: Double,
    val nextEpisodeNumber: Int,
    val nextEpisodeAtUtc: String,
)

@Serializable
data class AiringEventDto(
    val subjectId: Long,
    val title: String,
    val titleCn: String,
    val episode: Int,
    val airAtUtc: String,
    val kind: String,
)

/**
 * 放送时刻表相关智能体工具。
 * 支持按星期/今日查询番剧排播，以及查询指定条目接下来播出的单集信息。
 */
class ScheduleTools(
    private val scheduleRepository: ScheduleRepository,
    private val json: Json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        },
) {
    fun tools(): List<BgmTool> =
        listOf(
            bgmTool(
                name = "getSchedule",
                description =
                    "Query anime broadcast schedule for a given day of the week (1=Mon..7=Sun). " +
                        "Defaults to today if weekday is omitted or 0.",
                parametersJsonSchema =
                    schemaObject(
                        properties =
                            buildJsonObject {
                                put(
                                    "weekday",
                                    schemaProperty("integer", "Day of the week: 1 for Monday to 7 for Sunday. 0 for today."),
                                )
                            },
                    ),
            ) { args ->
                getSchedule(args.int("weekday", 0))
            },
            bgmTool(
                name = "getNextEpisodeAiring",
                description = "Query upcoming next episode air date and time for given anime subject IDs",
                parametersJsonSchema =
                    schemaObject(
                        properties =
                            buildJsonObject {
                                put(
                                    "subjectIds",
                                    schemaProperty(
                                        type = "array",
                                        description = "List of subject IDs to check upcoming airing events for",
                                        itemsType = "integer",
                                    ),
                                )
                                put(
                                    "hoursAhead",
                                    schemaProperty("integer", "Hours ahead to look into the future, default 72 hours"),
                                )
                            },
                        required = listOf("subjectIds"),
                    ),
            ) { args ->
                val subjectIds = args.longList("subjectIds")
                val hoursAhead = args.long("hoursAhead", 72L)
                getNextEpisodeAiring(subjectIds, hoursAhead)
            },
        )

    suspend fun getSchedule(weekday: Int = 0): String {
        val targetWeekday =
            if (weekday in 1..7) {
                weekday
            } else {
                TimeUtils.cstWeekdayOfEpoch(TimeUtils.nowEpochMillis())
            }

        val dayDesc = if (weekday in 1..7) "周$weekday" else "今日排播"
        AiToolActivity.report("查询放送表", dayDesc)

        val schedules = scheduleRepository.getSchedulesByWeekday(targetWeekday).first()
        if (schedules.isEmpty()) {
            return "No broadcast anime scheduled for weekday $targetWeekday."
        }

        val dtos =
            schedules.map {
                ScheduleItemDto(
                    bgmId = it.bgmId,
                    title = it.title,
                    titleCn = it.titleCn,
                    timeCst = it.timeCst,
                    ratingScore = it.ratingScore,
                    nextEpisodeNumber = it.nextEpisodeNumber,
                    nextEpisodeAtUtc = it.nextEpisodeAtUtc,
                )
            }
        return json.encodeToString(dtos)
    }

    suspend fun getNextEpisodeAiring(
        subjectIds: List<Long>,
        hoursAhead: Long = 72,
    ): String {
        val validSubjectIds = subjectIds.filter { it > 0 }
        if (validSubjectIds.isEmpty()) {
            return "No valid subject IDs provided."
        }
        AiToolActivity.report("查询单集更新时间", "条目 $validSubjectIds")
        val resolvedHours = if (hoursAhead <= 0) 72L else hoursAhead.coerceAtMost(720L)
        val airings =
            scheduleRepository.getUpcomingAiringForSubjects(
                subjectIds = validSubjectIds,
                hoursAhead = resolvedHours,
            )
        if (airings.isEmpty()) {
            return "No upcoming air events found for subject IDs $validSubjectIds within the next $resolvedHours hours."
        }

        val dtos =
            airings.map {
                AiringEventDto(
                    subjectId = it.subjectId,
                    title = it.title,
                    titleCn = it.titleCn,
                    episode = it.episode,
                    airAtUtc = it.airAtUtc,
                    kind = it.kind,
                )
            }
        return json.encodeToString(dtos)
    }
}
