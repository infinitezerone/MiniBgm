package com.infinitezerone.minibgm.core.data.download

import com.infinitezerone.minibgm.core.model.TorrentStreamState
import kotlinx.coroutines.flow.StateFlow

/**
 * 磁力/BT 边下边播流化引擎契约。
 *
 * 负责 BT 流化扩展插件的按需安装检测、启动本地 HTTP 流式代理并向播放器输出
 * 可供 Seek 播放的局部直链，且生命周期受控。
 */
interface TorrentStreamEngine {
    /** 边下边播状态只读流 */
    val streamState: StateFlow<TorrentStreamState>

    /** 检测 BT 流化扩展组件是否已在本地安装就绪 */
    suspend fun isPluginInstalled(): Boolean

    /**
     * 按需下载并安装 BT 流化插件组件（通过国内镜像高速拉取）。
     */
    suspend fun installPlugin(): Result<Unit>

    /**
     * 启动针对特定磁力/种子资源的边下边播流化任务。
     *
     * @param title 视频标题
     * @param magnetUrl 磁力链接
     * @param torrentUrl 可选种子直链（用于加速元数据抓取）
     * @return 成功时返回本地 HTTP 播放直链（如 http://127.0.0.1:8899/stream?hash=...）
     */
    suspend fun startStream(
        title: String,
        magnetUrl: String,
        torrentUrl: String? = null,
    ): Result<String>

    /** 停止并释放当前流媒体代理与 P2P 缓冲区 */
    suspend fun stopStream()
}
