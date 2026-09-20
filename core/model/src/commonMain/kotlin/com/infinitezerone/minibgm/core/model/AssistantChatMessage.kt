package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ActionCardStatus {
    PENDING,
    EXECUTING,
    SUCCESS,
    REJECTED,
    FAILED,
}

@Serializable
data class PendingActionCard(
    val action: PendingAction,
    val status: ActionCardStatus = ActionCardStatus.PENDING,
    val errorMessage: String? = null,
)

@Serializable
enum class ChatMessageRole {
    USER,
    ASSISTANT,
}

@Serializable
data class AssistantChatMessage(
    val id: String,
    val role: ChatMessageRole,
    val content: String,
    val timestamp: Long,
    val pendingActions: List<PendingActionCard> = emptyList(),
    val playableSources: PlayableEpisodeList? = null,
    val isError: Boolean = false,
)
