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
        return searchResult
    }

    override suspend fun searchSubjectsAdvanced(
        request: SearchSubjectsRequest,
        limit: Int,
        offset: Int,
    ): AppResult<List<Subject>> {
        advancedSearchCallCount++
        lastAdvancedRequest = request
        lastAdvancedOffset = offset
        lastAdvancedLimit = limit
        return advancedSearchResult
    }
}
