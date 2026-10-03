package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons

/**
 * 登录引导弹窗：统一图标、标题、说明与「立即登录 / 稍后再说」双按钮结构，文案按场景覆盖。
 *
 * 登录页是独立路由（应用内 WebView + ECH 通道）：本组件只负责发起提示与回调，
 * 关闭提示由调用方在 [onLogin] / [onDismiss] 内自行完成。
 */
@Composable
fun BgmLoginPromptDialog(
    description: String,
    onLogin: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "请先登录 Bangumi 账号",
    icon: ImageVector = BgmIcons.AccountCircle,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(onClick = onLogin) {
                Text("立即登录")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后再说")
            }
        },
    )
}
