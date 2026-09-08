package com.infinitezerone.minibgm.core.data.util

/**
 * 同步完成后的下游通知（依赖倒置，与 [SyncManager] 相对的出向契约）：
 * 由关心新数据落库的 UI 表面（如桌面小组件）实现并在 Koin 注册，
 * 同步基础设施（:sync:work）只依赖本接口，不感知具体 UI 模块。
 */
interface SyncCompletionObserver {
    /** 一次同步成功、新数据已写入本地存储后调用；实现方须自带「无消费者时短路」守卫 */
    suspend fun onSyncSucceeded()
}
