package com.infinitezerone.minibgm.feature.subject.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.ChineseConverter
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.common.onSuccess
import com.infinitezerone.minibgm.core.data.playback.PlaybackFailureStore
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.navigation.PlayerQueueEntry
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 断点续播落盘节流：进度回调约 500ms 一跳，每 20 跳（约 10 秒）落盘一次。
 * 用计数而不是定时器——避免在 ViewModel 里挂常驻协程。
 */
internal const val POSITION_SAVE_TICKS = 20

/** 位置太短没有恢复价值 */
internal const val MIN_RESUME_POSITION_MS = 5_000L

/**
 * 播放源标签
 */
data class PlayerSourceTab(
    val id: String,
    val name: String,
    val rule: PlaybackSourceRule? = null,
    val isDirect: Boolean = false,
)

/**
 * 分集网格条目
 */
data class PlayerEpisodeItem(
    val id: Long = 0L,
    val sort: Float = 1f,
    val type: Int = 0,
    val name: String = "",
    val nameCn: String = "",
) {
    val displayName: String
        get() =
            nameCn.ifBlank {
                name.ifBlank {
                    "第 ${if (sort == sort.toInt().toFloat()) sort.toInt().toString() else sort.toString()} 话"
                }
            }
}

/**
 * 播放器界面 UI 状态
 */
data class PlayerUiState(
    val subjectId: Long = 0L,
    val episodeId: Long = 0L,
    val streamUrl: String = "",
    val episodeName: String = "",
    val episodeSort: Float = 1f,
    val episodeType: Int = 0,
    val subjectName: String = "",
    val requestHeaders: Map<String, String> = emptyMap(),
    val isWatched: Boolean = false,
    val autoMarked: Boolean = false,
    val error: String? = null,
    /** 本次播放的分集队列；单集播放时长度为 1，选集抽屉与连播按钮仅在 size > 1 时展示 */
    val queue: List<PlayerQueueEntry> = emptyList(),
    val currentIndex: Int = 0,
    /** 播完自动连播下一集（可在选集抽屉内关闭） */
    val autoNextEnabled: Boolean = true,
    /** 当前分集的断点续播位置（毫秒，0 表示没有可恢复的记录；与打卡是两套独立进度） */
    val resumePositionMs: Long = 0L,
    /** 播放源标签列表（包含自备片单与外部规则源） */
    val sources: List<PlayerSourceTab> = emptyList(),
    val selectedSourceIndex: Int = 0,
    /** 全部分集列表（由条目详情持续同步） */
    val episodes: List<PlayerEpisodeItem> = emptyList(),
    /** 异步嗅探直链中 */
    val isResolvingSource: Boolean = false,
) {
    val hasNext: Boolean
        get() {
            if (episodes.size > 1) {
                val idx = episodes.indexOfFirst { it.sort == episodeSort || (it.id > 0 && it.id == episodeId) }
                return idx in 0 until episodes.lastIndex
            }
            return currentIndex < queue.lastIndex
        }

    val hasPrevious: Boolean
        get() {
            if (episodes.size > 1) {
                val idx = episodes.indexOfFirst { it.sort == episodeSort || (it.id > 0 && it.id == episodeId) }
                return idx > 0
            }
            return currentIndex > 0
        }

    val currentSource: PlayerSourceTab?
        get() = sources.getOrNull(selectedSourceIndex)
}

/**
 * 播放器一次性单发事件
 */
sealed interface PlayerUiEvent {
    data class ShowSnackbar(
        val message: String,
    ) : PlayerUiEvent

    data class MarkedWatched(
        val epNumber: Int,
    ) : PlayerUiEvent
}

/**
 * 播放器 ViewModel：
 * 维护分集队列中的当前播放会话（换集/连播）、处理自动打卡标记（观看进度达到阈值或播放完成时触发）、
 * 断点续播位置的节流落盘与恢复点下发、鉴权校验（未登录拦截）、错误提示与失败归因回传、
 * 播放源切换（直链/外部源）与分集流嗅探联动。
 */
