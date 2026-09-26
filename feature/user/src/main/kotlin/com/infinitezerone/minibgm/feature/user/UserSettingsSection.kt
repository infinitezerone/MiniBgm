package com.infinitezerone.minibgm.feature.user

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.model.UserProfile

private const val BGM_HOME_URL = "https://bgm.tv"

private const val BGM_WIKI_URL = "https://bgm.tv/wiki"

private const val PROJECT_GITHUB_URL = "https://github.com/infinitezerone/MiniBgm"

@Composable
internal fun SettingsSection(
    isLoggedIn: Boolean,
    activeProfile: UserProfile?,
    savedAccountsCount: Int,
    syncInterval: SyncInterval,
    lastSyncTimestamp: Long,
    isSyncing: Boolean,
    airingReminderEnabled: Boolean,
    onToggleAiringReminder: (Boolean) -> Unit,
    airingReminderHour: Int,
    hasNotificationPermission: Boolean = true,
    aiConfig: AiConfig = AiConfig(),
    onOpenAiSettingsDialog: () -> Unit = {},
    onOpenReminderHourDialog: () -> Unit,
    airDelayOffsetMinutes: Int,
    onOpenDelayOffsetDialog: () -> Unit,
    onOpenSyncDialog: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenWebUrl: (String) -> Unit,
    onClearCache: () -> Unit,
    onLogoutCurrentClick: () -> Unit,
    onLogoutAllClick: () -> Unit,
    onOpenPlaybackRules: () -> Unit = {},
    amoledDarkMode: Boolean = false,
    onToggleAmoledDarkMode: (Boolean) -> Unit = {},
    pipEnabled: Boolean = true,
    onTogglePipEnabled: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val lastSyncText =
        if (lastSyncTimestamp == 0L) {
            "尚未同步"
        } else {
            "已是最新"
        }

    // 版本号取自 PackageManager，与 BuildConfig 保持一致；预览环境下取不到则留空
    val context = LocalContext.current
    val clientVersion =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull()
                .orEmpty()
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Group 0: 外观显示
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                    text = "外观显示",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )

                SettingsItemRow(
                    icon = Icons.Filled.DarkMode,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "AMOLED 纯黑模式",
                    subtitle = "深色模式下使用纯黑表面，更省电更沉浸",
                    onClick = { onToggleAmoledDarkMode(!amoledDarkMode) },
                    trailing = {
                        Switch(
                            checked = amoledDarkMode,
                            onCheckedChange = onToggleAmoledDarkMode,
                        )
                    },
                )
            }
        }

        // Group 1: 播放设置
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                    text = "播放设置",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )

                SettingsItemRow(
                    icon = Icons.Filled.PictureInPictureAlt,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "自动画中画",
                    subtitle = "播放视频切回桌面或切换应用时自动开启小窗",
                    onClick = { onTogglePipEnabled(!pipEnabled) },
                    trailing = {
                        Switch(
                            checked = pipEnabled,
                            onCheckedChange = onTogglePipEnabled,
                        )
                    },
                )
            }
        }

        // Group 2: 数据同步与提醒
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                    text = "数据同步与提醒",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )

                SettingsItemRow(
                    icon = Icons.Filled.Sync,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "播放源自动同步",
                    subtitle = "周期：${syncInterval.displayName}",
                    onClick = onOpenSyncDialog,
                )

                SettingsItemRow(
                    icon = Icons.Filled.CloudQueue,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = "检查最新放送源",
                    subtitle = "状态：$lastSyncText · bgm-data",
                    trailing = {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            OutlinedButton(
                                onClick = onSyncNow,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("立即检查", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    onClick = if (!isSyncing) onSyncNow else null,
                )

                SettingsItemRow(
                    icon = Icons.Filled.PlayCircleOutline,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = "播放源管理",
                    subtitle = "导入自备片单、维护第三方解析规则",
                    onClick = onOpenPlaybackRules,
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                val isReminderActive = airingReminderEnabled && hasNotificationPermission
                val reminderSubtitle =
                    if (!hasNotificationPermission) {
                        "⚠️ 系统通知未开启，点击开启权限与每日推送"
                    } else {
                        "每日汇总「我追的」当日更新，开播前 15 分钟逐集提醒"
                    }
                SettingsItemRow(
                    icon = Icons.Filled.NotificationsActive,
                    iconTint = if (hasNotificationPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    title = "追番更新提醒",
                    subtitle = reminderSubtitle,
                    onClick = {
                        onToggleAiringReminder(!isReminderActive)
                    },
                    trailing = {
                        Switch(
                            checked = isReminderActive,
                            onCheckedChange = onToggleAiringReminder,
                        )
                    },
                )

                AnimatedVisibility(
                    visible = isReminderActive,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )

                        SettingsItemRow(
                            icon = Icons.Filled.Schedule,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = "提醒时刻",
                            subtitle = "每天 %02d:00 推送当日更新".format(airingReminderHour),
                            onClick = onOpenReminderHourDialog,
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )

                        SettingsItemRow(
                            icon = Icons.Filled.Schedule,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            title = "开播提醒延迟偏移",
                            subtitle = if (airDelayOffsetMinutes == 0) "无延迟" else "延迟 $airDelayOffsetMinutes 分钟",
                            onClick = onOpenDelayOffsetDialog,
                        )
                    }
                }
            }
        }

        // Group 2: 智能服务与存储
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                    text = "智能服务与存储",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )

                val providerDisplay =
                    when (aiConfig.provider) {
                        AiConfig.PROVIDER_OLLAMA -> "Ollama / Local"
                        AiConfig.PROVIDER_GEMINI -> "Gemini"
                        else -> "Custom OpenAI"
                    }
                val modelDisplay =
                    aiConfig.model.ifBlank {
                        when (aiConfig.provider) {
                            AiConfig.PROVIDER_OLLAMA -> "qwen2.5:7b"
                            AiConfig.PROVIDER_GEMINI -> "gemini-2.5-flash"
                            else -> "gpt-4o-mini"
                        }
                    }

                SettingsItemRow(
                    icon = Icons.Filled.AutoAwesome,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "AI 追番助手配置",
                    subtitle = "$providerDisplay · $modelDisplay",
                    onClick = onOpenAiSettingsDialog,
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                SettingsItemRow(
                    icon = Icons.Filled.CleaningServices,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = "清理本地缓存",
                    subtitle = "清理离线网络图片与临时缓存数据",
                    onClick = onClearCache,
                )
            }
        }

        // Group 3: 关于与系统支持
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                    text = "关于与支持",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )

                SettingsItemRow(
                    icon = Icons.Filled.BookmarkBorder,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "MiniBgm 客户端",
                    subtitle = "v$clientVersion · MIT 开源协议",
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "打开开源主页",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { onOpenWebUrl(PROJECT_GITHUB_URL) },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                SettingsItemRow(
                    icon = Icons.Filled.Language,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = "访问 Bangumi 官网",
                    subtitle = "bgm.tv · ACG 动漫数据库与社区",
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "打开网页",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { onOpenWebUrl(BGM_HOME_URL) },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                SettingsItemRow(
                    icon = Icons.Filled.Info,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = "Bangumi 维基协作指南",
                    subtitle = "条目收录规范与编辑守则",
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "打开网页",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { onOpenWebUrl(BGM_WIKI_URL) },
                )

                if (isLoggedIn) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )

                    val usernameText = activeProfile?.username.orEmpty().ifBlank { activeProfile?.id?.toString().orEmpty() }
                    SettingsItemRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = "退出当前账号",
                        subtitle = "注销当前登录 (@$usernameText)，保留其他已存账号",
                        onClick = onLogoutCurrentClick,
                    )

                    if (savedAccountsCount > 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )

                        SettingsItemRow(
                            icon = Icons.Filled.DeleteOutline,
                            iconTint = MaterialTheme.colorScheme.error,
                            title = "退出所有已存账号",
                            subtitle = "清除本机全部登录账号与本地缓存",
                            onClick = onLogoutAllClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SyncIntervalDialog(
    currentInterval: SyncInterval,
    onSelectInterval: (SyncInterval) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放源自动同步频率") },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                SyncInterval.entries.forEach { interval ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectInterval(interval)
                                    onDismiss()
                                }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = (interval == currentInterval),
                            onClick = {
                                onSelectInterval(interval)
                                onDismiss()
                            },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = interval.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
internal fun ReminderHourDialog(
    currentHour: Int,
    onSelectHour: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("每日提醒时刻") },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                listOf(7, 8, 12, 18, 21).forEach { hour ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectHour(hour)
                                    onDismiss()
                                }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = (hour == currentHour),
                            onClick = {
                                onSelectHour(hour)
                                onDismiss()
                            },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "%02d:00".format(hour),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
internal fun DelayOffsetDialog(
    currentOffset: Int,
    onSelectOffset: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开播提醒延迟偏移") },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                listOf(0, 5, 10, 15, 30, 60).forEach { offset ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectOffset(offset)
                                    onDismiss()
                                }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = (offset == currentOffset),
                            onClick = {
                                onSelectOffset(offset)
                                onDismiss()
                            },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (offset == 0) "无延迟" else "延迟 $offset 分钟",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
                ).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = iconTint.copy(alpha = 0.12f),
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun SettingsSectionPreview() {
    MiniBgmTheme {
        SettingsSection(
            isLoggedIn = true,
            activeProfile = null,
            savedAccountsCount = 1,
            syncInterval = SyncInterval.DAILY,
            lastSyncTimestamp = 123456789L,
            isSyncing = false,
            airingReminderEnabled = true,
            onToggleAiringReminder = {},
            airingReminderHour = 8,
            onOpenReminderHourDialog = {},
            airDelayOffsetMinutes = 15,
            onOpenDelayOffsetDialog = {},
            onOpenSyncDialog = {},
            onSyncNow = {},
            onOpenWebUrl = {},
            onClearCache = {},
            onLogoutCurrentClick = {},
            onLogoutAllClick = {},
            amoledDarkMode = true,
            onToggleAmoledDarkMode = {},
            pipEnabled = true,
            onTogglePipEnabled = {},
        )
    }
}
