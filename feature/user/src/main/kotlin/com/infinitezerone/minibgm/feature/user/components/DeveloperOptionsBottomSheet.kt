package com.infinitezerone.minibgm.feature.user.components

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmGhostIconButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.DeveloperSettings
import com.infinitezerone.minibgm.feature.user.R
import kotlinx.coroutines.launch
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

private val MIRROR_PRESETS =
    listOf(
        "直连 (默认)" to "",
        "ghproxy.net" to "https://ghproxy.net/",
        "mirror.ghproxy.com" to "https://mirror.ghproxy.com/",
        "moeyy.xyz" to "https://github.moeyy.xyz/",
    )

private fun normalizeMirrorPrefix(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""
    val withProtocol =
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }
    return if (withProtocol.endsWith("/")) withProtocol else "$withProtocol/"
}

/**
 * 现代应用内开发者调试抽屉（In-App Developer Panel / Debug Drawer）。
 *
 * 提供真机动态调试入口，支持详细日志排查、GitHub 下载加速前缀配置与系统诊断信息导出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsBottomSheet(
    developerSettings: DeveloperSettings,
    onToggleVerboseLogging: (Boolean) -> Unit,
    onCustomMirrorPrefixChange: (String) -> Unit,
    onResetAllOverrides: () -> Unit,
    onDisableDeveloperMode: () -> Unit,
    onOpenCrashLog: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onShowToast: (String) -> Unit = {},
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var customMirrorInput by remember(developerSettings.customMirrorPrefixOverride) {
        mutableStateOf(developerSettings.customMirrorPrefixOverride)
    }

    val applyNormalizedPrefix = { raw: String ->
        val normalized = normalizeMirrorPrefix(raw)
        customMirrorInput = normalized
        onCustomMirrorPrefixChange(normalized)
    }

    val appDiagnosticsText =
        remember {
            val pInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            buildString {
                appendLine("App: ${context.packageName}")
                appendLine("VersionName: ${pInfo?.versionName.orEmpty()}")
                @Suppress("DEPRECATION")
                appendLine("VersionCode: ${pInfo?.versionCode ?: 0}")
                val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                appendLine("BuildType: ${if (isDebug) "Debug" else "Release"}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
                appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            }
        }

    BgmModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 顶部标题与快速重置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(R.string.feature_user_dev_options_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = onResetAllOverrides) {
                        Text(
                            text = stringResource(R.string.feature_user_dev_action_reset_overrides),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    BgmGhostIconButton(
                        onClick = onDismiss,
                        icon = BgmIcons.Close,
                        contentDescription = stringResource(DesignSystemR.string.core_designsystem_action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 分区 1：GitHub 镜像与下载加速
            DevCategoryCard(title = stringResource(R.string.feature_user_dev_category_network)) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.feature_user_dev_custom_mirror_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MIRROR_PRESETS.forEach { (label, presetUrl) ->
                            val isSelected =
                                if (presetUrl.isEmpty()) {
                                    customMirrorInput.isEmpty()
                                } else {
                                    customMirrorInput.trimEnd('/') == presetUrl.trimEnd('/')
                                }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    applyNormalizedPrefix(presetUrl)
                                    focusManager.clearFocus()
                                },
                                label = { Text(label) },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customMirrorInput,
                        onValueChange = {
                            customMirrorInput = it
                            onCustomMirrorPrefixChange(it.trim())
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.feature_user_dev_custom_mirror_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingIcon = {
                            if (customMirrorInput.isNotEmpty()) {
                                BgmGhostIconButton(
                                    onClick = {
                                        applyNormalizedPrefix("")
                                        focusManager.clearFocus()
                                    },
                                    icon = BgmIcons.Clear,
                                    contentDescription = null,
                                )
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    applyNormalizedPrefix(customMirrorInput)
                                    focusManager.clearFocus()
                                },
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 分区 2：运行日志与排查
            DevCategoryCard(title = stringResource(R.string.feature_user_dev_category_logging)) {
                // 详细调试日志开关
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggleVerboseLogging(!developerSettings.verboseLoggingEnabled) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.feature_user_dev_verbose_logging_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.feature_user_dev_verbose_logging_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = developerSettings.verboseLoggingEnabled,
                        onCheckedChange = onToggleVerboseLogging,
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                // 查看最近崩溃日志
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenCrashLog)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.feature_user_dev_crash_log_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.feature_user_dev_crash_log_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = BgmIcons.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // 分区 3：系统与诊断
            DevCategoryCard(title = stringResource(R.string.feature_user_dev_category_diagnostics)) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.feature_user_dev_diag_app_info),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    val clip = ClipEntry(ClipData.newPlainText("diagnostics", appDiagnosticsText))
                                    clipboard.setClipEntry(clip)
                                    onShowToast(context.getString(R.string.feature_user_dev_diag_copied))
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.feature_user_action_copy),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(10.dp),
                    ) {
                        Text(
                            text = appDiagnosticsText.trim(),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 关闭开发者选项按钮
            OutlinedButton(
                onClick = {
                    onDisableDeveloperMode()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(text = stringResource(R.string.feature_user_dev_action_close_dev_mode))
            }
        }
    }
}

@Composable
private fun DevCategoryCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
            content()
        }
    }
}
