package com.infinitezerone.minibgm.feature.user

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.infinitezerone.minibgm.core.designsystem.theme.LocalWindowAdaptiveInfo
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.model.UserAvatar
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun UserScreen(
    onCollectionClick: (CollectionType) -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
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

    UserScreenContent(
        uiState = uiState,
        onLogin = {
            coroutineScope.launch {
                val authorizeUrl = viewModel.beginLogin()
                context.launchWebUrl(authorizeUrl, isAuth = true)
            }
        },
        onRefresh = {
            viewModel.refresh { success ->
                coroutineScope.launch {
                    if (success) {
                        snackbarHostState.showSnackbar(
                            if (uiState.isLoggedIn) "个人中心已刷新 ✨" else "已刷新（登录后可同步个人云端数据）",
                        )
                    } else {
                        snackbarHostState.showSnackbar("刷新失败，请检查网络设置")
                    }
                }
            }
        },
        onSwitchAccount = viewModel::switchAccount,
        onLogoutCurrent = viewModel::logout,
        onLogoutAccount = viewModel::logout,
        onLogoutAll = viewModel::logoutAll,
        onOpenWebUrl = openWebUrl,
        onClearCache = {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("本地缓存与临时数据已清理 ✨")
            }
        },
        onCollectionClick = onCollectionClick,
        onSelectSyncInterval = viewModel::setSyncInterval,
        onSyncNow = {
            viewModel.syncBangumiDataNow { success ->
                coroutineScope.launch {
                    if (success) {
                        snackbarHostState.showSnackbar("播放源已是最新状态 ✨")
                    } else {
                        snackbarHostState.showSnackbar("播放源同步失败，请检查网络")
                    }
                }
            }
        },
        onToggleAiringReminder = toggleAiringReminder,
        airingReminderHour = uiState.airingReminderHour,
        onSelectReminderHour = viewModel::setAiringReminderHour,
        scrollToTop = scrollToTop,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreenContent(
    uiState: UserUiState,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onLogoutCurrent: () -> Unit,
    onLogoutAccount: (Long) -> Unit,
    onLogoutAll: () -> Unit,
    onOpenWebUrl: (String) -> Unit,
    onClearCache: () -> Unit,
    onCollectionClick: (CollectionType) -> Unit,
    onSelectSyncInterval: (SyncInterval) -> Unit,
    onSyncNow: () -> Unit,
    onToggleAiringReminder: (Boolean) -> Unit = {},
    airingReminderHour: Int = 8,
    onSelectReminderHour: (Int) -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var showAccountSheet by remember { mutableStateOf(false) }
    var accountToLogout by remember { mutableStateOf<UserProfile?>(null) }
    var showLogoutAllDialog by remember { mutableStateOf(false) }
    var showLogoutCurrentDialog by remember { mutableStateOf(false) }
    var showSyncIntervalDialog by remember { mutableStateOf(false) }
    var showReminderHourDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(scrollToTop) {
        scrollToTop?.collect {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = {
                    Text(
                        text = "个人中心",
                    )
                },
                actions = {
                    if (uiState.isLoggedIn) {
                        IconButton(onClick = { showAccountSheet = true }) {
                            if (uiState.savedAccounts.size > 1) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(text = "${uiState.savedAccounts.size}")
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ManageAccounts,
                                        contentDescription = "账号管理",
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.ManageAccounts,
                                    contentDescription = "账号管理",
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            val adaptiveInfo = LocalWindowAdaptiveInfo.current
            val isWideScreen = adaptiveInfo.isWide

            if (isWideScreen) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // 左栏：个人资料概览、多账号管理与五维收藏分布
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .weight(0.45f)
                                .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (!uiState.isLoggedIn) {
                            item(key = "wide_unauthenticated_card") {
                                UnauthenticatedCard(
                                    onLogin = onLogin,
                                    isAuthenticating = uiState.isAuthenticating,
                                )
                            }
                        } else {
                            item(key = "wide_profile_header") {
                                UserProfileHeaderCard(
                                    profile = uiState.activeProfile,
                                    savedAccountsCount = uiState.savedAccounts.size,
                                    onManageAccountsClick = { showAccountSheet = true },
                                )
                            }

                            if (uiState.savedAccounts.size > 1) {
                                item(key = "wide_multi_account_card") {
                                    MultiAccountQuickCard(
                                        accounts = uiState.savedAccounts,
                                        activeProfile = uiState.activeProfile,
                                        onSwitchAccount = onSwitchAccount,
                                        onManageAccountsClick = { showAccountSheet = true },
                                        onAddAccountClick = {
                                            showAccountSheet = false
                                            onLogin()
                                        },
                                    )
                                }
                            }

                            item(key = "wide_collections_overview") {
                                CollectionOverviewCard(
                                    isLoggedIn = true,
                                    collectionCounts = uiState.collectionCounts,
                                    isCountsLoading = uiState.isCountsLoading,
                                    onCollectionClick = onCollectionClick,
                                )
                            }
                        }

                        if (!uiState.isLoggedIn) {
                            item(key = "wide_collections_overview_placeholder") {
                                CollectionOverviewCard(
                                    isLoggedIn = false,
                                    collectionCounts = emptyMap(),
                                    isCountsLoading = false,
                                    onCollectionClick = onCollectionClick,
                                )
                            }
                        }
                    }

                    // 右栏：同步设置、提醒、缓存与系统信息
                    LazyColumn(
                        modifier =
                            Modifier
                                .weight(0.55f)
                                .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item(key = "wide_settings_and_about") {
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
                                onOpenReminderHourDialog = { showReminderHourDialog = true },
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
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (!uiState.isLoggedIn) {
                        item(key = "unauthenticated_card") {
                            UnauthenticatedCard(
                                onLogin = onLogin,
                                isAuthenticating = uiState.isAuthenticating,
                            )
                        }
                    } else {
                        item(key = "profile_header") {
                            UserProfileHeaderCard(
                                profile = uiState.activeProfile,
                                savedAccountsCount = uiState.savedAccounts.size,
                                onManageAccountsClick = { showAccountSheet = true },
                            )
                        }

                        if (uiState.savedAccounts.size > 1) {
                            item(key = "multi_account_card") {
                                MultiAccountQuickCard(
                                    accounts = uiState.savedAccounts,
                                    activeProfile = uiState.activeProfile,
                                    onSwitchAccount = onSwitchAccount,
                                    onManageAccountsClick = { showAccountSheet = true },
                                    onAddAccountClick = {
                                        showAccountSheet = false
                                        onLogin()
                                    },
                                )
                            }
                        }

                        item(key = "collections_overview") {
                            CollectionOverviewCard(
                                isLoggedIn = true,
                                collectionCounts = uiState.collectionCounts,
                                isCountsLoading = uiState.isCountsLoading,
                                onCollectionClick = onCollectionClick,
                            )
                        }
                    }

                    if (!uiState.isLoggedIn) {
                        item(key = "collections_overview_placeholder") {
                            CollectionOverviewCard(
                                isLoggedIn = false,
                                collectionCounts = emptyMap(),
                                isCountsLoading = false,
                                onCollectionClick = onCollectionClick,
                            )
                        }
                    }

                    item(key = "settings_and_about") {
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
                            onOpenReminderHourDialog = { showReminderHourDialog = true },
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
        }
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

    if (showAccountSheet) {
        AccountManagementBottomSheet(
            accounts = uiState.savedAccounts,
            activeProfile = uiState.activeProfile,
            onDismiss = { showAccountSheet = false },
            onSwitchAccount = { userId ->
                onSwitchAccount(userId)
                showAccountSheet = false
            },
            onLogoutAccountClick = { profile ->
                accountToLogout = profile
            },
            onAddAccountClick = {
                showAccountSheet = false
                onLogin()
            },
            onLogoutAllClick = {
                showLogoutAllDialog = true
            },
        )
    }

    accountToLogout?.let { profile ->
        AlertDialog(
            onDismissRequest = { accountToLogout = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(text = "退出账号") },
            text = {
                Text(
                    text = "确定要退出账号「${profile.displayName}」(@${profile.username}) 吗？退出后本地保存的该账号凭据将被清除。",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onLogoutAccount(profile.id)
                        accountToLogout = null
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    Text(text = "退出该账号")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToLogout = null }) {
                    Text(text = "取消")
                }
            },
        )
    }

    if (showLogoutCurrentDialog) {
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
                val currentDisplayName =
                    uiState.activeProfile
                        ?.displayName
                        .orEmpty()
                        .ifBlank { "当前账号" }
                Text(
                    text = "确定要退出当前登录的账号「$currentDisplayName」吗？",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onLogoutCurrent()
                        showLogoutCurrentDialog = false
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    Text(text = "确认退出")
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
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(text = "退出所有账号") },
            text = {
                Text(
                    text = "确定要退出全部已登录的 Bangumi 账号吗？设备上的登录状态与本地缓存将被清除。",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onLogoutAll()
                        showLogoutAllDialog = false
                        showAccountSheet = false
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    Text(text = "退出所有账号")
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

// ---------------- Components ----------------

// ---------------- Previews ----------------

private val previewProfile1 =
    UserProfile(
        id = 123456L,
        username = "bgm_master",
        nickname = "零一",
        userGroup = 1,
        avatar = UserAvatar(large = "", medium = "", small = ""),
        sign = "探索二次元与科技的边界 ✨",
    )

private val previewProfile2 =
    UserProfile(
        id = 654321L,
        username = "anime_lover",
        nickname = "马甲二号",
        userGroup = 1,
        avatar = UserAvatar(large = "", medium = "", small = ""),
        sign = "补番进行中...",
    )

@ThemePreviews
@Composable
private fun UserScreenUnauthenticatedPreview() {
    MiniBgmTheme {
        UserScreenContent(
            uiState = UserUiState(isLoggedIn = false),
            onLogin = {},
            onRefresh = {},
            onSwitchAccount = {},
            onLogoutCurrent = {},
            onLogoutAccount = {},
            onLogoutAll = {},
            onOpenWebUrl = {},
            onClearCache = {},
            onCollectionClick = {},
            onSelectSyncInterval = {},
            onSyncNow = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@ThemePreviews
@Composable
private fun UserScreenSingleAccountPreview() {
    MiniBgmTheme {
        UserScreenContent(
            uiState =
                UserUiState(
                    isLoggedIn = true,
                    activeProfile = previewProfile1,
                    savedAccounts = listOf(previewProfile1),
                    collectionCounts =
                        mapOf(
                            CollectionType.DOING to 8,
                            CollectionType.WISH to 24,
                            CollectionType.COLLECT to 142,
                            CollectionType.ON_HOLD to 3,
                            CollectionType.DROPPED to 1,
                        ),
                ),
            onLogin = {},
            onRefresh = {},
            onSwitchAccount = {},
            onLogoutCurrent = {},
            onLogoutAccount = {},
            onLogoutAll = {},
            onOpenWebUrl = {},
            onClearCache = {},
            onCollectionClick = {},
            onSelectSyncInterval = {},
            onSyncNow = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@ThemePreviews
@Composable
private fun UserScreenMultiAccountPreview() {
    MiniBgmTheme {
        UserScreenContent(
            uiState =
                UserUiState(
                    isLoggedIn = true,
                    activeProfile = previewProfile1,
                    savedAccounts = listOf(previewProfile1, previewProfile2),
                    collectionCounts =
                        mapOf(
                            CollectionType.DOING to 8,
                            CollectionType.WISH to 24,
                            CollectionType.COLLECT to 142,
                        ),
                ),
            onLogin = {},
            onRefresh = {},
            onSwitchAccount = {},
            onLogoutCurrent = {},
            onLogoutAccount = {},
            onLogoutAll = {},
            onOpenWebUrl = {},
            onClearCache = {},
            onCollectionClick = {},
            onSelectSyncInterval = {},
            onSyncNow = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
