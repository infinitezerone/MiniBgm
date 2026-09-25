package com.infinitezerone.minibgm.feature.subject.player

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [PlayerController] 发出的一次性事件：一次性语义的东西（就绪/播完/出错）走事件，
 * 不要塞进 [PlaybackState]，避免重组时重复触发。
 */
sealed interface PlayerEngineEvent {
    data object Ready : PlayerEngineEvent

    data object Ended : PlayerEngineEvent

    data class Error(
        val message: String,
    ) : PlayerEngineEvent
}

/**
 * 播放器界面状态：由 [PlayerController] 从引擎事件 + 位置轮询收敛而来，
 * 取代旧实现里散落在 Composable 中的多个 `remember { mutableStateOf }`。
 */
data class PlaybackState(
    val mediaUrl: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
)

/**
 * 播放器控制器：持有唯一一个 [PlayerEngine]，把命令式播放器操作与状态收敛成单一 [StateFlow]。
 *
 * 纯逻辑、不依赖 Compose，可用 `FakePlayerEngine` 做纯 JVM 单测；需要真实 `Player` 的渲染
 * （PlayerSurface）与系统集成（MediaSession）留在 UI 层。
 */
class PlayerController(
    private val engine: PlayerEngine,
    private val scope: CoroutineScope,
    private val positionPollIntervalMs: Long = DEFAULT_POSITION_POLL_INTERVAL_MS,
) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _events = Channel<PlayerEngineEvent>(Channel.BUFFERED)

    /** 就绪 / 播完 / 出错的一次性事件流。 */
    val events: Flow<PlayerEngineEvent> = _events.receiveAsFlow()

    /** 真实 `Player`，供 UI 层（PlayerSurface / MediaSession）使用。 */
    val player: Player get() = engine.player

    private var pollJob: Job? = null

    private val engineListener =
        object : PlayerEngineListener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionPolling()
                } else {
                    stopPositionPolling()
                    syncPositionOnce()
                }
            }

            override fun onPlaybackStateChanged(state: EnginePlaybackState) {
                _state.update {
                    it.copy(
                        isBuffering = state == EnginePlaybackState.BUFFERING,
                        isEnded = state == EnginePlaybackState.ENDED,
                        durationMs = engine.durationMs,
                    )
                }
                when (state) {
                    EnginePlaybackState.BUFFERING -> startPositionPolling()
                    EnginePlaybackState.READY -> {
                        startPositionPolling()
                        _events.trySend(PlayerEngineEvent.Ready)
                    }

                    EnginePlaybackState.ENDED -> {
                        stopPositionPolling()
                        _events.trySend(PlayerEngineEvent.Ended)
                    }

                    EnginePlaybackState.IDLE -> stopPositionPolling()
                }
            }

            override fun onDurationChanged(durationMs: Long) {
                _state.update { it.copy(durationMs = durationMs) }
            }

            override fun onError(message: String) {
                stopPositionPolling()
                _state.update { it.copy(isBuffering = false, error = message) }
                _events.trySend(PlayerEngineEvent.Error(message))
            }
        }

    init {
        engine.setListener(engineListener)
    }

    /**
     * 对齐当前要播放的媒体：URL 变化才重新加载；URL 相同仅刷新请求头
     * （覆盖换源时 Referer 变化），避免每次换源都重载播放器。
     */
    fun setMedia(
        url: String,
        requestHeaders: Map<String, String>,
    ) {
        engine.setRequestHeaders(requestHeaders)
        if (url.isBlank()) {
            stopPositionPolling()
            engine.clear()
            _state.value = PlaybackState()
            return
        }
        if (url == _state.value.mediaUrl) return
        _state.value = PlaybackState(mediaUrl = url, isBuffering = true)
        engine.load(url, requestHeaders)
    }

    /** 重试当前地址（保留 URL 与请求头）。 */
    fun retry() {
        _state.update { it.copy(error = null, isBuffering = true, isEnded = false) }
        engine.retry()
    }

    fun play() {
        engine.play()
    }

    fun pause() {
        engine.pause()
    }

    fun togglePlayPause() {
        when {
            _state.value.isEnded -> {
                seekTo(0L)
                engine.play()
            }

            engine.isPlaying -> engine.pause()
            else -> engine.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        engine.seekTo(target)
        _state.update { it.copy(positionMs = target, isEnded = false) }
    }

    fun setPlaybackSpeed(speed: Float) {
        engine.setPlaybackSpeed(speed)
    }

    /** 界面离开时调用：停止轮询并释放引擎。 */
    fun release() {
        stopPositionPolling()
        engine.setListener(null)
        engine.release()
    }

    /** 立即把当前位置同步进状态（供界面在 ON_PAUSE 等时机落盘）。 */
    fun syncPosition() = syncPositionOnce()

    private fun startPositionPolling() {
        if (pollJob?.isActive == true) return
        pollJob =
            scope.launch {
                while (isActive) {
                    syncPositionOnce()
                    delay(positionPollIntervalMs)
                }
            }
    }

    private fun stopPositionPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun syncPositionOnce() {
        val position = engine.currentPositionMs
        val duration = engine.durationMs
        _state.update {
            it.copy(
                positionMs = position,
                durationMs = if (duration > 0L) duration else it.durationMs,
            )
        }
    }

    internal companion object {
        /** 位置轮询间隔：与旧实现一致，约 500ms 一跳。 */
        const val DEFAULT_POSITION_POLL_INTERVAL_MS = 500L
    }
}
