package com.infinitezerone.minibgm.core.data.cache

import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.DanmakuMode
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FileDanmakuDiskCacheTest {
    private lateinit var tempDir: File
    private lateinit var cache: FileDanmakuDiskCache

    private val sampleItems =
        listOf(
            DanmakuItem(timeMs = 1000L, text = "弹幕1", mode = DanmakuMode.SCROLL, color = 0xFFFFFF),
            DanmakuItem(timeMs = 5000L, text = "弹幕2", mode = DanmakuMode.TOP, color = 0xFF0000),
        )

    @BeforeTest
    fun setup() {
        tempDir =
            File.createTempFile("danmaku_disk_cache_test_", "").apply {
                delete()
                mkdirs()
            }
        cache = FileDanmakuDiskCache(tempDir, maxEntries = 2, ttlMs = 10_000L)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun get_nonExistent_returnsNull() =
        runTest {
            assertNull(cache.get("non_existent_key"))
        }

    @Test
    fun putAndGet_returnsStoredItems() =
        runTest {
            val key = "danmaku:ep:12345"
            cache.put(key, sampleItems)

            val retrieved = cache.get(key)
            assertNotNull(retrieved)
            assertEquals(2, retrieved.size)
            assertEquals("弹幕1", retrieved[0].text)
            assertEquals(1000L, retrieved[0].timeMs)
            assertEquals("弹幕2", retrieved[1].text)
            assertEquals(5000L, retrieved[1].timeMs)
        }

    @Test
    fun get_expired_returnsNullAndPrunesFile() =
        runTest {
            val key = "danmaku:ep:expired"
            cache.put(key, sampleItems)

            val cachedFiles = tempDir.listFiles()?.toList().orEmpty()
            assertEquals(1, cachedFiles.size)
            // 模拟超时已达 15 秒（TTL 为 10 秒）
            cachedFiles[0].setLastModified(System.currentTimeMillis() - 15_000L)

            val retrieved = cache.get(key)
            assertNull(retrieved)
            // 过期文件应被删除
            assertEquals(0, tempDir.listFiles()?.size ?: 0)
        }

    @Test
    fun put_exceedingMaxEntries_prunesOldest() =
        runTest {
            val now = System.currentTimeMillis()
            cache.put("key1", sampleItems)
            val files1 = tempDir.listFiles()?.toList().orEmpty()
            assertEquals(1, files1.size)
            files1[0].setLastModified(now - 4000L)

            cache.put("key2", sampleItems)
            val files2 = tempDir.listFiles()?.toList().orEmpty()
            assertEquals(2, files2.size)
            files2.first { it != files1[0] }.setLastModified(now - 2000L)

            // 写入第 3 条，超过 maxEntries=2，最旧的 key1 应被淘汰
            cache.put("key3", sampleItems)
            val files3 = tempDir.listFiles()?.toList().orEmpty()
            assertEquals(2, files3.size)

            assertNull(cache.get("key1"))
            assertNotNull(cache.get("key2"))
            assertNotNull(cache.get("key3"))
        }

    @Test
    fun get_refreshesLastModified_protectsFromEviction() =
        runTest {
            val now = System.currentTimeMillis()
            cache.put("key1", sampleItems)
            val file1 = tempDir.listFiles()!!.first()
            file1.setLastModified(now - 4000L)

            cache.put("key2", sampleItems)
            val file2 = tempDir.listFiles()!!.first { it != file1 }
            file2.setLastModified(now - 2000L)

            // 访问 key1，命中后刷新 lastModified 为当前时间
            val retrieved = cache.get("key1")
            assertNotNull(retrieved)
            file1.setLastModified(now + 1000L)

            // 插入 key3，此时 key2 最旧 (now - 2000L)，应被淘汰
            cache.put("key3", sampleItems)
            val file3 = tempDir.listFiles()!!.first { it != file1 && it != file2 }
            file3.setLastModified(now)

            assertNotNull(cache.get("key1"))
            assertNull(cache.get("key2"))
            assertNotNull(cache.get("key3"))
        }

    @Test
    fun clear_removesAllCachedFiles() =
        runTest {
            cache.put("key1", sampleItems)
            cache.put("key2", sampleItems)
            assertEquals(2, tempDir.listFiles()?.size)

            cache.clear()
            assertEquals(0, tempDir.listFiles()?.size ?: 0)
            assertNull(cache.get("key1"))
            assertNull(cache.get("key2"))
        }
}
