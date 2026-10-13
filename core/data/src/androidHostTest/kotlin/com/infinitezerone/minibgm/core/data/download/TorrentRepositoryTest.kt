package com.infinitezerone.minibgm.core.data.download

import android.content.ContextWrapper
import com.infinitezerone.minibgm.core.data.repository.AndroidTorrentRepository
import com.infinitezerone.minibgm.core.model.DownloadState
import com.infinitezerone.minibgm.core.model.TorrentStreamState
import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
import com.infinitezerone.minibgm.core.network.AssetDownloadService
import com.infinitezerone.minibgm.core.testing.repository.FakeTorrentRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeTorrentStreamEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TorrentRepositoryTest {
    @Test
    fun fakeTorrentRepository_taskLifecycle_managesStateCorrectly() =
        runTest {
            val repository = FakeTorrentRepository()

            val taskId =
                repository.startDownload(
                    title = "葬送的芙莉莲 01",
                    magnetUrl = "magnet:?xt=urn:btih:0123456789abcdef",
                    torrentUrl = "https://mikanani.me/Download/20231001/01.torrent",
                    totalBytes = 450L * 1024 * 1024,
                )

            assertEquals(1, repository.startDownloadCallCount)
            assertEquals(1, repository.tasks.value.size)
            assertEquals(
                TorrentTaskStatus.DOWNLOADING,
                repository.tasks.value
                    .first()
                    .status,
            )

            repository.pauseDownload(taskId)
            assertEquals(1, repository.pauseDownloadCallCount)
            assertEquals(
                TorrentTaskStatus.PAUSED,
                repository.tasks.value
                    .first()
                    .status,
            )

            repository.resumeDownload(taskId)
            assertEquals(1, repository.resumeDownloadCallCount)
            assertEquals(
                TorrentTaskStatus.DOWNLOADING,
                repository.tasks.value
                    .first()
                    .status,
            )

            val torrentResult = repository.downloadTorrentFile("https://mikanani.me/Download/1.torrent", "ep1")
            assertTrue(torrentResult.isSuccess)
            assertEquals(1, repository.downloadTorrentFileCallCount)

            repository.cancelDownload(taskId)
            assertEquals(1, repository.cancelDownloadCallCount)
            assertTrue(repository.tasks.value.isEmpty())
        }

    @Test
    fun androidTorrentRepository_downloadsStreamToFile_andReusesCachedTorrentFile() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_torrent_repo_test").toFile()
            try {
                val context =
                    object : ContextWrapper(null) {
                        override fun getFilesDir(): File = File(tempRoot, "files").apply { mkdirs() }

                        override fun getCacheDir(): File = File(tempRoot, "cache").apply { mkdirs() }

                        override fun getExternalFilesDir(type: String?): File = File(tempRoot, "ext_files").apply { mkdirs() }
                    }

                val torrentDownloadCalls = AtomicInteger(0)
                val downloader =
                    object : AssetDownloader {
                        override val downloadState: StateFlow<DownloadState> = MutableStateFlow(DownloadState.Idle)

                        override suspend fun downloadAsset(
                            rawUrl: String,
                            destinationPath: String,
                            customMirrorPrefix: String?,
                        ): DownloadState {
                            torrentDownloadCalls.incrementAndGet()
                            File(destinationPath).apply {
                                parentFile?.mkdirs()
                                writeBytes(ByteArray(128) { 0x42 })
                            }
                            return DownloadState.Completed(destinationPath, 128L)
                        }

                        override fun cancelDownload() = Unit
                    }

                val streamPayload = ByteArray(4096) { (it % 251).toByte() }
                val streamService =
                    object : AssetDownloadService {
                        override suspend fun streamAsset(
                            url: String,
                            rangeStart: Long,
                            onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
                        ): Long {
                            onChunk(streamPayload, streamPayload.size, streamPayload.size.toLong(), false)
                            return streamPayload.size.toLong()
                        }
                    }

                val streamEngine = FakeTorrentStreamEngine(initialInstalled = true)
                val repo =
                    AndroidTorrentRepository(
                        context = context,
                        assetDownloader = downloader,
                        assetDownloadService = streamService,
                        streamEngine = streamEngine,
                        torrentTaskDao = FakeTorrentTaskDao(),
                        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                        startForegroundService = { _, _, _ -> },
                    )

                val taskId =
                    repo.startDownload(
                        title = "[Sakurato] Steel Ball Run [04]",
                        magnetUrl = "magnet:?xt=urn:btih:0327ac7116411c81f53636b41710246754a503b5",
                        torrentUrl = "https://mikanani.me/Download/04.torrent",
                        totalBytes = 0L,
                    )

                val completedTask = repo.tasks.value.single { it.id == taskId }
                assertEquals(TorrentTaskStatus.COMPLETED, completedTask.status)
                assertEquals(streamPayload.size.toLong(), completedTask.downloadedBytes)
                assertEquals(streamPayload.size.toLong(), completedTask.totalBytes)
                assertTrue(File(completedTask.savePath).isFile)
                assertEquals(streamPayload.size.toLong(), File(completedTask.savePath).length())
                assertEquals(1, torrentDownloadCalls.get())

                // Second call to downloadTorrentFile with same title must reuse cached .torrent without network request
                val secondTorrent = repo.downloadTorrentFile("https://mikanani.me/Download/04.torrent", "[Sakurato] Steel Ball Run [04]")
                assertTrue(secondTorrent.isSuccess)
                assertEquals(1, torrentDownloadCalls.get())
            } finally {
                tempRoot.deleteRecursively()
            }
        }

    @Test
    fun androidTorrentRepository_directEngineDiskMode_skipsLoopbackCopy_protectsSession_queuesAndPersists() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_torrent_direct_test").toFile()
            try {
                val context =
                    object : ContextWrapper(null) {
                        override fun getFilesDir(): File = File(tempRoot, "files").apply { mkdirs() }

                        override fun getCacheDir(): File = File(tempRoot, "cache").apply { mkdirs() }

                        override fun getExternalFilesDir(type: String?): File = File(tempRoot, "ext_files").apply { mkdirs() }
                    }

                val downloader =
                    object : AssetDownloader {
                        override val downloadState: StateFlow<DownloadState> = MutableStateFlow(DownloadState.Idle)

                        override suspend fun downloadAsset(
                            rawUrl: String,
                            destinationPath: String,
                            customMirrorPrefix: String?,
                        ): DownloadState {
                            File(destinationPath).apply {
                                parentFile?.mkdirs()
                                writeBytes(ByteArray(64) { 0x11 })
                            }
                            return DownloadState.Completed(destinationPath, 64L)
                        }

                        override fun cancelDownload() = Unit
                    }

                val loopbackStreamCalls = AtomicInteger(0)
                val streamService =
                    object : AssetDownloadService {
                        override suspend fun streamAsset(
                            url: String,
                            rangeStart: Long,
                            onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
                        ): Long {
                            loopbackStreamCalls.incrementAndGet()
                            return 0L
                        }
                    }

                val downloadDir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
                val nativeMkvFile =
                    File(downloadDir, "[Sakurato] Steel Ball Run [04][1080p].mkv").apply {
                        writeBytes(ByteArray(8192) { 0x55 })
                    }

                val streamEngine =
                    FakeTorrentStreamEngine(initialInstalled = true).apply {
                        mockTargetFilePath = nativeMkvFile.absolutePath
                    }

                val fakeDao = FakeTorrentTaskDao()
                val repo =
                    AndroidTorrentRepository(
                        context = context,
                        assetDownloader = downloader,
                        assetDownloadService = streamService,
                        streamEngine = streamEngine,
                        torrentTaskDao = fakeDao,
                        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                        startForegroundService = { _, _, _ -> },
                    )

                // 1. Start task 1 -> enters DOWNLOADING, exposes streamUrl for play-while-downloading, and protects session
                val taskId1 =
                    repo.startDownload(
                        title = "[Sakurato] Steel Ball Run [04]",
                        magnetUrl = "magnet:?xt=urn:btih:0327ac7116411c81f53636b41710246754a503b5",
                        torrentUrl = "https://mikanani.me/Download/04.torrent",
                        totalBytes = 8192L,
                    )

                // Duplicate startDownload with same infoHash must deduplicate and return taskId1
                val duplicateTaskId =
                    repo.startDownload(
                        title = "[Sakurato] Steel Ball Run [04] Dup",
                        magnetUrl = "magnet:?xt=urn:btih:0327AC7116411C81F53636B41710246754A503B5&tr=udp://tracker",
                        torrentUrl = null,
                        totalBytes = 8192L,
                    )
                assertEquals(taskId1, duplicateTaskId)

                val runningTask1 = repo.tasks.value.single { it.id == taskId1 }
                assertEquals(TorrentTaskStatus.DOWNLOADING, runningTask1.status)
                assertTrue(runningTask1.streamUrl.startsWith("http://127.0.0.1"))
                assertTrue(runningTask1.savePath.endsWith(".mkv"), "Real container extension (.mkv) must be preserved")
                assertTrue(streamEngine.isDownloadSessionProtected())

                // 2. Starting a second distinct task while task 1 is DOWNLOADING must queue (QUEUED), not kill task 1
                val taskId2 =
                    repo.startDownload(
                        title = "[Sakurato] Steel Ball Run [05]",
                        magnetUrl = "magnet:?xt=urn:btih:9999999999999999999999999999999999999999",
                        torrentUrl = null,
                        totalBytes = 8192L,
                    )
                assertEquals(
                    TorrentTaskStatus.DOWNLOADING,
                    repo.tasks.value
                        .single { it.id == taskId1 }
                        .status,
                )
                assertEquals(
                    TorrentTaskStatus.QUEUED,
                    repo.tasks.value
                        .single { it.id == taskId2 }
                        .status,
                )

                // 3. Exiting player during play-while-downloading calls releasePlayerSession / stopStream(force = false),
                //    which must NOT kill the protected background download
                streamEngine.setPlayerSessionActive(true)
                streamEngine.releasePlayerSession()
                assertTrue(
                    streamEngine.streamState.value !is com.infinitezerone.minibgm.core.model.TorrentStreamState.Idle,
                    "Exiting player must not kill active background download session",
                )

                // 4. Native engine reports intermediate and 100% piece completion -> connectedPeers is propagated,
                //    task 1 completes with zero HTTP loopback copy, and task 2 automatically dequeues into DOWNLOADING
                streamEngine.emitStreamState(
                    com.infinitezerone.minibgm.core.model.TorrentStreamState.Buffering(
                        progress = 0.5f,
                        bufferedBytes = 4096L,
                        targetBytes = 8192L,
                        speedBytesPerSec = 1024L,
                        connectedPeers = 8,
                    ),
                )
                assertEquals(
                    8,
                    repo.tasks.value
                        .single { it.id == taskId1 }
                        .connectedPeers,
                )

                streamEngine.emitStreamState(
                    com.infinitezerone.minibgm.core.model.TorrentStreamState.Buffering(
                        progress = 1f,
                        bufferedBytes = 8192L,
                        targetBytes = 8192L,
                        speedBytesPerSec = 1024L,
                        connectedPeers = 8,
                    ),
                )

                assertEquals(0, loopbackStreamCalls.get(), "Direct engine disk mode must not copy video over HTTP loopback")
                val completedTask1 = repo.tasks.value.single { it.id == taskId1 }
                assertEquals(TorrentTaskStatus.COMPLETED, completedTask1.status)
                assertEquals(8192L, completedTask1.downloadedBytes)
                assertEquals(0, completedTask1.connectedPeers)
                assertEquals(
                    TorrentTaskStatus.DOWNLOADING,
                    repo.tasks.value
                        .single { it.id == taskId2 }
                        .status,
                )

                // 5. Pause task 2 and verify tasks_manifest.json restores state in a new repository instance
                repo.pauseDownload(taskId2)
                val restoredRepo =
                    AndroidTorrentRepository(
                        context = context,
                        assetDownloader = downloader,
                        assetDownloadService = streamService,
                        streamEngine = streamEngine,
                        torrentTaskDao = fakeDao,
                        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                        startForegroundService = { _, _, _ -> },
                    )
                assertEquals(2, restoredRepo.tasks.value.size)
                assertEquals(
                    TorrentTaskStatus.COMPLETED,
                    restoredRepo.tasks.value
                        .single { it.id == taskId1 }
                        .status,
                )
                assertEquals(
                    TorrentTaskStatus.PAUSED,
                    restoredRepo.tasks.value
                        .single { it.id == taskId2 }
                        .status,
                )
            } finally {
                tempRoot.deleteRecursively()
            }
        }

    @Test
    fun androidTorrentRepository_peerConnectTimeout_transitionsToErrorAndAdvancesQueue() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_torrent_timeout_test").toFile()
            try {
                val context =
                    object : ContextWrapper(null) {
                        override fun getFilesDir(): File = File(tempRoot, "files").apply { mkdirs() }

                        override fun getCacheDir(): File = File(tempRoot, "cache").apply { mkdirs() }

                        override fun getExternalFilesDir(type: String?): File = File(tempRoot, "ext_files").apply { mkdirs() }
                    }

                val downloader =
                    object : AssetDownloader {
                        override val downloadState: StateFlow<DownloadState> = MutableStateFlow(DownloadState.Idle)

                        override suspend fun downloadAsset(
                            rawUrl: String,
                            destinationPath: String,
                            customMirrorPrefix: String?,
                        ): DownloadState {
                            File(destinationPath).apply {
                                parentFile?.mkdirs()
                                writeBytes(ByteArray(64) { 0x22 })
                            }
                            return DownloadState.Completed(destinationPath, 64L)
                        }

                        override fun cancelDownload() = Unit
                    }

                val streamService =
                    object : AssetDownloadService {
                        override suspend fun streamAsset(
                            url: String,
                            rangeStart: Long,
                            onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
                        ): Long = 0L
                    }

                val downloadDir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
                val nativeMkvFile = File(downloadDir, "[BlackClover] 171.mkv")

                val streamEngine =
                    FakeTorrentStreamEngine(initialInstalled = true).apply {
                        mockTargetFilePath = nativeMkvFile.absolutePath
                    }

                val repo =
                    AndroidTorrentRepository(
                        context = context,
                        assetDownloader = downloader,
                        assetDownloadService = streamService,
                        streamEngine = streamEngine,
                        torrentTaskDao = FakeTorrentTaskDao(),
                        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                        startForegroundService = { _, _, _ -> },
                        peerConnectTimeoutMs = 5_000L,
                    )

                val deadTaskId =
                    repo.startDownload(
                        title = "[BlackClover] 171",
                        magnetUrl = "magnet:?xt=urn:btih:032a25341d0df0b2263419fc18c975acaf8eb8e6",
                        torrentUrl = "https://mikanani.me/Download/171.torrent",
                        totalBytes = 750_000_000L,
                    )
                val nextTaskId =
                    repo.startDownload(
                        title = "[BlackClover] 172",
                        magnetUrl = "magnet:?xt=urn:btih:1111111111111111111111111111111111111111",
                        torrentUrl = null,
                        totalBytes = 750_000_000L,
                    )

                assertEquals(
                    TorrentTaskStatus.DOWNLOADING,
                    repo.tasks.value
                        .single { it.id == deadTaskId }
                        .status,
                )
                assertEquals(
                    TorrentTaskStatus.QUEUED,
                    repo.tasks.value
                        .single { it.id == nextTaskId }
                        .status,
                )

                // Advance virtual time past peerConnectTimeoutMs (5_000L) while 0 peers & 0 speed
                testScheduler.advanceTimeBy(5_100L)
                testScheduler.runCurrent()

                val failedTask = repo.tasks.value.single { it.id == deadTaskId }
                assertEquals(TorrentTaskStatus.ERROR, failedTask.status)
                assertEquals(AndroidTorrentRepository.PEER_CONNECT_TIMEOUT_MESSAGE, failedTask.errorMessage)

                // Next queued task automatically starts downloading
                val dequeuedTask = repo.tasks.value.single { it.id == nextTaskId }
                assertEquals(TorrentTaskStatus.DOWNLOADING, dequeuedTask.status)
            } finally {
                tempRoot.deleteRecursively()
            }
        }

    @Test
    fun androidTorrentRepository_startStreamDownload_preemptsBackgroundAndReusesTask() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_stream_download_test").toFile()
            try {
                val context =
                    object : ContextWrapper(null) {
                        override fun getFilesDir(): File = File(tempRoot, "files").apply { mkdirs() }

                        override fun getCacheDir(): File = File(tempRoot, "cache").apply { mkdirs() }

                        override fun getExternalFilesDir(type: String?): File = File(tempRoot, "ext_files").apply { mkdirs() }
                    }

                val downloader =
                    object : AssetDownloader {
                        override val downloadState: StateFlow<DownloadState> = MutableStateFlow(DownloadState.Idle)

                        override suspend fun downloadAsset(
                            rawUrl: String,
                            destinationPath: String,
                            customMirrorPrefix: String?,
                        ): DownloadState = DownloadState.Completed(destinationPath, 64L)

                        override fun cancelDownload() = Unit
                    }

                val streamService =
                    object : AssetDownloadService {
                        override suspend fun streamAsset(
                            url: String,
                            rangeStart: Long,
                            onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
                        ): Long = 0L
                    }

                val downloadDir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
                val streamEngine =
                    FakeTorrentStreamEngine(initialInstalled = true).apply {
                        mockTargetFilePath = File(downloadDir, "episode.mkv").absolutePath
                    }

                val repo =
                    AndroidTorrentRepository(
                        context = context,
                        assetDownloader = downloader,
                        assetDownloadService = streamService,
                        streamEngine = streamEngine,
                        torrentTaskDao = FakeTorrentTaskDao(),
                        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                        startForegroundService = { _, _, _ -> },
                        peerConnectTimeoutMs = 0L,
                    )

                // 1. 先启动一个后台下载任务 Ep 01
                val bgTaskId =
                    repo.startDownload(
                        title = "Ep 01",
                        magnetUrl = "magnet:?xt=urn:btih:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        totalBytes = 1000L,
                    )
                assertEquals(
                    TorrentTaskStatus.DOWNLOADING,
                    repo.tasks.value
                        .single { it.id == bgTaskId }
                        .status,
                )

                // 2. 用户在选源面板点击 Ep 02 的“边下边播” (startStreamDownload)
                //    Ep 01 应礼让退回 QUEUED，Ep 02 立即进入 DOWNLOADING 并返回可播放 streamUrl
                val streamResult =
                    repo.startStreamDownload(
                        title = "Ep 02",
                        magnetUrl = "magnet:?xt=urn:btih:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        totalBytes = 1000L,
                    )
                assertTrue(streamResult.isSuccess)
                assertEquals("http://127.0.0.1:8899/stream?hash=fake", streamResult.getOrNull())

                val bgTaskAfterPreempt = repo.tasks.value.single { it.id == bgTaskId }
                val streamTask = repo.tasks.value.single { it.title == "Ep 02" }
                assertEquals(TorrentTaskStatus.QUEUED, bgTaskAfterPreempt.status)
                assertEquals(TorrentTaskStatus.DOWNLOADING, streamTask.status)

                // 3. 再次对正在下载的 Ep 02 调用 startStreamDownload，直接复用当前流而不重启
                val reuseResult =
                    repo.startStreamDownload(
                        title = "Ep 02",
                        magnetUrl = "magnet:?xt=urn:btih:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        totalBytes = 1000L,
                    )
                assertEquals("http://127.0.0.1:8899/stream?hash=fake", reuseResult.getOrNull())
                assertEquals(2, repo.tasks.value.size)
            } finally {
                tempRoot.deleteRecursively()
            }
        }

    @Test
    fun androidTorrentRepository_stallWatchdog_triggersOnConnectedPeersWithZeroSpeed() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_stall_watchdog_test").toFile()
            try {
                val context =
                    object : ContextWrapper(null) {
                        override fun getFilesDir(): File = File(tempRoot, "files").apply { mkdirs() }

                        override fun getCacheDir(): File = File(tempRoot, "cache").apply { mkdirs() }

                        override fun getExternalFilesDir(type: String?): File = File(tempRoot, "ext_files").apply { mkdirs() }
                    }

                val downloader =
                    object : AssetDownloader {
                        override val downloadState: StateFlow<DownloadState> = MutableStateFlow(DownloadState.Idle)

                        override suspend fun downloadAsset(
                            rawUrl: String,
                            destinationPath: String,
                            customMirrorPrefix: String?,
                        ): DownloadState = DownloadState.Completed(destinationPath, 64L)

                        override fun cancelDownload() = Unit
                    }

                val streamService =
                    object : AssetDownloadService {
                        override suspend fun streamAsset(
                            url: String,
                            rangeStart: Long,
                            onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
                        ): Long = 0L
                    }

                // Fake 引擎：有 connectedPeers = 2，但 speedBytesPerSec = 0，downloadedBytes = 0
                val streamEngine =
                    FakeTorrentStreamEngine(initialInstalled = true).apply {
                        mockTargetFilePath = File(tempRoot, "stall.mkv").absolutePath
                    }

                val repo =
                    AndroidTorrentRepository(
                        context = context,
                        assetDownloader = downloader,
                        assetDownloadService = streamService,
                        streamEngine = streamEngine,
                        torrentTaskDao = FakeTorrentTaskDao(),
                        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                        startForegroundService = { _, _, _ -> },
                        peerConnectTimeoutMs = 50L,
                    )

                val taskId =
                    repo.startDownload(
                        title = "Stall Anime",
                        magnetUrl = "magnet:?xt=urn:btih:cccccccccccccccccccccccccccccccccccccccc",
                        totalBytes = 1000L,
                    )

                // 模拟引擎发射 Buffering 状态：已连上 2 个 Peer，但速度为 0，进度为 0
                streamEngine.emitStreamState(
                    TorrentStreamState.Buffering(
                        progress = 0f,
                        bufferedBytes = 0L,
                        targetBytes = 1000L,
                        speedBytesPerSec = 0L,
                        connectedPeers = 2,
                    ),
                )

                // 在 peerConnectTimeoutMs 触发前，任务仍为 DOWNLOADING
                assertEquals(
                    TorrentTaskStatus.DOWNLOADING,
                    repo.tasks.value
                        .single { it.id == taskId }
                        .status,
                )

                // 推进时钟超过超时阈值
                testScheduler.advanceTimeBy(100L)

                // 即使 connectedPeers > 0，因为零速度零进度，看门狗应精准熔断为 ERROR
                val taskAfterTimeout = repo.tasks.value.single { it.id == taskId }
                assertEquals(TorrentTaskStatus.ERROR, taskAfterTimeout.status)
            } finally {
                tempRoot.deleteRecursively()
            }
        }

    @Test
    fun androidTorrentRepository_pauseDownload_awaitsTeardown_doesNotStopNextTask() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_pause_teardown_test").toFile()
            try {
                val context =
                    object : ContextWrapper(null) {
                        override fun getFilesDir(): File = File(tempRoot, "files").apply { mkdirs() }

                        override fun getCacheDir(): File = File(tempRoot, "cache").apply { mkdirs() }

                        override fun getExternalFilesDir(type: String?): File = File(tempRoot, "ext_files").apply { mkdirs() }
                    }

                val downloader =
                    object : AssetDownloader {
                        override val downloadState: StateFlow<DownloadState> = MutableStateFlow(DownloadState.Idle)

                        override suspend fun downloadAsset(
                            rawUrl: String,
                            destinationPath: String,
                            customMirrorPrefix: String?,
                        ) = DownloadState.Completed(destinationPath, 64L)

                        override fun cancelDownload() = Unit
                    }

                val streamService =
                    object : AssetDownloadService {
                        override suspend fun streamAsset(
                            url: String,
                            rangeStart: Long,
                            onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
                        ): Long = 0L
                    }

                val downloadDir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
                val streamEngine =
                    FakeTorrentStreamEngine(initialInstalled = true).apply {
                        mockTargetFilePath = File(downloadDir, "ep.mkv").absolutePath
                    }

                val repo =
                    AndroidTorrentRepository(
                        context = context,
                        assetDownloader = downloader,
                        assetDownloadService = streamService,
                        streamEngine = streamEngine,
                        torrentTaskDao = FakeTorrentTaskDao(),
                        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                        startForegroundService = { _, _, _ -> },
                        peerConnectTimeoutMs = 0L,
                    )

                val task1Id =
                    repo.startDownload(
                        title = "Ep 1",
                        magnetUrl = "magnet:?xt=urn:btih:1111111111111111111111111111111111111111",
                        totalBytes = 1000L,
                    )
                val task2Id =
                    repo.startDownload(
                        title = "Ep 2",
                        magnetUrl = "magnet:?xt=urn:btih:2222222222222222222222222222222222222222",
                        totalBytes = 1000L,
                    )

                assertEquals(
                    TorrentTaskStatus.DOWNLOADING,
                    repo.tasks.value
                        .single { it.id == task1Id }
                        .status,
                )
                assertEquals(
                    TorrentTaskStatus.QUEUED,
                    repo.tasks.value
                        .single { it.id == task2Id }
                        .status,
                )

                // 暂停正在运行的任务 1：必须等待其协程与底层会话清理完成，再拉起任务 2
                repo.pauseDownload(task1Id)

                assertEquals(
                    TorrentTaskStatus.PAUSED,
                    repo.tasks.value
                        .single { it.id == task1Id }
                        .status,
                )
                val task2 = repo.tasks.value.single { it.id == task2Id }
                assertEquals(TorrentTaskStatus.DOWNLOADING, task2.status)
                // 确保任务 2 的会话依然处于受保护运行状态，没有被任务 1 的 finally 扼杀
                assertTrue(streamEngine.isDownloadSessionProtected())
            } finally {
                tempRoot.deleteRecursively()
            }
        }

    @Test
    fun androidTorrentRepository_restore_mergesWithConcurrentTaskWithoutDropping() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_restore_merge_test").toFile()
            try {
                val context =
                    object : ContextWrapper(null) {
                        override fun getFilesDir(): File = File(tempRoot, "files").apply { mkdirs() }

                        override fun getCacheDir(): File = File(tempRoot, "cache").apply { mkdirs() }

                        override fun getExternalFilesDir(type: String?): File = File(tempRoot, "ext_files").apply { mkdirs() }
                    }

                val downloader =
                    object : AssetDownloader {
                        override val downloadState: StateFlow<DownloadState> = MutableStateFlow(DownloadState.Idle)

                        override suspend fun downloadAsset(
                            rawUrl: String,
                            destinationPath: String,
                            customMirrorPrefix: String?,
                        ) = DownloadState.Completed(destinationPath, 64L)

                        override fun cancelDownload() = Unit
                    }

                val streamService =
                    object : AssetDownloadService {
                        override suspend fun streamAsset(
                            url: String,
                            rangeStart: Long,
                            onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
                        ): Long = 0L
                    }

                val fakeDao = FakeTorrentTaskDao()
                // 先在 DAO 中存入一个持久化任务
                fakeDao.upsertTask(
                    com.infinitezerone.minibgm.core.database.entity.TorrentTaskEntity(
                        id = "persisted-task-1",
                        title = "Persisted Episode",
                        magnetUrl = "magnet:?xt=urn:btih:3333333333333333333333333333333333333333",
                        torrentUrl = null,
                        torrentFilePath = null,
                        status = TorrentTaskStatus.PAUSED.name,
                        totalBytes = 2000L,
                        downloadedBytes = 500L,
                        downloadSpeedBytesPerSec = 0L,
                        uploadSpeedBytesPerSec = 0L,
                        connectedPeers = 0,
                        connectedSeeds = 0,
                        savePath = "/tmp/fake.mkv",
                        streamUrl = "",
                        errorMessage = null,
                        createdTimestamp = 1000L,
                        completionTimestamp = null,
                        subjectId = 1L,
                        subjectName = "Anime",
                        subjectCoverUrl = "",
                        episodeId = 1L,
                        episodeSort = 1f,
                        episodeName = "Ep 1",
                    ),
                )

                val streamEngine = FakeTorrentStreamEngine(initialInstalled = true)
                val repo =
                    AndroidTorrentRepository(
                        context = context,
                        assetDownloader = downloader,
                        assetDownloadService = streamService,
                        streamEngine = streamEngine,
                        torrentTaskDao = fakeDao,
                        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                        startForegroundService = { _, _, _ -> },
                        peerConnectTimeoutMs = 0L,
                    )

                // 启动一个新任务，restore 完成后两者必须都被合并保留，新任务绝不能被覆盖丢失
                val newTaskId =
                    repo.startDownload(
                        title = "New Episode",
                        magnetUrl = "magnet:?xt=urn:btih:4444444444444444444444444444444444444444",
                        totalBytes = 3000L,
                    )

                assertEquals(2, repo.tasks.value.size)
                assertTrue(repo.tasks.value.any { it.id == "persisted-task-1" })
                assertTrue(repo.tasks.value.any { it.id == newTaskId })
            } finally {
                tempRoot.deleteRecursively()
            }
        }
}
