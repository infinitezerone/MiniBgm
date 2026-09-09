package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.model.CharacterDetail
import com.infinitezerone.minibgm.core.model.PersonDetail
import com.infinitezerone.minibgm.core.model.RelatedWork
import com.infinitezerone.minibgm.core.model.SearchSubjectsRequest
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectRelation
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.network.model.CalendarDayResponse
import com.infinitezerone.minibgm.core.network.model.EpisodePageResponse
import com.infinitezerone.minibgm.core.network.model.PageResponse
import com.infinitezerone.minibgm.core.network.model.SearchSubjectResponse
import com.infinitezerone.minibgm.core.network.model.UserCollectionPageResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserCollectionStatusGroup(
    val type: Int = 0,
    val name: String = "",
    @SerialName("name_cn") val nameCn: String = "",
    val collects: List<UserCollectionStatusEntry> = emptyList(),
)

@Serializable
data class UserCollectionStatusEntry(
    val status: UserCollectionStatusDetail,
    val count: Int = 0,
)

@Serializable
data class UserCollectionStatusDetail(
    val id: Int = 0,
    val type: String = "",
    val name: String = "",
)

interface BangumiApiService {
    suspend fun getCalendar(): List<CalendarDayResponse>

    suspend fun getSubject(id: Long): Subject

    suspend fun getSubjectCharacters(id: Long): List<SubjectCharacter>

    suspend fun getCharacter(id: Long): CharacterDetail

    suspend fun getCharacterSubjects(id: Long): List<RelatedWork>

    suspend fun getSubjectPersons(id: Long): List<SubjectPerson>

    suspend fun getPerson(id: Long): PersonDetail

    suspend fun getPersonSubjects(id: Long): List<RelatedWork>

    suspend fun getSubjectRelations(id: Long): List<SubjectRelation>

    suspend fun getEpisodes(
        subjectId: Long,
        limit: Int = 100,
        offset: Int = 0,
    ): EpisodePageResponse

    suspend fun searchSubjects(
        keyword: String,
        type: Int = 2,
        limit: Int = 30,
        offset: Int = 0,
    ): SearchSubjectResponse

    suspend fun searchSubjectsAdvanced(
        request: SearchSubjectsRequest,
        limit: Int = 30,
        offset: Int = 0,
    ): PageResponse<Subject>

    /**
     * 获取指定用户的条目收藏列表。
     *
     * 注意：Bangumi OpenAPI v0 规范规定此 GET 端点必须传入显式用户名或数字用户 ID（[username]），传入 "-" 会返回 404 Not Found。
     */
    suspend fun getUserCollections(
        username: String,
        subjectType: Int = 2,
        type: Int? = null,
        limit: Int = 30,
        offset: Int = 0,
    ): UserCollectionPageResponse

    suspend fun getMe(): UserProfile

    /**
     * 获取指定用户对特定条目的收藏状态。
     *
     * 若未收藏或不存在则返回 null（映射自 404）。
     */
    suspend fun getCollection(
        username: String,
        subjectId: Long,
    ): UserCollection?

    /**
     * 修改当前登录用户的条目收藏状态（POST /v0/users/-/collections/{subject_id}）。
     *
     * 注：[epStatus] 仅用于书籍类条目进度，动画进度需通过 [updateEpisodeStatus] 单集打卡驱动。
     */
    suspend fun updateCollection(
        subjectId: Long,
        type: Int,
        rate: Int? = null,
        comment: String? = null,
        private: Boolean = false,
        epStatus: Int? = null,
    )

    /**
     * 修改当前登录用户对特定章节的打卡状态（PATCH /v0/users/-/collections/{subject_id}/episodes）。
     *
     * @param type 0=未看/撤销, 1=想看, 2=看过, 3=抛弃
     */
    suspend fun updateEpisodeStatus(
        subjectId: Long,
        episodeId: Long,
        type: Int,
    )

    /**
     * 获取指定用户的全量条目收藏状态与分类统计（GET /user/{username}/collections/status?app_id={clientId}）。
     *
     * 该接口单次请求即可返回所有大类（动画、书籍、音乐、游戏、三次元）及各状态的统计。
     */
    suspend fun getUserCollectionStats(username: String): List<UserCollectionStatusGroup> = emptyList()
}

