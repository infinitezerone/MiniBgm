package com.infinitezerone.minibgm.core.data.util

import kotlinx.coroutines.flow.Flow

/**
 * 全局响应式网络连通性监听门面（对标 Now in Android NetworkMonitor）
 */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
