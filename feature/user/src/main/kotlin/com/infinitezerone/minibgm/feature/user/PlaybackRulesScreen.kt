package com.infinitezerone.minibgm.feature.user

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmCircleActionButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmGhostIconButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmPillButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmPillSize
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopBarMode
import com.infinitezerone.minibgm.core.designsystem.component.ConfirmDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.ObserveAsEvents
import com.infinitezerone.minibgm.core.designsystem.component.OverlayHostState
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.designsystem.theme.FeatureGate
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.feature.user.components.AddTorrentRuleDialog
import com.infinitezerone.minibgm.feature.user.components.PlaybackRuleCard
import com.infinitezerone.minibgm.feature.user.components.RuleAddOption
import com.infinitezerone.minibgm.feature.user.components.RuleAddSheet
import com.infinitezerone.minibgm.feature.user.components.RuleImportDialog
import com.infinitezerone.minibgm.feature.user.components.SiteProbeDialog
import com.infinitezerone.minibgm.feature.user.components.SubscriptionImportDialog
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

private enum class RuleFilter {
    ALL,
    ONLINE,
    TORRENT,
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
    onAiSourceSearch: ((String) -> Unit)? = null,
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
    var isAddSheetVisible by remember { mutableStateOf(false) }
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
                onAiSourceSearch?.invoke(event.prompt)
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

