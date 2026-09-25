package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.ai.tool.BgmToolRegistry
import com.infinitezerone.minibgm.core.ai.tools.CollectionTools
import com.infinitezerone.minibgm.core.ai.tools.CommunityTools
import com.infinitezerone.minibgm.core.ai.tools.PlayableSourceTools
import com.infinitezerone.minibgm.core.ai.tools.PlaybackRuleDiagnosticsTools
import com.infinitezerone.minibgm.core.ai.tools.ScheduleTools
import com.infinitezerone.minibgm.core.ai.tools.SubjectTools
import com.infinitezerone.minibgm.core.ai.tools.WebSearchTools
import com.infinitezerone.minibgm.core.ai.wire.OpenAiWireClient
import com.infinitezerone.minibgm.core.ai.wire.WireChatMessage
import com.infinitezerone.minibgm.core.ai.wire.WireChatRequest
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.model.AiConfig
import io.ktor.client.HttpClient
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** 单次 AI 执行的硬超时：推理模型多轮工具调用实测 1~3 分钟，上限给足余量 */
const val AI_RUN_TIMEOUT_MS = 180_000L

/** 单轮模型请求的超时：60 秒，正常推理远用不满，异常端点快速失败 */
const val AI_REQUEST_TIMEOUT_MS = 60_000L

private val agentLogger = bgmLogger("Bgm/AiAgent")

/**
 * 默认智能体执行服务，基于原生 OpenAI Wire 协议与 Pi Agent 极简 ReAct 循环。
 * 零第三方 Agent 框架黑盒依赖，直接且精准地支持 tool_calls、推理模型 reasoning_content 与多轮往返。
 */
