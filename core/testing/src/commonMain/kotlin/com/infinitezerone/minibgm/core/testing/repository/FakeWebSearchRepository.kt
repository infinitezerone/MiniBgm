package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.WebSearchRepository
import com.infinitezerone.minibgm.core.model.WebSearchResult

class FakeWebSearchRepository : WebSearchRepository {
    var searchResult: AppResult<List<WebSearchResult>> = AppResult.Success(emptyList())
    var fetchResult: AppResult<String> = AppResult.Success("")
    val searchCalls = mutableListOf<String>()
    val fetchCalls = mutableListOf<String>()

    override suspend fun searchGitHub(
        query: String,
        limit: Int,
    ): AppResult<List<WebSearchResult>> {
        searchCalls.add(query)
        return searchResult
    }

    override suspend fun fetchWebContent(url: String): AppResult<String> {
        fetchCalls.add(url)
        return fetchResult
    }
}
