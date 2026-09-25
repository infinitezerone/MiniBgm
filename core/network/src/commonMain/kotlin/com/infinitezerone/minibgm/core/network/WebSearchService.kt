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
 * GitHub 开源仓库搜索服务接口。
 */
interface WebSearchService {
    /**
     * 检索 GitHub 官方公开代码仓库，返回按 Star 数倒序排列的仓库列表。
     */
    suspend fun search(
        query: String,
        limit: Int = 10,
    ): List<WebSearchResult>
}

/**
 * 基于 GitHub 官方 REST API (https://api.github.com/search/repositories) 的开源仓库检索服务实现。
 */
class WebSearchServiceImpl(
    private val client: HttpClient,
    private val json: Json = BgmHttpClient.jsonConfig,
) : WebSearchService {
    private val logger = bgmLogger("Bgm/GitHubSearch")

    override suspend fun search(
        query: String,
        limit: Int,
    ): List<WebSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val boundedLimit = limit.coerceIn(1, 20)
        val effectiveQuery = extractGitHubQuery(trimmed).ifBlank { trimmed }

        return try {
            val encodedQuery = effectiveQuery.encodeURLQueryComponent()
            val url = "https://api.github.com/search/repositories?q=$encodedQuery&sort=stars&order=desc&per_page=$boundedLimit"

            val response =
                client.get(url) {
                    header(HttpHeaders.UserAgent, "MiniBgm-Android/1.0 (Bangumi Client)")
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                }

            if (!response.status.isSuccess()) {
                logger.w { "GitHub search HTTP ${response.status.value} for query: $effectiveQuery" }
                return emptyList()
            }

            val jsonText = response.bodyAsText()
            parseGitHubRepositoriesJson(jsonText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "GitHub search error for '$effectiveQuery': ${e.message}" }
            emptyList()
        }
    }

    internal fun extractGitHubQuery(raw: String): String =
        raw
            .replace(Regex("""site:\S*github\.com\S*""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\bgithub\.com\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\bgithub\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

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
}
