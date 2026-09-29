package com.infinitezerone.minibgm.core.data.crash

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [FileCrashLogRepository] 的分支覆盖。
 *
 * 仓库刻意接收**目录**而不是 `Context`，所以这里拿临时目录就能把全部分支跑完，
 * 不必为了伪造 `Context.filesDir` 引入 Robolectric。
 */
class FileCrashLogRepositoryTest {
    private val crashDir: File = Files.createTempDirectory("crash-log-test").toFile()

    @AfterTest
    fun cleanUp() {
        crashDir.deleteRecursively()
    }

    private fun repository() = FileCrashLogRepository(crashDir)

    private fun writeLog(
        name: String,
        content: String = "boom",
    ): File = File(crashDir, name).apply { writeText(content) }

    @Test
    fun latest_whenDirectoryMissing_returnsNull() =
        runTest {
            // 从未崩溃过：目录压根不存在，入口该给"暂无记录"而不是报错
            assertNull(FileCrashLogRepository(File(crashDir, "missing")).latest())
        }

    @Test
    fun latest_whenDirectoryEmpty_returnsNull() =
        runTest {
            assertTrue(crashDir.isDirectory)
            assertNull(repository().latest())
        }

    @Test
    fun latest_returnsNewestByFileName() =
        runTest {
            // epoch 毫秒恒为 13 位，所以文件名倒序即时间倒序
            writeLog("crash-1000000000000.txt", "旧")
            writeLog("crash-1700000000000.txt", "新")
            writeLog("crash-1500000000000.txt", "中")

            val log = repository().latest()

            assertEquals("新", log?.content)
            assertEquals(1_700_000_000_000L, log?.occurredAtMillis)
        }

    @Test
    fun latest_ignoresFilesWithoutTxtSuffix() =
        runTest {
            writeLog("crash-1700000000000.txt", "真日志")
            writeLog("notes.md", "不是日志")
            writeLog("crash-1800000000000.log", "后缀不对")

            assertEquals("真日志", repository().latest()?.content)
        }

    @Test
    fun latest_whenFileNameHasNoTimestamp_stillReturnsContent() =
        runTest {
            // 命名异常不该把整份日志判成无效——内容还在，时间给 0
            writeLog("crash-broken.txt", "内容还在")

            val log = repository().latest()

            assertEquals("内容还在", log?.content)
            assertEquals(0L, log?.occurredAtMillis)
        }

    @Test
    fun clear_removesEveryFile() =
        runTest {
            writeLog("crash-1700000000000.txt")
            writeLog("notes.md")

            repository().clear()

            assertEquals(0, crashDir.listFiles()?.size)
            assertNull(repository().latest())
        }

    @Test
    fun clear_whenDirectoryMissing_doesNotThrow() =
        runTest {
            val missing = File(crashDir, "not-created")

            FileCrashLogRepository(missing).clear()

            assertFalse(missing.exists())
        }
}