class DefaultBgmAiAgentService(
    private val settingsRepository: SettingsRepository,
    val scheduleTools: ScheduleTools? = null,
    val subjectTools: SubjectTools? = null,
    val collectionTools: CollectionTools? = null,
    val playableSourceTools: PlayableSourceTools? = null,
    val communityTools: CommunityTools? = null,
    val playbackRuleDiagnosticsTools: PlaybackRuleDiagnosticsTools? = null,
    val webSearchTools: WebSearchTools? = null,
    override val pendingActionExecutor: PendingActionExecutor? = null,
    override val pendingActionStore: PendingActionStore? = null,
    override val playableSourcesStore: PlayableSourcesStore? = null,
    httpClient: HttpClient? = null,
    wireClient: OpenAiWireClient? = null,
    private val coroutineDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
    agentRunner: (suspend (config: AiConfig, prompt: String, tools: BgmToolRegistry) -> String)? = null,
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
        playableSourcesStore = null,
        agentRunner = { config, prompt, _ -> agentRunner(config, prompt) },
    )

    private val catalogLogger = bgmLogger("Bgm/AiHttp")

    private val activeWireClient: OpenAiWireClient by lazy {
        wireClient ?: httpClient?.let { OpenAiWireClient(it) } ?: OpenAiWireClient()
    }

    val toolRegistry: BgmToolRegistry =
        BgmToolRegistry(
            listOfNotNull(
                scheduleTools?.tools(),
                subjectTools?.tools(),
                collectionTools?.tools(),
                playableSourceTools?.tools(),
                communityTools?.tools(),
                playbackRuleDiagnosticsTools?.tools(),
                webSearchTools?.tools(),
            ).flatten(),
        )

    private val effectiveAgentRunner: suspend (config: AiConfig, prompt: String, tools: BgmToolRegistry) -> String =
        agentRunner ?: { config, prompt, tools ->
            runPiAgent(
                wireClient = activeWireClient,
                config = config,
                prompt = prompt,
                tools = tools,
            )
        }

    override suspend fun execute(
        prompt: String,
        history: List<Pair<String, String>>,
    ): AppResult<String> {
        if (prompt.isBlank()) {
            return AppResult.Error(IllegalArgumentException("Prompt must not be blank."))
        }
        val config = settingsRepository.aiConfig.first()
        if (!config.provider.equals(AiConfig.PROVIDER_OLLAMA, ignoreCase = true) && config.apiKey.isBlank()) {
            return AppResult.Error(
                IllegalStateException("API key is missing for provider '${config.provider}'. Please configure it in AI settings."),
            )
        }

        val finalPrompt = buildFinalPrompt(prompt, history)
        AiToolActivity.clear()
        AiToolActivity.reportStatus("AI 正在思考并检索...")

        return try {
            val response =
                kotlinx.coroutines.withContext(coroutineDispatcher) {
                    withTimeout(AI_RUN_TIMEOUT_MS) {
                        executeWithRetry(config, finalPrompt)
                    }
                }
            AiToolActivity.clear()
            AppResult.Success(response)
        } catch (e: TimeoutCancellationException) {
            agentLogger.w(e) { "AI execution timed out: ${e.message}" }
            AiToolActivity.clear()
            AppResult.Error(e, "AI 响应超时（${AI_RUN_TIMEOUT_MS / 1000} 秒）：请重试，或更换更快的模型/端点。")
        } catch (e: kotlinx.coroutines.CancellationException) {
            AiToolActivity.clear()
            throw e
        } catch (e: Exception) {
            agentLogger.e(e) { "AI execution failed: ${e.message}" }
            AiToolActivity.clear()
            AppResult.Error(e, friendlyAiError(config, e))
        }
    }

    private suspend fun executeWithRetry(
        config: AiConfig,
        finalPrompt: String,
    ): String {
        var lastException: Exception? = null
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            try {
                return effectiveAgentRunner(config, finalPrompt, toolRegistry)
            } catch (e: Exception) {
                lastException = e
                val raw = (e.message ?: "") + (e.cause?.message?.let { " $it" } ?: "")
                val is429 = isRateLimitOrQuota(raw.lowercase())
                if (is429 && attempt < maxAttempts) {
                    val delayMs = extractRetryDelayMs(raw) ?: (attempt * 6000L)
                    val delaySec = (delayMs / 1000).coerceAtLeast(1)
                    AiToolActivity.reportStatus("AI 触发速率限制（429），等待重试（${delaySec}秒）...")
                    kotlinx.coroutines.delay(delayMs)
                } else {
                    throw e
                }
            }
        }
        throw (lastException ?: IllegalStateException("Agent execution failed"))
    }

    private fun buildFinalPrompt(
        prompt: String,
        history: List<Pair<String, String>>,
    ): String =
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

    override suspend fun fetchAvailableModels(
        endpoint: String?,
        apiKey: String?,
        provider: String?,
    ): AppResult<List<String>> {
        val config = settingsRepository.aiConfig.first()
        val target = resolveTargetConfig(config, endpoint, apiKey, provider)
        val modelsUrl = buildModelsUrl(target.endpoint, target.provider)
        catalogLogger.i { "Fetching models from: $modelsUrl (provider: ${target.provider})" }

        return try {
            val responseText = activeWireClient.fetchModelsRaw(target.endpoint, target.apiKey, target.provider)
            val models = parseModelsBody(responseText)
            if (models != null) {
                catalogLogger.i { "Successfully fetched ${models.size} models from $modelsUrl" }
                AppResult.Success(models)
            } else {
                catalogLogger.w { "Empty model list returned from $modelsUrl" }
                AppResult.Error(IllegalStateException("empty model list"), "端点未返回任何可用模型，请确认服务已正常运行")
            }
        } catch (e: Exception) {
            catalogLogger.e(e) { "Exception while fetching models from $modelsUrl" }
            val friendly = friendlyAiError(config.copy(endpoint = target.endpoint, provider = target.provider), e)
            AppResult.Error(e, friendly.ifBlank { "拉取模型列表失败：${e.message}" })
        }
    }
}

