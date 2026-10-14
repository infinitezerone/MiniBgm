package com.infinitezerone.minibgm.feature.user

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.ConfirmDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.ObserveAsEvents
import com.infinitezerone.minibgm.core.designsystem.component.OverlayHostState
import com.infinitezerone.minibgm.core.designsystem.component.SingleChoiceDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.PlaybackHistory
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.feature.user.components.AddTorrentRuleDialog
import com.infinitezerone.minibgm.feature.user.components.PlaybackHistoryCard
import com.infinitezerone.minibgm.feature.user.components.PlaybackRuleCard
import com.infinitezerone.minibgm.feature.user.components.RuleImportDialog
import com.infinitezerone.minibgm.feature.user.components.SiteProbeDialog
import com.infinitezerone.minibgm.feature.user.components.SubscriptionImportDialog
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

private enum class RuleAddOption {
    SUBSCRIPTION,
    SITE_PROBE,
    ADD_TORRENT,
    IMPORT_JSON,
    ;

    fun label(context: Context): String =
        when (this) {
            SUBSCRIPTION -> context.getString(R.string.feature_user_rule_choice_subscription)
            SITE_PROBE -> context.getString(R.string.feature_user_rule_choice_site_probe)
            ADD_TORRENT -> context.getString(R.string.feature_user_rule_choice_add_torrent)
            IMPORT_JSON -> context.getString(R.string.feature_user_rule_choice_import_json)
        }
}

/** 播放源管理页签：解析规则 / 观看记录 */
private enum class PlaybackRulesTab(
    val labelRes: Int,
) {
    RULES(R.string.feature_user_tab_rules),
    HISTORY(R.string.feature_user_tab_history),
}

