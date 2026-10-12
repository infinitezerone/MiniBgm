package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.TorrentEngineState
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
     * BT 引擎就绪状态。
     */
    val engineState: StateFlow<TorrentEngineState>

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
     * 暂停指定任务。
     */
    suspend fun pauseDownload(taskId: String)

    /**
     * 恢复指定任务。
     */
    suspend fun resumeDownload(taskId: String)

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
     * 检查或安装动态 BT 引擎模块。
     */
    suspend fun checkOrInstallEngine(): TorrentEngineState
}
