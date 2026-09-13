package com.infinitezerone.minibgm.feature.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.PersistenceUtils
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.data.repository.AgentConfig
import com.infinitezerone.minibgm.core.data.repository.AgentConfigRepository
import com.miniagent.harness.EncryptedCheckpointStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class AgentBubbleRole {
    USER,
    AGENT,
    SYSTEM,
}

data class AgentChatBubble(
    /** LazyColumn key：必须唯一且稳定，重复内容的气泡各自持有不同 id */
    val id: Long,
    val role: AgentBubbleRole,
    val text: String,
)

data class AgentChatUiState(
    val bubbles: List<AgentChatBubble> = emptyList(),
    val isThinking: Boolean = false,
    val input: String = "",
    /** 模型配置：加密持久化在 AgentConfigRepository（独立 DataStore，排除云备份），修改即落盘 */
    val baseUrl: String = "https://api.deepseek.com/v1",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && model.isNotBlank() && baseUrl.isNotBlank()
}

/** 模型执行器工厂接缝：生产注入 Koog OpenAI 兼容客户端（可配置 baseUrl），测试注入 mock 执行器 */
fun interface AgentExecutorFactory {
    fun create(config: AgentConfig): ai.koog.prompt.executor.model.PromptExecutor
}

/**
 * Agent 聊天 ViewModel：引擎为 Koog [AIAgent]（官方 single-run 工具循环策略），
 * 工具集 = [MiniBgmAgentTools] 经 [asKoogTools] 适配；提供 [checkpointStorage] 时
 * 会话 checkpoint 加密落盘——进程被杀后新实例自动从断点恢复，聊天历史亦随之还原。
 *
 * 错误契约：除协程取消外的任何模型侧故障（连接失败、HTTP 错误、解析失败）
 * 都必须转成错误气泡，绝不向上抛出——v0.2.9/0.2.10 的两次崩溃皆源于此。
 */