class PlayerViewModel(
    private val route: PlayerRoute,
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val failureStore: PlaybackFailureStore? = null,
    private val subjectRepository: SubjectRepository? = null,
    private val playbackResolverRepository: PlaybackResolverRepository? = null,
) : ViewModel() {
    /** 播放队列：调用方给了分集队列就用之，否则退化为单集播放（兼容空直链占位启动） */
    private val queue: List<PlayerQueueEntry> =
        route.queue.ifEmpty {
            listOf(
                PlayerQueueEntry(
                    streamUrl = route.streamUrl,
                    episodeName = route.episodeName,
                    episodeSort = route.episodeSort,
                    episodeType = route.episodeType,
                    episodeId = route.episodeId,
                    requestHeaders = route.requestHeaders,
                ),
            )
        }

    private var currentIndex = route.startIndex.coerceIn(queue.indices)

    private var hasTriggeredAutoMark = false

    /** 断点续播位置表（由 [settingsRepository.playbackPositions] 持续同步） */
    private var positionsMap: Map<String, Long> = emptyMap()

    /** 界面回报的当前播放位置（内存暂存，按 [POSITION_SAVE_TICKS] 节流落盘） */
    private var latestPositionMs = 0L

    private var progressTicksSinceSave = 0

    private var subjectOriginalName: String = ""

    private var resolveJob: Job? = null

    private fun initialEpisodes(): List<PlayerEpisodeItem> =
        if (route.queue.isNotEmpty()) {
            route.queue.map { entry ->
                PlayerEpisodeItem(
                    id = entry.episodeId,
                    sort = entry.episodeSort,
                    type = entry.episodeType,
                    name = entry.episodeName,
                )
            }
        } else {
            listOf(
                PlayerEpisodeItem(
                    id = route.episodeId,
                    sort = route.episodeSort,
                    type = route.episodeType,
                    name = route.episodeName,
                ),
            )
        }

    private fun buildSources(rules: List<PlaybackSourceRule>): List<PlayerSourceTab> {
        val list = mutableListOf<PlayerSourceTab>()
        val hasDirect = route.queue.isNotEmpty() || route.streamUrl.isNotBlank()
        if (hasDirect) {
            list.add(
                PlayerSourceTab(
                    id = "direct",
                    name = if (route.queue.size > 1) "自备片单" else "默认直链",
                    isDirect = true,
                ),
            )
        }
        rules.filter { it.isEnabled }.forEach { rule ->
            list.add(
                PlayerSourceTab(
                    id = rule.id,
                    name = rule.name,
                    rule = rule,
                    isDirect = false,
                ),
            )
        }
        return list
    }

    private fun createInitialState(): PlayerUiState {
        val entry = queue[currentIndex]
        val initialSources = buildSources(emptyList())
        return PlayerUiState(
            subjectId = route.subjectId,
            episodeId = entry.episodeId,
            streamUrl = entry.streamUrl,
            episodeName = entry.episodeName,
            episodeSort = entry.episodeSort,
            episodeType = entry.episodeType,
            subjectName = route.subjectName,
            requestHeaders = entry.requestHeaders,
            queue = queue,
            currentIndex = currentIndex,
            autoNextEnabled = true,
            resumePositionMs = 0L,
            sources = initialSources,
            selectedSourceIndex = 0,
            episodes = initialEpisodes(),
            isResolvingSource = false,
        )
    }

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlayerUiEvent>(Channel.BUFFERED)
    val events: Flow<PlayerUiEvent> = _events.receiveAsFlow()

    private val isLoggedIn =
        authRepository.isLoggedIn
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // 1. 订阅断点续播位置
        viewModelScope.launch {
            settingsRepository.playbackPositions.collect { map ->
                positionsMap = map
                _uiState.update { state ->
                    // 尚无可恢复点时跟随位置表刷新当前分集（已下发/消费过的不回退）
                    if (state.resumePositionMs == 0L && state.streamUrl.isNotBlank()) {
                        state.copy(resumePositionMs = map[state.streamUrl] ?: 0L)
                    } else {
                        state
                    }
                }
            }
        }

        // 2. 订阅播放规则并初始化可用源列表
        viewModelScope.launch {
            settingsRepository.playbackRules.collect { rules ->
                val newSources = buildSources(rules)
                val targetRuleIndex =
                    if (route.initialRuleId.isNotBlank()) {
                        newSources.indexOfFirst { it.rule?.id == route.initialRuleId }.takeIf { it >= 0 }
                    } else {
                        null
                    }
                _uiState.update { state ->
                    val newIndex =
                        targetRuleIndex
                            ?: state.selectedSourceIndex.coerceIn(0, (newSources.size - 1).coerceAtLeast(0))
                    state.copy(
                        sources = newSources,
                        selectedSourceIndex = newIndex,
                    )
                }
                // 若当前没有有效直链且有选中的规则源，自动触发嗅探
                val currentState = _uiState.value
                val selectedTab = currentState.sources.getOrNull(currentState.selectedSourceIndex)
                if (currentState.streamUrl.isBlank() && selectedTab != null && !selectedTab.isDirect) {
                    resolveCurrentEpisodeStream()
                }
            }
        }

        // 3. 若有 subjectId，加载番剧详情（补充标题）与全部分集列表
        if (route.subjectId > 0) {
            viewModelScope.launch {
                subjectRepository?.getSubjectStream(route.subjectId)?.collect { subject ->
                    if (subject != null) {
                        subjectOriginalName = subject.name
                        _uiState.update { current ->
                            if (current.subjectName.isBlank()) {
                                current.copy(subjectName = subject.displayName)
                            } else {
                                current
                            }
                        }
                    }
                }
            }

            viewModelScope.launch {
                subjectRepository?.getEpisodesStream(route.subjectId)?.collect { eps ->
                    if (eps.isNotEmpty()) {
                        val mapped =
                            eps
                                .filter { it.type == 0 || it.type == 1 }
                                .sortedWith(compareBy({ it.type }, { it.sort }))
                                .map { ep ->
                                    PlayerEpisodeItem(
                                        id = ep.id,
                                        sort = ep.sort,
                                        type = ep.type,
                                        name = ep.name,
                                        nameCn = ep.nameCn,
                                    )
                                }
                        if (mapped.isNotEmpty()) {
                            _uiState.update { current ->
                                current.copy(episodes = mapped)
                            }
                        }
                    }
                }
            }

            viewModelScope.launch {
                subjectRepository?.fetchEpisodes(route.subjectId)
                if (route.subjectName.isBlank()) {
                    subjectRepository?.fetchSubjectDetail(route.subjectId)
                }
            }
        }
    }

    /** 界面周期回报播放进度（毫秒）：内存暂存，每 [POSITION_SAVE_TICKS] 跳落盘一次 */
    fun onProgressChanged(positionMs: Long) {
        if (positionMs <= 0L) return
        latestPositionMs = positionMs
        if (++progressTicksSinceSave >= POSITION_SAVE_TICKS) {
            progressTicksSinceSave = 0
            flushPlaybackPosition()
        }
    }

    /**
     * 把内存中的当前分集位置落盘（立即捕获 url/位置再异步写，换集时先于状态切换调用）。
     * 由节流循环与界面 ON_PAUSE 触发。
     */
    fun flushPlaybackPosition() {
        val url = _uiState.value.streamUrl
        val position = latestPositionMs
        if (url.isBlank() || position <= 0L) return
        viewModelScope.launch { settingsRepository.savePlaybackPosition(url, position) }
    }

    /**
     * 当视频播放进度达到 85% 或播放结束时调用：
     * 自动向 Bangumi 提交当前分集的看过标记（一次播放会话仅触发一次）。
     */
    fun markWatched(epNumber: Int) {
        if (hasTriggeredAutoMark || _uiState.value.isWatched) return
        if (!isLoggedIn.value) {
            return
        }

        hasTriggeredAutoMark = true
        viewModelScope.launch {
            val result =
                collectionRepository.updateEpisodeStatus(
                    subjectId = _uiState.value.subjectId,
                    episodeId = _uiState.value.episodeId,
                    isWatched = true,
                    epNumber = epNumber,
                )
            result
                .onSuccess {
                    _uiState.update { it.copy(isWatched = true, autoMarked = true) }
                    _events.send(PlayerUiEvent.MarkedWatched(epNumber))
                    _events.send(PlayerUiEvent.ShowSnackbar("已自动标记为看过（第 $epNumber 话）"))
                }.onError { _, message ->
                    hasTriggeredAutoMark = false
                    _events.send(PlayerUiEvent.ShowSnackbar(message))
                }
        }
    }

    /** 观看进度达到 85% 阈值时由界面调用：对当前分集打卡 */
    fun onWatchThresholdReached() {
        markWatched(_uiState.value.episodeSort.toInt())
    }

    /**
     * 播放结束时由界面调用：对当前分集打卡、清除其续播点（看完的内容无需再恢复），
     * 随后在开启连播且有下一集时自动切换。
     * @return 是否已自动切到下一集（false 时界面展示重播按钮）
     */
    fun onPlaybackEnded(): Boolean {
        val state = _uiState.value
        markWatched(state.episodeSort.toInt())
        val finishedUrl = state.streamUrl
        latestPositionMs = 0L
        progressTicksSinceSave = 0
        viewModelScope.launch { settingsRepository.clearPlaybackPosition(finishedUrl) }
        if (!state.autoNextEnabled) return false

        // 优先根据全部分集列表查找下一集
        val epIndex =
            state.episodes.indexOfFirst {
                it.sort == state.episodeSort || (it.id > 0 && it.id == state.episodeId)
            }
        if (epIndex in 0 until state.episodes.lastIndex) {
            selectEpisode(state.episodes[epIndex + 1])
            return true
        }

        if (state.hasNext) {
            switchTo(state.currentIndex + 1)
            return true
        }
        return false
    }

    /** 全屏内选集抽屉换集：先落盘旧分集位置，再切换 */
    fun switchTo(index: Int) {
        if (index !in queue.indices || index == _uiState.value.currentIndex) return
        flushPlaybackPosition()
        latestPositionMs = 0L
        progressTicksSinceSave = 0
        hasTriggeredAutoMark = false
        currentIndex = index
        val entry = queue[index]
        _uiState.update {
            it.copy(
                episodeId = entry.episodeId,
                streamUrl = entry.streamUrl,
                episodeName = entry.episodeName,
                episodeSort = entry.episodeSort,
                episodeType = entry.episodeType,
                requestHeaders = entry.requestHeaders,
                currentIndex = index,
                isWatched = false,
                autoMarked = false,
                resumePositionMs = positionsMap[entry.streamUrl] ?: 0L,
            )
        }
    }

    /** 切换播放源 */
    fun selectSource(index: Int) {
        val state = _uiState.value
        if (index !in state.sources.indices || index == state.selectedSourceIndex) return
        flushPlaybackPosition()
        _uiState.update { it.copy(selectedSourceIndex = index) }
        resolveCurrentEpisodeStream()
    }

    /** 选中具体分集并播放 */
    fun selectEpisode(episode: PlayerEpisodeItem) {
        val state = _uiState.value
        if (episode.sort == state.episodeSort && episode.id == state.episodeId && state.streamUrl.isNotBlank()) return
        flushPlaybackPosition()
        latestPositionMs = 0L
        progressTicksSinceSave = 0
        hasTriggeredAutoMark = false

        val queueIndex =
            queue.indexOfFirst {
                (it.episodeId > 0 && it.episodeId == episode.id) ||
                    (it.episodeSort > 0 && it.episodeSort == episode.sort)
            }
        if (queueIndex >= 0) {
            currentIndex = queueIndex
        }

        _uiState.update {
            it.copy(
                episodeId = episode.id,
                episodeName = episode.displayName,
                episodeSort = episode.sort,
                episodeType = episode.type,
                currentIndex = if (queueIndex >= 0) queueIndex else it.currentIndex,
                isWatched = false,
                autoMarked = false,
                streamUrl = if (state.currentSource?.isDirect == true && queueIndex >= 0) queue[queueIndex].streamUrl else "",
                requestHeaders =
                    if (state.currentSource?.isDirect == true &&
                        queueIndex >= 0
                    ) {
                        queue[queueIndex].requestHeaders
                    } else {
                        emptyMap()
                    },
            )
        }
        resolveCurrentEpisodeStream()
    }

    /**
     * 针对当前选中的播放源与分集，解析可播放的媒体直链
     */
    fun resolveCurrentEpisodeStream() {
        val state = _uiState.value
        val currentTab = state.sources.getOrNull(state.selectedSourceIndex) ?: return
        if (currentTab.isDirect) {
            // 直链/自备片单：从队列或 route 中寻找
            val matchingEntry =
                state.queue.find {
                    (it.episodeId > 0 && it.episodeId == state.episodeId) ||
                        (it.episodeSort > 0 && it.episodeSort == state.episodeSort)
                } ?: state.queue.getOrNull(state.currentIndex)
            if (matchingEntry != null && matchingEntry.streamUrl.isNotBlank()) {
                _uiState.update {
                    it.copy(
                        streamUrl = matchingEntry.streamUrl,
                        requestHeaders = matchingEntry.requestHeaders,
                        isResolvingSource = false,
                        error = null,
                    )
                }
            }
            return
        }

        val rule = currentTab.rule ?: return
        val resolver = playbackResolverRepository
        if (resolver == null) {
            _uiState.update {
                it.copy(
                    isResolvingSource = false,
                    error = "流解析器未配置",
                )
            }
            return
        }

        resolveJob?.cancel()
        resolveJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isResolvingSource = true, error = null) }
                try {
                    val epSort = _uiState.value.episodeSort
                    val epNumStr =
                        if (epSort == epSort.toInt().toFloat()) {
                            epSort.toInt().toString()
                        } else {
                            epSort.toString()
                        }
                    val primaryTitle = _uiState.value.subjectName.ifBlank { route.subjectName }
                    val traditionalTitle = ChineseConverter.toTraditional(primaryTitle)
                    val queryTitles =
                        listOf(primaryTitle, traditionalTitle, subjectOriginalName)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .ifEmpty { listOf("") }

                    var playable: com.infinitezerone.minibgm.core.model.PlayableSource? = null
                    for (queryTitle in queryTitles) {
                        val targetUrl =
                            rule.resolveUrl(
                                title = queryTitle,
                                ep = epNumStr,
                                subjectId = _uiState.value.subjectId,
                                episodeId = _uiState.value.episodeId,
                            )

                        val candidates =
                            if (rule.kind == PlaybackRuleKind.SOURCE) {
                                resolver.resolveTemplate(
                                    url = targetUrl,
                                    headers = rule.headers,
                                    epNumber = epSort,
                                    siteName = rule.name,
                                )
                            } else {
                                resolver.resolvePages(
                                    pageUrls = listOf(targetUrl),
                                    epNumber = epSort,
                                    siteName = rule.name,
                                )
                            }

                        val directCandidate = candidates.firstOrNull { it.kind == PlaylistEntryKind.DIRECT }
                        if (directCandidate != null && directCandidate.url.isNotBlank()) {
                            playable = directCandidate
                            break
                        }
                        if (playable == null) {
                            playable = candidates.firstOrNull()
                        }
                    }

                    if (playable != null && playable.url.isNotBlank()) {
                        _uiState.update {
                            it.copy(
                                streamUrl = playable.url,
                                requestHeaders = playable.headers,
                                isResolvingSource = false,
                                error = null,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isResolvingSource = false,
                                error = "未在【${rule.name}】中嗅探到可播放直链，可尝试切换其他播放源",
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isResolvingSource = false,
                            error = "解析失败: ${e.message ?: "未知异常"}",
                        )
                    }
                }
            }
    }

    fun toggleAutoNext() {
        _uiState.update { it.copy(autoNextEnabled = !it.autoNextEnabled) }
    }

    /** 重试当前地址：清掉错误态，由界面重新 prepare 或重新嗅探 */
    fun retry() {
        _uiState.update { it.copy(error = null) }
        val state = _uiState.value
        val currentTab = state.sources.getOrNull(state.selectedSourceIndex)
        if (currentTab != null && !currentTab.isDirect) {
            resolveCurrentEpisodeStream()
        }
    }

    /** 播放成功建立（STATE_READY）：清除该地址的失败标记 */
    fun onPlaybackReady() {
        failureStore?.markPlayable(_uiState.value.streamUrl)
    }

    fun onPlaybackError(message: String) {
        _uiState.update { it.copy(error = message) }
        failureStore?.markFailed(_uiState.value.streamUrl, message)
    }
}
