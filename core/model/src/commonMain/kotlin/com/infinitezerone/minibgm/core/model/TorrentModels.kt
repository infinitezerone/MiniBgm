package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 磁力/BT 任务状态。
 */
@Serializable
enum class TorrentTaskStatus {
    /** 任务已创建，排队中 */
    QUEUED,

    /** 正在通过 DHT / Tracker 解析磁力元数据 */
    RESOLVING,

    /** 正在下载 */
    DOWNLOADING,

    /** 已暂停 */
    PAUSED,

    /** 下载完成 */
    COMPLETED,

    /** 出现错误 */
    ERROR,
}

/**
 * 磁力/BT 下载任务领域模型。
 */
@Serializable
data class TorrentTask(
    val id: String,
    val title: String,
    val magnetUrl: String,
    val torrentUrl: String? = null,
    val torrentFilePath: String? = null,
    val status: TorrentTaskStatus = TorrentTaskStatus.QUEUED,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val downloadSpeedBytesPerSec: Long = 0L,
    val uploadSpeedBytesPerSec: Long = 0L,
    val connectedPeers: Int = 0,
    val connectedSeeds: Int = 0,
    val savePath: String = "",
    val errorMessage: String? = null,
    val createdTimestamp: Long = 0L,
    val completionTimestamp: Long? = null,
) {
    /** 下载进度百分比 (0.0f ~ 1.0f) */
    val progress: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    /** 下载进度百分比整数 (0 ~ 100) */
    val percentage: Int
        get() = (progress * 100).toInt()

    /** 是否已完成 */
    val isCompleted: Boolean
        get() = status == TorrentTaskStatus.COMPLETED

    /** 格式化已下载/总大小 */
    val formattedProgress: String
        get() {
            val downloadedMb = downloadedBytes / (1024.0 * 1024.0)
            return if (totalBytes > 0L) {
                val totalMb = totalBytes / (1024.0 * 1024.0)
                "%.1f MB / %.1f MB".format(downloadedMb, totalMb)
            } else {
                "%.1f MB".format(downloadedMb)
            }
        }

    /** 格式化下载速度 */
    val formattedDownloadSpeed: String
        get() =
            when {
                downloadSpeedBytesPerSec <= 0L -> "0 KB/s"
                downloadSpeedBytesPerSec >= 1024 * 1024 -> "%.1f MB/s".format(downloadSpeedBytesPerSec / (1024.0 * 1024.0))
                else -> "%d KB/s".format(downloadSpeedBytesPerSec / 1024)
            }
}

/**
 * 外部支持处理磁力或种子的应用信息。
 */
@Serializable
data class TorrentAppInfo(
    val name: String,
    val packageName: String,
    val isPlayer: Boolean = false,
)

/**
 * BT 引擎就绪状态机。
 */
sealed interface TorrentEngineState {
    data object Ready : TorrentEngineState

    data object NotInstalled : TorrentEngineState

    data class Installing(
        val progress: Float,
    ) : TorrentEngineState

    data class Failed(
        val reason: String,
    ) : TorrentEngineState
}
