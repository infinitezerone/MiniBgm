package com.infinitezerone.minibgm.feature.subject.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.playback.M3u8AdSanitizer
import com.infinitezerone.minibgm.feature.subject.R
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * [PlayerEngine] 的 ExoPlayer 实现。
 *
 * 请求头走**动态 header holder**：`HttpDataSource` 每次 `createDataSource()` 时读取当前头，
 * 因此换源（Referer 变化）只需更新 holder，**不重建 ExoPlayer**——这正是旧实现每次换源
 * 都重建播放器、进而把 PlayerView 与播放器解绑导致黑屏的根因。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ExoPlayerEngine(
    context: Context,
) : PlayerEngine {
    private val requestHeaders = AtomicReference<Map<String, String>>(emptyMap())

    private val httpDataSourceFactory =
        object : HttpDataSource.Factory {
            private val base =
                DefaultHttpDataSource
                    .Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                    .setReadTimeoutMs(READ_TIMEOUT_MS)

            override fun createDataSource(): HttpDataSource {
                val rawSource =
                    base.createDataSource().apply {
                        requestHeaders.get().forEach { (key, value) -> setRequestProperty(key, value) }
                    }
                return AdSanitizingHttpDataSource(rawSource)
            }

            override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): HttpDataSource.Factory = this
        }

    private val audioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

    private val renderersFactory =
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

    private val exoPlayer =
        ExoPlayer
            .Builder(context, renderersFactory)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpDataSourceFactory)),
            ).setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                playWhenReady = true
                trackSelectionParameters =
                    trackSelectionParameters
                        .buildUpon()
                        .setPreferredTextLanguages("zh", "chi", "zho")
                        .setSelectUndeterminedTextLanguage(true)
                        .build()
            }

    private var listener: PlayerEngineListener? = null

    private val playerListener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener?.onIsPlayingChanged(isPlaying)
            }

            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                listener?.onPlayWhenReadyChanged(playWhenReady)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                listener?.onPlaybackStateChanged(playbackState.toEngineState())
                listener?.onDurationChanged(durationMs)
            }

            override fun onTracksChanged(tracks: Tracks) {
                listener?.onTracksChanged()
            }

            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                listener?.onTrackSelectionParametersChanged()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                listener?.onVideoSizeChanged(videoSize.width, videoSize.height)
            }

            override fun onPlayerError(error: PlaybackException) {
                listener?.onError(classifyPlaybackError(context, error))
            }
        }

    init {
        exoPlayer.addListener(playerListener)
    }

    override val player: Player get() = exoPlayer

    override val currentPositionMs: Long get() = exoPlayer.currentPosition.coerceAtLeast(0L)

    override val bufferedPositionMs: Long get() = exoPlayer.bufferedPosition.coerceAtLeast(0L)

    override val durationMs: Long get() = exoPlayer.duration.coerceAtLeast(0L)

    override val isPlaying: Boolean get() = exoPlayer.isPlaying

    override val playWhenReady: Boolean get() = exoPlayer.playWhenReady

    override fun setListener(listener: PlayerEngineListener?) {
        this.listener = listener
    }

    override fun setRequestHeaders(headers: Map<String, String>) {
        requestHeaders.set(headers)
    }

    override fun load(
        url: String,
        headers: Map<String, String>,
        startPositionMs: Long,
    ) {
        requestHeaders.set(headers)
        if (startPositionMs > 0L) {
            exoPlayer.setMediaItem(MediaItem.fromUri(url), startPositionMs)
        } else {
            exoPlayer.setMediaItem(MediaItem.fromUri(url))
        }
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    override fun retry() {
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    override fun clear() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    override fun play() {
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    override fun setLooping(enabled: Boolean) {
        exoPlayer.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    override var trackSelectionParameters: TrackSelectionParameters
        get() = exoPlayer.trackSelectionParameters
        set(value) {
            exoPlayer.trackSelectionParameters = value
        }

    override fun release() {
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}

private fun Int.toEngineState(): EnginePlaybackState =
    when (this) {
        Player.STATE_BUFFERING -> EnginePlaybackState.BUFFERING
        Player.STATE_READY -> EnginePlaybackState.READY
        Player.STATE_ENDED -> EnginePlaybackState.ENDED
        else -> EnginePlaybackState.IDLE
    }

internal fun classifyPlaybackError(
    context: Context,
    error: PlaybackException,
): String =
    when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> context.getString(R.string.feature_subject_player_err_timeout)

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        -> context.getString(R.string.feature_subject_player_err_invalid_url)

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        -> context.getString(R.string.feature_subject_player_err_unsupported_format)

        else -> error.localizedMessage ?: context.getString(R.string.feature_subject_player_err_unknown)
    }

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 45_000
private val adSanitizerLogger = bgmLogger("Bgm/AdSanitizer")

/**
 * 包装底层 [HttpDataSource]，对 HLS 播放列表（.m3u8）透明执行广告切片清洗。
 *
 * 遵循 Fail-Open 准则：
 * 仅对以 .m3u8 结尾或包含 m3u8 的请求拦截，非 m3u8（如 .ts 媒体切片）完全 0 拷贝透传直读；
 * 若解析清洗发生任何异常，安全退化为原样透传。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class AdSanitizingHttpDataSource(
    private val upstream: HttpDataSource,
) : HttpDataSource by upstream {
    private var sanitizedBytes: ByteArray? = null
    private var bytesReadFromSanitized: Int = 0

    override fun open(dataSpec: DataSpec): Long {
        sanitizedBytes = null
        bytesReadFromSanitized = 0

        val uriString = dataSpec.uri.toString()
        val path = dataSpec.uri.path.orEmpty()
        val isM3u8Request = path.endsWith(".m3u8", ignoreCase = true) || uriString.contains(".m3u8", ignoreCase = true)

        if (!isM3u8Request) {
            return upstream.open(dataSpec)
        }

        adSanitizerLogger.d { "拦截 HLS 播放列表请求: ${dataSpec.uri}" }

        // m3u8 播放列表请求：拉取并执行广告切片清洗
        return runCatching {
            upstream.open(dataSpec)
            val buffer = ByteArrayOutputStream()
            val temp = ByteArray(4096)
            var read: Int
            while (upstream.read(temp, 0, temp.size).also { read = it } != -1) {
                buffer.write(temp, 0, read)
            }
            val rawText = buffer.toString(Charsets.UTF_8.name())
            if (rawText.startsWith("#EXTM3U")) {
                val cleaned = M3u8AdSanitizer.sanitize(rawText)
                if (cleaned != rawText) {
                    adSanitizerLogger.i { "成功剥离 M3U8 广告切片: ${dataSpec.uri}" }
                } else {
                    adSanitizerLogger.d { "M3U8 无需清洗（未检测到广告切片）: ${dataSpec.uri}" }
                }
                val bytes = cleaned.toByteArray(Charsets.UTF_8)
                sanitizedBytes = bytes
                bytes.size.toLong()
            } else {
                val bytes = buffer.toByteArray()
                sanitizedBytes = bytes
                bytes.size.toLong()
            }
        }.getOrElse {
            sanitizedBytes = null
            runCatching { upstream.close() }
            upstream.open(dataSpec)
        }
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val memoryData = sanitizedBytes
        if (memoryData != null) {
            if (bytesReadFromSanitized >= memoryData.size) {
                return C.RESULT_END_OF_INPUT
            }
            val bytesToRead = minOf(length, memoryData.size - bytesReadFromSanitized)
            System.arraycopy(memoryData, bytesReadFromSanitized, buffer, offset, bytesToRead)
            bytesReadFromSanitized += bytesToRead
            return bytesToRead
        }
        return upstream.read(buffer, offset, length)
    }

    override fun close() {
        sanitizedBytes = null
        bytesReadFromSanitized = 0
        upstream.close()
    }
}
