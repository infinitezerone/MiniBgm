package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.R
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews

/**
 * 追番通知权限场景化引导对话框（方案 B）：
 * 当用户将番剧加入「在看」追番时触发，以具体的业务收益（每日开播与逐集更新提醒）引导用户开启通知权限，
 * 避免应用初次启动冷启动裸弹被拒绝。
 */
@Composable
fun AiringReminderPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subjectTitle: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = BgmIcons.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        },
        title = {
            Text(
                text = stringResource(R.string.core_designsystem_airing_perm_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                val detailText =
                    if (!subjectTitle.isNullOrBlank()) {
                        stringResource(R.string.core_designsystem_airing_perm_msg_with_title, subjectTitle)
                    } else {
                        stringResource(R.string.core_designsystem_airing_perm_msg_generic)
                    }
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.core_designsystem_airing_perm_tip),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.core_designsystem_action_enable_notification),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.core_designsystem_action_not_now),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@ThemePreviews
@Composable
private fun AiringReminderPermissionDialogPreview() {
    MiniBgmTheme {
        AiringReminderPermissionDialog(
            subjectTitle = "葬送的芙莉莲",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
