package com.infinitezerone.minibgm.core.network

import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.FileStorage
import java.io.File

actual fun createHttpCacheStorage(directory: String): CacheStorage {
    val cacheDirectory = File(directory)
    return BoundedCacheStorage(
        delegate = FileStorage(cacheDirectory),
        directory = cacheDirectory,
        maxBytes = MAX_HTTP_CACHE_BYTES,
    )
}
