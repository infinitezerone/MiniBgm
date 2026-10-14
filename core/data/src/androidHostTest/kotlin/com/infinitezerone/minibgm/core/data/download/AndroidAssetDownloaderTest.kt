package com.infinitezerone.minibgm.core.data.download

import com.infinitezerone.minibgm.core.model.DownloadState
import com.infinitezerone.minibgm.core.network.AssetDownloadService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAssetDownloaderTest {
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("downloader_test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun download_emitsColdFlow_withProgressAndCompleted() =
        runTest {
            val content = "Hello MiniBgm World!".toByteArray()
            val fakeService =
                object : AssetDownloadService {
                    override suspend fun streamAsset(
                        url: String,
                        rangeStart: Long,
                        onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
                    ): Long {
                        onChunk(content, content.size, content.size.toLong(), false)
                        return content.size.toLong()
                    }
                }

            val downloader = AndroidAssetDownloader(fakeService, Dispatchers.Unconfined)
            val targetFile = File(tempDir, "output.txt")

            val states = downloader.download("https://example.com/file.txt", targetFile.absolutePath).toList()

            assertTrue(states.any { it is DownloadState.Downloading })
            val completed = states.last()
            assertIs<DownloadState.Completed>(completed)
            assertEquals(targetFile.absolutePath, completed.filePath)
            assertEquals(content.size.toLong(), completed.totalBytes)
            assertEquals("Hello MiniBgm World!", targetFile.readText())
        }

    @Test
    fun download_independentFlows_multipleConcurrentDownloadsDoNotInterfere() =
        runTest {
            val contentA = "Resource A Content".toByteArray()
            val contentB = "Resource B Content Large".toByteArray()

            val fakeService =
                object : AssetDownloadService {
                    override suspend fun streamAsset(
                        url: String,
                        rangeStart: Long,
                        onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
                    ): Long {
                        val content = if (url.contains("A")) contentA else contentB
                        onChunk(content, content.size, content.size.toLong(), false)
                        return content.size.toLong()
                    }
                }

            val downloader = AndroidAssetDownloader(fakeService, Dispatchers.Unconfined)
            val fileA = File(tempDir, "a.txt")
            val fileB = File(tempDir, "b.txt")

            val jobA = async { downloader.download("https://example.com/A", fileA.absolutePath).toList() }
            val jobB = async { downloader.download("https://example.com/B", fileB.absolutePath).toList() }

            val statesA = jobA.await()
            val statesB = jobB.await()

            val completedA = statesA.last() as DownloadState.Completed
            val completedB = statesB.last() as DownloadState.Completed

            assertEquals(contentA.size.toLong(), completedA.totalBytes)
            assertEquals(contentB.size.toLong(), completedB.totalBytes)
            assertEquals("Resource A Content", fileA.readText())
            assertEquals("Resource B Content Large", fileB.readText())
        }

    @Test
    fun download_sameDestinationPath_isSerializedByMutex() =
        runTest {
            val content = "Synchronized Content".toByteArray()
            val concurrentWriters = AtomicInteger(0)
            val maxConcurrentWriters = AtomicInteger(0)
            val downloadGate = CompletableDeferred<Unit>()

            val fakeService =
                object : AssetDownloadService {
                    override suspend fun streamAsset(
                        url: String,
                        rangeStart: Long,
                        onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
                    ): Long {
                        val active = concurrentWriters.incrementAndGet()
                        maxConcurrentWriters.updateAndGet { current -> maxOf(current, active) }
                        downloadGate.await()
                        onChunk(content, content.size, content.size.toLong(), false)
                        concurrentWriters.decrementAndGet()
                        return content.size.toLong()
                    }
                }

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val downloader = AndroidAssetDownloader(fakeService, testDispatcher)
            val targetFile = File(tempDir, "shared.bin")

            val job1 = launch(testDispatcher) { downloader.download("https://example.com/file1", targetFile.absolutePath).toList() }
            val job2 = launch(testDispatcher) { downloader.download("https://example.com/file2", targetFile.absolutePath).toList() }

            testScheduler.advanceUntilIdle()
            // 此时由于相同目标路径受 Mutex 保护，只有一个协程能进入 streamAsset
            assertEquals(1, concurrentWriters.get())

            downloadGate.complete(Unit)
            testScheduler.advanceUntilIdle()

            job1.join()
            job2.join()

            // 无论如何，同一时刻互斥写同一个目标路径的最大并发数必须严格为 1
            assertEquals(1, maxConcurrentWriters.get())
        }

    @Test
    fun download_cancellation_cancelsStreamAndReleasesResources() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val fakeService =
                object : AssetDownloadService {
                    override suspend fun streamAsset(
                        url: String,
                        rangeStart: Long,
                        onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
                    ): Long {
                        if (url.contains("large")) {
                            started.complete(Unit)
                            kotlinx.coroutines.awaitCancellation()
                        } else {
                            val data = "Recovered Content".toByteArray()
                            onChunk(data, data.size, data.size.toLong(), false)
                            return data.size.toLong()
                        }
                    }
                }

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val downloader = AndroidAssetDownloader(fakeService, testDispatcher)
            val targetFile = File(tempDir, "cancelled.bin")

            val job =
                launch(testDispatcher) {
                    downloader.download("https://example.com/large", targetFile.absolutePath).toList()
                }

            testScheduler.runCurrent()
            started.await()

            job.cancelAndJoin()
            assertTrue(job.isCancelled)

            // 验证路径互斥锁已在取消后安全释放：同一目标路径可立即被后续下载正常加锁写入
            val result = downloader.downloadAsset("https://example.com/other", targetFile.absolutePath)
            assertIs<DownloadState.Completed>(result)
            assertEquals("Recovered Content", targetFile.readText())
        }

    @Test
    fun downloadAsset_suspendingMethod_delegatesAndReturnsCompleted() =
        runTest {
            val content = "Suspending result".toByteArray()
            val fakeService =
                object : AssetDownloadService {
                    override suspend fun streamAsset(
                        url: String,
                        rangeStart: Long,
                        onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
                    ): Long {
                        onChunk(content, content.size, content.size.toLong(), false)
                        return content.size.toLong()
                    }
                }

            val downloader = AndroidAssetDownloader(fakeService, Dispatchers.Unconfined)
            val targetFile = File(tempDir, "suspend_test.txt")

            val result = downloader.downloadAsset("https://example.com/test", targetFile.absolutePath)
            assertIs<DownloadState.Completed>(result)
            assertEquals(targetFile.absolutePath, result.filePath)
            assertEquals("Suspending result", targetFile.readText())
        }
}
