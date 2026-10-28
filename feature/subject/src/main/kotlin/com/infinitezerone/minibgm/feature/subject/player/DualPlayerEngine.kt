package com.infinitezerone.minibgm.feature.subject.player

import android.content.Context
import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters

/**
 * 智能双播放内核：将 ExoPlayer 与 MPV 聚合为单一 [PlayerEngine]。
 *
 * 分流策略：
 * 1. 本地离线 MKV 视频（通常包含 ASS 特效字幕与内嵌字体）：自动路由给 [MpvPlayerEngine]，100% 还原字幕特效；
 * 2. 在线流媒体（HLS / 直链 / 需清洗广告的 m3u8）：默认路由给 [ExoPlayerEngine]，兼顾极速起播与低功耗。
 */
class DualPlayerEngine(
    context: Context,
) : PlayerEngine {
    private val exoEngine = ExoPlayerEngine(context)
    private val mpvEngine by lazy { MpvPlayerEngine(context, exoEngine.player) }

    private var activeEngine: PlayerEngine = exoEngine
    private var externalListener: PlayerEngineListener? = null
    private var cachedSurface: Surface? = null

    override val player: Player get() = exoEngine.player

    override val isMpv: Boolean get() = activeEngine === mpvEngine

    override val currentPositionMs: Long get() = activeEngine.currentPositionMs

    override val bufferedPositionMs: Long get() = activeEngine.bufferedPositionMs

    override val durationMs: Long get() = activeEngine.durationMs

    override val isPlaying: Boolean get() = activeEngine.isPlaying

    override val playWhenReady: Boolean get() = activeEngine.playWhenReady

    override fun setListener(listener: PlayerEngineListener?) {
        externalListener = listener
        activeEngine.setListener(listener)
    }

    override fun setRequestHeaders(headers: Map<String, String>) {
        activeEngine.setRequestHeaders(headers)
    }

    override fun load(
        url: String,
        headers: Map<String, String>,
        startPositionMs: Long,
    ) {
        val targetEngine = if (isMpvPreferred(url)) mpvEngine else exoEngine

        if (targetEngine !== activeEngine) {
            activeEngine.pause()
            activeEngine.clear()
            activeEngine.setListener(null)
            activeEngine.detachSurface()

            activeEngine = targetEngine
            activeEngine.setListener(externalListener)
            cachedSurface?.let { activeEngine.attachSurface(it) }
        }

        activeEngine.load(url, headers, startPositionMs)
    }

    private fun isMpvPreferred(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        // 本地离线下载的动漫大包通常为 MKV 封装并带有复杂 ASS 字幕
        return lower.endsWith(".mkv") ||
            lower.contains(".mkv?") ||
            (lower.contains("/downloads/") && lower.contains(".mkv"))
    }

    override fun attachSurface(surface: Surface) {
        cachedSurface = surface
        activeEngine.attachSurface(surface)
    }

    override fun detachSurface() {
        cachedSurface = null
        activeEngine.detachSurface()
    }

    override fun retry() = activeEngine.retry()

    override fun clear() = activeEngine.clear()

    override fun play() = activeEngine.play()

    override fun pause() = activeEngine.pause()

    override fun seekTo(positionMs: Long) = activeEngine.seekTo(positionMs)

    override fun setPlaybackSpeed(speed: Float) = activeEngine.setPlaybackSpeed(speed)

    override fun setLooping(enabled: Boolean) = activeEngine.setLooping(enabled)

    override var trackSelectionParameters: TrackSelectionParameters
        get() = activeEngine.trackSelectionParameters
        set(value) {
            activeEngine.trackSelectionParameters = value
        }

    override fun extractTracksSnapshot(): PlayerTracksSnapshot = activeEngine.extractTracksSnapshot()

    override fun selectTrack(
        type: EngineTrackType,
        id: String,
    ) = activeEngine.selectTrack(type, id)

    override fun clearTrackOverride(type: EngineTrackType) = activeEngine.clearTrackOverride(type)

    override fun release() {
        exoEngine.release()
        if (isMpv) {
            mpvEngine.release()
        }
    }
}
