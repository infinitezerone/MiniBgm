package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 未登录访客状态个人卡片（当前无调用点，保留备选形态）：
 * 结构与 [UserProfileHero] 对齐（圆形头像 + 标题副标 + 紧凑操作入口），
 * 消除原本 400dp 大面积推销文案带来的臃肿感，登录前后视觉重心保持高度一致与克制。
 */
@Composable
internal fun UnauthenticatedCard(
    onLogin: () -> Unit,
    isAuthenticating: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(enabled = !isAuthenticating, onClick = onLogin),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 访客头像
            Surface(
                shape = CircleShape,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(54.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "未登录访客",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 标题、说明与特性标签
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "登录 Bangumi 账号",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "同步追番打卡与条目收藏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = "未登录 · 离线模式",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 快捷登录胶囊按钮
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(enabled = !isAuthenticating, onClick = onLogin),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = "去登录",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
        }
    }
}

@com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
@Composable
private fun UnauthenticatedCardPreview() {
    com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme {
        UnauthenticatedCard(
            onLogin = {},
            isAuthenticating = false,
        )
    }
}

/**
 * 全屏未登录沉浸式引导界面：
 * 当用户处于未登录状态时占据「个人中心」主体，提供聚焦的登录入口与核心权益亮点。
 */
@Composable
internal fun UnauthenticatedLandingView(
    onLogin: () -> Unit,
    onLoginWithToken: (String, (Boolean, String?) -> Unit) -> Unit = { _, _ -> },
    isAuthenticating: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showTokenDialog by rememberSaveable { mutableStateOf(false) }

    if (showTokenDialog) {
        PersonalAccessTokenDialog(
            onDismiss = { showTokenDialog = false },
            onSubmit = onLoginWithToken,
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
                            imageVector = Icons.Filled.Person,
                            contentDescription = "未登录",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "登录 Bangumi 账号",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "同步每日追番 · 整理条目资产 · 跨设备漫游",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 核心权益胶囊行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LandingFeatureBadge(
                        icon = Icons.Filled.Tv,
                        text = "追番打卡",
                        modifier = Modifier.weight(1f),
                    )
                    LandingFeatureBadge(
                        icon = Icons.Filled.Bookmark,
                        text = "五维收藏",
                        modifier = Modifier.weight(1f),
                    )
                    LandingFeatureBadge(
                        icon = Icons.Filled.Notifications,
                        text = "更新提醒",
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 大号主操作登录按钮
                Button(
                    onClick = onLogin,
                    enabled = !isAuthenticating,
                    shape = RoundedCornerShape(14.dp),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "立即登录 Bangumi 账号",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                // 次级操作：Access Token 直连登录
                OutlinedButton(
                    onClick = { showTokenDialog = true },
                    enabled = !isAuthenticating,
                    shape = RoundedCornerShape(14.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "使用 Access Token 登录 (国内推荐)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Text(
                    text = "支持官方 OAuth 网页授权与个人访问令牌直连",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun PersonalAccessTokenDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, (Boolean, String?) -> Unit) -> Unit,
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
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(text = "使用 Access Token 登录")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text =
                        "国内直连推荐方案：无需梯子与网页跳转，长期有效免续期。\n\n" +
                            "1. 在任意设备浏览器打开：\n   https://next.bgm.tv/demo/access-token\n" +
                            "2. 登录并复制生成的 Access Token 粘贴至下方。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = tokenText,
                    onValueChange = {
                        tokenText = it
                        errorMessage = null
                    },
                    label = { Text("个人访问令牌") },
                    placeholder = { Text("在此粘贴 Access Token") },
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
                                imageVector = Icons.Filled.ContentPaste,
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
                        errorMessage = "令牌不能为空"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    onSubmit(token) { success, error ->
                        isLoading = false
                        if (success) {
                            onDismiss()
                        } else {
                            errorMessage = error ?: "登录失败，请检查令牌有效性"
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
                    Text("验证并登录")
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

@Composable
private fun LandingFeatureBadge(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
