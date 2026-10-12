package com.infinitezerone.minibgm.core.data.download

import com.infinitezerone.minibgm.core.model.DownloadState
import kotlinx.coroutines.flow.StateFlow

/**
 * 资产与安装包下载器契约。
 *
 * 负责 GitHub Release 产物的高速镜像分发、HTTP Range 断点探测与写入，
 * 并以只读 StateFlow 暴露节流进度供 UI 与通知栏观察。
 */
interface AssetDownloader {
    /** 当前下载状态只读流 */
    val downloadState: StateFlow<DownloadState>

    /**
     * 执行资产下载任务。
     *
     * @param rawUrl 原始下载直链
     * @param destinationPath 本地落盘文件绝对路径
     * @param customMirrorPrefix 用户自定义加速镜像前缀（可选）
     * @return 最终下载结果状态
     */
    suspend fun downloadAsset(
        rawUrl: String,
        destinationPath: String,
        customMirrorPrefix: String? = null,
    ): DownloadState

    /** 取消当前活跃的下载任务 */
    fun cancelDownload()
}
