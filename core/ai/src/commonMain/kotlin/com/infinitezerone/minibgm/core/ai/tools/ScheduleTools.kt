package com.infinitezerone.minibgm.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.infinitezerone.minibgm.core.ai.AiToolActivity
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
 * 放送时刻表相关 Koog 智能体工具。
 * 支持按星期/今日查询番剧排播，以及查询指定条目接下来播出的单集信息。
 */
class ScheduleTools(
    private val scheduleRepository: ScheduleRepository,
    private val json: Json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        },
) : ToolSet {
    @Tool
    @LLMDescription(
        "Query anime broadcast schedule for today or a specific weekday. Weekday: 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun. Pass 0 for today.",
    )
    suspend fun getSchedule(
        @LLMDescription("Day of the week (1-7, 0 for today)")
        weekday: Int = 0,
    ): String {
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

    @Tool
    @LLMDescription("Query upcoming next episode air date and time for given anime subject IDs")
    suspend fun getNextEpisodeAiring(
        @LLMDescription("List of subject IDs to check upcoming airing events for")
        subjectIds: List<Long>,
        @LLMDescription("Hours ahead to look into the future, default 72 hours")
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