class BangumiApiServiceImpl(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.bgm.tv",
    private val authConfig: BgmAuthConfig = BgmAuthConfig(),
) : BangumiApiService {
    override suspend fun getCalendar(): List<CalendarDayResponse> = client.get("$baseUrl/calendar").body()

    override suspend fun getSubject(id: Long): Subject = client.get("$baseUrl/v0/subjects/$id").body()

    override suspend fun getSubjectCharacters(id: Long): List<SubjectCharacter> = client.get("$baseUrl/v0/subjects/$id/characters").body()

    override suspend fun getCharacter(id: Long): CharacterDetail = client.get("$baseUrl/v0/characters/$id").body()

    override suspend fun getCharacterSubjects(id: Long): List<RelatedWork> = client.get("$baseUrl/v0/characters/$id/subjects").body()

    override suspend fun getSubjectPersons(id: Long): List<SubjectPerson> = client.get("$baseUrl/v0/subjects/$id/persons").body()

    override suspend fun getPerson(id: Long): PersonDetail = client.get("$baseUrl/v0/persons/$id").body()

    override suspend fun getPersonSubjects(id: Long): List<RelatedWork> = client.get("$baseUrl/v0/persons/$id/subjects").body()

    override suspend fun getSubjectRelations(id: Long): List<SubjectRelation> = client.get("$baseUrl/v0/subjects/$id/subjects").body()

    override suspend fun getEpisodes(
        subjectId: Long,
        limit: Int,
        offset: Int,
    ): EpisodePageResponse =
        client
            .get("$baseUrl/v0/episodes") {
                parameter("subject_id", subjectId)
                parameter("limit", limit)
                parameter("offset", offset)
            }.body()

    override suspend fun searchSubjects(
        keyword: String,
        type: Int,
        limit: Int,
        offset: Int,
    ): SearchSubjectResponse {
        // 关键字作为单个 path 段编码，含 ?/#// 的输入不会改变请求语义
        val url =
            URLBuilder(baseUrl)
                .apply {
                    appendPathSegments("search", "subject", keyword)
                }.buildString()
        return client
            .get(url) {
                if (type > 0) parameter("type", type)
                parameter("responseGroup", "medium")
                parameter("max_results", limit)
                parameter("start", offset)
            }.body()
    }

    override suspend fun searchSubjectsAdvanced(
        request: SearchSubjectsRequest,
        limit: Int,
        offset: Int,
    ): PageResponse<Subject> =
        client
            .post("$baseUrl/v0/search/subjects") {
                parameter("limit", limit)
                parameter("offset", offset)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

    override suspend fun getUserCollections(
        username: String,
        subjectType: Int,
        type: Int?,
        limit: Int,
        offset: Int,
    ): UserCollectionPageResponse =
        client
            .get("$baseUrl/v0/users/$username/collections") {
                if (subjectType > 0) parameter("subject_type", subjectType)
                if (type != null) parameter("type", type)
                parameter("limit", limit)
                parameter("offset", offset)
            }.body()

    override suspend fun getMe(): UserProfile = client.get("$baseUrl/v0/me").body()

    override suspend fun getCollection(
        username: String,
        subjectId: Long,
    ): UserCollection? =
        try {
            client.get("$baseUrl/v0/users/$username/collections/$subjectId").body()
        } catch (e: BgmNetworkException.NotFound) {
            null
        }

    @Serializable
    private data class CollectionUpdateBody(
        val type: Int,
        val rate: Int? = null,
        val comment: String? = null,
        val `private`: Boolean = false,
        @kotlinx.serialization.SerialName("ep_status") val epStatus: Int? = null,
    )

    override suspend fun updateCollection(
        subjectId: Long,
        type: Int,
        rate: Int?,
        comment: String?,
        private: Boolean,
        epStatus: Int?,
    ) {
        client.post("$baseUrl/v0/users/-/collections/$subjectId") {
            contentType(ContentType.Application.Json)
            setBody(
                CollectionUpdateBody(
                    type = type,
                    rate = rate,
                    comment = comment,
                    private = private,
                    epStatus = epStatus,
                ),
            )
        }
    }

    @Serializable
    private data class EpisodeStatusUpdateBody(
        val episode_id: List<Long>,
        val type: Int,
    )

    // Authorization: Bearer 由 Ktor Auth 插件统一注入，见 BgmHttpClient
    override suspend fun updateEpisodeStatus(
        subjectId: Long,
        episodeId: Long,
        type: Int,
    ) {
        client.patch("$baseUrl/v0/users/-/collections/$subjectId/episodes") {
            contentType(ContentType.Application.Json)
            setBody(EpisodeStatusUpdateBody(episode_id = listOf(episodeId), type = type))
        }
    }

    override suspend fun getUserCollectionStats(username: String): List<UserCollectionStatusGroup> =
        client
            .get("$baseUrl/user/$username/collections/status") {
                parameter("app_id", authConfig.clientId)
            }.body()
}
