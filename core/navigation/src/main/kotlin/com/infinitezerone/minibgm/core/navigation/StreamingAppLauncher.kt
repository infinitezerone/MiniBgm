package com.infinitezerone.minibgm.core.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.infinitezerone.minibgm.core.common.intent.ExternalPlayerTarget
import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver
import java.io.File

/**
 * 播放源启动结果
 */
sealed interface StreamingLaunchResult {
    /** 成功唤起官方客户端 */
    data class LaunchedInApp(
        val appName: String,
        val packageName: String,
    ) : StreamingLaunchResult

    /** 检测到官方客户端未安装（返回应用名称与降级网页地址） */
    data class AppNotInstalled(
        val appName: String,
        val webUrl: String,
    ) : StreamingLaunchResult

    /** 非特定流媒体应用或已降级在浏览器/Custom Tabs 打开 */
    data class FallbackWeb(
        val webUrl: String,
    ) : StreamingLaunchResult
}

/**
 * 播放源智能探测与原生 App 唤醒分发器：
 * 优先探测并一键唤起官方 App 直达播放页；未安装时友好降级为内置 Chrome Custom Tabs / 浏览器。
 */
object StreamingAppLauncher {
    fun isAppInstalled(
        context: Context,
        packageName: String,
    ): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }

    fun findInstalledPackage(
        context: Context,
        packageNames: List<String>,
    ): String? = packageNames.firstOrNull { isAppInstalled(context, it) }

    /**
     * 智能启动播放源：
     * 若匹配到官方播放平台且手机已安装对应客户端，直接唤起客户端；
     * 若匹配到官方平台但未安装客户端：
     *   - 若提供了 [onAppNotInstalled]，调用该回调由 UI 自行展示确认弹窗（用户点击确认后再降级网页）；
     *   - 若未提供 [onAppNotInstalled]，直接平滑降级使用应用内浏览器打开。
     */
    fun launch(
        context: Context,
        url: String,
        onAppNotInstalled: ((appName: String, webUrl: String) -> Unit)? = null,
    ): StreamingLaunchResult {
        if (url.isBlank()) return StreamingLaunchResult.FallbackWeb(url)

        val target = StreamingIntentResolver.resolve(url)
        val isSearch = target?.isSearch == true || url.startsWith("bilibili://search")
        if (target != null && target.packageNames.isNotEmpty()) {
            val installedPkg = findInstalledPackage(context, target.packageNames)
            if (installedPkg != null) {
                try {
                    val intent =
                        Intent(Intent.ACTION_VIEW).apply {
                            data =
                                if (target.deepLinkUri != null) {
                                    Uri.parse(target.deepLinkUri)
                                } else {
                                    Uri.parse(url)
                                }
                            setPackage(installedPkg)
                            if (context !is Activity) {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        }
                    context.startActivity(intent)
                    return StreamingLaunchResult.LaunchedInApp(target.appName, installedPkg)
                } catch (_: Exception) {
                    // 唤起异常则向下降级
                }
            } else if (onAppNotInstalled != null && !isSearch) {
                onAppNotInstalled(target.appName, target.webFallbackUrl)
                return StreamingLaunchResult.AppNotInstalled(target.appName, target.webFallbackUrl)
            }
        }

        // 降级为 Custom Tabs / 外部浏览器（优先使用解析出的合法 webFallbackUrl，避免在浏览器中加载 custom scheme 导致异常）
        val fallbackUrl = target?.webFallbackUrl ?: url
        context.launchWebUrl(fallbackUrl)
        return StreamingLaunchResult.FallbackWeb(fallbackUrl)
    }

    /**
     * 按目标探测外部播放器（mpv、VLC 等）并唤起：
     * 探测到已安装包名则以 ACTION_VIEW 意图唤起（data = 视频 URL、type 为视频 MIME 类型、setPackage = 目标包名）；
     * 未安装或唤起失败返回 false，并在提供 [onPlayerNotInstalled] 时回调，由 UI 自行展示降级提示。
     *
     * 注意：外部播放器意图无法携带 Referer 等自定义请求头（见 [ExternalPlayerTarget]）。
     */
    fun launchExternalPlayer(
        context: Context,
        target: ExternalPlayerTarget,
        onPlayerNotInstalled: ((appName: String, packageName: String) -> Unit)? = null,
    ): Boolean {
        if (target.videoUrl.isBlank()) return false
        if (!isAppInstalled(context, target.packageName)) {
            onPlayerNotInstalled?.invoke(target.appName, target.packageName)
            return false
        }
        return try {
            val intent =
                Intent(ExternalPlayerTarget.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(target.videoUrl), target.mimeType)
                    setPackage(target.packageName)
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            onPlayerNotInstalled?.invoke(target.appName, target.packageName)
            false
        }
    }

    /**
     * 将本地视频文件经由 [FileProvider] 授予安全只读权限，并以系统选择器（Chooser）形式唤起外部播放器/相册：
     * 兼容手机已安装的相册、系统自带视频、mpv、VLC、MX Player 等所有声明处理视频媒体格式的外部应用。
     */
    fun launchVideoWithChooser(
        context: Context,
        file: File,
        title: String? = null,
        chooserTitle: String? = null,
    ): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val authority = "${context.packageName}.fileprovider"
        val contentUri =
            try {
                FileProvider.getUriForFile(context, authority, file)
            } catch (_: Exception) {
                return false
            }

        val extension = file.extension.lowercase()
        val mimeType =
            when (extension) {
                "mkv" -> "video/x-matroska"
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                "ts", "m2ts" -> "video/mp2t"
                "avi" -> "video/x-msvideo"
                else -> "video/*"
            }

        return try {
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (!title.isNullOrBlank()) {
                        putExtra(Intent.EXTRA_TITLE, title)
                    }
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            val chooser =
                Intent.createChooser(intent, chooserTitle).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 将在线流媒体或本地回环流 URL 以系统选择器（Chooser）形式唤起外部播放器。
     */
    fun launchStreamWithChooser(
        context: Context,
        streamUrl: String,
        title: String? = null,
        chooserTitle: String? = null,
    ): Boolean {
        if (streamUrl.isBlank()) return false
        return try {
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(streamUrl), "video/*")
                    if (!title.isNullOrBlank()) {
                        putExtra(Intent.EXTRA_TITLE, title)
                    }
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            val chooser =
                Intent.createChooser(intent, chooserTitle).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 自动判别本地文件路径或网络流 URL，以系统选择器形式唤起外部播放器。
     */
    fun launchMediaWithChooser(
        context: Context,
        mediaUriOrPath: String,
        title: String? = null,
        chooserTitle: String? = null,
    ): Boolean {
        if (mediaUriOrPath.isBlank()) return false
        val cleanPath = mediaUriOrPath.removePrefix("file://")
        val file = File(cleanPath)
        return if (file.exists() && file.isFile) {
            launchVideoWithChooser(context, file, title, chooserTitle)
        } else if (mediaUriOrPath.startsWith("http://") || mediaUriOrPath.startsWith("https://")) {
            launchStreamWithChooser(context, mediaUriOrPath, title, chooserTitle)
        } else {
            false
        }
    }
}

/**
 * [Context] 扩展快捷调用 [StreamingAppLauncher.launch]。
 */
fun Context.launchStreamingUrl(
    url: String,
    onAppNotInstalled: ((appName: String, webUrl: String) -> Unit)? = null,
): StreamingLaunchResult = StreamingAppLauncher.launch(this, url, onAppNotInstalled)

/**
 * [Context] 扩展按目标唤起外部播放器（mpv、VLC 等）。
 */
fun Context.launchExternalPlayer(
    target: ExternalPlayerTarget,
    onPlayerNotInstalled: ((appName: String, packageName: String) -> Unit)? = null,
): Boolean = StreamingAppLauncher.launchExternalPlayer(this, target, onPlayerNotInstalled)

/**
 * [Context] 扩展以系统选择器形式唤起外部播放器播放指定媒体文件或流。
 */
fun Context.launchMediaWithChooser(
    mediaUriOrPath: String,
    title: String? = null,
    chooserTitle: String? = null,
): Boolean = StreamingAppLauncher.launchMediaWithChooser(this, mediaUriOrPath, title, chooserTitle)
