package com.infinitezerone.minibgm.feature.user

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.ConfirmDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.ObserveAsEvents
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.feature.user.components.MAX_IMPORT_BYTES
import com.infinitezerone.minibgm.feature.user.components.PLAYLIST_MIME_TYPES
import com.infinitezerone.minibgm.feature.user.components.PlaybackPositionRow
import com.infinitezerone.minibgm.feature.user.components.PlaybackRuleCard
import com.infinitezerone.minibgm.feature.user.components.PlaylistCard
import com.infinitezerone.minibgm.feature.user.components.PlaylistEmptyCard
import com.infinitezerone.minibgm.feature.user.components.PlaylistImportDialog
import com.infinitezerone.minibgm.feature.user.components.PlaylistTemplateCard
import com.infinitezerone.minibgm.feature.user.components.RuleEditDialog
import com.infinitezerone.minibgm.feature.user.components.RuleImportDialog
import com.infinitezerone.minibgm.feature.user.components.RuleVariablesHintCard
import com.infinitezerone.minibgm.feature.user.components.SectionHeader
import com.infinitezerone.minibgm.feature.user.components.SiteProbeDialog
import com.infinitezerone.minibgm.feature.user.components.SubscriptionImportDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/**
 * 播放源管理界面：
 * 主入口是用户自备片单（JSON 导入，支持文件与粘贴），高级入口是第三方解析规则。
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
    val scope = rememberCoroutineScope()

    var ruleToEdit by remember { mutableStateOf<PlaybackSourceRule?>(null) }
    var isAddingRule by remember { mutableStateOf(false) }
    var isImportingRuleJson by remember { mutableStateOf(false) }
    var isImportingPlaylistJson by remember { mutableStateOf(false) }
    var showAdvancedRules by rememberSaveable { mutableStateOf(false) }
    val overlayHostState = rememberOverlayHostState()

    val playlistPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val outcome =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val bytes = stream.readNBytes(MAX_IMPORT_BYTES + 1)
                                require(bytes.size <= MAX_IMPORT_BYTES) {
                                    context.getString(
                                        R.string.feature_user_playlist_file_too_large,
                                        MAX_IMPORT_BYTES / 1024 / 1024,
                                    )
                                }
                                bytes.decodeToString()
                            } ?: error(context.getString(R.string.feature_user_playlist_file_unreadable))
                        }
                    }
                outcome
                    .onSuccess { text -> viewModel.importPlaylistsFromJson(text) }
                    .onFailure { error ->
                        snackbarHostState.showSnackbar(
                            context.getString(
                                R.string.feature_user_playlist_read_failed,
                                error.message ?: context.getString(R.string.feature_user_common_unknown_error),
                            ),
                        )
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
                    IconButton(onClick = { isImportingPlaylistJson = true }) {
                        Icon(
                            imageVector = BgmIcons.ContentPaste,
                            contentDescription = stringResource(R.string.feature_user_playlist_paste_title),
                        )
                    }
                    IconButton(onClick = { playlistPicker.launch(PLAYLIST_MIME_TYPES) }) {
                        Icon(
                            imageVector = BgmIcons.Upload,
                            contentDescription = stringResource(R.string.feature_user_playlist_cd_import_file),
                        )
                    }
                },
            )
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
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "playlist_header") {
                    SectionHeader(
                        title = stringResource(R.string.feature_user_playlist_section_title),
                        supporting = stringResource(R.string.feature_user_playlist_section_desc),
                        trailing = {
                            if (uiState.playlists.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            val confirmed =
                                                overlayHostState.await(
                                                    ConfirmDialogAction(
                                                        title = context.getString(R.string.feature_user_playlist_clear_all_title),
                                                        message =
                                                            context.getString(
                                                                R.string.feature_user_playlist_clear_all_message,
                                                            ),
                                                        confirmText = context.getString(R.string.feature_user_action_clear),
                                                        isDestructive = true,
                                                    ),
                                                )
                                            if (confirmed) {
                                                viewModel.clearPlaylists()
                                            }
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.feature_user_action_clear), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        },
                    )
                }

                if (uiState.playlists.isEmpty()) {
                    item(key = "playlist_empty") {
                        PlaylistEmptyCard(
                            onPickFile = { playlistPicker.launch(PLAYLIST_MIME_TYPES) },
                            onPasteJson = { isImportingPlaylistJson = true },
                        )
                    }
                } else {
                    items(uiState.playlists, key = { "playlist_${it.id}" }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onDelete = {
                                scope.launch {
                                    val confirmed =
                                        overlayHostState.await(
                                            ConfirmDialogAction(
                                                title = context.getString(R.string.feature_user_playlist_delete_title),
                                                message =
                                                    context.getString(
                                                        R.string.feature_user_playlist_delete_message,
                                                        playlist.name,
                                                        playlist.entries.size,
                                                    ),
                                                confirmText = context.getString(R.string.feature_user_action_delete),
                                                isDestructive = true,
                                            ),
                                        )
                                    if (confirmed) {
                                        viewModel.deletePlaylist(playlist.id)
                                    }
                                }
                            },
                        )
                    }
                }

                item(key = "playlist_template") {
                    PlaylistTemplateCard()
                }

                item(key = "positions_header") {
                    SectionHeader(
                        title = stringResource(R.string.feature_user_positions_section_title),
                        supporting = stringResource(R.string.feature_user_positions_section_desc),
                        trailing = {
                            if (uiState.playbackPositions.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            val confirmed =
                                                overlayHostState.await(
                                                    ConfirmDialogAction(
                                                        title = context.getString(R.string.feature_user_positions_clear_all_title),
                                                        message =
                                                            context.getString(
                                                                R.string.feature_user_positions_clear_all_message,
                                                            ),
                                                        confirmText = context.getString(R.string.feature_user_action_clear),
                                                        isDestructive = true,
                                                    ),
                                                )
                                            if (confirmed) {
                                                uiState.playbackPositions.keys.forEach { viewModel.clearPlaybackPosition(it) }
                                            }
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.feature_user_action_clear), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        },
                    )
                }

                if (uiState.playbackPositions.isEmpty()) {
                    item(key = "positions_empty") {
                        Text(
                            text = stringResource(R.string.feature_user_positions_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                } else {
                    items(
                        uiState.playbackPositions.entries
                            .toList()
                            .sortedByDescending { it.value },
                        key = { "position_" + it.key },
                    ) { entry ->
                        PlaybackPositionRow(
                            url = entry.key,
                            positionMs = entry.value,
                            onClear = { viewModel.clearPlaybackPosition(entry.key) },
                        )
                    }
                }

                item(key = "advanced_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        SectionHeader(
                            title = stringResource(R.string.feature_user_rules_section_title),
                            supporting =
                                if (showAdvancedRules) {
                                    stringResource(R.string.feature_user_rules_section_desc_expanded)
                                } else {
                                    stringResource(R.string.feature_user_rules_section_desc_count, uiState.rules.size)
                                },
                            trailing = {
                                // 尾部只留展开/收起：操作按钮放下面的独立行，
                                // 全塞进尾部会把标题列挤成竖排单字
                                TextButton(onClick = { showAdvancedRules = !showAdvancedRules }) {
                                    Text(
                                        if (showAdvancedRules) {
                                            stringResource(R.string.feature_user_action_collapse)
                                        } else {
                                            stringResource(R.string.feature_user_action_expand)
                                        },
                                    )
                                }
                            },
                        )
                        if (showAdvancedRules) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.openSubscriptionImport() }) {
                                    Text(stringResource(R.string.feature_user_action_subscribe))
                                }
                                TextButton(onClick = { viewModel.openSiteProbe() }) {
                                    Text(stringResource(R.string.feature_user_probe_action))
                                }
                                TextButton(onClick = { isImportingRuleJson = true }) {
                                    Text(stringResource(R.string.feature_user_action_import))
                                }
                                TextButton(onClick = { isAddingRule = true }) {
                                    Text(stringResource(R.string.feature_user_action_add))
                                }
                            }
                        }
                    }
                }

                if (showAdvancedRules) {
                    item(key = "template_hint") {
                        RuleVariablesHintCard()
                    }

                    if (uiState.rules.isEmpty()) {
                        item(key = "rule_empty") {
                            Surface(
                                shape = BgmShapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(R.string.feature_user_rules_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                    }

                    items(uiState.rules, key = { it.id }) { rule ->
                        PlaybackRuleCard(
                            rule = rule,
                            onToggle = { enabled -> viewModel.toggleRule(rule.id, enabled) },
                            onEdit = { ruleToEdit = rule },
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

    // 新增/编辑对话框
    if (isAddingRule || ruleToEdit != null) {
        val target = ruleToEdit
        RuleEditDialog(
            initialRule = target,
            onDismiss = {
                isAddingRule = false
                ruleToEdit = null
            },
            onConfirm = { name, url, desc, kind, parserType, headersText ->
                if (target != null) {
                    viewModel.updateRule(target.id, name, url, desc, kind, parserType, headersText)
                } else {
                    viewModel.addRule(name, url, desc, kind, parserType, headersText)
                }
                isAddingRule = false
                ruleToEdit = null
            },
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

    // 片单 JSON 粘贴导入对话框
    if (isImportingPlaylistJson) {
        PlaylistImportDialog(
            onDismiss = { isImportingPlaylistJson = false },
            onConfirm = { text ->
                viewModel.importPlaylistsFromJson(text)
                isImportingPlaylistJson = false
            },
        )
    }

    BgmOverlayHost(hostState = overlayHostState) {
        confirmDialog()
    }
}
