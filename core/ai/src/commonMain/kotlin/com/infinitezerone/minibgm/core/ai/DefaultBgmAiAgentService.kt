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
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    val communityTools: com.infinitezerone.minibgm.core.ai.tools.CommunityTools? = null,
    val playbackRuleDiagnosticsTools: com.infinitezerone.minibgm.core.ai.tools.PlaybackRuleDiagnosticsTools? = null,
    override val pendingActionExecutor: PendingActionExecutor? = null,
    override val pendingActionStore: PendingActionStore? = null,
    httpClient: HttpClient? = null,
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
            communityTools?.let { tools(it) }
            playbackRuleDiagnosticsTools?.let { tools(it) }
        }

    override suspend fun execute(
        prompt: String,
        history: List<Pair<String, String>>,
    ): AppResult<String> {
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

        val finalPrompt =
            if (history.isEmpty()) {
                prompt
            } else {
                buildString {
                    appendLine("以下是先前的会话历史记录（供参考上下文）：")
                    history.forEach { (role, content) ->
                        val roleLabel = if (role.equals("user", ignoreCase = true)) "用户" else "助手"
                        appendLine("[$roleLabel] $content")
                    }
                    appendLine("---")
                    appendLine("用户当前最新输入：")
                    append(prompt)
                }
            }

        AiToolActivity.clear()
        AiToolActivity.reportStatus("AI 正在思考并检索...")
        return try {
            // 推理模型多轮往返较慢（实测 1~3 分钟），但必须有硬上限防挂死；支持 429 限流退避重试
            val response =
                withTimeout(AI_RUN_TIMEOUT_MS) {
                    var lastException: Exception? = null
                    var result: String? = null
                    val maxAttempts = 3
                    for (attempt in 1..maxAttempts) {
                        try {
                            result = agentRunner(config, finalPrompt, toolRegistry)
                            break
                        } catch (e: Exception) {
                            lastException = e
                            val raw = (e.message ?: "") + (e.cause?.message?.let { " $it" } ?: "")
                            val is429 = isRateLimitOrQuota(raw.lowercase())
                            if (is429 && attempt < maxAttempts) {
                                AiToolActivity.reportStatus("AI 请求较频繁（429 限流），等待刷新（${attempt * 2}秒）...")
                                kotlinx.coroutines.delay(attempt * 2000L)
                            } else {
                                throw e
                            }
                        }
                    }
                    result ?: throw (lastException ?: IllegalStateException("Agent execution failed"))
                }
            AiToolActivity.clear()
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
        httpClient ?: HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
            }
        }
    }

    /** 拉取端点可用模型列表：优先 OpenAI 兼容 /models，兼容 Ollama /api/tags 的 models[].name */
    override suspend fun fetchAvailableModels(
        endpoint: String?,
        apiKey: String?,
        provider: String?,
    ): AppResult<List<String>> {
        val config = settingsRepository.aiConfig.first()
        val target = resolveTargetConfig(config, endpoint, apiKey, provider)
        val modelsUrl = buildModelsUrl(target.endpoint, target.provider)

        return try {
            val response: HttpResponse =
                catalogClient.get(modelsUrl) {
                    header(HttpHeaders.UserAgent, "MiniBgm/1.0 (Android)")
                    if (target.apiKey.isNotBlank()) {
                        header(HttpHeaders.Authorization, "Bearer ${target.apiKey}")
                    }
                    if (target.provider.equals(AiConfig.PROVIDER_GEMINI, ignoreCase = true) && target.apiKey.isNotBlank()) {
                        parameter("key", target.apiKey)
                    }
                }
            if (!response.status.isSuccess()) {
                val errorMsg = catalogHttpErrorMessage(response.status.value)
                return AppResult.Error(IllegalStateException("HTTP ${response.status.value}"), errorMsg)
            }
            val models = parseModelsBody(response.body<String>())
            if (models != null) {
                AppResult.Success(models)
            } else {
                AppResult.Error(IllegalStateException("empty model list"), "端点未返回任何可用模型，请确认服务已正常运行")
            }
        } catch (e: Exception) {
            val friendly = friendlyAiError(config.copy(endpoint = target.endpoint, provider = target.provider), e)
            AppResult.Error(e, friendly.ifBlank { "拉取模型列表失败：${e.message}" })
        }
    }

    companion object {
        // Internal for unit testing
    }
}

internal data class TargetModelConfig(
    val endpoint: String,
    val apiKey: String,
    val provider: String,
)

internal fun resolveTargetConfig(
    base: AiConfig,
    endpoint: String?,
    apiKey: String?,
    provider: String?,
): TargetModelConfig {
    val ep = endpoint?.takeIf { it.isNotBlank() } ?: base.endpoint
    val key = apiKey ?: base.apiKey
    val prov = provider?.takeIf { it.isNotBlank() } ?: base.provider
    return TargetModelConfig(ep, key, prov)
}

