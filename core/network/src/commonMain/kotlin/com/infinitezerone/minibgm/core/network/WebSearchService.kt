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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GitHub 官方代码仓库搜索 API 响应实体。
 */
@Serializable
internal data class GitHubSearchResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val items: List<GitHubRepoItem> = emptyList(),
)

@Serializable
internal data class GitHubRepoItem(
    val name: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val description: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
)

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

/**
 * 基于公网 Bing 网页检索结果的纯 HTML 抓取实现（零凭据轻量级方案）。
 *
 * 【定性说明与架构边界】：
 * 1. 纯降级兜底方案（Best-effort Fallback）：
 *    本服务不依赖任何第三方收费的搜索 API（如 Google Custom Search、Bing Web Search API、Tavily），
 *    仅作为 AI 智能体查询公网公开信息的轻量级兜底检索。
 * 2. 脆弱性与静默失效风险（Fragility & Silent Failure Risk）：
 *    直接依赖 Bing 搜索页面的前端 DOM 结构（如 `<li class="b_algo">`、`<h2><a href>`、`<p>`）。
 *    若搜索引擎前端改版（DOM class 变化）或由于公网 IP 高频访问触发反爬风控/人机验证（Captcha / WAF / Challenge），
 *    该抓取可能在 HTTP 200 下返回无法解析的空列表，具有固有的静默失效风险。
 * 3. 看门狗与异常诊断（Diagnostic Watchdog）：
 *    在接收到 HTTP 200 响应但解析出 0 条结果时，看门狗会主动检测页面是否包含人机验证（Captcha/Challenge）、
 *    阻断提示或 DOM 结构变更，并通过结构化日志输出明确警示（避免将抓取失效误判为"搜索词无结果"）。
 *    严格遵循 fail-open 原则，任何异常或阻断均安全降级为 emptyList()，绝不允许阻塞宿主流程。
 */
