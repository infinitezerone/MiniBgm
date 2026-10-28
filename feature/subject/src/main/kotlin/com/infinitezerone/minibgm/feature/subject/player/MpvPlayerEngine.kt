package com.infinitezerone.minibgm.feature.subject.player

import android.content.Context
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import com.infinitezerone.minibgm.core.common.bgmLogger
import dev.jdtech.mpv.MPVLib
import java.util.concurrent.atomic.AtomicBoolean

private val logger = bgmLogger("Bgm/MpvEngine")

/**
 * [PlayerEngine] 的 MPV 原生内核实现。
 *
 * 专为包含 ASS/SSA 特效字幕、内封特种字体、复杂报纸/招牌对位以及 10-bit HEVC/H.264
 * 的动漫视频提供终极播放支持，底层由 [MPVLib] 原生 C 语言管线驱动。
 */
class MpvPlayerEngine(
    context: Context,
    private val fallbackPlayer: Player,
) : PlayerEngine {
    private val appContext = context.applicationContext
    private var listener: PlayerEngineListener? = null
    private val isInitialized = AtomicBoolean(false)

    private var isPlayingState = false
    private var durationSeconds: Double = 0.0
    private var timePosSeconds: Double = 0.0
    private var cachedTimeSeconds: Double = 0.0
    private var currentSurface: Surface? = null
    private var trackParams: TrackSelectionParameters = TrackSelectionParameters.Builder(context).build()

    private val eventObserver =
        object : MPVLib.EventObserver {
            override fun eventProperty(property: String) {}

            override fun eventProperty(
                property: String,
                value: Long,
            ) {
                when (property) {
                    "width" -> {
                        val height = MPVLib.getPropertyInt("height") ?: 0
                        if (value > 0 && height > 0) {
                            listener?.onVideoSizeChanged(value.toInt(), height)
                        }
                    }
                    "height" -> {
                        val width = MPVLib.getPropertyInt("width") ?: 0
                        if (width > 0 && value > 0) {
                            listener?.onVideoSizeChanged(width, value.toInt())
                        }
                    }
                }
            }

            override fun eventProperty(
                property: String,
                value: Double,
            ) {
                when (property) {
                    "time-pos" -> {
                        timePosSeconds = value
                    }
                    "duration" -> {
                        durationSeconds = value
                        listener?.onDurationChanged((value * 1000.0).toLong())
                    }
                    "demuxer-cache-time" -> {
                        cachedTimeSeconds = value
                    }
                }
            }

            override fun eventProperty(
                property: String,
                value: Boolean,
            ) {
                when (property) {
                    "pause" -> {
                        isPlayingState = !value
                        listener?.onIsPlayingChanged(isPlayingState)
                        listener?.onPlayWhenReadyChanged(isPlayingState)
                        if (isPlayingState) {
                            listener?.onPlaybackStateChanged(EnginePlaybackState.READY)
                        }
                    }
                    "paused-for-cache" -> {
                        if (value) {
                            listener?.onPlaybackStateChanged(EnginePlaybackState.BUFFERING)
                        } else if (isPlayingState) {
                            listener?.onPlaybackStateChanged(EnginePlaybackState.READY)
                        }
                    }
                }
            }

            override fun eventProperty(
                property: String,
                value: String,
            ) {}

            override fun event(eventId: Int) {
                when (eventId) {
                    MPVLib.MPV_EVENT_FILE_LOADED -> {
                        listener?.onPlaybackStateChanged(EnginePlaybackState.READY)
                        listener?.onTracksChanged()
                    }
                    MPVLib.MPV_EVENT_END_FILE -> {
                        listener?.onPlaybackStateChanged(EnginePlaybackState.ENDED)
                    }
                    MPVLib.MPV_EVENT_PLAYBACK_RESTART -> {
                        listener?.onPlaybackStateChanged(EnginePlaybackState.READY)
                    }
                }
            }
        }

    private fun ensureInitialized() {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                MPVLib.create(appContext)
                MPVLib.setOptionString("vo", "gpu")
                MPVLib.setOptionString("gpu-context", "android")
                MPVLib.setOptionString("hwdec", "mediacodec")
                MPVLib.setOptionString("sub-auto", "all")
                MPVLib.setOptionString("sub-font-provider", "none")
                MPVLib.setOptionString("sub-ass-override", "no")
                MPVLib.setOptionString("blend-subtitles", "yes")
                MPVLib.init()

                MPVLib.addObserver(eventObserver)
                MPVLib.observeProperty("time-pos", MPVLib.MPV_FORMAT_DOUBLE)
                MPVLib.observeProperty("duration", MPVLib.MPV_FORMAT_DOUBLE)
                MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG)
                MPVLib.observeProperty("paused-for-cache", MPVLib.MPV_FORMAT_FLAG)
                MPVLib.observeProperty("demuxer-cache-time", MPVLib.MPV_FORMAT_DOUBLE)
                MPVLib.observeProperty("width", MPVLib.MPV_FORMAT_INT64)
                MPVLib.observeProperty("height", MPVLib.MPV_FORMAT_INT64)
            } catch (t: Throwable) {
                logger.e(t) { "MpvPlayerEngine 初始化失败" }
                isInitialized.set(false)
            }
        }
    }

    override val player: Player get() = fallbackPlayer

    override val isMpv: Boolean get() = true

    override val currentPositionMs: Long
        get() = (timePosSeconds * 1000.0).toLong().coerceAtLeast(0L)

    override val bufferedPositionMs: Long
        get() = (cachedTimeSeconds * 1000.0).toLong().coerceAtLeast(currentPositionMs)

    override val durationMs: Long
        get() = (durationSeconds * 1000.0).toLong().coerceAtLeast(0L)

    override val isPlaying: Boolean get() = isPlayingState

    override val playWhenReady: Boolean get() = isPlayingState

    override fun setListener(listener: PlayerEngineListener?) {
        this.listener = listener
    }

    override fun setRequestHeaders(headers: Map<String, String>) {
        if (headers.isNotEmpty() && isInitialized.get()) {
            val headerString = headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
            MPVLib.setOptionString("http-header-fields", headerString)
        }
    }

    override fun load(
        url: String,
        headers: Map<String, String>,
        startPositionMs: Long,
    ) {
        ensureInitialized()
        listener?.onPlaybackStateChanged(EnginePlaybackState.BUFFERING)
        currentSurface?.let { attachSurface(it) }

        if (headers.isNotEmpty()) {
            val headerString = headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
            MPVLib.setOptionString("http-header-fields", headerString)
        }

        if (startPositionMs > 0L) {
            val startSeconds = startPositionMs / 1000.0
            MPVLib.command(arrayOf("loadfile", url, "replace", "start=$startSeconds"))
        } else {
            MPVLib.command(arrayOf("loadfile", url))
        }
        MPVLib.setPropertyBoolean("pause", false)
    }

    override fun attachSurface(surface: Surface) {
        currentSurface = surface
        if (isInitialized.get()) {
            MPVLib.attachSurface(surface)
        }
    }

    override fun detachSurface() {
        currentSurface = null
        if (isInitialized.get()) {
            MPVLib.detachSurface()
        }
    }

    override fun retry() {
        MPVLib.setPropertyBoolean("pause", false)
    }

    override fun clear() {
        if (isInitialized.get()) {
            MPVLib.command(arrayOf("stop"))
        }
        timePosSeconds = 0.0
        durationSeconds = 0.0
        cachedTimeSeconds = 0.0
        isPlayingState = false
    }

    override fun play() {
        if (isInitialized.get()) {
            MPVLib.setPropertyBoolean("pause", false)
        }
    }

    override fun pause() {
        if (isInitialized.get()) {
            MPVLib.setPropertyBoolean("pause", true)
        }
    }

    override fun seekTo(positionMs: Long) {
        if (isInitialized.get()) {
            val seconds = positionMs.coerceAtLeast(0L) / 1000.0
            MPVLib.command(arrayOf("seek", "$seconds", "absolute"))
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (isInitialized.get()) {
            MPVLib.setPropertyDouble("speed", speed.toDouble())
        }
    }

    override fun setLooping(enabled: Boolean) {
        if (isInitialized.get()) {
            MPVLib.setPropertyString("loop-file", if (enabled) "inf" else "no")
        }
    }

    override var trackSelectionParameters: TrackSelectionParameters
        get() = trackParams
        set(value) {
            trackParams = value
            if (isInitialized.get()) {
                if (value.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
                    MPVLib.setPropertyString("sid", "no")
                } else {
                    MPVLib.setPropertyString("sid", "auto")
                }
            }
            listener?.onTrackSelectionParametersChanged()
        }

    override fun release() {
        if (isInitialized.compareAndSet(true, false)) {
            try {
                MPVLib.removeObserver(eventObserver)
                MPVLib.detachSurface()
                MPVLib.destroy()
            } catch (t: Throwable) {
                logger.e(t) { "MpvPlayerEngine 释放失败" }
            }
        }
    }
}
