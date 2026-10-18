package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

/** 快照是多 CDN 串行兜底：单 CDN 8s × 5 最坏 40s，冷启动等不起 */
private const val SNAPSHOT_TOTAL_BUDGET_MILLIS = 20_000L

/** 快照正文上限：正常快照 1–3 MB；无上限时一个坏 CDN 就能把内存打满 */
private const val MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024

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
    private val chainBudgetMillis: Long = SNAPSHOT_TOTAL_BUDGET_MILLIS,
) : ScheduleSnapshotService {
    override suspend fun getSnapshot(ifNoneMatchEtag: String?): ScheduleSnapshotResult =
        try {
            withWallClockTimeout(chainBudgetMillis) {
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
                            return@withWallClockTimeout ScheduleSnapshotResult.NotModified
                        }
                        if (response.status == HttpStatusCode.OK) {
                            val etag = response.headers[HttpHeaders.ETag]
                            val text = response.readLimitedText(MAX_SNAPSHOT_BYTES)
                            val snapshot =
                                BgmHttpClient.jsonConfig.decodeFromString(ScheduleSnapshotDto.serializer(), text)
                            return@withWallClockTimeout ScheduleSnapshotResult.Modified(snapshot, etag)
                        }
                    } catch (e: CancellationException) {
                        // 取消不是"这个 CDN 失败"，继续试下一个会让取消失效
                        throw e
                    } catch (e: Throwable) {
                        lastException = e
                    }
                }
                throw lastException ?: IllegalStateException("Failed to fetch schedule snapshot from CDN endpoints")
            }
        } catch (e: TimeoutCancellationException) {
            // 外层若已取消（调用方自己的 withTimeout），超时只是取消的副产物，必须继续上抛
            currentCoroutineContext().ensureActive()
            throw BgmNetworkException.Timeout(cause = e)
        }

    override suspend fun getSeasonSnapshot(
        year: Int,
        seasonKey: String,
    ): SeasonSnapshotDto? =
        try {
            withWallClockTimeout(chainBudgetMillis) {
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
                            val text = response.readLimitedText(MAX_SNAPSHOT_BYTES)
                            return@withWallClockTimeout BgmHttpClient.jsonConfig
                                .decodeFromString(SeasonSnapshotDto.serializer(), text)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        // 逐个 CDN 尝试
                    }
                }
                null
            }
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            null
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
