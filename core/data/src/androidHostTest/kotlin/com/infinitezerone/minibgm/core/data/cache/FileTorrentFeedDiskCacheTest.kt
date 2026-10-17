package com.infinitezerone.minibgm.core.data.cache

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileTorrentFeedDiskCacheTest {
    private lateinit var tempDir: File
    private lateinit var cache: FileTorrentFeedDiskCache

    @BeforeTest
    fun setup() {
        tempDir =
            File.createTempFile("torrent_cache_test_", "").apply {
                delete()
                mkdirs()
            }
        cache = FileTorrentFeedDiskCache(tempDir, maxEntries = 2)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun get_nonExistent_returnsNull() =
        runTest {
            assertNull(cache.get("feed:test:https://example.com/rss"))
        }

    @Test
    fun putAndGet_returnsStoredContent() =
        runTest {
            val key = "feed:rule1:https://mikanani.me/rss"
            val json = """[{"title":"[NC-Raws] Test - 01"}]"""
            cache.put(key, json)
            val result = cache.get(key)
            assertEquals(json, result)
        }

    @Test
    fun put_exceedingMaxEntries_prunesOldest() =
        runTest {
            cache.put("key1", "json1")
            val files1 = tempDir.listFiles()?.toList().orEmpty()
            assertEquals(1, files1.size)
            files1[0].setLastModified(1000L)

            cache.put("key2", "json2")
            val files2 = tempDir.listFiles()?.toList().orEmpty()
            assertEquals(2, files2.size)
            files2.first { it != files1[0] }.setLastModified(2000L)

            // 写入第 3 条，超过 maxEntries=2，最旧的 key1 应被淘汰
            cache.put("key3", "json3")
            assertEquals(2, tempDir.listFiles()?.size)
            assertNull(cache.get("key1"))
            assertEquals("json2", cache.get("key2"))
            assertEquals("json3", cache.get("key3"))
        }
}
