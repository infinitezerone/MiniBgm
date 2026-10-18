package com.infinitezerone.minibgm.core.network

import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 只做容量淘汰的 HTTP 缓存存储装饰器。
 *
 * 参照 ExoPlayer 的 `LeastRecentlyUsedCacheEvictor`：按字节预算、淘汰最久未写的条目，
 * **不做任何时间/新鲜度判断**——那属于 HTTP 语义（`Cache-Control` / `ETag`），
 * 在这里再写一套 TTL 只会和响应头打架（v0.x 的 `ttlMs` 就是这么出问题的）。
 *
 * 删除是 best-effort：文件被占用/已删除都忽略，下一次 store 会再 trim 一次。
 */
internal class BoundedCacheStorage(
    private val delegate: CacheStorage,
    private val directory: File,
    private val maxBytes: Long,
) : CacheStorage {
    override suspend fun store(
        url: Url,
        data: CachedResponseData,
    ) {
        delegate.store(url, data)
        trim()
    }

    override suspend fun find(
        url: Url,
        varyKeys: Map<String, String>,
    ): CachedResponseData? = delegate.find(url, varyKeys)

    override suspend fun findAll(url: Url): Set<CachedResponseData> = delegate.findAll(url)

    override suspend fun remove(
        url: Url,
        varyKeys: Map<String, String>,
    ) = delegate.remove(url, varyKeys)

    override suspend fun removeAll(url: Url) = delegate.removeAll(url)

    private suspend fun trim() =
        withContext(Dispatchers.IO) {
            runCatching {
                val files = directory.listFiles()?.filter { it.isFile } ?: return@runCatching
                var total = files.sumOf { it.length() }
                if (total <= maxBytes) return@runCatching
                files
                    .sortedBy { it.lastModified() }
                    .forEach { file ->
                        if (total <= maxBytes) return@forEach
                        val size = file.length()
                        if (file.delete()) total -= size
                    }
            }
        }
}
