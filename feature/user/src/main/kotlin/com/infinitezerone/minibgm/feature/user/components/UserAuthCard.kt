package com.infinitezerone.minibgm.feature.user.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons

/** Bangumi 官方访问令牌生成页（next.bgm.tv 子域，属 bgm 系域名，可走应用内浏览）。 */
private const val ACCESS_TOKEN_PAGE_URL = "https://next.bgm.tv/demo/access-token"

/**
 * 全屏未登录引导界面：
 * 当用户处于未登录状态时占据「个人中心」主体。
 *
 * 只保留"是什么 + 一个主操作 + 一个低强调备选"：登录态空态的职责是给出唯一的下一步，
 * 权益清单与卖点属于首次引导/商店页，堆在这里只会稀释唯一动作、并在小屏与大字体下换行。
 */
@Composable
internal fun UnauthenticatedLandingView(
    onLogin: () -> Unit,
    onLoginWithToken: (String, (Boolean, String?) -> Unit) -> Unit = { _, _ -> },
    onOpenTokenPage: (String) -> Unit = {},
    isAuthenticating: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showTokenDialog by rememberSaveable { mutableStateOf(false) }

    if (showTokenDialog) {
        PersonalAccessTokenDialog(
            onDismiss = { showTokenDialog = false },
            onSubmit = onLoginWithToken,
            onOpenTokenPage = onOpenTokenPage,
        )
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 品牌/头像质感徽标
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.size(80.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = BgmIcons.User,
                            contentDescription = "未登录",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }

                Text(
                    text = "登录 Bangumi 账号",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // 大号主操作登录按钮
                Button(
                    onClick = onLogin,
                    enabled = !isAuthenticating,
                    shape = RoundedCornerShape(12.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = "登录",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // 备选入口降到文字级强调：不与主路径争注意力，也不必再用脚注解释它
                TextButton(
                    onClick = { showTokenDialog = true },
                    enabled = !isAuthenticating,
                ) {
                    Text(
                        text = "使用访问令牌登录",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalAccessTokenDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, (Boolean, String?) -> Unit) -> Unit,
    onOpenTokenPage: (String) -> Unit = {},
) {
    var tokenText by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = BgmIcons.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(text = "使用访问令牌登录")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "在 Bangumi 官网生成访问令牌后粘贴到下方即可登录，令牌长期有效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 令牌页面与授权页一样在应用内打开：自定义浏览器用不了 ECH，强阻断网络下会打不开
                OutlinedButton(
                    onClick = { onOpenTokenPage(ACCESS_TOKEN_PAGE_URL) },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = BgmIcons.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("打开令牌页面")
                }

                Text(
                    text = "1. 打开令牌页面并生成令牌（需已登录 Bangumi）\n2. 复制生成的令牌，粘贴到下方",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = tokenText,
                    onValueChange = {
                        tokenText = it
                        errorMessage = null
                    },
                    label = { Text("访问令牌") },
                    placeholder = { Text("粘贴访问令牌") },
                    singleLine = true,
                    enabled = !isLoading,
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                clipboardManager.getText()?.text?.let { clipText ->
                                    tokenText = clipText.trim()
                                    errorMessage = null
                                }
                            },
                            enabled = !isLoading,
                        ) {
                            Icon(
                                imageVector = BgmIcons.ContentPaste,
                                contentDescription = "粘贴",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val token = tokenText.trim()
                    if (token.isBlank()) {
                        errorMessage = "请先粘贴访问令牌"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    onSubmit(token) { success, error ->
                        isLoading = false
                        if (success) {
                            onDismiss()
                        } else {
                            errorMessage = error ?: "登录失败，请确认令牌是否有效"
                        }
                    }
                },
                enabled = !isLoading && tokenText.isNotBlank(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("登录")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading,
            ) {
                Text("取消")
            }
        },
    )
}

@com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
@Composable
private fun UnauthenticatedLandingViewPreview() {
    com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme {
        UnauthenticatedLandingView(
            onLogin = {},
            isAuthenticating = false,
        )
    }
}
