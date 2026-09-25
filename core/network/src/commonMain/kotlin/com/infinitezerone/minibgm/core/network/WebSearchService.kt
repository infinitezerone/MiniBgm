package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.model.WebSearchResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * 公网 Web 搜索服务接口。
 */
interface WebSearchService {
    /**
     * 执行公网网页搜索，返回包含标题、链接与摘要的搜索结果列表。
     */
    suspend fun search(
        query: String,
        limit: Int = 10,
    ): List<WebSearchResult>
}

class WebSearchServiceImpl(
    private val client: HttpClient,
) : WebSearchService {
    private val logger = bgmLogger("Bgm/WebSearch")

    override suspend fun search(
        query: String,
        limit: Int,
    ): List<WebSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val boundedLimit = limit.coerceIn(1, 20)
        val encodedQuery = trimmed.encodeURLQueryComponent()
        val url = "https://cn.bing.com/search?q=$encodedQuery"

        return try {
            val response =
                client.get(url) {
                    header(
                        HttpHeaders.UserAgent,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    )
                    header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9,en;q=0.8")
                }

            if (!response.status.isSuccess()) {
                logger.w { "Web search failed with HTTP ${response.status.value} for query: $trimmed" }
                return emptyList()
            }

            val html = response.bodyAsText()
            parseBingHtml(html, boundedLimit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "Web search exception for query '$trimmed': ${e.message}" }
            emptyList()
        }
    }

    private fun parseBingHtml(
        html: String,
        limit: Int,
    ): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()
        val algoRegex = Regex("""<li class="b_algo"[^>]*>([\s\S]*?)</li>""")
        val titleRegex = Regex("""<h2[^>]*><a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a></h2>""")
        val captionRegex = Regex("""<p[^>]*>([\s\S]*?)</p>""")

        for (match in algoRegex.findAll(html)) {
            val itemHtml = match.groupValues[1]
            val titleMatch = titleRegex.find(itemHtml) ?: continue
            val rawUrl = titleMatch.groupValues[1]
            if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) continue

            val rawTitle = titleMatch.groupValues[2]
            val cleanTitle = cleanHtmlText(rawTitle)
            if (cleanTitle.isBlank()) continue

            val captionMatch = captionRegex.find(itemHtml)
            val cleanSnippet = captionMatch?.let { cleanHtmlText(it.groupValues[1]) }.orEmpty()

            results.add(
                WebSearchResult(
                    title = cleanTitle,
                    url = rawUrl,
                    snippet = cleanSnippet,
                ),
            )
            if (results.size >= limit) break
        }
        return results
    }

    private fun cleanHtmlText(input: String): String =
        unescapeHtml(input.replace(Regex("<[^>]+>"), " "))
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun unescapeHtml(text: String): String =
        text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&ensp;", " ")
            .replace("&emsp;", " ")
            .replace(Regex("&#([0-9]+);")) { m ->
                runCatching {
                    m.groupValues[1]
                        .toInt()
                        .toChar()
                        .toString()
                }.getOrDefault(m.value)
            }.replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
                runCatching {
                    m.groupValues[1]
                        .toInt(16)
                        .toChar()
                        .toString()
                }.getOrDefault(m.value)
            }
}
