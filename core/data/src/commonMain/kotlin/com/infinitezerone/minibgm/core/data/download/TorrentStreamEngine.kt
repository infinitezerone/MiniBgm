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
     * @param saveDirPath 可选目标落盘目录（离线下载任务传入持久化目录，使 librqbit 直接落盘而无需二次 HTTP 拷贝）
     * @return 成功时返回本地 HTTP 播放直链（如 http://127.0.0.1:8899/stream?hash=...）
     */
    suspend fun startStream(
        title: String,
        magnetUrl: String,
        torrentUrl: String? = null,
        saveDirPath: String? = null,
    ): Result<String>

    /**
     * 停止并释放当前流媒体代理与 P2P 缓冲区。
     *
     * @param force 为 `false`（默认）时若当前会话正由后台离线下载任务持有保护（[isDownloadSessionProtected]），
     *   则忽略播放器退出的释放请求以保持后台下载不断流；后台下载暂停/取消/完成时传 `force = true` 强制释放。
     */
    suspend fun stopStream(force: Boolean = false)

    /** 获取当前活跃 P2P 会话选定的真实目标媒体文件绝对路径（保留原始 `.mkv`/`.mp4` 扩展名） */
    fun getActiveTargetFilePath(): String? = null

    /** 获取当前活跃 P2P 会话的 InfoHash */
    fun getActiveInfoHash(): String? = null

    /** 标记或解除当前流会话受后台离线下载任务保护，防止退出播放器误杀正在进行的下载 */
    fun setDownloadSessionProtected(protected: Boolean) = Unit

    /** 当前流会话是否正受后台离线下载任务保护 */
    fun isDownloadSessionProtected(): Boolean = false

    /** 标记或解除当前流会话正由前台播放器消费，防止后台下载刚满 100% 时立即掐断正在播放的本地 HTTP 流 */
    fun setPlayerSessionActive(active: Boolean) = Unit

    /** 当前流会话是否正由前台播放器消费 */
    fun isPlayerSessionActive(): Boolean = false

    /** 前台播放器退出时调用：解除播放器会话活跃标记，并在无后台离线下载保护时异步释放底层流媒体服务 */
    fun releasePlayerSession() = Unit

    /**
     * 开发者调试能力：清理本地已下载的动态 `.so` 扩展，并切换是否屏蔽 Debug APK 内置库回退。
     *
     * @param simulateUninstalled 为 `true` 时删除动态扩展目录并屏蔽 APK 内置 `.so` 回退，模拟全新 Release 未安装态；
     *   一旦随后经 `installPlugin()` 完成云端下载，动态 `.so` 仍正常装载生效。为 `false` 时恢复默认内置库回退。
     */
    suspend fun setDebugSimulateUninstalled(simulateUninstalled: Boolean) = Unit

    /** 当前是否开启了开发者“屏蔽 APK 内置库（模拟未安装）”调试模式 */
    fun isDebugSimulateUninstalled(): Boolean = false
}
