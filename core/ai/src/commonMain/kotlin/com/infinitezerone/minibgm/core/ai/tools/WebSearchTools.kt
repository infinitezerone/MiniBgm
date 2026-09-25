package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.ai.AiToolActivity
import com.infinitezerone.minibgm.core.ai.aiJson
import com.infinitezerone.minibgm.core.ai.tool.BgmTool
import com.infinitezerone.minibgm.core.ai.tool.bgmTool
import com.infinitezerone.minibgm.core.ai.tool.int
import com.infinitezerone.minibgm.core.ai.tool.schemaObject
import com.infinitezerone.minibgm.core.ai.tool.schemaProperty
import com.infinitezerone.minibgm.core.ai.tool.string
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.WebSearchRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject

/**
 * 公网实时搜索与网页阅读工具集。
 *
 * 赋予 AI 助手主动检索互联网的能力，用于查询最新番剧资讯、挖掘第三方动漫站点、
 * 验证外部资源链接与获取不在本地 Bangumi 数据库内的通用二次元信息。
 */
class WebSearchTools(
    private val webSearchRepository: WebSearchRepository,
    private val json: Json = aiJson,
) {
    fun tools(): List<BgmTool> =
        listOf(
            bgmTool(
                name = "searchWeb",
                description =
                    "Search the public World Wide Web for real-time information, online anime streaming websites, " +
                        "release schedules, discussions, or topics beyond the local Bangumi database. " +
                        "Returns a list of search hits with page titles, direct URLs, and text snippets.",
                parametersJsonSchema =
                    schemaObject(
                        properties =
                            buildJsonObject {
                                put(
                                    "query",
                                    schemaProperty(
                                        "string",
                                        "Search query keywords (e.g. '葬送的芙莉莲 在线观看' or '动漫 在线播放 网站 推荐')",
                                    ),
                                )
                                put(
                                    "limit",
                                    schemaProperty("integer", "Max number of search results to return (default 4, range 1..6)"),
                                )
                            },
                        required = listOf("query"),
                    ),
            ) { args ->
                searchWeb(
                    query = args.string("query"),
                    limit = args.int("limit", 4),
                )
            },
            bgmTool(
                name = "fetchWebContent",
                description =
                    "Fetch and read the plain readable text content of a specific web page URL. " +
                        "Automatically strips HTML markup, scripts, and stylesheets. " +
                        "Use this after searchWeb to examine the contents of a promising search result.",
                parametersJsonSchema =
                    schemaObject(
                        properties =
                            buildJsonObject {
                                put("url", schemaProperty("string", "The absolute HTTP/HTTPS URL of the web page to read"))
                            },
                        required = listOf("url"),
                    ),
            ) { args ->
                fetchWebContent(args.string("url"))
            },
        )

    suspend fun searchWeb(
        query: String,
        limit: Int = 4,
    ): String {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return "Search query must not be blank."
        }
        val effectiveQuery = normalizeQuery(trimmed)
        AiToolActivity.report("公网搜索", "关键词：$effectiveQuery")
        val boundedLimit = limit.coerceIn(1, 6)

        return when (val result = webSearchRepository.searchWeb(effectiveQuery, boundedLimit)) {
            is AppResult.Success -> {
                val list = result.data
                if (list.isEmpty()) {
                    "No web search results found for '$effectiveQuery'. Please try different or more general keywords (e.g. 'site:github.com tvbox 动漫' or 'site:github.com tvbox 接口')."
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

    internal fun normalizeQuery(rawQuery: String): String {
        val trimmed = rawQuery.trim()
        val isSourceSearch =
            trimmed.contains("源") ||
                trimmed.contains("tvbox", ignoreCase = true) ||
                trimmed.contains("订阅")
        val hasSiteScope = trimmed.contains("site:", ignoreCase = true)
        val hasTvbox = trimmed.contains("tvbox", ignoreCase = true)
        return if (isSourceSearch && !hasSiteScope) {
            if (hasTvbox) {
                "site:github.com $trimmed"
            } else {
                "site:github.com tvbox $trimmed"
            }
        } else {
            trimmed
        }
    }

    suspend fun fetchWebContent(url: String): String {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "Invalid URL: must be an absolute http:// or https:// URL."
        }
        AiToolActivity.report("读取网页", trimmed.take(45))

        return when (val result = webSearchRepository.fetchWebContent(trimmed)) {
            is AppResult.Success -> {
                val text = result.data.take(1500)
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
