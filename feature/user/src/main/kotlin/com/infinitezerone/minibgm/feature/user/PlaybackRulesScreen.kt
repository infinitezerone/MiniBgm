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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

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
    var ruleToDelete by remember { mutableStateOf<PlaybackSourceRule?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaybackPlaylist?>(null) }
    var confirmClearPlaylists by rememberSaveable { mutableStateOf(false) }
    var confirmClearPositions by rememberSaveable { mutableStateOf(false) }

    val playlistPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val outcome =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val bytes = stream.readNBytes(MAX_IMPORT_BYTES + 1)
                                require(bytes.size <= MAX_IMPORT_BYTES) { "文件过大（上限 ${MAX_IMPORT_BYTES / 1024 / 1024} MB）" }
                                bytes.decodeToString()
                            } ?: error("无法读取所选文件")
                        }
                    }
                outcome
                    .onSuccess { text -> viewModel.importPlaylistsFromJson(text) }
                    .onFailure { error ->
                        snackbarHostState.showSnackbar("片单读取失败：${error.message ?: "未知错误"}")
                    }
            }
        }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaybackRulesUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is PlaybackRulesUiEvent.OpenAiSourceSearch -> {
                    onAiSourceSearch(event.prompt)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = { Text(text = "播放源管理") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = BgmIcons.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.requestAiSourceSearch() }) {
                        Icon(
                            imageVector = BgmIcons.Assistant,
                            contentDescription = "让 AI 助手找源",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { isImportingPlaylistJson = true }) {
                        Icon(
                            imageVector = BgmIcons.ContentPaste,
                            contentDescription = "粘贴片单 JSON",
                        )
                    }
                    IconButton(onClick = { playlistPicker.launch(PLAYLIST_MIME_TYPES) }) {
                        Icon(
                            imageVector = BgmIcons.Upload,
                            contentDescription = "从文件导入片单",
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
                        title = "自备片单",
                        supporting = "导入 JSON 片源后，分集播放向导会直接给出对应地址",
                        trailing = {
                            if (uiState.playlists.isNotEmpty()) {
                                TextButton(onClick = { confirmClearPlaylists = true }) {
                                    Text("清空", color = MaterialTheme.colorScheme.error)
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
                            onDelete = { playlistToDelete = playlist },
                        )
                    }
                }

                item(key = "playlist_template") {
                    PlaylistTemplateCard()
                }

                item(key = "positions_header") {
                    SectionHeader(
                        title = "续播记录",
                        supporting = "内置播放器自动记录各播放地址的观看位置，用于下次断点续播",
                        trailing = {
                            if (uiState.playbackPositions.isNotEmpty()) {
                                TextButton(onClick = { confirmClearPositions = true }) {
                                    Text("清空", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        },
                    )
                }

                if (uiState.playbackPositions.isEmpty()) {
                    item(key = "positions_empty") {
                        Text(
                            text = "暂无续播记录。播放器会在你观看时自动记录进度（保留最近 50 条）。",
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
                            title = "解析规则（高级）",
                            supporting =
                                if (showAdvancedRules) {
                                    "按占位符模板拼出解析地址，适合自定义第三方源"
                                } else {
                                    "已配置 ${uiState.rules.size} 条规则"
                                },
                            trailing = {
                                // 尾部只留展开/收起：操作按钮放下面的独立行，
                                // 全塞进尾部会把标题列挤成竖排单字
                                TextButton(onClick = { showAdvancedRules = !showAdvancedRules }) {
                                    Text(if (showAdvancedRules) "收起" else "展开")
                                }
                            },
                        )
                        if (showAdvancedRules) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.openSubscriptionImport() }) {
                                    Text("订阅")
                                }
                                TextButton(onClick = { viewModel.openSiteProbe() }) {
                                    Text("探测")
                                }
                                TextButton(onClick = { isImportingRuleJson = true }) {
                                    Text("导入")
                                }
                                TextButton(onClick = { isAddingRule = true }) {
                                    Text("添加")
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
                                    text = "暂无自定义播放规则，可点击「添加」或「导入」配置。",
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
                            onDelete = { ruleToDelete = rule },
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

    // 删除确认对话框
    ruleToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("确认删除规则") },
            text = { Text("确定要删除播放规则「${target.name}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRule(target.id)
                        ruleToDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text("取消")
                }
            },
        )
    }

    playlistToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("确认删除片单") },
            text = { Text("确定要删除片单「${target.name}」及其 ${target.entries.size} 条分集吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist(target.id)
                        playlistToDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (confirmClearPlaylists) {
        AlertDialog(
            onDismissRequest = { confirmClearPlaylists = false },
            title = { Text("清空全部片单") },
            text = { Text("将删除所有已导入的自备片单，解析规则不受影响。此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearPlaylists()
                        confirmClearPlaylists = false
                    },
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearPlaylists = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (confirmClearPositions) {
        AlertDialog(
            onDismissRequest = { confirmClearPositions = false },
            title = { Text("清空全部续播记录") },
            text = { Text("将删除所有播放地址的断点续播进度，再次播放将从头开始。此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        uiState.playbackPositions.keys.forEach { viewModel.clearPlaybackPosition(it) }
                        confirmClearPositions = false
                    },
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearPositions = false }) {
                    Text("取消")
                }
            },
        )
    }
}