/**
 * Pi Agent 架构的极简 ReAct 循环：
 * 只要模型返回 tool_calls，立即派发执行并以 role="tool" 追加上下文，绝不因模型输出过程文本而早退。
 * 当无 tool_calls 时，提取 content（或兼容思考模型的 reasoning_content）作为最终回答。
 */
internal suspend fun runPiAgent(
    wireClient: OpenAiWireClient,
    config: AiConfig,
    prompt: String,
    tools: BgmToolRegistry,
    maxTurns: Int = 10,
): String {
    val messages = mutableListOf<WireChatMessage>()
    messages.add(WireChatMessage.system(BGM_AGENT_SYSTEM_PROMPT))
    messages.add(WireChatMessage.user(prompt))

    val toolDefinitions = tools.toDefinitions().ifEmpty { null }
    val json = aiJson

    var turns = 0
    while (turns++ < maxTurns) {
        val request =
            WireChatRequest(
                model = config.model.ifBlank { defaultModel(config) },
                messages = messages,
                tools = toolDefinitions,
                temperature = 0.3,
            )

        val response = wireClient.chatCompletion(config, request)
        if (response.error != null && !response.error.message.isNullOrBlank()) {
            throw IllegalStateException(response.error.message)
        }

        val choice =
            response.choices.firstOrNull()
                ?: throw IllegalStateException("Model returned empty choices")
        val assistantMessage = choice.message
        messages.add(assistantMessage.copy(reasoningContent = null))

        val toolCalls = assistantMessage.toolCalls
        if (toolCalls.isNullOrEmpty()) {
            val content = assistantMessage.content?.trim().orEmpty()
            val reasoning = assistantMessage.reasoningContent?.trim().orEmpty()
            return content.ifBlank { reasoning }
        }

        for (call in toolCalls) {
            val funcName = call.function.name
            val argsJson =
                try {
                    json.parseToJsonElement(call.function.arguments).jsonObject
                } catch (e: Exception) {
                    buildJsonObject {}
                }

            val toolResult =
                try {
                    tools.execute(funcName, argsJson)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    agentLogger.w(e) { "Tool $funcName execution failed: ${e.message}" }
                    "Tool $funcName failed: ${e.message ?: "unknown error"}"
                }
            messages.add(
                WireChatMessage.tool(
                    toolCallId = call.id,
                    content = toolResult,
                ),
            )
        }
    }
    throw IllegalStateException("Agent reached maximum turn limit ($maxTurns) without completing")
}

internal fun defaultModel(config: AiConfig): String =
    when {
        config.provider.equals(AiConfig.PROVIDER_OLLAMA, ignoreCase = true) -> "qwen2.5:7b"
        config.provider.equals(AiConfig.PROVIDER_GEMINI, ignoreCase = true) -> "gemini-2.5-flash"
        else -> "gpt-4o-mini"
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
        isRateLimitOrQuota(lowered) -> formatRateLimitError(raw, config)
        isAuthFailure(lowered) ->
            "鉴权失败（HTTP 401）：API 密钥无效或已过期，请到 AI 设置更新密钥。"
        isForbidden(lowered) -> formatForbiddenError(config)
        isNetworkOrTimeout(lowered) ->
            "无法连接到 AI 端点或请求超时：请检查网络与 Base URL 是否可达。"
        else -> {
            extractJsonErrorMessage(raw)?.let { innerMsg ->
                "AI 服务商返回错误：$innerMsg"
            } ?: raw.ifBlank { e.toString() }
        }
    }
}

internal fun formatRateLimitError(
    raw: String,
    config: AiConfig,
): String {
    val detail = extractJsonErrorMessage(raw)
    val prefix = if (!detail.isNullOrBlank()) "：$detail" else "：超出速率或配额限制（TPM/RPM Limit）"
    val suffix =
        if (config.endpoint.contains("groq.com", ignoreCase = true)) {
            "。Groq 免费层限制为每分钟 8,000 Token，包含多轮工具调用时较易触发。请稍等约 10 秒后重试，或在 AI 设置中更换高并发服务商（如 DeepSeek、硅基流动、智谱 GLM 等）。"
        } else {
            "。请稍后重试，或在 AI 设置中更换模型/端点。"
        }
    return "请求已被服务商限制（HTTP 429）$prefix$suffix"
}

