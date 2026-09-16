package com.infinitezerone.minibgm.feature.assistant

import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.PendingAction

enum class MessageRole {
    USER,
    ASSISTANT,
}

enum class ActionStatus {
    PENDING,
    EXECUTING,
    SUCCESS,
    REJECTED,
    FAILED,
}

data class PendingActionCardState(
    val action: PendingAction,
    val status: ActionStatus = ActionStatus.PENDING,
    val errorMessage: String? = null,
)

data class AssistantMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val pendingActions: List<PendingActionCardState> = emptyList(),
    val isError: Boolean = false,
)

data class AssistantUiState(
    val messages: List<AssistantMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val aiConfig: AiConfig = AiConfig(),
    val showConfigDialog: Boolean = false,
)

sealed interface AssistantUiEvent {
    data class ShowSnackbar(
        val message: String,
    ) : AssistantUiEvent

    data class NavigateToSubject(
        val subjectId: Long,
    ) : AssistantUiEvent
}
