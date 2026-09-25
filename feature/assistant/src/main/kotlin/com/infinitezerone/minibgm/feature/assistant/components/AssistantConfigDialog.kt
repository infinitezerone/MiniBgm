package com.infinitezerone.minibgm.feature.assistant.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.AiConfigProfile
import kotlinx.coroutines.launch

/**
 * 常见服务商预设模型与官方推荐端点原型
 */
internal data class ProviderPreset(
    val id: String,
    val name: String,
    val badge: String,
    val endpoint: String,
    val defaultModel: String,
    val provider: String,
    val isApiKeyRequired: Boolean,
    val tip: String,
    val popularModels: List<String> = emptyList(),
)

internal val PROVIDER_PRESETS: List<ProviderPreset> =
    listOf(
        ProviderPreset(
            id = "gemini",
            name = "Google Gemini",
            badge = "官方推荐",
            endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/",
            defaultModel = "gemini-2.5-flash",
            provider = AiConfig.PROVIDER_GEMINI,
            isApiKeyRequired = true,
            tip = "Google 官方提供免费调用额度，只需配置 API Key",
            popularModels = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-2.5-pro"),
        ),
        ProviderPreset(
            id = "deepseek",
            name = "DeepSeek",
            badge = "高性价比",
            endpoint = "https://api.deepseek.com/v1",
            defaultModel = "deepseek-chat",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "深度推理与高智能对话，极低 Token 成本",
            popularModels = listOf("deepseek-chat", "deepseek-reasoner"),
        ),
        ProviderPreset(
            id = "siliconflow",
            name = "硅基流动",
            badge = "开源聚合",
            endpoint = "https://api.siliconflow.cn/v1",
            defaultModel = "Qwen/Qwen2.5-7B-Instruct",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "国内稳定聚合服务，提供主流开源模型免费与优惠额度",
            popularModels = listOf("Qwen/Qwen2.5-7B-Instruct", "deepseek-ai/DeepSeek-V3", "THUDM/glm-4-9b-chat"),
        ),
        ProviderPreset(
            id = "zhipu",
            name = "智谱 GLM",
            badge = "国产标杆",
            endpoint = "https://open.bigmodel.cn/api/paas/v4",
            defaultModel = "glm-4-flash",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "清华系自研基座大模型，glm-4-flash 高并发免费调用",
            popularModels = listOf("glm-4-flash", "glm-4-air", "glm-4-plus"),
        ),
        ProviderPreset(
            id = "dashscope",
            name = "阿里百炼",
            badge = "通义千问",
            endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModel = "qwen-plus",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "阿里巴巴通义系列千亿级模型，兼容 OpenAI 规范",
            popularModels = listOf("qwen-plus", "qwen-turbo", "qwen-max"),
        ),
        ProviderPreset(
            id = "moonshot",
            name = "月之暗面 Kimi",
            badge = "长上下文",
            endpoint = "https://api.moonshot.cn/v1",
            defaultModel = "moonshot-v1-8k",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "超长上下文与深度文本理解能力",
            popularModels = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"),
        ),
        ProviderPreset(
            id = "ollama",
            name = "Ollama 本地",
            badge = "私有化",
            endpoint = "http://10.0.2.2:11434/v1",
            defaultModel = "qwen2.5:7b",
            provider = AiConfig.PROVIDER_OLLAMA,
            isApiKeyRequired = false,
            tip = "本地或局域网 NAS 私有部署，免 API Key，隐私安全",
            popularModels = listOf("qwen2.5:7b", "llama3.1:8b", "deepseek-r1:7b"),
        ),
        ProviderPreset(
            id = "openrouter",
            name = "OpenRouter",
            badge = "全球聚合",
            endpoint = "https://openrouter.ai/api/v1",
            defaultModel = "google/gemini-2.5-flash",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "统一网关路由全球数百款闭源与开源模型",
            popularModels = listOf("google/gemini-2.5-flash", "deepseek/deepseek-chat", "meta-llama/llama-3.3-70b-instruct"),
        ),
        ProviderPreset(
            id = "openai",
            name = "OpenAI",
            badge = "行业标杆",
            endpoint = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "OpenAI 官方 API 服务",
            popularModels = listOf("gpt-4o-mini", "gpt-4o", "o3-mini"),
        ),
        ProviderPreset(
            id = "groq",
            name = "Groq",
            badge = "极速推理",
            endpoint = "https://api.groq.com/openai/v1",
            defaultModel = "llama-3.3-70b-versatile",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "LPU 硬件加速，极低延迟秒级响应（需海外代理，不支持大陆/香港 IP）",
            popularModels = listOf("llama-3.3-70b-versatile", "deepseek-r1-distill-llama-70b"),
        ),
        ProviderPreset(
            id = "custom",
            name = "自定义端点",
            badge = "OpenAI 兼容",
            endpoint = "",
            defaultModel = "gpt-4o-mini",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "兼容任何支持 OpenAI 规范的标准第三方反代或自建网关",
            popularModels = emptyList(),
        ),
    )

