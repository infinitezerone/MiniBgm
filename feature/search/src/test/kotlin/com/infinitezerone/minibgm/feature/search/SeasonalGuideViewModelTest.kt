package com.infinitezerone.minibgm.feature.search

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.Tag
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        scheduleRepository: FakeScheduleRepository = FakeScheduleRepository(),
        subjectRepository: FakeSubjectRepository = FakeSubjectRepository(),
        initialYear: Int = 0,
        initialSeasonMonth: Int = 0,
    ) = SeasonalGuideViewModel(
        searchRepository = searchRepository,
        collectionRepository = collectionRepository,
        authRepository = authRepository,
        scheduleRepository = scheduleRepository,
        subjectRepository = subjectRepository,
        initialYear = initialYear,
        initialSeasonMonth = initialSeasonMonth,
        timeProvider = { fixedDate },
    )

    /** 排期仓名册条目：`airDate` 为条目自身首播日，与窗口判定相关 */
    private fun airSchedule(
        id: Long,
        title: String,
        airDate: String,
    ) = AirSchedule(
        bgmId = id,
        title = title,
        titleCn = title,
        airDate = airDate,
        weekday = 1,
    )

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
            assertEquals(SeasonFormFilter.DEFAULT, state.selectedForms)
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
    fun toggleForm_onlyMovieIsPushableToServerAndItCombinesWithOriginAsAnd() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            // 默认只开「正片」。正片是「TV 或 WEB」，而服务端多值 meta_tags 是 AND、又没有排除语法，
            // 表达不了"或"，所以这一档只能客户端筛，请求里不该出现 meta_tags
            assertEquals(SeasonFormFilter.DEFAULT, viewModel.uiState.value.selectedForms)
            assertNull(searchRepository.lastAdvancedRequest?.filter?.metaTags)

            viewModel.selectOrigin(SeasonOriginFilter.JAPAN)
            advanceUntilIdle()
            assertEquals(listOf("日本"), searchRepository.lastAdvancedRequest?.filter?.metaTags)

            // 关掉正片 → 空集合（不筛形式），产地仍下推
            viewModel.toggleForm(SeasonFormFilter.MAIN)
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.selectedForms
                    .isEmpty(),
            )
            assertEquals(listOf("日本"), searchRepository.lastAdvancedRequest?.filter?.metaTags)

            // 换成"只选剧场版"：这是形式里唯一能整体下推服务端的情况，与产地组合成 AND；
            // 总数与分页因此都是准的，不必靠客户端补筛
            viewModel.toggleForm(SeasonFormFilter.MOVIE)
            advanceUntilIdle()
            assertEquals(setOf(SeasonFormFilter.MOVIE), viewModel.uiState.value.selectedForms)
            assertEquals(listOf("日本", "剧场版"), searchRepository.lastAdvancedRequest?.filter?.metaTags)

            // 再叠上短片 → 变成「剧场版 或 短片」，服务端表达不了，形式那部分不再下推；
            // 产地是独立的一维，仍然照常下推
            viewModel.toggleForm(SeasonFormFilter.SHORT)
            advanceUntilIdle()
            assertEquals(listOf("日本"), searchRepository.lastAdvancedRequest?.filter?.metaTags)
        }

    @Test
    fun selectWesternOrigin_leavesFilteringToClientBecauseServerTagIsNotAggregate() =
        runTest {
            // 服务端 meta_tags 是精确单标签匹配：「欧美」只覆盖 7 条，而只标具体国家的有 13 条，
            // 用任一单标签下推都会漏掉一半以上，所以这一档不下推、由客户端按集合筛
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult =
                AppResult.Success(
                    listOf(
                        sampleSubject.copy(id = 1L, metaTags = listOf("TV", "日本")),
                        sampleSubject.copy(id = 2L, metaTags = listOf("漫画改", "美国", "WEB")),
                        sampleSubject.copy(id = 3L, metaTags = listOf("奇幻", "欧美", "WEB")),
                    ),
                )
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            viewModel.selectOrigin(SeasonOriginFilter.WESTERN)
            advanceUntilIdle()

            assertNull(searchRepository.lastAdvancedRequest?.filter?.metaTags)

            assertEquals(
                listOf(2L, 3L),
                viewModel.uiState.value.filteredSubjects
                    .map { it.id },
            )
        }

    @Test
    fun defaultFormFilter_keepsOnlyMainFeatures() =
        runTest {
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult =
                AppResult.Success(
                    listOf(
                        sampleSubject.copy(id = 1L, platform = "TV", metaTags = listOf("TV", "日本")),
                        sampleSubject.copy(id = 2L, platform = "其他", metaTags = listOf("MV", "日本")),
                        sampleSubject.copy(id = 3L, platform = "WEB", metaTags = listOf("短片", "中国")),
                        sampleSubject.copy(id = 4L, platform = "剧场版", metaTags = listOf("剧场版", "日本")),
                    ),
                )
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            // 默认只开「正片」：片段型与剧场版都不该混进新番列表。
            // 注意 id=3 的 platform 是 WEB 却带「短片」标签——片段判据不能只看 platform。
            assertEquals(
                listOf(1L),
                viewModel.uiState.value.filteredSubjects
                    .map { it.id },
            )
        }

    @Test
    fun formFilter_isMultiSelectSoMainAndMovieCanCoexist() =
        runTest {
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult =
                AppResult.Success(
                    listOf(
                        sampleSubject.copy(id = 1L, platform = "TV"),
                        sampleSubject.copy(id = 2L, platform = "剧场版"),
                        sampleSubject.copy(id = 3L, platform = "其他", metaTags = listOf("MV")),
                    ),
                )
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()
            assertEquals(
                listOf(1L),
                viewModel.uiState.value.filteredSubjects
                    .map { it.id },
            )

            // 加上剧场版：两档并存，这正是"大家通常都会同时选"的那个组合
            viewModel.toggleForm(SeasonFormFilter.MOVIE)
            advanceUntilIdle()
            assertEquals(
                listOf(1L, 2L),
                viewModel.uiState.value.filteredSubjects
                    .map { it.id },
            )

            // 三档全关 = 不筛形式，连片段型也放出来
            viewModel.toggleForm(SeasonFormFilter.MAIN)
            viewModel.toggleForm(SeasonFormFilter.MOVIE)
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.selectedForms
                    .isEmpty(),
            )
            assertEquals(
                listOf(1L, 2L, 3L),
                viewModel.uiState.value.filteredSubjects
                    .map { it.id },
            )
        }

    @Test
    fun filterSummary_readsOutCurrentSelectionForTheCollapsedBar() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // 收起后只剩一行摘要，它必须能把"现在筛的是什么"交代清楚
            assertEquals("正片", viewModel.uiState.value.filterSummary)

            viewModel.selectOrigin(SeasonOriginFilter.JAPAN)
            advanceUntilIdle()
            assertEquals("日本 · 正片", viewModel.uiState.value.filterSummary)

            viewModel.toggleForm(SeasonFormFilter.MOVIE)
            advanceUntilIdle()
            assertEquals("日本 · 正片+剧场版", viewModel.uiState.value.filterSummary)

            // 全不选时回落为「全部」，不该出现「全部 · 全部」这种同义重复
            viewModel.selectOrigin(SeasonOriginFilter.ALL)
            viewModel.toggleForm(SeasonFormFilter.MAIN)
            viewModel.toggleForm(SeasonFormFilter.MOVIE)
            advanceUntilIdle()
            assertEquals("全部", viewModel.uiState.value.filterSummary)
        }

    @Test
    fun shortFormFilter_keepsFetchingPagesUntilSomethingIsVisible() =
        runTest {
            // 热度排序下片段型普遍靠后：前两页整页都是正片，第 3 页才出现 1 条 MV。
            // 若不在过滤后继续往后取，用户会停在"列表空白、又因为没有内容而无法滚动"的死角上。
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchPages =
                mapOf(
                    0 to List(20) { sampleSubject.copy(id = it.toLong() + 1, platform = "TV") },
                    20 to List(20) { sampleSubject.copy(id = it.toLong() + 101, platform = "TV") },
                    40 to listOf(sampleSubject.copy(id = 999L, platform = "其他", metaTags = listOf("MV"))),
                )
            searchRepository.advancedSearchTotal = 41

            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            // 形式是多选且默认开着「正片」，要"只看片段型"得先把正片关掉
            viewModel.toggleForm(SeasonFormFilter.MAIN)
            viewModel.toggleForm(SeasonFormFilter.SHORT)
            advanceUntilIdle()

            assertEquals(
                listOf(999L),
                viewModel.uiState.value.filteredSubjects
                    .map { it.id },
            )
            // 首屏 1 次 + 关掉正片 1 次 + 「短片 / MV」档下连续取了 3 页（片段型在第 3 页才出现）
            assertEquals(5, searchRepository.advancedSearchCallCount)
            // 三页原始条目都留在 state 里：过滤不参与游标推进，所以滚到底仍是完整结果
            assertEquals(41, viewModel.uiState.value.subjects.size)
        }

    @Test
    fun loadMore_keepsFetchingWhileWholePagesAreFilteredOut() =
        runTest {
            // 翻页的续取判据必须是"可见条目确实变多了"，而不是"列表非空"——翻页时列表本来就非空，
            // 用后者只会取一页就收工；碰上整页都是正片（热度排序下片段型普遍靠后）时，
            // 可见内容与滚动范围都不变，触底加载会永久卡死：滑到底不动、既不转圈也不加载。
            //
            // 每页都是满的 20 条：翻页游标按 offset + 实际条数前进，页宽不满时下一页的 offset 会跟着缩小。
            val searchRepository = FakeSearchRepository()
            val mv = { id: Long -> sampleSubject.copy(id = id, platform = "其他", metaTags = listOf("MV")) }
            val tv = { from: Long -> List(20) { sampleSubject.copy(id = from + it, platform = "TV") } }
            searchRepository.advancedSearchPages =
                mapOf(
                    0 to (listOf(mv(1L)) + tv(100L).dropLast(1)),
                    20 to tv(200L),
                    40 to tv(300L),
                    60 to (tv(400L).dropLast(1) + mv(999L)),
                )
            searchRepository.advancedSearchTotal = 100

            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            viewModel.toggleForm(SeasonFormFilter.MAIN)
            viewModel.toggleForm(SeasonFormFilter.SHORT)
            advanceUntilIdle()

            assertEquals(
                listOf(1L),
                viewModel.uiState.value.filteredSubjects
                    .map { it.id },
            )
            val callsBeforeLoadMore = searchRepository.advancedSearchCallCount

            viewModel.loadMore()
            advanceUntilIdle()

            // offset 20 / 40 两页整页被滤掉，必须一路取到 60 页才出现第 2 条 MV
            assertEquals(
                listOf(1L, 999L),
                viewModel.uiState.value.filteredSubjects
                    .map { it.id },
            )
            assertEquals(callsBeforeLoadMore + 3, searchRepository.advancedSearchCallCount)
            assertFalse(viewModel.uiState.value.isLoadingMore)
        }

    @Test
    fun loadMore_cancelledByFilterSwitch_clearsFlagSoNextPageStillLoads() =
        runTest {
            // 切产地/形式会 cancel 正在飞的翻页任务。若 isLoadingMore 只在正常返回路径复位，
            // 它会永久停在 true，loadMore 的 guard 从此恒真——表现是切完筛选后再也翻不了页。
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchPages =
                mapOf(
                    0 to List(20) { sampleSubject.copy(id = it.toLong() + 1) },
                    20 to List(20) { sampleSubject.copy(id = it.toLong() + 21) },
                )
            searchRepository.advancedSearchTotal = 100

            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()
            assertEquals(20, viewModel.uiState.value.subjects.size)

            val gate = CompletableDeferred<Unit>()
            searchRepository.advancedSearchGate = gate
            viewModel.loadMore()
            assertTrue(viewModel.uiState.value.isLoadingMore)

            // 请求还在飞的时候切产地：翻页任务被取消，旧条件的结果不能混进按新条件重建的列表
            viewModel.selectOrigin(SeasonOriginFilter.JAPAN)
            gate.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoadingMore)
            assertEquals(listOf("日本"), searchRepository.lastAdvancedRequest?.filter?.metaTags)

            // 关键：标志复位了，翻页才进得去。若卡在 true，这次调用会被 guard 直接挡回
            searchRepository.advancedSearchGate = null
            val callsBeforeLoadMore = searchRepository.advancedSearchCallCount
            viewModel.loadMore()
            advanceUntilIdle()

            assertEquals(callsBeforeLoadMore + 1, searchRepository.advancedSearchCallCount)
            assertEquals(40, viewModel.uiState.value.subjects.size)
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
    fun toggleCollection_whenLoggedIn_optimisticallyUpdatesAndPersists() =
        runTest {
            val collectionRepository = FakeCollectionRepository()
            val viewModel = createViewModel(collectionRepository = collectionRepository)
            advanceUntilIdle()

            viewModel.toggleCollection(100L, CollectionType.DOING)
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.doingSubjectIds
                    .contains(100L),
            )
            assertEquals(1, collectionRepository.updateCollectionCallCount)
            assertEquals("已标记为「在看」", viewModel.uiState.value.userMessage)
        }

    @Test
    fun toggleCollection_whenError_revertsOptimisticUpdate() =
        runTest {
            val collectionRepository = FakeCollectionRepository()
            collectionRepository.updateCollectionResult = AppResult.Error(Exception("Network failure"), "网络异常")
            val viewModel = createViewModel(collectionRepository = collectionRepository)
            advanceUntilIdle()

            viewModel.toggleCollection(100L, CollectionType.WISH)
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.wishedSubjectIds
                    .contains(100L),
            )
            assertEquals("网络异常", viewModel.uiState.value.userMessage)
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
    fun ongoing_keepsLongRunnersAiringThisSeasonAndDropsSeasonPremieres() =
        runTest {
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))

            val scheduleRepository = FakeScheduleRepository()
            scheduleRepository.schedulesAiringBetween =
                listOf(
                    airSchedule(id = 899L, title = "名侦探柯南", airDate = "1996-01-08"),
                    airSchedule(id = 2001L, title = "本季新番", airDate = "2026-01-10"),
                )

            val subjectRepository = FakeSubjectRepository()
            subjectRepository.sendSubject(
                sampleSubject.copy(id = 899L, name = "名探偵コナン", nameCn = "名侦探柯南", date = "1996-01-08"),
            )
            subjectRepository.sendSubject(
                sampleSubject.copy(id = 2001L, name = "新番", nameCn = "本季新番", date = "2026-01-10"),
            )

            val viewModel =
                createViewModel(
                    searchRepository = searchRepository,
                    scheduleRepository = scheduleRepository,
                    subjectRepository = subjectRepository,
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(899L), state.ongoingSubjects.map { it.id })
            assertFalse(state.isLoadingOngoing)
            // 名册自带 airDate 的预筛就剔掉了本季首播那条，无需为它补一轮详情
            assertEquals(1, subjectRepository.fetchSubjectDetailCallCount)
            assertEquals("2025-12-21T00:00:00Z" to "2026-03-20T23:59:59Z", scheduleRepository.lastAiringWindow)
        }

    @Test
    fun ongoing_usesBangumiDateAsFinalJudgeWhenRosterAirDateIsMissing() =
        runTest {
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))

            val scheduleRepository = FakeScheduleRepository()
            // 名册没给 airDate：预筛只能放行，必须靠 Bangumi 的 date 终判剔掉，否则会与网格重复
            scheduleRepository.schedulesAiringBetween = listOf(airSchedule(id = 2001L, title = "本季新番", airDate = ""))

            val subjectRepository = FakeSubjectRepository()
            subjectRepository.sendSubject(
                sampleSubject.copy(id = 2001L, name = "新番", nameCn = "本季新番", date = "2026-01-10"),
            )

            val viewModel =
                createViewModel(
                    searchRepository = searchRepository,
                    scheduleRepository = scheduleRepository,
                    subjectRepository = subjectRepository,
                )
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.ongoingSubjects
                    .isEmpty(),
            )
            assertEquals(1, subjectRepository.fetchSubjectDetailCallCount)
        }

    @Test
    fun ongoing_skipsDetailFetchFailureWithoutBreakingTheGroup() =
        runTest {
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))

            val scheduleRepository = FakeScheduleRepository()
            scheduleRepository.schedulesAiringBetween =
                listOf(
                    airSchedule(id = 899L, title = "名侦探柯南", airDate = "1996-01-08"),
                    airSchedule(id = 404L, title = "已下架", airDate = "1997-04-01"),
                )

            val subjectRepository = FakeSubjectRepository()
            subjectRepository.sendSubject(
                sampleSubject.copy(id = 899L, name = "名探偵コナン", nameCn = "名侦探柯南", date = "1996-01-08"),
            )

            val viewModel =
                createViewModel(
                    searchRepository = searchRepository,
                    scheduleRepository = scheduleRepository,
                    subjectRepository = subjectRepository,
                )
            advanceUntilIdle()

            assertEquals(
                listOf(899L),
                viewModel.uiState.value.ongoingSubjects
                    .map { it.id },
            )
            assertFalse(viewModel.uiState.value.isLoadingOngoing)
        }

    @Test
    fun ongoing_notQueriedForPastSeasonBecauseSnapshotHasNoHistory() =
        runTest {
            val scheduleRepository = FakeScheduleRepository()
            scheduleRepository.schedulesAiringBetween = listOf(airSchedule(id = 899L, title = "名侦探柯南", airDate = "1996-01-08"))

            // 2024 春窗口末端 2024-06-30 早于"今天"(2026-02-15)：滚动快照里查不到任何事件，
            // 与其给出"本季没有连载番"的错误结论，不如整组不呈现
            val viewModel =
                createViewModel(
                    scheduleRepository = scheduleRepository,
                    initialYear = 2024,
                    initialSeasonMonth = 4,
                )
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.ongoingSubjects
                    .isEmpty(),
            )
            assertNull(scheduleRepository.lastAiringWindow)
        }

    @Test
    fun switchingSeason_clearsPreviousOngoingGroup() =
        runTest {
            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))

            val scheduleRepository = FakeScheduleRepository()
            scheduleRepository.schedulesAiringBetween = listOf(airSchedule(id = 899L, title = "名侦探柯南", airDate = "1996-01-08"))

            val subjectRepository = FakeSubjectRepository()
            subjectRepository.sendSubject(
                sampleSubject.copy(id = 899L, name = "名探偵コナン", nameCn = "名侦探柯南", date = "1996-01-08"),
            )

            val viewModel =
                createViewModel(
                    searchRepository = searchRepository,
                    scheduleRepository = scheduleRepository,
                    subjectRepository = subjectRepository,
                )
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.ongoingSubjects.size)

            // 切到历史季：上一季的连载中分组必须立刻清空，不能残留
            scheduleRepository.schedulesAiringBetween = emptyList()
            viewModel.selectSeason(2024, SeasonQuarter.SPRING)
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.ongoingSubjects
                    .isEmpty(),
            )
        }

    @Test
    fun toggleCollection_whenSwitchingFromDoingToWish_andErrorOccurs_restoresDoingState() =
        runTest {
            val collectionRepository = FakeCollectionRepository()
            collectionRepository.sendCollection(UserCollection(subjectId = 100L, type = CollectionType.DOING.value))
            collectionRepository.updateCollectionResult = AppResult.Error(Exception("Network error"), "网络异常")
            val viewModel = createViewModel(collectionRepository = collectionRepository)
            advanceUntilIdle()

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
            assertEquals("网络异常", viewModel.uiState.value.userMessage)
        }

    @Test
    fun toggleCollection_whenSwitchingFromWishToDoing_andErrorOccurs_restoresWishState() =
        runTest {
            val collectionRepository = FakeCollectionRepository()
            collectionRepository.sendCollection(UserCollection(subjectId = 200L, type = CollectionType.WISH.value))
            collectionRepository.updateCollectionResult = AppResult.Error(Exception("Server error"), "服务器开小差了")
            val viewModel = createViewModel(collectionRepository = collectionRepository)
            advanceUntilIdle()

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
            assertEquals("服务器开小差了", viewModel.uiState.value.userMessage)
        }

    @Test
    fun toggleCollection_whenAlreadyInTargetCollection_emitsPromptWithoutCallingRepository() =
        runTest {
            val collectionRepository = FakeCollectionRepository()
            collectionRepository.sendCollection(UserCollection(subjectId = 300L, type = CollectionType.DOING.value))
            val viewModel = createViewModel(collectionRepository = collectionRepository)
            advanceUntilIdle()

            viewModel.toggleCollection(300L, CollectionType.DOING)
            advanceUntilIdle()

            assertEquals(0, collectionRepository.updateCollectionCallCount)
            assertEquals("已在您的「在看」列表中", viewModel.uiState.value.userMessage)
        }

    @Test
    fun matchesOrigin_readsOfficialMetaTagsIncludingCountryOnlyWesternEntries() {
        val japanese = Subject(id = 1, name = "x", metaTags = listOf("TV", "日本"))
        val chinese = Subject(id = 2, name = "x", metaTags = listOf("WEB", "中国"))
        // 真实数据里大量欧美条目只标了具体国家、没有「欧美」聚合标签（如 X战警97 只标「美国」）
        val americanOnly = Subject(id = 3, name = "x", metaTags = listOf("TV", "美国"))
        val unknown = Subject(id = 4, name = "x", tags = listOf(Tag("动画", 10)))

        assertTrue(matchesOrigin(japanese, SeasonOriginFilter.JAPAN))
        assertFalse(matchesOrigin(japanese, SeasonOriginFilter.CHINA))
        assertTrue(matchesOrigin(chinese, SeasonOriginFilter.CHINA))
        assertTrue(matchesOrigin(americanOnly, SeasonOriginFilter.WESTERN))
        assertFalse(matchesOrigin(unknown, SeasonOriginFilter.WESTERN))
        assertTrue(matchesOrigin(unknown, SeasonOriginFilter.ALL))
    }

    @Test
    fun matchesForm_readsPlatformAndShortFormTagsIntoThreeBuckets() {
        val tv = Subject(id = 1, name = "x", platform = "TV", metaTags = listOf("TV"))
        val web = Subject(id = 2, name = "x", platform = "WEB", metaTags = listOf("WEB"))
        val movie = Subject(id = 3, name = "x", platform = "剧场版", metaTags = listOf("剧场版"))
        val mv = Subject(id = 4, name = "x", platform = "其他", metaTags = listOf("MV"))
        // platform 标成 WEB，但带「短片」标签——片段判据不能只看 platform
        val shortViaTag = Subject(id = 5, name = "x", platform = "WEB", metaTags = listOf("短片"))

        // TV 与「网络」同属「正片」：两者只是发行渠道不同（地上波 vs 配信），
        // 真实数据里它们严格互斥（当季没有任何条目同时带这两个标签），看番的人也不会把它们分开要
        assertEquals(SeasonFormFilter.MAIN, formBucketOf(tv))
        assertEquals(SeasonFormFilter.MAIN, formBucketOf(web))
        assertEquals(SeasonFormFilter.MOVIE, formBucketOf(movie))
        assertEquals(SeasonFormFilter.SHORT, formBucketOf(mv))
        assertEquals(SeasonFormFilter.SHORT, formBucketOf(shortViaTag))

        val forms = setOf(SeasonFormFilter.MAIN, SeasonFormFilter.MOVIE)
        assertTrue(matchesForm(tv, forms))
        assertTrue(matchesForm(web, forms))
        assertTrue(matchesForm(movie, forms))
        assertFalse(matchesForm(mv, forms))

        // 空集合 = 不筛形式，全放行
        assertTrue(matchesForm(mv, emptySet()))
        assertTrue(matchesForm(shortViaTag, emptySet()))
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
                    scheduleRepository = FakeScheduleRepository(),
                    subjectRepository = FakeSubjectRepository(),
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
}