class WebSearchServiceImpl(
    private val client: HttpClient,
    private val json: Json = BgmHttpClient.jsonConfig,
) : WebSearchService {
    private val logger = bgmLogger("Bgm/WebSearch")

    override suspend fun search(
        query: String,
        limit: Int,
    ): List<WebSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val boundedLimit = limit.coerceIn(1, 20)

        if (isGitHubTargeted(trimmed)) {
            try {
                val gitHubResults = searchGitHubApi(trimmed, boundedLimit)
                if (gitHubResults.isNotEmpty()) {
                    logger.d { "GitHub API search returned ${gitHubResults.size} items for '$trimmed'" }
                    return gitHubResults
                }
                logger.w { "GitHub API search returned 0 items for '$trimmed', falling back to Bing" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w { "GitHub API search error for '$trimmed': ${e.message}, falling back to Bing" }
            }
        }

        return searchBing(trimmed, boundedLimit)
    }

    internal fun isGitHubTargeted(query: String): Boolean {
        val lower = query.lowercase()
        return lower.contains("github") ||
            lower.contains("tvbox") ||
            lower.contains("订阅源") ||
            lower.contains("播放源") ||
            lower.contains("影视仓") ||
            lower.contains("接口") ||
            (lower.contains("源") && (lower.contains("动漫") || lower.contains("番") || lower.contains("看")))
    }

    internal fun extractGitHubQuery(raw: String): String {
        val withoutSite =
            raw
                .replace(Regex("""site:\S*github\.com\S*""", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("""\bgithub\b""", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        return if (withoutSite.isBlank()) "tvbox" else withoutSite
    }

    internal suspend fun searchGitHubApi(
        query: String,
        limit: Int,
    ): List<WebSearchResult> {
        val effectiveQuery = extractGitHubQuery(query)
        if (effectiveQuery.isBlank()) return emptyList()

        val results = executeGitHubSearchRequest(effectiveQuery, limit)
        if (results.isNotEmpty()) return results

        // 若多余修饰词导致 0 结果，以标准 "tvbox 源" 兜底尝试一次，提升开源订阅召回率
        if (effectiveQuery.contains("tvbox", ignoreCase = true) && effectiveQuery != "tvbox 源" && effectiveQuery != "tvbox") {
            logger.d { "GitHub query '$effectiveQuery' yielded 0 items, retrying with 'tvbox 源'" }
            return executeGitHubSearchRequest("tvbox 源", limit)
        }
        return emptyList()
    }

    private suspend fun executeGitHubSearchRequest(
        query: String,
        limit: Int,
    ): List<WebSearchResult> {
        val encodedQuery = query.encodeURLQueryComponent()
        val url = "https://api.github.com/search/repositories?q=$encodedQuery&sort=stars&order=desc&per_page=$limit"

        val response =
            client.get(url) {
                header(
                    HttpHeaders.UserAgent,
                    "MiniBgm-Android/1.0 (Bangumi Client)",
                )
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }

        if (!response.status.isSuccess()) {
            logger.w { "GitHub search HTTP ${response.status.value} for query: $query" }
            return emptyList()
        }

        val jsonText = response.bodyAsText()
        return parseGitHubRepositoriesJson(jsonText)
    }

    internal fun parseGitHubRepositoriesJson(jsonText: String): List<WebSearchResult> =
        try {
            val response = json.decodeFromString<GitHubSearchResponse>(jsonText)
            response.items.map { item ->
                val desc = item.description?.trim().orEmpty()
                val snippet =
                    buildString {
                        if (desc.isNotBlank()) {
                            append(desc)
                            append(" | ")
                        }
                        append("⭐ Stars: ${item.stargazersCount}")
                        append(" | GitHub 开源项目")
                    }
                WebSearchResult(
                    title = "${item.fullName} (⭐ ${item.stargazersCount})",
                    url = item.htmlUrl,
                    snippet = snippet,
                )
            }
        } catch (e: Exception) {
            logger.w { "Failed to parse GitHub search JSON: ${e.message}" }
            emptyList()
        }

    internal suspend fun searchBing(
        trimmed: String,
        boundedLimit: Int,
    ): List<WebSearchResult> {
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
            val results = parseBingHtml(html, boundedLimit)
            if (results.isEmpty()) {
                inspectEmptyResultsWatchdog(html, trimmed)
            }
            results
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "Web search exception for query '$trimmed': ${e.message}" }
            emptyList()
        }
    }

    /**
     * 看门狗诊断：当 HTTP 成功但解析结果为 0 条时，分析 HTML 识别是真无结果、遭遇反爬拦截还是 DOM 改版。
     */
    internal fun inspectEmptyResultsWatchdog(
        html: String,
        query: String,
    ) {
        val lowerHtml = html.lowercase()
        val isCaptchaOrChallenge =
            lowerHtml.contains("challenge") ||
                lowerHtml.contains("captcha") ||
                lowerHtml.contains("verification") ||
                lowerHtml.contains("unusual traffic") ||
                lowerHtml.contains("robot") ||
                lowerHtml.contains("人机验证") ||
                lowerHtml.contains("安全验证")

        if (isCaptchaOrChallenge) {
            logger.w {
                "Bing search triggered bot detection or captcha verification for query '$query'. " +
                    "Web search scraping is temporarily blocked by provider."
            }
            return
        }

        // 页面存在大量内容（> 1000 字符）却未能匹配任何 b_algo 搜索条目，大概率是 Bing 前端 DOM 结构变更
        if (html.length > 1000 && !html.contains("class=\"b_algo\"") && !html.contains("class='b_algo'")) {
            val titleMatch = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.IGNORE_CASE).find(html)
            val pageTitle =
                titleMatch
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    .orEmpty()
            logger.w {
                "Bing search HTML layout mismatch (possible DOM update): received ${html.length} chars, " +
                    "page title: '$pageTitle', 0 results parsed for query '$query'"
            }
        } else {
            logger.d { "Bing search returned 0 results for query '$query' (clean empty result page)" }
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
            if (isDictionaryOrMapNoise(cleanTitle, rawUrl)) continue

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

    private fun isDictionaryOrMapNoise(
        title: String,
        url: String,
    ): Boolean {
        if (url.contains("/zidian/") ||
            url.contains("zdic.net") ||
            url.contains("gaode.com") ||
            url.contains("map.baidu.com") ||
            url.contains("dict.revised.moe.edu.tw")
        ) {
            return true
        }
        val lower = title.lowercase()
        return lower.contains("（汉语文字）") ||
            lower.contains("（汉语汉字）") ||
            lower.contains("（中国姓氏）") ||
            lower.contains("字源辞典") ||
            lower.contains("新华字典") ||
            lower.contains("康熙字典") ||
            lower.contains("辭典檢視") ||
            lower.contains("字典-意思-解释")
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
