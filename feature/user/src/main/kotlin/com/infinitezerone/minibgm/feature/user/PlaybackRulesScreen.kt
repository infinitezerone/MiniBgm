package com.infinitezerone.minibgm.feature.user

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.PlaybackPlaylistSchema
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.RuleParserType
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.requestAiSourceSearch() }) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "让 AI 助手找源",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { isImportingPlaylistJson = true }) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = "粘贴片单 JSON",
                        )
                    }
                    IconButton(onClick = { playlistPicker.launch(PLAYLIST_MIME_TYPES) }) {
                        Icon(
                            imageVector = Icons.Filled.FileUpload,
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

private const val MAX_IMPORT_BYTES = 4 * 1024 * 1024

private val PLAYLIST_MIME_TYPES =
    arrayOf(
        "application/json",
        "text/plain",
        "application/octet-stream",
    )

@Composable
private fun SectionHeader(
    title: String,
    supporting: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

/**
 * 片单为空时的引导卡片
 */
@Composable
private fun PlaylistEmptyCard(
    onPickFile: () -> Unit,
    onPasteJson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "还没有自备片单",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "自备片单是你自己提供的 JSON 文件：为每个条目声明分集地址（DIRECT 直链在应用内播放，PAGE 页面链接外部打开），可附带 Referer 等请求头。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPickFile) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("选择 JSON 文件")
                }
                TextButton(onClick = onPasteJson) {
                    Text("粘贴 JSON 导入")
                }
            }
        }
    }
}

/**
 * 单份自备片单概览
 */