class AgentChatViewModel(
    private val toolsFactory: MiniBgmAgentTools,
    private val configRepository: AgentConfigRepository,
    /** 加密 checkpoint 存储；null = 关闭持久化（单元测试用） */
    private val checkpointStorage: EncryptedCheckpointStorage? = null,
    private val executorFactory: AgentExecutorFactory =
        AgentExecutorFactory { config ->
            MultiLLMPromptExecutor(
                LLMProvider.OpenAI to
                    OpenAILLMClient(
                        apiKey = config.apiKey,
                        settings = OpenAIClientSettings(baseUrl = config.baseUrl),
                    ),
            )
        },
) : ViewModel() {
    private val _uiState = MutableStateFlow(AgentChatUiState())
    val uiState: StateFlow<AgentChatUiState> = _uiState.asStateFlow()

    init {
        // 进入屏幕时恢复上次配置；读取失败按未配置处理（存储层已兜底为 null）
        viewModelScope.launch {
            configRepository.current()?.let { cfg ->
                _uiState.update {
                    it.copy(baseUrl = cfg.baseUrl.ifBlank { it.baseUrl }, apiKey = cfg.apiKey, model = cfg.model.ifBlank { it.model })
                }
            }
            restoreChatHistory()
        }
    }

    private var runJob: Job? = null
    private var nextBubbleId: Long = 1L

    /** 气泡 id 生成器：单调递增，重复内容也不会碰撞 */
    private fun newBubbleId(): Long = nextBubbleId++

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
                bubbles = it.bubbles + AgentChatBubble(newBubbleId(), AgentBubbleRole.USER, trimmed),
                input = "",
                isThinking = true,
            )
        }

        val config = AgentConfig(baseUrl = state.baseUrl, apiKey = state.apiKey, model = state.model)
        runJob?.cancel()
        runJob =
            viewModelScope.launch {
                try {
                    val agent = buildAgent(config)
                    val result = agent.run(trimmed, sessionId = AGENT_SESSION_ID)
                    if (result.isNotBlank()) {
                        appendBubble(AgentBubbleRole.AGENT, result)
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    showErrorBubble("出错了：${e.message ?: e::class.simpleName}")
                } finally {
                    _uiState.update { it.copy(isThinking = false) }
                }
            }
    }

    private fun buildAgent(config: AgentConfig): AIAgent<String, String> =
        AIAgent(
            promptExecutor = executorFactory.create(config),
            strategy = singleRunStrategy(),
            agentConfig =
                AIAgentConfig(
                    prompt =
                        prompt("minibgm-agent") {
                            system(SYSTEM_PROMPT)
                        },
                    model =
                        LLModel(
                            provider = LLMProvider.OpenAI,
                            id = config.model,
                            capabilities = listOf(LLMCapability.Tools, LLMCapability.Temperature),
                            contextLength = 128_000L,
                            maxOutputTokens = 8_192L,
                        ),
                    maxAgentIterations = 8,
                ),
            toolRegistry = ToolRegistry { toolsFactory.create().asKoogTools().forEach { tool(it) } },
        ) {
            checkpointStorage?.let { storage ->
                install(Persistence) {
                    this.storage = storage
                    enableAutomaticPersistence = true
                }
            }
            install(EventHandler) {
                onToolCallStarting { ctx ->
                    appendBubble(AgentBubbleRole.SYSTEM, "🔧 ${ctx.toolName}")
                }
                onToolCallCompleted { ctx ->
                    val resultText =
                        (ctx.toolResult as? JsonPrimitive)?.contentOrNull
                            ?: ctx.toolResult.toString()
                    appendBubble(AgentBubbleRole.SYSTEM, "↳ $resultText")
                }
            }
        }

    /** 从最近的非墓碑 checkpoint 还原聊天历史（进程死亡后重进可见此前对话） */
    private suspend fun restoreChatHistory() {
        val storage = checkpointStorage ?: return
        val latest =
            storage
                .getCheckpoints(AGENT_SESSION_ID)
                .filterNot { it.checkpointId.startsWith(PersistenceUtils.TOMBSTONE_CHECKPOINT_NAME) }
                .maxByOrNull { it.createdAt }
                ?: return
        latest.messageHistory.forEach { message ->
            when (message) {
                is Message.User ->
                    message.textContent().takeIf { it.isNotBlank() }?.let { appendBubble(AgentBubbleRole.USER, it) }

                is Message.Assistant ->
                    message.textContent().takeIf { it.isNotBlank() }?.let { appendBubble(AgentBubbleRole.AGENT, it) }

                else -> Unit
            }
        }
    }

    private fun appendBubble(
        role: AgentBubbleRole,
        text: String,
    ) {
        _uiState.update {
            it.copy(bubbles = it.bubbles + AgentChatBubble(newBubbleId(), role, text))
        }
    }

    private fun showErrorBubble(text: String) {
        appendBubble(AgentBubbleRole.AGENT, text)
    }

    fun updateConfig(
        baseUrl: String,
        apiKey: String,
        model: String,
    ) {
        _uiState.update { it.copy(baseUrl = baseUrl, apiKey = apiKey, model = model) }
        // 编辑即持久化：加密落盘，下次进入无需重填
        viewModelScope.launch {
            configRepository.save(AgentConfig(baseUrl = baseUrl.trim(), apiKey = apiKey.trim(), model = model.trim()))
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    fun clearSession() {
        runJob?.cancel()
        _uiState.update { it.copy(bubbles = emptyList(), isThinking = false) }
        // 清空持久化会话，避免下次进入还原出已删除的对话
        checkpointStorage?.let { storage ->
            viewModelScope.launch { storage.clear(AGENT_SESSION_ID) }
        }
    }

    private companion object {
        const val AGENT_SESSION_ID = "minibgm-agent-chat"
        const val SYSTEM_PROMPT =
            "你是 MiniBgm 的内置助手，可以查询放送时刻表、搜索条目与即将开播。" +
                "回答用简体中文，风格简洁。需要数据时优先调用工具，不要编造条目或时间。"
    }
}
