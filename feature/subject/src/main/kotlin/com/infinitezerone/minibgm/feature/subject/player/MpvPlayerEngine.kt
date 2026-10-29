package com.infinitezerone.minibgm.feature.subject.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import com.infinitezerone.minibgm.core.common.bgmLogger
import java.util.concurrent.atomic.AtomicBoolean

private val logger = bgmLogger("Bgm/MpvEngine")

/** 等 render context 的上限：无 surface（纯音频）时不能无限期不 load */
private const val RENDER_CONTEXT_TIMEOUT_MS = 800L

/** debug 热切换缩放档位的 Settings.Global 键（值为 [SCALER_QUALITY] 时走画质档） */
private const val SCALER_SETTING_KEY = "bgm_mpv_scaler"
private const val SCALER_QUALITY = "quality"

/**
 * [PlayerEngine] 的 MPV 原生内核实现（render API 架构）。
 *
 * 专为 ASS/SSA 特效字幕、内封字体与 10-bit HEVC 的动漫片源提供参考级渲染。
 *
 * 架构要点：
 * - `vo=libmpv` + native EGL 渲染循环：surface 重建只是换 EGLSurface，
 *   旋转/PiP/后台往返零拆卸，没有任何 vo 拆建时序竞态。
 * - loadfile 不依赖 surface：无视图时照常解码出声，视图出现即出画。
 * - 事件回调经 [MpvNative] 事件线程到达，[notify] 统一收口主线程。
 */
