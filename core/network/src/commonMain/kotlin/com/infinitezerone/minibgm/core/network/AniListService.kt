package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/** AniList 逐话播出排期节点 */
data class AniListAiringEpisode(
    val episode: Int,
    /** 播出时刻的秒级 UNIX 时间戳 */
    val airAtEpochSeconds: Long,
)

/**
 * AniList GraphQL 逐话播出排期（airingSchedule）。
 * 社区实时维护：每话精确到秒，停播/延期通常数小时内更新，
 * 是逐话时间的真值来源；条目 ID 由 bangumi-data sites 映射而来。
 */
interface AniListService {
    /**
     * 批量查询条目的逐话播出时间表。
     * @return anilistId → 逐话时间（按 episode 升序）；查询失败的条目不出现在结果中
     */
    suspend fun getAiringSchedules(anilistIds: List<Long>): Map<Long, List<AniListAiringEpisode>>
}

@Serializable
internal data class AniListGraphQLResponse(
    val data: Map<String, AniListMediaAiring?> = emptyMap(),
)

@Serializable
internal data class AniListMediaAiring(
    val airingSchedule: AniListAiringSchedule? = null,
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
    override suspend fun getAiringSchedules(anilistIds: List<Long>): Map<Long, List<AniListAiringEpisode>> {
        val result = mutableMapOf<Long, List<AniListAiringEpisode>>()
        anilistIds
            .distinct()
            .filter { it > 0 }
            .chunked(chunkSize)
            .forEach { chunk ->
                runCatching {
                    val aliases =
                        chunk.mapIndexed { index, id ->
                            "s$index: Media(id: $id) { airingSchedule { nodes { episode airingAt } } }"
                        }
                    val response =
                        client
                            .post("https://graphql.anilist.co") {
                                contentType(ContentType.Application.Json)
                                setBody(mapOf("query" to "query { ${aliases.joinToString(" ")} }"))
                            }.body<AniListGraphQLResponse>()
                    chunk.forEachIndexed { index, id ->
                        val nodes =
                            response.data["s$index"]
                                ?.airingSchedule
                                ?.nodes
                                .orEmpty()
                        if (nodes.isNotEmpty()) {
                            result[id] = nodes.map { AniListAiringEpisode(it.episode, it.airingAt) }
                        }
                    }
                }
            }
        return result
    }
}
