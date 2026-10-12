package com.infinitezerone.minibgm.core.data.download

import android.content.Context
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.model.TorrentStreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android 平台磁力/BT 边下边播引擎实现。
 *
 * 依托 `crates/minibgm-torrent` (Rust cdylib) 原生实现的嵌入式 HTTP 流媒体服务端与顺序缓冲调度，
 * 彻底消除虚假空 Socket 占位与伪装库崩溃风险。
 */
class AndroidTorrentStreamEngine(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") private val assetDownloader: AssetDownloader,
) : TorrentStreamEngine {
    private val logger = bgmLogger("Bgm/TorrentStreamEngine")
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val _streamState = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Idle)
    override val streamState: StateFlow<TorrentStreamState> = _streamState.asStateFlow()

    private var activeStreamJob: Job? = null

    override suspend fun isPluginInstalled(): Boolean = isNativeLoaded && runCatching { nativeIsAvailable() }.getOrDefault(false)

    override suspend fun installPlugin(): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (isPluginInstalled()) {
                Result.success(Unit)
            } else {
                logger.e { "BT 原生流化库 (libminibgm_torrent.so) 缺失或当前架构不支持" }
                Result.failure(IllegalStateException("原生流化扩展组件未就绪"))
            }
        }

    override suspend fun startStream(
        title: String,
        magnetUrl: String,
        torrentUrl: String?,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                stopStream()

                if (!isPluginInstalled()) {
                    _streamState.value = TorrentStreamState.PluginRequired()
                    error("BT 流化组件未安装或当前设备架构不可用")
                }

                val infoHash = extractInfoHash(magnetUrl)
                _streamState.value = TorrentStreamState.Resolving(infoHash = infoHash)

                val downloadDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads").apply { mkdirs() }
                val torrentFilePath = ""

                logger.i { "调用 Rust 原生引擎启动 HTTP 流媒体代理: $infoHash" }
                val resultUrl =
                    nativeStartStream(
                        magnetUrl = magnetUrl,
                        torrentPath = torrentFilePath,
                        saveDir = downloadDir.absolutePath,
                    )

                if (resultUrl.startsWith("ERROR:")) {
                    val errMsg = resultUrl.removePrefix("ERROR:").trim()
                    _streamState.value = TorrentStreamState.Error(errMsg)
                    error("启动原生流媒体服务失败: $errMsg")
                }

                logger.i { "原生流媒体服务就绪: $resultUrl" }

                // 启动流状态与速率轮询
                activeStreamJob =
                    scope.launch {
                        _streamState.value =
                            TorrentStreamState.Ready(
                                streamUrl = resultUrl,
                                title = title,
                                totalBytes = 100L * 1024 * 1024,
                            )

                        while (isActive) {
                            delay(1000)
                            val stats = runCatching { nativeGetStreamStats() }.getOrNull() ?: break
                            if (stats.size >= 4) {
                                val downloaded = stats[0]
                                val total = stats[1]
                                val speed = stats[2]
                                if (total > 0L) {
                                    val progress = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    _streamState.value =
                                        TorrentStreamState.Buffering(
                                            progress = progress,
                                            bufferedBytes = downloaded,
                                            targetBytes = total,
                                            speedBytesPerSec = speed,
                                        )
                                }
                            }
                        }
                    }

                resultUrl
            }
        }

    override suspend fun stopStream() {
        withContext(Dispatchers.IO) {
            activeStreamJob?.cancel()
            activeStreamJob = null
            if (isNativeLoaded) {
                runCatching { nativeStopStream() }
            }
            _streamState.value = TorrentStreamState.Idle
        }
    }

    private fun extractInfoHash(magnetUrl: String): String {
        val xt = magnetUrl.substringAfter("xt=urn:btih:", "")
        return if (xt.isNotBlank()) xt.substringBefore("&") else "unknown_hash"
    }

    companion object {
        private val isNativeLoaded: Boolean =
            runCatching {
                System.loadLibrary("minibgm_torrent")
                true
            }.getOrDefault(false)

        @JvmStatic
        private external fun nativeIsAvailable(): Boolean

        @JvmStatic
        private external fun nativeStartStream(
            magnetUrl: String,
            torrentPath: String,
            saveDir: String,
        ): String

        @JvmStatic
        private external fun nativeStopStream()

        @JvmStatic
        private external fun nativeGetStreamStats(): LongArray
    }
}