    Scaffold(
        topBar = {
            BgmTopAppBar(
                mode = BgmTopBarMode.Solid,
                title = { Text(text = stringResource(R.string.feature_user_title_playback_rules)) },
                navigationIcon = {
                    BgmCircleActionButton(
                        onClick = onBackClick,
                        icon = BgmIcons.ArrowBack,
                        contentDescription = stringResource(DesignSystemR.string.core_designsystem_action_back),
                    )
                },
                actions = {
                    if (uiState.subscriptionUrl.isNotBlank()) {
                        BgmGhostIconButton(
                            onClick = { viewModel.refreshSubscription() },
                            enabled = !uiState.isRefreshingSubscription,
                        ) {
                            if (uiState.isRefreshingSubscription) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = BgmIcons.Refresh,
                                    contentDescription = stringResource(R.string.feature_user_subscription_sync_action),
                                )
                            }
                        }
                    }
                    FeatureGate(capability = { it.isAiAssistantEnabled && onAiSourceSearch != null }) {
                        BgmCircleActionButton(
                            onClick = { viewModel.requestAiSourceSearch() },
                            icon = BgmIcons.Assistant,
                            contentDescription = stringResource(R.string.feature_user_cd_ai_source_search),
                            contentColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading) {
                BgmPillButton(
                    onClick = { isAddSheetVisible = true },
                    text = stringResource(R.string.feature_user_action_add_rule),
                    icon = BgmIcons.Add,
                    size = BgmPillSize.Large,
                )
            }
        },
        snackbarHost = { BgmSnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            RulesTabContent(
                rules = uiState.rules,
                subscriptionUrl = uiState.subscriptionUrl,
                onOpenSubscriptionImport = viewModel::openSubscriptionImport,
                onOpenSiteProbe = viewModel::openSiteProbe,
                onToggleRule = viewModel::toggleRule,
                onDeleteRule = viewModel::deleteRule,
                onClearSubscription = viewModel::clearSubscriptionUrl,
                overlayHostState = overlayHostState,
                modifier = Modifier.padding(innerPadding),
                onShowSnackbar = { message ->
                    scope.launch {
                        snackbarHostState.showSnackbar(message)
                    }
                },
            )
        }
    }

    // 添加播放源的四入口底栏：点选后自身先收起，再开对应对话框，避免两层浮层叠着
    if (isAddSheetVisible) {
        RuleAddSheet(
            onSelect = { option ->
                isAddSheetVisible = false
                when (option) {
                    RuleAddOption.SUBSCRIPTION -> viewModel.openSubscriptionImport()
                    RuleAddOption.SITE_PROBE -> viewModel.openSiteProbe()
                    RuleAddOption.ADD_TORRENT -> isAddingTorrentRule = true
                    RuleAddOption.IMPORT_JSON -> isImportingRuleJson = true
                }
            },
            onDismiss = { isAddSheetVisible = false },
        )
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

/** 解析规则页签：当前订阅 + 规则列表 + 分类筛选 + 空态引导 */
@Composable
internal fun RulesTabContent(
    rules: List<PlaybackSourceRule>,
    onOpenSubscriptionImport: () -> Unit,
    onOpenSiteProbe: () -> Unit,
    onToggleRule: (String, Boolean) -> Unit,
    onDeleteRule: (String) -> Unit,
    overlayHostState: OverlayHostState,
    modifier: Modifier = Modifier,
    onShowSnackbar: ((String) -> Unit)? = null,
    subscriptionUrl: String = "",
    onClearSubscription: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // 订阅卡片的文案在 lambda 里用，必须先经 stringResource 解析（配置变更时可重组刷新）
    val subscriptionCopiedMessage = stringResource(R.string.feature_user_subscription_copied)
    val subscriptionClearTitle = stringResource(R.string.feature_user_subscription_clear_action)
    val subscriptionClearMessage = stringResource(R.string.feature_user_subscription_clear_message)

    var selectedFilter by rememberSaveable { mutableStateOf(RuleFilter.ALL) }

    val onlineCount = remember(rules) { rules.count { it.kind != PlaybackRuleKind.TORRENT } }
    val torrentCount = remember(rules) { rules.count { it.kind == PlaybackRuleKind.TORRENT } }
    val enabledCount = remember(rules) { rules.count { it.isEnabled } }

    val filteredRules =
        remember(rules, selectedFilter) {
            when (selectedFilter) {
                RuleFilter.ALL -> rules
                RuleFilter.ONLINE -> rules.filter { it.kind != PlaybackRuleKind.TORRENT }
                RuleFilter.TORRENT -> rules.filter { it.kind == PlaybackRuleKind.TORRENT }
            }
        }

    val copyToClipboard: (String, String) -> Unit = { text, successMessage ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("text", text)))
            onShowSnackbar?.invoke(successMessage)
        }
    }

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (subscriptionUrl.isNotBlank()) {
            item(key = "active_subscription") {
                ActiveSubscriptionCard(
                    url = subscriptionUrl,
                    onCopy = { copyToClipboard(subscriptionUrl, subscriptionCopiedMessage) },
                    onClear = {
                        scope.launch {
                            val confirmed =
                                overlayHostState.await(
                                    ConfirmDialogAction(
                                        title = subscriptionClearTitle,
                                        message = subscriptionClearMessage,
                                        confirmText = subscriptionClearTitle,
                                        isDestructive = true,
                                    ),
                                )
                            if (confirmed) onClearSubscription?.invoke()
                        }
                    },
                )
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
                            TextButton(
                                onClick = onOpenSubscriptionImport,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            ) {
                                Text(stringResource(R.string.feature_user_subscription_title))
                            }
                            TextButton(
                                onClick = onOpenSiteProbe,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            ) {
                                Text(stringResource(R.string.feature_user_probe_title))
                            }
                        }
                    }
                }
            }
        } else {
            item(key = "rule_category_filters") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = selectedFilter == RuleFilter.ALL,
                        onClick = { selectedFilter = RuleFilter.ALL },
                        label = {
                            Text(
                                stringResource(R.string.feature_user_rules_filter_all, rules.size),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                    FilterChip(
                        selected = selectedFilter == RuleFilter.ONLINE,
                        onClick = { selectedFilter = RuleFilter.ONLINE },
                        label = {
                            Text(
                                stringResource(R.string.feature_user_rules_filter_online, onlineCount),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        leadingIcon =
                            if (selectedFilter == RuleFilter.ONLINE) {
                                {
                                    Icon(
                                        imageVector = BgmIcons.PlayCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                    )
                    FilterChip(
                        selected = selectedFilter == RuleFilter.TORRENT,
                        onClick = { selectedFilter = RuleFilter.TORRENT },
                        label = {
                            Text(
                                stringResource(R.string.feature_user_rules_filter_torrent, torrentCount),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        leadingIcon =
                            if (selectedFilter == RuleFilter.TORRENT) {
                                {
                                    Icon(
                                        imageVector = BgmIcons.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                    )
                }
            }

            if (filteredRules.isEmpty()) {
                item(key = "filtered_rules_empty") {
                    Surface(
                        shape = BgmShapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.feature_user_rules_empty_filtered),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(filteredRules, key = { it.id }) { rule ->
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
                        onCopyUrl = { url ->
                            copyToClipboard(
                                url,
                                context.getString(R.string.feature_user_rule_url_copied),
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * 当前订阅源卡片：地址可见、可复制、可取消。
 *
 * 之前这块只有顶栏一个刷新图标——用户既看不出自己订的是哪个地址，也没有退订入口
 * （`clearSubscriptionUrl` 早就存在，只是一直没接线）。
 */
@Composable
private fun ActiveSubscriptionCard(
    url: String,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = BgmIcons.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.feature_user_subscription_active_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.feature_user_subscription_active_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text(stringResource(R.string.feature_user_subscription_copy_action))
                }
                TextButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.feature_user_subscription_clear_action))
                }
            }
        }
    }
}
