package com.infinitezerone.minibgm.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.infinitezerone.minibgm.core.ai.AiToolActivity
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.WebSearchRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 公网实时搜索与网页阅读工具集。
 *
 * 赋予 AI 助手主动检索互联网的能力，用于查询最新番剧资讯、挖掘第三方动漫站点、
 * 验证外部资源链接与获取不在本地 Bangumi 数据库内的通用二次元信息。
 */
class WebSearchTools(
    private val webSearchRepository: WebSearchRepository,
    private val json: Json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        },
) : ToolSet {
    @Tool
    @LLMDescription(
        "Search the public World Wide Web for real-time information, online anime streaming websites, " +
            "release schedules, discussions, or topics beyond the local Bangumi database. " +
            "Returns a list of search hits with page titles, direct URLs, and text snippets.",
    )
    suspend fun searchWeb(
        @LLMDescription("Search query keywords (e.g. '葬送的芙莉莲 在线观看' or '动漫 在线播放 网站 推荐')")
        query: String,
        @LLMDescription("Max number of search results to return (default 6, range 1..10)")
        limit: Int = 6,
    ): String {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return "Search query must not be blank."
        }
        AiToolActivity.report("公网搜索", "关键词：$trimmed")
        val boundedLimit = limit.coerceIn(1, 10)

        return when (val result = webSearchRepository.searchWeb(trimmed, boundedLimit)) {
            is AppResult.Success -> {
                val list = result.data
                if (list.isEmpty()) {
                    "No web search results found for '$trimmed'."
                } else {
                    json.encodeToString(list)
                }
            }
            is AppResult.Error -> {
                "Web search failed: ${result.throwable.message ?: "network error"}"
            }
            is AppResult.Loading -> {
                "Web search in progress, please retry."
            }
        }
    }

    @Tool
    @LLMDescription(
        "Fetch and read the plain readable text content of a specific web page URL. " +
            "Automatically strips HTML markup, scripts, and stylesheets. " +
            "Use this after searchWeb to examine the contents of a promising search result.",
    )
    suspend fun fetchWebContent(
        @LLMDescription("The absolute HTTP/HTTPS URL of the web page to read")
        url: String,
    ): String {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "Invalid URL: must be an absolute http:// or https:// URL."
        }
        AiToolActivity.report("读取网页", trimmed.take(45))

        return when (val result = webSearchRepository.fetchWebContent(trimmed)) {
            is AppResult.Success -> {
                val text = result.data
                if (text.isBlank()) {
                    "Web page was loaded but returned no readable text content."
                } else {
                    text
                }
            }
            is AppResult.Error -> {
                "Failed to fetch web content from $trimmed: ${result.throwable.message ?: "network error"}"
            }
            is AppResult.Loading -> {
                "Fetching web content in progress, please retry."
            }
        }
    }
}