@Composable
private fun PlaylistCard(
    playlist: PlaybackPlaylist,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val directCount = playlist.entries.count { it.kind == PlaylistEntryKind.DIRECT }
    val pageCount = playlist.entries.size - directCount
    val labelPreview = playlist.entries.take(8).joinToString("、") { it.label }

    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            if (playlist.bgmSubjectId > 0L) {
                                "绑定条目 ${playlist.bgmSubjectId} · 直链 $directCount / 页面 $pageCount"
                            } else {
                                "未绑定条目（不会出现在分集入口）· 直链 $directCount / 页面 $pageCount"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }

            Text(
                text = labelPreview + if (playlist.entries.size > 8) " …" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 导入模板示例（与 :core:model 的 schema 常量同源，避免文案漂移）
 */
@Composable
private fun PlaylistTemplateCard(modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "片单 JSON 格式（schemaVersion ${PlaybackPlaylistSchema.CURRENT_SCHEMA_VERSION}）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起示例" else "查看示例")
                }
            }
            if (expanded) {
                Text(
                    text = PlaybackPlaylistSchema.TEMPLATE_EXAMPLE_JSON.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                            .padding(10.dp),
                )
                Text(
                    text =
                        "上限：" +
                            "${PlaybackPlaylistSchema.MAX_PLAYLISTS} 份片单、" +
                            "每份 ${PlaybackPlaylistSchema.MAX_ENTRIES_PER_PLAYLIST} 条、" +
                            "每条 ${PlaybackPlaylistSchema.MAX_HEADERS_PER_ENTRY} 个请求头；" +
                            "同 id 的片单会被新导入的覆盖。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 片单 JSON 粘贴导入对话框
 */
@Composable
private fun PlaylistImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var jsonText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("粘贴片单 JSON") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "顶层需为 schemaVersion ${PlaybackPlaylistSchema.CURRENT_SCHEMA_VERSION} 的片单文档；校验通过的片单会合并进现有列表（同 id 覆盖）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    placeholder = {
                        Text(
                            PlaybackPlaylistSchema.TEMPLATE_EXAMPLE_JSON.trimIndent(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(jsonText) },
                enabled = jsonText.isNotBlank(),
            ) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * 变量支持提示卡片
 */
@Composable
private fun RuleVariablesHintCard() {
    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "支持的占位符变量",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "{title}: 番剧名称\n{ep}: 分集编号（如 1, 2）\n{subjectId}: 条目 ID\n{episodeId}: 分集 ID",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 单条播放规则卡片
 */
@Composable
private fun PlaybackRuleCard(
    rule: PlaybackSourceRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (rule.description.isNotBlank()) {
                        Text(
                            text = rule.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle,
                )
            }

            Text(
                text = rule.urlTemplate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
            )

            if (rule.kind == PlaybackRuleKind.SOURCE) {
                Text(
                    text = "取源接口 · AI 找源时会请求该地址并抽取可播放地址",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑")
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * 规则添加/编辑对话框
 */
@Composable
private fun RuleEditDialog(
    initialRule: PlaybackSourceRule?,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        url: String,
        desc: String,
        kind: PlaybackRuleKind,
        parserType: RuleParserType,
        headersText: String,
    ) -> Unit,
) {
    var name by remember(initialRule) { mutableStateOf(initialRule?.name ?: "") }
    var urlTemplate by remember(initialRule) { mutableStateOf(initialRule?.urlTemplate ?: "") }
    var description by remember(initialRule) { mutableStateOf(initialRule?.description ?: "") }
    var kind by remember(initialRule) { mutableStateOf(initialRule?.kind ?: PlaybackRuleKind.PAGE) }
    var parserType by remember(initialRule) { mutableStateOf(initialRule?.parserType ?: RuleParserType.AUTO) }
    var headersText by remember(initialRule) {
        mutableStateOf(
            initialRule
                ?.headers
                .orEmpty()
                .entries
                .joinToString("\n") { "${it.key}: ${it.value}" },
        )
    }

    val isEditing = initialRule != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "编辑播放规则" else "添加播放规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称") },
                    placeholder = { Text("例如：我的采集源") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = urlTemplate,
                    onValueChange = { urlTemplate = it },
                    label = { Text("URL 模板") },
                    placeholder = { Text("https://example.com/search?q={title}") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 快捷插入变量 Chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("{title}", "{ep}", "{subjectId}", "{episodeId}").forEach { placeholder ->
                        FilterChip(
                            selected = false,
                            onClick = { urlTemplate += placeholder },
                            label = { Text(placeholder, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                // 规则用途：跳转页面只是给用户一个入口；取源接口会真的发请求并抽取可播放地址
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PlaybackRuleKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = {
                                Text(
                                    text = if (option == PlaybackRuleKind.SOURCE) "取源接口" else "跳转页面",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }

                if (kind == PlaybackRuleKind.SOURCE) {
                    // 解析器决定播放时走哪条抽取路径。选错不会报错，只会静默降级成页面嗅探，
                    // 所以这里让人显式选，而不是一律 AUTO（「探测」入口不走这条，它永落 MACCMS）。
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "解析器",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RuleParserType.entries.forEach { option ->
                                FilterChip(
                                    selected = parserType == option,
                                    onClick = { parserType = option },
                                    label = {
                                        Text(
                                            text = parserTypeLabel(option),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = headersText,
                        onValueChange = { headersText = it },
                        label = { Text("请求头（可选，每行 Key: Value）") },
                        placeholder = { Text("Referer: https://example.com/") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("备注说明（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, urlTemplate, description, kind, parserType, headersText) },
                enabled = name.isNotBlank() && urlTemplate.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * 规则 JSON 批量导入对话框
 */
@Composable
private fun RuleImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var jsonText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入规则 (JSON)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "粘贴单条或数组格式的规则 JSON 配置：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    placeholder = {
                        Text(
                            """[{"id":"rule-1","name":"源名","urlTemplate":"https://...{title}"}]""",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    minLines = 5,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(jsonText) },
                enabled = jsonText.isNotBlank(),
            ) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * 站点探测对话框：贴域名 → 试标准 MacCMS 采集接口 → 命中即落成取源规则。
 *
 * 这里**不给形态选择**——探到的只可能是接口端点，落库必然是 `SOURCE + MACCMS`。
 * 让用户在"跳转页面 / 取源接口"之间选反而可能选错，选成页面会让专用解析器在播放时被丢掉。
 */
@Composable
private fun SiteProbeDialog(
    state: SiteProbeUiState,
    onInputChanged: (String) -> Unit,
    onProbe: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("探测站点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "填入站点域名，会试它是否符合标准采集接口（/api.php/provide/vod/）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChanged,
                    singleLine = true,
                    enabled = !state.isProbing,
                    placeholder = {
                        Text("example.com", style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.isProbing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在探测…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                state.result?.let { result ->
                    Surface(
                        shape = BgmShapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Text(
                                text = "✓ 识别为 MacCMS 采集接口",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "接口当前返回 ${result.sampleCount} 条内容，规则将以「取源接口」形态添加",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = result.ruleTemplate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                            )
                        }
                    }
                }

                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (state.result != null) {
                Button(onClick = onConfirm) {
                    Text("添加规则")
                }
            } else {
                Button(
                    onClick = onProbe,
                    enabled = !state.isProbing && state.input.isNotBlank(),
                ) {
                    Text("探测")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * 订阅导入对话框：贴订阅 URL → 检测出报告 → 按报告勾选 → 导入。
 *
 * 检测与导入全程确定性（拉取 → 解析 → 探活测速 → 落库），不经过 AI：
 * 报告里收下几条、跳过几条、为什么跳过都如实展示，与站点探测对话框同一形态。
 */
@Composable
private fun SubscriptionImportDialog(
    state: SubscriptionImportUiState,
    onInputChanged: (String) -> Unit,
    onValidate: () -> Unit,
    onToggleSource: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val report = state.report
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("订阅导入") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "填入 TVBox / MiniBgm 订阅地址，检测通过后按报告勾选要导入的来源。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChanged,
                    singleLine = true,
                    enabled = !state.isValidating,
                    placeholder = {
                        Text("https://example.com/tvbox.json", style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.isValidating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在检测…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                report?.let { result ->
                    Surface(
                        shape = BgmShapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Text(
                                text = "✓ 检测通过：有效连通 ${result.aliveRules}/${result.totalRules}，平均延迟 ${result.averageLatencyMs}ms",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            result.sources.forEachIndexed { index, source ->
                                SubscriptionSourceRow(
                                    index = index,
                                    name = source.name,
                                    latencyMs = source.latencyMs,
                                    isAlive = source.isAlive,
                                    isSelected = index in state.selected,
                                    onToggle = { onToggleSource(index) },
                                )
                            }
                        }
                    }
                }

                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (report != null) {
                Button(
                    onClick = onConfirm,
                    enabled = state.selected.isNotEmpty(),
                ) {
                    Text("导入所选（${state.selected.size}）")
                }
            } else {
                Button(
                    onClick = onValidate,
                    enabled = !state.isValidating && state.input.isNotBlank(),
                ) {
                    Text("检测")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/** 订阅报告里的单条来源：勾选导入与否；未探活通过的默认不勾，但仍可手动选 */
@Composable
private fun SubscriptionSourceRow(
    index: Int,
    name: String,
    latencyMs: Long,
    isAlive: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    buildString {
                        append(if (isAlive) "✓ 连通" else "✗ 未连通")
                        if (latencyMs > 0L) append(" · ${latencyMs}ms")
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

/** 续播记录单条：展示主机名与上次观看位置，可单条清除 */
@Composable
private fun PlaybackPositionRow(
    url: String,
    positionMs: Long,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hostLabelOf(url),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "看到 " + formatPlaybackPosition(positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "清除该续播记录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 解析器选项的短标签：四个 chip 要排在一行，长名字排不下 */
private fun parserTypeLabel(type: RuleParserType): String =
    when (type) {
        RuleParserType.AUTO -> "自动"
        RuleParserType.MACCMS -> "MacCMS"
        RuleParserType.STREMIO -> "Stremio"
        RuleParserType.PIPELINE -> "流水线"
    }

private fun hostLabelOf(url: String): String = url.substringAfter("://", url).substringBefore('/').substringBefore('?')

private fun formatPlaybackPosition(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val sec = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