/** 根据输入的 API Key 格式特征智能匹配对应的服务商预设 */
internal fun detectProviderFromApiKey(apiKey: String): ProviderPreset? {
    val key = apiKey.trim()
    if (key.isBlank()) return null

    val targetId =
        when {
            // Google Gemini AI Studio (以 AIzaSy 开头，通常 39 位)
            key.startsWith("AIzaSy") -> "gemini"
            // OpenRouter (以 sk-or-v1- 开头)
            key.startsWith("sk-or-v1-") -> "openrouter"
            // OpenAI 官方 Project / Service / Admin Key (sk-proj-, sk-admin-, sk-svcacct-)
            key.startsWith("sk-proj-") || key.startsWith("sk-admin-") || key.startsWith("sk-svcacct-") -> "openai"
            // OpenAI 经典个人 Key (sk- 开头接 48 或 51 位字符)
            key.matches(Regex("""^sk-[A-Za-z0-9]{48,51}$""")) -> "openai"
            // Groq (以 gsk_ 开头)
            key.startsWith("gsk_") -> "groq"
            // 智谱开放平台 API Key (通常为 32位十六进制/字符加点再加16-32位字符)
            key.matches(Regex("""^[0-9a-zA-Z]{32}\.[0-9a-zA-Z]{16,32}$""")) -> "zhipu"
            // DeepSeek 官方 API Key (格式固定为 sk- 加上 32 位十六进制字符，总长 35 位)
            key.matches(Regex("^sk-[0-9a-fA-F]{32}$")) -> "deepseek"
            else -> null
        } ?: return null

    return PROVIDER_PRESETS.firstOrNull { it.id == targetId }
}

/**
 * 检查模型是否已知在官方端点暂不支持 Tool Calling（函数调用 / 智能体工具执行）。
 * 若已知不支持，UI 渲染轻量警示，避免用户误选后导致查时刻表或打卡报错。
 */
internal fun isModelKnownUnsupportedToolCall(modelName: String): Boolean {
    val lower = modelName.trim().lowercase()
    return lower == "deepseek-reasoner" ||
        lower == "o1-preview" ||
        lower == "o1-mini"
}

/** 根据输入的 Base URL 自动解析并匹配协议提供商 */
internal fun autoDetectProvider(endpoint: String): String {
    val lowered = endpoint.trim().lowercase()
    return when {
        "generativelanguage.googleapis.com" in lowered || "gemini" in lowered -> AiConfig.PROVIDER_GEMINI
        "11434" in lowered || "ollama" in lowered -> AiConfig.PROVIDER_OLLAMA
        else -> AiConfig.PROVIDER_CUSTOM
    }
}

/** 规范化 Base URL：自动补齐 scheme、剥离多余的 chat/models 路径并剔除尾部斜杠 */
internal fun normalizeEndpoint(rawEndpoint: String): String {
    val trimmed = rawEndpoint.trim()
    if (trimmed.isBlank()) return ""
    val cleaned =
        trimmed
            .removeSuffix("/chat/completions")
            .removeSuffix("/chat/completions/")
            .removeSuffix("/models")
            .removeSuffix("/models/")
            .trimEnd('/')

    val withScheme =
        if (!cleaned.startsWith("http://", ignoreCase = true) &&
            !cleaned.startsWith("https://", ignoreCase = true)
        ) {
            if (cleaned.startsWith("localhost", ignoreCase = true) ||
                cleaned.startsWith("127.0.0.1") ||
                cleaned.startsWith("10.0.2.2") ||
                cleaned.startsWith("192.168.")
            ) {
                "http://$cleaned"
            } else {
                "https://$cleaned"
            }
        } else {
            cleaned
        }
    return withScheme.trimEnd('/')
}

