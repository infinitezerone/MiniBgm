package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.SubjectCommentPage
import com.infinitezerone.minibgm.core.model.SubjectTopicPage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Bangumi 新版只读社区服务接口 (基于 next.bgm.tv/p1 私有公开端点)
 */
interface BangumiCommunityService {
    /** 获取单集吐槽列表与楼中楼回复 */
    suspend fun getEpisodeComments(episodeId: Long): List<EpisodeComment>

    /** 获取条目全站短评流 */
    suspend fun getSubjectComments(
        subjectId: Long,
        limit: Int = 20,
        offset: Int = 0,
    ): SubjectCommentPage

    /** 获取条目关联讨论版帖子列表 */
    suspend fun getSubjectTopics(
        subjectId: Long,
        limit: Int = 10,
        offset: Int = 0,
    ): SubjectTopicPage
}

class BangumiCommunityServiceImpl(
    private val client: HttpClient,
    private val baseUrl: String = "https://next.bgm.tv",
) : BangumiCommunityService {
    override suspend fun getEpisodeComments(episodeId: Long): List<EpisodeComment> =
        client.get("$baseUrl/p1/episodes/$episodeId/comments").body()

    override suspend fun getSubjectComments(
        subjectId: Long,
        limit: Int,
        offset: Int,
    ): SubjectCommentPage =
        client
            .get("$baseUrl/p1/subjects/$subjectId/comments") {
                parameter("limit", limit)
                parameter("offset", offset)
            }.body()

    override suspend fun getSubjectTopics(
        subjectId: Long,
        limit: Int,
        offset: Int,
    ): SubjectTopicPage =
        client
            .get("$baseUrl/p1/subjects/$subjectId/topics") {
                parameter("limit", limit)
                parameter("offset", offset)
            }.body()
}
