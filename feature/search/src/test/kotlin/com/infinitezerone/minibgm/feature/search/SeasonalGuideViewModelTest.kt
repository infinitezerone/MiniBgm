package com.infinitezerone.minibgm.feature.search

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
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

    private fun createViewModel(
        searchRepository: FakeSearchRepository = FakeSearchRepository(),
        collectionRepository: FakeCollectionRepository = FakeCollectionRepository(),
        authRepository: FakeAuthRepository = FakeAuthRepository(initialLoggedIn = true),
        initialYear: Int = 0,
        initialSeasonMonth: Int = 0,
    ) = SeasonalGuideViewModel(
        searchRepository = searchRepository,
        collectionRepository = collectionRepository,
        authRepository = authRepository,
        initialYear = initialYear,
        initialSeasonMonth = initialSeasonMonth,
        timeProvider = { fixedDate },
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

            assertEquals("全部", viewModel.uiState.value.filterSummary)

            viewModel.selectOrigin(SeasonOriginFilter.JAPAN)
            advanceUntilIdle()
            assertEquals("日本", viewModel.uiState.value.filterSummary)

            viewModel.selectForm(SeasonFormFilter.MOVIE)
            advanceUntilIdle()
            assertEquals("日本 · 剧场版", viewModel.uiState.value.filterSummary)

            viewModel.selectOrigin(SeasonOriginFilter.ALL)
            advanceUntilIdle()
            assertEquals("剧场版", viewModel.uiState.value.filterSummary)
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
            val viewModel =
                SeasonalGuideViewModel(
                    searchRepository = FakeSearchRepository(),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(),
                    timeProvider = { LocalDate.of(2026, 9, 29) },
                )
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
    fun customFilterTags_addAndRemoveViaViewModel() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            viewModel.addCustomFilterTag("机战")
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.customFilterTags
                    .contains("机战"),
            )

            viewModel.removeCustomFilterTag("机战")
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.customFilterTags
                    .contains("机战"),
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
}
