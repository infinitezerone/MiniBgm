package com.infinitezerone.minibgm.core.data.cache

import com.infinitezerone.minibgm.core.model.DanmakuItem

/**
 * 弹弹play 聚合弹幕本地磁盘持久化缓存。
 * 遵循官方《开放弹幕网络接入指南》缓存建议，将分集弹幕数据持久化到本地存储，
 * 为弱网或离线播放提供毫秒级秒开能力，消除对官方/代理 API 的重复调用与配额消耗。
 */
interface DanmakuDiskCache {
    /** 读取指定缓存键（如 "ep_123456"）的弹幕列表，未命中或已过期返回 null */
    suspend fun get(cacheKey: String): List<DanmakuItem>?

    /** 将指定缓存键的弹幕列表持久化至本地磁盘 */
    suspend fun put(
        cacheKey: String,
        items: List<DanmakuItem>,
    )

    /** 清空所有弹幕磁盘缓存 */
    suspend fun clear()
}
