package com.infinitezerone.minibgm.core.network.ech

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.infinitezerone.minibgm.core.common.bgmLogger

/**
 * 网络切换监听（Android 平台契约）：换网后必须重新解析，并立刻允许一次兜底探测。
 *
 * 为什么必须有它：DNS 结果与「哪个边缘 IP 握得上手」都绑定在**当前网络**上。
 * Wi-Fi ↔ 蜂窝切换后，池里的节点、缓存的解析结果全部可能失效；而兜底探测带 10 分钟冷却，
 * 不重置就表现为「明明有网却一直超时」，用户只能杀进程自救。
 *
 * 用 [ConnectivityManager.registerDefaultNetworkCallback] 而不是轮询网络类型：
 * 平台在默认网络变化时回调，且不需要声明额外权限。
 */
internal object EchNetworkMonitor {
    private val logger = bgmLogger("Bgm/EchNetwork")
    private var registered = false

    fun start(context: Context) {
        if (registered) return
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onNetworkSwitched("onAvailable")

                override fun onLost(network: Network) = onNetworkSwitched("onLost")
            }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onSuccess {
                registered = true
                logger.i { "default network callback registered" }
            }.onFailure { logger.w(it) { "registerDefaultNetworkCallback failed" } }
    }

    /** 收敛成一次显式动作，便于单测（不依赖真实网络回调）。 */
    internal fun onNetworkSwitched(reason: String) {
        logger.i { "[NET] $reason -> reset probe cooldown, drop cached resolution, and clear connection pool" }
        CloudflareEdgeProbe.resetCooldown()
        AdaptiveDnsResolver.invalidateCaches()
        EchRuntime.clearConnectionPool()
    }
}
