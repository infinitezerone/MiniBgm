package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.model.CommunityLikeTarget
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.SubjectCommentPage
import com.infinitezerone.minibgm.core.model.SubjectTopicPage
import com.infinitezerone.minibgm.core.model.TopicDetail
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Bangumi 新版社区服务接口 (基于 next.bgm.tv/p1 私有公开端点)
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

    /** 获取条目讨论版帖子详情（包含主楼正文与楼层楼中楼回帖） */
    suspend fun getSubjectTopicDetail(topicId: Long): TopicDetail

    /** 获取小组讨论帖子详情（包含主楼正文与楼层楼中楼回帖） */
    suspend fun getGroupTopicDetail(topicId: Long): TopicDetail

    /**
     * 对楼层/吐槽添加一个表情表态（需登录态 Bearer）。
     * [reactionValue] 为 bgm.tv 表情类型 id（如楼层既有 reactions 里的 value）。
     */
    suspend fun setLike(
        target: CommunityLikeTarget,
        id: Long,
        reactionValue: Int,
    )

    /** 取消自己在该楼层/吐槽上的表态 */
    suspend fun removeLike(
        target: CommunityLikeTarget,
        id: Long,
    )
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

    override suspend fun getSubjectTopicDetail(topicId: Long): TopicDetail = client.get("$baseUrl/p1/subjects/-/topics/$topicId").body()

    override suspend fun getGroupTopicDetail(topicId: Long): TopicDetail = client.get("$baseUrl/p1/groups/-/topics/$topicId").body()

    override suspend fun setLike(
        target: CommunityLikeTarget,
        id: Long,
        reactionValue: Int,
    ) {
        client
            .put("$baseUrl${target.likePath(id)}") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("value" to reactionValue))
            }.body<Map<String, String>>()
    }

    override suspend fun removeLike(
        target: CommunityLikeTarget,
        id: Long,
    ) {
        client
            .delete("$baseUrl${target.likePath(id)}")
            .body<Map<String, String>>()
    }
}

/** 表态范围 → p1 like 端点路径（小组帖子/条目楼层/单集吐槽） */
private fun CommunityLikeTarget.likePath(id: Long): String =
    when (this) {
        CommunityLikeTarget.GROUP_POST -> "/p1/groups/-/posts/$id/like"
        CommunityLikeTarget.SUBJECT_POST -> "/p1/subjects/-/posts/$id/like"
        CommunityLikeTarget.EPISODE_COMMENT -> "/p1/episodes/-/comments/$id/like"
    }
