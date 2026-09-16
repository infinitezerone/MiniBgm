package com.infinitezerone.minibgm.feature.assistant.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.model.AiConfig

private const val DEFAULT_OLLAMA_ENDPOINT = "http://10.0.2.2:11434"
private const val DEFAULT_GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/openai/"
private const val DEFAULT_OPENAI_ENDPOINT = "https://api.openai.com/v1"

private const val DEFAULT_OLLAMA_MODEL = "qwen2.5:7b"
private const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
private const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"

@Composable
fun AssistantConfigDialog(
    currentConfig: AiConfig,
    onSaveConfig: (AiConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedProvider by remember { mutableStateOf(currentConfig.provider.ifBlank { AiConfig.PROVIDER_OLLAMA }) }
    var endpoint by remember {
        mutableStateOf(
            currentConfig.endpoint.ifBlank {
                when (currentConfig.provider) {
                    AiConfig.PROVIDER_GEMINI -> DEFAULT_GEMINI_ENDPOINT
                    AiConfig.PROVIDER_CUSTOM -> DEFAULT_OPENAI_ENDPOINT
                    else -> DEFAULT_OLLAMA_ENDPOINT
                }
            },
        )
    }
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var model by remember {
        mutableStateOf(
            currentConfig.model.ifBlank {
                when (currentConfig.provider) {
                    AiConfig.PROVIDER_GEMINI -> DEFAULT_GEMINI_MODEL
                    AiConfig.PROVIDER_CUSTOM -> DEFAULT_OPENAI_MODEL
                    else -> DEFAULT_OLLAMA_MODEL
                }
            },
        )
    }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    val onSelectProvider = { providerKey: String ->
        if (selectedProvider != providerKey) {
            selectedProvider = providerKey
            if (endpoint.isBlank() ||
                endpoint in listOf(DEFAULT_OLLAMA_ENDPOINT, DEFAULT_GEMINI_ENDPOINT, DEFAULT_OPENAI_ENDPOINT)
            ) {
                endpoint =
                    when (providerKey) {
                        AiConfig.PROVIDER_OLLAMA -> DEFAULT_OLLAMA_ENDPOINT
                        AiConfig.PROVIDER_GEMINI -> DEFAULT_GEMINI_ENDPOINT
                        else -> DEFAULT_OPENAI_ENDPOINT
                    }
            }
            if (model.isBlank() ||
                model in listOf(DEFAULT_OLLAMA_MODEL, DEFAULT_GEMINI_MODEL, DEFAULT_OPENAI_MODEL)
            ) {
                model =
                    when (providerKey) {
                        AiConfig.PROVIDER_OLLAMA -> DEFAULT_OLLAMA_MODEL
                        AiConfig.PROVIDER_GEMINI -> DEFAULT_GEMINI_MODEL
                        else -> DEFAULT_OPENAI_MODEL
                    }
            }
        }
    }

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
                Text(
                    text = "服务提供商",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(4.dp))

                listOf(
                    AiConfig.PROVIDER_OLLAMA to "Ollama (本地私有化)",
                    AiConfig.PROVIDER_GEMINI to "Google Gemini (云端推荐)",
                    AiConfig.PROVIDER_CUSTOM to "OpenAI 兼容端点",
                ).forEach { (providerKey, providerLabel) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProvider(providerKey) }
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (selectedProvider == providerKey),
                            onClick = { onSelectProvider(providerKey) },
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = providerLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Base URL / 服务端点") },
                    placeholder = {
                        Text(
                            when (selectedProvider) {
                                AiConfig.PROVIDER_OLLAMA -> DEFAULT_OLLAMA_ENDPOINT
                                AiConfig.PROVIDER_GEMINI -> DEFAULT_GEMINI_ENDPOINT
                                else -> DEFAULT_OPENAI_ENDPOINT
                            },
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key / 访问密钥") },
                    placeholder = {
                        Text(if (selectedProvider == AiConfig.PROVIDER_OLLAMA) "Ollama 本地可留空" else "输入 API Key")
                    },
                    supportingText = {
                        if (selectedProvider != AiConfig.PROVIDER_OLLAMA && apiKey.isBlank()) {
                            Text("使用云端服务商必须配置 API Key", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (isApiKeyVisible) "隐藏 API Key" else "显示 API Key",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model Name / 模型名称") },
                    placeholder = {
                        Text(
                            when (selectedProvider) {
                                AiConfig.PROVIDER_OLLAMA -> DEFAULT_OLLAMA_MODEL
                                AiConfig.PROVIDER_GEMINI -> DEFAULT_GEMINI_MODEL
                                else -> DEFAULT_OPENAI_MODEL
                            },
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val defaultEndpoint =
                        when (selectedProvider) {
                            AiConfig.PROVIDER_OLLAMA -> DEFAULT_OLLAMA_ENDPOINT
                            AiConfig.PROVIDER_GEMINI -> DEFAULT_GEMINI_ENDPOINT
                            else -> DEFAULT_OPENAI_ENDPOINT
                        }
                    val defaultModel =
                        when (selectedProvider) {
                            AiConfig.PROVIDER_OLLAMA -> DEFAULT_OLLAMA_MODEL
                            AiConfig.PROVIDER_GEMINI -> DEFAULT_GEMINI_MODEL
                            else -> DEFAULT_OPENAI_MODEL
                        }
                    onSaveConfig(
                        AiConfig(
                            endpoint = endpoint.trim().ifBlank { defaultEndpoint },
                            apiKey = apiKey.trim(),
                            model = model.trim().ifBlank { defaultModel },
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
