package com.infinitezerone.minibgm.core.data.seasonal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 基于磁盘文件的季度快照 LRU 缓存。
 * 限制最大保留 [maxEntries] 个季度快照（默认 6 个），单季约 80-120KB，总存储占用严格控制在 1MB 以内。
 * 每次读取或写入时刷新文件最后修改时间（lastModified），超出上限时按 LRU 顺序清理最久未访问的快照。
 *
 * 接收 [cacheDir] 目录入参，便于单测使用临时目录隔离测试。
 */
class FileSeasonalDiskCache(
    private val cacheDir: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : SeasonalDiskCache {
    override suspend fun get(seasonKey: String): String? =
        withContext(Dispatchers.IO) {
            val file = getFile(seasonKey)
            if (!file.exists() || !file.isFile) return@withContext null
            val content = runCatching { file.readText() }.getOrNull() ?: return@withContext null
            // 访问时更新修改时间以维护 LRU
            file.setLastModified(System.currentTimeMillis())
            content
        }

    override suspend fun put(
        seasonKey: String,
        jsonContent: String,
    ): Unit =
        withContext(Dispatchers.IO) {
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val file = getFile(seasonKey)
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

    private fun getFile(seasonKey: String): File = File(cacheDir, "$seasonKey$FILE_SUFFIX")

    private fun pruneCache() {
        val files =
            cacheDir
                .listFiles { entry -> entry.isFile && entry.name.endsWith(FILE_SUFFIX) }
                ?.toList()
                ?: return

        if (files.size <= maxEntries) return

        // 按最后访问时间升序排序（最旧的在前面）
        val sortedByAge = files.sortedBy { it.lastModified() }
        val toDeleteCount = files.size - maxEntries
        for (i in 0 until toDeleteCount) {
            sortedByAge[i].delete()
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 6
        private const val FILE_SUFFIX = ".json"
    }
}