class MpvPlayerEngine(
    context: Context,
    private val fallbackPlayer: Player,
) : PlayerEngine {
    private val appContext = context.applicationContext
    private var listener: PlayerEngineListener? = null
    private val isInitialized = AtomicBoolean(false)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 事件线程与主线程并发读写，volatile 保证可见性 */
    @Volatile private var isPlayingState = false

    @Volatile private var durationSeconds: Double = 0.0

    @Volatile private var timePosSeconds: Double = 0.0

    @Volatile private var cachedTimeSeconds: Double = 0.0

    private var hasActiveMedia = false

    /** 当前 loadfile 是否已到 FILE_LOADED（此前 mpv 会静默丢弃 seek 命令） */
    @Volatile private var fileLoaded = false

    /** fileLoaded 之前到达的 seek：FILE_LOADED 后补放，否则续播定位在快速起播时被吞 */
    @Volatile private var pendingSeekMs = -1L

    private var trackParams: TrackSelectionParameters = TrackSelectionParameters.Builder(context).build()

    /**
     * 等待 render context 期间挂起的 loadfile。
     *
     * `vo=libmpv` 的 vo 只初始化一次，若那时上下文还没建好，mpv 会直接
     * `No render context set.` → `Video: no video` 并**永久**丢弃视频轨
     * （症状：只有声音、画面全黑、时间停在 00:00）。GL surface 总是晚于
     * 首次 loadfile 就绪，故 load 先挂起，等 [MpvNative.markRenderContextReady]
     * 或超时兜底（无 surface 的纯音频场景）再真正下命令。
     */
    private var pendingLoad: (() -> Unit)? = null

    /** 当前媒体地址：GL 上下文重建后需要据此重新起播以重绑 vo */
    private var currentUrl: String? = null

    init {
        // 上下文常驻（自持 EGL）：surface 变化只换 EGLSurface，不会再有"重建 mpv"分支
        MpvNative.onRenderContextBecameReady = {
            mainHandler.post { flushPendingLoad() }
        }
    }

    /**
     * 对上收敛线程模型：mpv 事件线程到达的回调统一投递主线程。
     * 主线程发起的调用同步执行，避免无谓的异步化。
     */
    private fun notify(block: (PlayerEngineListener) -> Unit) {
        val target = listener
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            target?.let(block)
        } else {
            mainHandler.post { listener?.let(block) }
        }
    }

    private val nativeListener =
        object : MpvNative.EventListener {
            override fun onEvent(eventId: Int) {
                logger.d { "event=$eventId" }
                when (eventId) {
                    MpvNative.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                        fileLoaded = true
                        // 硬解真实生效值（mediacodec-copy / no / ...），排查软解回退只看这一条
                        logger.d { "hwdec-current=${MpvNative.getPropertyString("hwdec-current")}" }
                        notify {
                            it.onPlaybackStateChanged(EnginePlaybackState.READY)
                            it.onTracksChanged()
                        }
                        pendingSeekMs.takeIf { it >= 0L }?.let { target ->
                            pendingSeekMs = -1L
                            val seconds = target / 1000.0
                            MpvNative.command(arrayOf("seek", "$seconds", "absolute"))
                        }
                    }
                    MpvNative.MpvEvent.MPV_EVENT_END_FILE -> {
                        notify { it.onPlaybackStateChanged(EnginePlaybackState.ENDED) }
                    }
                    MpvNative.MpvEvent.MPV_EVENT_SHUTDOWN -> {
                        fileLoaded = false
                        notify { it.onError("mpv 引擎已退出") }
                    }
                    MpvNative.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                        notify { it.onPlaybackStateChanged(EnginePlaybackState.READY) }
                    }
                }
            }

            override fun onProperty(name: String) {}

            override fun onPropertyLong(
                name: String,
                value: Long,
            ) {
                when (name) {
                    "width" -> {
                        val height = MpvNative.getInt("height") ?: 0
                        if (value > 0 && height > 0) {
                            notify { it.onVideoSizeChanged(value.toInt(), height) }
                        }
                    }
                    "height" -> {
                        val width = MpvNative.getInt("width") ?: 0
                        if (width > 0 && value > 0) {
                            notify { it.onVideoSizeChanged(width, value.toInt()) }
                        }
                    }
                }
            }

            override fun onPropertyDouble(
                name: String,
                value: Double,
            ) {
                when (name) {
                    "time-pos" -> timePosSeconds = value
                    "duration" -> {
                        durationSeconds = value
                        notify { it.onDurationChanged((value * 1000.0).toLong()) }
                    }
                    "demuxer-cache-time" -> cachedTimeSeconds = value
                }
            }

            override fun onPropertyFlag(
                name: String,
                value: Boolean,
            ) {
                when (name) {
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

            override fun onPropertyString(
                name: String,
                value: String,
            ) {}
        }

    private fun ensureInitialized() {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                MpvNative.create(appContext)
                // create 会释放上一轮的 render context（新句柄从零开始），
                // 标志必须同步复位：否则下一轮 load 会误以为上下文还在，
                // 抢在 GL 上下文建好前下发 loadfile → vo 永久丢视频轨（黑屏只剩声音）
                MpvNative.markRenderContextReleased()
                // render API：视频输出由 MpvRenderView 的 GL 上下文承载
                MpvNative.setOptionString("vo", "libmpv")
                // 先要 mpv 自己的零拷贝 mediacodec（内部走 AImageReader + 外部纹理），
                // 失败再退到 ffmpeg 的回拷模式。能否零拷贝取决于 mpv 侧的 JNI/EGL 能力，
                // 实测日志会打印真实生效值（hwdec-current）。
                MpvNative.setOptionString("hwdec", "mediacodec,mediacodec-copy")
                MpvNative.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
                MpvNative.setOptionString("ao", "audiotrack,opensles")
                // 限制 demuxer 缓存：默认 150MiB 在移动端内存压力过大（官方上限 32/64MB）
                MpvNative.setOptionString("demuxer-max-bytes", "${64 * 1024 * 1024}")
                MpvNative.setOptionString("demuxer-max-back-bytes", "${32 * 1024 * 1024}")
                MpvNative.setOptionString("sub-auto", "all")
                MpvNative.setOptionString("sub-font-provider", "none")
                MpvNative.setOptionString("sub-ass-override", "no")
                // 官方默认 no：字幕在输出分辨率上由 OSD 通道绘制，不进视频合成 pass。
                // 旧值 yes 让 libass 光栅化 + 全屏混合都压在渲染线程上（实测 8.9% 单核的主要构成）
                MpvNative.setOptionString("blend-subtitles", "no")
                applyScalerProfile()
                MpvNative.init()

                // 官方姿势：init 后设置——idle=once 防止 stop 后 mpv 核心退出
                MpvNative.setOptionString("idle", "once")

                MpvNative.eventListener = nativeListener
                MpvNative.observeProperty("time-pos", MpvNative.MpvFormat.MPV_FORMAT_DOUBLE)
                MpvNative.observeProperty("duration", MpvNative.MpvFormat.MPV_FORMAT_DOUBLE)
                MpvNative.observeProperty("pause", MpvNative.MpvFormat.MPV_FORMAT_FLAG)
                MpvNative.observeProperty("paused-for-cache", MpvNative.MpvFormat.MPV_FORMAT_FLAG)
                MpvNative.observeProperty("demuxer-cache-time", MpvNative.MpvFormat.MPV_FORMAT_DOUBLE)
                MpvNative.observeProperty("width", MpvNative.MpvFormat.MPV_FORMAT_INT64)
                MpvNative.observeProperty("height", MpvNative.MpvFormat.MPV_FORMAT_INT64)

                logger.d { "mpv 初始化完成 vo=${MpvNative.getPropertyString("vo")}" }
                // 渲染视图通常比 mpv 句柄先就绪（视图在组合阶段创建，句柄在首次 load 时创建），
                // 其 onSurfaceCreated 里的建上下文会因句柄为空而失败——此处补建一次
                MpvNative.retryRenderContext()
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
            MpvNative.setOptionString("http-header-fields", headerString)
        }
    }

    override fun load(
        url: String,
        headers: Map<String, String>,
        startPositionMs: Long,
    ) {
        // render API 下 loadfile 不依赖任何 surface：无视图时照常解码出声，
        // MpvRenderView 出现即出画——没有 wid 时代的时序竞态
        ensureInitialized()
        notify { it.onPlaybackStateChanged(EnginePlaybackState.BUFFERING) }
        hasActiveMedia = true
        fileLoaded = false
        pendingSeekMs = -1L

        if (headers.isNotEmpty()) {
            val headerString = headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
            MpvNative.setOptionString("http-header-fields", headerString)
        }

        val startLoad = { startLoad(url, startPositionMs) }
        currentUrl = url
        if (MpvNative.isRenderContextReady) {
            startLoad()
        } else {
            // 等上下文就绪再 loadfile，否则 vo 初始化失败会永久丢视频
            pendingLoad = startLoad
            mainHandler.postDelayed(::flushPendingLoad, RENDER_CONTEXT_TIMEOUT_MS)
        }
    }

    private fun startLoad(
        url: String,
        startPositionMs: Long,
    ) {
        logger.d { "load url=${url.takeLast(60)} startPos=$startPositionMs" }
        // 注意：loadfile 的 `start=` 只接受**整数秒**，传小数会被 mpv 拒绝
        // （`The loadfile option must be an integer: start=228.567` → 命令整体失败）。
        // 因此这里只下发 loadfile，定位交给 FILE_LOADED 后的 pendingSeek 精确 seek。
        if (startPositionMs > 0L) {
            pendingSeekMs = startPositionMs
        }
        MpvNative.command(arrayOf("loadfile", url, "replace"))
        MpvNative.setPropertyBoolean("pause", false)
    }

    /**
     * 缩放与抖动档位（功耗主要开关）。
     *
     * 官方 mpv-android 的第一行就是 `profile=fast`：bilinear 三件套 + 关抖动 +
     * 关 correct/linear-downscaling + 关 hdr-compute-peak。我们此前全走 mpv 默认
     * （lanczos + fruit 抖动 + 两个 downscaling 后处理 + HDR 峰值统计），
     * 1920×1080 上采样到 2K 屏时这部分全压 GPU，是"比官方 App 费电"的头号嫌疑。
     *
     * 档位：
     * - fast（默认）：与官方一致，省电优先
     * - quality：mpv 默认（lanczos 全开），画质优先
     *
     * debug 构建可热切换（选项只在 init 前生效，退出播放页重进即可）：
     * `adb shell settings put global bgm_mpv_scaler quality`
     */
    private fun applyScalerProfile() {
        val debuggable =
            (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val mode =
            if (debuggable) {
                android.provider.Settings.Global.getString(
                    appContext.contentResolver,
                    SCALER_SETTING_KEY,
                )
            } else {
                null
            }
        if (mode != SCALER_QUALITY) {
            // 官方 profile=fast 的等价集合（见 mpv etc/builtin.conf）
            MpvNative.setOptionString("scale", "bilinear")
            MpvNative.setOptionString("dscale", "bilinear")
            // cscale 默认跟随 scale：不显式设为 bilinear 时，4:2:0→4:4:4 会走 lanczos，
            // 这是每帧最大的一笔色度上采样开销
            MpvNative.setOptionString("cscale", "bilinear")
            MpvNative.setOptionString("dither", "no")
            MpvNative.setOptionString("correct-downscaling", "no")
            MpvNative.setOptionString("linear-downscaling", "no")
            MpvNative.setOptionString("hdr-compute-peak", "no")
        }
        logger.d { "scaler profile=${mode ?: "fast(default)"}" }
    }

    /** 放行挂起的 load（上下文就绪或超时兜底），幂等 */
    private fun flushPendingLoad() {
        val pending = pendingLoad ?: return
        pendingLoad = null
        pending.invoke()
    }

    override fun retry() {
        if (isInitialized.get()) {
            MpvNative.setPropertyBoolean("pause", false)
        }
    }

    override fun clear() {
        hasActiveMedia = false
        fileLoaded = false
        pendingSeekMs = -1L
        pendingLoad = null
        currentUrl = null
        if (isInitialized.get()) {
            MpvNative.command(arrayOf("stop"))
        }
        timePosSeconds = 0.0
        durationSeconds = 0.0
        cachedTimeSeconds = 0.0
        isPlayingState = false
    }

    override fun play() {
        if (isInitialized.get()) {
            MpvNative.setPropertyBoolean("pause", false)
        }
    }

    override fun pause() {
        if (isInitialized.get()) {
            MpvNative.setPropertyBoolean("pause", true)
        }
    }

    override fun seekTo(positionMs: Long) {
        if (!isInitialized.get() || !fileLoaded) {
            // mpv 在 FILE_LOADED 之前会静默丢弃 seek：排队等加载完成后补放
            pendingSeekMs = positionMs.coerceAtLeast(0L)
            return
        }
        val seconds = positionMs.coerceAtLeast(0L) / 1000.0
        MpvNative.command(arrayOf("seek", "$seconds", "absolute"))
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (isInitialized.get()) {
            MpvNative.setPropertyDouble("speed", speed.toDouble())
        }
    }

    override fun setLooping(enabled: Boolean) {
        if (isInitialized.get()) {
            MpvNative.setPropertyString("loop-file", if (enabled) "inf" else "no")
        }
    }

    override var trackSelectionParameters: TrackSelectionParameters
        get() = trackParams
        set(value) {
            trackParams = value
            if (isInitialized.get()) {
                if (value.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
                    MpvNative.setPropertyString("sid", "no")
                } else {
                    MpvNative.setPropertyString("sid", "auto")
                }
            }
            listener?.onTrackSelectionParametersChanged()
        }

    /** 逐条读取 mpv track-list 属性（免 JSON 依赖），映射成引擎中立快照 */
    override fun extractTracksSnapshot(): PlayerTracksSnapshot {
        if (!isInitialized.get()) return PlayerTracksSnapshot()
        val count = MpvNative.getInt("track-list/count") ?: 0
        val qualityOptions = mutableListOf<PlayerQualityOption>()
        val subtitleOptions = mutableListOf<PlayerTrackOption>()
        val audioOptions = mutableListOf<PlayerTrackOption>()

        for (i in 0 until count) {
            val base = "track-list/$i/"
            val type = MpvNative.getPropertyString("${base}type") ?: continue
            val trackId = MpvNative.getInt("${base}id") ?: continue
            val selected =
                MpvNative.getBoolean("${base}selected")
                    ?: ((MpvNative.getInt("${base}selected") ?: 0) == 1)
            val title = MpvNative.getPropertyString("${base}title")
            val lang = resolveLanguageName(MpvNative.getPropertyString("${base}lang"))

            when (type) {
                "video" -> {
                    val width = MpvNative.getInt("${base}demux-w") ?: 0
                    val height = MpvNative.getInt("${base}demux-h") ?: 0
                    qualityOptions.add(
                        PlayerQualityOption(
                            id = "mpv-video-$trackId",
                            name = formatQualityLabel(width, height),
                            // 不显示码率：mpv 顶层 video-bitrate 是当前轨滑动估算值，
                            // 不是文件整体码率，展示会误导
                            detail = formatQualityDetail(width, height, 0),
                            width = width,
                            height = height,
                            bitrate = 0,
                            isSelected = selected,
                        ),
                    )
                }
                "sub" ->
                    subtitleOptions.add(
                        PlayerTrackOption(
                            id = "mpv-sub-$trackId",
                            name = trackDisplayName(title, lang, "字幕", trackId),
                            isSelected = selected,
                        ),
                    )
                "audio" ->
                    audioOptions.add(
                        PlayerTrackOption(
                            id = "mpv-audio-$trackId",
                            name = trackDisplayName(title, lang, "音轨", trackId),
                            isSelected = selected,
                        ),
                    )
            }
        }

        val isTextDisabled = trackParams.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val hasSelectable =
            qualityOptions.isNotEmpty() || subtitleOptions.isNotEmpty() || audioOptions.size > 1

        return PlayerTracksSnapshot(
            qualityTracks =
                qualityOptions.sortedWith(
                    compareByDescending<PlayerQualityOption> { it.height }.thenByDescending { it.bitrate },
                ),
            subtitleTracks = disambiguateTrackOptions(subtitleOptions),
            audioTracks = disambiguateTrackOptions(audioOptions),
            subtitleMode = if (isTextDisabled) SubtitleMode.OFF else SubtitleMode.AUTO,
            hasSelectableTracks = hasSelectable,
        )
    }

    override fun selectTrack(
        type: EngineTrackType,
        id: String,
    ) {
        if (!isInitialized.get()) return
        val trackNo = id.substringAfterLast('-')
        when (type) {
            EngineTrackType.VIDEO -> MpvNative.setPropertyString("vid", trackNo)
            EngineTrackType.AUDIO -> MpvNative.setPropertyString("aid", trackNo)
            EngineTrackType.TEXT -> MpvNative.setPropertyString("sid", trackNo)
        }
    }

    override fun clearTrackOverride(type: EngineTrackType) {
        if (!isInitialized.get()) return
        when (type) {
            EngineTrackType.VIDEO -> MpvNative.setPropertyString("vid", "auto")
            EngineTrackType.AUDIO -> MpvNative.setPropertyString("aid", "auto")
            EngineTrackType.TEXT -> MpvNative.setPropertyString("sid", "auto")
        }
    }

    override fun release() {
        MpvNative.onRenderContextBecameReady = null
        mainHandler.removeCallbacksAndMessages(null)
        pendingLoad = null
        if (isInitialized.compareAndSet(true, false)) {
            try {
                MpvNative.eventListener = null
                MpvNative.destroy()
            } catch (t: Throwable) {
                logger.e(t) { "MpvPlayerEngine 释放失败" }
            }
        }
        pendingSeekMs = -1L
        fileLoaded = false
        hasActiveMedia = false
    }
}

/** mpv 轨道显示名：title 优先，其次解析后的语言名，最后退回「字幕 N / 音轨 N」 */
private fun trackDisplayName(
    title: String?,
    language: String?,
    defaultLabel: String,
    trackId: Int,
): String {
    val parts = listOfNotNull(title?.takeIf { it.isNotBlank() }, language?.takeIf { it.isNotBlank() })
    return parts.joinToString(" · ").ifBlank { "$defaultLabel $trackId" }
}
