package com.infinitezerone.minibgm.core.data.download

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.model.TorrentAppInfo
import java.io.File

class AndroidTorrentIntentDispatcher(
    private val context: Context,
) : TorrentIntentDispatcher {
    private val logger = bgmLogger("Bgm/TorrentIntentDispatcher")

    private val knownPlayers =
        setOf(
            "is.xyz.mpv",
            "org.videolan.vlc",
            "com.mxtech.videoplayer.ad",
            "com.mxtech.videoplayer.pro",
        )

    override fun getAvailableTorrentApps(includePlayers: Boolean): List<TorrentAppInfo> {
        val pm = context.packageManager
        val results = linkedMapOf<String, TorrentAppInfo>()

        // 1. 探测处理 magnet 协议的应用
        runCatching {
            val magnetIntent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("magnet:?xt=urn:btih:0000000000000000000000000000000000000000"),
                )
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PackageManager.MATCH_ALL
                } else {
                    0
                }
            val resolveInfos = pm.queryIntentActivities(magnetIntent, flags)
            for (info in resolveInfos) {
                val pkg = info.activityInfo.packageName
                if (pkg != context.packageName) {
                    val label = info.loadLabel(pm).toString()
                    results[pkg] =
                        TorrentAppInfo(
                            name = label,
                            packageName = pkg,
                            isPlayer = pkg in knownPlayers,
                        )
                }
            }
        }.onFailure { e ->
            logger.w { "探测 magnet 处理应用失败: ${e.message}" }
        }

        // 2. 探测处理 application/x-bittorrent 种子文件的应用
        runCatching {
            val torrentIntent =
                Intent(Intent.ACTION_VIEW).apply {
                    type = "application/x-bittorrent"
                }
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PackageManager.MATCH_ALL
                } else {
                    0
                }
            val resolveInfos = pm.queryIntentActivities(torrentIntent, flags)
            for (info in resolveInfos) {
                val pkg = info.activityInfo.packageName
                if (pkg != context.packageName && !results.containsKey(pkg)) {
                    val label = info.loadLabel(pm).toString()
                    results[pkg] =
                        TorrentAppInfo(
                            name = label,
                            packageName = pkg,
                            isPlayer = pkg in knownPlayers,
                        )
                }
            }
        }.onFailure { e ->
            logger.w { "探测 torrent 处理应用失败: ${e.message}" }
        }

        // 3. 补充支持直连流播放的外部播放器
        if (includePlayers) {
            for (playerPkg in knownPlayers) {
                if (!results.containsKey(playerPkg)) {
                    runCatching {
                        val appInfo = pm.getApplicationInfo(playerPkg, 0)
                        val label = pm.getApplicationLabel(appInfo).toString()
                        results[playerPkg] =
                            TorrentAppInfo(
                                name = label,
                                packageName = playerPkg,
                                isPlayer = true,
                            )
                    }
                }
            }
        }

        return results.values.toList()
    }

    override fun launchMagnet(
        magnetUrl: String,
        packageName: String?,
    ): Boolean {
        if (magnetUrl.isBlank()) return false
        return runCatching {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(magnetUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (!packageName.isNullOrBlank()) {
                        setPackage(packageName)
                    }
                }
            val finalIntent =
                if (packageName.isNullOrBlank()) {
                    Intent.createChooser(intent, "选择下载器或播放器").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    intent
                }
            context.startActivity(finalIntent)
            true
        }.getOrElse { e ->
            logger.e { "唤起磁力链接应用失败: ${e.message}" }
            false
        }
    }

    override fun launchTorrentFile(
        filePath: String,
        packageName: String?,
    ): Boolean {
        val file = File(filePath)
        if (!file.exists() || file.length() <= 0L) {
            logger.w { "种子文件不存在或为空: $filePath" }
            return false
        }

        return runCatching {
            val contentUri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )

            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "application/x-bittorrent")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (!packageName.isNullOrBlank()) {
                        setPackage(packageName)
                    }
                }

            val finalIntent =
                if (packageName.isNullOrBlank()) {
                    Intent.createChooser(intent, "打开种子文件").apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    intent
                }

            context.startActivity(finalIntent)
            true
        }.getOrElse { e ->
            logger.e { "打开种子文件失败: ${e.message}" }
            false
        }
    }

    override fun copyMagnetToClipboard(magnetUrl: String): Boolean {
        if (magnetUrl.isBlank()) return false
        return runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("magnet", magnetUrl)
            clipboard?.setPrimaryClip(clip)
            true
        }.getOrDefault(false)
    }
}
