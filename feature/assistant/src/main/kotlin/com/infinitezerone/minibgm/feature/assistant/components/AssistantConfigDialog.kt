package com.infinitezerone.minibgm.feature.assistant.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.AiConfig
import kotlinx.coroutines.launch

/** 根据输入的 Base URL 自动解析并匹配协议提供商 */
internal fun autoDetectProvider(endpoint: String): String {
    val lowered = endpoint.trim().lowercase()
    return when {
        "generativelanguage.googleapis.com" in lowered || "gemini" in lowered -> AiConfig.PROVIDER_GEMINI
        "11434" in lowered || "ollama" in lowered -> AiConfig.PROVIDER_OLLAMA
        else -> AiConfig.PROVIDER_CUSTOM
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantConfigDialog(
    currentConfig: AiConfig,
    onSaveConfig: (AiConfig) -> Unit,
    onDismiss: () -> Unit,
    onFetchModels: suspend (endpoint: String, apiKey: String, provider: String) -> AppResult<List<String>>,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var endpoint by remember {
        mutableStateOf(currentConfig.endpoint.ifBlank { "https://generativelanguage.googleapis.com/v1beta/openai/" })
    }
    var selectedProvider by remember {
        mutableStateOf(currentConfig.provider.ifBlank { autoDetectProvider(endpoint) })
    }
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var model by remember {
        mutableStateOf(currentConfig.model.ifBlank { defaultModelFor(selectedProvider) })
    }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var availableModels by remember { mutableStateOf<List<String>?>(null) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    val dialogScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 智能体配置", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text("远程服务")
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text("本地模型")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = {
                            endpoint = it
                            selectedProvider = autoDetectProvider(it)
                        },
                        label = { Text("服务地址 (Base URL)") },
                        placeholder = { Text("输入端点地址") },
                        supportingText = {
                            Text(
                                text = "协议：${providerDisplayName(selectedProvider)}",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("自建服务可留空") },
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (isApiKeyVisible) "隐藏" else "显示",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("模型名称") },
                        placeholder = { Text(defaultModelFor(selectedProvider)) },
                        supportingText = {
                            modelsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        },
                        singleLine = true,
                        trailingIcon = {
                            if (isFetchingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    TextButton(
                        onClick = {
                            dialogScope.launch {
                                isFetchingModels = true
                                modelsError = null
                                when (val result = onFetchModels(endpoint.trim(), apiKey.trim(), selectedProvider)) {
                                    is AppResult.Success -> {
                                        availableModels = result.data
                                        modelsError = null
                                    }
                                    is AppResult.Error -> {
                                        availableModels = null
                                        modelsError = result.message.ifBlank { "拉取失败" }
                                    }
                                    AppResult.Loading -> Unit
                                }
                                isFetchingModels = false
                            }
                        },
                        enabled = endpoint.isNotBlank() && !isFetchingModels,
                    ) {
                        Text(if (availableModels == null) "获取可用模型" else "刷新模型列表")
                    }

                    availableModels?.takeIf { it.isNotEmpty() }?.let { models ->
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            models.forEach { candidate ->
                                FilterChip(
                                    selected = model == candidate,
                                    onClick = { model = candidate },
                                    label = {
                                        Text(
                                            text = candidate,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                        )
                                    },
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "规划研发中",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveConfig(
                        AiConfig(
                            endpoint = endpoint.trim().ifBlank { "https://generativelanguage.googleapis.com/v1beta/openai/" },
                            apiKey = apiKey.trim(),
                            model = model.trim().ifBlank { defaultModelFor(selectedProvider) },
                            provider = selectedProvider,
                        ),
                    )
                },
            ) {
                Text("保存配置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
