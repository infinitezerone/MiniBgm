package com.infinitezerone.minibgm.feature.search

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Rating
import com.infinitezerone.minibgm.core.model.SearchResult
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        searchRepo: FakeSearchRepository = FakeSearchRepository(),
        collectionRepo: FakeCollectionRepository = FakeCollectionRepository(),
        authRepo: FakeAuthRepository = FakeAuthRepository(initialLoggedIn = true),
    ) = SearchViewModel(searchRepo, collectionRepo, authRepo)

    @Test
    fun initialStateIsEmptyAndNotLoading() {
        val searchRepo = FakeSearchRepository()
        val viewModel = createViewModel(searchRepo = searchRepo)

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertEquals(0, state.selectedType)
        assertEquals(SearchSort.MATCH, state.selectedSort)
        assertEquals(SearchViewMode.LIST, state.viewMode)
        assertFalse(state.isLoading)
        assertFalse(state.isLoadingMore)
        assertFalse(state.hasMore)
        assertEquals(0, state.totalCount)
        assertTrue(state.results.isEmpty())
        assertNull(state.error)
        assertEquals(0, searchRepo.searchCallCount)
    }

    @Test
    fun onQueryChangeTriggersDebouncedSearch() =
        runTest {
            val repository = FakeSearchRepository()
            repository.searchResult = AppResult.Success(SearchResult(total = 1, list = listOf(sampleSubject)))
            val viewModel = createViewModel(searchRepo = repository)

            viewModel.onQueryChange("芙莉莲")

            assertEquals("芙莉莲", viewModel.uiState.value.query)
            advanceTimeBy(200)
            assertEquals(0, repository.searchCallCount)

            advanceTimeBy(150)
            advanceUntilIdle()

            assertEquals(1, repository.searchCallCount)
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(1, state.results.size)
            assertEquals(1, state.totalCount)
            assertFalse(state.hasMore)
            assertEquals("葬送的芙莉莲", state.results.first().nameCn)
            assertNull(state.error)
        }

    @Test
    fun onQueryChangeWithBlankResetsResultsAndDoesNotSearch() =
        runTest {
            val repository = FakeSearchRepository()
            repository.searchResult = AppResult.Success(SearchResult(total = 1, list = listOf(sampleSubject)))
            val viewModel = createViewModel(searchRepo = repository)

            viewModel.onQueryChange("芙莉莲")
            advanceUntilIdle()
            assertEquals(1, repository.searchCallCount)
            assertEquals(1, viewModel.uiState.value.results.size)

            viewModel.onQueryChange("   ")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("   ", state.query)
            assertTrue(state.results.isEmpty())
            assertEquals(0, state.totalCount)
            assertFalse(state.hasMore)
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertEquals(1, repository.searchCallCount)
        }

    @Test
    fun onTypeSelectSwitchesTypeAndSearchesWhenQueryPresent() =
        runTest {
            val repository = FakeSearchRepository()
            repository.searchResult = AppResult.Success(SearchResult(total = 1, list = listOf(sampleSubject)))
            val viewModel = createViewModel(searchRepo = repository)

            viewModel.onQueryChange("芙莉莲")
            advanceUntilIdle()
            assertEquals(1, repository.searchCallCount)

            viewModel.onTypeSelect(2)
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.selectedType)
            assertEquals(2, repository.searchCallCount)
        }

    @Test
    fun onTypeSelectDoesNotSearchWhenQueryBlank() =
        runTest {
            val repository = FakeSearchRepository()
            val viewModel = createViewModel(searchRepo = repository)

            viewModel.onTypeSelect(2)
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.selectedType)
            assertEquals(0, repository.searchCallCount)
        }

    @Test
    fun searchTriggersImmediateSearchWithoutDebounce() =
        runTest {
            val repository = FakeSearchRepository()
            repository.searchResult = AppResult.Success(SearchResult(total = 1, list = listOf(sampleSubject)))
            val viewModel = createViewModel(searchRepo = repository)

            viewModel.onQueryChange("EVA")
            viewModel.search()
            advanceUntilIdle()

            assertEquals(1, repository.searchCallCount)
            assertEquals(1, viewModel.uiState.value.results.size)
        }

    @Test
    fun clearQueryClearsAllState() =
        runTest {
            val repository = FakeSearchRepository()
            repository.searchResult = AppResult.Success(SearchResult(total = 1, list = listOf(sampleSubject)))
            val viewModel = createViewModel(searchRepo = repository)

            viewModel.onQueryChange("芙莉莲")
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.results.size)

            viewModel.clearQuery()

            val state = viewModel.uiState.value
            assertEquals("", state.query)
            assertTrue(state.results.isEmpty())
            assertEquals(0, state.totalCount)
            assertFalse(state.hasMore)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }

    @Test
    fun searchFailureSetsErrorState() =
        runTest {
            val repository = FakeSearchRepository()
            repository.searchResult = AppResult.Error(RuntimeException("网络连接失败"), "搜索请求失败")
            val viewModel = createViewModel(searchRepo = repository)

            viewModel.onQueryChange("测试")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.results.isEmpty())
            assertEquals("搜索请求失败", state.error)
        }

    @Test
    fun searchSuccessClearsPreviousError() =
        runTest {
            val repository = FakeSearchRepository()
            repository.searchResult = AppResult.Error(RuntimeException("网络连接失败"), "搜索请求失败")
            val viewModel = createViewModel(searchRepo = repository)

            viewModel.onQueryChange("测试")
            advanceUntilIdle()
            assertEquals("搜索请求失败", viewModel.uiState.value.error)

            repository.searchResult = AppResult.Success(SearchResult(total = 1, list = listOf(sampleSubject)))
            viewModel.search()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.error)
            assertEquals(1, state.results.size)
        }

    @Test
    fun rapidQueryChangesOnlyExecutesLatestQuery() =
        runTest {
            val repository = FakeSearchRepository()
            repository.searchResult = AppResult.Success(SearchResult(total = 1, list = listOf(sampleSubject)))
            val viewModel = createViewModel(searchRepo = repository)

            viewModel.onQueryChange("a")
            advanceTimeBy(100)
            viewModel.onQueryChange("ab")
            advanceTimeBy(100)
            viewModel.onQueryChange("abc")
            advanceTimeBy(100)
            assertEquals(0, repository.searchCallCount)

            advanceTimeBy(300)
            advanceUntilIdle()

            assertEquals(1, repository.searchCallCount)
            assertEquals("abc", viewModel.uiState.value.query)
        }

    @Test
    fun searchPopulatesTotalCountAndHasMore() =
        runTest {
            val repository = FakeSearchRepository()
            repository.searchResult =
                AppResult.Success(
                    SearchResult(
                        total = 43,
                        list = List(22) { sampleSubject.copy(id = 1000L + it) },
                    ),
                )
            val viewModel = createViewModel(searchRepo = repository)

            viewModel.onQueryChange("鬼灭之刃")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(22, state.results.size)
            assertEquals(43, state.totalCount)
            assertTrue(state.hasMore)
            assertFalse(state.isLoadingMore)
        }

    @Test
    fun loadMoreAppendsRemainingItemsAndCompletes() =
        runTest {
            val repository = FakeSearchRepository()
            val firstBatch = List(22) { sampleSubject.copy(id = 1000L + it) }
            repository.searchResult =
                AppResult.Success(
                    SearchResult(
                        total = 43,
                        list = firstBatch,
                    ),
                )
            val viewModel = createViewModel(searchRepo = repository)

            viewModel.onQueryChange("鬼灭之刃")
            advanceUntilIdle()
            assertEquals(22, viewModel.uiState.value.results.size)

            val secondBatch = List(21) { sampleSubject.copy(id = 2000L + it) }
            repository.searchResult =
                AppResult.Success(
                    SearchResult(
                        total = 43,
                        list = secondBatch,
                    ),
                )

            viewModel.loadMore()
            advanceUntilIdle()

            val updatedState = viewModel.uiState.value
            assertEquals(43, updatedState.results.size)
            assertEquals(43, updatedState.totalCount)
            assertFalse(updatedState.hasMore)
            assertFalse(updatedState.isLoadingMore)
        }

    @Test
    fun onSortChangeSortsByScoreAndRank() =
        runTest {
            val repository = FakeSearchRepository()
            val item1 = sampleSubject.copy(id = 1L, rating = Rating(score = 7.5, rank = 100))
            val item2 = sampleSubject.copy(id = 2L, rating = Rating(score = 9.2, rank = 2))
            val item3 = sampleSubject.copy(id = 3L, rating = Rating(score = 8.1, rank = 20))

            repository.searchResult = AppResult.Success(SearchResult(total = 3, list = listOf(item1, item2, item3)))
            val viewModel = createViewModel(searchRepo = repository)

            viewModel.onQueryChange("测试")
            advanceUntilIdle()

            // 切换为高分优先，验证即时排序及服务端参数传递
            viewModel.onSortChange(SearchSort.SCORE)
            advanceUntilIdle()
            assertEquals("score", repository.lastSort)
            val scoreSorted = viewModel.uiState.value.results
            assertEquals(2L, scoreSorted[0].id) // 9.2
            assertEquals(3L, scoreSorted[1].id) // 8.1
            assertEquals(1L, scoreSorted[2].id) // 7.5

            // 切换为排名靠前，验证即时排序及服务端参数传递
            viewModel.onSortChange(SearchSort.RANK)
            advanceUntilIdle()
            assertEquals("rank", repository.lastSort)
            val rankSorted = viewModel.uiState.value.results
            assertEquals(2L, rankSorted[0].id) // rank 2
            assertEquals(3L, rankSorted[1].id) // rank 20
            assertEquals(1L, rankSorted[2].id) // rank 100

            // 切换为热门收藏
            viewModel.onSortChange(SearchSort.HEAT)
            advanceUntilIdle()
            assertEquals("heat", repository.lastSort)
        }

    @Test
    fun onViewModeToggleSwitchesBetweenListAndGrid() {
        val viewModel = createViewModel()
        assertEquals(SearchViewMode.LIST, viewModel.uiState.value.viewMode)

        viewModel.onViewModeToggle()
        assertEquals(SearchViewMode.GRID, viewModel.uiState.value.viewMode)

        viewModel.onViewModeToggle()
        assertEquals(SearchViewMode.LIST, viewModel.uiState.value.viewMode)
    }

    @Test
    fun toggleCollectionWhenNotLoggedInShowsDialog() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false)
            val viewModel = createViewModel(authRepo = authRepo)

            viewModel.toggleCollection(sampleSubject, CollectionType.DOING)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.showLoginPromptDialog)
        }

    @Test
    fun toggleCollectionWhenLoggedInOptimisticallyUpdatesAndAdaptsVerbs() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = true)
            val collectionRepo = FakeCollectionRepository()
            val viewModel = createViewModel(collectionRepo = collectionRepo, authRepo = authRepo)

            // 书籍条目 (type = 1)
            val bookSubject = sampleSubject.copy(id = 888L, type = SubjectType.BOOK.value)

            viewModel.toggleCollection(bookSubject, CollectionType.DOING)
            advanceUntilIdle()

            // 验证 0ms 乐观更新成功
            assertEquals(CollectionType.DOING, viewModel.uiState.value.userCollections[888L])
            // 验证动词为书籍对应的“在读”
            assertEquals("已标记为「在读」", viewModel.uiState.value.userMessage)
        }

    @Test
    fun toggleCollectionWhenAlreadyInTargetTypeIsNoOp() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = true)
            val collectionRepo = FakeCollectionRepository()
            val viewModel = createViewModel(collectionRepo = collectionRepo, authRepo = authRepo)

            // 先标记为 DOING
            viewModel.toggleCollection(sampleSubject, CollectionType.DOING)
            advanceUntilIdle()

            assertEquals(CollectionType.DOING, viewModel.uiState.value.userCollections[sampleSubject.id])
            assertEquals(1, collectionRepo.updateCollectionCallCount)

            // 再次点击相同状态 DOING，应为幂等 no-op，不再发起打卡调用，保留原状态
            viewModel.toggleCollection(sampleSubject, CollectionType.DOING)
            advanceUntilIdle()

            assertEquals(CollectionType.DOING, viewModel.uiState.value.userCollections[sampleSubject.id])
            assertEquals(1, collectionRepo.updateCollectionCallCount)
        }

    @Test
    fun searchHistoryIsLoadedAndUpdatedOnSearch() =
        runTest {
            val repository = FakeSearchRepository()
            repository.setInitialHistory(listOf("命运石之门"))
            val viewModel = createViewModel(searchRepo = repository)
            advanceUntilIdle()

            assertEquals(listOf("命运石之门"), viewModel.uiState.value.searchHistory)

            viewModel.onQueryChange("芙莉莲")
            advanceTimeBy(350)
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.searchHistory
                    .contains("芙莉莲"),
            )
            assertEquals(
                "芙莉莲",
                viewModel.uiState.value.searchHistory
                    .first(),
            )
            assertEquals(1, repository.addHistoryCallCount)
        }

    @Test
    fun deleteHistoryItemAndClearAllHistoryWorks() =
        runTest {
            val repository = FakeSearchRepository()
            repository.setInitialHistory(listOf("EVA", "电锯人", "芙莉莲"))
            val viewModel = createViewModel(searchRepo = repository)
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.searchHistory.size)

            viewModel.deleteHistoryItem("电锯人")
            advanceUntilIdle()
            assertEquals(listOf("EVA", "芙莉莲"), viewModel.uiState.value.searchHistory)

            viewModel.clearAllHistory()
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.searchHistory
                    .isEmpty(),
            )
        }

    @Test
    fun beginLogin_dismissesPromptAndReturnsUrl() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false)
            val viewModel = createViewModel(authRepo = authRepo)
            advanceUntilIdle()

            viewModel.toggleCollection(sampleSubject, CollectionType.DOING)
            assertTrue(viewModel.uiState.value.showLoginPromptDialog)

            val url = viewModel.beginLogin()

            assertFalse(viewModel.uiState.value.showLoginPromptDialog)
            assertTrue(url.contains("bgm.tv/oauth/authorize"))
            assertEquals(1, authRepo.beginLoginCallCount)
        }
}
