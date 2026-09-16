package com.infinitezerone.minibgm.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.ai.BgmAiAgentService
import com.infinitezerone.minibgm.core.ai.PendingActionParser
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.model.AiConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class AssistantViewModel(
    private val agentService: BgmAiAgentService,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val _events = Channel<AssistantUiEvent>(Channel.BUFFERED)
    val events: Flow<AssistantUiEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            settingsRepository.aiConfig.collect { config ->
                _uiState.update { it.copy(aiConfig = config) }
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage(prompt: String = _uiState.value.inputText) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank() || _uiState.value.isLoading) return

        val userMessage =
            AssistantMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.USER,
                content = trimmed,
            )

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                inputText = "",
                isLoading = true,
            )
        }

        viewModelScope.launch {
            when (val result = agentService.execute(trimmed)) {
                is AppResult.Success -> {
                    val rawContent = result.data
                    val storeActions = agentService.pendingActionStore?.popAll().orEmpty()
                    val parsedActions = PendingActionParser.extractPendingActions(rawContent)
                    val combinedActions = (storeActions + parsedActions).distinctBy { it.actionId }

                    val actionCardStates =
                        combinedActions.map {
                            PendingActionCardState(action = it, status = ActionStatus.PENDING)
                        }

                    val displayContent =
                        if (rawContent.trim().startsWith("{") && combinedActions.isNotEmpty()) {
                            "已为您生成待确认操作提案，请确认是否提交同步至 Bangumi："
                        } else {
                            rawContent
                        }

                    val assistantMessage =
                        AssistantMessage(
                            id = UUID.randomUUID().toString(),
                            role = MessageRole.ASSISTANT,
                            content = displayContent,
                            pendingActions = actionCardStates,
                        )

                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages + assistantMessage,
                            isLoading = false,
                        )
                    }
                }
                is AppResult.Error -> {
                    val errorMsg = result.throwable.message ?: "智能体执行失败"
                    val assistantMessage =
                        AssistantMessage(
                            id = UUID.randomUUID().toString(),
                            role = MessageRole.ASSISTANT,
                            content = "❌ 执行出错：$errorMsg",
                            isError = true,
                        )
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages + assistantMessage,
                            isLoading = false,
                        )
                    }
                    _events.send(AssistantUiEvent.ShowSnackbar(errorMsg))
                }
                is AppResult.Loading -> Unit
            }
        }
    }

    fun approveAction(actionId: String) {
        val currentCard =
            _uiState.value.messages
                .flatMap { it.pendingActions }
                .firstOrNull { it.action.actionId == actionId }
                ?: return

        // 仅允许对等待确认 (PENDING) 或执行失败 (FAILED) 状态的卡片执行提交/重试
        if (currentCard.status != ActionStatus.PENDING && currentCard.status != ActionStatus.FAILED) {
            return
        }

        val executor = agentService.pendingActionExecutor
        if (executor == null) {
            viewModelScope.launch {
                _events.send(AssistantUiEvent.ShowSnackbar("执行器未配置"))
            }
            return
        }

        val action = currentCard.action

        _uiState.update { state ->
            val updatedMessages =
                state.messages.map { msg ->
                    val updatedCards =
                        msg.pendingActions.map { card ->
                            if (card.action.actionId == actionId) {
                                card.copy(status = ActionStatus.EXECUTING, errorMessage = null)
                            } else {
                                card
                            }
                        }
                    msg.copy(pendingActions = updatedCards)
                }
            state.copy(messages = updatedMessages)
        }

        viewModelScope.launch {
            when (val result = executor.execute(action)) {
                is AppResult.Success -> {
                    _uiState.update { state ->
                        val updatedMessages =
                            state.messages.map { msg ->
                                val updatedCards =
                                    msg.pendingActions.map { card ->
                                        if (card.action.actionId == actionId) {
                                            card.copy(status = ActionStatus.SUCCESS)
                                        } else {
                                            card
                                        }
                                    }
                                msg.copy(pendingActions = updatedCards)
                            }
                        state.copy(messages = updatedMessages)
                    }
                    agentService.pendingActionStore?.remove(actionId)
                    _events.send(AssistantUiEvent.ShowSnackbar("操作已成功执行并同步至 Bangumi！"))
                }
                is AppResult.Error -> {
                    val err = result.throwable.message ?: "执行操作失败"
                    _uiState.update { state ->
                        val updatedMessages =
                            state.messages.map { msg ->
                                val updatedCards =
                                    msg.pendingActions.map { card ->
                                        if (card.action.actionId == actionId) {
                                            card.copy(status = ActionStatus.FAILED, errorMessage = err)
                                        } else {
                                            card
                                        }
                                    }
                                msg.copy(pendingActions = updatedCards)
                            }
                        state.copy(messages = updatedMessages)
                    }
                    _events.send(AssistantUiEvent.ShowSnackbar("执行失败: $err"))
                }
                is AppResult.Loading -> Unit
            }
        }
    }

    fun rejectAction(actionId: String) {
        val currentCard =
            _uiState.value.messages
                .flatMap { it.pendingActions }
                .firstOrNull { it.action.actionId == actionId }
                ?: return

        // 仅允许取消等待确认或失败重试状态的提案，禁止反转已执行成功的操作
        if (currentCard.status != ActionStatus.PENDING && currentCard.status != ActionStatus.FAILED) {
            return
        }

        _uiState.update { state ->
            val updatedMessages =
                state.messages.map { msg ->
                    val updatedCards =
                        msg.pendingActions.map { card ->
                            if (card.action.actionId == actionId) {
                                card.copy(status = ActionStatus.REJECTED)
                            } else {
                                card
                            }
                        }
                    msg.copy(pendingActions = updatedCards)
                }
            state.copy(messages = updatedMessages)
        }
        agentService.pendingActionStore?.remove(actionId)
        viewModelScope.launch {
            _events.send(AssistantUiEvent.ShowSnackbar("已取消操作提案"))
        }
    }

    fun clearConversation() {
        _uiState.update { it.copy(messages = emptyList()) }
        agentService.pendingActionStore?.clear()
    }

    fun toggleConfigDialog(show: Boolean) {
        _uiState.update { it.copy(showConfigDialog = show) }
    }

    fun saveAiConfig(config: AiConfig) {
        viewModelScope.launch {
            settingsRepository.setAiConfig(config)
            _uiState.update { it.copy(showConfigDialog = false) }
            _events.send(AssistantUiEvent.ShowSnackbar("AI 配置已更新"))
        }
    }
}
