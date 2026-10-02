package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.RuleParserType

/**
 * 变量支持提示卡片
 */
@Composable
internal fun RuleVariablesHintCard() {
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
                imageVector = BgmIcons.Info,
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
internal fun PlaybackRuleCard(
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = rule.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            shape = BgmShapes.small,
                            color =
                                if (rule.kind == PlaybackRuleKind.SOURCE) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                        ) {
                            Text(
                                text = if (rule.kind == PlaybackRuleKind.SOURCE) "接口·" + parserTypeLabel(rule.parserType) else "页面",
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (rule.kind == PlaybackRuleKind.SOURCE) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (rule.description.isNotBlank()) {
                        Text(
                            text = rule.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onEdit) {
                    Icon(
                        imageVector = BgmIcons.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDelete) {
                    Icon(
                        imageVector = BgmIcons.Delete,
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
 * 规则添加/编辑对话框
 */
@Composable
internal fun RuleEditDialog(
    initialRule: PlaybackSourceRule?,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        url: String,
        desc: String,
        kind: PlaybackRuleKind,
        parserType: RuleParserType,
        headersText: String,
    ) -> Unit,
) {
    var name by remember(initialRule) { mutableStateOf(initialRule?.name ?: "") }
    var urlTemplate by remember(initialRule) { mutableStateOf(initialRule?.urlTemplate ?: "") }
    var description by remember(initialRule) { mutableStateOf(initialRule?.description ?: "") }
    var kind by remember(initialRule) { mutableStateOf(initialRule?.kind ?: PlaybackRuleKind.PAGE) }
    var parserType by remember(initialRule) { mutableStateOf(initialRule?.parserType ?: RuleParserType.AUTO) }
    var headersText by remember(initialRule) {
        mutableStateOf(
            initialRule
                ?.headers
                .orEmpty()
                .entries
                .joinToString("\n") { "${it.key}: ${it.value}" },
        )
    }

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
                    placeholder = { Text("例如：我的采集源") },
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
                    listOf("{title}", "{ep}", "{subjectId}", "{episodeId}").forEach { placeholder ->
                        FilterChip(
                            selected = false,
                            onClick = { urlTemplate += placeholder },
                            label = { Text(placeholder, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                // 规则用途：跳转页面只是给用户一个入口；取源接口会真的发请求并抽取可播放地址
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PlaybackRuleKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = {
                                Text(
                                    text = if (option == PlaybackRuleKind.SOURCE) "取源接口" else "跳转页面",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }

                if (kind == PlaybackRuleKind.SOURCE) {
                    // 解析器决定播放时走哪条抽取路径。选错不会报错，只会静默降级成页面嗅探，
                    // 所以这里让人显式选，而不是一律 AUTO（「探测」入口不走这条，它永落 MACCMS）。
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "解析器",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RuleParserType.entries.forEach { option ->
                                FilterChip(
                                    selected = parserType == option,
                                    onClick = { parserType = option },
                                    label = {
                                        Text(
                                            text = parserTypeLabel(option),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = headersText,
                        onValueChange = { headersText = it },
                        label = { Text("请求头（可选，每行 Key: Value）") },
                        placeholder = { Text("Referer: https://example.com/") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                onClick = { onConfirm(name, urlTemplate, description, kind, parserType, headersText) },
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
internal fun RuleImportDialog(
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

/**
 * 站点探测对话框：贴域名 → 试标准 MacCMS 采集接口 → 命中即落成取源规则。
 */
@Composable
internal fun SiteProbeDialog(
    state: SiteProbeUiState,
    onInputChanged: (String) -> Unit,
    onProbe: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("探测站点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "填入站点域名，会试它是否符合标准采集接口（/api.php/provide/vod/）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChanged,
                    singleLine = true,
                    enabled = !state.isProbing,
                    placeholder = {
                        Text("example.com", style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.isProbing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在探测…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                state.result?.let { result ->
                    Surface(
                        shape = BgmShapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Text(
                                text = "✓ 识别为 MacCMS 采集接口",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "接口当前返回 ${result.sampleCount} 条内容，规则将以「取源接口」形态添加",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = result.ruleTemplate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                            )
                        }
                    }
                }

                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (state.result != null) {
                Button(onClick = onConfirm) {
                    Text("添加规则")
                }
            } else {
                Button(
                    onClick = onProbe,
                    enabled = !state.isProbing && state.input.isNotBlank(),
                ) {
                    Text("探测")
                }
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
 * 订阅导入对话框：贴订阅 URL → 检测出报告 → 按报告勾选 → 导入。
 */
@Composable
internal fun SubscriptionImportDialog(
    state: SubscriptionImportUiState,
    onInputChanged: (String) -> Unit,
    onValidate: () -> Unit,
    onToggleSource: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val report = state.report
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("订阅导入") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "填入 TVBox / MiniBgm 订阅地址，检测通过后按报告勾选要导入的来源。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChanged,
                    singleLine = true,
                    enabled = !state.isValidating,
                    placeholder = {
                        Text("https://example.com/tvbox.json", style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.isValidating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在检测…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                report?.let { result ->
                    Surface(
                        shape = BgmShapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Text(
                                text = "✓ 检测通过：有效连通 ${result.aliveRules}/${result.totalRules}，平均延迟 ${result.averageLatencyMs}ms",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            result.sources.forEachIndexed { index, source ->
                                SubscriptionSourceRow(
                                    index = index,
                                    name = source.name,
                                    latencyMs = source.latencyMs,
                                    isAlive = source.isAlive,
                                    isSelected = index in state.selected,
                                    onToggle = { onToggleSource(index) },
                                )
                            }
                        }
                    }
                }

                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (report != null) {
                Button(
                    onClick = onConfirm,
                    enabled = state.selected.isNotEmpty(),
                ) {
                    Text("导入所选（${state.selected.size}）")
                }
            } else {
                Button(
                    onClick = onValidate,
                    enabled = !state.isValidating && state.input.isNotBlank(),
                ) {
                    Text("检测")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/** 订阅报告里的单条来源：勾选导入与否；未探活通过的默认不勾，但仍可手动选 */
@Composable
internal fun SubscriptionSourceRow(
    index: Int,
    name: String,
    latencyMs: Long,
    isAlive: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    buildString {
                        append(if (isAlive) "✓ 连通" else "✗ 未连通")
                        if (latencyMs > 0L) append(" · ${latencyMs}ms")
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

/** 续播记录单条：展示主机名与上次观看位置，可单条清除 */
@Composable
internal fun PlaybackPositionRow(
    url: String,
    positionMs: Long,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hostLabelOf(url),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "看到 " + formatPlaybackPosition(positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = BgmIcons.CloseBorder,
                    contentDescription = "清除该续播记录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 解析器选项的短标签：四个 chip 要排在一行，长名字排不下 */
internal fun parserTypeLabel(type: RuleParserType): String =
    when (type) {
        RuleParserType.AUTO -> "自动"
        RuleParserType.MACCMS -> "MacCMS"
        RuleParserType.STREMIO -> "Stremio"
        RuleParserType.PIPELINE -> "流水线"
    }

internal fun hostLabelOf(url: String): String = url.substringAfter("://", url).substringBefore('/').substringBefore('?')

internal fun formatPlaybackPosition(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val sec = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
