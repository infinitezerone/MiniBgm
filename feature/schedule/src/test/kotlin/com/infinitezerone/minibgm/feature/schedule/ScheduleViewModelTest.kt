package com.infinitezerone.minibgm.feature.schedule

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.testing.data.sampleAirScheduleList
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /**
     * 钉死的"现在"。
     *
     * 排期判定全是窗口比较（≤45 分钟 IMMINENT / >45 分钟 TODAY_UPCOMING / 已过则 TODAY_AIRED），
     * 用真实时钟会让同一份数据在一天中的不同时刻得出不同结论——本文件曾因此在 CI 上按小时
     * 随机失败（本地 CST 通过、CI 的 UTC 挂掉）。固定为 CST 上午 08:00：所有 `timeCst`
     * （18:00 / 20:00）都还在今天之内，结论与运行时刻、运行时区都无关。
     */
    private val fixedNow: Instant = Instant.parse("2026-03-10T00:00:00Z")

    private val cstZone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun cstDate(): LocalDate = fixedNow.atZone(cstZone).toLocalDate()

    private val today = cstDate().dayOfWeek.value

    private fun createViewModel(
        repository: FakeScheduleRepository = FakeScheduleRepository(),
        collectionRepository: FakeCollectionRepository = FakeCollectionRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        authRepository: FakeAuthRepository = FakeAuthRepository(initialLoggedIn = true),
        subjectRepository: FakeSubjectRepository = FakeSubjectRepository(),
    ): ScheduleViewModel =
        ScheduleViewModel(
            scheduleRepository = repository,
            collectionRepository = collectionRepository,
            settingsRepository = settingsRepository,
            authRepository = authRepository,
            subjectRepository = subjectRepository,
            clock = { fixedNow.toEpochMilli() },
        )

    @Test
    fun initTriggersRefreshAndEmitsTodaySchedules() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()
            repository.sendSchedules(weekday = today, schedules = sampleAirScheduleList)
            val viewModel = createViewModel(repository, collectionRepository)

            val state = viewModel.uiState.first { it.schedules.isNotEmpty() && !it.isLoading }

            assertEquals(1, repository.refreshAllCalls)
            assertNull(state.error)
            assertEquals(today, state.selectedWeekday)
            assertEquals(today, state.todayWeekday)
            assertFalse(state.isOfflineCache)
            assertEquals(13, state.dateItems.size)
            assertTrue(state.dateItems[ScheduleViewModel.TODAY_PAGE_INDEX].isToday)
            assertEquals(today, state.dateItems[ScheduleViewModel.TODAY_PAGE_INDEX].weekday)
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
    fun toggleOnlyWatching_whenNotLoggedIn_showsLoginPrompt() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository(initialLoggedIn = false)
            val viewModel =
                createViewModel(
                    repository = repository,
                    collectionRepository = collectionRepository,
                    authRepository = authRepository,
                )

            val initial = viewModel.uiState.first { !it.isLoggedIn }
            assertFalse(initial.isLoggedIn)
            assertFalse(initial.showLoginPromptDialog)

            viewModel.toggleOnlyWatching()

            val promptState = viewModel.uiState.first { it.showLoginPromptDialog }
            assertTrue(promptState.showLoginPromptDialog)
            assertFalse(promptState.onlyWatching)
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
            assertEquals(2, repository.refreshAllCalls)
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

    @Test
    fun watchingSubjectIds_includesBothDoingAndWishCollections() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()

            val doingAnime =
                AirSchedule(
                    bgmId = 101L,
                    title = "在看番",
                    titleCn = "在看番",
                    weekday = today,
                    timeCst = "18:00",
                )
            val wishAnime =
                AirSchedule(
                    bgmId = 102L,
                    title = "想看番",
                    titleCn = "想看番",
                    weekday = today,
                    timeCst = "20:00",
                )
            val droppedAnime =
                AirSchedule(
                    bgmId = 103L,
                    title = "搁置番",
                    titleCn = "搁置番",
                    weekday = today,
                    timeCst = "22:00",
                )

            repository.sendSchedules(weekday = today, schedules = listOf(doingAnime, wishAnime, droppedAnime))

            collectionRepository.sendCollection(
                UserCollection(
                    subjectId = 101L,
                    subjectType = 2,
                    type = CollectionType.DOING.value,
                ),
            )
            collectionRepository.sendCollection(
                UserCollection(
                    subjectId = 102L,
                    subjectType = 2,
                    type = CollectionType.WISH.value,
                ),
            )
            collectionRepository.sendCollection(
                UserCollection(
                    subjectId = 103L,
                    subjectType = 2,
                    type = CollectionType.DROPPED.value,
                ),
            )

            val viewModel = createViewModel(repository, collectionRepository)

            val state =
                viewModel.uiState.first {
                    it.watchingSubjectIds.contains(101L) && it.watchingSubjectIds.contains(102L)
                }

            assertTrue("DOING collection should be in watchingSubjectIds", state.watchingSubjectIds.contains(101L))
            assertTrue("WISH collection should be in watchingSubjectIds", state.watchingSubjectIds.contains(102L))
            assertFalse("DROPPED collection should NOT be in watchingSubjectIds", state.watchingSubjectIds.contains(103L))
            assertEquals(2, state.getWatchingCountForWeekday(today))
            assertEquals(3, state.getTotalCountForWeekday(today))

            viewModel.toggleOnlyWatching()
            val filteredState = viewModel.uiState.first { it.onlyWatching }
            val currentDayIds = filteredState.currentDaySchedules.map { it.bgmId }
            assertTrue(currentDayIds.contains(101L))
            assertTrue(currentDayIds.contains(102L))
            assertFalse(currentDayIds.contains(103L))
        }

    @Test
    fun unauthenticated_toggleWatching_showsLoginPromptDialog() =
        runTest {
            val authRepository = FakeAuthRepository(initialLoggedIn = false)
            val collectionRepository = FakeCollectionRepository()
            val viewModel = createViewModel(collectionRepository = collectionRepository, authRepository = authRepository)

            assertFalse(viewModel.uiState.value.showLoginPromptDialog)

            viewModel.toggleWatching(101L)

            val state = viewModel.uiState.first { it.showLoginPromptDialog }
            assertTrue(state.showLoginPromptDialog)
            assertEquals(0, collectionRepository.updateCollectionCallCount)

            viewModel.dismissLoginPrompt()
            val dismissedState = viewModel.uiState.first { !it.showLoginPromptDialog }
            assertFalse(dismissedState.showLoginPromptDialog)
        }

    @Test
    fun unauthenticated_markEpisodeWatched_showsLoginPromptDialog() =
        runTest {
            val authRepository = FakeAuthRepository(initialLoggedIn = false)
            val collectionRepository = FakeCollectionRepository()
            val viewModel = createViewModel(collectionRepository = collectionRepository, authRepository = authRepository)

            assertFalse(viewModel.uiState.value.showLoginPromptDialog)

            viewModel.markEpisodeWatched(101L, 1)

            val url = viewModel.uiState.first { it.showLoginPromptDialog }
            assertTrue(url.showLoginPromptDialog)
            assertEquals(0, collectionRepository.updateEpisodeCallCount)

            // 登录由独立路由接管（应用内 WebView + ECH 通道），ViewModel 只负责收起提示
            viewModel.dismissLoginPrompt()
            val dismissedState = viewModel.uiState.first { !it.showLoginPromptDialog }
            assertFalse(dismissedState.showLoginPromptDialog)
        }

    @Test
    fun enableAiringReminder_setsSettingsRepositoryTrue() =
        runTest {
            val settingsRepository = FakeSettingsRepository()
            val viewModel = createViewModel(settingsRepository = settingsRepository)

            viewModel.enableAiringReminder()

            assertEquals(1, settingsRepository.setAiringReminderEnabledCallCount)
            assertTrue(settingsRepository.settings.first().airingReminderEnabled)
        }

    @Test
    fun selectPage_switchesScheduleStreamAndUpdatesWeekday() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()
            val viewModel = createViewModel(repository, collectionRepository)

            viewModel.selectPage(0) // 6 days before today

            val state = viewModel.uiState.first { it.selectedPageIndex == 0 }
            assertEquals(0, state.selectedPageIndex)
            val expectedWeekday = state.dateItems[0].weekday
            assertEquals(expectedWeekday, state.selectedWeekday)
        }

    @Test
    fun rolling13Days_mapsAirEventsToSpecificDaysAccurately() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()

            val anime =
                AirSchedule(
                    bgmId = 555L,
                    title = "咒术回战",
                    titleCn = "咒术回战",
                    weekday = today,
                    timeCst = "23:00",
                )
            repository.sendSchedules(weekday = today, schedules = listOf(anime))

            val currentToday = cstDate()
            val yesterdayDateStr = currentToday.minusDays(1).toString()
            val yesterdayEvent =
                com.infinitezerone.minibgm.core.model.AirScheduleEvent(
                    subjectId = 555L,
                    episode = 15,
                    airAtUtc = "${yesterdayDateStr}T15:00:00Z", // 23:00 CST
                )
            repository.sendAirEvents(listOf(yesterdayEvent))

            val viewModel = createViewModel(repository, collectionRepository)

            val yesterdayPageIndex = ScheduleViewModel.TODAY_PAGE_INDEX - 1
            viewModel.selectPage(yesterdayPageIndex)

            val state = viewModel.uiState.first { it.selectedPageIndex == yesterdayPageIndex && it.schedules.isNotEmpty() }
            val scheduleOnYesterday = state.schedules.first()
            assertEquals(555L, scheduleOnYesterday.bgmId)
            assertEquals(15, scheduleOnYesterday.nextEpisodeNumber)
            assertEquals("23:00", scheduleOnYesterday.timeCst)
        }

    @Test
    fun sameDayMultipleEpisodes_displaysAllEpisodesInOrder() =
        runTest {
            val repository = FakeScheduleRepository()
            val collectionRepository = FakeCollectionRepository()

            val baseSchedule =
                AirSchedule(
                    bgmId = 777L,
                    title = "连播动画",
                    titleCn = "连播动画",
                    weekday = today,
                    timeCst = "20:00",
                )
            repository.sendSchedules(weekday = today, schedules = listOf(baseSchedule))

            val currentToday = cstDate()
            val todayDateStr = currentToday.toString()
            val ep1 =
                com.infinitezerone.minibgm.core.model.AirScheduleEvent(
                    subjectId = 777L,
                    episode = 1,
                    airAtUtc = "${todayDateStr}T12:00:00Z",
                )
            val ep2 =
                com.infinitezerone.minibgm.core.model.AirScheduleEvent(
                    subjectId = 777L,
                    episode = 2,
                    airAtUtc = "${todayDateStr}T12:30:00Z",
                )
            repository.sendAirEvents(listOf(ep1, ep2))

            val viewModel = createViewModel(repository, collectionRepository)

            val state =
                viewModel.uiState.first {
                    it.selectedPageIndex == ScheduleViewModel.TODAY_PAGE_INDEX && it.schedules.size == 2
                }
            assertEquals(2, state.schedules.size)
            assertEquals(1, state.schedules[0].nextEpisodeNumber)
            assertEquals("20:00", state.schedules[0].timeCst)
            assertEquals(2, state.schedules[1].nextEpisodeNumber)
            assertEquals("20:30", state.schedules[1].timeCst)
        }
}
