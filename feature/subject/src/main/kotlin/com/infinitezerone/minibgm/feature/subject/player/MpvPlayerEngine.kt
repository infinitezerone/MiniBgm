package com.infinitezerone.minibgm.feature.subject.player

import android.content.Context
import android.os.Handler
import android.os.Looper
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
 *
 * 线程契约：mpv 事件回调全部来自原生 event 线程，本类负责把对 [PlayerEngineListener]
 * 的所有回调统一投递回主线程——消费方（[PlayerController]）按 ExoPlayer 语义假设
 * 回调在主线程，这里收口后对上永远是同一份线程模型。
 */
class MpvPlayerEngine(
    context: Context,
    private val fallbackPlayer: Player,
) : PlayerEngine {
    private val appContext = context.applicationContext
    private var listener: PlayerEngineListener? = null
    private val isInitialized = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 事件线程与主线程并发读写，volatile 保证可见性 */
    @Volatile private var isPlayingState = false

    @Volatile private var durationSeconds: Double = 0.0

    @Volatile private var timePosSeconds: Double = 0.0

    @Volatile private var cachedTimeSeconds: Double = 0.0

    private var currentSurface: Surface? = null
    private var trackParams: TrackSelectionParameters = TrackSelectionParameters.Builder(context).build()

    /** loadfile 早于 Surface 就绪时挂起，等 attachSurface 到达再真正加载（vo 初始化需要有效 WinID） */
    private var pendingLoad: PendingLoad? = null

    private data class PendingLoad(
        val url: String,
        val headers: Map<String, String>,
        val startPositionMs: Long,
    )

    /**
     * 对上收敛线程模型：mpv 事件线程到达的回调统一投递主线程。
     * 主线程发起的调用同步执行，避免无谓的异步化。
     */
    private fun dispatchToMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun notify(block: (PlayerEngineListener) -> Unit) {
        dispatchToMain { listener?.let(block) }
    }

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
                            notify { it.onVideoSizeChanged(value.toInt(), height) }
                        }
                    }
                    "height" -> {
                        val width = MPVLib.getPropertyInt("width") ?: 0
                        if (width > 0 && value > 0) {
                            notify { it.onVideoSizeChanged(width, value.toInt()) }
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
                        notify { it.onDurationChanged((value * 1000.0).toLong()) }
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
                        notify { it.onIsPlayingChanged(isPlayingState) }
                        notify { it.onPlayWhenReadyChanged(isPlayingState) }
                        if (isPlayingState) {
                            notify { it.onPlaybackStateChanged(EnginePlaybackState.READY) }
                        }
                    }
                    "paused-for-cache" -> {
                        if (value) {
                            notify { it.onPlaybackStateChanged(EnginePlaybackState.BUFFERING) }
                        } else if (isPlayingState) {
                            notify { it.onPlaybackStateChanged(EnginePlaybackState.READY) }
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
                        notify {
                            it.onPlaybackStateChanged(EnginePlaybackState.READY)
                            it.onTracksChanged()
                        }
                    }
                    MPVLib.MPV_EVENT_END_FILE -> {
                        notify { it.onPlaybackStateChanged(EnginePlaybackState.ENDED) }
                    }
                    MPVLib.MPV_EVENT_PLAYBACK_RESTART -> {
                        notify { it.onPlaybackStateChanged(EnginePlaybackState.READY) }
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
        notify { it.onPlaybackStateChanged(EnginePlaybackState.BUFFERING) }

        // Surface 未就绪（surfaceCreated 尚未回调）时严禁 loadfile：
        // mpv 的 android vo 初始化拿不到有效 WinID 会触发原生 assert 直接 SIGABRT，
        // Kotlin 层无法兜底。挂起待 attachSurface 到达后由 attachSurface 补发加载。
        val surface =
            currentSurface ?: run {
                pendingLoad = PendingLoad(url, headers, startPositionMs)
                return
            }

        // 防引擎重建后 wid 丢失：加载前幂等重挂一次
        attachSurface(surface)
        startLoadfile(url, headers, startPositionMs)
    }

    private fun startLoadfile(
        url: String,
        headers: Map<String, String>,
        startPositionMs: Long,
    ) {
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
            pendingLoad?.let { pending ->
                pendingLoad = null
                startLoadfile(pending.url, pending.headers, pending.startPositionMs)
            }
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
        pendingLoad = null
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
        pendingLoad = null
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
