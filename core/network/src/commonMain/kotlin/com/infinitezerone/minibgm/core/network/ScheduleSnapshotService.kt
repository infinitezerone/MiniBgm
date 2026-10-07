package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/**
 * 放送时刻表快照（minibgm-schedule-snapshot/1）。
 * 由 [minibgm-schedule-data](https://github.com/infinitezerone/minibgm-schedule-data) 的
 * GitHub Actions 定时扫描 AniList 已验证播出事件生成：排期真值的唯一来源，
 * 客户端不再直连 AniList。字段语义见该仓库 README。
 */
@Serializable
data class ScheduleSnapshotDto(
    val schema: String,
    val generatedAt: String,
    val items: List<ScheduleSnapshotItemDto>,
)

@Serializable
data class ScheduleSnapshotSiteDto(
    val site: String = "",
    val id: String = "",
    val url: String = "",
)

@Serializable
data class ScheduleSnapshotItemDto(
    val anilistId: Long? = null,
    /** bgmId 可空：CI 侧桥接/搜索兜底未覆盖时为 null，由客户端降级解析 */
    val bgmId: Long? = null,
    val title: String,
    val titleCn: String? = null,
    val countryOfOrigin: String = "JP",
    val format: String = "",
    val status: String = "",
    val isAdult: Boolean = false,
    val coverUrl: String? = null,
    val startYear: Int = 0,
    val startMonth: Int = 0,
    val airDate: String? = null,
    val ratingScore: Double = 0.0,
    val popularity: Int = 0,
    val totalEpisodes: Int = 0,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val sites: List<ScheduleSnapshotSiteDto> = emptyList(),
    val episodes: List<ScheduleSnapshotEpisodeDto> = emptyList(),
)

@Serializable
data class ScheduleSnapshotEpisodeDto(
    val n: Int,
    val t: Long,
)

sealed interface ScheduleSnapshotResult {
    data class Modified(
        val snapshot: ScheduleSnapshotDto,
        val etag: String?,
    ) : ScheduleSnapshotResult

    data object NotModified : ScheduleSnapshotResult
}

@Serializable
data class SeasonSnapshotDto(
    val schema: String = "minibgm-season-snapshot/1",
    val year: Int = 0,
    val season: String = "",
    val generatedAt: String = "",
    val total: Int = 0,
    val mappedTotal: Int = 0,
    val items: List<ScheduleSnapshotItemDto> = emptyList(),
)

typealias SeasonSnapshotItemDto = ScheduleSnapshotItemDto

interface ScheduleSnapshotService {
    /** 拉取当前快照；所有 CDN 均不可达时抛出异常，由调用方降级到本地缓存。 */
    suspend fun getSnapshot(): ScheduleSnapshotDto =
        when (val result = getSnapshot(null)) {
            is ScheduleSnapshotResult.Modified -> result.snapshot
            is ScheduleSnapshotResult.NotModified -> throw IllegalStateException("Unexpected 304 without ETag")
        }

    /** 支持 ETag / 304 条件请求的快照拉取 */
    suspend fun getSnapshot(ifNoneMatchEtag: String?): ScheduleSnapshotResult

    /** 支持按年+季拉取静态 JSON；CDN 逐个尝试 */
    suspend fun getSeasonSnapshot(
        year: Int,
        seasonKey: String,
    ): SeasonSnapshotDto? = null
}

internal class ScheduleSnapshotServiceImpl(
    private val client: HttpClient,
    private val cdnUrls: List<String> = DEFAULT_SCHEDULE_SNAPSHOT_URLS,
) : ScheduleSnapshotService {
    override suspend fun getSnapshot(ifNoneMatchEtag: String?): ScheduleSnapshotResult {
        var lastException: Throwable? = null
        for (url in cdnUrls) {
            try {
                val response =
                    client.get(url) {
                        if (!ifNoneMatchEtag.isNullOrBlank()) {
                            header(HttpHeaders.IfNoneMatch, ifNoneMatchEtag)
                        }
                        timeout {
                            requestTimeoutMillis = 8_000
                            connectTimeoutMillis = 5_000
                            socketTimeoutMillis = 8_000
                        }
                    }
                if (response.status == HttpStatusCode.NotModified) {
                    return ScheduleSnapshotResult.NotModified
                }
                if (response.status == HttpStatusCode.OK) {
                    val etag = response.headers[HttpHeaders.ETag]
                    return ScheduleSnapshotResult.Modified(response.body<ScheduleSnapshotDto>(), etag)
                }
            } catch (e: Throwable) {
                lastException = e
            }
        }
        throw lastException ?: IllegalStateException("Failed to fetch schedule snapshot from CDN endpoints")
    }

    override suspend fun getSeasonSnapshot(
        year: Int,
        seasonKey: String,
    ): SeasonSnapshotDto? {
        val path = "data/seasons/$year-$seasonKey.json"
        val urls = cdnUrls.map { it.replace("data/snapshot.json", path) }
        for (url in urls) {
            try {
                val response =
                    client.get(url) {
                        timeout {
                            requestTimeoutMillis = 6_000
                            connectTimeoutMillis = 4_000
                            socketTimeoutMillis = 6_000
                        }
                    }
                if (response.status == HttpStatusCode.OK) {
                    return response.body<SeasonSnapshotDto>()
                }
            } catch (_: Throwable) {
                // 逐个 CDN 尝试
            }
        }
        return null
    }

    companion object {
        val DEFAULT_SCHEDULE_SNAPSHOT_URLS =
            listOf(
                "https://ghproxy.net/https://raw.githubusercontent.com/infinitezerone/minibgm-schedule-data/main/data/snapshot.json",
                "https://fastly.jsdelivr.net/gh/infinitezerone/minibgm-schedule-data@main/data/snapshot.json",
                "https://raw.githubusercontent.com/infinitezerone/minibgm-schedule-data/main/data/snapshot.json",
                "https://gcore.jsdelivr.net/gh/infinitezerone/minibgm-schedule-data@main/data/snapshot.json",
                "https://cdn.jsdelivr.net/gh/infinitezerone/minibgm-schedule-data@main/data/snapshot.json",
            )
    }
}

// ---------- 快照 → 排期管线的桥接载体 ----------

/** AniList 逐话播出排期节点 */
data class AniListAiringEpisode(
    val episode: Int,
    /** 秒级 UNIX 时间戳 */
    val airAtEpochSeconds: Long,
)

/** AniList 媒体播出排期与封面 */
data class AniListMediaSchedule(
    val episodes: List<AniListAiringEpisode> = emptyList(),
    val coverUrl: String? = null,
)

/** AniList 当周排期条目（含精准秒级时间、真实当前集数、标题、海报、独播类型、开播年月） */
data class AniListWeeklyScheduleItem(
    val anilistId: Long,
    val episode: Int,
    /** 秒级 UNIX 播出时间戳 */
    val airAtEpochSeconds: Long,
    val titleNative: String = "",
    val titleRomaji: String = "",
    val coverUrl: String? = null,
    val format: String = "",
    /** 条目开播年（0 = AniList 未提供） */
    val startYear: Int = 0,
    /** 条目开播月（0/13 = 未知） */
    val startMonth: Int = 0,
    val isAdult: Boolean = false,
)
