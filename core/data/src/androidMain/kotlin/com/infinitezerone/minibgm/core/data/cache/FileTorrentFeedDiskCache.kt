package com.infinitezerone.minibgm.core.data.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 基于本地磁盘文件的种子与磁链 LRU 缓存。
 * 限制最大保留 [maxEntries] 个 Feed 快照（默认 100 条），单条约 5-20KB，总存储占用严格控制在 2MB 以内。
 * 每次读取或写入时刷新文件修改时间（lastModified），超出上限时按 LRU 顺序清理最久未访问的快照。
 */
class FileTorrentFeedDiskCache(
    private val cacheDir: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : TorrentFeedDiskCache {
    override suspend fun get(key: String): String? =
        withContext(Dispatchers.IO) {
            val file = getFile(key)
            if (!file.exists() || !file.isFile) return@withContext null
            val content = runCatching { file.readText() }.getOrNull() ?: return@withContext null
            file.setLastModified(System.currentTimeMillis())
            content
        }

    override suspend fun put(
        key: String,
        jsonContent: String,
    ): Unit =
        withContext(Dispatchers.IO) {
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val file = getFile(key)
            runCatching {
                file.writeText(jsonContent)
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

        if (files.size <= maxEntries) return

        files
            .sortedBy { it.lastModified() }
            .take(files.size - maxEntries)
            .forEach { file ->
                runCatching { file.delete() }
            }
    }

    companion object {
        private const val FILE_SUFFIX = ".json"
        private const val DEFAULT_MAX_ENTRIES = 100
    }
}
