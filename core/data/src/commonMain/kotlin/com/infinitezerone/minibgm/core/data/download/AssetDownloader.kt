package com.infinitezerone.minibgm.core.data.download

import com.infinitezerone.minibgm.core.model.DownloadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * 下载器在 [AssetDownloader.downloadAsset] 目标路径旁使用的中转文件后缀（`<destinationPath>.tmp`）。
 *
 * 该文件承载 HTTP Range 断点续传的已落盘字节。调用方若需要保证「重新开始」而非「续传」，
 * 必须连同这个中转文件一起清理，否则上一轮的残缺字节会被拼接到新内容之前。
 */
const val DOWNLOAD_TEMP_SUFFIX: String = ".tmp"

/**
 * 资产与安装包下载器契约。
 *
 * 负责 GitHub Release 产物的高速镜像分发、HTTP Range 断点探测与写入，
 * 并支持基于请求作用域的冷流（Cold Flow）节流进度观察与目标路径级并发互斥控制。
 */
interface AssetDownloader {
    /**
     * 发起资产下载任务，返回请求作用域的冷流（Cold Flow）。
     *
     * 每次调用均产生独立的下载流，调用方收集（collect）时开始执行下载，
     * 收集终止或协程取消时将天然中止流并释放底层网络与文件连接。
     * 状态流按序发射 [DownloadState.Downloading] 节流进度，
     * 并在终态发射 [DownloadState.Completed] 或 [DownloadState.Failed]。
     *
     * 同一 [destinationPath] 的并发写入将在下载器内部受路径级锁互斥保护，杜绝文件争抢与损坏。
     *
     * @param rawUrl 原始下载直链
     * @param destinationPath 本地落盘文件绝对路径
     * @param customMirrorPrefix 用户自定义加速镜像前缀（可选）
     */
    fun download(
        rawUrl: String,
        destinationPath: String,
        customMirrorPrefix: String? = null,
    ): Flow<DownloadState> =
        flow {
            emit(downloadAsset(rawUrl, destinationPath, customMirrorPrefix))
        }

    /**
     * 挂起执行资产下载任务直至终态，并返回最终下载结果。
     *
     * 适合仅关心最终完成结果或在非流式协程中调用的场景。
     * 默认实现直接收集 [download] 冷流至终态。
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
    ): DownloadState =
        download(rawUrl, destinationPath, customMirrorPrefix)
            .filter { it is DownloadState.Completed || it is DownloadState.Failed }
            .first()

    /**
     * 当前下载状态只读流。
     *
     * @deprecated 建议直接使用 [download] 返回的请求作用域独立 Flow，避免多任务共享状态流导致的互相覆写。
     */
    @Deprecated("建议直接使用 download(...) 返回的独立 Flow，避免多任务共享状态流导致的互相覆写")
    val downloadState: StateFlow<DownloadState>
        get() = MutableStateFlow(DownloadState.Idle)

    /** 取消当前活跃的下载任务（向后兼容）。 */
    @Deprecated("建议通过取消 collect 所在的 CoroutineScope 终止下载，避免无参数全局取消误伤其他任务")
    fun cancelDownload() {}
}
