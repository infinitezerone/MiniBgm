package com.infinitezerone.minibgm.core.data.cache

/**
 * 种子与磁力 RSS 结果的本地磁盘持久化缓存。
 * 为已检索过的番剧分集提供离线与毫秒级秒开能力，消除高频重复的网络请求。
 */
interface TorrentFeedDiskCache {
    /** 读取指定缓存键（如 "feed:rule_id:url"）的 JSON 文本，未命中返回 null */
    suspend fun get(key: String): String?

    /** 将指定缓存键的 JSON 文本写入磁盘 */
    suspend fun put(
        key: String,
        jsonContent: String,
    )

    /** 清空所有磁盘缓存 */
    suspend fun clear()
}
