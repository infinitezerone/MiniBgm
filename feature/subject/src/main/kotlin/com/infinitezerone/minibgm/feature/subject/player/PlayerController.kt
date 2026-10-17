package com.infinitezerone.minibgm.feature.subject.player

import android.util.Rational
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
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
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val durationMs: Long = 0L,
    val error: String? = null,
    /** 单集循环播放（设置面板开关） */
    val isLooping: Boolean = false,
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

    /**
     * 播放位置单独成一个流：约 500ms 一跳，若混进 [PlaybackState] 会让整棵 UI 树每跳都重组。
     * 只有真正展示进度的叶子（进度条/时间文本/手势层）才读它。
     */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    /** 轨道快照（清晰度/字幕/音轨），由引擎轨道事件驱动重建，UI 只读不直挂 Player.Listener */
    private val _tracksSnapshot = MutableStateFlow(PlayerTracksSnapshot())
    val tracksSnapshot: StateFlow<PlayerTracksSnapshot> = _tracksSnapshot.asStateFlow()

    /** 视频真实宽高比（黑边剔除与 PiP sourceRectHint 用），null 表示尚未拿到真实尺寸 */
    private val _videoAspectRatio = MutableStateFlow<Rational?>(null)
    val videoAspectRatio: StateFlow<Rational?> = _videoAspectRatio.asStateFlow()

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

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
                _state.update { it.copy(playWhenReady = playWhenReady) }
            }

            override fun onPlaybackStateChanged(state: EnginePlaybackState) {
                val hasMedia = _state.value.mediaUrl.isNotBlank()
                _state.update {
                    it.copy(
                        isBuffering = state == EnginePlaybackState.BUFFERING,
                        isEnded = state == EnginePlaybackState.ENDED && hasMedia,
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
                        if (hasMedia) {
                            _events.trySend(PlayerEngineEvent.Ended)
                        }
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

            override fun onTracksChanged() {
                _tracksSnapshot.value = extractPlayerTracksSnapshot(engine.player)
            }

            override fun onTrackSelectionParametersChanged() {
                _tracksSnapshot.value = extractPlayerTracksSnapshot(engine.player)
            }

            override fun onVideoSizeChanged(
                width: Int,
                height: Int,
            ) {
                // 异常比例（黑边/加载中间态）不覆盖上一个有效值
                videoAspectRatioOf(width, height)?.let { _videoAspectRatio.value = it }
            }
        }

    init {
        engine.setListener(engineListener)
        // 播放器复用（换源不重建）时轨道/尺寸可能已就绪，进入页面即补一次初值。
        // FakePlayerEngine（纯 JVM 单测）不提供真实 Player，按 Fail-Open 跳过
        runCatching {
            _tracksSnapshot.value = extractPlayerTracksSnapshot(engine.player)
            engine.player.videoSize.let { vs ->
                _videoAspectRatio.value = videoAspectRatioOf(vs.width, vs.height)
            }
        }
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
        // 单集循环由引擎的 repeatMode 持有、且是用户级偏好：换源/换集只重置媒体相关字段，
        // 必须把 isLooping 一并带过去，否则界面显示"已关闭"而引擎仍在循环（开关要点两次才关得掉）。
        val looping = _state.value.isLooping
        if (url.isBlank()) {
            stopPositionPolling()
            engine.clear()
            _positionMs.value = 0L
            _bufferedPositionMs.value = 0L
            _state.value = PlaybackState(isLooping = looping)
            return
        }
        if (url == _state.value.mediaUrl && _state.value.error == null) return
        _positionMs.value = 0L
        _bufferedPositionMs.value = 0L
        _state.value =
            PlaybackState(
                mediaUrl = url,
                isBuffering = true,
                playWhenReady = engine.playWhenReady,
                isLooping = looping,
            )
        resetTrackOverridesOnMediaChange()
        engine.load(url, requestHeaders)
    }

    private fun resetTrackOverridesOnMediaChange() {
        val params = engine.trackSelectionParameters
        // 只清「跟着上一份媒体选的」轨道覆盖：override 里存的是上一份媒体的轨道组下标，
        // 换源后必须清掉，让引擎按语言偏好（setPreferredTextLanguages）重新选一条。
        // 但 disabledTrackTypes 是用户级偏好（用户显式点了「关闭字幕」），属"媒体态重置不得吞掉"的字段，
        // 严禁在此一并 setTrackTypeDisabled(TEXT, false)——那会让用户每换一条线路/换一集都要重关一次字幕。
        if (params.overrides.isNotEmpty()) {
            engine.trackSelectionParameters =
                params
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .build()
        }
    }

    /** 重试当前地址（保留 URL 与请求头）。 */
    fun retry() {
        if (_state.value.mediaUrl.isBlank()) return
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

            engine.isPlaying || engine.playWhenReady -> engine.pause()
            else -> engine.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        engine.seekTo(target)
        _positionMs.value = target
        _state.update { it.copy(isEnded = false) }
    }

    fun setPlaybackSpeed(speed: Float) {
        engine.setPlaybackSpeed(speed)
    }

    /** 单集循环播放开关 */
    fun setLooping(enabled: Boolean) {
        engine.setLooping(enabled)
        _state.update { it.copy(isLooping = enabled) }
    }

    /** 切换清晰度为指定档位（轨道覆盖）。 */
    fun selectVideoQuality(quality: PlayerQualityOption) {
        val group = quality.group ?: return
        engine.trackSelectionParameters =
            engine.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, quality.trackIndex))
                .build()
    }

    /** 切换清晰度为自适应模式（ABR）：清除视频覆盖，交给自适应选择。 */
    fun selectAutoVideoQuality() {
        engine.trackSelectionParameters =
            engine.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
    }

    /** 切换字幕为指定轨道。 */
    fun selectSubtitleTrack(track: PlayerTrackOption) {
        engine.trackSelectionParameters =
            engine.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex))
                .build()
    }

    /** 关闭字幕。 */
    fun disableSubtitles() {
        engine.trackSelectionParameters =
            engine.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
    }

    /** 自动选择字幕（遵循 ExoPlayer 与系统首选语言配置）。 */
    fun autoSelectSubtitles() {
        engine.trackSelectionParameters =
            engine.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
    }

    /**
     * 应用「关闭字幕」的长期偏好（进页时按设备侧偏好恢复；幂等）。
     *
     * 只处理「关」这一个方向：偏好为「未关闭」时不动引擎——`false` 只代表"用户没有要求关闭"，
     * 并不代表"要强制打开并清掉轨道选择"，引擎该按系统语言自动选轨（`autoSelectSubtitles`）。
     * 与 [resetTrackOverridesOnMediaChange] 同一立场：轨道 override 跟着具体媒体走，
     * 这条偏好只表达"关不关"。
     */
    fun applySubtitlesDisabledPreference(disabled: Boolean) {
        if (!disabled) return
        if (engine.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) return
        engine.trackSelectionParameters =
            engine.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
    }

    /** 主动刷新轨道快照（供打开设置面板等场景确保轨道列表最新）。 */
    fun refreshTracksSnapshot() {
        runCatching {
            _tracksSnapshot.value = extractPlayerTracksSnapshot(engine.player)
        }
    }

    /** 切换音轨为指定轨道。 */
    fun selectAudioTrack(track: PlayerTrackOption) {
        engine.trackSelectionParameters =
            engine.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex))
                .build()
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
        val buffered = engine.bufferedPositionMs
        val duration = engine.durationMs
        _positionMs.value = position
        _bufferedPositionMs.value = buffered
        _state.update {
            it.copy(durationMs = if (duration > 0L) duration else it.durationMs)
        }
    }

    internal companion object {
        /** 位置轮询间隔：与旧实现一致，约 500ms 一跳。 */
        const val DEFAULT_POSITION_POLL_INTERVAL_MS = 500L
    }
}

/** 视频真实宽高比：比例在常见动画长宽范围内才采用，避免 0 宽高或异常比例污染布局 */
internal fun videoAspectRatioOf(
    width: Int,
    height: Int,
): Rational? {
    if (width <= 0 || height <= 0) return null
    val ratio = width.toFloat() / height.toFloat()
    return if (ratio in 0.41841f..2.39f) Rational(width, height) else null
}