internal fun catalogHttpErrorMessage(status: Int): String =
    when (status) {
        401 -> "鉴权失败（HTTP 401）：API 密钥无效或已过期"
        403 -> "端点拒绝访问（HTTP 403）：请确认密钥权限"
        404 -> "端点未找到（HTTP 404）：请检查 Base URL 是否正确"
        else -> "拉取模型列表失败（HTTP $status）"
    }

/** 把常见 LLM 端点异常翻译成用户可直接行动的提示，提取真实底层 JSON 错误说明 */
internal fun friendlyAiError(
    config: AiConfig,
    e: Exception,
): String {
    val raw = (e.message ?: "").let { m -> m + (e.cause?.message?.let { " $it" } ?: "") }
    val lowered = raw.lowercase()
    return when {
        isModelRouteNotFound(lowered) ->
            "模型「${config.model.ifBlank {
                "（未填写）"
            }}」不可用（服务商提示 model route not found / HTTP 404，该模型可能已下线或不支持对话路由）。请在 AI 设置中点击「获取可用模型」并选择最新的可用模型。"
        isModelUnavailable(lowered) ->
            "模型「${config.model.ifBlank { "（未填写）" }}」在端点上不可用（可能已下线或改名）。请到 AI 设置更换模型——可先点「获取可用模型」查看端点上实际可用的模型。"
        isRateLimitOrQuota(lowered) ->
            "请求已被服务商限制（HTTP 429）：服务商提示配额不足或超过并发频率限制（TPM/RPM Limit），请稍后重试或更换模型/端点。"
        isAuthFailure(lowered) ->
            "鉴权失败（HTTP 401）：API 密钥无效或已过期，请到 AI 设置更新密钥。"
        isForbidden(lowered) ->
            "端点拒绝了访问（HTTP 403）：请确认密钥对该模型拥有调用权限。"
        isNetworkOrTimeout(lowered) ->
            "无法连接到 AI 端点或请求超时：请检查网络与 Base URL 是否可达。"
        else -> {
            extractJsonErrorMessage(raw)?.let { innerMsg ->
                "AI 服务商返回错误：$innerMsg"
            } ?: raw.ifBlank { e.toString() }
        }
    }
}

internal fun isModelRouteNotFound(lower: String): Boolean = "model route not found" in lower || ("404" in lower && "model" in lower)

internal fun isModelUnavailable(lower: String): Boolean =
    "model" in lower && ("not available" in lower || "not found" in lower || "does not exist" in lower)

internal fun isRateLimitOrQuota(lower: String): Boolean =
    "tpm/rpm limit" in lower || "insufficient_quota" in lower || "quota" in lower || "rate limit" in lower || "429" in lower

internal fun isAuthFailure(lower: String): Boolean =
    "401" in lower || "unauthorized" in lower || "invalid api key" in lower || "invalid_api_key" in lower

internal fun isForbidden(lower: String): Boolean = "403" in lower || "forbidden" in lower

internal fun isNetworkOrTimeout(lower: String): Boolean =
    "timeout" in lower || "timed out" in lower || "connection" in lower || "unresolved" in lower || "refused" in lower

