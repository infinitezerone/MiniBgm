package com.infinitezerone.minibgm.feature.search

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
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

@OptIn(ExperimentalCoroutinesApi::class)
class TagSubjectsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        tag: String = "搞笑",
        initialType: Int = 0,
        searchRepo: FakeSearchRepository = FakeSearchRepository(),
        collectionRepo: FakeCollectionRepository = FakeCollectionRepository(),
    ) = TagSubjectsViewModel(
        tag = tag,
        initialType = initialType,
        searchRepository = searchRepo,
        collectionRepository = collectionRepo,
    )

    @Test
    fun init_loadsSubjectsWithTagAndSortRank() =
        runTest {
            val searchRepo = FakeSearchRepository()
            searchRepo.advancedSearchResult = AppResult.Success(listOf(sampleSubject))

            val viewModel = createViewModel(tag = "搞笑", searchRepo = searchRepo)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("搞笑", state.tag)
            assertEquals(0, state.selectedType)
            assertEquals(SearchSort.RANK, state.selectedSort)
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals(listOf(sampleSubject), state.subjects)
            assertEquals(1, searchRepo.advancedSearchCallCount)
            assertEquals(listOf("搞笑"), searchRepo.lastAdvancedRequest?.filter?.tag)
            assertEquals("rank", searchRepo.lastAdvancedRequest?.sort)
        }

    @Test
    fun onSelectType_reloadsSubjectsWithCategoryFilter() =
        runTest {
            val searchRepo = FakeSearchRepository()
            val viewModel = createViewModel(tag = "科幻", searchRepo = searchRepo)
            advanceUntilIdle()

            viewModel.onSelectType(2) // 动画
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.selectedType)
            assertEquals(listOf(2), searchRepo.lastAdvancedRequest?.filter?.type)
            assertEquals(listOf("科幻"), searchRepo.lastAdvancedRequest?.filter?.tag)
        }

    @Test
    fun onSelectSort_reloadsSubjectsWithNewSort() =
        runTest {
            val searchRepo = FakeSearchRepository()
            val viewModel = createViewModel(tag = "日常", searchRepo = searchRepo)
            advanceUntilIdle()

            viewModel.onSelectSort(SearchSort.SCORE)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(SearchSort.SCORE, state.selectedSort)
            assertEquals("score", searchRepo.lastAdvancedRequest?.sort)
        }

    @Test
    fun loadMore_appendsUniqueSubjects() =
        runTest {
            val searchRepo = FakeSearchRepository()
            val firstBatch = List(20) { sampleSubject.copy(id = it.toLong() + 1) }
            val secondBatch = List(5) { sampleSubject.copy(id = it.toLong() + 21) }
            searchRepo.advancedSearchResult = AppResult.Success(firstBatch)

            val viewModel = createViewModel(tag = "京阿尼", searchRepo = searchRepo)
            advanceUntilIdle()

            assertEquals(20, viewModel.uiState.value.subjects.size)
            assertTrue(viewModel.uiState.value.hasMore)

            searchRepo.advancedSearchResult = AppResult.Success(secondBatch)
            viewModel.loadMore()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(25, state.subjects.size)
            assertFalse(state.hasMore) // < 20 means no more
            assertFalse(state.isLoadingMore)
        }

    @Test
    fun loadSubjectsError_showsErrorMessage() =
        runTest {
            val searchRepo = FakeSearchRepository()
            searchRepo.advancedSearchResult = AppResult.Error(RuntimeException("Network error"), "网络异常，请重试")

            val viewModel = createViewModel(tag = "芳文社", searchRepo = searchRepo)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals("网络异常，请重试", state.errorMessage)
            assertTrue(state.subjects.isEmpty())
        }

    @Test
    fun updateCollection_callsRepository() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            val viewModel = createViewModel(collectionRepo = collectionRepo)
            advanceUntilIdle()

            viewModel.updateCollection(sampleSubject, CollectionType.DOING)
            advanceUntilIdle()

            assertEquals(1, collectionRepo.updateCollectionCallCount)
        }
}
