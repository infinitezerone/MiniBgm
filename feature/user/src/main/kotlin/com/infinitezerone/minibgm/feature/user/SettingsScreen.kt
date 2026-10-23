package com.infinitezerone.minibgm.feature.user

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.download.TorrentStreamEngine
import com.infinitezerone.minibgm.core.designsystem.component.BgmCircleActionButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopBarMode
import com.infinitezerone.minibgm.core.designsystem.component.ConfirmDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.OverlayHostState
import com.infinitezerone.minibgm.core.designsystem.component.SingleChoiceDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.DanmakuConfig
import com.infinitezerone.minibgm.core.model.DownloadState
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.model.ThemeMode
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.model.formatTransferSpeed
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import com.infinitezerone.minibgm.feature.user.components.AiSettingsDialog
import com.infinitezerone.minibgm.feature.user.components.AiringTimingBottomSheet
import com.infinitezerone.minibgm.feature.user.components.DanmakuSettingsDialog
import com.infinitezerone.minibgm.feature.user.components.DeveloperOptionsBottomSheet
import com.infinitezerone.minibgm.feature.user.util.BackgroundKeepAliveHelper
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
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
    onDownloadsClick: (() -> Unit)? = null,
    enableAiConfig: Boolean = true,
    onClearCache: suspend () -> Unit = {},
    viewModel: UserViewModel = koinViewModel(),
    torrentStreamEngine: TorrentStreamEngine = koinInject(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadState by viewModel.updateDownloadState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val overlayHostState = rememberOverlayHostState()
    val coroutineScope = rememberCoroutineScope()

    val updateProgress = (downloadState as? DownloadState.Downloading)?.progress
    // 服务端未给 Content-Length 时 isIndeterminate 为真：保持 null，不要显示一个假的 0%。
    val updatePercent = updateProgress?.takeUnless { it.isIndeterminate }?.percentage
    val updateSpeedSuffix =
        updateProgress
            ?.speedBytesPerSec
            ?.takeIf { it > 0L }
            ?.let { " · ${formatTransferSpeed(it)}" }
            .orEmpty()
    // 整条更新提示由单一 effect 驱动：
    // ① 进度流以 10Hz 节流发射，直接用 DownloadState 作 key 会让 snackbar 被每秒重建十次，
    //    按整数百分比去抖后整段下载最多重建 100 次；
    // ② 不能拆成「进度」与「终态」两个 effect——同一 SnackbarHostState 上前者被取消时的
    //    finally 会把后者刚装上的「下载完成」提示一并抹掉。
    val updateSnackbarKey =
        when (val state = downloadState) {
            is DownloadState.Downloading -> "downloading:${updatePercent ?: "unknown"}"
            is DownloadState.Completed -> "completed"
            is DownloadState.Failed -> "failed:${state.message}"
            else -> null
        }

    LaunchedEffect(updateSnackbarKey) {
        when (val state = downloadState) {
            is DownloadState.Downloading -> {
                val message =
                    if (updatePercent == null) {
                        context.getString(R.string.feature_user_update_downloading_indeterminate)
                    } else {
                        context.getString(
                            R.string.feature_user_update_downloading,
                            "$updatePercent%$updateSpeedSuffix",
                        )
                    }
                val result =
                    snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = context.getString(R.string.feature_user_update_download_cancel),
                        duration = SnackbarDuration.Indefinite,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.cancelUpdateDownload()
                    // 用 Toast 而非 snackbar 反馈：取消会让本 effect 的 key 归 null 而被取消，
                    // 其 finally 会立刻清掉刚装上的确认提示。
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.feature_user_update_download_canceled),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
            is DownloadState.Completed -> {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.feature_user_update_download_success),
                    duration = SnackbarDuration.Short,
                )
            }
            is DownloadState.Failed -> {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.feature_user_update_download_failed, state.message),
                    duration = SnackbarDuration.Long,
                )
            }
            else -> Unit
        }
    }

    val openWebUrl = { url: String ->
        context.launchWebUrl(url)
    }

    val isDebugBuild =
        remember {
            (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        }
    val developerSettings by viewModel.developerSettings.collectAsStateWithLifecycle()
    val isDeveloperModeActive = isDebugBuild || developerSettings.isDeveloperModeEnabled
    var showDeveloperOptionsSheet by remember { mutableStateOf(false) }
    var versionClickCount by remember { mutableIntStateOf(0) }
    var lastVersionClickTime by remember { mutableLongStateOf(0L) }

    val onVersionClick: () -> Unit = {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastVersionClickTime < 500L) {
            versionClickCount++
        } else {
            versionClickCount = 1
        }
        lastVersionClickTime = now

        if (!isDeveloperModeActive) {
            val remaining = 7 - versionClickCount
            if (remaining <= 0) {
                viewModel.setDeveloperModeEnabled(true)
                versionClickCount = 0
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.feature_user_dev_mode_activated),
                        duration = SnackbarDuration.Short,
                    )
                }
            } else if (versionClickCount in 3..6) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.feature_user_dev_easter_egg_step, remaining),
                        duration = SnackbarDuration.Short,
                    )
                }
            } else if (versionClickCount == 1) {
                coroutineScope.launch {
                    kotlinx.coroutines.delay(350L)
                    if (versionClickCount == 1) {
                        openWebUrl(PROJECT_GITHUB_URL)
                    }
                }
            }
        } else {
            if (versionClickCount >= 3) {
                showDeveloperOptionsSheet = true
                versionClickCount = 0
            } else if (versionClickCount == 1) {
                coroutineScope.launch {
                    kotlinx.coroutines.delay(350L)
                    if (versionClickCount == 1) {
                        openWebUrl(PROJECT_GITHUB_URL)
                    }
                }
            }
        }
    }

    var hasNotificationPermission by remember { mutableStateOf(true) }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(false) }
    var isSimulatingTorrentUninstalled by remember { mutableStateOf(false) }
    var isTorrentPluginInstalled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasNotificationPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
        isIgnoringBatteryOptimizations = BackgroundKeepAliveHelper.isIgnoringBatteryOptimizations(context)
        if (isDeveloperModeActive) {
            isSimulatingTorrentUninstalled = torrentStreamEngine.isDebugSimulateUninstalled()
            isTorrentPluginInstalled = torrentStreamEngine.isPluginInstalled()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasNotificationPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
        isIgnoringBatteryOptimizations = BackgroundKeepAliveHelper.isIgnoringBatteryOptimizations(context)
        if (isDeveloperModeActive) {
            isSimulatingTorrentUninstalled = torrentStreamEngine.isDebugSimulateUninstalled()
            coroutineScope.launch {
                isTorrentPluginInstalled = torrentStreamEngine.isPluginInstalled()
            }
        }
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
        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
        onOpenKeepAliveSettings = {
            coroutineScope.launch {
                val confirmed =
                    overlayHostState.await(
                        ConfirmDialogAction(
                            title = context.getString(R.string.feature_user_keep_alive_dialog_title),
                            message = context.getString(R.string.feature_user_keep_alive_dialog_message),
                            confirmText = context.getString(R.string.feature_user_keep_alive_action_battery),
                            dismissText = context.getString(R.string.feature_user_keep_alive_action_autostart),
                            isPrimary = true,
                            icon = BgmIcons.Bolt,
                        ),
                    )
                if (confirmed) {
                    BackgroundKeepAliveHelper.requestIgnoreBatteryOptimizations(context)
                } else {
                    BackgroundKeepAliveHelper.openAutoStartSettings(context)
                }
            }
        },
        themeMode = uiState.themeMode,
        onSelectThemeMode = viewModel::setThemeMode,
        dynamicColor = uiState.dynamicColor,
        onToggleDynamicColor = viewModel::setDynamicColor,
        showBottomAiButton = uiState.showBottomAiButton,
        onToggleShowBottomAiButton = viewModel::setShowBottomAiButton,
        pipEnabled = uiState.pipEnabled,
        onTogglePipEnabled = viewModel::setPipEnabled,
        showRestrictedContent = uiState.showRestrictedContent,
        onToggleShowRestrictedContent = viewModel::setShowRestrictedContent,
        onSaveAiConfig = viewModel::setAiConfig,
        onSaveDanmakuConfig = viewModel::setDanmakuConfig,
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
                            val hasDownloadUrl = !info.downloadUrl.isNullOrBlank()
                            val confirmed =
                                overlayHostState.await(
                                    ConfirmDialogAction(
                                        title = context.getString(R.string.feature_user_update_found_title, info.latestVersion),
                                        message = message,
                                        confirmText =
                                            if (hasDownloadUrl) {
                                                context.getString(R.string.feature_user_action_download_update)
                                            } else {
                                                context.getString(R.string.feature_user_action_go_release)
                                            },
                                        dismissText = context.getString(R.string.feature_user_action_update_later),
                                        isPrimary = true,
                                        icon = if (hasDownloadUrl) BgmIcons.Download else BgmIcons.OpenInNew,
                                    ),
                                )
                            if (confirmed) {
                                if (hasDownloadUrl) {
                                    val downloadUrl = info.downloadUrl.orEmpty()
                                    if (!viewModel.canRequestPackageInstalls()) {
                                        val grantConfirmed =
                                            overlayHostState.await(
                                                ConfirmDialogAction(
                                                    title = context.getString(R.string.feature_user_update_permission_title),
                                                    message = context.getString(R.string.feature_user_update_permission_message),
                                                    confirmText = context.getString(R.string.feature_user_action_grant_install_permission),
                                                    dismissText = context.getString(DesignSystemR.string.core_designsystem_action_cancel),
                                                    isPrimary = true,
                                                    icon = BgmIcons.Settings,
                                                ),
                                            )
                                        if (grantConfirmed) {
                                            viewModel.openInstallPermissionSettings()
                                        }
                                    }
                                    viewModel.downloadAndInstallUpdate(
                                        downloadUrl = downloadUrl,
                                        targetVersion = info.latestVersion,
                                    )
                                } else {
                                    val targetUrl =
                                        info.releaseUrl.ifBlank {
                                            "https://github.com/infinitezerone/MiniBgm/releases/latest"
                                        }
                                    openWebUrl(targetUrl)
                                }
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
        onDownloadsClick = onDownloadsClick,
        enableAiConfig = enableAiConfig,
        isDeveloperModeActive = isDeveloperModeActive,
        onOpenDeveloperOptions = { showDeveloperOptionsSheet = true },
        onVersionClick = onVersionClick,
        snackbarHostState = snackbarHostState,
        overlayHostState = overlayHostState,
        modifier = modifier,
    )

    if (showDeveloperOptionsSheet) {
        DeveloperOptionsBottomSheet(
            developerSettings = developerSettings,
            onOverrideAi = viewModel::setDevOverrideEnableAi,
            onOverridePlayer = viewModel::setDevOverrideEnablePlayer,
            onToggleVerboseLogging = viewModel::setDevVerboseLogging,
            isSimulatingTorrentUninstalled = isSimulatingTorrentUninstalled,
            isTorrentPluginInstalled = isTorrentPluginInstalled,
            onToggleSimulateTorrentUninstalled = { enabled ->
                coroutineScope.launch {
                    torrentStreamEngine.setDebugSimulateUninstalled(enabled)
                    viewModel.setDevSimulateTorrentUninstalled(enabled)
                    isSimulatingTorrentUninstalled = torrentStreamEngine.isDebugSimulateUninstalled()
                    isTorrentPluginInstalled = torrentStreamEngine.isPluginInstalled()
                    val toastRes =
                        if (enabled) {
                            R.string.feature_user_dev_torrent_simulated_uninstalled_toast
                        } else {
                            R.string.feature_user_dev_torrent_restored_default_toast
                        }
                    snackbarHostState.showSnackbar(
                        message = context.getString(toastRes),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            onResetTorrentPlugin = {
                coroutineScope.launch {
                    torrentStreamEngine.setDebugSimulateUninstalled(true)
                    viewModel.setDevSimulateTorrentUninstalled(true)
                    isSimulatingTorrentUninstalled = torrentStreamEngine.isDebugSimulateUninstalled()
                    isTorrentPluginInstalled = torrentStreamEngine.isPluginInstalled()
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.feature_user_dev_torrent_simulated_uninstalled_toast),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            onCustomMirrorPrefixChange = viewModel::setDevCustomMirrorPrefix,
            onResetAllOverrides = {
                viewModel.resetAllDevOverrides()
                coroutineScope.launch {
                    torrentStreamEngine.setDebugSimulateUninstalled(false)
                    isSimulatingTorrentUninstalled = false
                    isTorrentPluginInstalled = torrentStreamEngine.isPluginInstalled()
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.feature_user_dev_action_reset_overrides),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            onDisableDeveloperMode = {
                viewModel.setDeveloperModeEnabled(false)
                viewModel.resetAllDevOverrides()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.feature_user_dev_mode_disabled),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            onOpenCrashLog = {
                coroutineScope.launch {
                    crashLogText = viewModel.loadLatestCrashLog()?.content.orEmpty()
                    if (crashLogText.isBlank()) {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.feature_user_dev_crash_log_none),
                            duration = SnackbarDuration.Short,
                        )
                    } else {
                        showCrashLogDialog = true
                    }
                }
            },
            onDismiss = { showDeveloperOptionsSheet = false },
            onShowToast = { msg ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(msg)
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
    isIgnoringBatteryOptimizations: Boolean = false,
    onOpenKeepAliveSettings: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onSelectThemeMode: (ThemeMode) -> Unit = {},
    dynamicColor: Boolean = false,
    onToggleDynamicColor: (Boolean) -> Unit = {},
    showBottomAiButton: Boolean = true,
    onToggleShowBottomAiButton: ((Boolean) -> Unit)? = null,
    pipEnabled: Boolean = true,
    onTogglePipEnabled: (Boolean) -> Unit = {},
    showRestrictedContent: Boolean = false,
    onToggleShowRestrictedContent: (Boolean) -> Unit = {},
    onSaveAiConfig: (AiConfig) -> Unit,
    onSaveDanmakuConfig: (DanmakuConfig) -> Unit = {},
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
    onDownloadsClick: (() -> Unit)? = null,
    enableAiConfig: Boolean = true,
    isDeveloperModeActive: Boolean = false,
    onOpenDeveloperOptions: () -> Unit = {},
    onVersionClick: () -> Unit = {},
    overlayHostState: OverlayHostState = rememberOverlayHostState(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showTimingBottomSheet by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }
    var showDanmakuSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BgmTopAppBar(
                mode = BgmTopBarMode.Solid,
                contentMaxWidth = 840.dp,
                title = { Text(text = stringResource(R.string.feature_user_title_settings)) },
                navigationIcon = {
                    BgmCircleActionButton(
                        onClick = onBackClick,
                        icon = BgmIcons.ArrowBack,
                        contentDescription = stringResource(DesignSystemR.string.core_designsystem_action_back),
                    )
                },
            )
        },
        snackbarHost = { BgmSnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "settings_appearance") {
                    AppearanceSettingsCard(
                        themeMode = themeMode,
                        onSelectThemeMode = onSelectThemeMode,
                        dynamicColor = dynamicColor,
                        onToggleDynamicColor = onToggleDynamicColor,
                        showBottomAiButton = showBottomAiButton,
                        onToggleShowBottomAiButton = onToggleShowBottomAiButton,
                    )
                }

                item(key = "settings_playback_content") {
                    PlaybackAndContentSettingsCard(
                        pipEnabled = pipEnabled,
                        onTogglePipEnabled = onTogglePipEnabled,
                        showRestrictedContent = showRestrictedContent,
                        onToggleShowRestrictedContent = onToggleShowRestrictedContent,
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
                        onOpenDanmakuSettings = { showDanmakuSettingsDialog = true },
                        danmakuSummary =
                            if (uiState.danmakuConfig.endpoint.isNotBlank()) {
                                uiState.danmakuConfig.endpoint
                            } else if (uiState.danmakuConfig.appId.isNotBlank()) {
                                stringResource(R.string.feature_user_settings_danmaku_desc_configured)
                            } else {
                                null
                            },
                    )
                }

                item(key = "settings_reminder") {
                    ReminderSettingsCard(
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
                        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                        onOpenKeepAliveSettings = onOpenKeepAliveSettings,
                    )
                }

                item(key = "settings_data_storage") {
                    DataAndStorageSettingsCard(
                        aiConfig = uiState.aiConfig,
                        onOpenAiSettingsDialog =
                            if (enableAiConfig) {
                                { showAiSettingsDialog = true }
                            } else {
                                null
                            },
                        onClearCache = onClearCache,
                        isClearingCache = isClearingCache,
                        onOpenDownloads = onDownloadsClick,
                    )
                }

                if (isDeveloperModeActive) {
                    item(key = "settings_developer_entry") {
                        DeveloperEntrySettingsCard(
                            onOpenDeveloperOptions = onOpenDeveloperOptions,
                        )
                    }
                }

                item(key = "settings_about") {
                    AboutSettingsCard(
                        onOpenWebUrl = onOpenWebUrl,
                        isCheckingUpdate = uiState.isCheckingUpdate,
                        onCheckForUpdate = onCheckForUpdate,
                        onVersionClick = onVersionClick,
                    )
                }

                if (uiState.isLoggedIn) {
                    item(key = "settings_account") {
                        AccountSettingsCard(
                            activeProfile = uiState.activeProfile,
                            savedAccountsCount = uiState.savedAccounts.size,
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
        }
    }

    if (showAiSettingsDialog) {
        AiSettingsDialog(
            currentConfig = uiState.aiConfig,
            onSaveConfig = onSaveAiConfig,
            onDismiss = { showAiSettingsDialog = false },
        )
    }

    if (showDanmakuSettingsDialog) {
        DanmakuSettingsDialog(
            currentConfig = uiState.danmakuConfig,
            onSaveConfig = onSaveDanmakuConfig,
            onDismiss = { showDanmakuSettingsDialog = false },
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
            pipEnabled = true,
            onTogglePipEnabled = {},
            onPlaybackRulesClick = {},
            onSaveAiConfig = {},
            onSaveDanmakuConfig = {},
            onOpenWebUrl = {},
            onClearCache = {},
            onLogoutCurrent = {},
            onLogoutAll = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
