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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
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

/** 根据输入的 Base URL 自动解析并匹配协议提供商 */
internal fun autoDetectProvider(endpoint: String): String {
    val lowered = endpoint.trim().lowercase()
    return when {
        "generativelanguage.googleapis.com" in lowered || "gemini" in lowered -> AiConfig.PROVIDER_GEMINI
        "11434" in lowered || "ollama" in lowered -> AiConfig.PROVIDER_OLLAMA
        else -> AiConfig.PROVIDER_CUSTOM
    }
}

/** 规范化 Base URL：自动补齐 scheme 并剔除末尾多余斜杠 */
internal fun normalizeEndpoint(rawEndpoint: String): String {
    val trimmed = rawEndpoint.trim()
    if (trimmed.isBlank()) return ""
    val withScheme =
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            if (trimmed.startsWith("localhost", ignoreCase = true) ||
                trimmed.startsWith("127.0.0.1") ||
                trimmed.startsWith("10.0.2.2") ||
                trimmed.startsWith("192.168.")
            ) {
                "http://$trimmed"
            } else {
                "https://$trimmed"
            }
        } else {
            trimmed
        }
    return withScheme.trimEnd('/')
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
    if (name.contains("flash") || name.contains("mini") || name.contains("nano") || name.contains("lite") || name.contains("small") ||
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
    var isManualModelMode by remember {
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
        isManualModelMode = profile.config.model.isNotBlank()
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
        isManualModelMode = false
        diagnosticState = ConnectionDiagnosticState.Idle
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
                    availableRemoteModels = result.data
                    if (result.data.isNotEmpty()) {
                        val currentPreset = PROVIDER_PRESETS.firstOrNull { it.id == selectedPresetId }
                        if (model.isBlank() || model !in result.data) {
                            val preferred = currentPreset?.popularModels?.firstOrNull { it in result.data } ?: result.data.first()
                            model = preferred
                        }
                    } else {
                        isManualModelMode = true
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
                            "timeout" in rawMsg.lowercase() || "connect" in rawMsg.lowercase() -> "连接超时 / 无法访问"
                            else -> "连通失败"
                        }
                    val detail =
                        when {
                            "401" in rawMsg || "unauthorized" in rawMsg.lowercase() -> "API Key 无效、已过期或无权访问该模型，请检查密钥"
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
                        ) {
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
                    supportingText = { Text("保存时按此名称入池；与已有方案同名则自动覆盖") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                    supportingText = {
                        Text(
                            text = "匹配协议：${providerDisplayName(selectedProvider)}",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
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

                Spacer(modifier = Modifier.height(8.dp))

                // API Key 输入框（集成安全开关与剪贴板一键粘贴）
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        diagnosticState = ConnectionDiagnosticState.Idle
                    },
                    label = { Text("API Key / 访问凭据") },
                    placeholder = { Text(if (selectedProvider == AiConfig.PROVIDER_OLLAMA) "本地 Ollama 免密钥" else "填入 API Key") },
                    supportingText = {
                        if (selectedProvider != AiConfig.PROVIDER_OLLAMA && apiKey.isBlank()) {
                            Text("使用云端模型服务需配置 API 密钥", color = MaterialTheme.colorScheme.error)
                        } else if (selectedProvider == AiConfig.PROVIDER_OLLAMA) {
                            Text("私有部署环境可保持留空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
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
                                    apiKey = pasted
                                    diagnosticState = ConnectionDiagnosticState.Idle
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

                Spacer(modifier = Modifier.height(10.dp))

                // 2. 智能模型选择器：未检测出可用模型前默认隐藏，仅保留手动输入入口或待探测成功后动态展开
                val currentPreset = PROVIDER_PRESETS.firstOrNull { it.id == selectedPresetId }
                val isTestingConnection = diagnosticState is ConnectionDiagnosticState.Testing
                val isModelVisible = availableRemoteModels.isNotEmpty() || isManualModelMode

                if (!isModelVisible) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "💡 点击下方「测试连接」以探测并获取可用模型",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { isManualModelMode = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("手动填写模型", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                AnimatedVisibility(visible = isModelVisible) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (isManualModelMode) {
                            OutlinedTextField(
                                value = model,
                                onValueChange = {
                                    model = it
                                    diagnosticState = ConnectionDiagnosticState.Idle
                                },
                                label = { Text("自定义模型名称 (Model)") },
                                placeholder = { Text(defaultModelFor(selectedProvider)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.SmartToy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (model.isNotBlank()) {
                                            IconButton(onClick = { model = "" }) {
                                                Icon(Icons.Filled.Clear, contentDescription = "清空模型")
                                            }
                                        }
                                        if (availableRemoteModels.isNotEmpty()) {
                                            IconButton(onClick = { isManualModelMode = false }) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.List,
                                                    contentDescription = "返回选择器卡片",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            ModelSelectorCard(
                                model = model,
                                selectedProvider = selectedProvider,
                                selectedPreset = currentPreset,
                                remoteModelsCount = availableRemoteModels.size,
                                isTesting = isTestingConnection,
                                onOpenPicker = { showModelPickerSheet = true },
                                onSyncRemote = { startConnectionTest() },
                                onSwitchToManual = { isManualModelMode = true },
                            )
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
 * 紧凑展示当前选中的模型、状态/来源，并提供原地探测同步与切换手输能力
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
    onSwitchToManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentModelDisplay = model.ifBlank { defaultModelFor(selectedProvider) }
    val isRemoteSynced = remoteModelsCount > 0
    val subtitle =
        if (isRemoteSynced) {
            "已同步 $remoteModelsCount 个远端可用模型 · 点击切换"
        } else if (selectedPreset != null) {
            "${selectedPreset.name} 预设推荐 · 点击选择"
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
                            contentDescription = "同步远端模型",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                // 切换为自由手输模式按钮
                IconButton(onClick = onSwitchToManual) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "手动输入自定义模型",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
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
    val allUniqueModels =
        remember(remoteModels, presetModels) {
            (remoteModels + presetModels).distinct()
        }

    val trimmedQuery = searchQuery.trim()
    val isSearching = trimmedQuery.isNotBlank()

    // 过滤候选列表
    val filteredModels =
        remember(allUniqueModels, trimmedQuery) {
            if (trimmedQuery.isBlank()) {
                allUniqueModels
            } else {
                allUniqueModels.filter { it.contains(trimmedQuery, ignoreCase = true) }
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
                        .heightIn(max = 420.dp),
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

                Spacer(modifier = Modifier.height(10.dp))

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
                            text = "未找到可用模型",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        // 1. 如果不在搜索模式且有远端模型：展示分组
                        if (!isSearching && remoteModels.isNotEmpty()) {
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
                            // 搜索结果列表或仅有预设模型
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
                if (capabilities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
