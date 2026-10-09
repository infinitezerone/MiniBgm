package com.infinitezerone.minibgm.feature.search

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SeasonalGuideViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedDate = LocalDate.of(2026, 2, 15) // Q1 Winter 2026

    /** 单元测试无 Android 资源表：用固定文案模拟 labelRes 解析，断言摘要拼装格式。 */
    private fun SeasonalGuideUiState.filterSummaryForTest(
        scopeLabel: String? = null,
        originLabel: String? = null,
        formLabel: String? = null,
    ): String =
        filterSummary(
            scopeLabel = scopeLabel,
            originLabel = originLabel,
            formLabel = formLabel,
            excludedTagLabels = emptyList(),
            purifyLabel = null,
            fallbackLabel = "全部",
        )

    private fun createViewModel(
        searchRepository: FakeSearchRepository = FakeSearchRepository(),
        collectionRepository: FakeCollectionRepository = FakeCollectionRepository(),
        authRepository: FakeAuthRepository = FakeAuthRepository(initialLoggedIn = true),
        scheduleRepository: FakeScheduleRepository = FakeScheduleRepository(),
        settingsRepository: SettingsRepository? = null,
        initialYear: Int = 0,
        initialSeasonMonth: Int = 0,
        timeProvider: () -> LocalDate = { fixedDate },
    ) = SeasonalGuideViewModel(
        searchRepository = searchRepository,
        collectionRepository = collectionRepository,
        authRepository = authRepository,
        scheduleRepository = scheduleRepository,
        settingsRepository = settingsRepository,
        initialYear = initialYear,
        initialSeasonMonth = initialSeasonMonth,
        timeProvider = timeProvider,
    )

    /** 订阅一次性事件流；backgroundScope 在测试结束时会自动取消，也不阻塞 advanceUntilIdle */
    private fun TestScope.collectUiEffects(
        flow: Flow<UiEffect>,
        into: MutableList<UiEffect>,
    ) {
        // Unconfined：注册时立即挂到 receive 上，避免"先 send 后订阅"的时序依赖
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { into += it } }
    }

    @Test
    fun initialState_isLoadingIsTrue_toPreventEmptyPlaceholderFlash() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val gate = CompletableDeferred<Unit>()
            searchRepository.advancedSearchGate = gate
            val viewModel = createViewModel(searchRepository = searchRepository)

            // 初值快照与首次请求在途时必须处于 isLoading = true，确保展示骨架屏而不是空状态占位插画
            assertTrue(viewModel.uiState.value.isLoading)
            assertTrue(
                viewModel.uiState.value.subjects
                    .isEmpty(),
            )

            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun switchingFilter_setsIsLoadingTrueImmediately_avoidingEmptyPlaceholder() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isLoading)

            val gate = CompletableDeferred<Unit>()
            searchRepository.advancedSearchGate = gate
            viewModel.selectOrigin(SeasonOriginFilter.JAPAN)

            // 换挡瞬间必须立即处于 isLoading = true
            assertTrue(viewModel.uiState.value.isLoading)

            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun initialLoadWithDefaultDate_queriesAdvancedSearchWithWinter2026() =
        runTest {
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))
            val viewModel = createViewModel(searchRepository = searchRepository)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2026, state.selectedYear)
            assertEquals(SeasonQuarter.WINTER, state.selectedQuarter)
            assertEquals(SeasonOriginFilter.ALL, state.selectedOrigin)
            assertEquals(SeasonFormFilter.DEFAULT, state.selectedForm)
            assertFalse(state.isLoading)
            assertFalse(state.isRefreshing)
            assertNull(state.error)
            assertEquals(1, state.subjects.size)

            assertEquals(1, searchRepository.advancedSearchCallCount)
            val request = searchRepository.lastAdvancedRequest
            assertEquals("heat", request?.sort)
            assertEquals(listOf(2), request?.filter?.type)
            // 季界在每月 21 日（业界クール口径），冬季窗口因此跨年
            assertEquals(listOf(">=2025-12-21", "<=2026-03-20"), request?.filter?.airDate)
            assertNull(request?.filter?.tag)
        }

    @Test
    fun initialLoadWithCustomParams_queriesSpecifiedYearAndQuarter() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val viewModel =
                createViewModel(
                    searchRepository = searchRepository,
                    initialYear = 2024,
                    initialSeasonMonth = 7, // Summer
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2024, state.selectedYear)
            assertEquals(SeasonQuarter.SUMMER, state.selectedQuarter)

            val request = searchRepository.lastAdvancedRequest
            assertEquals("heat", request?.sort)
            assertEquals(listOf(">=2024-06-21", "<=2024-09-20"), request?.filter?.airDate)
            assertNull(request?.filter?.tag)
        }

    @Test
    fun selectYear_triggersNewSearchWithUpdatedYear() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            viewModel.selectYear(2025)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2025, state.selectedYear)
            assertEquals(2, searchRepository.advancedSearchCallCount)
            val request = searchRepository.lastAdvancedRequest
            assertEquals("heat", request?.sort)
            assertEquals(listOf(">=2024-12-21", "<=2025-03-20"), request?.filter?.airDate)
            assertNull(request?.filter?.tag)
        }

    @Test
    fun selectQuarter_triggersNewSearchWithUpdatedQuarter() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            viewModel.selectQuarter(SeasonQuarter.AUTUMN)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(SeasonQuarter.AUTUMN, state.selectedQuarter)
            assertEquals(2, searchRepository.advancedSearchCallCount)
            val request = searchRepository.lastAdvancedRequest
            assertEquals("heat", request?.sort)
            assertEquals(listOf(">=2026-09-21", "<=2026-12-20"), request?.filter?.airDate)
            assertNull(request?.filter?.tag)
        }

    @Test
    fun selectSeason_triggersSingleSearchWithUpdatedYearAndQuarter() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            assertEquals(1, searchRepository.advancedSearchCallCount)

            viewModel.selectSeason(2025, SeasonQuarter.SUMMER)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2025, state.selectedYear)
            assertEquals(SeasonQuarter.SUMMER, state.selectedQuarter)
            assertEquals(2, searchRepository.advancedSearchCallCount)
            val request = searchRepository.lastAdvancedRequest
            assertEquals("heat", request?.sort)
            assertEquals(listOf(">=2025-06-21", "<=2025-09-20"), request?.filter?.airDate)
            assertNull(request?.filter?.tag)
        }

    @Test
    fun selectOrigin_pushesMetaTagToServerAndRefetchesFromStart() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            assertEquals(1, searchRepository.advancedSearchCallCount)
            assertNull(searchRepository.lastAdvancedRequest?.filter?.metaTags)

            viewModel.selectOrigin(SeasonOriginFilter.CHINA)
            advanceUntilIdle()

            // 产地必须下推服务端并重新取数：只筛"已加载的那几十条"会显示成假的小数字
            // （当季国产实际 53 部，而首屏只有 20 条）
            assertEquals(SeasonOriginFilter.CHINA, viewModel.uiState.value.selectedOrigin)
            assertEquals(2, searchRepository.advancedSearchCallCount)
            assertEquals(listOf("中国"), searchRepository.lastAdvancedRequest?.filter?.metaTags)
            assertEquals(0, searchRepository.lastAdvancedOffset)
        }

    @Test
    fun selectSort_pushesServerSideSortAndRefetchesFromStart() =
        runTest {
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            // 默认热度
            assertEquals("heat", searchRepository.lastAdvancedRequest?.sort)

            viewModel.selectSort(SeasonSortOption.SCORE)
            advanceUntilIdle()

            // 排序是服务端能力：直接下推，且换挡后从 offset 0 重建
            assertEquals("score", searchRepository.lastAdvancedRequest?.sort)
            assertEquals(0, searchRepository.lastAdvancedOffset)
            assertEquals(SeasonSortOption.SCORE, viewModel.uiState.value.selectedSort)
        }

    @Test
    fun selectForm_pushesMetaTagToServerAndRefetches() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            assertEquals(SeasonFormFilter.ALL, viewModel.uiState.value.selectedForm)
            assertNull(searchRepository.lastAdvancedRequest?.filter?.metaTags)

            viewModel.selectForm(SeasonFormFilter.MOVIE)
            advanceUntilIdle()

            assertEquals(SeasonFormFilter.MOVIE, viewModel.uiState.value.selectedForm)
            assertEquals(listOf("剧场版"), searchRepository.lastAdvancedRequest?.filter?.metaTags)
            assertEquals(2, searchRepository.advancedSearchCallCount)

            viewModel.selectForm(SeasonFormFilter.ALL)
            advanceUntilIdle()

            assertEquals(SeasonFormFilter.ALL, viewModel.uiState.value.selectedForm)
            assertNull(searchRepository.lastAdvancedRequest?.filter?.metaTags)
            assertEquals(3, searchRepository.advancedSearchCallCount)
        }

    @Test
    fun selectOriginAndForm_combinesAsAndInServerRequest() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            viewModel.selectOrigin(SeasonOriginFilter.JAPAN)
            advanceUntilIdle()
            assertEquals(listOf("日本"), searchRepository.lastAdvancedRequest?.filter?.metaTags)

            viewModel.selectForm(SeasonFormFilter.MOVIE)
            advanceUntilIdle()
            assertEquals(listOf("日本", "剧场版"), searchRepository.lastAdvancedRequest?.filter?.metaTags)
        }

    @Test
    fun filterSummary_readsOutCurrentSelectionForTheCollapsedBar() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals("全部", viewModel.uiState.value.filterSummaryForTest())

            viewModel.selectOrigin(SeasonOriginFilter.JAPAN)
            advanceUntilIdle()
            assertEquals("日本", viewModel.uiState.value.filterSummaryForTest(originLabel = "日本"))

            viewModel.selectForm(SeasonFormFilter.MOVIE)
            advanceUntilIdle()
            assertEquals("日本 · 剧场版", viewModel.uiState.value.filterSummaryForTest(originLabel = "日本", formLabel = "剧场版"))

            viewModel.selectOrigin(SeasonOriginFilter.ALL)
            advanceUntilIdle()
            assertEquals("剧场版", viewModel.uiState.value.filterSummaryForTest(formLabel = "剧场版"))
        }

    @Test
    fun switchingFilter_midRequest_cancelsTheStaleSessionAndRebuildsFromScratch() =
        runTest {
            // 竞态处理交给 flatMapLatest：查询一变，旧查询的分页链整条取消、游标归零。
            // 若沿用旧游标继续翻，得到的就是"新条件下第 21~40 条"这种错位结果。
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchPages =
                mapOf(0 to List(20) { sampleSubject.copy(id = it.toLong() + 1) })
            searchRepository.advancedSearchTotal = 100

            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()
            assertEquals(20, viewModel.uiState.value.subjects.size)

            // 让翻页请求停在半路
            val gate = CompletableDeferred<Unit>()
            searchRepository.advancedSearchGate = gate
            viewModel.loadMore()
            assertTrue("loadMore 后应处于 isLoadingMore", viewModel.uiState.value.isLoadingMore)

            // 请求还在飞时换产地：旧会话被取消，新查询从 offset 0 重建
            viewModel.selectOrigin(SeasonOriginFilter.JAPAN)
            advanceUntilIdle()
            assertEquals("换产地后应以新条件下推", listOf("日本"), searchRepository.lastAdvancedRequest?.filter?.metaTags)
            assertEquals("换产地必须从 offset 0 重建", 0, searchRepository.lastAdvancedOffset)
            assertFalse("进行中标记位应复位", viewModel.uiState.value.isLoadingMore)

            gate.complete(Unit)
            advanceUntilIdle()

            // 原始条目照旧留在 state 里（过滤不参与游标推进），但按新条件可见的为零
            assertEquals("原始条目照旧留在 state", 20, viewModel.uiState.value.subjects.size)
            // 旧会话的 offset=20 结果没有并进来：游标停在"从零重建"后的 20，而不是累加到 40。
            // （filteredSubjects 对日本/国产不做客户端复筛——信任服务端已按 meta_tags 筛过，
            //   而测试替身不模拟这层，所以这里断言游标而不是断言可见条目数。）
            assertEquals("旧会话的结果不应并入新查询", 20, viewModel.uiState.value.pageOffset)
            assertFalse(viewModel.uiState.value.isLoadingMore)
        }

    @Test
    fun toggleCollection_whenNotLoggedIn_showsLoginPrompt() =
        runTest {
            val authRepository = FakeAuthRepository(initialLoggedIn = false)
            val collectionRepository = FakeCollectionRepository()
            val viewModel =
                createViewModel(
                    collectionRepository = collectionRepository,
                    authRepository = authRepository,
                )
            advanceUntilIdle()

            viewModel.toggleCollection(100L, CollectionType.DOING)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.showLoginPromptDialog)
            assertEquals(0, collectionRepository.updateCollectionCallCount)
        }

    @Test
    fun toggleCollection_whenLoggedIn_persistsAndReflectsThroughRepositoryStream() =
        runTest {
            val collectionRepository = FakeCollectionRepository()
            val viewModel = createViewModel(collectionRepository = collectionRepository)
            advanceUntilIdle()
            val effects = mutableListOf<UiEffect>()
            collectUiEffects(viewModel.uiEffects, effects)

            viewModel.toggleCollection(100L, CollectionType.DOING)
            advanceUntilIdle()

            // 收藏集合是仓库流（Room）combine 进来的投影：写入后流自己重发，UI 随之更新。
            // 不再手写乐观值 + 失败回滚——那等于维护第二份事实源（方案 B 的"外部源同步"）。
            assertTrue(
                viewModel.uiState.value.doingSubjectIds
                    .contains(100L),
            )
            assertEquals(1, collectionRepository.updateCollectionCallCount)
            // 成功不给 toast：列表里那一条的状态变化本身就是反馈
            assertTrue("成功不应发一次性事件", effects.isEmpty())
        }

    @Test
    fun toggleCollection_whenError_surfacesTheMessageWithoutTouchingTheList() =
        runTest {
            val collectionRepository = FakeCollectionRepository()
            collectionRepository.updateCollectionResult = AppResult.Error(Exception("Network failure"), "网络异常")
            val viewModel = createViewModel(collectionRepository = collectionRepository)
            advanceUntilIdle()
            val effects = mutableListOf<UiEffect>()
            collectUiEffects(viewModel.uiEffects, effects)

            viewModel.toggleCollection(100L, CollectionType.WISH)
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.wishedSubjectIds
                    .contains(100L),
            )
            assertEquals("网络异常", (effects.single() as UiEffect.ShowMessage).text)
        }

    @Test
    fun refresh_reloadsCurrentSeasonAndQuarter() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            assertEquals(1, searchRepository.advancedSearchCallCount)

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(2, searchRepository.advancedSearchCallCount)
            assertFalse(viewModel.uiState.value.isRefreshing)
        }

    @Test
    fun refresh_whenFailure_preservesExistingSubjectsAndSurfacesErrorToast() =
        runTest {
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject.copy(id = 100L)))
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.subjects.size)
            assertEquals(
                100L,
                viewModel.uiState.value.subjects
                    .first()
                    .id,
            )

            val effects = mutableListOf<UiEffect>()
            collectUiEffects(viewModel.uiEffects, effects)

            searchRepository.advancedSearchResult = AppResult.Error(Exception("Network down"), "网络开小差了")
            viewModel.refresh()
            advanceUntilIdle()

            // 非破坏性刷新：失败时不应清空原列表，不进入全屏错误态，而是发出提示
            assertEquals(1, viewModel.uiState.value.subjects.size)
            assertEquals(
                100L,
                viewModel.uiState.value.subjects
                    .first()
                    .id,
            )
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertNull(viewModel.uiState.value.error)
            assertEquals("网络开小差了", (effects.single() as UiEffect.ShowMessage).text)
        }

    @Test
    fun loadMore_usesServerCursorAndStopsAtServerTotal() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val firstBatch = List(20) { sampleSubject.copy(id = it.toLong() + 1) }
            val secondBatch = List(10) { sampleSubject.copy(id = it.toLong() + 21) }

            searchRepository.advancedSearchResult = AppResult.Success(firstBatch)
            // 服务端共 30 条，首页 20 条后仍有下一页
            searchRepository.advancedSearchTotal = 30
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            assertEquals(20, viewModel.uiState.value.subjects.size)
            assertTrue(viewModel.uiState.value.hasMore)
            // 单页宽度必须是 20：传更大只会被服务端静默截断，导致"满页即还有"的判定永远为假
            assertEquals(20, searchRepository.lastAdvancedLimit)
            assertEquals(0, searchRepository.lastAdvancedOffset)

            searchRepository.advancedSearchResult = AppResult.Success(secondBatch)
            viewModel.loadMore()
            advanceUntilIdle()

            assertEquals(30, viewModel.uiState.value.subjects.size)
            // 翻页 offset 取服务端游标而非已加载条数，去重丢弃条目后也不会错位
            assertEquals(20, searchRepository.lastAdvancedOffset)
            assertFalse(viewModel.uiState.value.hasMore)
        }

    @Test
    fun firstPage_marksNoMoreAsSoonAsServerTotalIsCovered() =
        runTest {
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult = AppResult.Success(List(20) { sampleSubject.copy(id = it.toLong() + 1) })
            searchRepository.advancedSearchTotal = 20
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            assertEquals(20, viewModel.uiState.value.subjects.size)
            assertFalse(viewModel.uiState.value.hasMore)
        }

    @Test
    fun toggleCollection_whenSwitchingFromDoingToWish_andErrorOccurs_leavesRepositoryStateUntouched() =
        runTest {
            val collectionRepository = FakeCollectionRepository()
            collectionRepository.sendCollection(UserCollection(subjectId = 100L, type = CollectionType.DOING.value))
            collectionRepository.updateCollectionResult = AppResult.Error(Exception("Network error"), "网络异常")
            val viewModel = createViewModel(collectionRepository = collectionRepository)
            advanceUntilIdle()
            val effects = mutableListOf<UiEffect>()
            collectUiEffects(viewModel.uiEffects, effects)

            assertTrue(
                viewModel.uiState.value.doingSubjectIds
                    .contains(100L),
            )
            assertFalse(
                viewModel.uiState.value.wishedSubjectIds
                    .contains(100L),
            )

            viewModel.toggleCollection(100L, CollectionType.WISH)
            advanceUntilIdle()

            // On error, 100L must be restored back into doingSubjectIds and removed from wishedSubjectIds
            assertTrue(
                viewModel.uiState.value.doingSubjectIds
                    .contains(100L),
            )
            assertFalse(
                viewModel.uiState.value.wishedSubjectIds
                    .contains(100L),
            )
            assertEquals("网络异常", (effects.single() as UiEffect.ShowMessage).text)
        }

    @Test
    fun toggleCollection_whenSwitchingFromWishToDoing_andErrorOccurs_leavesRepositoryStateUntouched() =
        runTest {
            val collectionRepository = FakeCollectionRepository()
            collectionRepository.sendCollection(UserCollection(subjectId = 200L, type = CollectionType.WISH.value))
            collectionRepository.updateCollectionResult = AppResult.Error(Exception("Server error"), "服务器开小差了")
            val viewModel = createViewModel(collectionRepository = collectionRepository)
            advanceUntilIdle()
            val effects = mutableListOf<UiEffect>()
            collectUiEffects(viewModel.uiEffects, effects)

            assertTrue(
                viewModel.uiState.value.wishedSubjectIds
                    .contains(200L),
            )
            assertFalse(
                viewModel.uiState.value.doingSubjectIds
                    .contains(200L),
            )

            viewModel.toggleCollection(200L, CollectionType.DOING)
            advanceUntilIdle()

            // On error, 200L must be restored back into wishedSubjectIds and removed from doingSubjectIds
            assertTrue(
                viewModel.uiState.value.wishedSubjectIds
                    .contains(200L),
            )
            assertFalse(
                viewModel.uiState.value.doingSubjectIds
                    .contains(200L),
            )
            assertEquals("服务器开小差了", (effects.single() as UiEffect.ShowMessage).text)
        }

    @Test
    fun toggleCollection_whenAlreadyInTargetCollection_emitsPromptWithoutCallingRepository() =
        runTest {
            val collectionRepository = FakeCollectionRepository()
            collectionRepository.sendCollection(UserCollection(subjectId = 300L, type = CollectionType.DOING.value))
            val viewModel = createViewModel(collectionRepository = collectionRepository)
            advanceUntilIdle()
            val effects = mutableListOf<UiEffect>()
            collectUiEffects(viewModel.uiEffects, effects)

            viewModel.toggleCollection(300L, CollectionType.DOING)
            advanceUntilIdle()

            assertEquals(0, collectionRepository.updateCollectionCallCount)
            assertEquals("effects 个数", 1, effects.size)
        }

    @Test
    fun quarterBoundary_followsIndustryCourNotCalendarMonth() {
        // 业界按「クール」分季，季界在每月 21 日：秋番可以从 9 月下旬开播
        // （葬送的芙莉莲 2023-09-29 是公认的 2023 秋番），夏番可以从 6 月下旬开播。
        // 用日历月末当季界会把前者判成夏番、把后者判成春番。
        assertEquals(SeasonQuarter.SUMMER, SeasonQuarter.fromDate(LocalDate.of(2026, 9, 20)))
        assertEquals(SeasonQuarter.AUTUMN, SeasonQuarter.fromDate(LocalDate.of(2026, 9, 21)))
        assertEquals(SeasonQuarter.AUTUMN, SeasonQuarter.fromDate(LocalDate.of(2026, 9, 29)))

        assertEquals(SeasonQuarter.SPRING, SeasonQuarter.fromDate(LocalDate.of(2026, 6, 20)))
        assertEquals(SeasonQuarter.SUMMER, SeasonQuarter.fromDate(LocalDate.of(2026, 6, 21)))
        assertEquals(SeasonQuarter.SUMMER, SeasonQuarter.fromDate(LocalDate.of(2026, 6, 30)))

        // 冬季窗口跨年：12 月下旬属于**次年**冬季
        assertEquals(SeasonQuarter.AUTUMN, SeasonQuarter.fromDate(LocalDate.of(2026, 12, 20)))
        assertEquals(SeasonQuarter.WINTER, SeasonQuarter.fromDate(LocalDate.of(2026, 12, 21)))
        assertEquals(2026, SeasonQuarter.seasonYearOf(LocalDate.of(2026, 12, 20)))
        assertEquals(2027, SeasonQuarter.seasonYearOf(LocalDate.of(2026, 12, 21)))
    }

    @Test
    fun airDateRange_coversWholeYearWithoutOverlapOrGap() {
        assertEquals("2025-12-21" to "2026-03-20", SeasonQuarter.WINTER.getAirDateRange(2026))
        assertEquals("2026-03-21" to "2026-06-20", SeasonQuarter.SPRING.getAirDateRange(2026))
        assertEquals("2026-06-21" to "2026-09-20", SeasonQuarter.SUMMER.getAirDateRange(2026))
        assertEquals("2026-09-21" to "2026-12-20", SeasonQuarter.AUTUMN.getAirDateRange(2026))
    }

    @Test
    fun currentSeason_isDerivedFromTheCourBoundaryNotTheCalendarMonth() =
        runTest {
            // 9/29 按业界口径已经是**秋季**（秋番自 9 月下旬开播），旧实现会算成夏季。
            // 这直接决定进页面时默认落在哪一档。
            val viewModel = createViewModel(timeProvider = { LocalDate.of(2026, 9, 29) })
            advanceUntilIdle()

            assertEquals(SeasonQuarter.AUTUMN, viewModel.uiState.value.selectedQuarter)
            assertEquals(SeasonQuarter.AUTUMN, viewModel.uiState.value.currentQuarter)
            assertEquals(2026, viewModel.uiState.value.currentYear)
        }

    @Test
    fun viewMode_defaultsToCompactListAndTogglesBothWays() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // 默认紧凑列表：导视的首要诉求是"一季有哪些番"，行式一屏约 6-7 条且带集数／电视台／题材
            assertEquals(SeasonalViewMode.LIST, viewModel.uiState.value.viewMode)

            viewModel.toggleViewMode()
            assertEquals(SeasonalViewMode.POSTER, viewModel.uiState.value.viewMode)

            viewModel.toggleViewMode()
            assertEquals(SeasonalViewMode.LIST, viewModel.uiState.value.viewMode)
        }

    @Test
    fun toggleViewMode_keepsLoadedDataAndDoesNotRefetch() =
        runTest {
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))
            searchRepository.advancedSearchTotal = 1

            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()
            val callsBeforeToggle = searchRepository.advancedSearchCallCount

            viewModel.toggleViewMode()
            advanceUntilIdle()

            // 视图形态是纯展示偏好：条目、翻页游标与是否还有下一页都不应变，也不该再打一次网络
            assertEquals(
                listOf(sampleSubject.id),
                viewModel.uiState.value.subjects
                    .map { it.id },
            )
            assertEquals(callsBeforeToggle, searchRepository.advancedSearchCallCount)
            assertFalse(viewModel.uiState.value.hasMore)
        }

    @Test
    fun tagFilter_togglesTagAndPushesToSearchFilter() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            val initialCalls = searchRepository.advancedSearchCallCount

            viewModel.toggleTag("百合")
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.selectedTags
                    .contains("百合"),
            )
            assertEquals(listOf("百合"), searchRepository.lastAdvancedRequest?.filter?.tag)
            assertEquals(initialCalls + 1, searchRepository.advancedSearchCallCount)

            viewModel.toggleTag("百合")
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.selectedTags
                    .contains("百合"),
            )
            assertNull(searchRepository.lastAdvancedRequest?.filter?.tag)
        }

    @Test
    fun filterTags_addAndRemoveViaViewModel() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            viewModel.addCustomFilterTag("Mecha")
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.selectedTags
                    .contains("Mecha"),
            )

            viewModel.removeCustomFilterTag("Mecha")
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.selectedTags
                    .contains("Mecha"),
            )
        }

    @Test
    fun purifyContent_groupsNoiseItemsIntoFoldedCapsules() =
        runTest {
            val normalSubject = sampleSubject.copy(id = 101, name = "正片动画")
            val noiseSubject1 =
                sampleSubject.copy(
                    id = 102,
                    name = "泡面短剧",
                    tags =
                        listOf(
                            com.infinitezerone.minibgm.core.model
                                .Tag(name = "泡面番", count = 99),
                        ),
                )
            val noiseSubject2 =
                sampleSubject.copy(
                    id = 103,
                    name = "音乐映像",
                    metaTags = listOf("MV"),
                )
            val normalSubject2 = sampleSubject.copy(id = 104, name = "另一部正片")

            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult =
                AppResult.Success(listOf(normalSubject, noiseSubject1, noiseSubject2, normalSubject2))
            searchRepository.advancedSearchTotal = 4

            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.purifyContent)
            // 应该聚合为：Anime(101), FoldedGroup(102, [102, 103]), Anime(104) -> 3 个展示单元
            assertEquals(3, state.displayItems.size)
            assertTrue(state.displayItems[0] is SeasonalDisplayItem.Anime)
            assertTrue(state.displayItems[1] is SeasonalDisplayItem.FoldedGroup)
            assertTrue(state.displayItems[2] is SeasonalDisplayItem.Anime)

            val folded = state.displayItems[1] as SeasonalDisplayItem.FoldedGroup
            assertEquals("102", folded.groupKey)
            assertEquals(2, folded.subjects.size)
            assertFalse(folded.isExpanded)

            // 就地展开
            viewModel.toggleFoldedGroup("102")
            val expandedState = viewModel.uiState.value
            // 展开后：Anime(101), FoldedGroup(102, isExpanded=true), Anime(102), Anime(103), Anime(104) -> 5 个展示单元
            assertEquals(5, expandedState.displayItems.size)
            assertTrue((expandedState.displayItems[1] as SeasonalDisplayItem.FoldedGroup).isExpanded)

            // 关闭净化开关：全部平铺为 4 个常规 Anime
            viewModel.togglePurifyContent()
            val unpurifiedState = viewModel.uiState.value
            assertFalse(unpurifiedState.purifyContent)
            assertEquals(4, unpurifiedState.displayItems.size)
            assertTrue(unpurifiedState.displayItems.all { it is SeasonalDisplayItem.Anime })
        }

    @Test
    fun currentSeason_ongoingAnimeFromSchedule_mergedAndRankedWithSeasonalNew() =
        runTest {
            val newAnime =
                sampleSubject.copy(
                    id = 101,
                    name = "冬季新番",
                    airDate = "2026-01-10",
                    rating =
                        com.infinitezerone.minibgm.core.model
                            .Rating(score = 7.5),
                )
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(newAnime))
            searchRepository.advancedSearchTotal = 1

            val continuingAnime =
                sampleSubject.copy(
                    id = 201,
                    name = "秋季半年番续播",
                    airDate = "2025-10-05",
                    rating =
                        com.infinitezerone.minibgm.core.model
                            .Rating(score = 8.8),
                )
            val scheduleRepository = FakeScheduleRepository()
            scheduleRepository.seasonalAnimeListResult = listOf(newAnime, continuingAnime)
            val continuingSchedule =
                AirSchedule(
                    bgmId = 201,
                    title = "秋季半年番续播",
                    titleCn = "秋季半年番续播",
                    airDate = "2025-10-05",
                    ratingScore = 8.8,
                    nextEpisodeNumber = 16,
                    weekday = 1,
                )
            scheduleRepository.sendSchedules(1, listOf(continuingSchedule))

            val viewModel =
                createViewModel(
                    searchRepository = searchRepository,
                    scheduleRepository = scheduleRepository,
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.isCurrentSeason)
            assertEquals(2, state.subjects.size)
            assertEquals(16, state.continuingNextEpisodes[201L])

            // 切换为 SCORE 高分优先：8.8 分的跨季番排在 7.5 分的新番前面
            viewModel.selectSort(SeasonSortOption.SCORE)
            val scoreState = viewModel.uiState.value
            assertEquals(201L, scoreState.subjects[0].id)
            assertEquals(101L, scoreState.subjects[1].id)
        }

    @Test
    fun selectSort_multipleSortingOptions_sortCorrectly() =
        runTest {
            val animeEarly =
                sampleSubject.copy(
                    id = 101L,
                    name = "Alpha",
                    nameCn = "阿尔法",
                    airDate = "2026-01-05",
                )
            val animeLate =
                sampleSubject.copy(
                    id = 102L,
                    name = "Beta",
                    nameCn = "贝塔",
                    airDate = "2026-01-25",
                )
            val scheduleRepository = FakeScheduleRepository()
            scheduleRepository.seasonalAnimeListResult = listOf(animeEarly, animeLate)

            val viewModel = createViewModel(scheduleRepository = scheduleRepository)
            advanceUntilIdle()

            // 开播时间：新到旧
            viewModel.selectSort(SeasonSortOption.AIR_DATE_DESC)
            val descState = viewModel.uiState.value
            assertEquals(102L, descState.subjects[0].id)
            assertEquals(101L, descState.subjects[1].id)

            // 开播时间：早到晚
            viewModel.selectSort(SeasonSortOption.AIR_DATE_ASC)
            val ascState = viewModel.uiState.value
            assertEquals(101L, ascState.subjects[0].id)
            assertEquals(102L, ascState.subjects[1].id)

            // 标题 A-Z (阿尔法 -> 贝塔)
            viewModel.selectSort(SeasonSortOption.TITLE)
            val titleState = viewModel.uiState.value
            assertEquals(101L, titleState.subjects[0].id)
            assertEquals(102L, titleState.subjects[1].id)
        }

    @Test
    fun originFilter_chineseAnime_matchesByCountryOfOriginOrMetaTagFallback() =
        runTest {
            val jpAnime =
                sampleSubject.copy(
                    id = 101L,
                    name = "JP Anime",
                    countryOfOrigin = "JP",
                    metaTags = listOf("TV", "日本"),
                )
            val cnAnimeCode =
                sampleSubject.copy(
                    id = 102L,
                    name = "CN Anime Code",
                    countryOfOrigin = "CN",
                    metaTags = listOf("WEB"),
                )
            val cnAnimeFallback =
                sampleSubject.copy(
                    id = 103L,
                    name = "CN Anime Fallback",
                    countryOfOrigin = "",
                    metaTags = listOf("WEB", "中国"),
                )
            val scheduleRepository = FakeScheduleRepository()
            scheduleRepository.seasonalAnimeListResult = listOf(jpAnime, cnAnimeCode, cnAnimeFallback)

            val viewModel = createViewModel(scheduleRepository = scheduleRepository)
            advanceUntilIdle()

            // 筛选国产：同时匹配 countryOfOrigin=CN 或 metaTag="中国" 的条目
            viewModel.selectOrigin(SeasonOriginFilter.CHINA)
            val cnState = viewModel.uiState.value
            assertEquals(2, cnState.subjects.size)
            assertTrue(cnState.subjects.any { it.id == 102L })
            assertTrue(cnState.subjects.any { it.id == 103L })

            // 筛选日本：排除国创条目
            viewModel.selectOrigin(SeasonOriginFilter.JAPAN)
            val jpState = viewModel.uiState.value
            assertEquals(1, jpState.subjects.size)
            assertEquals(101L, jpState.subjects[0].id)
        }

    @Test
    fun formFilter_matchesMultipleFormatsCorrectly() =
        runTest {
            val tvAnime =
                sampleSubject.copy(
                    id = 101L,
                    name = "TV Anime",
                    platform = "TV",
                )
            val movieAnime =
                sampleSubject.copy(
                    id = 102L,
                    name = "Movie Anime",
                    platform = "MOVIE",
                )
            val webAnime =
                sampleSubject.copy(
                    id = 103L,
                    name = "Web Anime",
                    platform = "WEB",
                )
            val ovaAnime =
                sampleSubject.copy(
                    id = 104L,
                    name = "OVA Anime",
                    platform = "OVA",
                )
            val scheduleRepository = FakeScheduleRepository()
            scheduleRepository.seasonalAnimeListResult = listOf(tvAnime, movieAnime, webAnime, ovaAnime)

            val viewModel = createViewModel(scheduleRepository = scheduleRepository)
            advanceUntilIdle()

            viewModel.selectForm(SeasonFormFilter.TV)
            assertEquals(1, viewModel.uiState.value.subjects.size)
            assertEquals(
                101L,
                viewModel.uiState.value.subjects[0]
                    .id,
            )

            viewModel.selectForm(SeasonFormFilter.MOVIE)
            assertEquals(1, viewModel.uiState.value.subjects.size)
            assertEquals(
                102L,
                viewModel.uiState.value.subjects[0]
                    .id,
            )

            viewModel.selectForm(SeasonFormFilter.WEB)
            assertEquals(1, viewModel.uiState.value.subjects.size)
            assertEquals(
                103L,
                viewModel.uiState.value.subjects[0]
                    .id,
            )

            viewModel.selectForm(SeasonFormFilter.OVA)
            assertEquals(1, viewModel.uiState.value.subjects.size)
            assertEquals(
                104L,
                viewModel.uiState.value.subjects[0]
                    .id,
            )

            viewModel.selectForm(SeasonFormFilter.ALL)
            assertEquals(4, viewModel.uiState.value.subjects.size)
        }

    @Test
    fun airingScopeFilter_switchesBetweenAll_newOnly_continuingOnly() =
        runTest {
            val newAnime =
                sampleSubject.copy(
                    id = 101,
                    name = "冬季新番",
                    airDate = "2026-01-10",
                    rating =
                        com.infinitezerone.minibgm.core.model
                            .Rating(score = 7.0),
                )
            val continuingAnime =
                sampleSubject.copy(
                    id = 201,
                    name = "跨季在播番",
                    airDate = "2025-10-05",
                    rating =
                        com.infinitezerone.minibgm.core.model
                            .Rating(score = 8.0),
                )
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(newAnime))
            searchRepository.advancedSearchTotal = 1

            val scheduleRepository = FakeScheduleRepository()
            scheduleRepository.seasonalAnimeListResult = listOf(newAnime, continuingAnime)
            val continuingSchedule =
                AirSchedule(
                    bgmId = 201,
                    title = "跨季在播番",
                    titleCn = "跨季在播番",
                    airDate = "2025-10-05",
                    ratingScore = 8.0,
                    nextEpisodeNumber = 14,
                    weekday = 2,
                )
            scheduleRepository.sendSchedules(2, listOf(continuingSchedule))

            val viewModel =
                createViewModel(
                    searchRepository = searchRepository,
                    scheduleRepository = scheduleRepository,
                )
            advanceUntilIdle()

            // 默认全部在播
            assertEquals(SeasonAiringScope.ALL, viewModel.uiState.value.selectedAiringScope)
            assertEquals(2, viewModel.uiState.value.subjects.size)

            // 切换仅首播新番
            viewModel.selectAiringScope(SeasonAiringScope.NEW_ONLY)
            val newOnlyState = viewModel.uiState.value
            assertEquals(SeasonAiringScope.NEW_ONLY, newOnlyState.selectedAiringScope)
            assertEquals(listOf(101L), newOnlyState.subjects.map { it.id })

            // 切换仅跨季续播
            viewModel.selectAiringScope(SeasonAiringScope.CONTINUING_ONLY)
            val continuingOnlyState = viewModel.uiState.value
            assertEquals(SeasonAiringScope.CONTINUING_ONLY, continuingOnlyState.selectedAiringScope)
            assertEquals(listOf(201L), continuingOnlyState.subjects.map { it.id })

            // 切回全部在播
            viewModel.selectAiringScope(SeasonAiringScope.ALL)
            val allState = viewModel.uiState.value
            assertEquals(2, allState.subjects.size)
        }

    @Test
    fun aniListPrimary_doesNotAppendRemoteSearchAdditions() =
        runTest {
            val aniListSubject =
                sampleSubject.copy(
                    id = 101,
                    name = "AniList季度新番",
                    airDate = "2026-01-10",
                )
            val extraneousRemoteSubject =
                sampleSubject.copy(
                    id = 999,
                    name = "网络搜索多余条目",
                    airDate = "2026-01-15",
                )
            val searchRepository = FakeSearchRepository()
            // 模拟服务端搜索返回了 AniList 中没有的额外条目
            searchRepository.advancedSearchResult = AppResult.Success(listOf(aniListSubject, extraneousRemoteSubject))
            searchRepository.advancedSearchTotal = 2

            val scheduleRepository = FakeScheduleRepository()
            scheduleRepository.seasonalAnimeListResult = listOf(aniListSubject)

            val viewModel =
                createViewModel(
                    searchRepository = searchRepository,
                    scheduleRepository = scheduleRepository,
                )
            advanceUntilIdle()

            // AniList 季度快照作为单一事实源时，不追加网络搜索多余条目
            val state = viewModel.uiState.value
            assertEquals(1, state.subjects.size)
            assertEquals(101L, state.subjects[0].id)
        }

    @Test
    fun switchingSeason_resetsAiringScopeToAll() =
        runTest {
            val anime =
                sampleSubject.copy(
                    id = 101,
                    name = "冬番",
                    airDate = "2026-01-10",
                )
            val scheduleRepository = FakeScheduleRepository()
            scheduleRepository.seasonalAnimeListResult = listOf(anime)

            val viewModel =
                createViewModel(
                    scheduleRepository = scheduleRepository,
                )
            advanceUntilIdle()

            viewModel.selectAiringScope(SeasonAiringScope.CONTINUING_ONLY)
            assertEquals(SeasonAiringScope.CONTINUING_ONLY, viewModel.uiState.value.selectedAiringScope)

            // 切换季度应重置 airingScope 为 ALL
            viewModel.selectSeason(2025, SeasonQuarter.AUTUMN)
            advanceUntilIdle()
            assertEquals(SeasonAiringScope.ALL, viewModel.uiState.value.selectedAiringScope)
        }

    @Test
    fun pastSeason_doesNotIncludeOngoingSchedules() =
        runTest {
            val pastAnime =
                sampleSubject.copy(
                    id = 99,
                    name = "2025夏季番",
                    airDate = "2025-07-10",
                    rating =
                        com.infinitezerone.minibgm.core.model
                            .Rating(score = 7.2),
                )
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(pastAnime))
            searchRepository.advancedSearchTotal = 1

            val scheduleRepository = FakeScheduleRepository()
            val continuingSchedule =
                AirSchedule(
                    bgmId = 201,
                    title = "当前排期条目",
                    titleCn = "当前排期条目",
                    airDate = "2025-01-05",
                    ratingScore = 8.5,
                    nextEpisodeNumber = 20,
                    weekday = 1,
                )
            scheduleRepository.sendSchedules(1, listOf(continuingSchedule))

            // 指定历史季度 2025 年 7 月（非当前季度 2026 Q1）
            val viewModel =
                createViewModel(
                    searchRepository = searchRepository,
                    scheduleRepository = scheduleRepository,
                    initialYear = 2025,
                    initialSeasonMonth = 7,
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isCurrentSeason)
            // 历史季度只包含服务端搜索返回的条目，排期库中的当季条目不注入
            assertEquals(1, state.subjects.size)
            assertEquals(99L, state.subjects[0].id)
        }

    @Test
    fun continuingAdultSchedule_isIncludedWhenTagSelected_andExcludedWhenFilteredByOtherTags() =
        runTest {
            val adultSubject =
                sampleSubject.copy(
                    id = 575204,
                    name = "姐妹调教饲育者",
                    nameCn = "姐妹调教饲育者",
                    airDate = "2025-09-01",
                    rating =
                        com.infinitezerone.minibgm.core.model
                            .Rating(score = 8.0),
                    tags =
                        listOf(
                            com.infinitezerone.minibgm.core.model
                                .Tag("Hentai", 1),
                        ),
                    metaTags = listOf("OVA"),
                    platform = "OVA",
                )
            val regularSubject =
                sampleSubject.copy(
                    id = 201,
                    name = "名侦探柯南",
                    nameCn = "名侦探柯南",
                    airDate = "1996-01-08",
                    rating =
                        com.infinitezerone.minibgm.core.model
                            .Rating(score = 8.5),
                    platform = "TV",
                )
            val searchRepository = FakeSearchRepository()
            val scheduleRepository = FakeScheduleRepository()
            scheduleRepository.seasonalAnimeListResult = listOf(adultSubject, regularSubject)

            val viewModel =
                createViewModel(
                    searchRepository = searchRepository,
                    scheduleRepository = scheduleRepository,
                )
            advanceUntilIdle()

            // 1. 默认未开启限制级内容且未选择 Hentai 标签：成人条目被隐藏，普通跨季条目保留
            var state = viewModel.uiState.value
            assertEquals(1, state.subjects.size)
            assertEquals(201L, state.subjects[0].id)

            // 2. 勾选「Hentai」标签：成人跨季条目命中并展示，非成人内容的普通跨季条目被过滤剔除
            viewModel.toggleTag("Hentai")
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertEquals(1, state.subjects.size)
            assertEquals(575204L, state.subjects[0].id)
            assertTrue(state.subjects[0].tags.any { it.name == "Hentai" })
            assertTrue(state.subjects[0].metaTags.contains("OVA"))

            // 3. 反选 Hentai 标签，选择「科幻」标签：由于成人跨季条目没有科幻标签，两者均不应出现
            viewModel.toggleTag("Hentai")
            viewModel.toggleTag("科幻")
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertEquals(0, state.subjects.size)
        }

    @Test
    fun currentSeason_localAniListIsPrimarySource_includesUnairedSeasonalAdultAnime() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val scheduleRepository = FakeScheduleRepository()
            val unairedAdultSubject =
                sampleSubject.copy(
                    id = 575204,
                    name = "姐妹调教饲育者",
                    nameCn = "姐妹调教饲育者",
                    airDate = "2026-03-06",
                    date = "2026-03-06",
                    rating =
                        com.infinitezerone.minibgm.core.model
                            .Rating(score = 0.0),
                    tags =
                        listOf(
                            com.infinitezerone.minibgm.core.model
                                .Tag("Hentai", 1),
                        ),
                    platform = "OVA",
                )
            val regularNewSubject =
                sampleSubject.copy(
                    id = 301,
                    name = "葬送的芙莉莲",
                    nameCn = "葬送的芙莉莲",
                    airDate = "2026-01-16",
                    date = "2026-01-16",
                    rating =
                        com.infinitezerone.minibgm.core.model
                            .Rating(score = 9.2),
                    platform = "TV",
                )
            scheduleRepository.seasonalAnimeListResult = listOf(unairedAdultSubject, regularNewSubject)

            val viewModel =
                createViewModel(
                    searchRepository = searchRepository,
                    scheduleRepository = scheduleRepository,
                )
            advanceUntilIdle()

            // AniList 静态源秒开（isLoading == false）
            var state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.hasMore)
            // 默认只展示常规新番
            assertEquals(1, state.subjects.size)
            assertEquals(301L, state.subjects[0].id)

            // 选择「Hentai」标签：命中当季未开播里番
            viewModel.toggleTag("Hentai")
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertEquals(1, state.subjects.size)
            assertEquals(575204L, state.subjects[0].id)
            assertEquals("姐妹调教饲育者", state.subjects[0].nameCn)
            assertEquals("2026-03-06", state.subjects[0].airDate)
        }

    @Test
    fun pastSeason_prefersStaticSeasonalAniListSnapshot_withTagsAndAdultSupport() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val scheduleRepository = FakeScheduleRepository()

            val pastRegularAnime =
                sampleSubject.copy(
                    id = 524986,
                    name = "キャッツ♥アイ (2025)",
                    nameCn = "猫眼三姐妹",
                    airDate = "2025-09-25",
                    metaTags = listOf("日本", "WEB"),
                    tags =
                        listOf(
                            com.infinitezerone.minibgm.core.model
                                .Tag("动作", 1),
                        ),
                )
            val pastAdultAnime =
                sampleSubject.copy(
                    id = 575204,
                    name = "シスターブリーダー",
                    nameCn = "姐妹调教饲育者",
                    airDate = "2025-09-26",
                    metaTags = listOf("日本", "OVA"),
                    tags =
                        listOf(
                            com.infinitezerone.minibgm.core.model
                                .Tag("Hentai", 1),
                        ),
                )

            // 预置 2025 年秋季的 AniList 静态源数据
            scheduleRepository.seasonalAnimeListResult = listOf(pastRegularAnime, pastAdultAnime)

            val viewModel =
                createViewModel(
                    searchRepository = searchRepository,
                    scheduleRepository = scheduleRepository,
                    initialYear = 2025,
                    initialSeasonMonth = 10, // Autumn
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isCurrentSeason)
            // 静态 AniList 源秒开：无需 loading，无需触底加载
            assertFalse(state.isLoading)
            assertFalse(state.hasMore)
            // 默认过滤成人条目
            assertEquals(1, state.subjects.size)
            assertEquals(524986L, state.subjects[0].id)
            assertEquals("猫眼三姐妹", state.subjects[0].nameCn)

            // 勾选「Hentai」标签：静态源中的成人条目立即可见
            viewModel.toggleTag("Hentai")
            advanceUntilIdle()

            val adultState = viewModel.uiState.value
            assertEquals(1, adultState.subjects.size)
            assertEquals(575204L, adultState.subjects[0].id)
            assertEquals("姐妹调教饲育者", adultState.subjects[0].nameCn)
        }

    @Test
    fun triStateTagFilter_rotatesCorrectlyAndExcludesCorrectly() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val scheduleRepository = FakeScheduleRepository()

            val actionAnime =
                sampleSubject.copy(
                    id = 101,
                    name = "Action Anime",
                    nameCn = "动作动画",
                    airDate = "2025-10-01",
                    tags =
                        listOf(
                            com.infinitezerone.minibgm.core.model
                                .Tag("Action", 10),
                        ),
                )
            val romanceAnime =
                sampleSubject.copy(
                    id = 102,
                    name = "Romance Anime",
                    nameCn = "恋爱动画",
                    airDate = "2025-10-02",
                    tags =
                        listOf(
                            com.infinitezerone.minibgm.core.model
                                .Tag("Romance", 10),
                        ),
                )

            scheduleRepository.seasonalAnimeListResult = listOf(actionAnime, romanceAnime)

            val viewModel =
                createViewModel(
                    searchRepository = searchRepository,
                    scheduleRepository = scheduleRepository,
                    initialYear = 2025,
                    initialSeasonMonth = 10,
                )
            advanceUntilIdle()

            // 初始状态：两部均可见，标签筛选均为空
            assertEquals(2, viewModel.uiState.value.subjects.size)
            assertTrue(
                viewModel.uiState.value.selectedTags
                    .isEmpty(),
            )
            assertTrue(
                viewModel.uiState.value.excludedTags
                    .isEmpty(),
            )

            // 第一次 toggle: 未选 -> 包含
            viewModel.toggleTag("Action")
            advanceUntilIdle()
            assertEquals(setOf("Action"), viewModel.uiState.value.selectedTags)
            assertTrue(
                viewModel.uiState.value.excludedTags
                    .isEmpty(),
            )
            assertEquals(1, viewModel.uiState.value.subjects.size)
            assertEquals(
                101L,
                viewModel.uiState.value.subjects[0]
                    .id,
            )

            // 第二次 toggle: 包含 -> 排除（避雷）
            viewModel.toggleTag("Action")
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.selectedTags
                    .isEmpty(),
            )
            assertEquals(setOf("Action"), viewModel.uiState.value.excludedTags)
            assertEquals(1, viewModel.uiState.value.subjects.size)
            assertEquals(
                102L,
                viewModel.uiState.value.subjects[0]
                    .id,
            )

            // 第三次 toggle: 排除 -> 恢复默认未选
            viewModel.toggleTag("Action")
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.selectedTags
                    .isEmpty(),
            )
            assertTrue(
                viewModel.uiState.value.excludedTags
                    .isEmpty(),
            )
            assertEquals(2, viewModel.uiState.value.subjects.size)

            // 显式排除与显式包含
            viewModel.excludeTag("Action")
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.subjects.size)
            assertEquals(
                102L,
                viewModel.uiState.value.subjects[0]
                    .id,
            )

            viewModel.includeTag("Action")
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.subjects.size)
            assertEquals(
                101L,
                viewModel.uiState.value.subjects[0]
                    .id,
            )

            // 切换季度自动重置临时标签筛选
            viewModel.selectQuarter(SeasonQuarter.SPRING)
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.selectedTags
                    .isEmpty(),
            )
            assertTrue(
                viewModel.uiState.value.excludedTags
                    .isEmpty(),
            )
        }

    @Test
    fun anilistDynamicTags_areCategorizedIntoGenresAndHotTags() =
        runTest {
            val regularAnime =
                sampleSubject.copy(
                    id = 201L,
                    name = "Regular Anime",
                    genres = listOf("Action", "Romance", "Music"),
                    tags =
                        listOf(
                            com.infinitezerone.minibgm.core.model
                                .Tag("Action", 1),
                            com.infinitezerone.minibgm.core.model
                                .Tag("Romance", 1),
                            com.infinitezerone.minibgm.core.model
                                .Tag("Music", 1),
                            com.infinitezerone.minibgm.core.model
                                .Tag("Isekai", 1),
                        ),
                )
            val adultAnime =
                sampleSubject.copy(
                    id = 202L,
                    name = "Adult Anime",
                    genres = listOf("Hentai", "Romance"),
                    tags =
                        listOf(
                            com.infinitezerone.minibgm.core.model
                                .Tag("Hentai", 1),
                            com.infinitezerone.minibgm.core.model
                                .Tag("Romance", 1),
                            com.infinitezerone.minibgm.core.model
                                .Tag("Magic", 1),
                        ),
                )
            val scheduleRepository = FakeScheduleRepository()
            scheduleRepository.seasonalAnimeListResult = listOf(regularAnime, adultAnime)

            val viewModel =
                createViewModel(
                    scheduleRepository = scheduleRepository,
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val genreNames = state.seasonalGenres.map { it.first }.toSet()
            val hotTagNames = state.seasonalHotTags.map { it.first }.toSet()

            // 核心题材分类（Genres 大类）：Action, Romance, Music, Hentai
            assertTrue(genreNames.contains("Action"))
            assertTrue(genreNames.contains("Romance"))
            assertTrue(genreNames.contains("Music"))
            assertTrue(genreNames.contains("Hentai"))

            // 特色微观标签（Tags 小类）：Isekai, Magic
            assertTrue(hotTagNames.contains("Isekai"))
            assertTrue(hotTagNames.contains("Magic"))
        }

    @Test
    fun customFilterTags_persistsAndTogglesFavoriteState() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.customFilterTags
                    .isEmpty(),
            )

            // 添加常用偏好标签
            viewModel.toggleFavoriteTag("Romance")
            advanceUntilIdle()

            assertEquals(listOf("Romance"), viewModel.uiState.value.customFilterTags)

            // 再次切换 -> 移出偏好
            viewModel.toggleFavoriteTag("Romance")
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.customFilterTags
                    .isEmpty(),
            )

            // 使用 addCustomFilterTag: 持久化并自动包含
            viewModel.addCustomFilterTag("Sci-Fi")
            advanceUntilIdle()

            assertEquals(listOf("Sci-Fi"), viewModel.uiState.value.customFilterTags)
            assertTrue(
                viewModel.uiState.value.selectedTags
                    .contains("Sci-Fi"),
            )
        }

    @Test
    fun currentSeason_excludesEndedPastSeasonAnimeFromContinuingAndList() =
        runTest {
            val autumnDate = LocalDate.of(2026, 10, 6) // Q4 Autumn 2026
            val searchRepository = FakeSearchRepository()
            val scheduleRepository = FakeScheduleRepository()

            // 1. 夏季已完结番（如《尼古喵喵》）：7月开播、9月25日完结第12集，无后续排期
            val endedSummerAnime =
                AirSchedule(
                    bgmId = 622206L,
                    title = "ヤニねこ",
                    titleCn = "尼古喵喵",
                    airDate = "2026-07-02",
                    nextEpisodeNumber = 12,
                    nextEpisodeKind = AirEventKind.ACTUAL,
                    nextEpisodeAtUtc = "2026-09-25T14:30:00Z",
                    ratingScore = 7.0,
                )

            // 2. 真正的跨季连载番（如半年番）：7月开播，但在秋季静态名单中收录，且10月还有第14话
            val continuingAnimeSubject =
                sampleSubject.copy(
                    id = 701L,
                    name = "战国妖狐",
                    airDate = "2026-07-10",
                )
            val continuingAnimeSchedule =
                AirSchedule(
                    bgmId = 701L,
                    title = "战国妖狐",
                    titleCn = "战国妖狐",
                    airDate = "2026-07-10",
                    nextEpisodeNumber = 14,
                    nextEpisodeKind = AirEventKind.SCHEDULED,
                    nextEpisodeAtUtc = "2026-10-10T14:30:00Z",
                    ratingScore = 8.0,
                )

            // 3. 当季首播新番：10月开播
            val newAnimeSubject =
                sampleSubject.copy(
                    id = 801L,
                    name = "秋季新番",
                    airDate = "2026-10-05",
                )

            // 静态 AniList 秋季快照：只有 701 (续播) 和 801 (新番)，官方已排除完结的 622206
            scheduleRepository.seasonalAnimeListResult = listOf(continuingAnimeSubject, newAnimeSubject)
            // 本地周时刻表包含缓冲中的完结番 622206 以及在播番 701
            scheduleRepository.sendSchedules(1, listOf(endedSummerAnime, continuingAnimeSchedule))

            val viewModel =
                createViewModel(
                    searchRepository = searchRepository,
                    scheduleRepository = scheduleRepository,
                    timeProvider = { autumnDate },
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            // 完结番坚决不可出现在当季全部列表中
            val subjectIds = state.subjects.map { it.id }
            assertTrue(subjectIds.contains(701L))
            assertTrue(subjectIds.contains(801L))
            assertFalse(subjectIds.contains(622206L))

            // 完结番不可出现在跨季话数映射中
            assertNull(state.continuingNextEpisodes[622206L])
            assertEquals(14, state.continuingNextEpisodes[701L])

            // 切换仅跨季续播：仅包含 701，绝无 622206
            viewModel.selectAiringScope(SeasonAiringScope.CONTINUING_ONLY)
            val continuingState = viewModel.uiState.value
            assertEquals(listOf(701L), continuingState.subjects.map { it.id })

            // 切换仅首播新番：仅包含 801
            viewModel.selectAiringScope(SeasonAiringScope.NEW_ONLY)
            val newState = viewModel.uiState.value
            assertEquals(listOf(801L), newState.subjects.map { it.id })
        }
}
