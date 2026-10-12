package com.infinitezerone.minibgm.core.data.download

import com.infinitezerone.minibgm.core.model.TorrentAppInfo

/**
 * 磁力与种子外部意图分发器。
 */
interface TorrentIntentDispatcher {
    /**
     * 获取系统支持处理磁力或种子的外部应用列表。
     */
    fun getAvailableTorrentApps(includePlayers: Boolean = true): List<TorrentAppInfo>

    /**
     * 打开磁力链接（可指定包名，默认唤起系统选择器）。
     */
    fun launchMagnet(
        magnetUrl: String,
        packageName: String? = null,
    ): Boolean

    /**
     * 打开本地 .torrent 种子文件（通过 FileProvider 安全授予读取权限）。
     */
    fun launchTorrentFile(
        filePath: String,
        packageName: String? = null,
    ): Boolean

    /**
     * 将磁力链接写入剪贴板。
     */
    fun copyMagnetToClipboard(magnetUrl: String): Boolean
}
