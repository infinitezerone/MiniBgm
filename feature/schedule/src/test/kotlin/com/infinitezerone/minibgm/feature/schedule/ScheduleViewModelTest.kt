package com.infinitezerone.minibgm.feature.schedule

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.testing.data.sampleAirScheduleList
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = ScheduleViewModel.currentCstDate().dayOfWeek.value

    private fun createViewModel(
        repository: FakeScheduleRepository = FakeScheduleRepository(),
        collectionRepository: FakeCollectionRepository = FakeCollectionRepository(),
    ): ScheduleViewModel =
        ScheduleViewModel(
            scheduleRepository = repository,
            collectionRepository = collectionRepository,
        )

    @Test
    fun initTriggersRefreshAndEmitsTodaySchedules() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()
            repository.sendSchedules(weekday = today, schedules = sampleAirScheduleList)
            val viewModel = createViewModel(repository, collectionRepository)

            val state = viewModel.uiState.first { it.schedules.isNotEmpty() && !it.isLoading }

            assertEquals(1, repository.refreshCallCount)
            assertNull(state.error)
            assertEquals(today, state.selectedWeekday)
            assertEquals(today, state.todayWeekday)
            assertFalse(state.isOfflineCache)
            assertEquals(7, state.dateItems.size)
            assertTrue(state.dateItems.any { it.isToday && it.weekday == today })
            assertEquals(1, state.dateItems.first().weekday)
            assertEquals("周一", state.dateItems.first().weekdayLabel)
            assertEquals(
                "葬送的芙莉莲",
                state.schedules.first().titleCn,
            )
        }

    @Test
    fun selectWeekdaySwitchesScheduleStream() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()
            repository.sendSchedules(weekday = today, schedules = sampleAirScheduleList)
            val saturdaySchedules =
                listOf(
                    AirSchedule(
                        bgmId = 2002L,
                        title = "オッドタクシー",
                        titleCn = "奇巧计程车",
                        weekday = 6,
                        timeCst = "23:30",
                    ),
                )
            repository.sendSchedules(weekday = 6, schedules = saturdaySchedules)
            val viewModel = createViewModel(repository, collectionRepository)

            viewModel.selectWeekday(6)

            val state = viewModel.uiState.first { it.selectedWeekday == 6 && it.schedules == saturdaySchedules }
            assertEquals(6, state.selectedWeekday)
            assertEquals(2002L, state.schedules.single().bgmId)
        }

    @Test
    fun watchingFilterAndTimelineSortingWorkCorrectly() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()

            val fma =
                AirSchedule(
                    bgmId = 101L,
                    title = "钢之炼金术师",
                    titleCn = "钢之炼金术师",
                    weekday = today,
                    timeCst = "23:00",
                    ratingScore = 9.2,
                )
            val frieren =
                AirSchedule(
                    bgmId = 102L,
                    title = "葬送的芙莉莲",
                    titleCn = "葬送的芙莉莲",
                    weekday = today,
                    timeCst = "18:00",
                    ratingScore = 8.8,
                )
            val unknownTime =
                AirSchedule(
                    bgmId = 103L,
                    title = "某部泡面番",
                    titleCn = "某部泡面番",
                    weekday = today,
                    timeCst = "",
                    timeJst = "",
                )

            repository.sendSchedules(weekday = today, schedules = listOf(unknownTime, fma, frieren))

            // 用户正在追 frieren (102L)
            collectionRepository.sendCollection(
                UserCollection(
                    subjectId = 102L,
                    subjectType = 2,
                    type = CollectionType.DOING.value,
                ),
            )

            val viewModel = createViewModel(repository, collectionRepository)

            val initialState = viewModel.uiState.first { it.watchingSubjectIds.contains(102L) }

            // 1. 验证按时间先后排序：18:00 (frieren) -> 23:00 (fma) -> 无时间 (unknownTime)
            assertEquals(3, initialState.currentDaySchedules.size)
            assertEquals(102L, initialState.currentDaySchedules[0].bgmId)
            assertEquals(101L, initialState.currentDaySchedules[1].bgmId)
            assertEquals(103L, initialState.currentDaySchedules[2].bgmId)

            // 验证时间段与全天分组拆分逻辑
            val timedList = initialState.getTimedSchedulesForWeekday(today)
            val allDayList = initialState.getAllDaySchedulesForWeekday(today)
            assertEquals(2, timedList.size)
            assertEquals(1, allDayList.size)
            assertEquals(103L, allDayList.first().bgmId)

            // 2. 验证今日追番 spotlight 推荐
            assertEquals(1, initialState.todayWatchingSchedules.size)
            assertEquals(102L, initialState.todayWatchingSchedules[0].bgmId)
            assertEquals(1, initialState.getWatchingCountForWeekday(today))
            assertEquals(3, initialState.getTotalCountForWeekday(today))

            // 3. 切换为仅看我追的
            viewModel.toggleOnlyWatching()
            val filteredState = viewModel.uiState.first { it.onlyWatching }
            assertEquals(1, filteredState.currentDaySchedules.size)
            assertEquals(102L, filteredState.currentDaySchedules[0].bgmId)
        }

    @Test
    fun toggleWatching_updatesCollectionAndSendsMessage() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()
            val viewModel = createViewModel(repository, collectionRepository)

            // 1. 初始不在看，点击加入追番
            viewModel.toggleWatching(101L)
            assertEquals(1, collectionRepository.updateCollectionCallCount)

            val message = viewModel.userMessage.first()
            assertEquals("已加入在看追番", message)
        }

    @Test
    fun toggleWatching_optimisticallyUpdatesImmediately() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()
            val viewModel = createViewModel(repository, collectionRepository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }

            assertFalse(
                viewModel.uiState.value.watchingSubjectIds
                    .contains(101L),
            )

            // 点击加入追番 -> 乐观更新立即生效
            viewModel.toggleWatching(101L)
            assertTrue(
                viewModel.uiState.value.watchingSubjectIds
                    .contains(101L),
            )

            val message = viewModel.userMessage.first()
            assertEquals("已加入在看追番", message)
            assertTrue(
                viewModel.uiState.value.watchingSubjectIds
                    .contains(101L),
            )
        }

    @Test
    fun toggleWatching_rollsBackOnFailure() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()
            collectionRepository.updateCollectionResult = AppResult.Error(IllegalStateException("网络异常"))
            val viewModel = createViewModel(repository, collectionRepository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }

            assertFalse(
                viewModel.uiState.value.watchingSubjectIds
                    .contains(101L),
            )

            viewModel.toggleWatching(101L)

            val message = viewModel.userMessage.first()
            assertEquals("网络异常", message)
            // 失败后乐观状态已回滚
            assertFalse(
                viewModel.uiState.value.watchingSubjectIds
                    .contains(101L),
            )
        }

    @Test
    fun toggleOnlyWatching_persistsToPreferences() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()
            val viewModel = createViewModel(repository, collectionRepository)

            assertFalse(viewModel.uiState.value.onlyWatching)

            viewModel.toggleOnlyWatching()
            assertTrue(viewModel.uiState.first { it.onlyWatching }.onlyWatching)

            assertTrue(repository.scheduleDefaultOnlyWatching)
        }

    @Test
    fun refreshFailureSetsErrorState() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()
            repository.refreshResult = AppResult.Error(RuntimeException("网络请求失败"))
            val viewModel = createViewModel(repository, collectionRepository)

            val state = viewModel.uiState.first { it.error != null && !it.isLoading }

            assertTrue(state.error!!.contains("网络请求失败"))
            assertFalse(state.isLoading)
            assertFalse(state.isOfflineCache)
            assertTrue(state.schedules.isEmpty())
        }

    @Test
    fun refreshFailureWithExistingCachePreservesDataAndSetsOfflineState() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()
            repository.sendSchedules(weekday = today, schedules = sampleAirScheduleList)
            repository.refreshResult = AppResult.Error(RuntimeException("网络连接超时"))
            val viewModel = createViewModel(repository, collectionRepository)

            val state = viewModel.uiState.first { it.error != null && it.schedules.isNotEmpty() && !it.isLoading }

            assertNotNull(state.error)
            assertTrue(state.error!!.contains("网络连接超时"))
            assertTrue(state.isOfflineCache)
            assertEquals(1, state.schedules.size)
            assertEquals("葬送的芙莉莲", state.schedules.first().titleCn)
        }

    @Test
    fun refreshSuccessClearsPreviousError() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()
            repository.refreshResult = AppResult.Error(RuntimeException("网络请求失败"))
            val viewModel = createViewModel(repository, collectionRepository)
            assertTrue(viewModel.uiState.first { it.error != null }.error != null)

            repository.refreshResult = AppResult.Success(Unit)
            viewModel.refresh()

            val state = viewModel.uiState.first { !it.isLoading && it.error == null }
            assertEquals(2, repository.refreshCallCount)
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertFalse(state.isOfflineCache)
        }

    @Test
    fun timeGroupedSchedules_groupsAnimeByTimeSlotCorrectly() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()

            val anime1 = AirSchedule(bgmId = 1L, title = "A1", titleCn = "A1", weekday = today, timeCst = "23:30")
            val anime2 = AirSchedule(bgmId = 2L, title = "A2", titleCn = "A2", weekday = today, timeCst = "23:30")
            val anime3 = AirSchedule(bgmId = 3L, title = "A3", titleCn = "A3", weekday = today, timeCst = "18:00")

            repository.sendSchedules(weekday = today, schedules = listOf(anime1, anime2, anime3))
            val viewModel = createViewModel(repository, collectionRepository)

            val state = viewModel.uiState.first { it.schedules.size == 3 }
            val grouped = state.getTimeGroupedSchedulesForWeekday(today)

            assertEquals(2, grouped.size)
            assertEquals(1, grouped["18:00"]?.size)
            assertEquals(2, grouped["23:30"]?.size)
            assertEquals(1L, grouped["23:30"]?.get(0)?.bgmId)
            assertEquals(2L, grouped["23:30"]?.get(1)?.bgmId)
        }

    @Test
    fun catchupItems_aggregatesUnwatchedAiredEpisodesFromYesterday() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()

            val yesterday = if (today == 1) 7 else today - 1
            val yesterdayAnime =
                AirSchedule(
                    bgmId = 888L,
                    title = "Dandadan",
                    titleCn = "胆大党",
                    weekday = yesterday,
                    timeCst = "00:30",
                    nextEpisodeNumber = 9,
                )
            repository.sendSchedules(weekday = yesterday, schedules = listOf(yesterdayAnime))

            // 用户正在追 888L，且只打卡到了第 8 话 (落后 1 话)
            collectionRepository.sendCollection(
                UserCollection(
                    subjectId = 888L,
                    type = CollectionType.DOING.value,
                    epStatus = 8,
                ),
            )

            val viewModel = createViewModel(repository, collectionRepository)

            val state = viewModel.uiState.first { it.catchupItems.isNotEmpty() }
            assertEquals(1, state.catchupItems.size)
            val catchup = state.catchupItems.first()
            assertEquals(888L, catchup.schedule.bgmId)
            assertEquals("昨天", catchup.dayLabel)
            assertEquals(8, catchup.epStatus)
            assertEquals(9, catchup.targetEp)
            assertEquals(1, state.yesterdaySchedules.size)
        }

    @Test
    fun markEpisodeWatched_invokesRepositoryAndSendsFeedback() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()
            val viewModel = createViewModel(repository, collectionRepository)

            viewModel.markEpisodeWatched(subjectId = 888L, epNumber = 9)

            assertEquals(1, collectionRepository.updateEpisodeCallCount)
            val message = viewModel.userMessage.first()
            assertEquals("已标记第 9 话已看过", message)
        }
}
