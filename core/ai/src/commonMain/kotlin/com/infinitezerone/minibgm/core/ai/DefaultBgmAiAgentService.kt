package com.infinitezerone.minibgm.core.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.infinitezerone.minibgm.core.ai.tools.CollectionTools
import com.infinitezerone.minibgm.core.ai.tools.ScheduleTools
import com.infinitezerone.minibgm.core.ai.tools.SubjectTools
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.model.AiConfig
import kotlinx.coroutines.flow.first

/**
 * 默认智能体执行服务，利用 JetBrains Koog 框架与 SettingsRepository 提供的 AI 配置进行交互。
 * 整合放送时刻表、条目检索与收藏进度等 Koog 工具集，并在写操作执行中引入 HITL 安全保护机制。
 */
class DefaultBgmAiAgentService(
    private val settingsRepository: SettingsRepository,
    val scheduleTools: ScheduleTools? = null,
    val subjectTools: SubjectTools? = null,
    val collectionTools: CollectionTools? = null,
    override val pendingActionExecutor: PendingActionExecutor? = null,
    override val pendingActionStore: PendingActionStore? = null,
    private val agentRunner: suspend (config: AiConfig, prompt: String, tools: ToolRegistry) -> String = { config, prompt, tools ->
        val client: LLMClient =
            when {
                config.provider.equals(AiConfig.PROVIDER_OLLAMA, ignoreCase = true) -> {
                    OllamaClient(
                        httpClientFactory = KtorKoogHttpClient.Factory(),
                        baseUrl = config.endpoint.ifBlank { OllamaClient.DEFAULT_BASE_URL },
                    )
                }
                config.provider.equals(AiConfig.PROVIDER_GEMINI, ignoreCase = true) -> {
                    val rawEndpoint =
                        config.endpoint
                            .ifBlank { "https://generativelanguage.googleapis.com/v1beta/openai" }
                            .removeSuffix("/chat/completions")
                            .removeSuffix("/chat/completions/")
                            .trimEnd('/')
                    OpenAILLMClient(
                        apiKey = config.apiKey,
                        settings =
                            OpenAIClientSettings(
                                baseUrl = rawEndpoint,
                                chatCompletionsPath = "chat/completions",
                            ),
                        httpClientFactory = KtorKoogHttpClient.Factory(),
                    )
                }
                else -> {
                    val rawEndpoint =
                        config.endpoint
                            .ifBlank { "https://api.openai.com/v1" }
                            .removeSuffix("/chat/completions")
                            .removeSuffix("/chat/completions/")
                            .trimEnd('/')
                    val (baseUrl, chatCompletionsPath) =
                        if (rawEndpoint.endsWith("/v1")) {
                            rawEndpoint to "chat/completions"
                        } else {
                            rawEndpoint to "v1/chat/completions"
                        }
                    OpenAILLMClient(
                        apiKey = config.apiKey,
                        settings =
                            OpenAIClientSettings(
                                baseUrl = baseUrl,
                                chatCompletionsPath = chatCompletionsPath,
                            ),
                        httpClientFactory = KtorKoogHttpClient.Factory(),
                    )
                }
            }

        val defaultModel =
            when {
                config.provider.equals(AiConfig.PROVIDER_OLLAMA, ignoreCase = true) -> "qwen2.5:7b"
                config.provider.equals(AiConfig.PROVIDER_GEMINI, ignoreCase = true) -> "gemini-2.5-flash"
                else -> "gpt-4o-mini"
            }

        val capabilities =
            if (config.provider.equals(AiConfig.PROVIDER_OLLAMA, ignoreCase = true)) {
                listOf(
                    LLMCapability.Tools,
                    LLMCapability.Completion,
                    LLMCapability.Temperature,
                )
            } else {
                listOf(
                    LLMCapability.OpenAIEndpoint.Completions,
                    LLMCapability.Tools,
                    LLMCapability.Completion,
                    LLMCapability.Temperature,
                )
            }

        val llmModel =
            LLModel(
                provider =
                    if (config.provider.equals(AiConfig.PROVIDER_OLLAMA, ignoreCase = true)) {
                        LLMProvider.Ollama
                    } else {
                        LLMProvider.OpenAI
                    },
                id = config.model.ifBlank { defaultModel },
                capabilities = capabilities,
            )
        val executor = MultiLLMPromptExecutor(client)
        val agent =
            AIAgent(
                promptExecutor = executor,
                llmModel = llmModel,
                toolRegistry = tools,
            )
        agent.run(prompt)
    },
) : BgmAiAgentService {
    constructor(
        settingsRepository: SettingsRepository,
        agentRunner: suspend (config: AiConfig, prompt: String) -> String,
    ) : this(
        settingsRepository = settingsRepository,
        scheduleTools = null,
        subjectTools = null,
        collectionTools = null,
        pendingActionExecutor = null,
        pendingActionStore = null,
        agentRunner = { config, prompt, _ -> agentRunner(config, prompt) },
    )

    val toolRegistry: ToolRegistry =
        ToolRegistry {
            scheduleTools?.let { tools(it) }
            subjectTools?.let { tools(it) }
            collectionTools?.let { tools(it) }
        }

    override suspend fun execute(prompt: String): AppResult<String> {
        if (prompt.isBlank()) {
            return AppResult.Error(IllegalArgumentException("Prompt must not be blank."))
        }
        val config = settingsRepository.aiConfig.first()
        if (config.provider.equals(AiConfig.PROVIDER_OLLAMA, ignoreCase = true)) {
            // Ollama can run with local defaults and doesn't require an API key
        } else {
            if (config.apiKey.isBlank()) {
                return AppResult.Error(
                    IllegalStateException("API key is missing for provider '${config.provider}'. Please configure it in AI settings."),
                )
            }
        }
        return try {
            val response = agentRunner(config, prompt, toolRegistry)
            AppResult.Success(response)
        } catch (e: Exception) {
            AppResult.Error(e)
        }
    }
}
