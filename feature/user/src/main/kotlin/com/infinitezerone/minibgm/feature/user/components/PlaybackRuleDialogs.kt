package com.infinitezerone.minibgm.feature.user.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmGhostIconButton
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.RuleParserType
import com.infinitezerone.minibgm.feature.user.R
import com.infinitezerone.minibgm.feature.user.SiteProbeUiState
import com.infinitezerone.minibgm.feature.user.SubscriptionImportUiState
import kotlinx.coroutines.launch
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/**
 * 单条播放规则卡片
 */
@Composable
internal fun PlaybackRuleCard(
    rule: PlaybackSourceRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onCopyUrl: ((String) -> Unit)? = null,
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
            val iconContainerColor =
                if (rule.isEnabled) {
                    when (rule.kind) {
                        PlaybackRuleKind.SOURCE -> MaterialTheme.colorScheme.primaryContainer
                        PlaybackRuleKind.TORRENT -> MaterialTheme.colorScheme.tertiaryContainer
                        PlaybackRuleKind.PAGE -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }

            val iconTint =
                if (rule.isEnabled) {
                    when (rule.kind) {
                        PlaybackRuleKind.SOURCE -> MaterialTheme.colorScheme.onPrimaryContainer
                        PlaybackRuleKind.TORRENT -> MaterialTheme.colorScheme.onTertiaryContainer
                        PlaybackRuleKind.PAGE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }

            val ruleIcon =
                when (rule.kind) {
                    PlaybackRuleKind.SOURCE -> BgmIcons.PlayCircle
                    PlaybackRuleKind.TORRENT -> BgmIcons.Download
                    PlaybackRuleKind.PAGE -> BgmIcons.OpenInBrowser
                }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = BgmShapes.small,
                    color = iconContainerColor,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = ruleIcon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color =
                            if (rule.isEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Surface(
                            shape = BgmShapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = parserTypeLabel(rule.parserType),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        if (rule.headers.isNotEmpty()) {
                            Surface(
                                shape = BgmShapes.extraSmall,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = "${rule.headers.size} Headers",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle,
                )
            }

            if (rule.description.isNotBlank()) {
                Text(
                    text = rule.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape = BgmShapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = rule.urlTemplate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (onCopyUrl != null) {
                        BgmGhostIconButton(
                            onClick = { onCopyUrl(rule.urlTemplate) },
                            icon = BgmIcons.ContentCopy,
                            contentDescription = stringResource(R.string.feature_user_rule_copy_url_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconSize = 14.dp,
                        )
                    }
                    BgmGhostIconButton(
                        onClick = onDelete,
                        icon = BgmIcons.Delete,
                        contentDescription = stringResource(R.string.feature_user_action_delete),
                        tint = MaterialTheme.colorScheme.error,
                        iconSize = 14.dp,
                    )
                }
            }
        }
    }
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
        title = { Text(stringResource(R.string.feature_user_rule_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.feature_user_rule_import_desc),
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
                Text(stringResource(R.string.feature_user_action_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(DesignSystemR.string.core_designsystem_action_cancel))
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
        title = { Text(stringResource(R.string.feature_user_probe_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.feature_user_probe_desc),
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
                        Text(stringResource(R.string.feature_user_probe_probing), style = MaterialTheme.typography.bodySmall)
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
                                text = stringResource(R.string.feature_user_probe_recognized),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = stringResource(R.string.feature_user_probe_result_desc, result.sampleCount),
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
                    Text(stringResource(R.string.feature_user_probe_add_action))
                }
            } else {
                Button(
                    onClick = onProbe,
                    enabled = !state.isProbing && state.input.isNotBlank(),
                ) {
                    Text(stringResource(R.string.feature_user_probe_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(DesignSystemR.string.core_designsystem_action_cancel))
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
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_user_subscription_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.feature_user_subscription_desc),
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
                    trailingIcon = {
                        if (state.input.isNotBlank()) {
                            BgmGhostIconButton(
                                onClick = { onInputChanged("") },
                                icon = BgmIcons.Close,
                                contentDescription = stringResource(R.string.feature_user_action_clear),
                                enabled = !state.isValidating,
                                iconSize = 20.dp,
                            )
                        } else {
                            BgmGhostIconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        runCatching {
                                            val clipEntry = clipboard.getClipEntry()
                                            val clipText =
                                                clipEntry
                                                    ?.clipData
                                                    ?.getItemAt(0)
                                                    ?.text
                                                    ?.toString()
                                                    .orEmpty()
                                                    .trim()
                                            if (clipText.isNotBlank()) {
                                                onInputChanged(clipText)
                                            }
                                        }
                                    }
                                },
                                icon = BgmIcons.ContentPaste,
                                contentDescription = stringResource(R.string.feature_user_action_paste),
                                enabled = !state.isValidating,
                                iconSize = 20.dp,
                            )
                        }
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
                        Text(stringResource(R.string.feature_user_subscription_checking), style = MaterialTheme.typography.bodySmall)
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
                                text =
                                    stringResource(
                                        R.string.feature_user_subscription_report,
                                        result.aliveRules,
                                        result.totalRules,
                                        result.averageLatencyMs,
                                    ),
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
                    Text(stringResource(R.string.feature_user_subscription_import_selected, state.selected.size))
                }
            } else {
                Button(
                    onClick = onValidate,
                    enabled = !state.isValidating && state.input.isNotBlank(),
                ) {
                    Text(stringResource(R.string.feature_user_subscription_check_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(DesignSystemR.string.core_designsystem_action_cancel))
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
        val aliveLabel =
            stringResource(
                if (isAlive) R.string.feature_user_subscription_alive else R.string.feature_user_subscription_dead,
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
                        append(aliveLabel)
                        if (latencyMs > 0L) append(" · ${latencyMs}ms")
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

/** 解析器选项的短标签：四个 chip 要排在一行，长名字排不下 */
@Composable
internal fun parserTypeLabel(type: RuleParserType): String =
    when (type) {
        RuleParserType.AUTO -> stringResource(R.string.feature_user_parser_auto)
        RuleParserType.MACCMS -> "MacCMS"
        RuleParserType.STREMIO -> "Stremio"
        RuleParserType.PIPELINE -> stringResource(R.string.feature_user_parser_pipeline)
        RuleParserType.RSS_TORRENT -> stringResource(R.string.feature_user_parser_rss_torrent)
        RuleParserType.SPIDER -> "Spider"
    }

/**
 * 添加种子/磁力 RSS 规则对话框
 */
@Composable
internal fun AddTorrentRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, urlTemplate: String, description: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var urlTemplate by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_user_rule_add_torrent_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.feature_user_rule_add_torrent_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.feature_user_rule_name_label)) },
                    placeholder = { Text("例如：动漫字幕组 RSS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = urlTemplate,
                    onValueChange = { urlTemplate = it },
                    label = { Text(stringResource(R.string.feature_user_rule_url_label)) },
                    placeholder = { Text("https://example.com/rss?q={title}") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.feature_user_rule_desc_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), urlTemplate.trim(), description.trim()) },
                enabled = name.isNotBlank() && urlTemplate.isNotBlank(),
            ) {
                Text(stringResource(R.string.feature_user_probe_add_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(DesignSystemR.string.core_designsystem_action_cancel))
            }
        },
    )
}

/** 把毫秒时长格式化为 `mm:ss`（超过一小时给 `h:mm:ss`），供观看记录进度文案使用 */
internal fun formatPlaybackPosition(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val sec = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
