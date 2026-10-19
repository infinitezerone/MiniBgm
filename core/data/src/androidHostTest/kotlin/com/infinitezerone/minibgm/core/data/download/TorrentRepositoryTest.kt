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
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
                            // 必须是合法 bencode 载荷：坏种子会被 TorrentFileCache 拒收并重下
                            val payload = validTorrentBytes()
                            File(destinationPath).apply {
                                parentFile?.mkdirs()
                                writeBytes(payload)
                            }
                            return DownloadState.Completed(destinationPath, payload.size.toLong())
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
                assertTrue(TorrentFileCache.isValidPayload(File(secondTorrent.getOrThrow())))
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

    /**
     * 播放优先：用户看第 5 集（本地回环流）时给第 6 集起离线下载，
     * 单实例 librqbit 会话下任何新任务都会 release 掉正在播放的会话——必须礼让排队，
     * 并在播放器释放会话后自动接手（不允许永久卡在 QUEUED）。
     */
    @Test
    fun androidTorrentRepository_offlineDownload_yieldsToActivePlaybackAndResumesOnRelease() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_playback_priority_test").toFile()
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
                        mockTargetFilePath = File(downloadDir, "episode06.mkv").absolutePath
                        // 模拟前台播放器正在消费第 5 集的本地流会话
                        setPlayerSessionActive(true)
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

                val offlineTaskId =
                    repo.startDownload(
                        title = "Ep 06",
                        magnetUrl = "magnet:?xt=urn:btih:cccccccccccccccccccccccccccccccccccccccc",
                        totalBytes = 1000L,
                    )

                assertEquals(
                    TorrentTaskStatus.QUEUED,
                    repo.tasks.value
                        .single { it.id == offlineTaskId }
                        .status,
                )
                assertNull(
                    streamEngine.getActiveInfoHash(),
                    "播放期间严禁启动会 release 掉当前播放会话的新任务（否则直接掐断用户正在看的画面）",
                )
                assertTrue(streamEngine.isPlayerSessionActive(), "下载侧不得清除播放器会话标记")

                testScheduler.advanceTimeBy(10_000L)
                testScheduler.runCurrent()
                assertEquals(
                    TorrentTaskStatus.QUEUED,
                    repo.tasks.value
                        .single { it.id == offlineTaskId }
                        .status,
                )

                // 播放器释放会话（退出播放器 / 切到在线线路）后必须被唤醒接手
                streamEngine.setPlayerSessionActive(false)
                testScheduler.runCurrent()

                val resumedTask = repo.tasks.value.single { it.id == offlineTaskId }
                assertEquals(TorrentTaskStatus.DOWNLOADING, resumedTask.status)
                assertTrue(resumedTask.streamUrl.startsWith("http://127.0.0.1"))
            } finally {
                tempRoot.deleteRecursively()
            }
        }

    /**
     * 用户显式点播（边下边播）必须仍能接管播放中的会话：此刻播放器本就准备切到新地址，
     * 若一并礼让会让 startStreamDownload 死等到获取超时。
     */
    @Test
    fun androidTorrentRepository_startStreamDownload_takesOverActivePlaybackSession() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_playback_takeover_test").toFile()
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
                        mockTargetFilePath = File(downloadDir, "episode07.mkv").absolutePath
                        setPlayerSessionActive(true)
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

                val streamResult =
                    repo.startStreamDownload(
                        title = "Ep 07",
                        magnetUrl = "magnet:?xt=urn:btih:dddddddddddddddddddddddddddddddddddddddd",
                        totalBytes = 1000L,
                    )

                assertTrue(streamResult.isSuccess)
                assertEquals(
                    TorrentTaskStatus.DOWNLOADING,
                    repo.tasks.value
                        .single { it.title == "Ep 07" }
                        .status,
                )
                assertTrue(streamEngine.getActiveInfoHash() != null)
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

    @Test
    fun androidTorrentRepository_startDownload_sameMagnetDifferentEpisode_isNotDeduplicated() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_episode_isolation_test").toFile()
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
                        mockTargetFilePath = File(downloadDir, "collection.mkv").absolutePath
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

                // 合集/BD 包：三集共用同一磁力（同一 infoHash）
                val collectionMagnet = "magnet:?xt=urn:btih:cccccccccccccccccccccccccccccccccccccccc"
                val ep1 =
                    repo.startDownload(
                        title = "合集 BD",
                        magnetUrl = collectionMagnet,
                        totalBytes = 4096L,
                        episodeId = 101L,
                        episodeSort = 1f,
                        episodeName = "第一话",
                    )
                val ep2 =
                    repo.startDownload(
                        title = "合集 BD",
                        magnetUrl = collectionMagnet,
                        totalBytes = 4096L,
                        episodeId = 102L,
                        episodeSort = 2f,
                        episodeName = "第二话",
                    )

                // 同磁力换集必须是两个独立任务，否则第 2 集会复用第 1 集的 streamUrl / 落盘路径
                assertTrue(ep1 != ep2, "同一磁力的不同分集绝不能被去重合并")
                assertEquals(2, repo.tasks.value.size)

                // 同一分集重复请求仍然去重复用
                val ep1Again =
                    repo.startDownload(
                        title = "合集 BD",
                        magnetUrl = collectionMagnet,
                        totalBytes = 4096L,
                        episodeId = 101L,
                        episodeSort = 1f,
                        episodeName = "第一话",
                    )
                assertEquals(ep1, ep1Again)
                assertEquals(2, repo.tasks.value.size)
            } finally {
                tempRoot.deleteRecursively()
            }
        }

    @Test
    fun androidTorrentRepository_startStreamDownload_streamNeverReady_returnsFailureInsteadOfThrowing() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_stream_timeout_test").toFile()
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

                // 起播永不就绪（死种）：任务停留在 RESOLVING，永远拿不到可播地址
                val streamEngine =
                    FakeTorrentStreamEngine(initialInstalled = true).apply {
                        hangOnStartStream = true
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

                // 业务失败必须以 Result.failure 返回：一旦以 CancellationException 冒泡，
                // 会连带取消调用方协程，UI 的 onFailure 永不执行，用户全程无提示。
                val result =
                    repo.startStreamDownload(
                        title = "Ep 01",
                        magnetUrl = "magnet:?xt=urn:btih:dddddddddddddddddddddddddddddddddddddddd",
                        totalBytes = 1000L,
                    )

                assertTrue(result.isFailure)
                assertEquals(
                    AndroidTorrentRepository.STREAM_ACQUISITION_TIMEOUT_MESSAGE,
                    result.exceptionOrNull()?.message,
                )
            } finally {
                tempRoot.deleteRecursively()
            }
        }

    @Test
    fun androidTorrentRepository_pipeline_discardsStaleTorrentFilePathFromDbAndRefetches() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_stale_torrent_path_test").toFile()
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
                            val payload = validTorrentBytes()
                            File(destinationPath).apply {
                                parentFile?.mkdirs()
                                writeBytes(payload)
                            }
                            return DownloadState.Completed(destinationPath, payload.size.toLong())
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

                // DB 里留着一个「路径存在但载荷非法」的种子：镜像返回 HTTP 200 的 HTML 错误页，
                // AssetDownloader 只按字节数判成功，历史上会被永久复用。
                val staleTorrent =
                    File(context.getCacheDir(), "torrents")
                        .apply { mkdirs() }
                        .resolve("url_deadbeef.torrent")
                        .apply { writeText("<html><body>404 Not Found</body></html>") }

                val fakeDao = FakeTorrentTaskDao()
                fakeDao.upsertTask(
                    com.infinitezerone.minibgm.core.database.entity.TorrentTaskEntity(
                        id = "stale-torrent-task",
                        title = "Stale Episode",
                        magnetUrl = "magnet:?xt=urn:btih:5555555555555555555555555555555555555555",
                        torrentUrl = "https://example.org/stale.torrent",
                        torrentFilePath = staleTorrent.absolutePath,
                        status = TorrentTaskStatus.PAUSED.name,
                        totalBytes = 1000L,
                        downloadedBytes = 0L,
                        downloadSpeedBytesPerSec = 0L,
                        uploadSpeedBytesPerSec = 0L,
                        connectedPeers = 0,
                        connectedSeeds = 0,
                        savePath = File(context.getExternalFilesDir(null), "stale.mp4").absolutePath,
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

                repo.resumeDownload("stale-torrent-task", resetDownloadedBytes = true)

                // 坏路径必须被丢弃并重新获取：若照旧复用，引擎每次启动都拿到同一个坏种子，
                // 用户只能清应用数据自救。
                assertEquals(1, torrentDownloadCalls.get())
                assertFalse(staleTorrent.exists(), "载荷非法的 DB 种子路径必须就地删除")
                val refreshed =
                    repo.tasks.value
                        .single { it.id == "stale-torrent-task" }
                        .torrentFilePath
                assertTrue(
                    refreshed != null && TorrentFileCache.isValidPayload(File(refreshed)),
                    "重新获取的种子必须是合法载荷",
                )
            } finally {
                tempRoot.deleteRecursively()
            }
        }

    @Test
    fun exportTaskToGallery_withNonExistentTask_returnsFailure() =
        runTest {
            val tempRoot = Files.createTempDirectory("torrent-export-test").toFile()
            try {
                val repo = createTestRepository(tempRoot, testScheduler)
                val result = repo.exportTaskToGallery("non-existent-id")
                assertTrue(result.isFailure)
            } finally {
                tempRoot.deleteRecursively()
            }
        }

    @Test
    fun exportTaskToGallery_withUncompletedTask_returnsFailure() =
        runTest {
            val tempRoot = Files.createTempDirectory("torrent-export-test-uncompleted").toFile()
            try {
                val repo = createTestRepository(tempRoot, testScheduler)
                val taskId = repo.startDownload("Ep 01", "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567")
                val result = repo.exportTaskToGallery(taskId)
                assertTrue(result.isFailure)
            } finally {
                tempRoot.deleteRecursively()
            }
        }

    private fun createTestRepository(
        tempRoot: File,
        testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
        dao: FakeTorrentTaskDao = FakeTorrentTaskDao(),
    ): AndroidTorrentRepository {
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
                ): DownloadState = DownloadState.Completed(destinationPath, 0L)

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
        return AndroidTorrentRepository(
            context = context,
            assetDownloader = downloader,
            assetDownloadService = streamService,
            streamEngine = FakeTorrentStreamEngine(initialInstalled = true),
            torrentTaskDao = dao,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            startForegroundService = { _, _, _ -> },
            peerConnectTimeoutMs = 0L,
        )
    }
}

private fun validTorrentBytes(): ByteArray = "d8:announce14:http://tracker4:infod6:lengthi1e4:name3:abcee".toByteArray(Charsets.ISO_8859_1)
