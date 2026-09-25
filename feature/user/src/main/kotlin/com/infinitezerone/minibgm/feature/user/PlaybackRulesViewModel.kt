package com.infinitezerone.minibgm.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.model.MacCmsProbeResult
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistImportSummary
import com.infinitezerone.minibgm.core.model.RuleParserType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
)

/**
 * 站点探测对话框状态。
 *
 * 「我有个网址」是最常见的入口，但采集站的接口地址是**可判定的**（标准 MacCMS V10 路径），
 * 不该每次都让人手写 `{title}` 模板或去问模型。
 */
data class SiteProbeUiState(
    val isVisible: Boolean = false,
    val input: String = "",
    val isProbing: Boolean = false,
    val result: MacCmsProbeResult? = null,
    val errorMessage: String? = null,
)

/**
 * 播放规则单发事件
 */
sealed interface PlaybackRulesUiEvent {
    data class ShowSnackbar(
        val message: String,
    ) : PlaybackRulesUiEvent

    /** 请求打开助手会话并带上找源意图；导航由上层完成（feature 之间不互相依赖） */
    data class OpenAiSourceSearch(
        val prompt: String,
    ) : PlaybackRulesUiEvent
}

/**
 * 播放源管理 ViewModel：自备片单（JSON 导入）与第三方解析规则的读写。
 */
class PlaybackRulesViewModel(
    private val settingsRepository: SettingsRepository,
    private val playbackResolverRepository: PlaybackResolverRepository? = null,
) : ViewModel() {
    // feature 层看不到 :core:network 的 jsonConfig（红线 2 禁止越级依赖），这里是
    // feature 作用域唯一的 Json 实例（ArchitectureRulesTest 白名单登记处），仅用于
    // 解析用户粘贴的规则 JSON；如需新增用例请复用本实例而不是再建
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val _siteProbe = MutableStateFlow(SiteProbeUiState())

    /** 站点探测状态；与主列表相互独立，探测过程不该让规则列表重建 */
    val siteProbe: StateFlow<SiteProbeUiState> = _siteProbe.asStateFlow()

    private val _events = Channel<PlaybackRulesUiEvent>(Channel.BUFFERED)
    val events: Flow<PlaybackRulesUiEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<PlaybackRulesUiState> =
        combine(
            settingsRepository.playbackRules,
            settingsRepository.playlists,
            settingsRepository.playbackPositions,
        ) { rules, playlists, positions ->
            PlaybackRulesUiState(
                rules = rules,
                playlists = playlists,
                playbackPositions = positions,
                isLoading = false,
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

    /** 打开「探测站点」对话框（清掉上一次的输入与结论，避免误当成这次的） */
    fun openSiteProbe() {
        _siteProbe.value = SiteProbeUiState(isVisible = true)
    }

    fun closeSiteProbe() {
        _siteProbe.value = SiteProbeUiState()
    }

    fun onProbeInputChanged(text: String) {
        _siteProbe.update { it.copy(input = text, errorMessage = null, result = null) }
    }

    /**
     * 探测输入是否为标准 MacCMS 采集接口。
     *
     * 纯确定性路径：拿 `/api.php/provide/vod/` 上的响应结构做判定，不调模型、不猜站点。
     * 探不到就如实说探不到，并指出还有哪两条路可走。
     */
    fun startSiteProbe() {
        val repository = playbackResolverRepository
        if (repository == null) {
            sendSnackbar("站点探测不可用")
            return
        }
        val input = _siteProbe.value.input.trim()
        if (input.isBlank()) {
            _siteProbe.update { it.copy(errorMessage = "请填写站点域名或接口地址") }
            return
        }
        viewModelScope.launch {
            _siteProbe.update { it.copy(isProbing = true, errorMessage = null, result = null) }
            when (val outcome = repository.probeMacCmsEndpoint(input)) {
                is AppResult.Success ->
                    _siteProbe.update {
                        it.copy(isProbing = false, result = outcome.data, errorMessage = null)
                    }
                is AppResult.Error ->
                    _siteProbe.update { it.copy(isProbing = false, result = null, errorMessage = outcome.message) }
                is AppResult.Loading -> _siteProbe.update { it.copy(isProbing = false) }
            }
        }
    }

    /**
     * 把探测结论落成规则。
     *
     * 必须显式声明 `kind = SOURCE` + `parserType = MACCMS`：探到的是**接口端点**，
     * 标成 PAGE 会让播放时丢掉专用解析器、静默降级成页面嗅探。
     */
    @OptIn(ExperimentalUuidApi::class)
    fun addProbedRule() {
        val probed = _siteProbe.value.result ?: return
        viewModelScope.launch {
            settingsRepository.addPlaybackRule(
                PlaybackSourceRule(
                    id = Uuid.random().toString(),
                    name = probed.siteName,
                    urlTemplate = probed.ruleTemplate,
                    description = "站点探测生成（${probed.endpointUrl}）",
                    isEnabled = true,
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.MACCMS,
                ),
            )
            _siteProbe.value = SiteProbeUiState()
            sendSnackbar("已添加规则：${probed.siteName}")
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun addRule(
        name: String,
        urlTemplate: String,
        description: String = "",
        kind: PlaybackRuleKind = PlaybackRuleKind.PAGE,
        parserType: RuleParserType = RuleParserType.AUTO,
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
                    parserType = parserType,
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
        parserType: RuleParserType = RuleParserType.AUTO,
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
                    parserType = parserType,
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
                    // kind 与 parserType 不匹配时流水线/专用解析器根本没有执行路径，收下只会静默降级成嗅探；
                    // minClientApi 超出本客户端能力级别的规则同理——宁拒收，不跑错语义
                    val (accepted, rejected) = importedList.partition { it.isImportable }
                    if (accepted.isEmpty()) {
                        sendSnackbar("规则组合无效：专用解析器只能配在取源(SOURCE)规则上")
                    } else {
                        settingsRepository.importPlaybackRules(accepted)
                        val dropped = if (rejected.isEmpty()) "" else "，已跳过 ${rejected.size} 条无效或超出客户端版本的规则"
                        sendSnackbar("成功导入 ${accepted.size} 条规则$dropped")
                    }
                }
            }.onFailure {
                sendSnackbar("规则解析失败，请检查 JSON 格式")
            }
        }
    }

    /**
     * 请求 AI 助手接手找源。
     *
     * 这个页面没有站点上下文，所以只交代意图，由助手先问地址，再走既有的
     * 「探查 → 网络审计 → 录制骨架 → 沙箱自测 → 可导入提案」流程。
     *
     * 之所以不再由本页自己发起社区检索：那条路径的搜索词在 GitHub 上零命中，
     * 点下去只会得到「未检索到可用规则」，留着比没有更糟。
     */
    fun requestAiSourceSearch() {
        val prompt = "我想把一个第三方站点适配成播放源规则：先问我要站点地址，再推导并生成可导入的规则提案"
        viewModelScope.launch {
            _events.send(PlaybackRulesUiEvent.OpenAiSourceSearch(prompt))
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
