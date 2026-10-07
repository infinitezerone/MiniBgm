package com.infinitezerone.minibgm.core.data.seasonal

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FileSeasonalDiskCacheTest {
    private lateinit var tempDir: File
    private lateinit var cache: FileSeasonalDiskCache

    @BeforeTest
    fun setup() {
        tempDir =
            File.createTempFile("seasonal_test_", "").apply {
                delete()
                mkdirs()
            }
        cache = FileSeasonalDiskCache(tempDir, maxEntries = 2)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun get_nonExistent_returnsNull() =
        runTest {
            assertNull(cache.get("2026-autumn"))
        }

    @Test
    fun putAndGet_returnsStoredContent() =
        runTest {
            cache.put("2026-autumn", "{\"season\":\"autumn\"}")
            val content = cache.get("2026-autumn")
            assertEquals("{\"season\":\"autumn\"}", content)
        }

    @Test
    fun put_exceedingMaxEntries_prunesOldestFile() =
        runTest {
            cache.put("2025-winter", "{\"season\":\"winter\"}")
            // 稍微间隔以确保时间戳不同
            val winterFile = File(tempDir, "2025-winter.json")
            winterFile.setLastModified(1000L)

            cache.put("2025-spring", "{\"season\":\"spring\"}")
            val springFile = File(tempDir, "2025-spring.json")
            springFile.setLastModified(2000L)

            // 写入第三个，此时超过 maxEntries = 2，最旧的 2025-winter 应被淘汰
            cache.put("2025-summer", "{\"season\":\"summer\"}")
            val summerFile = File(tempDir, "2025-summer.json")
            summerFile.setLastModified(3000L)

            assertNull(cache.get("2025-winter"))
            assertNotNull(cache.get("2025-spring"))
            assertNotNull(cache.get("2025-summer"))
        }

    @Test
    fun get_refreshesLastModified_protectsFromEviction() =
        runTest {
            cache.put("season-1", "{\"n\":1}")
            val file1 = File(tempDir, "season-1.json")
            file1.setLastModified(1000L)

            cache.put("season-2", "{\"n\":2}")
            val file2 = File(tempDir, "season-2.json")
            file2.setLastModified(2000L)

            // 访问 season-1，刷新其 lastModified
            cache.get("season-1")
            file1.setLastModified(5000L)

            // 插入 season-3，此时 season-2 是最旧的 (2000L)，应淘汰 season-2
            cache.put("season-3", "{\"n\":3}")
            val file3 = File(tempDir, "season-3.json")
            file3.setLastModified(4000L)

            assertNotNull(cache.get("season-1"))
            assertNull(cache.get("season-2"))
            assertNotNull(cache.get("season-3"))
        }

    @Test
    fun clear_removesAllCachedFiles() =
        runTest {
            cache.put("season-1", "1")
            cache.put("season-2", "2")
            cache.clear()
            assertNull(cache.get("season-1"))
            assertNull(cache.get("season-2"))
        }
}
