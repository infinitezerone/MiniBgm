package com.infinitezerone.minibgm.feature.user.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmGhostIconButton
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
import com.infinitezerone.minibgm.core.model.DanmakuConfig
import com.infinitezerone.minibgm.feature.user.R
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/**
 * 弹幕服务配置对话框：
 * - 支持自定义 API 服务端点（用于直连官方或自建 Cloudflare Worker 代理）；
 * - 支持配置官方开放平台的 AppId 与 AppSecret（密文显示与显隐切换）；
 * - 提供一键恢复默认端点操作。
 */
@Composable
internal fun DanmakuSettingsDialog(
    currentConfig: DanmakuConfig,
    onSaveConfig: (DanmakuConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var endpoint by remember { mutableStateOf(currentConfig.endpoint) }
    var appId by remember { mutableStateOf(currentConfig.appId) }
    var appSecret by remember { mutableStateOf(currentConfig.appSecret) }
    var isSecretVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.feature_user_danmaku_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.feature_user_danmaku_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text(stringResource(R.string.feature_user_danmaku_endpoint_label)) },
                    placeholder = { Text(DanmakuConfig.DEFAULT_ENDPOINT) },
                    supportingText = {
                        Text(
                            text = stringResource(R.string.feature_user_danmaku_endpoint_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = appId,
                    onValueChange = { appId = it },
                    label = { Text(stringResource(R.string.feature_user_danmaku_appid_label)) },
                    placeholder = { Text(stringResource(R.string.feature_user_danmaku_appid_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = appSecret,
                    onValueChange = { appSecret = it },
                    label = { Text(stringResource(R.string.feature_user_danmaku_appsecret_label)) },
                    placeholder = { Text(stringResource(R.string.feature_user_danmaku_appsecret_placeholder)) },
                    singleLine = true,
                    visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        BgmGhostIconButton(
                            onClick = { isSecretVisible = !isSecretVisible },
                            icon = if (isSecretVisible) BgmIcons.VisibilityOff else BgmIcons.Visibility,
                            contentDescription =
                                if (isSecretVisible) {
                                    stringResource(R.string.feature_user_danmaku_appsecret_hide)
                                } else {
                                    stringResource(R.string.feature_user_danmaku_appsecret_show)
                                },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            endpoint = ""
                        },
                    ) {
                        Text(stringResource(R.string.feature_user_danmaku_reset_default))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveConfig(
                        DanmakuConfig(
                            endpoint = endpoint.trim(),
                            appId = appId.trim(),
                            appSecret = appSecret.trim(),
                        ),
                    )
                    onDismiss()
                },
            ) {
                Text(stringResource(DesignSystemR.string.core_designsystem_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(DesignSystemR.string.core_designsystem_action_cancel))
            }
        },
    )
}

@ThemePreviews
@Composable
private fun DanmakuSettingsDialogPreview() {
    MiniBgmTheme {
        DanmakuSettingsDialog(
            currentConfig =
                DanmakuConfig(
                    endpoint = "",
                    appId = "demo_app",
                    appSecret = "demo_secret",
                ),
            onSaveConfig = {},
            onDismiss = {},
        )
    }
}
