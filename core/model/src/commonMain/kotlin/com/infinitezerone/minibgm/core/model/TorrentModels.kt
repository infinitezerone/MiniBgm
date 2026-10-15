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
    val streamUrl: String = "",
    val errorMessage: String? = null,
    val createdTimestamp: Long = 0L,
    val completionTimestamp: Long? = null,
    val subjectId: Long = 0L,
    val subjectName: String = "",
    val subjectCoverUrl: String = "",
    val episodeId: Long = 0L,
    val episodeSort: Float = 0f,
    val episodeName: String = "",
    val isFileMissing: Boolean = false,
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

    /** 格式化已下载/总大小（唯一真源 formatDownloadProgressPair，总量未知时只显示已下载） */
    val formattedProgress: String
        get() = formatDownloadProgressPair(downloadedBytes, totalBytes)

    /** 格式化下载速度 */
    val formattedDownloadSpeed: String
        get() = formatTransferSpeed(downloadSpeedBytesPerSec)

    /** 预计剩余时间（秒），无有效速率或未知大小时返回 -1L */
    val etaSeconds: Long
        get() {
            val remaining = totalBytes - downloadedBytes
            return if (status == TorrentTaskStatus.DOWNLOADING && remaining > 0L && downloadSpeedBytesPerSec > 0L) {
                (remaining / downloadSpeedBytesPerSec).coerceAtLeast(1L)
            } else {
                -1L
            }
        }

    /** 是否处于连上做种节点前（无速率且无节点）的寻道状态 */
    val isConnectingPeers: Boolean
        get() = status == TorrentTaskStatus.DOWNLOADING && connectedPeers == 0 && downloadSpeedBytesPerSec <= 0L

    /**
     * 分集身份键：合集/BD 包内多个分集共用同一磁力，去重、会话复用与本地文件快捷复用都必须以此键隔离。
     *
     * 只按 magnet/infoHash 判定"同一资源"时，点第 N 集会命中第 M 集的既有任务，
     * 直接复用第 M 集的 `streamUrl` 或 `file://` 落盘路径，静默播错集且全程无任何提示。
     */
    val episodeKey: String
        get() = torrentEpisodeKey(episodeId, episodeSort, episodeName)
}

/**
 * 由分集三元组派生稳定的分集身份键：优先 `episodeId`（跨重启最可靠），
 * 退化到 `episodeSort`（半集/总集篇的小数字号得以保留），最后才用分集名。
 *
 * 禁止改成"有集数就比集数、否则整体相等"之类的模糊匹配：合集包内任意两集都必须是不同的键。
 */
fun torrentEpisodeKey(
    episodeId: Long,
    episodeSort: Float,
    episodeName: String,
): String =
    when {
        episodeId > 0L -> "id:$episodeId"
        episodeSort > 0f -> "sort:$episodeSort"
        else -> "name:${episodeName.trim()}"
    }

/** 下载/上传速率的唯一格式化真源（任务卡片、合集视图与播放器流进度共用）。 */
fun formatTransferSpeed(bytesPerSec: Long): String =
    when {
        bytesPerSec <= 0L -> "0 KB/s"
        bytesPerSec >= 1024 * 1024 -> "%.1f MB/s".format(bytesPerSec / (1024.0 * 1024.0))
        else -> "%d KB/s".format(bytesPerSec / 1024)
    }

/**
 * 「已下载 / 总量」进度文案的唯一格式化真源（总量未知时只显示已下载）。
 *
 * 资产下载进度与磁力任务进度此前各抄了一份逐字相同的 MB 分支。此处**刻意**沿用固定 MB 口径，
 * 而不改用 [DownloadStorageUsage.formatBytes] 的 KB/GB 自适应：后者会改变既有 UI 文案
 * （`0.0 MB` → `0 B`）与截图黄金图，属独立的产品决策，不与「消除重复实现」混做。
 */
fun formatDownloadProgressPair(
    downloadedBytes: Long,
    totalBytes: Long,
): String {
    val downloadedMb = downloadedBytes / (1024.0 * 1024.0)
    return if (totalBytes > 0L) {
        val totalMb = totalBytes / (1024.0 * 1024.0)
        "%.1f MB / %.1f MB".format(downloadedMb, totalMb)
    } else {
        "%.1f MB".format(downloadedMb)
    }
}

/**
 * 按番剧聚合的下载合集视图模型。
 */
