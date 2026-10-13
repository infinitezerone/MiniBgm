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
import com.infinitezerone.minibgm.core.designsystem.component.SingleChoiceDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.feature.user.components.AddTorrentRuleDialog
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
                snackbarHostState.showSnackbar(event.message)
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
                    IconButton(onClick = { viewModel.requestAiSourceSearch() }) {
                        Icon(
                            imageVector = BgmIcons.Assistant,
                            contentDescription = stringResource(R.string.feature_user_cd_ai_source_search),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading) {
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
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (uiState.subscriptionUrl.isNotBlank()) {
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
                                            text = uiState.subscriptionUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.refreshSubscription() },
                                        enabled = !uiState.isRefreshingSubscription,
                                    ) {
                                        if (uiState.isRefreshingSubscription) {
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
                                                    viewModel.clearSubscriptionUrl()
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

                    if (uiState.rules.isEmpty()) {
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
                                        TextButton(onClick = { viewModel.openSubscriptionImport() }) {
                                            Text(stringResource(R.string.feature_user_subscription_title))
                                        }
                                        TextButton(onClick = { viewModel.openSiteProbe() }) {
                                            Text(stringResource(R.string.feature_user_probe_title))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(uiState.rules, key = { it.id }) { rule ->
                            PlaybackRuleCard(
                                rule = rule,
                                onToggle = { enabled -> viewModel.toggleRule(rule.id, enabled) },
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
                                            viewModel.deleteRule(rule.id)
                                        }
                                    }
                                },
                            )
                        }
                    }
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
