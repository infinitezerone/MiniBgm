package com.infinitezerone.minibgm.feature.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miniagent.agentloop.AgentEvent
import com.miniagent.agentloop.AgentLoop
import com.miniagent.provider.cloud.CloudModelConfig
import com.miniagent.provider.cloud.OpenAiCompatibleProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AgentBubbleRole {
    USER,
    AGENT,
    SYSTEM,
}

data class AgentChatBubble(
    val role: AgentBubbleRole,
    val text: String,
)

data class AgentChatUiState(
    val bubbles: List<AgentChatBubble> = emptyList(),
    val isThinking: Boolean = false,
    val input: String = "",
    /** 模型配置：仅驻留内存，不持久化（凭据卫生：API key 不写入任何存储） */
    val baseUrl: String = "https://api.deepseek.com/v1",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && model.isNotBlank() && baseUrl.isNotBlank()
}

/**
 * Agent 聊天 ViewModel：把用户消息送入 [AgentLoop]（工具集 = [MiniBgmAgentTools]），
 * 循环事件映射为聊天气泡。模型配置（baseUrl/key/model）仅驻留内存。
 */
class AgentChatViewModel(
    private val toolsFactory: MiniBgmAgentTools,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AgentChatUiState())
    val uiState: StateFlow<AgentChatUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null

    fun send(message: String) {
        val trimmed = message.trim()
        val state = _uiState.value
        if (trimmed.isEmpty() || state.isThinking) return
        if (!state.isConfigured) {
            _uiState.update { it.copy(input = trimmed) }
            return
        }

        _uiState.update {
            it.copy(
                bubbles = it.bubbles + AgentChatBubble(AgentBubbleRole.USER, trimmed),
                input = "",
                isThinking = true,
            )
        }

        val provider = OpenAiCompatibleProvider(CloudModelConfig(state.baseUrl, state.apiKey, state.model))
        val loop = AgentLoop(provider = provider, tools = toolsFactory.create())
        runJob?.cancel()
        runJob =
            viewModelScope.launch {
                try {
                    val result =
                        loop.run(
                            systemPrompt = SYSTEM_PROMPT,
                            userMessage = trimmed,
                            onEvent = { event ->
                                when (event) {
                                    is AgentEvent.ToolCalled ->
                                        _uiState.update {
                                            it.copy(bubbles = it.bubbles + AgentChatBubble(AgentBubbleRole.SYSTEM, "🔧 ${event.call.name}"))
                                        }

                                    is AgentEvent.ToolFinished ->
                                        _uiState.update {
                                            it.copy(
                                                bubbles =
                                                    it.bubbles + AgentChatBubble(AgentBubbleRole.SYSTEM, "↳ ${event.result.content}"),
                                            )
                                        }

                                    is AgentEvent.FinalAnswer ->
                                        _uiState.update {
                                            it.copy(bubbles = it.bubbles + AgentChatBubble(AgentBubbleRole.AGENT, event.content))
                                        }

                                    is AgentEvent.StepStarted -> Unit
                                }
                            },
                        )
                    if (result.stopReason == com.miniagent.agentloop.AgentStopReason.MAX_STEPS) {
                        _uiState.update { it.copy(isThinking = false) }
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (e: com.miniagent.provider.cloud.CloudProviderException) {
                    _uiState.update {
                        it.copy(
                            bubbles = it.bubbles + AgentChatBubble(AgentBubbleRole.AGENT, "调用模型失败：${e.message}"),
                            isThinking = false,
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            bubbles = it.bubbles + AgentChatBubble(AgentBubbleRole.AGENT, "出错了：${e.message ?: e::class.simpleName}"),
                            isThinking = false,
                        )
                    }
                } finally {
                    _uiState.update { it.copy(isThinking = false) }
                }
            }
    }

    fun updateConfig(
        baseUrl: String,
        apiKey: String,
        model: String,
    ) {
        _uiState.update { it.copy(baseUrl = baseUrl, apiKey = apiKey, model = model) }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    fun clearSession() {
        runJob?.cancel()
        _uiState.update { it.copy(bubbles = emptyList(), isThinking = false) }
    }

    private companion object {
        const val SYSTEM_PROMPT =
            "你是 MiniBgm 的内置助手，可以查询放送时刻表、搜索条目与即将开播。" +
                "回答用简体中文，风格简洁。需要数据时优先调用工具，不要编造条目或时间。"
    }
}
