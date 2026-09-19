package com.infinitezerone.minibgm.feature.search

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.Tag
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
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
            assertEquals(SeasonCategoryFilter.ALL, state.selectedCategory)
            assertFalse(state.isLoading)
            assertFalse(state.isRefreshing)
            assertNull(state.error)
            assertEquals(1, state.subjects.size)

            assertEquals(1, searchRepository.advancedSearchCallCount)
            val request = searchRepository.lastAdvancedRequest
            assertEquals("heat", request?.sort)
            assertEquals(listOf(2), request?.filter?.type)
            assertEquals(listOf(">=2026-01-01", "<=2026-03-31"), request?.filter?.airDate)
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
            assertEquals(listOf(">=2024-07-01", "<=2024-09-30"), request?.filter?.airDate)
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
            assertEquals(listOf(">=2025-01-01", "<=2025-03-31"), request?.filter?.airDate)
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
            assertEquals(listOf(">=2026-10-01", "<=2026-12-31"), request?.filter?.airDate)
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
            assertEquals(listOf(">=2025-07-01", "<=2025-09-30"), request?.filter?.airDate)
            assertNull(request?.filter?.tag)
        }

    @Test
    fun selectCategory_filtersSubjectsLocallyWithoutAdditionalNetworkCall() =
        runTest {
            val tvAnime = sampleSubject.copy(id = 1L, name = "TV Anime", tags = listOf(Tag("TV", 10)))
            val webAnime = sampleSubject.copy(id = 2L, name = "Web Anime", tags = listOf(Tag("WEB", 10)))
            val movieAnime = sampleSubject.copy(id = 3L, name = "Movie Anime", tags = listOf(Tag("剧场版", 10)))

            val searchRepository = FakeSearchRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(tvAnime, webAnime, movieAnime))
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            assertEquals(1, searchRepository.advancedSearchCallCount)
            assertEquals(3, viewModel.uiState.value.filteredSubjects.size)

            viewModel.selectCategory(SeasonCategoryFilter.TV)
            assertEquals(1, searchRepository.advancedSearchCallCount) // No network re-query
            assertEquals(listOf(tvAnime), viewModel.uiState.value.filteredSubjects)

            viewModel.selectCategory(SeasonCategoryFilter.WEB)
            assertEquals(listOf(webAnime), viewModel.uiState.value.filteredSubjects)

            viewModel.selectCategory(SeasonCategoryFilter.MOVIE_OVA)
            assertEquals(listOf(movieAnime), viewModel.uiState.value.filteredSubjects)

            viewModel.selectCategory(SeasonCategoryFilter.ALL)
            assertEquals(3, viewModel.uiState.value.filteredSubjects.size)
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
    fun loadMore_appendsUniqueSubjectsWhenHasMore() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val firstBatch = List(50) { sampleSubject.copy(id = it.toLong() + 1) }
            val secondBatch = List(10) { sampleSubject.copy(id = it.toLong() + 51) }

            searchRepository.advancedSearchResult = AppResult.Success(firstBatch)
            val viewModel = createViewModel(searchRepository = searchRepository)
            advanceUntilIdle()

            assertEquals(50, viewModel.uiState.value.subjects.size)
            assertTrue(viewModel.uiState.value.hasMore)

            searchRepository.advancedSearchResult = AppResult.Success(secondBatch)
            viewModel.loadMore()
            advanceUntilIdle()

            assertEquals(60, viewModel.uiState.value.subjects.size)
            assertFalse(viewModel.uiState.value.hasMore)
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
    fun matchesCategory_handlesEdgeCasesCorrectly() {
        val taglessSubject = Subject(id = 1, name = "Original Title", tags = emptyList())
        assertTrue(matchesCategory(taglessSubject, SeasonCategoryFilter.TV))
        assertFalse(matchesCategory(taglessSubject, SeasonCategoryFilter.WEB))
        assertFalse(matchesCategory(taglessSubject, SeasonCategoryFilter.MOVIE_OVA))

        val webTagSubject = Subject(id = 2, name = "Web Series", tags = listOf(Tag("网络动画", 10)))
        assertTrue(matchesCategory(webTagSubject, SeasonCategoryFilter.WEB))
        assertFalse(matchesCategory(webTagSubject, SeasonCategoryFilter.TV))

        val wordBoundarySubject = Subject(id = 3, name = "Spider Webster", tags = emptyList())
        assertTrue(matchesCategory(wordBoundarySubject, SeasonCategoryFilter.TV))
        assertFalse(matchesCategory(wordBoundarySubject, SeasonCategoryFilter.WEB))

        val movieSubject = Subject(id = 4, name = "Movie Title", nameCn = "剧场版 某作品", tags = emptyList())
        assertTrue(matchesCategory(movieSubject, SeasonCategoryFilter.MOVIE_OVA))
        assertFalse(matchesCategory(movieSubject, SeasonCategoryFilter.TV))
    }
}
