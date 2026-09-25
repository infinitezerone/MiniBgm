package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.WebSearchResult
import com.infinitezerone.minibgm.core.testing.repository.FakeWebSearchRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSearchToolsTest {
    @Test
    fun searchGitHub_emptyQuery_returnsError() =
        runTest {
            val repo = FakeWebSearchRepository()
            val tools = WebSearchTools(repo)
            val result = tools.searchGitHub("")
            assertTrue(result.contains("blank"))
            assertTrue(repo.searchCalls.isEmpty())
        }

    @Test
    fun searchGitHub_success_returnsJsonResults() =
        runTest {
            val repo =
                FakeWebSearchRepository().apply {
                    searchResult =
                        AppResult.Success(
                            listOf(
                                WebSearchResult(
                                    title = "测试仓库",
                                    url = "https://github.com/example/repo",
                                    snippet = "测试仓库简介",
                                ),
                            ),
                        )
                }
            val tools = WebSearchTools(repo)
            val result = tools.searchGitHub("测试")
            assertTrue(result.contains("https://github.com/example/repo"))
            assertTrue(result.contains("测试仓库"))
            assertEquals(listOf("测试"), repo.searchCalls)
        }

    @Test
    fun fetchWebContent_validUrl_returnsContent() =
        runTest {
            val repo =
                FakeWebSearchRepository().apply {
                    fetchResult = AppResult.Success("这是网页正文内容")
                }
            val tools = WebSearchTools(repo)
            val result = tools.fetchWebContent("https://example.com/article")
            assertEquals("这是网页正文内容", result)
            assertEquals(listOf("https://example.com/article"), repo.fetchCalls)
        }

    @Test
    fun normalizeQuery_neutral_whitespace_normalization() {
        val tools = WebSearchTools(FakeWebSearchRepository())
        assertEquals("葬送的芙莉莲 播出时间", tools.normalizeQuery("  葬送的芙莉莲   播出时间  "))
        assertEquals("github bangumi-data", tools.normalizeQuery(" github   bangumi-data "))
        assertEquals("动漫 推荐", tools.normalizeQuery("动漫  推荐"))
    }
}
