package com.infinitezerone.minibgm.feature.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    uiState: AgentChatUiState,
    onBackClick: () -> Unit,
    onSendMessage: (String) -> Unit,
    onInputChange: (String) -> Unit,
    onConfigChange: (baseUrl: String, apiKey: String, model: String) -> Unit,
    onClearSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Agent 助手") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onClearSession) { Text("清空") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
        ) {
            // 模型配置区（内存驻留，不持久化）
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("模型配置（仅本次会话内存保留）", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.baseUrl,
                        onValueChange = { onConfigChange(it, uiState.apiKey, uiState.model) },
                        label = { Text("Base URL") },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = uiState.model,
                        onValueChange = { onConfigChange(uiState.baseUrl, uiState.apiKey, it) },
                        label = { Text("Model") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = uiState.apiKey,
                    onValueChange = { onConfigChange(uiState.baseUrl, it, uiState.model) },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = uiState.bubbles, key = { it.hashCode() }) { bubble ->
                    val alignment = if (bubble.role == AgentBubbleRole.USER) Alignment.End else Alignment.Start
                    val bubbleColor =
                        when (bubble.role) {
                            AgentBubbleRole.USER -> MaterialTheme.colorScheme.primaryContainer
                            AgentBubbleRole.AGENT -> MaterialTheme.colorScheme.surfaceVariant
                            AgentBubbleRole.SYSTEM -> MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
                        Text(
                            text = bubble.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier =
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .background(bubbleColor, MaterialTheme.shapes.medium)
                                    .padding(12.dp),
                        )
                    }
                }
                if (uiState.isThinking) {
                    item(key = "thinking") {
                        Text(
                            "思考中…",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (uiState.isConfigured) "问点什么，例如「今天有什么更新」" else "先填写模型配置") },
                )
                TextButton(
                    onClick = { onSendMessage(uiState.input) },
                    enabled = !uiState.isThinking && uiState.input.isNotBlank(),
                ) { Text("发送") }
            }
        }
    }
}