internal fun formatForbiddenError(config: AiConfig): String =
    if (config.endpoint.contains("groq.com", ignoreCase = true)) {
        "端点拒绝了访问（HTTP 403）：Groq 对中国大陆 IP 存在访问地域限制，请配置代理访问，或切换至 DeepSeek、智谱 GLM、阿里百炼等国内直连服务商。"
    } else {
        "端点拒绝了访问（HTTP 403）：请确认密钥对该模型拥有调用权限。"
    }

internal fun extractRetryDelayMs(raw: String): Long? {
    val match = Regex("""try again in\s+(\d+(?:\.\d+)?)\s*s""", RegexOption.IGNORE_CASE).find(raw)
    return match?.let {
        val seconds = it.groupValues[1].toDoubleOrNull() ?: return@let null
        ((seconds + 1.0) * 1000).toLong().coerceIn(3000L, 25000L)
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
    "timeout" in lower ||
        "timed out" in lower ||
        "connection" in lower ||
        "unresolved" in lower ||
        "refused" in lower ||
        "eof" in lower ||
        "not enough data" in lower ||
        "socket" in lower ||
        "broken pipe" in lower ||
        "reset" in lower

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
 * 端点里是否已经带了 API 版本段：`/v1`、`/v4`、`/v1beta/openai`、`/compatible-mode/v1`、以 `/openai` 结尾……
 */
private val API_VERSION_SEGMENT = Regex("""/(?:v\d+[a-z0-9]*|openai)(?:/|$)""", RegexOption.IGNORE_CASE)

/**
 * 从用户填写的端点推导「API 基址」——chat 与 models 两条路径共用，避免各拼各的。
 */
internal fun resolveApiBase(
    rawEndpoint: String,
    provider: String,
): String {
    var base = rawEndpoint.trim().trimEnd('/')
    base = base.removeSuffix("/chat/completions").removeSuffix("/models").trimEnd('/')
    if (base.isBlank()) return ""
    if (provider.equals(AiConfig.PROVIDER_OLLAMA, ignoreCase = true)) return base
    return if (API_VERSION_SEGMENT.containsMatchIn(base)) base else "$base/v1"
}

/**
 * 模型列表端点：与 chat 共用同一个基址（[resolveApiBase]），只把最后一段换成 `models`；
 * Ollama 用原生 `/api/tags`。
 */
internal fun buildModelsUrl(
    rawEndpoint: String,
    provider: String,
): String {
    val isOllama = provider.equals(AiConfig.PROVIDER_OLLAMA, ignoreCase = true)
    if (isOllama) {
        val base =
            rawEndpoint
                .trim()
                .ifBlank { "http://10.0.2.2:11434" }
                .trimEnd('/')
                .removeSuffix("/api/tags")
                .removeSuffix("/v1")
                .trimEnd('/')
        return "$base/api/tags"
    }

    val defaultBase =
        if (provider.equals(AiConfig.PROVIDER_GEMINI, ignoreCase = true)) {
            "https://generativelanguage.googleapis.com/v1beta/openai"
        } else {
            "https://api.openai.com/v1"
        }
    return resolveApiBase(rawEndpoint.trim().ifBlank { defaultBase }, provider) + "/models"
}

private val catalogJson = aiJson

/**
 * 解析模型列表：
 * 兼容 OpenAI（data[].id）、Ollama（models[].name / models[].model）、
 * Gemini 原生（models[].name 去除 "models/" 前缀）、以及各类代理返回的字符串列表或根数组。
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
