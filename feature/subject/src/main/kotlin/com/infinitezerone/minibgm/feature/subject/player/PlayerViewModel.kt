package com.infinitezerone.minibgm.feature.subject.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.common.onSuccess
import com.infinitezerone.minibgm.core.data.playback.PlaybackFailureStore
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.navigation.PlayerQueueEntry
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
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
 * 播放器界面 UI 状态
 */
data class PlayerUiState(
    val subjectId: Long = 0L,
    val episodeId: Long = 0L,
    val streamUrl: String = "",
    val episodeName: String = "",
    val episodeSort: Float = 1f,
    val episodeType: Int = 0,
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
) {
    val hasNext: Boolean get() = currentIndex < queue.lastIndex
    val hasPrevious: Boolean get() = currentIndex > 0
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
 * 断点续播位置的节流落盘与恢复点下发、鉴权校验（未登录拦截）、错误提示与失败归因回传。
 */
class PlayerViewModel(
    private val route: PlayerRoute,
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val failureStore: PlaybackFailureStore? = null,
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

    private val _uiState = MutableStateFlow(stateFor(currentIndex))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlayerUiEvent>(Channel.BUFFERED)
    val events: Flow<PlayerUiEvent> = _events.receiveAsFlow()

    private val isLoggedIn =
        authRepository.isLoggedIn
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
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
    }

    private fun stateFor(
        index: Int,
        autoNextEnabled: Boolean = true,
    ): PlayerUiState {
        val entry = queue[index]
        return PlayerUiState(
            subjectId = route.subjectId,
            episodeId = entry.episodeId,
            streamUrl = entry.streamUrl,
            episodeName = entry.episodeName,
            episodeSort = entry.episodeSort,
            episodeType = entry.episodeType,
            requestHeaders = entry.requestHeaders,
            queue = queue,
            currentIndex = index,
            autoNextEnabled = autoNextEnabled,
            resumePositionMs = positionsMap[entry.streamUrl] ?: 0L,
        )
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
        if (!state.autoNextEnabled || !state.hasNext) return false
        switchTo(state.currentIndex + 1)
        return true
    }

    /** 全屏内选集抽屉换集：先落盘旧分集位置，再切换 */
    fun switchTo(index: Int) {
        if (index !in queue.indices || index == _uiState.value.currentIndex) return
        flushPlaybackPosition()
        latestPositionMs = 0L
        progressTicksSinceSave = 0
        hasTriggeredAutoMark = false
        currentIndex = index
        _uiState.value = stateFor(index, _uiState.value.autoNextEnabled)
    }

    fun toggleAutoNext() {
        _uiState.update { it.copy(autoNextEnabled = !it.autoNextEnabled) }
    }

    /** 重试当前地址：清掉错误态，由界面重新 prepare */
    fun retry() {
        _uiState.update { it.copy(error = null) }
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
