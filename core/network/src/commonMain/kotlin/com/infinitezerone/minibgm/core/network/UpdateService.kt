package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubReleaseAssetDto> = emptyList(),
)

@Serializable
data class GitHubReleaseAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L,
    @SerialName("content_type") val contentType: String? = null,
)

interface UpdateService {
    /** 获取最新的 GitHub Release 信息 */
    suspend fun getLatestRelease(): GitHubReleaseDto
}

internal class UpdateServiceImpl(
    private val client: HttpClient,
    private val releaseApiUrl: String = DEFAULT_RELEASE_API_URL,
) : UpdateService {
    override suspend fun getLatestRelease(): GitHubReleaseDto {
        val response =
            client.get(releaseApiUrl) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                timeout {
                    requestTimeoutMillis = 10_000
                    connectTimeoutMillis = 8_000
                    socketTimeoutMillis = 10_000
                }
            }
        if (response.status == HttpStatusCode.OK) {
            return response.body<GitHubReleaseDto>()
        }
        if (response.status == HttpStatusCode.NotFound) {
            throw BgmNetworkException.NotFound("未找到版本发布")
        }
        if (response.status == HttpStatusCode.TooManyRequests) {
            throw BgmNetworkException.RateLimited()
        }
        throw BgmNetworkException.ServerError(response.status.value)
    }

    companion object {
        const val DEFAULT_RELEASE_API_URL = "https://api.github.com/repos/infinitezerone/MiniBgm/releases/latest"
    }
}
