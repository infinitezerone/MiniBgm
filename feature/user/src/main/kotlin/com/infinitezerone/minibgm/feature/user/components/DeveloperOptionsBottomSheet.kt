package com.infinitezerone.minibgm.feature.user.components

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.IconButton
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
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.DeveloperSettings
import com.infinitezerone.minibgm.feature.user.R
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR
import kotlinx.coroutines.launch

/**
 * 现代应用内开发者调试抽屉（In-App Developer Panel / Debug Drawer）。
 *
 * 提供真机动态调试入口，支持特性开关覆写、原生组件状态模拟、网络加速前缀配置与系统诊断信息导出，
 * 避免每次测试改动都需要重新打 Gradle 包。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsBottomSheet(
    developerSettings: DeveloperSettings,
    onOverrideAi: (Boolean?) -> Unit,
    onOverridePlayer: (Boolean?) -> Unit,
    onToggleVerboseLogging: (Boolean) -> Unit,
    isSimulatingTorrentUninstalled: Boolean,
    isTorrentPluginInstalled: Boolean,
    onToggleSimulateTorrentUninstalled: (Boolean) -> Unit,
    onResetTorrentPlugin: () -> Unit,
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = BgmIcons.Close,
                            contentDescription = stringResource(DesignSystemR.string.core_designsystem_action_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 分区 1：特性开关覆写 (Feature Toggles)
            DevCategoryCard(title = stringResource(R.string.feature_user_dev_category_feature_toggles)) {
                // AI 追番助手覆写
                ThreeStateToggleRow(
                    title = stringResource(R.string.feature_user_dev_override_ai_title),
                    subtitle = stringResource(R.string.feature_user_dev_override_ai_desc),
                    currentValue = developerSettings.enableAiOverride,
                    onSelect = onOverrideAi,
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                // 内置播放器覆写
                ThreeStateToggleRow(
                    title = stringResource(R.string.feature_user_dev_override_player_title),
                    subtitle = stringResource(R.string.feature_user_dev_override_player_desc),
                    currentValue = developerSettings.enablePlayerOverride,
                    onSelect = onOverridePlayer,
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                // 详细调试日志开关
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggleVerboseLogging(!developerSettings.verboseLoggingEnabled) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
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
            }

            // 分区 2：原生组件与离线流化引擎
            DevCategoryCard(title = stringResource(R.string.feature_user_dev_category_torrent)) {
                val simulateSubtitle =
                    when {
                        !isSimulatingTorrentUninstalled ->
                            stringResource(R.string.feature_user_dev_simulate_torrent_uninstalled_off)
                        isTorrentPluginInstalled ->
                            stringResource(R.string.feature_user_dev_simulate_torrent_uninstalled_on_ready)
                        else ->
                            stringResource(R.string.feature_user_dev_simulate_torrent_uninstalled_on_empty)
                    }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggleSimulateTorrentUninstalled(!isSimulatingTorrentUninstalled) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.feature_user_dev_simulate_torrent_uninstalled_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = simulateSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isSimulatingTorrentUninstalled,
                        onCheckedChange = onToggleSimulateTorrentUninstalled,
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onResetTorrentPlugin)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.feature_user_dev_reset_torrent_plugin_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.feature_user_dev_reset_torrent_plugin_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = BgmIcons.CleaningServices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // 分区 3：网络与镜像前缀
            DevCategoryCard(title = stringResource(R.string.feature_user_dev_category_network)) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.feature_user_dev_custom_mirror_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.feature_user_dev_custom_mirror_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = customMirrorInput,
                        onValueChange = {
                            customMirrorInput = it
                            onCustomMirrorPrefixChange(it)
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.feature_user_dev_custom_mirror_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingIcon = {
                            if (customMirrorInput.isNotEmpty()) {
                                IconButton(onClick = {
                                    customMirrorInput = ""
                                    onCustomMirrorPrefixChange("")
                                }) {
                                    Icon(imageVector = BgmIcons.Clear, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 分区 4：系统诊断与崩溃排查
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
                    Text(
                        text = stringResource(R.string.feature_user_dev_crash_log_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = BgmIcons.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
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

@Composable
private fun ThreeStateToggleRow(
    title: String,
    subtitle: String,
    currentValue: Boolean?,
    onSelect: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = currentValue == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.feature_user_dev_override_default)) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
            FilterChip(
                selected = currentValue == true,
                onClick = { onSelect(true) },
                label = { Text(stringResource(R.string.feature_user_dev_override_enabled)) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
            FilterChip(
                selected = currentValue == false,
                onClick = { onSelect(false) },
                label = { Text(stringResource(R.string.feature_user_dev_override_disabled)) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
            )
        }
    }
}