@Serializable
data class SubjectDownloadGroup(
    val subjectId: Long,
    val subjectName: String,
    val subjectCoverUrl: String,
    val tasks: List<TorrentTask>,
) {
    val totalTasks: Int get() = tasks.size
    val completedCount: Int get() = tasks.count { it.isCompleted }
    val downloadingCount: Int get() = tasks.count { it.status == TorrentTaskStatus.DOWNLOADING }
    val resolvingCount: Int get() = tasks.count { it.status == TorrentTaskStatus.RESOLVING }
    val queuedCount: Int get() = tasks.count { it.status == TorrentTaskStatus.QUEUED }
    val pausedCount: Int get() = tasks.count { it.status == TorrentTaskStatus.PAUSED }
    val errorCount: Int get() = tasks.count { it.status == TorrentTaskStatus.ERROR }
    val firstErrorMessage: String?
        get() =
            tasks
                .firstOrNull { it.status == TorrentTaskStatus.ERROR && !it.errorMessage.isNullOrBlank() }
                ?.errorMessage
    val isAllCompleted: Boolean get() = tasks.isNotEmpty() && tasks.all { it.isCompleted }
    val totalBytes: Long get() = tasks.sumOf { it.totalBytes }
    val downloadedBytes: Long get() = tasks.sumOf { it.downloadedBytes }
    val totalSpeedBytesPerSec: Long get() = tasks.sumOf { it.downloadSpeedBytesPerSec }

    val activeTaskCount: Int get() =
        tasks.count {
            it.status == TorrentTaskStatus.DOWNLOADING ||
                it.status == TorrentTaskStatus.RESOLVING ||
                it.status == TorrentTaskStatus.QUEUED
        }
    val sortedTasks: List<TorrentTask> get() = tasks.sortedBy { it.episodeSort }
    val progress: Float get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val percentage: Int get() = (progress * 100).toInt()
    val isConnectingPeers: Boolean
        get() =
            downloadingCount > 0 &&
                totalSpeedBytesPerSec <= 0L &&
                tasks.none { it.status == TorrentTaskStatus.DOWNLOADING && it.connectedPeers > 0 }

    val formattedTotalSpeed: String
        get() = formatTransferSpeed(totalSpeedBytesPerSec)

    val formattedTotalSize: String
        get() = DownloadStorageUsage.formatBytes(totalBytes)
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
 * 边下边播流状态机。
 */
@Serializable
sealed interface TorrentStreamState {
    /** 初始空闲状态 */
    @Serializable
    data object Idle : TorrentStreamState

    /** 插件未安装，需要用户确认后按需下载（实测 arm64-v8a/x86_64 约 9.8MB~11MB） */
    @Serializable
    data class PluginRequired(
        val estimatedSizeBytes: Long = 10_000_000L,
    ) : TorrentStreamState

    /** 正在按需安装 BT 流化插件 */
    @Serializable
    data class InstallingPlugin(
        val progress: Float,
    ) : TorrentStreamState

    /** 正在连接 Peer 并解析种子元数据 */
    @Serializable
    data class Resolving(
        val infoHash: String = "",
    ) : TorrentStreamState

    /**
     * 正在缓冲首部数据（Moov Atom / EBML Header），确保起播不卡顿。
     */
    @Serializable
    data class Buffering(
        val progress: Float,
        val bufferedBytes: Long,
        val targetBytes: Long,
        val speedBytesPerSec: Long = 0L,
        val connectedPeers: Int = 0,
    ) : TorrentStreamState

    /**
     * 流已就绪，提供本地代理 HTTP URL（如 http://127.0.0.1:port/stream）供播放器消费。
     */
    @Serializable
    data class Ready(
        val streamUrl: String,
        val title: String,
        val totalBytes: Long = 0L,
        val connectedPeers: Int = 0,
    ) : TorrentStreamState

    /** 启动或播放失败 */
    @Serializable
    data class Error(
        val message: String,
    ) : TorrentStreamState
}

/**
 * 本地存储使用情况信息（离线占用与剩余可用）。
 */
@Serializable
data class DownloadStorageUsage(
    val downloadSizeBytes: Long = 0L,
    val availableBytes: Long = 0L,
) {
    val formattedDownloadSize: String
        get() = formatBytes(downloadSizeBytes)

    val formattedAvailableSize: String
        get() = formatBytes(availableBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val gb = bytes / (1024.0 * 1024.0 * 1024.0)
            if (gb >= 1.0) {
                return "%.1f GB".format(gb)
            }
            val mb = bytes / (1024.0 * 1024.0)
            if (mb >= 1.0) {
                return "%.1f MB".format(mb)
            }
            val kb = bytes / 1024.0
            return "%.1f KB".format(kb)
        }
    }
}