/** 校验模型名称与端点/服务商是否疑似错配 */
internal fun checkModelProviderMismatch(
    endpoint: String,
    model: String,
    provider: String,
): String? {
    val ep = endpoint.trim().lowercase()
    val m = model.trim().lowercase()
    if (m.isBlank() || ep.isBlank()) return null

    // 端点是 DeepSeek，模型却填了 gemini 或 gpt
    if ("deepseek" in ep && ("gemini" in m || "gpt-" in m || "claude" in m)) {
        return "当前端点为 DeepSeek，模型疑似错配，建议切换为 deepseek-chat 或 deepseek-reasoner"
    }
    // 端点是 Google Gemini，模型却填了 deepseek / qwen / gpt
    if (("generativelanguage.googleapis.com" in ep || provider == AiConfig.PROVIDER_GEMINI) &&
        ("deepseek" in m || "qwen" in m || "gpt-" in m || "claude" in m)
    ) {
        return "当前端点为 Google Gemini，模型疑似错配，建议切换为 gemini-2.5-flash"
    }
    // 端点是 OpenAI 官方，模型填了 deepseek / gemini / qwen
    if ("api.openai.com" in ep && ("deepseek" in m || "gemini" in m || "qwen" in m)) {
        return "当前端点为 OpenAI 官方，模型疑似错配，建议切换为 gpt-4o-mini"
    }
    // 端点是 智谱，模型填了 gemini / gpt / deepseek
    if ("bigmodel.cn" in ep && ("gemini" in m || "gpt-" in m || "deepseek" in m)) {
        return "当前端点为智谱 GLM，模型疑似错配，建议切换为 glm-4-flash"
    }
    return null
}

internal fun defaultModelFor(provider: String): String =
    when (provider) {
        AiConfig.PROVIDER_GEMINI -> "gemini-2.5-flash"
        AiConfig.PROVIDER_OLLAMA -> "qwen2.5:7b"
        else -> "gpt-4o-mini"
    }

internal fun providerDisplayName(provider: String): String =
    when (provider) {
        AiConfig.PROVIDER_GEMINI -> "Google Gemini"
        AiConfig.PROVIDER_OLLAMA -> "Ollama (自建服务)"
        else -> "OpenAI 兼容协议"
    }

/** 方案默认名：服务商（自定义取端点主机名）+ 模型，用户可改 */
internal fun defaultProfileName(
    provider: String,
    model: String,
    endpoint: String,
): String {
    val base =
        when (provider) {
            AiConfig.PROVIDER_GEMINI -> "Gemini"
            AiConfig.PROVIDER_OLLAMA -> "Ollama"
            else -> {
                val host =
                    runCatching { java.net.URI(endpoint.trim()).host }
                        .getOrNull()
                        ?.removePrefix("www.")
                        .orEmpty()
                host.ifBlank { "自定义端点" }
            }
        }
    val modelPart = model.trim().ifBlank { defaultModelFor(provider) }
    return "$base · $modelPart"
}

/** 连通测试诊断状态机 */
internal sealed interface ConnectionDiagnosticState {
    data object Idle : ConnectionDiagnosticState

    data object Testing : ConnectionDiagnosticState

    data class Success(
        val latencyMs: Long,
        val models: List<String>,
    ) : ConnectionDiagnosticState

    data class Failure(
        val summary: String,
        val detail: String,
    ) : ConnectionDiagnosticState
}

/**
 * 模型特征能力标签
 */
internal enum class ModelCapability(
    val label: String,
) {
    REASONING("推理"),
    VISION("视觉"),
    LIGHTWEIGHT("轻量"),
}

/**
 * 依据模型标识符自动提取其主要能力特征
 */
