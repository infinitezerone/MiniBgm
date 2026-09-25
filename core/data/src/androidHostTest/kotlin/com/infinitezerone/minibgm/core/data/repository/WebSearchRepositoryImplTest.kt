package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.WebSearchResult
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import com.infinitezerone.minibgm.core.network.StreamProbe
import com.infinitezerone.minibgm.core.network.WebSearchService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebSearchRepositoryImplTest {
    private class FakeWebSearchService : WebSearchService {
        var results = emptyList<WebSearchResult>()
        var shouldThrow = false

        override suspend fun search(
            query: String,
            limit: Int,
        ): List<WebSearchResult> {
            if (shouldThrow) throw IllegalStateException("Network failure")
            return results
        }
    }

    private class FakePageFetchService : PageFetchService {
        var pageToReturn: FetchedPage? = null

        override suspend fun fetchHtml(
            url: String,
            requestHeaders: Map<String, String>,
        ): FetchedPage? = pageToReturn

        override suspend fun postForm(
            url: String,
            formData: Map<String, String>,
            requestHeaders: Map<String, String>,
        ): FetchedPage? = null

        override suspend fun probeStream(
            url: String,
            requestHeaders: Map<String, String>,
        ): StreamProbe = StreamProbe.Unsupported
    }

    @Test
    fun searchWeb_success_returnsList() =
        runTest {
            val searchService =
                FakeWebSearchService().apply {
                    results =
                        listOf(
                            WebSearchResult(
                                title = "搜索结果1",
                                url = "https://example.com/1",
                                snippet = "摘要1",
                            ),
                        )
                }
            val repo = WebSearchRepositoryImpl(searchService, FakePageFetchService())
            val result = repo.searchWeb("test")

            assertIs<AppResult.Success<List<WebSearchResult>>>(result)
            assertEquals(1, result.data.size)
            assertEquals("搜索结果1", result.data[0].title)
        }

    @Test
    fun searchWeb_failure_returnsError() =
        runTest {
            val searchService = FakeWebSearchService().apply { shouldThrow = true }
            val repo = WebSearchRepositoryImpl(searchService, FakePageFetchService())
            val result = repo.searchWeb("test")

            assertIs<AppResult.Error>(result)
            assertTrue(result.message.contains("Network failure"))
        }

    @Test
    fun fetchWebContent_extractsReadableText() =
        runTest {
            val fetchService =
                FakePageFetchService().apply {
                    pageToReturn =
                        FetchedPage(
                            url = "https://example.com",
                            html =
                                """
                                <html>
                                <head>
                                    <style>body { color: red; }</style>
                                    <script>alert(1);</script>
                                </head>
                                <body>
                                    <h1>动漫标题</h1>
                                    <p>这是正文内容描述。</p>
                                </body>
                                </html>
                                """.trimIndent(),
                        )
                }
            val repo = WebSearchRepositoryImpl(FakeWebSearchService(), fetchService)
            val result = repo.fetchWebContent("https://example.com")

            assertIs<AppResult.Success<String>>(result)
            val text = result.data
            assertTrue(text.contains("动漫标题"))
            assertTrue(text.contains("这是正文内容描述。"))
            assertTrue(!text.contains("alert(1)"))
            assertTrue(!text.contains("color: red"))
        }
}
