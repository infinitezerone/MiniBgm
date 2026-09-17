package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 社区评论/吐槽的用户简要信息
 */
@Serializable
data class CommentUser(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: UserAvatar? = null,
    val sign: String = "",
    val isFriend: Boolean = false,
) {
    val displayName: String
        get() = nickname.ifBlank { username }
}

/**
 * 点赞/表情表态的用户信息
 */
@Serializable
data class CommentReactionUser(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
)

/**
 * 评论点赞/表情表态统计与用户列表
 */
@Serializable
data class CommentReaction(
    val value: Int = 0,
    val users: List<CommentReactionUser> = emptyList(),
) {
    val count: Int
        get() = users.size
}

/**
 * 单集吐槽楼中楼回复
 */
@Serializable
data class EpisodeCommentReply(
    val id: Long = 0,
    @SerialName("creatorID") val creatorId: Long = 0,
    @SerialName("relatedID") val relatedId: Long = 0,
    val createdAt: Long = 0,
    val content: String = "",
    val state: Int = 0,
    val user: CommentUser? = null,
)

/**
 * 单集吐槽（包含发帖人、点赞反应与楼中楼回复）
 */
@Serializable
data class EpisodeComment(
    val id: Long = 0,
    @SerialName("mainID") val mainId: Long = 0,
    @SerialName("creatorID") val creatorId: Long = 0,
    @SerialName("relatedID") val relatedId: Long = 0,
    val createdAt: Long = 0,
    val content: String = "",
    val state: Int = 0,
    val user: CommentUser? = null,
    val reactions: List<CommentReaction> = emptyList(),
    val replies: List<EpisodeCommentReply> = emptyList(),
)

/**
 * 条目全站短评/吐槽
 */
@Serializable
data class SubjectComment(
    val id: Long = 0,
    val user: CommentUser? = null,
    val type: Int = 0,
    val rate: Int = 0,
    val comment: String = "",
    val updatedAt: Long = 0,
    val reactions: List<CommentReaction> = emptyList(),
)

/**
 * 条目全站短评分页数据
 */
@Serializable
data class SubjectCommentPage(
    val total: Int = 0,
    val data: List<SubjectComment> = emptyList(),
)

/**
 * 条目讨论版/论坛话题帖
 */
@Serializable
data class SubjectTopic(
    val id: Long = 0,
    val title: String = "",
    @SerialName("creatorID") val creatorId: Long = 0,
    @SerialName("parentID") val parentId: Long = 0,
    val replyCount: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val creator: CommentUser? = null,
)

/**
 * 条目讨论版分页数据
 */
@Serializable
data class SubjectTopicPage(
    val total: Int = 0,
    val data: List<SubjectTopic> = emptyList(),
)
