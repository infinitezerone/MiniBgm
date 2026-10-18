package com.infinitezerone.minibgm.core.network

import io.ktor.client.plugins.cache.storage.CacheStorage

/**
 * 平台相关的 HTTP 磁盘缓存后端（Ktor [io.ktor.client.plugins.cache.HttpCache] 的持久化存储）。
 *
 * 缓存的新鲜度完全由服务端响应头（`Cache-Control` / `Expires` / `ETag` / `Last-Modified`）决定：
 * 命中即不再发请求，过期则发条件请求（304 复用正文）。仓库不再自研 TTL——
 * 自研 TTL 与 HTTP 语义各算一套，只会在"该刷新时不刷新、该复用时重复下载"之间摇摆。
 *
 * @param directory 持久化目录（通常位于 `cacheDir` 下，系统可回收）
 */
expect fun createHttpCacheStorage(directory: String): CacheStorage

/**
 * HTTP 缓存的磁盘预算（字节）。
 *
 * 淘汰只看容量，不看时间：新鲜度归 HTTP（`Cache-Control`/`ETag`），
 * 容量归存储层（等价于 ExoPlayer 的 `LeastRecentlyUsedCacheEvictor`）。
 */
const val MAX_HTTP_CACHE_BYTES: Long = 64L * 1024 * 1024