/**
 * 播放规则与源管理界面：
 * 全面聚焦远程规则订阅（TVBox/MiniBgm）、单站智能探测与规则导入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackRulesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAiSourceSearch: (String) -> Unit = {},
    onPlayHistory: (PlayerRoute) -> Unit = {},
    viewModel: PlaybackRulesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val siteProbeState by viewModel.siteProbe.collectAsStateWithLifecycle()
    val subscriptionImportState by viewModel.subscriptionImport.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var isImportingRuleJson by remember { mutableStateOf(false) }
    var isAddingTorrentRule by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(PlaybackRulesTab.RULES.ordinal) }
    val overlayHostState = rememberOverlayHostState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            runCatching {
                val clipEntry = clipboard.getClipEntry()
                val text =
                    clipEntry
                        ?.clipData
                        ?.getItemAt(0)
                        ?.text
                        ?.toString()
                        .orEmpty()
                        .trim()
                viewModel.checkClipboardForSubscription(text)
            }
        }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is PlaybackRulesUiEvent.ShowSnackbar -> {
                val text =
                    event.message?.takeIf { it.isNotBlank() }
                        ?: context.getString(event.messageRes)
                snackbarHostState.showSnackbar(text)
            }
            is PlaybackRulesUiEvent.OpenAiSourceSearch -> {
                onAiSourceSearch(event.prompt)
            }
            is PlaybackRulesUiEvent.PromptClipboardSubscription -> {
                val confirmed =
                    overlayHostState.await(
                        ConfirmDialogAction(
                            title = context.getString(R.string.feature_user_clipboard_sub_detected_title),
                            message = context.getString(R.string.feature_user_clipboard_sub_detected_desc, event.url),
                            confirmText = context.getString(R.string.feature_user_clipboard_sub_action_import),
                            dismissText = context.getString(R.string.feature_user_clipboard_sub_action_ignore),
                            icon = BgmIcons.ContentPaste,
                        ),
                    )
                if (confirmed) {
                    viewModel.openSubscriptionImport(
                        prefilledUrl = event.url,
                        autoValidate = true,
                    )
                }
            }
        }
    }

    val currentTab = PlaybackRulesTab.entries.getOrElse(selectedTab) { PlaybackRulesTab.RULES }

    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = { Text(text = stringResource(R.string.feature_user_title_playback_rules)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = BgmIcons.ArrowBack,
                            contentDescription = stringResource(DesignSystemR.string.core_designsystem_action_back),
                        )
                    }
                },
                actions = {
                    when (currentTab) {
                        PlaybackRulesTab.RULES ->
                            IconButton(onClick = { viewModel.requestAiSourceSearch() }) {
                                Icon(
                                    imageVector = BgmIcons.Assistant,
                                    contentDescription = stringResource(R.string.feature_user_cd_ai_source_search),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        PlaybackRulesTab.HISTORY ->
                            if (uiState.playbackHistory.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val confirmed =
                                                overlayHostState.await(
                                                    ConfirmDialogAction(
                                                        title =
                                                            context.getString(
                                                                R.string.feature_user_history_clear_all_title,
                                                            ),
                                                        message =
                                                            context.getString(
                                                                R.string.feature_user_history_clear_all_message,
                                                            ),
                                                        confirmText =
                                                            context.getString(
                                                                R.string.feature_user_history_clear_all_action,
                                                            ),
                                                        isDestructive = true,
                                                    ),
                                                )
                                            if (confirmed) {
                                                viewModel.clearPlaybackHistory()
                                            }
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = BgmIcons.CleaningServices,
                                        contentDescription =
                                            stringResource(R.string.feature_user_history_clear_all_action),
                                    )
                                }
                            }
                    }
                },
            )
        },
        floatingActionButton = {
            if (currentTab == PlaybackRulesTab.RULES && !uiState.isLoading) {
                ExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            val choice =
                                overlayHostState.await(
                                    SingleChoiceDialogAction(
                                        title = context.getString(R.string.feature_user_rule_add_choice_title),
                                        options = RuleAddOption.entries,
                                        selectedOption = RuleAddOption.SUBSCRIPTION,
                                        optionLabel = { it.label(context) },
                                    ),
                                )
                            when (choice) {
                                RuleAddOption.SUBSCRIPTION -> viewModel.openSubscriptionImport()
                                RuleAddOption.SITE_PROBE -> viewModel.openSiteProbe()
                                RuleAddOption.ADD_TORRENT -> isAddingTorrentRule = true
                                RuleAddOption.IMPORT_JSON -> isImportingRuleJson = true
                                null -> {}
                            }
                        }
                    },
                    icon = { Icon(imageVector = BgmIcons.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.feature_user_action_add_rule)) },
                )
            }
        },
        snackbarHost = { BgmSnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = currentTab.ordinal) {
                PlaybackRulesTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == currentTab,
                        onClick = { selectedTab = tab.ordinal },
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                when (currentTab) {
                    PlaybackRulesTab.RULES ->
                        RulesTabContent(
                            rules = uiState.rules,
                            subscriptionUrl = uiState.subscriptionUrl,
                            isRefreshingSubscription = uiState.isRefreshingSubscription,
                            onRefreshSubscription = viewModel::refreshSubscription,
                            onClearSubscription = viewModel::clearSubscriptionUrl,
                            onOpenSubscriptionImport = viewModel::openSubscriptionImport,
                            onOpenSiteProbe = viewModel::openSiteProbe,
                            onToggleRule = viewModel::toggleRule,
                            onDeleteRule = viewModel::deleteRule,
                            overlayHostState = overlayHostState,
                        )
                    PlaybackRulesTab.HISTORY ->
                        HistoryTabContent(
                            history = uiState.playbackHistory,
                            onResume = { entry ->
                                onPlayHistory(
                                    PlayerRoute(
                                        subjectId = entry.subjectId,
                                        episodeId = entry.episodeId,
                                        streamUrl = entry.streamUrl,
                                        episodeName = entry.episodeName,
                                        subjectName = entry.subjectName,
                                        episodeSort = entry.episodeSort,
                                    ),
                                )
                            },
                            onDelete = viewModel::deleteHistory,
                            overlayHostState = overlayHostState,
                        )
                }
            }
        }
    }

    // 规则 JSON 批量导入对话框
    if (isImportingRuleJson) {
        RuleImportDialog(
            onDismiss = { isImportingRuleJson = false },
            onConfirm = { json ->
                viewModel.importRulesFromJson(json)
                isImportingRuleJson = false
            },
        )
    }

    // 种子规则添加对话框
    if (isAddingTorrentRule) {
        AddTorrentRuleDialog(
            onDismiss = { isAddingTorrentRule = false },
            onConfirm = { name, urlTemplate, description ->
                viewModel.addTorrentRule(name, urlTemplate, description)
                isAddingTorrentRule = false
            },
        )
    }

    // 站点探测对话框：贴域名 → 试标准 MacCMS 接口 → 命中即落成取源规则
    if (siteProbeState.isVisible) {
        SiteProbeDialog(
            state = siteProbeState,
            onInputChanged = viewModel::onProbeInputChanged,
            onProbe = viewModel::startSiteProbe,
            onConfirm = viewModel::addProbedRule,
            onDismiss = viewModel::closeSiteProbe,
        )
    }

    // 订阅导入对话框：贴订阅 URL → 检测出报告 → 按报告勾选 → 导入
    if (subscriptionImportState.isVisible) {
        SubscriptionImportDialog(
            state = subscriptionImportState,
            onInputChanged = viewModel::onSubscriptionInputChanged,
            onValidate = viewModel::validateSubscription,
            onToggleSource = viewModel::toggleSubscriptionSource,
            onConfirm = viewModel::confirmSubscriptionImport,
            onDismiss = viewModel::closeSubscriptionImport,
        )
    }

    BgmOverlayHost(hostState = overlayHostState) {
        confirmDialog()
        singleChoiceDialog()
    }
}

/** 解析规则页签：订阅状态条 + 规则列表 + 空态引导 */
@Composable
private fun RulesTabContent(
    rules: List<PlaybackSourceRule>,
    subscriptionUrl: String,
    isRefreshingSubscription: Boolean,
    onRefreshSubscription: () -> Unit,
    onClearSubscription: () -> Unit,
    onOpenSubscriptionImport: () -> Unit,
    onOpenSiteProbe: () -> Unit,
    onToggleRule: (String, Boolean) -> Unit,
    onDeleteRule: (String) -> Unit,
    overlayHostState: OverlayHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (subscriptionUrl.isNotBlank()) {
            item(key = "subscription_status") {
                Surface(
                    shape = BgmShapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.CloudQueue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.feature_user_subscription_active_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = subscriptionUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(
                            onClick = onRefreshSubscription,
                            enabled = !isRefreshingSubscription,
                        ) {
                            if (isRefreshingSubscription) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.feature_user_subscription_syncing),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            } else {
                                Text(
                                    stringResource(R.string.feature_user_subscription_sync_action),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val confirmed =
                                        overlayHostState.await(
                                            ConfirmDialogAction(
                                                title = context.getString(R.string.feature_user_subscription_clear_action),
                                                message = "确定要移除当前订阅源链接吗？已导入的本地规则将被保留。",
                                                confirmText =
                                                    context.getString(
                                                        R.string.feature_user_subscription_clear_action,
                                                    ),
                                                isDestructive = true,
                                            ),
                                        )
                                    if (confirmed) {
                                        onClearSubscription()
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.Close,
                                contentDescription = stringResource(R.string.feature_user_subscription_clear_action),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        if (rules.isEmpty()) {
            item(key = "rules_empty_hero") {
                Surface(
                    shape = BgmShapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = stringResource(R.string.feature_user_rules_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = onOpenSubscriptionImport) {
                                Text(stringResource(R.string.feature_user_subscription_title))
                            }
                            TextButton(onClick = onOpenSiteProbe) {
                                Text(stringResource(R.string.feature_user_probe_title))
                            }
                        }
                    }
                }
            }
        } else {
            items(rules, key = { it.id }) { rule ->
                PlaybackRuleCard(
                    rule = rule,
                    onToggle = { enabled -> onToggleRule(rule.id, enabled) },
                    onDelete = {
                        scope.launch {
                            val confirmed =
                                overlayHostState.await(
                                    ConfirmDialogAction(
                                        title = context.getString(R.string.feature_user_rules_delete_title),
                                        message =
                                            context.getString(
                                                R.string.feature_user_rules_delete_message,
                                                rule.name,
                                            ),
                                        confirmText = context.getString(R.string.feature_user_action_delete),
                                        isDestructive = true,
                                    ),
                                )
                            if (confirmed) {
                                onDeleteRule(rule.id)
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * 观看记录页签：按分集粒度的历史列表，点击整卡即续播、删除走显式按钮 + 二次确认。
 */
@Composable
private fun HistoryTabContent(
    history: List<PlaybackHistory>,
    onResume: (PlaybackHistory) -> Unit,
    onDelete: (Long) -> Unit,
    overlayHostState: OverlayHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (history.isEmpty()) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
        ) {
            item(key = "history_empty_hero") {
                Surface(
                    shape = BgmShapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = stringResource(R.string.feature_user_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(history, key = { it.episodeId }) { entry ->
            PlaybackHistoryCard(
                history = entry,
                onResume = { onResume(entry) },
                onDelete = {
                    scope.launch {
                        val confirmed =
                            overlayHostState.await(
                                ConfirmDialogAction(
                                    title = context.getString(R.string.feature_user_history_delete_title),
                                    message =
                                        context.getString(
                                            R.string.feature_user_history_delete_message,
                                            entry.subjectName.ifBlank { entry.episodeName },
                                        ),
                                    confirmText = context.getString(R.string.feature_user_action_delete),
                                    isDestructive = true,
                                ),
                            )
                        if (confirmed) {
                            onDelete(entry.episodeId)
                        }
                    }
                },
            )
        }
    }
}
