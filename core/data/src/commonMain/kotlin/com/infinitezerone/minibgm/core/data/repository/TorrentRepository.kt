package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.TorrentStreamState
import com.infinitezerone.minibgm.core.model.TorrentTask
import kotlinx.coroutines.flow.StateFlow

/**
 * 磁力与 BT 任务数据仓库契约。
 */
interface TorrentRepository {
    /**
     * 当前所有磁力/BT 下载任务列表。
     */
    val tasks: StateFlow<List<TorrentTask>>

    /**
     * 边下边播流状态只读流（Idle / 安装插件 / 解析元数据 / 缓冲 / 就绪 / 失败）。
     *
     * 供 UI 呈现起播进度与失败归因；feature 层只能经本接口消费，严禁直引 `TorrentStreamEngine`。
     */
    val streamState: StateFlow<TorrentStreamState>

    /**
     * 创建并启动一个下载任务。
     *
     * @param title 视频或种子标题
     * @param magnetUrl 磁力链接
     * @param torrentUrl 种子直链（可选）
     * @param totalBytes 预估总大小（可选）
     * @return 创建的任务 ID
     */
    suspend fun startDownload(
        title: String,
        magnetUrl: String,
        torrentUrl: String? = null,
        totalBytes: Long = 0L,
        subjectId: Long = 0L,
        subjectName: String = "",
        subjectCoverUrl: String = "",
        episodeId: Long = 0L,
        episodeSort: Float = 0f,
        episodeName: String = "",
    ): String

    /**
     * 启动（或复用）下载任务并挂起等待可播放地址就绪（统一收口“边下边播”与“应用内下载”）。
     *
     * - 若任务已离线完成且本地文件存在，直接返回 `file://` 路径；
     * - 若任务正在下载且本地流服务已就绪，直接复用当前 `streamUrl`；
     * - 若任务处于排队/暂停/异常或尚未创建，优先调度该任务启动，并在流服务就绪后返回 `streamUrl`。
     *
     * @param onTaskQueued 任务身份回调，任务一落库即回调其 ID（早于可播放地址就绪）。
     *   调用方必须靠它持有任务身份：否则「停止起播」只能取消等待协程，底层任务会继续占用
     *   单实例 P2P 队列、并在下载页留下一条用户从未主动创建过的幽灵条目。
     */
    suspend fun startStreamDownload(
        title: String,
        magnetUrl: String,
        torrentUrl: String? = null,
        totalBytes: Long = 0L,
        subjectId: Long = 0L,
        subjectName: String = "",
        subjectCoverUrl: String = "",
        episodeId: Long = 0L,
        episodeSort: Float = 0f,
        episodeName: String = "",
        onTaskQueued: (String) -> Unit = {},
    ): Result<String>

    /**
     * 暂停指定任务。
     */
    suspend fun pauseDownload(taskId: String)

    /**
     * 恢复指定任务。
     */
    suspend fun resumeDownload(
        taskId: String,
        resetDownloadedBytes: Boolean = false,
    )

    /**
     * 取消并移除指定任务。
     */
    suspend fun cancelDownload(
        taskId: String,
        deleteFiles: Boolean = true,
    )

    /**
     * 下载并保存 .torrent 种子文件到本地沙盒。
     *
     * @return 成功时返回本地种子绝对路径
     */
    suspend fun downloadTorrentFile(
        torrentUrl: String,
        fileName: String,
    ): Result<String>

    /**
     * 原生 BT 流媒体引擎扩展插件是否已就绪。
     */
    suspend fun isPluginInstalled(): Boolean

    /**
     * 安装原生 BT 流媒体引擎扩展插件。
     */
    suspend fun installPlugin(): Result<Unit>

    /**
     * 获取当前下载存储占用与可用存储空间。
     */
    suspend fun getStorageUsage(): com.infinitezerone.minibgm.core.model.DownloadStorageUsage

    /**
     * 校验已完成任务物理文件的存在性，更新任务的 isFileMissing 状态。
     */
    suspend fun verifyTaskFiles()
}
