package com.infinitezerone.minibgm.feature.agent

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.SearchResult
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniBgmAgentToolsTest {
    /** 空实现桩：本组测试只覆盖搜索/即将开播工具 */
    private class StubScheduleRepository : ScheduleRepository {
        var upcoming: List<UpcomingAiring> = emptyList()

        override fun getSchedulesByWeekday(weekday: Int): Flow<List<AirSchedule>> = flowOf(emptyList())

        override fun getAllSchedulesStream(): Flow<List<AirSchedule>> = flowOf(emptyList())

        override suspend fun getUpcomingAiringForSubjects(
            subjectIds: List<Long>,
            hoursAhead: Long,
            lookbackHours: Long,
        ): List<UpcomingAiring> = upcoming

        override suspend fun refreshSchedules() = AppResult.Success(Unit)

        override suspend fun syncBangumiData(force: Boolean) = AppResult.Success(Unit)

        override suspend fun getScheduleDefaultOnlyWatching(): Boolean = false

        override suspend fun setScheduleDefaultOnlyWatching(onlyWatching: Boolean) = Unit
    }

    private fun createTools(
        scheduleRepository: ScheduleRepository = StubScheduleRepository(),
        searchRepository: SearchRepository = FakeSearchRepository(),
        collectionRepository: CollectionRepository = FakeCollectionRepository(),
    ): MiniBgmAgentTools = MiniBgmAgentTools(scheduleRepository, searchRepository, collectionRepository)

    @Test
    fun searchSubjectsTool_returnsFormattedList() =
        runTest {
            val searchRepo = FakeSearchRepository()
            searchRepo.searchResult =
                AppResult.Success(
                    SearchResult(
                        total = 2,
                        list =
                            listOf(
                                sampleSubject.copy(id = 1, name = "Re:ゼロ", nameCn = "Re:从零开始", rating = null),
                                sampleSubject.copy(id = 2, name = "Frieren", nameCn = "葬送的芙莉莲"),
                            ),
                    ),
                )
            val tool = createTools(searchRepository = searchRepo).create().first { it.name == "search_subjects" }

            val result = tool.execute("""{"query":"芙莉莲"}""")

            assertTrue(!result.isError)
            assertTrue(result.content.contains("葬送的芙莉莲"))
            assertTrue(result.content.contains("Re:从零开始"))
        }

    @Test
    fun searchSubjectsTool_requiresQueryArg() =
        runTest {
            val tool = createTools().create().first { it.name == "search_subjects" }

            val result = tool.execute("{}")

            assertTrue(result.isError)
            assertTrue(result.content.contains("query"))
        }

    @Test
    fun upcomingAiringsTool_reportsEmptyForNoTracking() =
        runTest {
            val tool = createTools(collectionRepository = FakeCollectionRepository()).create().first { it.name == "get_upcoming_airings" }

            val result = tool.execute("{}")

            assertTrue(!result.isError)
            assertTrue(result.content.contains("没有在看"))
        }

    @Test
    fun upcomingAiringsTool_listsTrackedEpisodes() =
        runTest {
            val scheduleRepo = StubScheduleRepository()
            scheduleRepo.upcoming =
                listOf(
                    UpcomingAiring(
                        subjectId = 1L,
                        title = "Re:ゼロ",
                        titleCn = "Re:从零开始",
                        episode = 3,
                        airAtUtc = "2026-09-12T14:00:00Z",
                        kind = "scheduled",
                        coverUrl = "",
                    ),
                )
            val collectionRepo = FakeCollectionRepository()
            // sampleUserCollection.subjectId = 1001，与 upcoming 的 subjectId 对齐
            collectionRepo.sendCollection(
                com.infinitezerone.minibgm.core.testing.data.sampleUserCollection
                    .copy(subjectId = 1L),
            )
            val tools =
                createTools(
                    scheduleRepository = scheduleRepo,
                    collectionRepository = collectionRepo,
                ).create()
            val tool = tools.first { it.name == "get_upcoming_airings" }

            val result = tool.execute("{}")

            assertTrue(!result.isError)
            assertTrue(result.content.contains("Re:从零开始"))
            assertTrue(result.content.contains("第 3 话"))
        }

    @Test
    fun todayScheduleTool_reportsEmptyDay() =
        runTest {
            val tool = createTools().create().first { it.name == "get_today_schedule" }

            val result = tool.execute("{}")

            assertTrue(!result.isError)
            assertTrue(result.content.contains("今日暂无") || result.content.contains("《"))
        }
}
