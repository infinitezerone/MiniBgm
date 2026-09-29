package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.data.repository.TrackingFootprint
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UserAvatar
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 个人页四层结构（对齐主流社区 App 个人页与仓库内 `SubjectDetailScreen` 的既有范式）：
 * 身份头部 → 通栏数字带 → 吸顶分区 Tab → 当前分区内容流。
 */
@Composable
fun UserScreen(
    onSubjectClick: (SubjectDetailRoute) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    scrollToTop: Flow<Unit>? = null,
    modifier: Modifier = Modifier,
    viewModel: UserViewModel = koinViewModel(),
    collectionsViewModel: UserCollectionsViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val collectionsState by collectionsViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 分区默认落在「在看」，进页面即可见内容，无需用户先手动切一次 Tab
    LaunchedEffect(collectionsViewModel) {
        collectionsViewModel.setInitialType(CollectionType.DOING)
    }

    UserScreenContent(
        uiState = uiState,
        collectionsState = collectionsState,
        onLogin = {
            coroutineScope.launch {
                val authorizeUrl = viewModel.beginLogin()
                context.launchWebUrl(authorizeUrl, isAuth = true)
            }
        },
        onRefresh = {
            // 下拉刷新同时覆盖两个数据块：个人资料/收藏计数（头部与 Tab 计数）与当前分区的收藏列表
            collectionsViewModel.refresh()
            viewModel.refresh { success ->
                coroutineScope.launch {
                    if (success) {
                        snackbarHostState.showSnackbar(
                            if (uiState.isLoggedIn) "个人中心已刷新" else "已刷新（登录后可同步个人云端数据）",
                        )
                    } else {
                        snackbarHostState.showSnackbar("刷新失败，请检查网络设置")
                    }
                }
            }
        },
        onRetryCollections = collectionsViewModel::retry,
        onSettingsClick = onSettingsClick,
        onSwitchAccount = viewModel::switchAccount,
        onLogoutAccount = viewModel::logout,
        onLogoutAll = viewModel::logoutAll,
        onSelectCollectionType = collectionsViewModel::selectType,
        onSelectSubjectFilter = collectionsViewModel::selectSubjectFilter,
        onLoadMore = collectionsViewModel::loadMore,
        onIncrementProgress = collectionsViewModel::incrementEpisodeProgress,
        onSubjectClick = onSubjectClick,
        scrollToTop = scrollToTop,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UserScreenContent(
    uiState: UserUiState,
    collectionsState: UserCollectionsUiState,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onRetryCollections: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onLogoutAccount: (Long) -> Unit,
    onLogoutAll: () -> Unit,
    onSelectCollectionType: (CollectionType) -> Unit,
    onSelectSubjectFilter: (CollectionSubjectFilter) -> Unit,
    onLoadMore: (CollectionType) -> Unit,
    onIncrementProgress: (UserCollection) -> Unit,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    scrollToTop: Flow<Unit>? = null,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var showAccountSheet by remember { mutableStateOf(false) }
    var accountToLogout by remember { mutableStateOf<UserProfile?>(null) }
    var showLogoutAllDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(scrollToTop) {
        scrollToTop?.collect {
            listState.animateScrollToItem(0)
        }
    }

    // 数字带位于头部之下、吸顶 Tab 之上，据此推出 Tab 在列表中的下标（供点击数字带跳转用）
    val statsVisible = uiState.trackingFootprint != null
    val tabsIndex = if (statsVisible) 2 else 1

    // 触底加载：滑动到列表末尾（倒数第 3 项以内）且有更多数据时自动增量加载
    val activeType = collectionsState.selectedType
    val activeHasMore = collectionsState.hasMoreByType[activeType] ?: false
    LaunchedEffect(listState, activeType, activeHasMore, collectionsState.loadingMoreTypes) {
        if (!activeHasMore) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            layoutInfo.totalItemsCount > 0 && lastVisibleIndex >= layoutInfo.totalItemsCount - 3
        }.distinctUntilChanged().collect { shouldLoadMore ->
            val loadingMore = collectionsState.loadingMoreTypes.contains(collectionsState.selectedType)
            if (shouldLoadMore && !loadingMore) {
                onLoadMore(collectionsState.selectedType)
            }
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
        snackbarHost = {
            BgmSnackbarHost(
                hostState = snackbarHostState,
                isTopLevel = true,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing || collectionsState.isRefreshing,
            onRefresh = onRefresh,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> {
                    // 首帧加载态：沉浸式骨架屏，平滑过渡，杜绝未决会话前抢先闪烁未登录引导卡片
                    UserScreenSkeleton(
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                !uiState.isLoggedIn -> {
                    // 未登录状态：全屏沉浸式登录引导区，干净聚焦无冗余
                    UnauthenticatedLandingView(
                        onLogin = onLogin,
                        isAuthenticating = uiState.isAuthenticating,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    // 筛选只在"有列表可筛"时有意义：错误态与空态下摆着 5 个胶囊，点了什么都不会发生。
                    // 加载中仍然显示，是为了避开"加载完才冒出来"的跳版。
                    val filterType = collectionsState.selectedType
                    val filterIsLoading =
                        collectionsState.loadingTypes.contains(filterType) ||
                            (
                                collectionsState.isLoading &&
                                    !collectionsState.collectionsByType.containsKey(filterType)
                            )
                    val hasFilterableList =
                        collectionsState.collectionsByType[filterType].orEmpty().isNotEmpty()

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        item(key = "profile_hero") {
                            UserProfileHero(
                                profile = uiState.activeProfile,
                                savedAccountsCount = uiState.savedAccounts.size,
                                onManageAccountsClick = { showAccountSheet = true },
                            )
                        }

                        // 始终占位渲染：footprint 未就绪时显示「—」，不整项消失（否则下方会跳版）
                        item(key = "tracking_stats") {
                            TrackingStatsRow(
                                footprint = uiState.trackingFootprint,
                                onClick = {
                                    coroutineScope.launch { listState.animateScrollToItem(tabsIndex) }
                                },
                            )
                        }

                        stickyHeader(key = "collection_tabs") {
                            CollectionTypeTabs(
                                selectedType = collectionsState.selectedType,
                                counts = uiState.collectionCounts,
                                onSelectType = onSelectCollectionType,
                            )
                        }

                        if (filterIsLoading || hasFilterableList) {
                            item(key = "subject_filter") {
                                SubjectFilterRow(
                                    selectedFilter = collectionsState.selectedSubjectFilter,
                                    onSelectFilter = onSelectSubjectFilter,
                                )
                            }
                        }

                        collectionSection(
                            state = collectionsState,
                            onSubjectClick = onSubjectClick,
                            onIncrementProgress = onIncrementProgress,
                            onRefresh = onRefresh,
                            onRetry = onRetryCollections,
                            onLoadMore = onLoadMore,
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

/**
 * 第四层：当前分区的收藏内容流。
 *
 * 数据由 [UserCollectionsViewModel] 按分区懒加载（进入分区才发 1 次请求），
 * 因此这里只负责把已加载状态映射成列表项，不做任何请求编排。
 */
private fun LazyListScope.collectionSection(
    state: UserCollectionsUiState,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onIncrementProgress: (UserCollection) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: (CollectionType) -> Unit,
) {
    val type = state.selectedType
    val collections = state.collectionsByType[type].orEmpty()
    val isLoaded = state.collectionsByType.containsKey(type)
    val isLoading = state.loadingTypes.contains(type) || (state.isLoading && !isLoaded)
    val errorMessage = state.errorByType[type] ?: state.error
    val isLoadingMore = state.loadingMoreTypes.contains(type)
    val hasMore = state.hasMoreByType[type] ?: false

    when {
        isLoading && !isLoaded -> {
            item(key = "collection_loading") {
                CollectionLoadingView(modifier = Modifier.padding(top = 4.dp))
            }
        }

        errorMessage != null && collections.isEmpty() -> {
            item(key = "collection_error") {
                ErrorCollectionsView(
                    errorMessage = errorMessage,
                    onRetry = onRetry,
                )
            }
        }

        isLoaded && collections.isEmpty() -> {
            item(key = "collection_empty") {
                EmptyCollectionsView(onRefresh = onRefresh)
            }
        }

        else -> {
            items(
                items = collections,
                key = { "collection_${it.subjectId}" },
            ) { item ->
                UserCollectionCard(
                    collection = item,
                    isUpdating = state.updatingSubjectIds.contains(item.subjectId),
                    onSubjectClick = onSubjectClick,
                    onIncrementProgress = { onIncrementProgress(item) },
                    modifier =
                        Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            bottom = 6.dp,
                        ),
                )
            }

            item(key = "collection_footer") {
                CollectionListFooter(
                    isLoadingMore = isLoadingMore,
                    hasMore = hasMore,
                    loadedCount = collections.size,
                )
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
        sign = "探索二次元与科技的边界",
    )

private val previewCollections =
    listOf(
        UserCollection(
            userId = 123456L,
            subjectId = 1001L,
            subjectType = 2,
            rate = 8,
            type = CollectionType.DOING.value,
            comment = "分镜稳，节奏舒服。",
            epStatus = 7,
            volStatus = 0,
            updatedAt = "2026-09-26T14:30:00Z",
        ),
        UserCollection(
            userId = 123456L,
            subjectId = 1002L,
            subjectType = 2,
            rate = 0,
            type = CollectionType.DOING.value,
            comment = "",
            epStatus = 3,
            volStatus = 0,
            updatedAt = "2026-09-25T10:00:00Z",
        ),
    )

private fun previewCollectionsState() =
    UserCollectionsUiState(
        isLoggedIn = true,
        activeProfile = previewProfile1,
        selectedType = CollectionType.DOING,
        collectionsByType = mapOf(CollectionType.DOING to previewCollections),
    )

private fun previewUserState() =
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
        trackingFootprint =
            TrackingFootprint(
                watchingCount = 8,
                episodesWatched = 96,
                monthActiveCount = 3,
                lastActiveAtIso = "2026-09-26T14:30:00Z",
            ),
    )

@ThemePreviews
@Composable
private fun UserScreenLoadingPreview() {
    MiniBgmTheme {
        UserScreenContent(
            uiState = UserUiState(isLoading = true),
            collectionsState = UserCollectionsUiState(),
            onLogin = {},
            onRefresh = {},
            onSettingsClick = {},
            onSwitchAccount = {},
            onLogoutAccount = {},
            onLogoutAll = {},
            onSelectCollectionType = {},
            onSelectSubjectFilter = {},
            onLoadMore = {},
            onIncrementProgress = {},
            onSubjectClick = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@ThemePreviews
@Composable
private fun UserScreenUnauthenticatedPreview() {
    MiniBgmTheme {
        UserScreenContent(
            uiState = UserUiState(isLoggedIn = false),
            collectionsState = UserCollectionsUiState(),
            onLogin = {},
            onRefresh = {},
            onSettingsClick = {},
            onSwitchAccount = {},
            onLogoutAccount = {},
            onLogoutAll = {},
            onSelectCollectionType = {},
            onSelectSubjectFilter = {},
            onLoadMore = {},
            onIncrementProgress = {},
            onSubjectClick = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@ThemePreviews
@Composable
private fun UserScreenLoggedInPreview() {
    MiniBgmTheme {
        UserScreenContent(
            uiState = previewUserState(),
            collectionsState = previewCollectionsState(),
            onLogin = {},
            onRefresh = {},
            onSettingsClick = {},
            onSwitchAccount = {},
            onLogoutAccount = {},
            onLogoutAll = {},
            onSelectCollectionType = {},
            onSelectSubjectFilter = {},
            onLoadMore = {},
            onIncrementProgress = {},
            onSubjectClick = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
