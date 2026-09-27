package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
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
    val anilistId: Long,
    /** bgmId 可空：CI 侧桥接/搜索兜底未覆盖时为 null，由客户端降级解析 */
    val bgmId: Long? = null,
    val title: String,
    val titleCn: String? = null,
    val countryOfOrigin: String = "JP",
    val format: String = "",
    val status: String = "",
    val coverUrl: String? = null,
    val isAdult: Boolean = false,
    val startYear: Int = 0,
    val startMonth: Int = 0,
    val airDate: String? = null,
    val sites: List<ScheduleSnapshotSiteDto> = emptyList(),
    val episodes: List<ScheduleSnapshotEpisodeDto> = emptyList(),
)

@Serializable
data class ScheduleSnapshotEpisodeDto(
    val n: Int,
    val t: Long,
)

interface ScheduleSnapshotService {
    /** 拉取当前快照；所有 CDN 均不可达时抛出异常，由调用方降级到本地缓存。 */
    suspend fun getSnapshot(): ScheduleSnapshotDto
}

class ScheduleSnapshotServiceImpl(
    private val client: HttpClient,
    private val cdnUrls: List<String> = DEFAULT_SCHEDULE_SNAPSHOT_URLS,
) : ScheduleSnapshotService {
    override suspend fun getSnapshot(): ScheduleSnapshotDto {
        var lastException: Throwable? = null
        for (url in cdnUrls) {
            try {
                val response =
                    client.get(url) {
                        timeout {
                            requestTimeoutMillis = 30_000
                            connectTimeoutMillis = 15_000
                            socketTimeoutMillis = 30_000
                        }
                    }
                if (response.status == HttpStatusCode.OK) {
                    return response.body<ScheduleSnapshotDto>()
                }
            } catch (e: Throwable) {
                lastException = e
            }
        }
        throw lastException ?: IllegalStateException("Failed to fetch schedule snapshot from CDN endpoints")
    }

    companion object {
        val DEFAULT_SCHEDULE_SNAPSHOT_URLS =
            listOf(
                "https://cdn.jsdelivr.net/gh/infinitezerone/minibgm-schedule-data@main/data/snapshot.json",
                "https://fastly.jsdelivr.net/gh/infinitezerone/minibgm-schedule-data@main/data/snapshot.json",
                "https://gcore.jsdelivr.net/gh/infinitezerone/minibgm-schedule-data@main/data/snapshot.json",
            )
    }
}

// ---------- 快照 → 排期管线的桥接载体（原 AniListService 域模型，语义不变） ----------

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
    /** 条目开播年（0 = AniList 未提供）；用于按需定位 bangumi-data 的 begin 月切片 */
    val startYear: Int = 0,
    /** 条目开播月（0/13 = 未知） */
    val startMonth: Int = 0,
    val isAdult: Boolean = false,
)
