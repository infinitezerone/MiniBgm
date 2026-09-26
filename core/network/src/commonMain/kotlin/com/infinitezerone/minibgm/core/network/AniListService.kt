package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

/** AniList 逐话播出排期节点 */
data class AniListAiringEpisode(
    val episode: Int,
    /** 播出时刻的秒级 UNIX 时间戳 */
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
)

/**
 * AniList GraphQL 逐话播出排期（airingSchedule）与条目元数据。
 * 社区实时维护：每话精确到秒，停播/延期通常数小时内更新，
 * 是逐话时间的真值来源；条目 ID 由 bangumi-data sites 映射而来。
 */
interface AniListService {
    /**
     * 单次复合 GraphQL 请求拉取当周（weekStart 到 weekEnd）全网在播排期（上限 100 部，含 TV 与网络独播）。
     * 是时刻表当周排期的唯一排期真源，杜绝算术预测与漏番。
     */
    suspend fun getWeeklyAiringSchedule(
        weekStartEpochSeconds: Long,
        weekEndEpochSeconds: Long,
    ): List<AniListWeeklyScheduleItem> = emptyList()

    /**
     * 批量查询条目的逐话播出时间表与高清封面。
     * 单次请求按条目拉取上限 50 话的排期（`perPage: 50`）并合并 `nextAiringEpisode` 与 `coverImage`。
     * @param anilistIds 需要查询的 AniList 媒体 ID 列表
     * @return anilistId → 排期与封面信息；查询失败的条目不出现在结果中
     */
    suspend fun getMediaSchedules(anilistIds: List<Long>): Map<Long, AniListMediaSchedule>

    /**
     * 批量查询条目的逐话播出时间表（向后兼容）。
     * @param anilistIds 需要查询的 AniList 媒体 ID 列表
     * @return anilistId → 逐话时间（按 episode 升序）；查询失败的条目不出现在结果中
     */
    suspend fun getAiringSchedules(anilistIds: List<Long>): Map<Long, List<AniListAiringEpisode>> =
        getMediaSchedules(anilistIds).mapValues { it.value.episodes }
}

@Serializable
internal data class AniListGraphQLResponse(
    val data: Map<String, AniListMediaAiring?>? = null,
)

@Serializable
internal data class AniListMediaAiring(
    val coverImage: AniListCoverImage? = null,
    val airingSchedule: AniListAiringSchedule? = null,
    val nextAiringEpisode: AniListAiringNode? = null,
)

@Serializable
internal data class AniListCoverImage(
    val large: String? = null,
)

@Serializable
internal data class AniListAiringSchedule(
    val nodes: List<AniListAiringNode> = emptyList(),
)

@Serializable
internal data class AniListAiringNode(
    val episode: Int,
    val airingAt: Long,
)

/**
 * 通过别名批量化查询（单次请求最多 [chunkSize] 个条目），
 * 未收录/查询失败的条目静默跳过，不影响同批其它条目。
 */
