package com.infinitezerone.minibgm.core.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

class ConnectivityManagerNetworkMonitor(
    private val context: Context,
) : NetworkMonitor {
    override val isOnline: Flow<Boolean> =
        callbackFlow {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) {
                channel.trySend(false)
                channel.close()
                return@callbackFlow
            }

            /**
             * 对标 Now in Android 最佳实践：
             * 回调对满足 [NetworkRequest] 的任意网络触发。
             * 维护活跃且具备互联网能力的网络集合，仅当集合彻底为空时才触发离线，
             * 避免网络切换（如 WiFi ↔ 流量握手）或次级通道变更导致的虚假断网。
             */
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    private val networks = mutableSetOf<Network>()

                    override fun onAvailable(network: Network) {
                        networks += network
                        channel.trySend(true)
                    }

                    override fun onLost(network: Network) {
                        networks -= network
                        channel.trySend(networks.isNotEmpty())
                    }
                }

            val request =
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
            connectivityManager.registerNetworkCallback(request, callback)

            channel.trySend(connectivityManager.isCurrentlyConnected())

            awaitClose {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.flowOn(Dispatchers.IO).conflate()

    private fun ConnectivityManager.isCurrentlyConnected(): Boolean =
        activeNetwork
            ?.let(::getNetworkCapabilities)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
}
