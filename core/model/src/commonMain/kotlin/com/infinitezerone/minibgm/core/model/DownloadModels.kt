package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 资产或安装包下载进度领域模型。
 *
 * @param bytesDownloaded 已下载字节数
 * @param totalBytes 总字节数（若服务端未知则为 -1 或 0）
 * @param speedBytesPerSec 当前瞬时下载速率（字节/秒）
 */
@Serializable
data class DownloadProgress(
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
) {
    /** 进度百分比浮点数 (0.0f ~ 1.0f) */
    val fraction: Float
        get() = if (totalBytes > 0L) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    /** 进度百分比整数 (0 ~ 100) */
    val percentage: Int
        get() = (fraction * 100).toInt()

    /** 是否无法预估总量（如未返回 Content-Length） */
    val isIndeterminate: Boolean
        get() = totalBytes <= 0L

    /** 格式化已下载/总大小（如 "12.4 MB / 18.2 MB"） */
    val formattedProgress: String
        get() {
            val downloadedMb = bytesDownloaded / (1024.0 * 1024.0)
            return if (totalBytes > 0L) {
                val totalMb = totalBytes / (1024.0 * 1024.0)
                "%.1f MB / %.1f MB".format(downloadedMb, totalMb)
            } else {
                "%.1f MB".format(downloadedMb)
            }
        }

    /** 格式化下载速度（如 "2.4 MB/s" 或 "350 KB/s"） */
    val formattedSpeed: String
        get() =
            when {
                speedBytesPerSec <= 0L -> ""
                speedBytesPerSec >= 1024 * 1024 -> "%.1f MB/s".format(speedBytesPerSec / (1024.0 * 1024.0))
                else -> "%d KB/s".format(speedBytesPerSec / 1024)
            }
}

/**
 * 资产/更新下载状态机。
 */
sealed interface DownloadState {
    data object Idle : DownloadState

    data class Downloading(
        val progress: DownloadProgress,
    ) : DownloadState

    data class Completed(
        val filePath: String,
        val totalBytes: Long = 0L,
    ) : DownloadState

    data class Failed(
        val message: String,
        val error: Throwable? = null,
    ) : DownloadState
}
