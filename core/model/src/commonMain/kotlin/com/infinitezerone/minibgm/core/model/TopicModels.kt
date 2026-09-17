package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 讨论帖回复（包含发帖人、点赞反应与楼中楼子回复）
 */
@Serializable
data class TopicReply(
    val id: Long = 0,
    @SerialName("creatorID") val creatorId: Long = 0,
    val createdAt: Long = 0,
    val content: String = "",
    val state: Int = 0,
    val creator: CommentUser? = null,
    val reactions: List<CommentReaction> = emptyList(),
    val replies: List<TopicReply> = emptyList(),
)

/**
 * 讨论帖所属条目的精简元数据
 */
@Serializable
data class TopicParentSubject(
    val id: Long = 0,
    val name: String = "",
    @SerialName("nameCN") val nameCn: String = "",
    val type: Int = 0,
    val images: SubjectImages? = null,
    val rating: Rating? = null,
) {
    val displayName: String
        get() = nameCn.ifBlank { name }
}

/**
 * 讨论帖所属小组的精简元数据
 */
@Serializable
data class TopicParentGroup(
    val id: Long = 0,
    val name: String = "",
    val title: String = "",
) {
    val displayName: String
        get() = title.ifBlank { name }
}

/**
 * 讨论帖完整详情模型
 */
@Serializable
data class TopicDetail(
    val id: Long = 0,
    val title: String = "",
    @SerialName("creatorID") val creatorId: Long = 0,
    @SerialName("parentID") val parentId: Long = 0,
    val replyCount: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val state: Int = 0,
    val display: Int = 0,
    val creator: CommentUser? = null,
    val subject: TopicParentSubject? = null,
    val group: TopicParentGroup? = null,
    val replies: List<TopicReply> = emptyList(),
) {
    /** 主楼正文（1 楼，楼主原帖） */
    val mainPost: TopicReply?
        get() = replies.firstOrNull()

    /** 楼层回帖列表（2 楼及后续回帖） */
    val floorReplies: List<TopicReply>
        get() = if (replies.size > 1) replies.drop(1) else emptyList()
}
