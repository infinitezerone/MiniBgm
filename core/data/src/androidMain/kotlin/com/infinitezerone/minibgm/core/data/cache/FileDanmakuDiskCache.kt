package com.infinitezerone.minibgm.core.data.cache

import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 基于本地磁盘文件的弹幕分集 LRU 持久化缓存。
 *
 * 遵循官方《开放弹幕网络接入指南》缓存策略：
 * - 默认保留上限 [maxEntries]（默认 200 集分集弹幕），单集通常 30KB~100KB，总存储占用严格限制在 20MB 以内；
 * - 默认有效期 [ttlMs]（默认 7 天），过期文件自动在读取与写入清理阶段淘汰；
 * - 读取或写入时自动更新 [File.setLastModified] 维护访问热度，超出上限时按 LRU 顺序淘汰最久未访问条目；
 * - 接收 [cacheDir] 入参，方便单测使用临时目录隔离。
 */
class FileDanmakuDiskCache(
    private val cacheDir: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val ttlMs: Long = DEFAULT_TTL_MS,
) : DanmakuDiskCache {
    override suspend fun get(cacheKey: String): List<DanmakuItem>? =
        withContext(Dispatchers.IO) {
            val file = getFile(cacheKey)
            if (!file.exists() || !file.isFile) return@withContext null

            val now = System.currentTimeMillis()
            if (ttlMs > 0 && now - file.lastModified() > ttlMs) {
                runCatching { file.delete() }
                return@withContext null
            }

            val content = runCatching { file.readText() }.getOrNull() ?: return@withContext null
            val items =
                runCatching {
                    BgmHttpClient.jsonConfig.decodeFromString<List<DanmakuItem>>(content)
                }.getOrNull()

            if (items != null) {
                file.setLastModified(now)
            }
            items
        }

    override suspend fun put(
        cacheKey: String,
        items: List<DanmakuItem>,
    ): Unit =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val file = getFile(cacheKey)
            runCatching {
                val json = BgmHttpClient.jsonConfig.encodeToString(items)
                file.writeText(json)
                file.setLastModified(System.currentTimeMillis())
            }
            pruneCache()
        }

    override suspend fun clear(): Unit =
        withContext(Dispatchers.IO) {
            if (!cacheDir.exists()) return@withContext
            cacheDir
                .listFiles { entry -> entry.isFile && entry.name.endsWith(FILE_SUFFIX) }
                ?.forEach { it.delete() }
        }

    private fun getFile(key: String): File {
        val safeName = hashKey(key)
        return File(cacheDir, "$safeName$FILE_SUFFIX")
    }

    private fun hashKey(key: String): String =
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(key.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        }.getOrElse {
            key.hashCode().toString()
        }

    private fun pruneCache() {
        val files =
            cacheDir
                .listFiles { entry -> entry.isFile && entry.name.endsWith(FILE_SUFFIX) }
                ?.toList()
                ?: return

        val now = System.currentTimeMillis()
        // 1. 优先清理超时过期的缓存文件
        val activeFiles =
            if (ttlMs > 0) {
                files.filter { file ->
                    val isExpired = now - file.lastModified() > ttlMs
                    if (isExpired) {
                        runCatching { file.delete() }
                        false
                    } else {
                        true
                    }
                }
            } else {
                files
            }

        // 2. 超出最大保留数量时按 LRU 顺序清理最久未访问的文件
        if (activeFiles.size <= maxEntries) return

        activeFiles
            .sortedBy { it.lastModified() }
            .take(activeFiles.size - maxEntries)
            .forEach { file ->
                runCatching { file.delete() }
            }
    }

    companion object {
        private const val FILE_SUFFIX = ".json"
        const val DEFAULT_MAX_ENTRIES = 200
        const val DEFAULT_TTL_MS = 7 * 24 * 3600 * 1000L // 7 天
    }
}
