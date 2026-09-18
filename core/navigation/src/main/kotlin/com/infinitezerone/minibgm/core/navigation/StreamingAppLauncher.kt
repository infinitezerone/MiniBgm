package com.infinitezerone.minibgm.core.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver

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
     * 快捷发起哔哩哔哩番剧搜索：
     * 优先通过 DeepLink 唤起 B 站客户端直接搜索番剧，若未安装或未传 [onAppNotInstalled] 则平滑降级至网页端搜索页。
     */
    fun launchBilibiliSearch(
        context: Context,
        keyword: String,
        onAppNotInstalled: ((appName: String, webUrl: String) -> Unit)? = null,
    ): StreamingLaunchResult {
        val target = StreamingIntentResolver.buildBilibiliSearchTarget(keyword)
        val targetUri = target.deepLinkUri ?: target.webFallbackUrl
        return launch(context, targetUri, onAppNotInstalled)
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
 * [Context] 扩展快捷发起哔哩哔哩搜索。
 */
fun Context.launchBilibiliSearch(
    keyword: String,
    onAppNotInstalled: ((appName: String, webUrl: String) -> Unit)? = null,
): StreamingLaunchResult = StreamingAppLauncher.launchBilibiliSearch(this, keyword, onAppNotInstalled)