internal fun detectModelCapabilities(modelName: String): List<ModelCapability> {
    val name = modelName.lowercase()
    val caps = mutableListOf<ModelCapability>()
    if (name.contains("reasoner") || name.contains("r1") || name.contains("o1") || name.contains("o3") || name.contains("thinking")) {
        caps.add(ModelCapability.REASONING)
    }
    if (name.contains("vision") || name.contains("vl") || name.contains("omni") || name.contains("4o")) {
        caps.add(ModelCapability.VISION)
    }
    if (name.contains("flash") ||
        name.contains("mini") ||
        name.contains("nano") ||
        name.contains("lite") ||
        name.contains("small") ||
        name.contains("7b") ||
        name.contains("8b")
    ) {
        caps.add(ModelCapability.LIGHTWEIGHT)
    }
    return caps
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantConfigDialog(
    currentConfig: AiConfig,
    onSaveConfig: (AiConfig) -> Unit,
    onDismiss: () -> Unit,
    onFetchModels: suspend (endpoint: String, apiKey: String, provider: String) -> AppResult<List<String>>,
    aiProfiles: List<AiConfigProfile> = emptyList(),
    activeProfileId: String = "",
    onActivateProfile: (String) -> Unit = {},
    onSaveProfile: (profileId: String?, name: String, config: AiConfig) -> Unit = { _, _, _ -> },
    onDeleteProfile: (String) -> Unit = {},
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)

    // 当前表单编辑中的方案（草稿态与激活态解耦）
    var editingProfileId by remember {
        mutableStateOf(aiProfiles.firstOrNull { it.id == activeProfileId }?.id ?: aiProfiles.firstOrNull()?.id)
    }
    val initialProfile =
        remember(editingProfileId, aiProfiles) {
            aiProfiles.firstOrNull { it.id == editingProfileId }
        }

    var profileName by remember { mutableStateOf(initialProfile?.name.orEmpty()) }
    var endpoint by remember {
        mutableStateOf(
            initialProfile?.config?.endpoint
                ?: currentConfig.endpoint.ifBlank { "https://generativelanguage.googleapis.com/v1beta/openai/" },
        )
    }
    var selectedProvider by remember {
        mutableStateOf(initialProfile?.config?.provider ?: currentConfig.provider.ifBlank { autoDetectProvider(endpoint) })
    }
    var apiKey by remember { mutableStateOf(initialProfile?.config?.apiKey ?: currentConfig.apiKey) }
    var model by remember {
        mutableStateOf(
            initialProfile?.config?.model
                ?: currentConfig.model.ifBlank { defaultModelFor(selectedProvider) },
        )
    }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    // 选中的预设 ID（单选排他，避免多个 custom 服务商全部高亮）
    var selectedPresetId by remember {
        mutableStateOf(
            PROVIDER_PRESETS.firstOrNull { it.id != "custom" && it.endpoint == endpoint }?.id
                ?: if (selectedProvider == AiConfig.PROVIDER_GEMINI) {
                    "gemini"
                } else if (selectedProvider == AiConfig.PROVIDER_OLLAMA) {
                    "ollama"
                } else {
                    "custom"
                },
        )
    }

    // 远端拉取到的可用模型列表（与推荐模型区分）
    var availableRemoteModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModelPickerSheet by remember { mutableStateOf(false) }
    var isModelConfigured by remember {
        mutableStateOf(initialProfile != null && initialProfile.config.model.isNotBlank())
    }

    // 连通诊断状态
    var diagnosticState by remember { mutableStateOf<ConnectionDiagnosticState>(ConnectionDiagnosticState.Idle) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    fun loadProfileToDraft(profile: AiConfigProfile) {
        editingProfileId = profile.id
        profileName = profile.name
        endpoint = profile.config.endpoint
        selectedProvider = profile.config.provider
        apiKey = profile.config.apiKey
        model = profile.config.model
        selectedPresetId =
            PROVIDER_PRESETS.firstOrNull { it.id != "custom" && it.endpoint == profile.config.endpoint }?.id
                ?: if (profile.config.provider == AiConfig.PROVIDER_GEMINI) {
                    "gemini"
                } else if (profile.config.provider == AiConfig.PROVIDER_OLLAMA) {
                    "ollama"
                } else {
                    "custom"
                }
        availableRemoteModels = emptyList()
        isModelConfigured = profile.config.model.isNotBlank()
        diagnosticState = ConnectionDiagnosticState.Idle
    }

    fun applyPreset(preset: ProviderPreset) {
        selectedPresetId = preset.id
        selectedProvider = preset.provider
        if (preset.endpoint.isNotBlank()) {
            endpoint = preset.endpoint
        }
        if (preset.defaultModel.isNotBlank()) {
            model = preset.defaultModel
        }
        availableRemoteModels = emptyList()
        isModelConfigured = false
        diagnosticState = ConnectionDiagnosticState.Idle
    }

    fun onApiKeyUpdated(newKey: String) {
        apiKey = newKey
        diagnosticState = ConnectionDiagnosticState.Idle

        val detected = detectProviderFromApiKey(newKey)
        if (detected != null) {
            val isEndpointEmptyOrDefault =
                endpoint.isBlank() ||
                    PROVIDER_PRESETS.any { it.endpoint.isNotBlank() && it.endpoint == endpoint.trim() }
            if (isEndpointEmptyOrDefault && selectedPresetId != detected.id) {
                applyPreset(detected)
            }
        }
    }

    fun startConnectionTest() {
        val normalized = normalizeEndpoint(endpoint)
        if (normalized.isBlank()) return
        diagnosticState = ConnectionDiagnosticState.Testing
        coroutineScope.launch {
            val startMs = System.currentTimeMillis()
            when (val result = onFetchModels(normalized, apiKey.trim(), selectedProvider)) {
                is AppResult.Success -> {
                    val elapsed = System.currentTimeMillis() - startMs
                    val currentPreset = PROVIDER_PRESETS.firstOrNull { it.id == selectedPresetId }
                    val resolvedModels =
                        if (result.data.isNotEmpty()) {
                            result.data
                        } else {
                            currentPreset?.popularModels.orEmpty()
                        }
                    availableRemoteModels = resolvedModels
                    if (resolvedModels.isNotEmpty()) {
                        if (model.isBlank() || model !in resolvedModels) {
                            val preferred = currentPreset?.popularModels?.firstOrNull { it in resolvedModels } ?: resolvedModels.first()
                            model = preferred
                        }
                        isModelConfigured = true
                    }
                    diagnosticState =
                        ConnectionDiagnosticState.Success(
                            latencyMs = elapsed.coerceAtLeast(1),
                            models = result.data,
                        )
                }
                is AppResult.Error -> {
                    val rawMsg = result.message.ifBlank { result.throwable.message.orEmpty() }
                    val summary =
                        when {
                            "401" in rawMsg || "unauthorized" in rawMsg.lowercase() -> "鉴权失败 (HTTP 401)"
                            "403" in rawMsg || "forbidden" in rawMsg.lowercase() -> {
                                if ("groq" in normalized.lowercase()) {
                                    "访问受限 (HTTP 403：Groq 限制大陆/香港 IP)"
                                } else {
                                    "访问受限 (HTTP 403)"
                                }
                            }
                            "timeout" in rawMsg.lowercase() || "connect" in rawMsg.lowercase() -> "连接超时 / 无法访问"
                            else -> "连通失败"
                        }
                    val detail =
                        when {
                            "401" in rawMsg || "unauthorized" in rawMsg.lowercase() -> "API Key 无效、已过期或无权访问该模型，请检查密钥"
                            "403" in rawMsg || "forbidden" in rawMsg.lowercase() -> {
                                if ("groq" in normalized.lowercase()) {
                                    "Groq 官方在 Cloudflare 边缘阻断了中国大陆及香港 IP 请求。请在设备上开启科学上网/VPN 并切换至美区/日区/新加坡等支持节点，或使用第三方中转代理。"
                                } else {
                                    "端点拒绝访问（HTTP 403），请检查账号权限或 IP 地域限制"
                                }
                            }
                            "timeout" in rawMsg.lowercase() || "connect" in rawMsg.lowercase() -> "请检查网络代理环境或服务地址是否拼写正确"
                            rawMsg.isNotBlank() -> rawMsg
                            else -> "请确认端点与网络可用性后重试"
                        }
                    diagnosticState = ConnectionDiagnosticState.Failure(summary = summary, detail = detail)
                }
                AppResult.Loading -> Unit
            }
        }
    }

    fun commitAndSave() {
        val normalized =
            normalizeEndpoint(endpoint).ifBlank {
                "https://generativelanguage.googleapis.com/v1beta/openai/"
            }
        val finalModel = model.trim().ifBlank { defaultModelFor(selectedProvider) }
        val newConfig =
            AiConfig(
                endpoint = normalized,
                apiKey = apiKey.trim(),
                model = finalModel,
                provider = selectedProvider,
            )
        onSaveConfig(newConfig)
        val name =
            profileName.trim().ifBlank {
                defaultProfileName(selectedProvider, finalModel, normalized)
            }
        onSaveProfile(editingProfileId, name, newConfig)
        onDismiss()
    }

    BgmModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
        ) {
            // 1. 顶部标题栏与关闭
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI 智能体配置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "端点服务、模型路由与凭据方案",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 2. 表单可滚动主体
            Column(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                // 服务商一键预设区
                Text(
                    text = "快捷服务商预设",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PROVIDER_PRESETS.forEach { preset ->
                        val isSelected = preset.id == selectedPresetId
                        FilterChip(
                            selected = isSelected,
                            onClick = { applyPreset(preset) },
                            label = { Text(preset.name) },
                            leadingIcon =
                                if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 已保存配置方案池（多方案快速载入）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "配置方案池",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            editingProfileId = null
                            profileName = ""
                            applyPreset(PROVIDER_PRESETS.first())
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新建方案")
                    }
                }

                if (aiProfiles.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        aiProfiles.forEach { profile ->
                            val isActive = profile.id == activeProfileId
                            val isEditing = profile.id == editingProfileId
                            FilterChip(
                                selected = isEditing,
                                onClick = { loadProfileToDraft(profile) },
                                label = {
                                    val label = if (isActive) "${profile.name} (生效中)" else profile.name
                                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                            )
                        }
                    }

                    if (editingProfileId != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = {
                                editingProfileId = null
                                profileName = if (profileName.isNotBlank()) "$profileName (副本)" else ""
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("克隆为新方案")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("删除当前方案", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 方案名称输入
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("方案名称") },
                    placeholder = { Text(defaultProfileName(selectedProvider, model, endpoint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 端点 Base URL 输入框
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = {
                        endpoint = it
                        selectedProvider = autoDetectProvider(it)
                        selectedPresetId =
                            PROVIDER_PRESETS.firstOrNull { preset -> preset.id != "custom" && preset.endpoint == it.trim() }?.id
                                ?: "custom"
                        availableRemoteModels = emptyList()
                        diagnosticState = ConnectionDiagnosticState.Idle
                    },
                    label = { Text("服务地址 (Base URL)") },
                    placeholder = { Text("例如 https://api.deepseek.com/v1") },
                    trailingIcon = {
                        if (endpoint.isNotBlank()) {
                            IconButton(onClick = {
                                endpoint = ""
                                selectedPresetId = "custom"
                                availableRemoteModels = emptyList()
                                diagnosticState = ConnectionDiagnosticState.Idle
                            }) {
                                Icon(Icons.Filled.Clear, contentDescription = "清空端点")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // API Key 输入框（集成安全开关与剪贴板一键粘贴）
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { onApiKeyUpdated(it) },
                    label = { Text("API Key / 访问凭据") },
                    placeholder = { Text(if (selectedProvider == AiConfig.PROVIDER_OLLAMA) "本地 Ollama 免密钥" else "填入 API Key") },
                    singleLine = true,
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val pasted =
                                    clipboardManager
                                        .getText()
                                        ?.text
                                        .orEmpty()
                                        .trim()
                                if (pasted.isNotBlank()) {
                                    onApiKeyUpdated(pasted)
                                }
                            }) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = "粘贴剪贴板内容")
                            }
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (isApiKeyVisible) "隐藏密钥" else "显示明文",
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // 2. 智能模型选择卡片：未探测到可用模型前隐藏，测试连通后自动展开
                val currentPreset = PROVIDER_PRESETS.firstOrNull { it.id == selectedPresetId }
                val isTestingConnection = diagnosticState is ConnectionDiagnosticState.Testing
                val isModelVisible = availableRemoteModels.isNotEmpty() || isModelConfigured

                AnimatedVisibility(visible = isModelVisible) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        ModelSelectorCard(
                            model = model,
                            selectedProvider = selectedProvider,
                            selectedPreset = currentPreset,
                            remoteModelsCount = availableRemoteModels.size,
                            isTesting = isTestingConnection,
                            onOpenPicker = { showModelPickerSheet = true },
                            onSyncRemote = { startConnectionTest() },
                        )
                        val mismatchWarning =
                            remember(endpoint, model, selectedProvider) {
                                checkModelProviderMismatch(endpoint, model, selectedProvider)
                            }
                        if (mismatchWarning != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = mismatchWarning,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. 连通性测试诊断卡片（状态机展示）
                Spacer(modifier = Modifier.height(14.dp))
                when (val state = diagnosticState) {
                    ConnectionDiagnosticState.Idle -> Unit
                    ConnectionDiagnosticState.Testing -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("正在探测端点连通性并同步可用模型...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    is ConnectionDiagnosticState.Success -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "连通正常 · 延迟 ${state.latencyMs}ms",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text =
                                            if (state.models.isNotEmpty()) {
                                                "已成功同步 ${state.models.size} 个可用模型至上方列表"
                                            } else {
                                                "端点响应正常，但未返回模型列表"
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (state.models.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showModelPickerSheet = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text("选择模型", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                    is ConnectionDiagnosticState.Failure -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = state.summary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = state.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. 底部吸底固定操作栏（Sticky Actions）
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { startConnectionTest() },
                    enabled = endpoint.isNotBlank() && diagnosticState !is ConnectionDiagnosticState.Testing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (diagnosticState is ConnectionDiagnosticState.Testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("测试中")
                    } else {
                        Icon(imageVector = Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("测试连接")
                    }
                }

                Button(
                    onClick = { commitAndSave() },
                    modifier = Modifier.weight(1.3f),
                ) {
                    Text("保存并启用")
                }
            }
        }
    }

    // 智能模型搜索与选择弹窗
    if (showModelPickerSheet) {
        val currentPreset = PROVIDER_PRESETS.firstOrNull { it.id == selectedPresetId }
        val presetModels = currentPreset?.popularModels.orEmpty()
        ModelPickerDialog(
            currentModel = model.ifBlank { defaultModelFor(selectedProvider) },
            remoteModels = availableRemoteModels,
            presetModels = presetModels,
            onSelectModel = { selected ->
                model = selected
                diagnosticState = ConnectionDiagnosticState.Idle
            },
            onDismiss = { showModelPickerSheet = false },
        )
    }

    // 删除方案确认防误触弹窗
    if (showDeleteConfirmDialog && editingProfileId != null) {
        val targetName = profileName.ifBlank { "当前方案" }
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除方案") },
            text = { Text("确定要删除方案「$targetName」吗？删除后配置不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = editingProfileId ?: return@TextButton
                        onDeleteProfile(toDelete)
                        showDeleteConfirmDialog = false
                        editingProfileId = null
                        profileName = ""
                        applyPreset(PROVIDER_PRESETS.first())
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * 智能模型聚合卡片组件
 * 紧凑展示当前选中的模型、状态/来源，并提供原地探测同步能力
 */
@Composable
private fun ModelSelectorCard(
    model: String,
    selectedProvider: String,
    selectedPreset: ProviderPreset?,
    remoteModelsCount: Int,
    isTesting: Boolean,
    onOpenPicker: () -> Unit,
    onSyncRemote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentModelDisplay = model.ifBlank { defaultModelFor(selectedProvider) }
    val isRemoteSynced = remoteModelsCount > 0
    val subtitle =
        if (isRemoteSynced) {
            "已同步 $remoteModelsCount 个可用模型 · 点击切换"
        } else if (selectedPreset != null) {
            "${selectedPreset.name} 预设推荐 · 点击切换"
        } else {
            "点击选择模型"
        }

    Surface(
        onClick = onOpenPicker,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "模型 (Model)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentModelDisplay,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isModelKnownUnsupportedToolCall(currentModelDisplay)) {
                    Text(
                        text = "⚠️ 官方暂不支持工具调用（无法查番或打卡）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRemoteSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 原地同步远端模型按钮
                IconButton(
                    onClick = onSyncRemote,
                    enabled = !isTesting,
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "同步可用模型",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                // 展开指示图标
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * 完整模型选择面板弹窗：支持搜索、属性特征标签识别、远端/预设分组与直接使用输入词
 */
@Composable
private fun ModelPickerDialog(
    currentModel: String,
    remoteModels: List<String>,
    presetModels: List<String>,
    onSelectModel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCapabilityFilter by remember { mutableStateOf<ModelCapability?>(null) }
    val allUniqueModels =
        remember(remoteModels, presetModels) {
            (remoteModels + presetModels).distinct()
        }

    val capabilityFilteredModels =
        remember(allUniqueModels, selectedCapabilityFilter) {
            if (selectedCapabilityFilter == null) {
                allUniqueModels
            } else {
                allUniqueModels.filter { detectModelCapabilities(it).contains(selectedCapabilityFilter) }
            }
        }

    val trimmedQuery = searchQuery.trim()
    val isSearching = trimmedQuery.isNotBlank()

    // 过滤候选列表
    val filteredModels =
        remember(capabilityFilteredModels, trimmedQuery) {
            if (trimmedQuery.isBlank()) {
                capabilityFilteredModels
            } else {
                capabilityFilteredModels.filter { it.contains(trimmedQuery, ignoreCase = true) }
            }
        }

    val hasExactMatch =
        remember(allUniqueModels, trimmedQuery) {
            allUniqueModels.any { it.equals(trimmedQuery, ignoreCase = true) }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "选择模型",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (remoteModels.isNotEmpty()) "已连接远端服务 · ${remoteModels.size} 个模型就绪" else "服务商推荐模型库",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索模型名称...") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "清除搜索")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = selectedCapabilityFilter == null,
                        onClick = { selectedCapabilityFilter = null },
                        label = { Text("全部", style = MaterialTheme.typography.labelSmall) },
                    )
                    ModelCapability.entries.forEach { cap ->
                        val isSelected = selectedCapabilityFilter == cap
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCapabilityFilter = if (isSelected) null else cap
                            },
                            label = { Text(cap.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 如果搜索词无精确匹配，提供直接使用当前搜索词作为自定义模型的通道
                if (isSearching && !hasExactMatch) {
                    Surface(
                        onClick = {
                            onSelectModel(trimmedQuery)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "使用「$trimmedQuery」",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "直接填入作为自定义模型",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (filteredModels.isEmpty() && (!isSearching || hasExactMatch)) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "未找到匹配模型",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        // 1. 如果无搜索且无能力筛选，且有远端模型：展示分组
                        if (!isSearching && selectedCapabilityFilter == null && remoteModels.isNotEmpty()) {
                            item {
                                Text(
                                    text = "🌐 远端已同步模型 (${remoteModels.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                            }
                            items(remoteModels) { item ->
                                ModelPickerItem(
                                    name = item,
                                    isSelected = item == currentModel,
                                    onSelect = {
                                        onSelectModel(item)
                                        onDismiss()
                                    },
                                )
                            }
                            val presetOnly = presetModels.filterNot { it in remoteModels }
                            if (presetOnly.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "💡 推荐候选模型",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                    )
                                }
                                items(presetOnly) { item ->
                                    ModelPickerItem(
                                        name = item,
                                        isSelected = item == currentModel,
                                        onSelect = {
                                            onSelectModel(item)
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        } else {
                            // 搜索结果列表或筛选结果
                            items(filteredModels) { item ->
                                ModelPickerItem(
                                    name = item,
                                    isSelected = item == currentModel,
                                    onSelect = {
                                        onSelectModel(item)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun ModelPickerItem(
    name: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val capabilities = remember(name) { detectModelCapabilities(name) }
    val isUnsupportedTool = remember(name) { isModelKnownUnsupportedToolCall(name) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (capabilities.isNotEmpty() || isUnsupportedTool) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isUnsupportedTool) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            ) {
                                Text(
                                    text = "⚠️ 官方暂无工具调用支持",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                        capabilities.forEach { cap ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color =
                                    when (cap) {
                                        ModelCapability.REASONING -> MaterialTheme.colorScheme.tertiaryContainer
                                        ModelCapability.VISION -> MaterialTheme.colorScheme.secondaryContainer
                                        ModelCapability.LIGHTWEIGHT -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                            ) {
                                Text(
                                    text = cap.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        when (cap) {
                                            ModelCapability.REASONING -> MaterialTheme.colorScheme.onTertiaryContainer
                                            ModelCapability.VISION -> MaterialTheme.colorScheme.onSecondaryContainer
                                            ModelCapability.LIGHTWEIGHT -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "当前使用",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    }
}
