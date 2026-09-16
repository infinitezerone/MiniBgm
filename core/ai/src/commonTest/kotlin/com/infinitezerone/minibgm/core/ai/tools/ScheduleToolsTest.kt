package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleToolsTest {
    private val fakeScheduleRepository = FakeScheduleRepository()
    private val scheduleTools = ScheduleTools(fakeScheduleRepository)

    @Test
    fun getSchedule_returns_formatted_schedules_for_weekday() =
        runTest {
            val scheduleItem =
                AirSchedule(
                    bgmId = 1001L,
                    title = "葬送のフリーレン",
                    titleCn = "葬送的芙莉莲",
                    ratingScore = 9.1,
                    weekday = 5,
                    timeCst = "23:00",
                    nextEpisodeNumber = 28,
                    nextEpisodeAtUtc = "2024-03-22T15:00:00Z",
                )
            fakeScheduleRepository.sendSchedules(5, listOf(scheduleItem))

            val result = scheduleTools.getSchedule(weekday = 5)
            assertTrue(result.contains("1001"))
            assertTrue(result.contains("葬送的芙莉莲"))
            assertTrue(result.contains("23:00"))
            assertTrue(result.contains("9.1"))
        }

    @Test
    fun getSchedule_with_zero_queries_current_weekday() =
        runTest {
            val currentWeekday = TimeUtils.cstWeekdayOfEpoch(TimeUtils.nowEpochMillis())
            val scheduleItem =
                AirSchedule(
                    bgmId = 2002L,
                    title = "Test Anime Today",
                    titleCn = "今日动画",
                    ratingScore = 8.5,
                    weekday = currentWeekday,
                    timeCst = "20:00",
                    nextEpisodeNumber = 1,
                    nextEpisodeAtUtc = "2026-09-16T12:00:00Z",
                )
            fakeScheduleRepository.sendSchedules(currentWeekday, listOf(scheduleItem))

            val result = scheduleTools.getSchedule(weekday = 0)
            assertTrue(result.contains("2002"))
            assertTrue(result.contains("今日动画"))
        }

    @Test
    fun getSchedule_returns_fallback_message_when_no_schedules() =
        runTest {
            fakeScheduleRepository.sendSchedules(1, emptyList())
            val result = scheduleTools.getSchedule(weekday = 1)
            assertTrue(result.contains("No broadcast anime scheduled for weekday 1"))
        }

    @Test
    fun getNextEpisodeAiring_returns_airing_events() =
        runTest {
            fakeScheduleRepository.upcomingAiring =
                listOf(
                    UpcomingAiring(
                        subjectId = 1001L,
                        title = "Frieren",
                        titleCn = "芙莉莲",
                        episode = 29,
                        airAtUtc = "2026-09-20T15:00:00Z",
                        kind = "scheduled",
                    ),
                )

            val result = scheduleTools.getNextEpisodeAiring(subjectIds = listOf(1001L), hoursAhead = 120)
            assertTrue(result.contains("1001"))
            assertTrue(result.contains("芙莉莲"))
            assertTrue(result.contains("29"))
            assertTrue(result.contains("scheduled"))
        }

    @Test
    fun getNextEpisodeAiring_handles_empty_input_and_results() =
        runTest {
            val emptyInputResult = scheduleTools.getNextEpisodeAiring(subjectIds = emptyList())
            assertEquals("No valid subject IDs provided.", emptyInputResult)

            val negativeIdsResult = scheduleTools.getNextEpisodeAiring(subjectIds = listOf(-1L, 0L))
            assertEquals("No valid subject IDs provided.", negativeIdsResult)

            fakeScheduleRepository.upcomingAiring = emptyList()
            val noAiringResult = scheduleTools.getNextEpisodeAiring(subjectIds = listOf(9999L), hoursAhead = -10)
            assertTrue(noAiringResult.contains("No upcoming air events found"))
        }
}
