package com.infinitezerone.minibgm.core.network

import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.FileStorage
import java.io.File

actual fun createHttpCacheStorage(directory: String): CacheStorage {
    val cacheDirectory = File(directory)
    return BoundedCacheStorage(
        // FileStorage 构造即 mkdirs：必须延迟到第一次在 IO 线程上使用时
        delegateProvider = { FileStorage(cacheDirectory) },
        directory = cacheDirectory,
        maxBytes = MAX_HTTP_CACHE_BYTES,
    )
}
