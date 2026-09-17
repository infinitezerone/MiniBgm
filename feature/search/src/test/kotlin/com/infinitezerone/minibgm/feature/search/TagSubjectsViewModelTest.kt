package com.infinitezerone.minibgm.feature.search

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SearchSubjectsRequest
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
        searchRepo: SearchRepository = FakeSearchRepository(),
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

    @Test
    fun onSelectType_cancelsInFlightLoadMore_staleResultsNotAppended() =
        runTest {
            // 回归：类型切换时在途的"加载更多"必须被取消。旧实现只取消 searchJob，
            // 挂起的 loadMore 完成后会把旧过滤条件的条目追加进新类型列表
            val fake = FakeSearchRepository()
            val releaseLoadMore = CompletableDeferred<Unit>()
            var lastRequest: SearchSubjectsRequest? = null
            val searchRepo =
                object : SearchRepository {
                    override suspend fun searchSubjects(
                        query: String,
                        type: Int,
                        sort: String?,
                        limit: Int,
                        offset: Int,
                    ) = fake.searchSubjects(query, type, sort, limit, offset)

                    override suspend fun searchSubjectsAdvanced(
                        request: SearchSubjectsRequest,
                        limit: Int,
                        offset: Int,
                    ): AppResult<List<Subject>> {
                        lastRequest = request
                        return if (offset > 0) {
                            // 挂起直至测试放行；若取消逻辑正确，恢复时所在协程已被取消
                            releaseLoadMore.await()
                            AppResult.Success(List(20) { sampleSubject.copy(id = 10_000L + it) })
                        } else {
                            AppResult.Success(List(20) { sampleSubject.copy(id = it.toLong() + 1) })
                        }
                    }

                    override fun getSearchHistory(): Flow<List<String>> = fake.getSearchHistory()

                    override suspend fun addSearchHistory(query: String) = fake.addSearchHistory(query)

                    override suspend fun removeSearchHistory(query: String) = fake.removeSearchHistory(query)

                    override suspend fun clearSearchHistory() = fake.clearSearchHistory()
                }

            val viewModel = createViewModel(searchRepo = searchRepo)
            advanceUntilIdle()
            assertEquals(20, viewModel.uiState.value.subjects.size)

            viewModel.loadMore()
            runCurrent() // loadMore 已挂起在 releaseLoadMore 上

            viewModel.onSelectType(2) // 切换类型：应取消在途 loadMore 并重置 isLoadingMore
            releaseLoadMore.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(2), lastRequest?.filter?.type)
            // Unconfined 调度下新类型首页会即时加载（20 条 id<10000）；
            // 被取消的在途 loadMore 若未被取消，会追加 20 条 id>=10000 的旧过滤结果
            assertTrue("旧过滤条件的加载结果不得混入新类型列表", state.subjects.none { it.id >= 10_000L })
            assertEquals(20, state.subjects.size)
            assertFalse(state.isLoadingMore)
            assertTrue(state.hasMore)
        }
}
