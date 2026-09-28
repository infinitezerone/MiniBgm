package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.model.SearchResult
import com.infinitezerone.minibgm.core.model.SearchSubjectsRequest
import com.infinitezerone.minibgm.core.model.Subject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSearchRepository : SearchRepository {
    var searchCallCount: Int = 0
        private set
    var lastSearchOffset: Int? = null
        private set
    var lastSearchLimit: Int? = null
        private set
    var searchResult: AppResult<SearchResult> = AppResult.Success(SearchResult())

    var advancedSearchCallCount: Int = 0
        private set
    var lastAdvancedRequest: SearchSubjectsRequest? = null
        private set
    var lastAdvancedOffset: Int? = null
        private set
    var lastAdvancedLimit: Int? = null
        private set
    var advancedSearchResult: AppResult<List<Subject>> = AppResult.Success(emptyList())

    /**
     * 覆盖响应里的 `total`；负数表示"未设置"，此时取列表长度，
     * 语义等价于"服务端总共就这么多条"（即当前已是最后一页）。
     * 需要模拟"还有下一页"时显式设一个更大的值。
     */
    var advancedSearchTotal: Int = -1

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    var addHistoryCallCount: Int = 0
        private set

    fun setInitialHistory(history: List<String>) {
        _searchHistory.value = history
    }

    override fun getSearchHistory(): Flow<List<String>> = _searchHistory.asStateFlow()

    override suspend fun addSearchHistory(query: String) {
        addHistoryCallCount++
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val updated = listOf(trimmed) + (_searchHistory.value - trimmed)
        _searchHistory.value = updated.take(20)
    }

    override suspend fun removeSearchHistory(query: String) {
        _searchHistory.value = _searchHistory.value - query.trim()
    }

    override suspend fun clearSearchHistory() {
        _searchHistory.value = emptyList()
    }

    var lastSort: String? = null
        private set

    override suspend fun searchSubjects(
        query: String,
        type: Int,
        sort: String?,
        limit: Int,
        offset: Int,
    ): AppResult<SearchResult> {
        searchCallCount++
        lastSort = sort
        lastSearchOffset = offset
        lastSearchLimit = limit
        return searchResult
    }

    override suspend fun searchSubjectsAdvanced(
        request: SearchSubjectsRequest,
        limit: Int,
        offset: Int,
    ): AppResult<SearchResult> {
        advancedSearchCallCount++
        lastAdvancedRequest = request
        lastAdvancedOffset = offset
        lastAdvancedLimit = limit
        return when (val result = advancedSearchResult) {
            is AppResult.Success ->
                AppResult.Success(
                    SearchResult(
                        total = if (advancedSearchTotal >= 0) advancedSearchTotal else result.data.size,
                        list = result.data,
                    ),
                )
            is AppResult.Error -> result
            is AppResult.Loading -> result
        }
    }
}
