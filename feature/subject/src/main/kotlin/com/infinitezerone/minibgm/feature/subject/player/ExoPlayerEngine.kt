package com.infinitezerone.minibgm.feature.subject.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * [PlayerEngine] 的 ExoPlayer 实现。
 *
 * 请求头走**动态 header holder**：`HttpDataSource` 每次 `createDataSource()` 时读取当前头，
 * 因此换源（Referer 变化）只需更新 holder，**不重建 ExoPlayer**——这正是旧实现每次换源
 * 都重建播放器、进而把 PlayerView 与播放器解绑导致黑屏的根因。
 */
class ExoPlayerEngine(
    context: Context,
) : PlayerEngine {
    private val requestHeaders = AtomicReference<Map<String, String>>(emptyMap())

    private val httpDataSourceFactory =
        object : HttpDataSource.Factory {
            private val base = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)

            override fun createDataSource(): HttpDataSource =
                base.createDataSource().apply {
                    requestHeaders.get().forEach { (key, value) -> setRequestProperty(key, value) }
                }

            override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): HttpDataSource.Factory = this
        }

    private val audioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

    private val exoPlayer =
        ExoPlayer
            .Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply { playWhenReady = true }

    private var listener: PlayerEngineListener? = null

    private val playerListener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener?.onIsPlayingChanged(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                listener?.onPlaybackStateChanged(playbackState.toEngineState())
                listener?.onDurationChanged(durationMs)
            }

            override fun onPlayerError(error: PlaybackException) {
                listener?.onError(classifyPlaybackError(error))
            }
        }

    init {
        exoPlayer.addListener(playerListener)
    }

    override val player: Player get() = exoPlayer

    override val currentPositionMs: Long get() = exoPlayer.currentPosition.coerceAtLeast(0L)

    override val durationMs: Long get() = exoPlayer.duration.coerceAtLeast(0L)

    override val isPlaying: Boolean get() = exoPlayer.isPlaying

    override fun setListener(listener: PlayerEngineListener?) {
        this.listener = listener
    }

    override fun setRequestHeaders(headers: Map<String, String>) {
        requestHeaders.set(headers)
    }

    override fun load(
        url: String,
        headers: Map<String, String>,
    ) {
        requestHeaders.set(headers)
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
    }

    override fun retry() {
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

internal fun classifyPlaybackError(error: PlaybackException): String =
    when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "网络连接超时，请检查网络"

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        -> "播放地址已失效或返回错误"

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        -> "视频流格式无法解析"

        else -> error.localizedMessage ?: "播放出现未知异常"
    }
