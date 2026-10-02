package com.infinitezerone.minibgm.feature.user

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 全局设置二级页面（自「个人中心」右上角 ⚙️ 齿轮进入）：
 * - 播放源周期同步与即时检查；
 * - 追番播映提醒与延迟偏移设置；
 * - AI 追番助手本地模型配置；
 * - 离线图片与网络缓存清理；
 * - 客户端版本、开源协议与 Bangumi 官方入口；
 * - 账号退出与安全管理。
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlaybackRulesClick: (() -> Unit)? = null,
    enableAiConfig: Boolean = true,
    viewModel: UserViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val openWebUrl = { url: String ->
        context.launchWebUrl(url)
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasNotificationPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }

    // 崩溃日志按需读取：内容留在 state 里，关掉再打开不必重读磁盘
    var showCrashLogDialog by remember { mutableStateOf(false) }
    var crashLogText by remember { mutableStateOf("") }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasNotificationPermission = granted
            if (granted) {
                viewModel.setAiringReminderEnabled(true)
                coroutineScope.launch {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = "已开启追番开播提醒（如需横幅/振动可在系统设置中开启）",
                            actionLabel = "去设置",
                            duration = SnackbarDuration.Short,
                        )
                    if (result == SnackbarResult.ActionPerformed) {
                        val intent =
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        context.startActivity(intent)
                    }
                }
            } else {
                viewModel.setAiringReminderEnabled(false)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("未授予通知权限，无法接收提醒")
                }
            }
        }

    val toggleAiringReminder: (Boolean) -> Unit = { enabled ->
        if (enabled) {
            val systemAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (!systemAllowed) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    showPermissionRationaleDialog = true
                }
            } else {
                viewModel.setAiringReminderEnabled(true)
            }
        } else {
            viewModel.setAiringReminderEnabled(false)
        }
    }

    SettingsScreenContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onSelectSyncInterval = viewModel::setSyncInterval,
        onSyncNow = {
            viewModel.syncBangumiDataNow { success ->
                coroutineScope.launch {
                    if (success) {
                        snackbarHostState.showSnackbar("播放源已是最新状态")
                    } else {
                        snackbarHostState.showSnackbar("同步失败，请检查网络设置")
                    }
                }
            }
        },
        onToggleAiringReminder = toggleAiringReminder,
        airingDailySummaryEnabled = uiState.airingDailySummaryEnabled,
        onToggleAiringDailySummary = viewModel::setAiringDailySummaryEnabled,
        airingPreAirEnabled = uiState.airingPreAirEnabled,
        onToggleAiringPreAir = viewModel::setAiringPreAirEnabled,
        hasNotificationPermission = hasNotificationPermission,
        airingReminderHour = uiState.airingReminderHour,
        onSelectReminderHour = viewModel::setAiringReminderHour,
        airingNotificationOffsetMinutes = uiState.airingNotificationOffsetMinutes,
        onSelectAiringNotificationOffsetMinutes = viewModel::setAiringNotificationOffsetMinutes,
        onOpenSystemNotificationSettings = {
            val intent =
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            context.startActivity(intent)
        },
        amoledDarkMode = uiState.amoledDarkMode,
        onToggleAmoledDarkMode = viewModel::setAmoledDarkMode,
        pipEnabled = uiState.pipEnabled,
        onTogglePipEnabled = viewModel::setPipEnabled,
        showRestrictedContent = uiState.showRestrictedContent,
        onToggleShowRestrictedContent = viewModel::setShowRestrictedContent,
        onSaveAiConfig = viewModel::setAiConfig,
        onOpenWebUrl = openWebUrl,
        onClearCache = {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("本地缓存与临时数据已清理")
            }
        },
        onOpenCrashLog = {
            coroutineScope.launch {
                val log = viewModel.loadLatestCrashLog()
                if (log == null) {
                    snackbarHostState.showSnackbar("暂无崩溃记录")
                } else {
                    crashLogText = log.content
                    showCrashLogDialog = true
                }
            }
        },
        onLogoutCurrent = viewModel::logout,
        onLogoutAll = viewModel::logoutAll,
        onPlaybackRulesClick = onPlaybackRulesClick,
        enableAiConfig = enableAiConfig,
        onTestNotification = {
            val intent =
                Intent("com.infinitezerone.minibgm.sync.work.action.TEST_NOTIFICATION").apply {
                    setPackage(context.packageName)
                }
            context.sendBroadcast(intent)
            com.infinitezerone.minibgm.core.common.InAppNotificationBus.post(
                com.infinitezerone.minibgm.core.common.InAppNotification(
                    title = "番剧完结提醒",
                    message = "🎬《葬送的芙莉莲》全剧完结！共 28 话现已全部放送完毕，可以一口气爽快开刷了～",
                    subjectId = 398061L,
                ),
            )
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )

    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = {
                Text(
                    text = "需要系统通知权限",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "您已关闭或未开启 MiniBgm 的通知权限。请前往系统设置中允许通知，以便接收每日追番开播提醒。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationaleDialog = false
                        val intent =
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        context.startActivity(intent)
                    },
                ) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationaleDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showCrashLogDialog) {
        CrashLogDialog(
            content = crashLogText,
            onClear = {
                coroutineScope.launch {
                    viewModel.clearCrashLogs()
                    showCrashLogDialog = false
                    snackbarHostState.showSnackbar("崩溃日志已清空")
                }
            },
            onDismiss = { showCrashLogDialog = false },
        )
    }
}