class AniListServiceImpl(
    private val client: HttpClient,
    private val chunkSize: Int = 40,
) : AniListService {
    override suspend fun getMediaSchedules(anilistIds: List<Long>): Map<Long, AniListMediaSchedule> =
        coroutineScope {
            val validIds = anilistIds.distinct().filter { it > 0 }
            if (validIds.isEmpty()) return@coroutineScope emptyMap()

            val deferreds =
                validIds.chunked(chunkSize).map { chunk ->
                    async {
                        val chunkResult = mutableMapOf<Long, AniListMediaSchedule>()
                        runCatching {
                            val aliases =
                                chunk.mapIndexed { index, id ->
                                    "s$index: Media(id: $id) { coverImage { large } airingSchedule(perPage: 50) { nodes { episode airingAt } } nextAiringEpisode { episode airingAt } }"
                                }
                            val response =
                                client
                                    .post("https://graphql.anilist.co") {
                                        contentType(ContentType.Application.Json)
                                        setBody(mapOf("query" to "query { ${aliases.joinToString(" ")} }"))
                                    }.body<AniListGraphQLResponse>()
                            chunk.forEachIndexed { index, id ->
                                val media = response.data?.get("s$index")
                                val coverUrl = media?.coverImage?.large?.takeIf { it.isNotBlank() }
                                val nodes = media?.airingSchedule?.nodes.orEmpty()
                                val next = media?.nextAiringEpisode
                                val combined =
                                    if (next != null && nodes.none { it.episode == next.episode }) {
                                        nodes + next
                                    } else {
                                        nodes
                                    }
                                val episodes =
                                    combined
                                        .map { AniListAiringEpisode(it.episode, it.airingAt) }
                                        .sortedBy { it.episode }
                                if (episodes.isNotEmpty() || coverUrl != null) {
                                    chunkResult[id] = AniListMediaSchedule(episodes = episodes, coverUrl = coverUrl)
                                }
                            }
                        }
                        chunkResult
                    }
                }
            val finalMap = mutableMapOf<Long, AniListMediaSchedule>()
            deferreds.forEach { finalMap.putAll(it.await()) }
            finalMap
        }

    override suspend fun getAiringSchedules(anilistIds: List<Long>): Map<Long, List<AniListAiringEpisode>> =
        getMediaSchedules(anilistIds).mapValues { it.value.episodes }

    override suspend fun getWeeklyAiringSchedule(
        weekStartEpochSeconds: Long,
        weekEndEpochSeconds: Long,
    ): List<AniListWeeklyScheduleItem> =
        runCatching {
            val query =
                """
                query {
                  p1: Page(page: 1, perPage: 50) {
                    airingSchedules(airingAt_greater: $weekStartEpochSeconds, airingAt_lesser: $weekEndEpochSeconds) {
                      episode airingAt
                      media { id format title { native romaji } coverImage { large } startDate { year month } }
                    }
                  }
                  p2: Page(page: 2, perPage: 50) {
                    airingSchedules(airingAt_greater: $weekStartEpochSeconds, airingAt_lesser: $weekEndEpochSeconds) {
                      episode airingAt
                      media { id format title { native romaji } coverImage { large } startDate { year month } }
                    }
                  }
                }
                """.trimIndent()
            val response =
                client
                    .post("https://graphql.anilist.co") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("query" to query))
                    }.body<AniListWeeklyGraphQLResponse>()
            val allEntries =
                (
                    response.data
                        ?.p1
                        ?.airingSchedules
                        .orEmpty() +
                        response.data
                            ?.p2
                            ?.airingSchedules
                            .orEmpty()
                )
            allEntries
                .mapNotNull { entry ->
                    val media = entry.media ?: return@mapNotNull null
                    AniListWeeklyScheduleItem(
                        anilistId = media.id,
                        episode = entry.episode,
                        airAtEpochSeconds = entry.airingAt,
                        titleNative = media.title?.native.orEmpty(),
                        titleRomaji = media.title?.romaji.orEmpty(),
                        coverUrl = media.coverImage?.large,
                        format = media.format.orEmpty(),
                        startYear = media.startDate?.year ?: 0,
                        startMonth = media.startDate?.month ?: 0,
                    )
                }.sortedBy { it.airAtEpochSeconds }
        }.getOrElse { emptyList() }
}

@Serializable
internal data class AniListWeeklyGraphQLResponse(
    val data: AniListWeeklyData? = null,
)

@Serializable
internal data class AniListWeeklyData(
    val p1: AniListPageAiring? = null,
    val p2: AniListPageAiring? = null,
)

@Serializable
internal data class AniListPageAiring(
    val airingSchedules: List<AniListWeeklyScheduleEntry> = emptyList(),
)

@Serializable
internal data class AniListWeeklyScheduleEntry(
    val episode: Int,
    val airingAt: Long,
    val media: AniListWeeklyMediaInfo? = null,
)

@Serializable
internal data class AniListWeeklyMediaInfo(
    val id: Long,
    val format: String? = null,
    val title: AniListWeeklyMediaTitle? = null,
    val coverImage: AniListCoverImage? = null,
    val startDate: AniListWeeklyFuzzyDate? = null,
)

@Serializable
internal data class AniListWeeklyFuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
)

@Serializable
internal data class AniListWeeklyMediaTitle(
    val native: String? = null,
    val romaji: String? = null,
)
