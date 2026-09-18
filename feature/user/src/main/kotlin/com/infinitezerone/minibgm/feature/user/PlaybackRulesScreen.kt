package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import org.koin.androidx.compose.koinViewModel

/**
 * 自定义播放规则管理界面：
 * 支持查看、新增、编辑、启用/禁用、删除及 JSON 批量导入自定义播放源规则。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackRulesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaybackRulesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var ruleToEdit by remember { mutableStateOf<PlaybackSourceRule?>(null) }
    var isAddingRule by remember { mutableStateOf(false) }
    var isImportingJson by remember { mutableStateOf(false) }
    var ruleToDelete by remember { mutableStateOf<PlaybackSourceRule?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaybackRulesUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = { Text(text = "播放规则管理") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isImportingJson = true }) {
                        Icon(
                            imageVector = Icons.Filled.FileDownload,
                            contentDescription = "导入规则",
                        )
                    }
                    IconButton(onClick = { isAddingRule = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加规则",
                        )
                    }
                },
            )
        },
        snackbarHost = { BgmSnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.rules.isEmpty()) {
            PlaybackRulesEmptyView(
                onAddClick = { isAddingRule = true },
                onImportClick = { isImportingJson = true },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "template_hint") {
                    RuleVariablesHintCard()
                }

                items(uiState.rules, key = { it.id }) { rule ->
                    PlaybackRuleCard(
                        rule = rule,
                        onToggle = { enabled -> viewModel.toggleRule(rule.id, enabled) },
                        onEdit = { ruleToEdit = rule },
                        onDelete = { ruleToDelete = rule },
                    )
                }
            }
        }
    }

    // 新增/编辑对话框
    if (isAddingRule || ruleToEdit != null) {
        val target = ruleToEdit
        RuleEditDialog(
            initialRule = target,
            onDismiss = {
                isAddingRule = false
                ruleToEdit = null
            },
            onConfirm = { name, url, desc ->
                if (target != null) {
                    viewModel.updateRule(target.id, name, url, desc)
                } else {
                    viewModel.addRule(name, url, desc)
                }
                isAddingRule = false
                ruleToEdit = null
            },
        )
    }

    // 导入 JSON 对话框
    if (isImportingJson) {
        RuleImportDialog(
            onDismiss = { isImportingJson = false },
            onConfirm = { json ->
                viewModel.importRulesFromJson(json)
                isImportingJson = false
            },
        )
    }

    // 删除确认对话框
    ruleToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("确认删除规则") },
            text = { Text("确定要删除播放规则「${target.name}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRule(target.id)
                        ruleToDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * 变量支持提示卡片
 */
@Composable
private fun RuleVariablesHintCard() {
    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "支持的占位符变量",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "{title}: 番剧名称\n{ep}: 分集编号（如 1, 2）\n{subjectId}: 条目 ID\n{episodeId}: 分集 ID",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 单条播放规则卡片
 */
@Composable
private fun PlaybackRuleCard(
    rule: PlaybackSourceRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (rule.description.isNotBlank()) {
                        Text(
                            text = rule.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle,
                )
            }

            Text(
                text = rule.urlTemplate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑")
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * 规则为空时的占位
 */
@Composable
private fun PlaybackRulesEmptyView(
    onAddClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = "暂无自定义播放规则",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "配置规则后，在分集播放向导中可直接唤起目标播放地址",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAddClick) {
                    Text("添加规则")
                }
                TextButton(onClick = onImportClick) {
                    Text("导入 JSON")
                }
            }
        }
    }
}

/**
 * 规则添加/编辑对话框
 */
@Composable
private fun RuleEditDialog(
    initialRule: PlaybackSourceRule?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, desc: String) -> Unit,
) {
    var name by remember(initialRule) { mutableStateOf(initialRule?.name ?: "") }
    var urlTemplate by remember(initialRule) { mutableStateOf(initialRule?.urlTemplate ?: "") }
    var description by remember(initialRule) { mutableStateOf(initialRule?.description ?: "") }

    val isEditing = initialRule != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "编辑播放规则" else "添加播放规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称") },
                    placeholder = { Text("例如：AGE动漫") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = urlTemplate,
                    onValueChange = { urlTemplate = it },
                    label = { Text("URL 模板") },
                    placeholder = { Text("https://example.com/search?q={title}") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 快捷插入变量 Chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("{title}", "{ep}", "{subjectId}").forEach { placeholder ->
                        FilterChip(
                            selected = false,
                            onClick = { urlTemplate += placeholder },
                            label = { Text(placeholder, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("备注说明（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, urlTemplate, description) },
                enabled = name.isNotBlank() && urlTemplate.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * 规则 JSON 批量导入对话框
 */
@Composable
private fun RuleImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var jsonText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入规则 (JSON)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "粘贴单条或数组格式的规则 JSON 配置：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    placeholder = {
                        Text(
                            """[{"id":"rule-1","name":"源名","urlTemplate":"https://...{title}"}]""",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    minLines = 5,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(jsonText) },
                enabled = jsonText.isNotBlank(),
            ) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
