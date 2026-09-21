package com.infinitezerone.minibgm.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.model.DiscoveredSource
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistImportSummary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 播放源管理界面状态：自备片单为主入口，解析规则为高级入口
 */
data class PlaybackRulesUiState(
    val rules: List<PlaybackSourceRule> = emptyList(),
    val playlists: List<PlaybackPlaylist> = emptyList(),
    val isLoading: Boolean = false,
    /** 断点续播记录（key = 播放地址，value = 上次观看位置毫秒），按最近写入降序展示 */
    val playbackPositions: Map<String, Long> = emptyMap(),
    val isDiscovering: Boolean = false,
    val discoveredSources: List<DiscoveredSource> = emptyList(),
    val showDiscoveryDialog: Boolean = false,
)

/**
 * 播放规则单发事件
 */
sealed interface PlaybackRulesUiEvent {
    data class ShowSnackbar(
        val message: String,
    ) : PlaybackRulesUiEvent
}

/**
 * 播放源管理 ViewModel：自备片单（JSON 导入）与第三方解析规则的读写。
 */
class PlaybackRulesViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val _events = Channel<PlaybackRulesUiEvent>(Channel.BUFFERED)
    val events: Flow<PlaybackRulesUiEvent> = _events.receiveAsFlow()

    private data class DiscoveryState(
        val isDiscovering: Boolean = false,
        val discoveredSources: List<DiscoveredSource> = emptyList(),
        val showDiscoveryDialog: Boolean = false,
    )

    private val discoveryState = MutableStateFlow(DiscoveryState())

    val uiState: StateFlow<PlaybackRulesUiState> =
        combine(
            settingsRepository.playbackRules,
            settingsRepository.playlists,
            settingsRepository.playbackPositions,
            discoveryState,
        ) { rules, playlists, positions, discovery ->
            PlaybackRulesUiState(
                rules = rules,
                playlists = playlists,
                playbackPositions = positions,
                isLoading = false,
                isDiscovering = discovery.isDiscovering,
                discoveredSources = discovery.discoveredSources,
                showDiscoveryDialog = discovery.showDiscoveryDialog,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaybackRulesUiState(isLoading = true),
        )

    /** 导入文本来自 SAF 文件或粘贴框，解析/校验/合并由 SettingsRepository 负责 */
    fun importPlaylistsFromJson(jsonText: String) {
        val trimmed = jsonText.trim()
        if (trimmed.isBlank()) {
            sendSnackbar("导入内容不能为空")
            return
        }
        viewModelScope.launch {
            when (val result = settingsRepository.importPlaylistsFromJson(trimmed)) {
                is AppResult.Success -> sendSnackbar(describeImport(result.data))
                is AppResult.Error -> sendSnackbar(result.message)
                AppResult.Loading -> Unit
            }
        }
    }

    /** 清除单条续播记录 */
    fun clearPlaybackPosition(url: String) {
        viewModelScope.launch {
            settingsRepository.clearPlaybackPosition(url)
            sendSnackbar("已清除该续播记录")
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            val target = uiState.value.playlists.firstOrNull { it.id == playlistId }
            settingsRepository.deletePlaylist(playlistId)
            sendSnackbar("已删除片单${if (target != null) "：${target.name}" else ""}")
        }
    }

    fun clearPlaylists() {
        viewModelScope.launch {
            settingsRepository.clearPlaylists()
            sendSnackbar("已清空全部自备片单")
        }
    }

    private fun describeImport(summary: PlaylistImportSummary): String {
        val parts =
            buildList {
                if (summary.addedCount > 0) add("新增 ${summary.addedCount} 份片单")
                if (summary.replacedCount > 0) add("覆盖 ${summary.replacedCount} 份")
            }
        val head = if (parts.isEmpty()) "没有导入任何片单" else parts.joinToString("，")
        return if (summary.issues.isEmpty()) {
            head
        } else {
            "$head；${summary.issues.size} 条问题：${summary.issues.first()}"
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun addRule(
        name: String,
        urlTemplate: String,
        description: String = "",
        kind: PlaybackRuleKind = PlaybackRuleKind.PAGE,
        headersText: String = "",
    ) {
        val trimmedName = name.trim()
        val trimmedUrl = urlTemplate.trim()
        if (trimmedName.isBlank() || trimmedUrl.isBlank()) {
            sendSnackbar("规则名称和 URL 模板不能为空")
            return
        }
        viewModelScope.launch {
            val rule =
                PlaybackSourceRule(
                    id = Uuid.random().toString(),
                    name = trimmedName,
                    urlTemplate = trimmedUrl,
                    description = description.trim(),
                    isEnabled = true,
                    kind = kind,
                    headers = parseHeaderLines(headersText),
                )
            settingsRepository.addPlaybackRule(rule)
            sendSnackbar("已添加规则：$trimmedName")
        }
    }

    fun updateRule(
        id: String,
        name: String,
        urlTemplate: String,
        description: String = "",
        kind: PlaybackRuleKind = PlaybackRuleKind.PAGE,
        headersText: String = "",
    ) {
        val trimmedName = name.trim()
        val trimmedUrl = urlTemplate.trim()
        if (trimmedName.isBlank() || trimmedUrl.isBlank()) {
            sendSnackbar("规则名称和 URL 模板不能为空")
            return
        }
        viewModelScope.launch {
            val existing = uiState.value.rules.firstOrNull { it.id == id }
            val rule =
                PlaybackSourceRule(
                    id = id,
                    name = trimmedName,
                    urlTemplate = trimmedUrl,
                    description = description.trim(),
                    isEnabled = existing?.isEnabled ?: true,
                    kind = kind,
                    headers = parseHeaderLines(headersText),
                )
            settingsRepository.updatePlaybackRule(rule)
            sendSnackbar("已更新规则：$trimmedName")
        }
    }

    fun deleteRule(id: String) {
        viewModelScope.launch {
            val target = uiState.value.rules.firstOrNull { it.id == id }
            settingsRepository.deletePlaybackRule(id)
            sendSnackbar("已删除规则${if (target != null) "：${target.name}" else ""}")
        }
    }

    fun toggleRule(
        id: String,
        isEnabled: Boolean,
    ) {
        viewModelScope.launch {
            settingsRepository.togglePlaybackRule(id, isEnabled)
        }
    }

    fun importRulesFromJson(jsonStr: String) {
        val trimmed = jsonStr.trim()
        if (trimmed.isBlank()) {
            sendSnackbar("导入内容不能为空")
            return
        }
        viewModelScope.launch {
            runCatching {
                // 支持单条规则或规则数组解析
                val importedList =
                    if (trimmed.startsWith("[")) {
                        json.decodeFromString<List<PlaybackSourceRule>>(trimmed)
                    } else {
                        listOf(json.decodeFromString<PlaybackSourceRule>(trimmed))
                    }
                if (importedList.isEmpty()) {
                    sendSnackbar("未解析到有效规则")
                } else {
                    // kind 与 parserType 不匹配时流水线/专用解析器根本没有执行路径，收下只会静默降级成嗅探
                    val (accepted, rejected) = importedList.partition { it.isResolvable }
                    if (accepted.isEmpty()) {
                        sendSnackbar("规则组合无效：专用解析器只能配在取源(SOURCE)规则上")
                    } else {
                        settingsRepository.importPlaybackRules(accepted)
                        val dropped = if (rejected.isEmpty()) "" else "，已跳过 ${rejected.size} 条无效组合"
                        sendSnackbar("成功导入 ${accepted.size} 条规则$dropped")
                    }
                }
            }.onFailure {
                sendSnackbar("规则解析失败，请检查 JSON 格式")
            }
        }
    }

    fun startAiDiscovery(customSubscriptionUrl: String? = null) {
        if (discoveryState.value.isDiscovering) return
        discoveryState.update { it.copy(isDiscovering = true) }
        viewModelScope.launch {
            when (val result = settingsRepository.discoverCommunityPlaybackSources(customSubscriptionUrl)) {
                is AppResult.Success -> {
                    val sources = result.data
                    discoveryState.update {
                        it.copy(
                            isDiscovering = false,
                            discoveredSources = sources,
                            showDiscoveryDialog = sources.isNotEmpty(),
                        )
                    }
                    if (sources.isEmpty()) {
                        sendSnackbar("未从社区检索到可用规则")
                    }
                }
                is AppResult.Error -> {
                    discoveryState.update { it.copy(isDiscovering = false) }
                    sendSnackbar("社区规则检索失败：${result.throwable.message ?: "网络异常"}")
                }
                is AppResult.Loading -> {
                    // 已由 isDiscovering 状态处理
                }
            }
        }
    }

    fun dismissDiscoveryDialog() {
        discoveryState.update { it.copy(showDiscoveryDialog = false) }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun importDiscoveredSources(sources: List<DiscoveredSource>) {
        if (sources.isEmpty()) return
        viewModelScope.launch {
            val newRules =
                sources.map {
                    it.toPlaybackSourceRule(id = Uuid.random().toString())
                }
            settingsRepository.importPlaybackRules(newRules)
            discoveryState.update { it.copy(showDiscoveryDialog = false) }
            sendSnackbar("成功导入 ${newRules.size} 条社区规则")
        }
    }

    private fun sendSnackbar(message: String) {
        viewModelScope.launch {
            _events.send(PlaybackRulesUiEvent.ShowSnackbar(message))
        }
    }

/** 规则编辑器里的请求头按 "Key: Value" 每行一条书写；空行与无冒号的行忽略 */
    internal fun parseHeaderLines(text: String): Map<String, String> =
        text
            .lines()
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                val name = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()
                if (name.isEmpty() || value.isEmpty()) null else name to value
            }.toMap()
}
