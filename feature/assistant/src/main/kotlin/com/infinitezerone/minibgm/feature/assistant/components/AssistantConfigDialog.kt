package com.infinitezerone.minibgm.feature.assistant.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.AiConfigProfile
import kotlinx.coroutines.launch

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
                            "403" in rawMsg || "forbidden" in rawMsg.lowercase() -> "访问受限 (HTTP 403)"
                            "timeout" in rawMsg.lowercase() || "connect" in rawMsg.lowercase() -> "连接超时 / 无法访问"
                            else -> "连通失败"
                        }
                    val detail =
                        when {
                            "401" in rawMsg || "unauthorized" in rawMsg.lowercase() -> "API Key 无效、已过期或无权访问该模型，请检查密钥"
                            "403" in rawMsg || "forbidden" in rawMsg.lowercase() -> {
                                if ("groq" in normalized.lowercase()) {
                                    "Groq 不向中国大陆及香港地区提供服务，可改用 DeepSeek、智谱 GLM、阿里百炼等国内服务商。"
                                } else {
                                    "端点拒绝访问（HTTP 403），请检查账号权限或 IP 地域限制"
                                }
                            }
                            "timeout" in rawMsg.lowercase() || "connect" in rawMsg.lowercase() -> "请检查网络连接与接口地址是否正确"
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
                    Icon(imageVector = BgmIcons.Close, contentDescription = "关闭")
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
                                            imageVector = BgmIcons.Check,
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
                        Icon(BgmIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
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
                                    imageVector = BgmIcons.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("克隆为新方案")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(
                                    imageVector = BgmIcons.Delete,
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
                                Icon(BgmIcons.Clear, contentDescription = "清空端点")
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
                                Icon(BgmIcons.ContentPaste, contentDescription = "粘贴剪贴板内容")
                            }
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) BgmIcons.VisibilityOff else BgmIcons.Visibility,
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
                                        imageVector = BgmIcons.ErrorOutline,
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
                                    imageVector = BgmIcons.CheckCircle,
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
                                    imageVector = BgmIcons.ErrorOutline,
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
                        Icon(imageVector = BgmIcons.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
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
