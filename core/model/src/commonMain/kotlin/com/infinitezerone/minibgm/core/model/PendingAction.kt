package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Human-In-The-Loop (HITL) 安全机制待确认操作。
 * AI 智能体执行写操作（如修改收藏状态、打卡单集、评分）时，
 * 严禁在未获用户同意前静默修改用户数据，必须生成 [PendingAction] 提案等待用户确认。
 */
@Serializable
sealed interface PendingAction {
    val actionId: String
    val subjectId: Long
    val description: String

    /**
     * 更新条目收藏状态提案。
     */
    @Serializable
    @SerialName("update_collection")
    data class UpdateCollection(
        override val actionId: String,
        override val subjectId: Long,
        val subjectTitle: String = "",
        val collectionType: CollectionType,
        val rating: Int? = null,
        val comment: String? = null,
        val isPrivate: Boolean = false,
        override val description: String,
    ) : PendingAction

    /**
     * 更新单集观看进度提案。
     */
    @Serializable
    @SerialName("update_episode")
    data class UpdateEpisode(
        override val actionId: String,
        override val subjectId: Long,
        val subjectTitle: String = "",
        val episodeNumber: Int,
        val isWatched: Boolean = true,
        override val description: String,
    ) : PendingAction
}

/**
 * 智能体工具执行后返回的提案封装。
 */
@Serializable
data class ActionProposal(
    val status: String = "PENDING_CONFIRMATION",
    val message: String,
    val action: PendingAction,
)