/** 从原始异常文本中尝试提取 JSON 报文里的核心 error.message 避免冗长堆栈暴露给用户 */
internal fun extractJsonErrorMessage(raw: String): String? {
    if (!raw.contains("{") || !raw.contains("}")) return null
    return runCatching {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start in 0 until end) {
            val jsonStr = raw.substring(start, end + 1)
            val element = catalogJson.parseToJsonElement(jsonStr)
            if (element is JsonObject) {
                element["error"]?.let { err ->
                    if (err is JsonObject) {
                        err["message"]?.let { if (it is JsonPrimitive) return@runCatching it.contentOrNull }
                    } else if (err is JsonPrimitive) {
                        return@runCatching err.contentOrNull
                    }
                }
                element["message"]?.let {
                    if (it is JsonPrimitive) return@runCatching it.contentOrNull
                }
            }
        }
        null
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * 模型列表端点：OpenAI 兼容形态保留 /v1 前缀（…/v1/models）；
 * Ollama 用原生 /api/tags。端点若以 /chat/completions 结尾则先剥掉。
 */
internal fun buildModelsUrl(
    rawEndpoint: String,
    provider: String,
): String {
    val defaultBase =
        when (provider.lowercase()) {
            AiConfig.PROVIDER_OLLAMA -> "http://10.0.2.2:11434"
            AiConfig.PROVIDER_GEMINI -> "https://generativelanguage.googleapis.com/v1beta/openai"
            else -> "https://api.openai.com/v1"
        }
    var base = rawEndpoint.trim().ifBlank { defaultBase }
    base = base.trimEnd('/')
    base = base.removeSuffix("/chat/completions").trimEnd('/')

    val isOllama = provider.equals(AiConfig.PROVIDER_OLLAMA, ignoreCase = true)
    return if (isOllama) {
        base.removeSuffix("/api/tags").trimEnd('/') + "/api/tags"
    } else {
        base.removeSuffix("/models").trimEnd('/') + "/models"
    }
}

private val catalogJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

/**
 * 解析模型列表：
 * 兼容 OpenAI（data[].id）、Ollama（models[].name / models[].model）、
 * Gemini 原生（models[].name 去除 "models/" 前缀）、以及各类代理返回的字符串列表或根数组。
 *
 * 智能过滤：自动过滤纯生图（output_modalities 仅包含 image）、向量 Embedding、语音及 Moderation 等非对话模型。
 * 智能排序：优先将支持 Chat/Instruct/Flash/Pro 的最新主流对话模型排在前面。
 * 若无有效模型则返回 null。
 */
internal fun parseModelsBody(body: String): List<String>? =
    runCatching {
        val root = catalogJson.parseToJsonElement(body)
        val candidateModels = mutableListOf<String>()

        fun isNonChatModel(id: String): Boolean {
            val lower = id.lowercase()
            return lower.contains("embedding") ||
                lower.contains("embed") ||
                lower.startsWith("bge-") ||
                lower.contains("-rerank") ||
                lower.contains("rerank") ||
                lower.contains("whisper") ||
                lower.contains("tts") ||
                lower.contains("dall-e") ||
                lower.contains("flux") ||
                lower.contains("sdxl") ||
                lower.contains("stable-diffusion") ||
                lower.contains("moderation") ||
                lower.contains("image-generation") ||
                lower.contains("text-to-image")
        }

        fun extractFromObject(obj: JsonObject) {
            val id =
                obj["id"]?.let { if (it is JsonPrimitive) it.contentOrNull else null }
                    ?: obj["name"]?.let { if (it is JsonPrimitive) it.contentOrNull else null }
                    ?: obj["model"]?.let { if (it is JsonPrimitive) it.contentOrNull else null }
            if (id.isNullOrBlank()) return
            val trimmed = id.trim().removePrefix("models/")
            if (trimmed.isBlank() || isNonChatModel(trimmed)) return

            // 检查输出模态（例如纯生图模型 output_modalities: ["image"]）
            val outputModalities = obj["output_modalities"]
            if (outputModalities is JsonArray) {
                val hasText =
                    outputModalities.any {
                        it is JsonPrimitive && it.contentOrNull?.equals("text", ignoreCase = true) == true
                    }
                if (!hasText) return
            }

            candidateModels.add(trimmed)
        }

        fun addPrimitiveId(raw: String?) {
            if (raw == null) return
            val trimmed = raw.trim().removePrefix("models/")
            if (trimmed.isNotBlank() && !isNonChatModel(trimmed)) {
                candidateModels.add(trimmed)
            }
        }

        when (root) {
            is JsonObject -> {
                root["data"]?.let { dataElement ->
                    if (dataElement is JsonArray) {
                        dataElement.forEach { elem ->
                            when (elem) {
                                is JsonObject -> extractFromObject(elem)
                                is JsonPrimitive -> addPrimitiveId(elem.contentOrNull)
                                else -> Unit
                            }
                        }
                    }
                }
                root["models"]?.let { modelsElement ->
                    if (modelsElement is JsonArray) {
                        modelsElement.forEach { elem ->
                            when (elem) {
                                is JsonObject -> extractFromObject(elem)
                                is JsonPrimitive -> addPrimitiveId(elem.contentOrNull)
                                else -> Unit
                            }
                        }
                    }
                }
            }
            is JsonArray -> {
                root.forEach { elem ->
                    when (elem) {
                        is JsonObject -> extractFromObject(elem)
                        is JsonPrimitive -> addPrimitiveId(elem.contentOrNull)
                        else -> Unit
                    }
                }
            }
            else -> Unit
        }

        fun rankModel(id: String): Int {
            val lower = id.lowercase()
            var score = 0
            if (lower.contains("chat") || lower.contains("instruct")) score += 20
            if (lower.contains("flash") || lower.contains("turbo") || lower.contains("lite")) score += 15
            if (lower.contains("deepseek") || lower.contains("glm") || lower.contains("qwen") || lower.contains("kimi")) score += 10
            if (lower.contains("gpt-4") || lower.contains("claude-3") || lower.contains("gemini")) score += 10
            if (lower.contains("6.8") || lower.contains("2.5") || lower.contains("5.2") || lower.contains("v4")) score += 5
            return score
        }

        val distinct = candidateModels.distinct()
        distinct.sortedByDescending { rankModel(it) }.takeIf { it.isNotEmpty() }
    }.getOrNull()
