package com.infinitezerone.minibgm.feature.subject.player

import android.content.Context
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

    /** 保留 Lazy 句柄：release 需要知道 mpv 是否**曾经**初始化过，而不只是当前是否活跃 */
    private val mpvEngineDelegate =
        lazy { MpvPlayerEngine(context, exoEngine.player) }
    private val mpvEngine by mpvEngineDelegate

    private var activeEngine: PlayerEngine = exoEngine
    private var externalListener: PlayerEngineListener? = null

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

            activeEngine = targetEngine
            activeEngine.setListener(externalListener)
        }

        activeEngine.load(url, headers, startPositionMs)
    }

    private fun isMpvPreferred(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        // ① 容器即 mkv：ASS 特效字幕的最大来源
        if (lower.endsWith(".mkv") || lower.contains(".mkv?")) return true
        // ② BT 边下边播：本地代理流不带扩展名（127.0.0.1:port/stream/N），
        //    无法从 URL 判断容器；种子番剧几乎全是 mkv + ASS，统一交给 mpv
        return isLocalTorrentStream(lower)
    }

    private fun isLocalTorrentStream(lowerUrl: String): Boolean =
        (lowerUrl.startsWith("http://127.0.0.1:") || lowerUrl.startsWith("http://localhost:")) &&
            lowerUrl.contains("/stream")

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
        // 按「是否初始化过」释放，而不是「当前是否活跃」：播过 mkv 再切回在线流后，
        // activeEngine 已经变回 ExoPlayer，旧写法的 isMpv 为 false → mpv 原生句柄与
        // GL 上下文永不释放（泄漏到下次 create 才被顺手终止）
        if (mpvEngineDelegate.isInitialized()) {
            mpvEngine.release()
        }
    }
}