/**
 * 崩溃日志查看与导出对话框。
 *
 * 「复制」对应 GitHub Issue / Bangumi 小组发帖（粘贴最顺手），「分享」对应任意 IM 或邮箱——
 * 两条出口都留着，只给一条会让另一类用户多绕几步。
 *
 * 正文用等宽字体：堆栈是靠缩进层级和行号读的，非等宽下嵌套关系糊成一片。
 */
@Composable
private fun CrashLogDialog(
    content: String,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "崩溃日志",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = "贴进 Issue 或发给开发者，配合 release 页的 mapping 才能还原出崩溃位置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(content))
                    onDismiss()
                },
            ) {
                Text("复制")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { shareCrashLog(context, content) }) {
                    Text("分享")
                }
                TextButton(onClick = onClear) {
                    Text("清空")
                }
            }
        },
    )
}

/** 拉起系统分享面板。日志只有几 KB，走 `EXTRA_TEXT` 就够，不必引入 FileProvider */
private fun shareCrashLog(
    context: Context,
    content: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MiniBgm 崩溃报告")
            putExtra(Intent.EXTRA_TEXT, content)
        }
    context.startActivity(Intent.createChooser(intent, "导出崩溃日志"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    uiState: UserUiState,
    onBackClick: () -> Unit,
    onSelectSyncInterval: (SyncInterval) -> Unit,
    onSyncNow: () -> Unit,
    onToggleAiringReminder: (Boolean) -> Unit,
    airingDailySummaryEnabled: Boolean = true,
    onToggleAiringDailySummary: (Boolean) -> Unit = {},
    airingPreAirEnabled: Boolean = true,
    onToggleAiringPreAir: (Boolean) -> Unit = {},
    hasNotificationPermission: Boolean = true,
    airingReminderHour: Int,
    onSelectReminderHour: (Int) -> Unit,
    airingNotificationOffsetMinutes: Int = -15,
    onSelectAiringNotificationOffsetMinutes: (Int) -> Unit = {},
    onOpenSystemNotificationSettings: () -> Unit = {},
    amoledDarkMode: Boolean = false,
    onToggleAmoledDarkMode: (Boolean) -> Unit = {},
    pipEnabled: Boolean = true,
    onTogglePipEnabled: (Boolean) -> Unit = {},
    showRestrictedContent: Boolean = false,
    onToggleShowRestrictedContent: (Boolean) -> Unit = {},
    onSaveAiConfig: (AiConfig) -> Unit,
    onOpenWebUrl: (String) -> Unit,
    onClearCache: () -> Unit,
    onOpenCrashLog: () -> Unit = {},
    onLogoutCurrent: () -> Unit,
    onLogoutAll: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onPlaybackRulesClick: (() -> Unit)? = null,
    enableAiConfig: Boolean = true,
    onTestNotification: () -> Unit = {},
) {
    var showLogoutAllDialog by remember { mutableStateOf(false) }
    var showLogoutCurrentDialog by remember { mutableStateOf(false) }
    var showSyncIntervalDialog by remember { mutableStateOf(false) }
    var showReminderHourDialog by remember { mutableStateOf(false) }
    var showTimingBottomSheet by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = { Text(text = "设置") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        snackbarHost = { BgmSnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "settings_sections") {
                SettingsSection(
                    isLoggedIn = uiState.isLoggedIn,
                    activeProfile = uiState.activeProfile,
                    savedAccountsCount = uiState.savedAccounts.size,
                    syncInterval = uiState.syncInterval,
                    lastSyncTimestamp = uiState.lastSyncTimestamp,
                    isSyncing = uiState.isSyncing,
                    airingReminderEnabled = uiState.airingReminderEnabled,
                    onToggleAiringReminder = onToggleAiringReminder,
                    airingDailySummaryEnabled = airingDailySummaryEnabled,
                    onToggleAiringDailySummary = onToggleAiringDailySummary,
                    airingPreAirEnabled = airingPreAirEnabled,
                    onToggleAiringPreAir = onToggleAiringPreAir,
                    hasNotificationPermission = hasNotificationPermission,
                    airingReminderHour = airingReminderHour,
                    aiConfig = uiState.aiConfig,
                    onOpenAiSettingsDialog =
                        if (enableAiConfig) {
                            { showAiSettingsDialog = true }
                        } else {
                            null
                        },
                    onOpenReminderHourDialog = { showReminderHourDialog = true },
                    airingNotificationOffsetMinutes = airingNotificationOffsetMinutes,
                    onOpenTimingBottomSheet = { showTimingBottomSheet = true },
                    onOpenSystemNotificationSettings = onOpenSystemNotificationSettings,
                    amoledDarkMode = amoledDarkMode,
                    onToggleAmoledDarkMode = onToggleAmoledDarkMode,
                    pipEnabled = pipEnabled,
                    onTogglePipEnabled = onTogglePipEnabled,
                    showRestrictedContent = showRestrictedContent,
                    onToggleShowRestrictedContent = onToggleShowRestrictedContent,
                    onTestNotification = onTestNotification,
                    onOpenSyncDialog = { showSyncIntervalDialog = true },
                    onSyncNow = onSyncNow,
                    onOpenWebUrl = onOpenWebUrl,
                    onClearCache = onClearCache,
                    onOpenCrashLog = onOpenCrashLog,
                    onLogoutCurrentClick = { showLogoutCurrentDialog = true },
                    onLogoutAllClick = { showLogoutAllDialog = true },
                    onOpenPlaybackRules = onPlaybackRulesClick,
                )
            }
        }
    }

    if (showAiSettingsDialog) {
        AiSettingsDialog(
            currentConfig = uiState.aiConfig,
            onSaveConfig = onSaveAiConfig,
            onDismiss = { showAiSettingsDialog = false },
        )
    }

    if (showSyncIntervalDialog) {
        SyncIntervalDialog(
            currentInterval = uiState.syncInterval,
            onSelectInterval = onSelectSyncInterval,
            onDismiss = { showSyncIntervalDialog = false },
        )
    }

    if (showReminderHourDialog) {
        ReminderHourDialog(
            currentHour = airingReminderHour,
            onSelectHour = onSelectReminderHour,
            onDismiss = { showReminderHourDialog = false },
        )
    }

    if (showTimingBottomSheet) {
        AiringTimingBottomSheet(
            initialOffsetMinutes = airingNotificationOffsetMinutes,
            onConfirmOffset = onSelectAiringNotificationOffsetMinutes,
            onDismiss = { showTimingBottomSheet = false },
        )
    }

    if (showLogoutCurrentDialog) {
        val currentProfile = uiState.activeProfile
        AlertDialog(
            onDismissRequest = { showLogoutCurrentDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(text = "退出当前账号") },
            text = {
                Text(
                    text =
                        if (currentProfile != null) {
                            "退出「${currentProfile.displayName}」(@${currentProfile.username})？退出后需重新登录。"
                        } else {
                            "退出当前账号？退出后需重新登录。"
                        },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutCurrentDialog = false
                        onLogoutCurrent()
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    Text(text = "退出登录")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutCurrentDialog = false }) {
                    Text(text = "取消")
                }
            },
        )
    }

    if (showLogoutAllDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutAllDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(text = "退出所有账号") },
            text = {
                Text(
                    text = "退出设备上保存的全部 ${uiState.savedAccounts.size} 个账号？退出后需重新登录。",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutAllDialog = false
                        onLogoutAll()
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    Text(text = "退出全部")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutAllDialog = false }) {
                    Text(text = "取消")
                }
            },
        )
    }
}

@ThemePreviews
@Composable
private fun SettingsScreenPreview() {
    MiniBgmTheme {
        SettingsScreenContent(
            uiState =
                UserUiState(
                    isLoggedIn = true,
                    activeProfile =
                        UserProfile(
                            id = 1L,
                            username = "test_user",
                            nickname = "测试萌友",
                        ),
                    savedAccounts = emptyList(),
                ),
            onBackClick = {},
            onSelectSyncInterval = {},
            onSyncNow = {},
            onToggleAiringReminder = {},
            airingReminderHour = 8,
            onSelectReminderHour = {},
            airingNotificationOffsetMinutes = -15,
            onSelectAiringNotificationOffsetMinutes = {},
            amoledDarkMode = false,
            onToggleAmoledDarkMode = {},
            pipEnabled = true,
            onTogglePipEnabled = {},
            onSaveAiConfig = {},
            onOpenWebUrl = {},
            onClearCache = {},
            onLogoutCurrent = {},
            onLogoutAll = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
