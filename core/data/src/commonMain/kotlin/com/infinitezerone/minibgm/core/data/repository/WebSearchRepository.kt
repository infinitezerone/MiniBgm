package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.WebSearchResult
import com.infinitezerone.minibgm.core.network.PageFetchService
import com.infinitezerone.minibgm.core.network.WebSearchService
import kotlinx.coroutines.CancellationException

/**
 * 开源仓库检索与网页正文提取数据仓库。
 */
interface WebSearchRepository {
    /**
     * 检索 GitHub 官方公开代码仓库，返回仓库名、直达链接与星标描述列表。
     */
    suspend fun searchGitHub(
        query: String,
        limit: Int = 10,
    ): AppResult<List<WebSearchResult>>

    /**
     * 读取指定公网网页的正文文本（已过滤脚本、样式与 HTML 标签）。
     */
    suspend fun fetchWebContent(url: String): AppResult<String>
}

class WebSearchRepositoryImpl(
    private val webSearchService: WebSearchService,
    private val pageFetchService: PageFetchService,
) : WebSearchRepository {
    override suspend fun searchGitHub(
        query: String,
        limit: Int,
    ): AppResult<List<WebSearchResult>> =
        try {
            val results = webSearchService.search(query, limit)
            AppResult.Success(results)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Error(e, "GitHub 搜索失败：${e.message}")
        }

    override suspend fun fetchWebContent(url: String): AppResult<String> =
        try {
            val page = pageFetchService.fetchHtml(url)
            if (page == null) {
                AppResult.Error(IllegalStateException("Failed to fetch page"), "无法访问该网页或请求失败：$url")
            } else {
                val cleanText = extractReadableText(page.html)
                AppResult.Success(cleanText)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Error(e, "读取网页内容失败：${e.message}")
        }

    private fun extractReadableText(html: String): String {
        val noScript = html.replace(Regex("""<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>""", RegexOption.IGNORE_CASE), " ")
        val noStyle = noScript.replace(Regex("""<style\b[^<]*(?:(?!<\/style>)<[^<]*)*<\/style>""", RegexOption.IGNORE_CASE), " ")
        val noTags = noStyle.replace(Regex("<[^>]+>"), "\n")
        return noTags
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .take(4000)
    }
}
