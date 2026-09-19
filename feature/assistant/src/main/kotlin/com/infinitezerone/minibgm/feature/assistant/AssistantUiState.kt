package com.infinitezerone.minibgm.feature.assistant

import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.PendingAction
import com.infinitezerone.minibgm.core.model.PlayableEpisodeList

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
    /** 智能体转交的找源结果：非空时渲染为可播放清单卡片 */
    val playableSources: PlayableEpisodeList? = null,
    val isError: Boolean = false,
)

/** WebView 深度解析入口状态：subjectId 来自找源工具的"无结果"回复；null 表示入口不可用 */
data class DeepResolveState(
    val subjectId: Long,
    val isRunning: Boolean = false,
)

data class AssistantUiState(
    val messages: List<AssistantMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val aiConfig: AiConfig = AiConfig(),
    val showConfigDialog: Boolean = false,
    /** 播放失败归因（key = 播放地址）：找源卡片据此把打不开的条目标出来 */
    val failedSources: Map<String, String> = emptyMap(),
    /** 非 null 时界面展示「WebView 深度解析」入口 */
    val deepResolve: DeepResolveState? = null,
)

sealed interface AssistantUiEvent {
    data class ShowSnackbar(
        val message: String,
    ) : AssistantUiEvent

    data class NavigateToSubject(
        val subjectId: Long,
    ) : AssistantUiEvent
}
