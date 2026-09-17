package com.infinitezerone.minibgm.core.data.util

import kotlinx.coroutines.flow.Flow

/**
 * 全局同步管理器接口（对标 Now in Android 的 SyncManager 契约）
 */
interface SyncManager {
    /** 观察当前是否有同步任务正在执行 */
    val isSyncing: Flow<Boolean>

    /** 请求即时触发一次单次后台同步 */
    fun requestSync()
}
