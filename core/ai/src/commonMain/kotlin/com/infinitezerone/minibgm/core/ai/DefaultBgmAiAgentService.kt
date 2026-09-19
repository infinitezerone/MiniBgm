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
import com.infinitezerone.minibgm.core.ai.tools.PlayableSourceTools
import com.infinitezerone.minibgm.core.ai.tools.ScheduleTools
import com.infinitezerone.minibgm.core.ai.tools.SubjectTools
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.model.AiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 单次 AI 执行的硬超时：推理模型多轮工具调用实测 1~3 分钟，上限给足余量 */
const val AI_RUN_TIMEOUT_MS = 180_000L

/**
 * 默认智能体执行服务，利用 JetBrains Koog 框架与 SettingsRepository 提供的 AI 配置进行交互。
 * 整合放送时刻表、条目检索、收藏进度与找源等 Koog 工具集，并在写操作执行中引入 HITL 安全保护机制。
 */
class DefaultBgmAiAgentService(
    private val settingsRepository: SettingsRepository,
    val scheduleTools: ScheduleTools? = null,
    val subjectTools: SubjectTools? = null,
    val collectionTools: CollectionTools? = null,
    val playableSourceTools: PlayableSourceTools? = null,
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
                systemPrompt = BGM_AGENT_SYSTEM_PROMPT,
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
        playableSourceTools = null,
        pendingActionExecutor = null,
        pendingActionStore = null,
        agentRunner = { config, prompt, _ -> agentRunner(config, prompt) },
    )

    val toolRegistry: ToolRegistry =
        ToolRegistry {
            scheduleTools?.let { tools(it) }
            subjectTools?.let { tools(it) }
            collectionTools?.let { tools(it) }
            playableSourceTools?.let { tools(it) }
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
        AiToolActivity.clear()
        return try {
            // 推理模型多轮往返较慢（实测 1~3 分钟），但必须有硬上限防挂死
            val response =
                withTimeout(AI_RUN_TIMEOUT_MS) {
                    agentRunner(config, prompt, toolRegistry)
                }
            AppResult.Success(response)
        } catch (e: TimeoutCancellationException) {
            AiToolActivity.clear()
            AppResult.Error(e, "AI 响应超时（${AI_RUN_TIMEOUT_MS / 1000} 秒）：请重试，或更换更快的模型/端点。")
        } catch (e: Exception) {
            AiToolActivity.clear()
            AppResult.Error(e, friendlyAiError(config, e))
        }
    }

    private val catalogClient: HttpClient by lazy {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
            }
        }
    }

    /** 拉取端点可用模型列表：优先 OpenAI 兼容 /models，兼容 Ollama /api/tags 的 models[].name */
    override suspend fun fetchAvailableModels(): AppResult<List<String>> {
        val config = settingsRepository.aiConfig.first()
        val modelsUrl = buildModelsUrl(config.endpoint, config.provider)
        return try {
            val response: HttpResponse =
                catalogClient.get(modelsUrl) {
                    if (config.apiKey.isNotBlank()) {
                        header(HttpHeaders.Authorization, "Bearer " + config.apiKey)
                    }
                }
            if (!response.status.isSuccess()) {
                return AppResult.Error(
                    IllegalStateException("HTTP ${response.status.value}"),
                    "拉取模型列表失败（HTTP ${response.status.value}），请检查端点与密钥",
                )
            }
            parseModelsBody(response.body<String>())?.let { AppResult.Success(it) }
                ?: AppResult.Error(IllegalStateException("empty model list"), "端点未返回任何模型")
        } catch (e: Exception) {
            AppResult.Error(e, friendlyAiError(config, e).ifBlank { "拉取模型列表失败：${e.message}" })
        }
    }

    private companion object {
        /** 把常见 LLM 端点异常翻译成用户可直接行动的提示 */
        fun friendlyAiError(
            config: AiConfig,
            e: Exception,
        ): String {
            val raw = (e.message ?: "").let { m -> m + (e.cause?.message?.let { " $it" } ?: "") }
            val lowered = raw.lowercase()
            return when {
                "model" in lowered && ("not available" in lowered || "not found" in lowered || "does not exist" in lowered) ->
                    "模型「${config.model.ifBlank { "（未填写）" }}」在端点上不可用（可能已下线或改名）。请到 AI 设置更换模型——可先点「获取模型列表」查看端点上实际可用的模型。"
                "404" in lowered && "model" in lowered -> "模型「${config.model}」在端点上不可用（HTTP 404）。请到 AI 设置更换模型。"
                "401" in lowered || "unauthorized" in lowered || "invalid api key" in lowered || "invalid_api_key" in lowered ->
                    "鉴权失败：API 密钥无效或已过期，请到 AI 设置更新密钥。"
                "403" in lowered || "forbidden" in lowered -> "端点拒绝了访问（HTTP 403）：请确认密钥对该模型有权限。"
                "429" in lowered || "rate limit" in lowered || "quota" in lowered -> "请求过于频繁或额度不足（HTTP 429），请稍后重试。"
                "timeout" in lowered || "timed out" in lowered -> "AI 端点请求超时：请检查网络，或确认端点地址可达。"
                "connection" in lowered || "unresolved" in lowered || "refused" in lowered -> "无法连接到 AI 端点：请检查网络与 Base URL 是否可达。"
                else -> raw.ifBlank { e.toString() }
            }
        }
    }
}

/**
 * 模型列表端点：OpenAI 兼容形态保留 /v1 前缀（…/v1/models）；
 * Ollama 用原生 /api/tags。端点若以 /chat/completions 结尾则先剥掉。
 */
internal fun buildModelsUrl(
    rawEndpoint: String,
    provider: String,
): String {
    val base =
        rawEndpoint
            .ifBlank {
                when (provider) {
                    AiConfig.PROVIDER_OLLAMA -> "http://localhost:11434"
                    AiConfig.PROVIDER_GEMINI -> "https://generativelanguage.googleapis.com/v1beta/openai"
                    else -> "https://api.openai.com/v1"
                }
            }.removeSuffix("/chat/completions")
            .removeSuffix("/chat/completions/")
            .trimEnd('/')
    val path = if (provider.equals(AiConfig.PROVIDER_OLLAMA, ignoreCase = true)) "/api/tags" else "/models"
    return base + path
}

@Serializable
private data class ModelsResponse(
    val data: List<ModelIdEntry>? = null,
    val models: List<ModelNameEntry>? = null,
)

@Serializable
private data class ModelIdEntry(
    val id: String = "",
)

@Serializable
private data class ModelNameEntry(
    val name: String = "",
)

private val catalogJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

/** 解析 OpenAI 兼容（data[].id）与 Ollama（models[].name）两种形态；解析失败返回 null */
internal fun parseModelsBody(body: String): List<String>? =
    runCatching {
        val parsed = catalogJson.decodeFromString<ModelsResponse>(body)
        buildList {
            parsed.data?.forEach { if (it.id.isNotBlank()) add(it.id) }
            parsed.models?.forEach { if (it.name.isNotBlank()) add(it.name) }
        }.distinct()
    }.getOrNull()
