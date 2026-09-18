package com.infinitezerone.minibgm.feature.user

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    viewModel: UserViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val openWebUrl = { url: String ->
        context.launchWebUrl(url)
    }

    // 开启提醒时顺带请求通知权限（Android 13+；拒绝仅影响送达，不影响开关本身）
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val toggleAiringReminder: (Boolean) -> Unit = { enabled ->
        viewModel.setAiringReminderEnabled(enabled)
        if (enabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
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
                        snackbarHostState.showSnackbar("播放源已是最新状态 ✨")
                    } else {
                        snackbarHostState.showSnackbar("同步失败，请检查网络设置")
                    }
                }
            }
        },
        onToggleAiringReminder = toggleAiringReminder,
        airingReminderHour = uiState.airingReminderHour,
        onSelectReminderHour = viewModel::setAiringReminderHour,
        airDelayOffsetMinutes = uiState.airDelayOffsetMinutes,
        onSelectDelayOffsetMinutes = viewModel::setAirDelayOffsetMinutes,
        onSaveAiConfig = viewModel::setAiConfig,
        onOpenWebUrl = openWebUrl,
        onClearCache = {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("本地缓存与临时数据已清理 ✨")
            }
        },
        onLogoutCurrent = viewModel::logout,
        onLogoutAll = viewModel::logoutAll,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    uiState: UserUiState,
    onBackClick: () -> Unit,
    onSelectSyncInterval: (SyncInterval) -> Unit,
    onSyncNow: () -> Unit,
    onToggleAiringReminder: (Boolean) -> Unit,
    airingReminderHour: Int,
    onSelectReminderHour: (Int) -> Unit,
    airDelayOffsetMinutes: Int,
    onSelectDelayOffsetMinutes: (Int) -> Unit,
    onSaveAiConfig: (AiConfig) -> Unit,
    onOpenWebUrl: (String) -> Unit,
    onClearCache: () -> Unit,
    onLogoutCurrent: () -> Unit,
    onLogoutAll: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var showLogoutAllDialog by remember { mutableStateOf(false) }
    var showLogoutCurrentDialog by remember { mutableStateOf(false) }
    var showSyncIntervalDialog by remember { mutableStateOf(false) }
    var showReminderHourDialog by remember { mutableStateOf(false) }
    var showDelayOffsetDialog by remember { mutableStateOf(false) }
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                    airingReminderHour = airingReminderHour,
                    aiConfig = uiState.aiConfig,
                    onOpenAiSettingsDialog = { showAiSettingsDialog = true },
                    onOpenReminderHourDialog = { showReminderHourDialog = true },
                    airDelayOffsetMinutes = airDelayOffsetMinutes,
                    onOpenDelayOffsetDialog = { showDelayOffsetDialog = true },
                    onOpenSyncDialog = { showSyncIntervalDialog = true },
                    onSyncNow = onSyncNow,
                    onOpenWebUrl = onOpenWebUrl,
                    onClearCache = onClearCache,
                    onLogoutCurrentClick = { showLogoutCurrentDialog = true },
                    onLogoutAllClick = { showLogoutAllDialog = true },
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

    if (showDelayOffsetDialog) {
        DelayOffsetDialog(
            currentOffset = airDelayOffsetMinutes,
            onSelectOffset = onSelectDelayOffsetMinutes,
            onDismiss = { showDelayOffsetDialog = false },
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
                            "确定要退出当前账号「${currentProfile.displayName}」(@${currentProfile.username}) 吗？"
                        } else {
                            "确定要退出当前登录账号吗？"
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
                    text = "确定要退出设备上保存的全部 ${uiState.savedAccounts.size} 个账号吗？所有已保存的授权凭据都将被清除。",
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
            airDelayOffsetMinutes = 0,
            onSelectDelayOffsetMinutes = {},
            onSaveAiConfig = {},
            onOpenWebUrl = {},
            onClearCache = {},
            onLogoutCurrent = {},
            onLogoutAll = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
