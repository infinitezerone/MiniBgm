package com.infinitezerone.minibgm.feature.subject.player

import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters

/** 引擎侧播放阶段；与 media3 的 `Player.STATE_*` 解耦，便于纯 JVM 单测。 */
enum class EnginePlaybackState { IDLE, BUFFERING, READY, ENDED }

/** 引擎向 [PlayerController] 回传的播放事件。 */
interface PlayerEngineListener {
    fun onIsPlayingChanged(isPlaying: Boolean)

    fun onPlayWhenReadyChanged(playWhenReady: Boolean) {}

    fun onPlaybackStateChanged(state: EnginePlaybackState)

    fun onDurationChanged(durationMs: Long)

    fun onError(message: String)

    /** 可选轨道集合变化（清晰度/字幕/音轨快照重建的触发源） */
    fun onTracksChanged() {}

    /** 轨道选择参数变化（用户切换清晰度/字幕/音轨后选中态快照需重建） */
    fun onTrackSelectionParametersChanged() {}

    /** 视频真实尺寸变化（宽高比跟随的触发源） */
    fun onVideoSizeChanged(
        width: Int,
        height: Int,
    ) {}
}

/**
 * 播放引擎窄接口：把 ExoPlayer 收在一个薄边界后面。
 *
 * 一是让 [PlayerController] 能纯 JVM 单测（`FakePlayerEngine`）；二是将来切换成
 * `MediaController`（后台播放）时，UI 层持有的仍是同一个 `Player` 抽象，无需改动。
 */
interface PlayerEngine {
    /** 真实 `Player`，仅供 PlayerSurface / MediaSession 等需要它的 Compose/系统层使用。 */
    val player: Player

    val currentPositionMs: Long

    val bufferedPositionMs: Long get() = currentPositionMs

    val durationMs: Long

    val isPlaying: Boolean

    val playWhenReady: Boolean get() = isPlaying

    fun setListener(listener: PlayerEngineListener?)

    /** 只更新请求头 holder，不触发重新加载（供 URL 不变时的换源使用）。 */
    fun setRequestHeaders(headers: Map<String, String>)

    fun load(
        url: String,
        headers: Map<String, String>,
    )

    fun retry()

    fun clear()

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun setPlaybackSpeed(speed: Float)

    /** 单集循环播放开关（REPEAT_MODE_ONE / OFF） */
    fun setLooping(enabled: Boolean) {}

    /**
     * 轨道选择参数（清晰度覆盖、字幕/音轨启停）。
     * 收在引擎接口后面，让 [PlayerController] 的轨道命令可以纯 JVM 单测——
     * UI 层不再直接摸 `Player.trackSelectionParameters`。
     */
    var trackSelectionParameters: TrackSelectionParameters

    fun release()
}
