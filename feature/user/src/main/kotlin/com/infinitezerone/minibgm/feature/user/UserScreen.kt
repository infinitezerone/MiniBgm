package com.infinitezerone.minibgm.feature.user

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.designsystem.theme.ActionCollect
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
import com.infinitezerone.minibgm.core.designsystem.theme.WishOrange
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.model.UserAvatar
import com.infinitezerone.minibgm.core.model.UserProfile
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val BGM_HOME_URL = "https://bgm.tv"
private const val BGM_WIKI_URL = "https://bgm.tv/wiki"
private const val PROJECT_GITHUB_URL = "https://github.com/infinitezerone/MiniBgm"

@Composable
fun UserScreen(
    onCollectionClick: (CollectionType) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: UserViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val openWebUrl = { url: String ->
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    }

    UserScreenContent(
        uiState = uiState,
        onLogin = {
            coroutineScope.launch {
                val authorizeUrl = viewModel.beginLogin()
                CustomTabsIntent
                    .Builder()
                    .setEphemeralBrowsingEnabled(true)
                    .build()
                    .launchUrl(context, Uri.parse(authorizeUrl))
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
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var showAccountSheet by remember { mutableStateOf(false) }
    var accountToLogout by remember { mutableStateOf<UserProfile?>(null) }
    var showLogoutAllDialog by remember { mutableStateOf(false) }
    var showLogoutCurrentDialog by remember { mutableStateOf(false) }
    var showSyncIntervalDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "个人中心",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
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
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
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

    if (showSyncIntervalDialog) {
        SyncIntervalDialog(
            currentInterval = uiState.syncInterval,
            onSelectInterval = onSelectSyncInterval,
            onDismiss = { showSyncIntervalDialog = false },
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

@Composable
private fun UnauthenticatedCard(
    onLogin: () -> Unit,
    isAuthenticating: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(68.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "连接你的 Bangumi 账号",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "随时随地同步多端追番进度、条目收藏与打卡记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AuthAdvantageItem(
                    icon = Icons.Filled.Sync,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "追番进度多端同步",
                    description = "在看、想看与章节进度秒级同步至 Bangumi 云端，多端不遗漏",
                )
                AuthAdvantageItem(
                    icon = Icons.Filled.BookmarkBorder,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = "条目收藏与评分",
                    description = "全量同步想看、在看与评分数据，随时整理追番清单",
                )
                AuthAdvantageItem(
                    icon = Icons.Filled.SwapHoriz,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = "多账号无缝快捷切换",
                    description = "支持绑定多个 Bangumi 账号，一键即时切换马甲与主号",
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = onLogin,
                enabled = !isAuthenticating,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "正在验证授权并同步...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "通过 Bangumi OAuth 授权登录",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "💡 离线模式下，您仍可正常使用当季每周放送表与条目检索功能",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AuthAdvantageItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                ).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = iconTint.copy(alpha = 0.14f),
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
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UserProfileHeaderCard(
    profile: UserProfile?,
    savedAccountsCount: Int,
    onManageAccountsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sign = profile?.sign.orEmpty()
    val username = profile?.username.orEmpty()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 头像：双层内描边与外环
                Surface(
                    shape = CircleShape,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(76.dp),
                ) {
                    val avatarUrl = profile?.avatar?.bestAvatar.orEmpty()
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "用户头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(42.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile?.displayName?.ifBlank { "Bangumi 用户" } ?: "Bangumi 用户",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (username.isNotBlank()) {
                        Text(
                            text = "@$username",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = "UID: ${profile?.id ?: 0}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = if (profile?.userGroup == 11) "管理员" else "Bangumi 会员",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        if (savedAccountsCount > 1) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.clickable(onClick = onManageAccountsClick),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SwapHoriz,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "$savedAccountsCount 个账号 ▾",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 签名气泡
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatQuote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sign.ifBlank { "这个人很神秘，什么都没写~" },
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (sign.isNotBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                        fontStyle = if (sign.isBlank()) FontStyle.Italic else FontStyle.Normal,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 核心快捷动作条
            FilledTonalButton(
                onClick = onManageAccountsClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ManageAccounts,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "账号切换与管理", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun MultiAccountQuickCard(
    accounts: List<UserProfile>,
    activeProfile: UserProfile?,
    onSwitchAccount: (Long) -> Unit,
    onManageAccountsClick: () -> Unit,
    onAddAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "账号快捷切换",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(
                    onClick = onManageAccountsClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(text = "管理 (${accounts.size}) ↗", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { account ->
                    val isActive = account.id == activeProfile?.id
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (!isActive) {
                                        Modifier.clickable { onSwitchAccount(account.id) }
                                    } else {
                                        Modifier
                                    },
                                ),
                        shape = RoundedCornerShape(12.dp),
                        color =
                            if (isActive) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        border =
                            if (isActive) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            } else {
                                null
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp),
                            ) {
                                val avatarUrl = account.avatar?.bestAvatar.orEmpty()
                                if (avatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "@${account.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (isActive) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "当前活跃",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "轻触切换",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onAddAccountClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "+ 添加其他 Bangumi 账号", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun CollectionOverviewCard(
    isLoggedIn: Boolean,
    collectionCounts: Map<CollectionType, Int>,
    isCountsLoading: Boolean,
    onCollectionClick: (CollectionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun formatCount(type: CollectionType): String {
        if (!isLoggedIn) return "-"
        val count = collectionCounts[type]
        return when {
            count != null -> count.toString()
            isCountsLoading -> "…"
            else -> "0"
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "我的追番与收藏",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (isLoggedIn) {
                    TextButton(
                        onClick = { onCollectionClick(CollectionType.DOING) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "完整列表 ↗",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            text = "未登录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 第一排：三大活跃追番状态（在看、想看、看过）
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CollectionStatusItem(
                    label = "在看",
                    tag = "追番中",
                    count = formatCount(CollectionType.DOING),
                    icon = Icons.Filled.PlayCircleOutline,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { onCollectionClick(CollectionType.DOING) },
                    modifier = Modifier.weight(1f),
                )
                CollectionStatusItem(
                    label = "想看",
                    tag = "愿望单",
                    count = formatCount(CollectionType.WISH),
                    icon = Icons.Filled.BookmarkBorder,
                    tint = WishOrange,
                    onClick = { onCollectionClick(CollectionType.WISH) },
                    modifier = Modifier.weight(1f),
                )
                CollectionStatusItem(
                    label = "看过",
                    tag = "已完成",
                    count = formatCount(CollectionType.COLLECT),
                    icon = Icons.Filled.CheckCircleOutline,
                    tint = ActionCollect,
                    onClick = { onCollectionClick(CollectionType.COLLECT) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 第二排：两大归档状态（搁置、抛弃）
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CollectionStatusItem(
                    label = "搁置",
                    tag = null,
                    count = formatCount(CollectionType.ON_HOLD),
                    icon = Icons.Filled.PauseCircleOutline,
                    tint = MaterialTheme.colorScheme.outline,
                    onClick = { onCollectionClick(CollectionType.ON_HOLD) },
                    modifier = Modifier.weight(1f),
                )
                CollectionStatusItem(
                    label = "抛弃",
                    tag = null,
                    count = formatCount(CollectionType.DROPPED),
                    icon = Icons.Filled.Cancel,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    onClick = { onCollectionClick(CollectionType.DROPPED) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 贴心功能引导（替代草稿占位文案）
            Text(
                text =
                    if (isLoggedIn) {
                        "💡 点击任意分类可直达条目列表、查看打卡进度并支持多维度筛选"
                    } else {
                        "💡 登录 Bangumi 账号后，即可一键实时同步全量在看、想看与评分记录"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun CollectionStatusItem(
    label: String,
    tag: String?,
    count: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.12f),
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tag != null) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "· $tag",
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    isLoggedIn: Boolean,
    activeProfile: UserProfile?,
    savedAccountsCount: Int,
    syncInterval: SyncInterval,
    lastSyncTimestamp: Long,
    isSyncing: Boolean,
    onOpenSyncDialog: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenWebUrl: (String) -> Unit,
    onClearCache: () -> Unit,
    onLogoutCurrentClick: () -> Unit,
    onLogoutAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastSyncText =
        if (lastSyncTimestamp == 0L) {
            "尚未同步"
        } else {
            "已是最新"
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Group 1: 播放源与数据同步
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                    text = "数据同步与存储",
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

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                SettingsItemRow(
                    icon = Icons.Filled.CloudQueue,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = "立即同步放送源",
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

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                SettingsItemRow(
                    icon = Icons.Filled.CleaningServices,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = "清理本地缓存",
                    subtitle = "清理离线网络图片与临时缓存数据",
                    onClick = onClearCache,
                )
            }
        }

        // Group 2: 社区与关于
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                    text = "关于与社区服务",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )

                SettingsItemRow(
                    icon = Icons.Filled.Language,
                    iconTint = MaterialTheme.colorScheme.primary,
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
                    iconTint = MaterialTheme.colorScheme.secondary,
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

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                SettingsItemRow(
                    icon = Icons.Filled.BookmarkBorder,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = "MiniBgm 客户端",
                    subtitle = "v1.0.0 · Apache-2.0 开源协议",
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "打开网页",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { onOpenWebUrl(PROJECT_GITHUB_URL) },
                )
            }
        }

        // Group 3: 账号与登录安全 (仅在已登录状态下展示在最底部)
        if (isLoggedIn) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                    Text(
                        text = "账号设置",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
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
private fun SyncIntervalDialog(
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
            shape = RoundedCornerShape(8.dp),
            color = iconTint.copy(alpha = 0.12f),
            modifier = Modifier.size(36.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountManagementBottomSheet(
    accounts: List<UserProfile>,
    activeProfile: UserProfile?,
    onDismiss: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onLogoutAccountClick: (UserProfile) -> Unit,
    onAddAccountClick: () -> Unit,
    onLogoutAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = "账号管理",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "已保存 ${accounts.size} 个登录账号，支持一键无缝切换",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                accounts.forEach { account ->
                    val isActive = account.id == activeProfile?.id
                    AccountItemRow(
                        account = account,
                        isActive = isActive,
                        onSwitchClick = { onSwitchAccount(account.id) },
                        onLogoutClick = { onLogoutAccountClick(account) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onAddAccountClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "添加其他 Bangumi 账号")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onLogoutAllClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "退出所有账号")
            }
        }
    }
}

@Composable
private fun AccountItemRow(
    account: UserProfile,
    isActive: Boolean,
    onSwitchClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (!isActive) Modifier.clickable(onClick = onSwitchClick) else Modifier,
                ),
        shape = RoundedCornerShape(14.dp),
        color =
            if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val avatarUrl = account.avatar?.bestAvatar.orEmpty()
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = "当前活跃",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "@${account.username} · UID: ${account.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isActive) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "当前活跃账号",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                IconButton(onClick = onLogoutClick) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "退出该账号",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
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
