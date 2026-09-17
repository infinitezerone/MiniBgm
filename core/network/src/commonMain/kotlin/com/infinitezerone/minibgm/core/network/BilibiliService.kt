package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

/** Bilibili 逐话播出排期节点 */
data class BilibiliAiringEpisode(
    val episode: Int,
    /** 秒级 UNIX 时间戳 */
    val airAtEpochSeconds: Long,
)

/**
 * Bilibili 番剧/国创单集播出时间查询服务。
 * 针对国创及 B 站独家番剧，从官方 Web API 直接获取每集真实的 `pub_time` 秒级排期。
 */
interface BilibiliService {
    /**
     * 根据 bilibiliSiteId 查询逐话播出时间。
     * @param bilibiliSiteId 可以是 "23352451" (media_id), "md23352451", "ss68585", 或 "ep12345"
     * @return 逐话时间列表（按 episode 升序）
     */
    suspend fun getAiringEpisodes(bilibiliSiteId: String): List<BilibiliAiringEpisode>
}

@Serializable
internal data class BilibiliReviewUserResponse(
    val code: Int = 0,
    val result: BilibiliReviewResult? = null,
)

@Serializable
internal data class BilibiliReviewResult(
    val media: BilibiliMediaInfo? = null,
)

@Serializable
internal data class BilibiliMediaInfo(
    val season_id: Long? = null,
)

@Serializable
internal data class BilibiliSeasonResponse(
    val code: Int = 0,
    val result: BilibiliSeasonResult? = null,
)

@Serializable
internal data class BilibiliSeasonResult(
    val episodes: List<BilibiliEpisodeItem> = emptyList(),
)

@Serializable
internal data class BilibiliEpisodeItem(
    val title: String = "",
    val pub_time: Long = 0L,
    val pv: Int = 0,
)

class BilibiliServiceImpl(
    private val client: HttpClient,
) : BilibiliService {
    override suspend fun getAiringEpisodes(bilibiliSiteId: String): List<BilibiliAiringEpisode> {
        val cleanId = bilibiliSiteId.trim()
        if (cleanId.isBlank()) return emptyList()

        return when {
            cleanId.startsWith("ss", ignoreCase = true) -> {
                val seasonId = cleanId.substring(2).toLongOrNull() ?: return emptyList()
                fetchEpisodes(seasonId = seasonId)
            }
            cleanId.startsWith("ep", ignoreCase = true) -> {
                val epId = cleanId.substring(2).toLongOrNull() ?: return emptyList()
                fetchEpisodes(epId = epId)
            }
            else -> {
                val mediaIdStr = if (cleanId.startsWith("md", ignoreCase = true)) cleanId.substring(2) else cleanId
                val mediaId = mediaIdStr.toLongOrNull() ?: return emptyList()
                val seasonId = resolveSeasonId(mediaId) ?: return emptyList()
                fetchEpisodes(seasonId = seasonId)
            }
        }
    }

    private suspend fun resolveSeasonId(mediaId: Long): Long? =
        runCatching {
            val resp: BilibiliReviewUserResponse =
                client
                    .get("https://api.bilibili.com/pgc/review/user") {
                        timeout {
                            requestTimeoutMillis = 15_000
                            connectTimeoutMillis = 10_000
                            socketTimeoutMillis = 15_000
                        }
                        parameter("media_id", mediaId)
                    }.body()
            resp.result?.media?.season_id
        }.getOrNull()

    private suspend fun fetchEpisodes(
        seasonId: Long? = null,
        epId: Long? = null,
    ): List<BilibiliAiringEpisode> =
        runCatching {
            val resp: BilibiliSeasonResponse =
                client
                    .get("https://api.bilibili.com/pgc/view/web/season") {
                        timeout {
                            requestTimeoutMillis = 15_000
                            connectTimeoutMillis = 10_000
                            socketTimeoutMillis = 15_000
                        }
                        if (seasonId != null) {
                            parameter("season_id", seasonId)
                        }
                        if (epId != null) {
                            parameter("ep_id", epId)
                        }
                    }.body()
            val episodes = resp.result?.episodes.orEmpty()
            episodes
                .filter { it.pv == 0 && it.pub_time > 0L }
                .mapNotNull { item ->
                    val epNum = item.title.trim().toIntOrNull() ?: return@mapNotNull null
                    BilibiliAiringEpisode(episode = epNum, airAtEpochSeconds = item.pub_time)
                }.sortedBy { it.episode }
        }.getOrElse { emptyList() }
}
