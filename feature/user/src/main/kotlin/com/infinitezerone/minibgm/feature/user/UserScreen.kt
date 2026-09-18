package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.outlined.Settings
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
import com.infinitezerone.minibgm.core.model.UserAvatar
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun UserScreen(
    onCollectionClick: (CollectionType) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
    modifier: Modifier = Modifier,
    viewModel: UserViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
        onSettingsClick = onSettingsClick,
        onSwitchAccount = viewModel::switchAccount,
        onLogoutAccount = viewModel::logout,
        onLogoutAll = viewModel::logoutAll,
        onCollectionClick = onCollectionClick,
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
    onSettingsClick: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onLogoutAccount: (Long) -> Unit,
    onLogoutAll: () -> Unit,
    onCollectionClick: (CollectionType) -> Unit,
    scrollToTop: Flow<Unit>? = null,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var showAccountSheet by remember { mutableStateOf(false) }
    var accountToLogout by remember { mutableStateOf<UserProfile?>(null) }
    var showLogoutAllDialog by remember { mutableStateOf(false) }

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
                    Text(text = "个人中心")
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

                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "设置",
                        )
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

            if (!uiState.isLoggedIn) {
                // 未登录状态：全屏沉浸式登录引导区，干净聚焦无冗余
                UnauthenticatedLandingView(
                    onLogin = onLogin,
                    isAuthenticating = uiState.isAuthenticating,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (isWideScreen) {
                // 已登录宽屏双栏模式
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // 左栏：个人资料概览与多账号管理
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .weight(0.45f)
                                .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
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
                    }

                    // 右栏：五维收藏分布全景看板
                    LazyColumn(
                        modifier =
                            Modifier
                                .weight(0.55f)
                                .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item(key = "wide_collections_overview") {
                            CollectionOverviewCard(
                                isLoggedIn = true,
                                collectionCounts = uiState.collectionCounts,
                                isCountsLoading = uiState.isCountsLoading,
                                onCollectionClick = onCollectionClick,
                                onLogin = onLogin,
                            )
                        }
                    }
                }
            } else {
                // 已登录单栏竖屏模式
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
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
                            onLogin = onLogin,
                        )
                    }
                }
            }
        }
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
                    text = "确定要退出设备上保存的全部 ${uiState.savedAccounts.size} 个账号吗？所有已保存的授权凭据都将被清除。",
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
            onSettingsClick = {},
            onSwitchAccount = {},
            onLogoutAccount = {},
            onLogoutAll = {},
            onCollectionClick = {},
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
            onSettingsClick = {},
            onSwitchAccount = {},
            onLogoutAccount = {},
            onLogoutAll = {},
            onCollectionClick = {},
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
            onSettingsClick = {},
            onSwitchAccount = {},
            onLogoutAccount = {},
            onLogoutAll = {},
            onCollectionClick = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
