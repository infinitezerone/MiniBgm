package com.infinitezerone.minibgm.feature.user

import android.content.ClipData
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.ConfirmDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.OverlayHostState
import com.infinitezerone.minibgm.core.designsystem.component.SingleChoiceDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.model.ThemeMode
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import com.infinitezerone.minibgm.feature.user.components.AiSettingsDialog
import com.infinitezerone.minibgm.feature.user.components.AiringTimingBottomSheet
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

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
    onClearCache: suspend () -> Unit = {},
    viewModel: UserViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val overlayHostState = rememberOverlayHostState()
    val coroutineScope = rememberCoroutineScope()

    val openWebUrl = { url: String ->
        context.launchWebUrl(url)
    }

    var hasNotificationPermission by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        hasNotificationPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasNotificationPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // 清理缓存期间行内置忙（onClick 置空防重入），结果按真实成败反馈
    var isClearingCache by remember { mutableStateOf(false) }

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
                            message = context.getString(R.string.feature_user_reminder_enabled_toast),
                            actionLabel = context.getString(R.string.feature_user_action_go_settings),
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
                    snackbarHostState.showSnackbar(context.getString(R.string.feature_user_notification_denied_toast))
                }
            }
        }

    val showNotificationRationale = {
        coroutineScope.launch {
            val confirmed =
                overlayHostState.await(
                    ConfirmDialogAction(
                        title = context.getString(R.string.feature_user_notification_rationale_title),
                        message = context.getString(R.string.feature_user_notification_rationale_message),
                        confirmText = context.getString(R.string.feature_user_action_go_system_settings),
                        dismissText = context.getString(DesignSystemR.string.core_designsystem_action_cancel),
                    ),
                )
            if (confirmed) {
                val intent =
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                context.startActivity(intent)
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
                    showNotificationRationale()
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
        onToggleAiringReminder = toggleAiringReminder,
        airingDailySummaryEnabled = uiState.airingDailySummaryEnabled,
        onToggleAiringDailySummary = viewModel::setAiringDailySummaryEnabled,
        airingPreAirEnabled = uiState.airingPreAirEnabled,
        onToggleAiringPreAir = viewModel::setAiringPreAirEnabled,
        airingBingeFinaleEnabled = uiState.airingBingeFinaleEnabled,
        onToggleAiringBingeFinale = viewModel::setAiringBingeFinaleEnabled,
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
        themeMode = uiState.themeMode,
        onSelectThemeMode = viewModel::setThemeMode,
        dynamicColor = uiState.dynamicColor,
        onToggleDynamicColor = viewModel::setDynamicColor,
        pipEnabled = uiState.pipEnabled,
        onTogglePipEnabled = viewModel::setPipEnabled,
        showRestrictedContent = uiState.showRestrictedContent,
        onToggleShowRestrictedContent = viewModel::setShowRestrictedContent,
        onSaveAiConfig = viewModel::setAiConfig,
        onOpenWebUrl = openWebUrl,
        onClearCache = {
            if (!isClearingCache) {
                isClearingCache = true
                coroutineScope.launch {
                    val result = runCatching { onClearCache() }
                    isClearingCache = false
                    snackbarHostState.showSnackbar(
                        if (result.isSuccess) {
                            context.getString(R.string.feature_user_cache_cleared_toast)
                        } else {
                            context.getString(R.string.feature_user_cache_clear_failed_toast)
                        },
                    )
                }
            }
        },
        isClearingCache = isClearingCache,
        onOpenCrashLog = {
            coroutineScope.launch {
                val log = viewModel.loadLatestCrashLog()
                if (log == null) {
                    snackbarHostState.showSnackbar(context.getString(R.string.feature_user_crash_none_toast))
                } else {
                    crashLogText = log.content
                    showCrashLogDialog = true
                }
            }
        },
        onCheckForUpdate = {
            val clientVersion =
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                    .getOrNull()
                    .orEmpty()
            coroutineScope.launch {
                when (val result = viewModel.checkForUpdate(clientVersion)) {
                    is AppResult.Success -> {
                        val info = result.data
                        if (info.hasUpdate) {
                            val message =
                                buildString {
                                    append(context.getString(R.string.feature_user_update_latest_version, info.latestVersion))
                                    if (info.publishedAt.isNotBlank()) {
                                        append(" ")
                                        append(
                                            context.getString(
                                                R.string.feature_user_update_published_at,
                                                info.publishedAt.take(10),
                                            ),
                                        )
                                    }
                                    if (info.releaseNotes.isNotBlank()) {
                                        append("\n\n")
                                        append(context.getString(R.string.feature_user_update_release_notes_header))
                                        append("\n")
                                        append(info.releaseNotes.trim())
                                    }
                                }
                            val confirmed =
                                overlayHostState.await(
                                    ConfirmDialogAction(
                                        title = context.getString(R.string.feature_user_update_found_title, info.latestVersion),
                                        message = message,
                                        confirmText = context.getString(R.string.feature_user_action_go_release),
                                        dismissText = context.getString(R.string.feature_user_action_update_later),
                                        isPrimary = true,
                                        icon = BgmIcons.OpenInNew,
                                    ),
                                )
                            if (confirmed) {
                                val targetUrl =
                                    info.releaseUrl.ifBlank {
                                        info.downloadUrl
                                            ?: "https://github.com/infinitezerone/MiniBgm/releases/latest"
                                    }
                                openWebUrl(targetUrl)
                            }
                        } else {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.feature_user_update_up_to_date_toast, clientVersion),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                    is AppResult.Error -> {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.feature_user_update_failed_toast, result.message),
                            duration = SnackbarDuration.Short,
                        )
                    }
                    is AppResult.Loading -> Unit
                }
            }
        },
        onLogoutCurrent = viewModel::logout,
        onLogoutAll = viewModel::logoutAll,
        onPlaybackRulesClick = onPlaybackRulesClick,
        enableAiConfig = enableAiConfig,
        snackbarHostState = snackbarHostState,
        overlayHostState = overlayHostState,
        modifier = modifier,
    )

    if (showCrashLogDialog) {
        CrashLogDialog(
            content = crashLogText,
            onClear = {
                coroutineScope.launch {
                    viewModel.clearCrashLogs()
                    showCrashLogDialog = false
                    snackbarHostState.showSnackbar(context.getString(R.string.feature_user_crash_cleared_toast))
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
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.feature_user_crash_log_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.feature_user_crash_log_desc),
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
                    coroutineScope.launch {
                        val clipEntry = ClipEntry(ClipData.newPlainText("crash_log", content))
                        clipboard.setClipEntry(clipEntry)
                    }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.feature_user_action_copy))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { shareCrashLog(context, content) }) {
                    Text(stringResource(R.string.feature_user_action_share))
                }
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.feature_user_action_clear))
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
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feature_user_crash_report_subject))
            putExtra(Intent.EXTRA_TEXT, content)
        }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.feature_user_crash_export_title)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    uiState: UserUiState,
    onBackClick: () -> Unit,
    onSelectSyncInterval: (SyncInterval) -> Unit,
    onToggleAiringReminder: (Boolean) -> Unit,
    airingDailySummaryEnabled: Boolean = true,
    onToggleAiringDailySummary: (Boolean) -> Unit = {},
    airingPreAirEnabled: Boolean = true,
    onToggleAiringPreAir: (Boolean) -> Unit = {},
    airingBingeFinaleEnabled: Boolean = true,
    onToggleAiringBingeFinale: (Boolean) -> Unit = {},
    hasNotificationPermission: Boolean = true,
    airingReminderHour: Int,
    onSelectReminderHour: (Int) -> Unit,
    airingNotificationOffsetMinutes: Int = -15,
    onSelectAiringNotificationOffsetMinutes: (Int) -> Unit = {},
    onOpenSystemNotificationSettings: () -> Unit = {},
    amoledDarkMode: Boolean = false,
    onToggleAmoledDarkMode: (Boolean) -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onSelectThemeMode: (ThemeMode) -> Unit = {},
    dynamicColor: Boolean = false,
    onToggleDynamicColor: (Boolean) -> Unit = {},
    pipEnabled: Boolean = true,
    onTogglePipEnabled: (Boolean) -> Unit = {},
    showRestrictedContent: Boolean = false,
    onToggleShowRestrictedContent: (Boolean) -> Unit = {},
    onSaveAiConfig: (AiConfig) -> Unit,
    onOpenWebUrl: (String) -> Unit,
    onClearCache: () -> Unit,
    isClearingCache: Boolean = false,
    onOpenCrashLog: () -> Unit = {},
    onLogoutCurrent: () -> Unit,
    onLogoutAll: () -> Unit,
    onCheckForUpdate: () -> Unit = {},
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onPlaybackRulesClick: (() -> Unit)? = null,
    enableAiConfig: Boolean = true,
    overlayHostState: OverlayHostState = rememberOverlayHostState(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showTimingBottomSheet by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = { Text(text = stringResource(R.string.feature_user_title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = BgmIcons.ArrowBack,
                            contentDescription = stringResource(DesignSystemR.string.core_designsystem_action_back),
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
            item(key = "settings_appearance") {
                AppearanceSettingsCard(
                    themeMode = themeMode,
                    onSelectThemeMode = onSelectThemeMode,
                    dynamicColor = dynamicColor,
                    onToggleDynamicColor = onToggleDynamicColor,
                    amoledDarkMode = amoledDarkMode,
                    onToggleAmoledDarkMode = onToggleAmoledDarkMode,
                )
            }

            if (onPlaybackRulesClick != null) {
                item(key = "settings_playback") {
                    PlaybackSettingsCard(
                        pipEnabled = pipEnabled,
                        onTogglePipEnabled = onTogglePipEnabled,
                    )
                }
            }

            item(key = "settings_preference") {
                PreferenceSettingsCard(
                    showRestrictedContent = showRestrictedContent,
                    onToggleShowRestrictedContent = onToggleShowRestrictedContent,
                )
            }

            item(key = "settings_sync_reminder") {
                SyncAndReminderSettingsCard(
                    syncInterval = uiState.syncInterval,
                    onOpenSyncDialog = {
                        coroutineScope.launch {
                            val selected =
                                overlayHostState.await(
                                    SingleChoiceDialogAction(
                                        title = context.getString(R.string.feature_user_sync_interval_title),
                                        options = SyncInterval.entries,
                                        selectedOption = uiState.syncInterval,
                                        optionLabel = { it.displayName },
                                    ),
                                )
                            if (selected != null) {
                                onSelectSyncInterval(selected)
                            }
                        }
                    },
                    onOpenPlaybackRules = onPlaybackRulesClick,
                    airingReminderEnabled = uiState.airingReminderEnabled,
                    onToggleAiringReminder = onToggleAiringReminder,
                    hasNotificationPermission = hasNotificationPermission,
                    airingDailySummaryEnabled = airingDailySummaryEnabled,
                    onToggleAiringDailySummary = onToggleAiringDailySummary,
                    airingReminderHour = airingReminderHour,
                    onOpenReminderHourDialog = {
                        coroutineScope.launch {
                            val selected =
                                overlayHostState.await(
                                    SingleChoiceDialogAction(
                                        title = context.getString(R.string.feature_user_reminder_hour_title),
                                        options = listOf(7, 8, 12, 18, 21),
                                        selectedOption = airingReminderHour,
                                        optionLabel = { "%02d:00".format(it) },
                                    ),
                                )
                            if (selected != null) {
                                onSelectReminderHour(selected)
                            }
                        }
                    },
                    airingPreAirEnabled = airingPreAirEnabled,
                    onToggleAiringPreAir = onToggleAiringPreAir,
                    airingNotificationOffsetMinutes = airingNotificationOffsetMinutes,
                    onOpenTimingBottomSheet = { showTimingBottomSheet = true },
                    airingBingeFinaleEnabled = airingBingeFinaleEnabled,
                    onToggleAiringBingeFinale = onToggleAiringBingeFinale,
                    onOpenSystemNotificationSettings = onOpenSystemNotificationSettings,
                )
            }

            item(key = "settings_ai_storage") {
                AiAndStorageSettingsCard(
                    aiConfig = uiState.aiConfig,
                    onOpenAiSettingsDialog =
                        if (enableAiConfig) {
                            { showAiSettingsDialog = true }
                        } else {
                            null
                        },
                    onClearCache = onClearCache,
                    isClearingCache = isClearingCache,
                    onOpenCrashLog = onOpenCrashLog,
                )
            }

            item(key = "settings_about_support") {
                AboutAndSupportSettingsCard(
                    isLoggedIn = uiState.isLoggedIn,
                    activeProfile = uiState.activeProfile,
                    savedAccountsCount = uiState.savedAccounts.size,
                    onOpenWebUrl = onOpenWebUrl,
                    isCheckingUpdate = uiState.isCheckingUpdate,
                    onCheckForUpdate = onCheckForUpdate,
                    onLogoutCurrentClick = {
                        val currentProfile = uiState.activeProfile
                        val message =
                            if (currentProfile != null) {
                                context.getString(
                                    R.string.feature_user_logout_confirm_named,
                                    currentProfile.displayName,
                                    currentProfile.username,
                                )
                            } else {
                                context.getString(R.string.feature_user_logout_current_confirm)
                            }
                        coroutineScope.launch {
                            val confirmed =
                                overlayHostState.await(
                                    ConfirmDialogAction(
                                        title = context.getString(R.string.feature_user_logout_current_title),
                                        message = message,
                                        confirmText = context.getString(R.string.feature_user_logout_action),
                                        isDestructive = true,
                                        icon = BgmIcons.Logout,
                                    ),
                                )
                            if (confirmed) {
                                onLogoutCurrent()
                            }
                        }
                    },
                    onLogoutAllClick = {
                        coroutineScope.launch {
                            val confirmed =
                                overlayHostState.await(
                                    ConfirmDialogAction(
                                        title = context.getString(R.string.feature_user_logout_all_title),
                                        message =
                                            context.getString(
                                                R.string.feature_user_logout_all_confirm,
                                                uiState.savedAccounts.size,
                                            ),
                                        confirmText = context.getString(R.string.feature_user_logout_all_action),
                                        isDestructive = true,
                                        icon = BgmIcons.Delete,
                                    ),
                                )
                            if (confirmed) {
                                onLogoutAll()
                            }
                        }
                    },
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

    if (showTimingBottomSheet) {
        AiringTimingBottomSheet(
            initialOffsetMinutes = airingNotificationOffsetMinutes,
            onConfirmOffset = onSelectAiringNotificationOffsetMinutes,
            onDismiss = { showTimingBottomSheet = false },
        )
    }

    BgmOverlayHost(hostState = overlayHostState) {
        confirmDialog()
        singleChoiceDialog()
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
