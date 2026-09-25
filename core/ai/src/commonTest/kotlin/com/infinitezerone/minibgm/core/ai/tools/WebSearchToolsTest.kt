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
    fun searchWeb_emptyQuery_returnsError() =
        runTest {
            val repo = FakeWebSearchRepository()
            val tools = WebSearchTools(repo)
            val result = tools.searchWeb("")
            assertTrue(result.contains("blank"))
            assertTrue(repo.searchCalls.isEmpty())
        }

    @Test
    fun searchWeb_success_returnsJsonResults() =
        runTest {
            val repo =
                FakeWebSearchRepository().apply {
                    searchResult =
                        AppResult.Success(
                            listOf(
                                WebSearchResult(
                                    title = "测试动漫",
                                    url = "https://example.com/play/1",
                                    snippet = "测试播放简介",
                                ),
                            ),
                        )
                }
            val tools = WebSearchTools(repo)
            val result = tools.searchWeb("测试")
            assertTrue(result.contains("https://example.com/play/1"))
            assertTrue(result.contains("测试动漫"))
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
    fun normalizeQuery_scopes_source_queries_to_github() {
        val tools = WebSearchTools(FakeWebSearchRepository())
        // 普通资讯搜索不加限定
        assertEquals("葬送的芙莉莲 播出时间", tools.normalizeQuery("葬送的芙莉莲 播出时间"))

        // 搜源相关自动追加 GitHub TVBox 限定
        assertEquals("site:github.com tvbox 动漫源", tools.normalizeQuery("动漫源"))
        assertEquals("site:github.com tvbox 高质量动漫播放源", tools.normalizeQuery("高质量动漫播放源"))
        assertEquals("site:github.com tvbox 动漫 订阅", tools.normalizeQuery("动漫 订阅"))

        // 已经带有 site: 作用域的不重复添加
        assertEquals("site:github.com tvbox 动漫", tools.normalizeQuery("site:github.com tvbox 动漫"))
    }
}
