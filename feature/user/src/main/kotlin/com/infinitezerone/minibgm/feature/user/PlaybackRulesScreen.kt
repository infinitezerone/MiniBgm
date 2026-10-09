package com.infinitezerone.minibgm.feature.user

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

private enum class RuleAddOption {
    SITE_PROBE,
    SUBSCRIPTION,
    IMPORT_JSON,
    MANUAL,
    ;

    fun label(context: Context): String =
        when (this) {
            SITE_PROBE -> context.getString(R.string.feature_user_rule_choice_site_probe)
            SUBSCRIPTION -> context.getString(R.string.feature_user_rule_choice_subscription)
            IMPORT_JSON -> context.getString(R.string.feature_user_rule_choice_import_json)
            MANUAL -> context.getString(R.string.feature_user_rule_choice_manual)
        }
}

private enum class PlaylistAddOption {
    PICK_FILE,
    PASTE_JSON,
    ;

    fun label(context: Context): String =
        when (this) {
            PICK_FILE -> context.getString(R.string.feature_user_playlist_choice_file)
            PASTE_JSON -> context.getString(R.string.feature_user_playlist_choice_paste)
        }
}

/**
 * 播放源管理界面：
 * 采用双 Tab 清晰切分「解析规则」与「自备片单」，操作收拢至右下角悬浮按钮，降低视觉与心智负担。
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

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var ruleToEdit by remember { mutableStateOf<PlaybackSourceRule?>(null) }
    var isAddingRule by remember { mutableStateOf(false) }
    var isImportingRuleJson by remember { mutableStateOf(false) }
    var isImportingPlaylistJson by remember { mutableStateOf(false) }
    var showVarsHint by rememberSaveable { mutableStateOf(false) }
    var showPositionsSection by rememberSaveable { mutableStateOf(false) }
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
                    if (selectedTabIndex == 0) {
                        IconButton(onClick = { showVarsHint = !showVarsHint }) {
                            Icon(
                                imageVector = BgmIcons.Info,
                                contentDescription = stringResource(R.string.feature_user_menu_rule_vars),
                                tint = if (showVarsHint) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading) {
                if (selectedTabIndex == 0) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            scope.launch {
                                val choice =
                                    overlayHostState.await(
                                        SingleChoiceDialogAction(
                                            title = context.getString(R.string.feature_user_rule_add_choice_title),
                                            options = RuleAddOption.entries,
                                            selectedOption = RuleAddOption.SITE_PROBE,
                                            optionLabel = { it.label(context) },
                                        ),
                                    )
                                when (choice) {
                                    RuleAddOption.SITE_PROBE -> viewModel.openSiteProbe()
                                    RuleAddOption.SUBSCRIPTION -> viewModel.openSubscriptionImport()
                                    RuleAddOption.IMPORT_JSON -> isImportingRuleJson = true
                                    RuleAddOption.MANUAL -> isAddingRule = true
                                    null -> {}
                                }
                            }
                        },
                        icon = { Icon(imageVector = BgmIcons.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.feature_user_action_add_rule)) },
                    )
                } else {
                    ExtendedFloatingActionButton(
                        onClick = {
                            scope.launch {
                                val choice =
                                    overlayHostState.await(
                                        SingleChoiceDialogAction(
                                            title = context.getString(R.string.feature_user_playlist_add_choice_title),
                                            options = PlaylistAddOption.entries,
                                            selectedOption = PlaylistAddOption.PICK_FILE,
                                            optionLabel = { it.label(context) },
                                        ),
                                    )
                                when (choice) {
                                    PlaylistAddOption.PICK_FILE -> playlistPicker.launch(PLAYLIST_MIME_TYPES)
                                    PlaylistAddOption.PASTE_JSON -> isImportingPlaylistJson = true
                                    null -> {}
                                }
                            }
                        },
                        icon = { Icon(imageVector = BgmIcons.Upload, contentDescription = null) },
                        text = { Text(stringResource(R.string.feature_user_action_add_playlist)) },
                    )
                }
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
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(stringResource(R.string.feature_user_tab_rules))
                            if (uiState.rules.isNotEmpty()) {
                                Badge(
                                    containerColor =
                                        if (selectedTabIndex == 0) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                    contentColor =
                                        if (selectedTabIndex == 0) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                ) {
                                    Text("${uiState.rules.size}")
                                }
                            }
                        }
                    },
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(stringResource(R.string.feature_user_tab_playlists))
                            if (uiState.playlists.isNotEmpty()) {
                                Badge(
                                    containerColor =
                                        if (selectedTabIndex == 1) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                    contentColor =
                                        if (selectedTabIndex == 1) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                ) {
                                    Text("${uiState.playlists.size}")
                                }
                            }
                        }
                    },
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (selectedTabIndex == 0) {
                // TAB 0: 解析规则
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "vars_hint") {
                        AnimatedVisibility(visible = showVarsHint) {
                            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                                RuleVariablesHintCard()
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
                                        TextButton(onClick = { viewModel.openSiteProbe() }) {
                                            Text(stringResource(R.string.feature_user_probe_title))
                                        }
                                        TextButton(onClick = { viewModel.openSubscriptionImport() }) {
                                            Text(stringResource(R.string.feature_user_subscription_title))
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
            } else {
                // TAB 1: 自备片单
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState.playlists.isNotEmpty()) {
                        item(key = "playlists_header") {
                            SectionHeader(
                                title = stringResource(R.string.feature_user_playlist_section_title),
                                supporting = stringResource(R.string.feature_user_playlist_section_desc),
                                trailing = {
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
                                        Text(
                                            stringResource(R.string.feature_user_action_clear),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                },
                            )
                        }

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
                    } else {
                        item(key = "playlist_empty") {
                            PlaylistEmptyCard(
                                onPickFile = { playlistPicker.launch(PLAYLIST_MIME_TYPES) },
                                onPasteJson = { isImportingPlaylistJson = true },
                            )
                        }
                    }

                    item(key = "playlist_template") {
                        PlaylistTemplateCard()
                    }

                    // 续播断点记录（紧凑折叠项，不破坏整体布局）
                    if (uiState.playbackPositions.isNotEmpty()) {
                        item(key = "positions_collapsible") {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            Surface(
                                shape = BgmShapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { showPositionsSection = !showPositionsSection },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.feature_user_positions_toggle_title),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text =
                                                    stringResource(
                                                        R.string.feature_user_positions_toggle_subtitle,
                                                        uiState.playbackPositions.size,
                                                    ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        TextButton(onClick = { showPositionsSection = !showPositionsSection }) {
                                            Text(
                                                if (showPositionsSection) {
                                                    stringResource(R.string.feature_user_action_collapse)
                                                } else {
                                                    stringResource(R.string.feature_user_action_expand)
                                                },
                                            )
                                        }
                                    }

                                    AnimatedVisibility(visible = showPositionsSection) {
                                        Column(
                                            modifier = Modifier.padding(top = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        scope.launch {
                                                            val confirmed =
                                                                overlayHostState.await(
                                                                    ConfirmDialogAction(
                                                                        title =
                                                                            context.getString(
                                                                                R.string.feature_user_positions_clear_all_title,
                                                                            ),
                                                                        message =
                                                                            context.getString(
                                                                                R.string.feature_user_positions_clear_all_message,
                                                                            ),
                                                                        confirmText =
                                                                            context.getString(
                                                                                R.string.feature_user_action_clear,
                                                                            ),
                                                                        isDestructive = true,
                                                                    ),
                                                                )
                                                            if (confirmed) {
                                                                uiState.playbackPositions.keys.forEach {
                                                                    viewModel.clearPlaybackPosition(it)
                                                                }
                                                            }
                                                        }
                                                    },
                                                ) {
                                                    Text(
                                                        stringResource(R.string.feature_user_action_clear),
                                                        color = MaterialTheme.colorScheme.error,
                                                        style = MaterialTheme.typography.labelMedium,
                                                    )
                                                }
                                            }

                                            uiState.playbackPositions.entries
                                                .toList()
                                                .sortedByDescending { it.value }
                                                .forEach { entry ->
                                                    PlaybackPositionRow(
                                                        url = entry.key,
                                                        positionMs = entry.value,
                                                        onClear = { viewModel.clearPlaybackPosition(entry.key) },
                                                    )
                                                }
                                        }
                                    }
                                }
                            }
                        }
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
        singleChoiceDialog()
    }
}
