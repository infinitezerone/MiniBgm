package com.infinitezerone.minibgm.core.data.seasonal

/**
 * 季度片单本地磁盘缓存接口。
 * 供 [com.infinitezerone.minibgm.core.data.repository.ScheduleRepository] 离线秒开及 LRU 空间控制使用。
 */
interface SeasonalDiskCache {
    /** 读取指定季度（如 "2026-autumn"）的快照 JSON，若未命中或损坏返回 null */
    suspend fun get(seasonKey: String): String?

    /** 将指定季度的快照 JSON 写入磁盘，并触发 LRU 淘汰以控制容量上限 */
    suspend fun put(
        seasonKey: String,
        jsonContent: String,
    )

    /** 清空所有季度磁盘缓存 */
    suspend fun clear()
}
