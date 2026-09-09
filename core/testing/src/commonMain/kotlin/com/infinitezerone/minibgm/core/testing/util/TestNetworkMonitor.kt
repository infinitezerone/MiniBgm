package com.infinitezerone.minibgm.core.testing.util

import com.infinitezerone.minibgm.core.data.util.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class TestNetworkMonitor : NetworkMonitor {
    private val isOnlineFlow = MutableStateFlow(true)
    override val isOnline: Flow<Boolean> = isOnlineFlow

    fun setOnline(online: Boolean) {
        isOnlineFlow.value = online
    }
}
