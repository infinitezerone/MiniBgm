package com.infinitezerone.minibgm.core.data.download

import android.content.ContextWrapper
import com.infinitezerone.minibgm.core.model.DownloadState
import com.infinitezerone.minibgm.core.model.TorrentStreamState
import com.infinitezerone.minibgm.core.testing.repository.FakeTorrentStreamEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TorrentStreamEngineTest {
    @Test
    fun uninstalled_startStream_triggersPluginRequired() =
        runTest {
            val engine = FakeTorrentStreamEngine(initialInstalled = false)

            assertFalse(engine.isPluginInstalled())
            val result = engine.startStream("Title", "magnet:?xt=urn:btih:abc")

            assertTrue(result.isFailure)
            assertTrue(engine.streamState.value is TorrentStreamState.PluginRequired)
        }

    @Test
    fun installPlugin_succeedsAndAllowsStream() =
        runTest {
            val engine = FakeTorrentStreamEngine(initialInstalled = false)

            val installResult = engine.installPlugin()
            assertTrue(installResult.isSuccess)
            assertTrue(engine.isPluginInstalled())

            val streamResult = engine.startStream("Title", "magnet:?xt=urn:btih:abc")
            assertTrue(streamResult.isSuccess)
            assertEquals("http://127.0.0.1:8899/stream?hash=fake", streamResult.getOrNull())
            assertTrue(engine.streamState.value is TorrentStreamState.Ready)
        }

    @Test
    fun stopStream_resetsToIdle() =
        runTest {
            val engine = FakeTorrentStreamEngine(initialInstalled = true)

            engine.startStream("Title", "magnet:?xt=urn:btih:abc")
            assertTrue(engine.streamState.value is TorrentStreamState.Ready)

            engine.stopStream()
            assertTrue(engine.streamState.value is TorrentStreamState.Idle)
        }

    @Test
    fun startStream_withTorrentUrl_succeeds() =
        runTest {
            val engine = FakeTorrentStreamEngine(initialInstalled = true)

            val streamResult = engine.startStream("Title", "magnet:?xt=urn:btih:abc", "https://example.com/test.torrent")
            assertTrue(streamResult.isSuccess)
            assertEquals("http://127.0.0.1:8899/stream?hash=fake", streamResult.getOrNull())
        }

    @Test
    fun androidEngine_installPlugin_downloadsVersionedSo_marksReadOnly_andCleansStaleVersions() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_torrent_test").toFile()
            try {
                val context = StubDirContext(tempRoot)
                // Create a stale version directory from a previous release
                val staleSo =
                    File(context.filesDir, "modules/torrent/v0.2.7/libminibgm_torrent.so").apply {
                        parentFile?.mkdirs()
                        writeText("stale-so-binary")
                        setReadOnly()
                    }
                assertTrue(staleSo.exists())

                val requestedUrls = mutableListOf<String>()
                val payload = ByteArray(1024) { 0x7F }
                val downloader =
                    StubAssetDownloader { url, destinationPath ->
                        requestedUrls += url
                        File(destinationPath).apply {
                            parentFile?.mkdirs()
                            writeBytes(payload)
                        }
                        DownloadState.Completed(filePath = destinationPath, totalBytes = 1024L)
                    }
                val bridge = StubNativeTorrentBridge()
                var currentVersion = "0.2.8"

                val engine =
                    AndroidTorrentStreamEngine(
                        context = context,
                        assetDownloader = downloader,
                        pluginVersionProvider = { currentVersion },
                        supportedAbisProvider = { arrayOf("arm64-v8a") },
                        nativeBridge = bridge,
                        expectedPluginHashes = mapOf("arm64-v8a" to sha256Hex(payload)),
                    )

                assertFalse(engine.isPluginInstalled())

                val result = engine.installPlugin()
                assertTrue(result.isSuccess)
                assertTrue(engine.isPluginInstalled())

                val versionedSo = File(context.filesDir, "modules/torrent/v0.2.8/libminibgm_torrent.so")
                assertTrue(versionedSo.isFile)
                assertFalse(versionedSo.canWrite(), "Downloaded .so must be marked read-only for Android 10+ W^X")
                assertFalse(staleSo.parentFile!!.exists(), "Stale v0.2.7 plugin directory must be cleaned up")
                assertEquals(
                    listOf("https://github.com/infinitezerone/MiniBgm/releases/download/v0.2.8/libminibgm_torrent-arm64-v8a.so"),
                    requestedUrls,
                )

                // Simulate app upgrade to 0.2.9: isPluginInstalled() must re-verify and return false
                currentVersion = "0.2.9"
                assertFalse(engine.isPluginInstalled(), "Upgrading app version must invalidate old v0.2.8 .so")
            } finally {
                tempRoot.walkBottomUp().forEach {
                    it.setWritable(true)
                    it.delete()
                }
            }
        }

    @Test
    fun androidEngine_installPlugin_versionedTag404_failsClosedWithoutLatestFallback_andSerializesConcurrentCalls() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_torrent_missing_tag_test").toFile()
            try {
                val context = StubDirContext(tempRoot)
                val requestedUrls = mutableListOf<String>()
                val downloader =
                    StubAssetDownloader { url, _ ->
                        requestedUrls += url
                        delay(20)
                        DownloadState.Failed("HTTP 404")
                    }
                val bridge = StubNativeTorrentBridge()
                val engine =
                    AndroidTorrentStreamEngine(
                        context = context,
                        assetDownloader = downloader,
                        pluginVersionProvider = { "0.9.9" },
                        supportedAbisProvider = { arrayOf("x86_64") },
                        nativeBridge = bridge,
                        expectedPluginHashes = mapOf("x86_64" to sha256Hex(ByteArray(512) { 1 })),
                    )

                // 两个并发调用都会被 Mutex 串行化；每个调用只尝试固定版本 URL。
                val first = async { engine.installPlugin() }
                val second = async { engine.installPlugin() }

                assertTrue(first.await().isFailure)
                assertTrue(second.await().isFailure)
                assertEquals(2, requestedUrls.size)
                assertTrue(requestedUrls.all { it.contains("/download/v0.9.9/") })
                assertFalse(requestedUrls.any { it.contains("/latest/") })
                assertTrue(engine.streamState.value is TorrentStreamState.Error)
            } finally {
                tempRoot.walkBottomUp().forEach {
                    it.setWritable(true)
                    it.delete()
                }
            }
        }

    @Test
    fun normalizePluginVersion_stripsDebugAndSuffixes() {
        assertEquals("0.2.8", AndroidTorrentStreamEngine.normalizePluginVersion("0.2.8.debug"))
        assertEquals("0.3.0", AndroidTorrentStreamEngine.normalizePluginVersion("0.3.0-rc1"))
        assertEquals(AndroidTorrentStreamEngine.DEFAULT_PLUGIN_VERSION, AndroidTorrentStreamEngine.normalizePluginVersion(null))
        assertEquals(AndroidTorrentStreamEngine.DEFAULT_PLUGIN_VERSION, AndroidTorrentStreamEngine.normalizePluginVersion("  "))
    }

    @Test
    fun androidEngine_setDebugSimulateUninstalled_bypassesEmbeddedAndDeletesDynamicSo() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_torrent_debug_panel").toFile()
            try {
                val context = StubDirContext(tempRoot)
                val payload = ByteArray(256) { 1 }
                val downloader =
                    StubAssetDownloader { _, destinationPath ->
                        File(destinationPath).apply {
                            parentFile?.mkdirs()
                            writeBytes(payload)
                        }
                        DownloadState.Completed(filePath = destinationPath, totalBytes = 256L)
                    }
                val bridge = StubNativeTorrentBridge(embeddedAvailable = true)
                val engine =
                    AndroidTorrentStreamEngine(
                        context = context,
                        assetDownloader = downloader,
                        pluginVersionProvider = { "0.2.8" },
                        supportedAbisProvider = { arrayOf("arm64-v8a") },
                        nativeBridge = bridge,
                        expectedPluginHashes = mapOf("arm64-v8a" to sha256Hex(payload)),
                    )

                // Default in Debug APK: embedded library is available
                assertFalse(engine.isDebugSimulateUninstalled())
                assertTrue(engine.isPluginInstalled())

                // Developer turns on "Simulate Uninstalled" in Developer Debug Panel
                engine.setDebugSimulateUninstalled(true)
                assertTrue(engine.isDebugSimulateUninstalled())
                assertFalse(engine.isPluginInstalled())

                // Now user confirms dialog and installs plugin from cloud -> dynamic .so is loaded
                assertTrue(engine.installPlugin().isSuccess)
                assertTrue(engine.isPluginInstalled())
                val dynamicSo = File(context.filesDir, "modules/torrent/v0.2.8/libminibgm_torrent.so")
                assertTrue(dynamicSo.isFile)

                // Developer resets again from Developer Debug Panel -> dynamic .so is deleted & uninstalled state returns
                engine.setDebugSimulateUninstalled(true)
                assertFalse(dynamicSo.exists())
                assertFalse(engine.isPluginInstalled())

                // Turning off simulation restores embedded fallback
                engine.setDebugSimulateUninstalled(false)
                assertFalse(engine.isDebugSimulateUninstalled())
                assertTrue(engine.isPluginInstalled())
            } finally {
                tempRoot.walkBottomUp().forEach {
                    it.setWritable(true)
                    it.delete()
                }
            }
        }

    @Test
    fun androidEngine_startStream_switchingEpisode_reparsesInsteadOfReusingSession() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_torrent_episode_isolation").toFile()
            try {
                val context = StubDirContext(tempRoot)
                val downloader =
                    StubAssetDownloader { _, destinationPath ->
                        File(destinationPath).apply {
                            parentFile?.mkdirs()
                            writeBytes(validTorrentBytes())
                        }
                        DownloadState.Completed(filePath = destinationPath, totalBytes = 64L)
                    }
                val bridge = RecordingNativeTorrentBridge()
                val engine =
                    AndroidTorrentStreamEngine(
                        context = context,
                        assetDownloader = downloader,
                        pluginVersionProvider = { "0.2.8" },
                        supportedAbisProvider = { arrayOf("arm64-v8a") },
                        nativeBridge = bridge,
                    )
                val magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"

                assertTrue(engine.startStream(title = "合集 BD", magnetUrl = magnet, episodeHint = "1").isSuccess)
                assertEquals(listOf("1"), bridge.startEpisodeHints)

                // 同一分集重复请求：允许复用已激活会话（不得重复拉起原生会话）
                assertTrue(engine.startStream(title = "合集 BD", magnetUrl = magnet, episodeHint = "1").isSuccess)
                assertEquals(listOf("1"), bridge.startEpisodeHints)

                // 换集（同 infoHash）：必须重新解析目标文件，绝不能复用上一集的会话与落盘路径
                assertTrue(engine.startStream(title = "合集 BD", magnetUrl = magnet, episodeHint = "2").isSuccess)
                assertEquals(listOf("1", "2"), bridge.startEpisodeHints)
            } finally {
                tempRoot.walkBottomUp().forEach {
                    it.setWritable(true)
                    it.delete()
                }
            }
        }

    @Test
    fun androidEngine_prefetch_corruptCachedTorrent_isDeletedAndRedownloaded() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_torrent_payload_guard").toFile()
            try {
                val context = StubDirContext(tempRoot)
                val torrentUrl = "https://example.com/collection.torrent"
                // 预置镜像 HTML 错误页：旧实现只按「文件名 + 非空」判缓存命中，会永久复用坏种子
                val corruptCached =
                    File(
                        context.cacheDir,
                        "torrents/${TorrentFileCache.cacheKey(magnetUrl = "", torrentUrl = torrentUrl, title = "合集 BD")}.torrent",
                    ).apply {
                        parentFile?.mkdirs()
                        writeText("<html><body>404 Not Found</body></html>")
                    }
                assertTrue(corruptCached.isFile)

                var downloadCount = 0
                val downloader =
                    StubAssetDownloader { _, destinationPath ->
                        downloadCount++
                        File(destinationPath).apply {
                            parentFile?.mkdirs()
                            writeBytes(validTorrentBytes())
                        }
                        DownloadState.Completed(filePath = destinationPath, totalBytes = 64L)
                    }
                val bridge = RecordingNativeTorrentBridge()
                val engine =
                    AndroidTorrentStreamEngine(
                        context = context,
                        assetDownloader = downloader,
                        pluginVersionProvider = { "0.2.8" },
                        supportedAbisProvider = { arrayOf("arm64-v8a") },
                        nativeBridge = bridge,
                    )

                val result =
                    engine.startStream(
                        title = "合集 BD",
                        magnetUrl = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
                        torrentUrl = torrentUrl,
                        episodeHint = "1",
                    )

                assertTrue(result.isSuccess)
                assertEquals(1, downloadCount, "坏缓存必须被删除并重新下载")
                assertFalse(corruptCached.readText().contains("404 Not Found"))
                assertTrue(TorrentFileCache.isValidPayload(corruptCached))
            } finally {
                tempRoot.walkBottomUp().forEach {
                    it.setWritable(true)
                    it.delete()
                }
            }
        }

    @Test
    fun androidEngine_statsPolling_survivesTransientFailureInsteadOfHanging() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_torrent_stats_resilience").toFile()
            try {
                val context = StubDirContext(tempRoot)
                var failCount = 0
                val bridge =
                    FlakyStatsNativeTorrentBridge(
                        failuresBeforeSuccess = 3,
                        onFailure = { failCount++ },
                    )
                val engine =
                    AndroidTorrentStreamEngine(
                        context = context,
                        pluginVersionProvider = { "0.2.8" },
                        supportedAbisProvider = { arrayOf("arm64-v8a") },
                        nativeBridge = bridge,
                        statsPollIntervalMs = 10L,
                    )

                assertTrue(
                    engine
                        .startStream(
                            title = "合集 BD",
                            magnetUrl = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
                        ).isSuccess,
                )

                // 单次 stats 读取失败绝不能永久中断轮询流：连续失败后必须恢复并继续发射 Buffering，
                // 否则下游 first{} 与死种看门狗会双双无界挂起。
                //
                // 轮询跑在引擎自有的 `Dispatchers.IO` 上（真实时间），因此必须切到真实调度器再套超时：
                // 留在 runTest 的虚拟时钟里，5s 会被瞬间推进直接判超时（与轮询真实进度无关）。
                val buffering =
                    withContext(Dispatchers.Default.limitedParallelism(1)) {
                        withTimeout(5_000) {
                            engine.streamState.first {
                                it is TorrentStreamState.Buffering && it.targetBytes == 100_000_000L
                            }
                        }
                    } as? TorrentStreamState.Buffering

                assertTrue(failCount >= 3)
                assertTrue(buffering != null)
                assertEquals(100_000_000L, buffering.targetBytes)
                engine.stopStream(force = true)
            } finally {
                tempRoot.walkBottomUp().forEach {
                    it.setWritable(true)
                    it.delete()
                }
            }
        }

    /**
     * 引擎级错误可见性：librqbit 把种子判为 `Error`（磁盘写入失败 / 初始化校验失败）时，
     * 过去只表现为"0 速度 0 Peer"，用户只能在 45 秒后被死种看门狗告知"连不上做种节点"——
     * 真实原因被伪装成死种。必须立刻以原生原文收敛为 `TorrentStreamState.Error`。
     */
    @Test
    fun androidEngine_statsPolling_surfacesEngineErrorReasonInsteadOfDeadTorrentZeroSpeed() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_torrent_engine_error").toFile()
            try {
                val context = StubDirContext(tempRoot)
                val reason = "fatal error: No space left on device"
                // 首次读取（起播初值）仍健康，之后转为引擎级错误态：覆盖轮询途中才暴露的致命错误
                val bridge = EngineErrorNativeTorrentBridge(healthyReads = 1, reason = reason)
                val engine =
                    AndroidTorrentStreamEngine(
                        context = context,
                        pluginVersionProvider = { "0.2.8" },
                        supportedAbisProvider = { arrayOf("arm64-v8a") },
                        nativeBridge = bridge,
                        statsPollIntervalMs = 10L,
                    )

                assertTrue(
                    engine
                        .startStream(
                            title = "合集 BD",
                            magnetUrl = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
                        ).isSuccess,
                )

                // 轮询跑在引擎自有的 Dispatchers.IO 上（真实时间），必须切真实调度器再套超时
                val error =
                    withContext(Dispatchers.Default.limitedParallelism(1)) {
                        withTimeout(5_000) {
                            engine.streamState.first { it is TorrentStreamState.Error }
                        }
                    } as TorrentStreamState.Error

                assertEquals(reason, error.message)
                engine.stopStream(force = true)
            } finally {
                tempRoot.walkBottomUp().forEach {
                    it.setWritable(true)
                    it.delete()
                }
            }
        }

    /** 起播瞬间引擎就已是错误态：必须直接暴露真实原因，不先发一个会被立刻推翻的 Ready。 */
    @Test
    fun androidEngine_startStream_whenEngineAlreadyErrored_surfacesReasonImmediately() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_torrent_engine_error_initial").toFile()
            try {
                val context = StubDirContext(tempRoot)
                val reason = "fatal error: torrent initial check failed"
                val bridge = EngineErrorNativeTorrentBridge(healthyReads = 0, reason = reason)
                val engine =
                    AndroidTorrentStreamEngine(
                        context = context,
                        pluginVersionProvider = { "0.2.8" },
                        supportedAbisProvider = { arrayOf("arm64-v8a") },
                        nativeBridge = bridge,
                        statsPollIntervalMs = 10L,
                    )

                engine.startStream(
                    title = "合集 BD",
                    magnetUrl = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
                )

                val error = engine.streamState.value as? TorrentStreamState.Error
                assertTrue(error != null, "起播即错误态必须立刻收敛为 Error，当前: ${engine.streamState.value}")
                assertEquals(reason, error.message)
                engine.stopStream(force = true)
            } finally {
                tempRoot.walkBottomUp().forEach {
                    it.setWritable(true)
                    it.delete()
                }
            }
        }

    @Test
    fun androidEngine_installPlugin_rejectsHashMismatch_andDeletesStaging() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_torrent_hash_mismatch").toFile()
            try {
                val context = StubDirContext(tempRoot)
                val downloadedPayload = ByteArray(1024) { 0x42 }
                val downloader =
                    StubAssetDownloader { _, destinationPath ->
                        File(destinationPath).apply {
                            parentFile?.mkdirs()
                            writeBytes(downloadedPayload)
                        }
                        DownloadState.Completed(filePath = destinationPath, totalBytes = 1024L)
                    }
                val engine =
                    AndroidTorrentStreamEngine(
                        context = context,
                        assetDownloader = downloader,
                        pluginVersionProvider = { "0.2.8" },
                        supportedAbisProvider = { arrayOf("arm64-v8a") },
                        nativeBridge = StubNativeTorrentBridge(),
                        expectedPluginHashes = mapOf("arm64-v8a" to "00".repeat(32)),
                    )

                assertTrue(engine.installPlugin().isFailure)
                assertFalse(engine.isPluginInstalled())

                val versionedSo = File(context.filesDir, "modules/torrent/v0.2.8/libminibgm_torrent.so")
                assertFalse(versionedSo.exists(), "哈希不匹配的 .so 绝不能落到最终加载路径")
                assertFalse(
                    File(context.filesDir, "modules/torrent/v0.2.8/libminibgm_torrent.so.staging").exists(),
                    "哈希不匹配的 staging 文件必须被清理",
                )
                assertTrue(engine.streamState.value is TorrentStreamState.Error)
            } finally {
                tempRoot.walkBottomUp().forEach {
                    it.setWritable(true)
                    it.delete()
                }
            }
        }

    @Test
    fun androidEngine_isPluginInstalled_deletesTamperedCachedSo() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_torrent_tampered_cache").toFile()
            try {
                val context = StubDirContext(tempRoot)
                val tamperedPayload = ByteArray(1024) { 0x24 }
                val versionedSo =
                    File(context.filesDir, "modules/torrent/v0.2.8/libminibgm_torrent.so").apply {
                        parentFile?.mkdirs()
                        writeBytes(tamperedPayload)
                    }
                val engine =
                    AndroidTorrentStreamEngine(
                        context = context,
                        pluginVersionProvider = { "0.2.8" },
                        supportedAbisProvider = { arrayOf("arm64-v8a") },
                        nativeBridge = StubNativeTorrentBridge(),
                        expectedPluginHashes = mapOf("arm64-v8a" to sha256Hex(ByteArray(1024) { 0x7F })),
                    )

                assertFalse(engine.isPluginInstalled(), "被篡改的已缓存 .so 不得被装载")
                assertFalse(versionedSo.exists(), "校验失败的缓存文件必须删除，避免下次启动再次命中")
            } finally {
                tempRoot.walkBottomUp().forEach {
                    it.setWritable(true)
                    it.delete()
                }
            }
        }
}

private fun validTorrentBytes(): ByteArray = "d8:announce14:http://tracker4:infod6:lengthi1e4:name3:abcee".toByteArray(Charsets.ISO_8859_1)

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }

private class StubDirContext(
    private val rootDir: File,
) : ContextWrapper(null) {
    override fun getFilesDir(): File = File(rootDir, "files").apply { mkdirs() }

    override fun getCacheDir(): File = File(rootDir, "cache").apply { mkdirs() }
}

private class StubAssetDownloader(
    private val handler: suspend (url: String, destinationPath: String) -> DownloadState,
) : AssetDownloader {
    override val downloadState: StateFlow<DownloadState> = MutableStateFlow(DownloadState.Idle)

    override fun download(
        rawUrl: String,
        destinationPath: String,
        customMirrorPrefix: String?,
    ): Flow<DownloadState> =
        flow {
            emit(handler(rawUrl, destinationPath))
        }

    override suspend fun downloadAsset(
        rawUrl: String,
        destinationPath: String,
        customMirrorPrefix: String?,
    ): DownloadState = handler(rawUrl, destinationPath)

    override fun cancelDownload() = Unit
}

private class StubNativeTorrentBridge(
    private val embeddedAvailable: Boolean = false,
) : NativeTorrentBridge {
    private var loadedPath: String? = null
    private var embeddedLoaded: Boolean = false

    override fun loadFromPath(absolutePath: String): Boolean {
        val f = File(absolutePath)
        if (f.isFile && f.length() > 0L) {
            loadedPath = absolutePath
            return true
        }
        return false
    }

    override fun loadEmbeddedLibrary(): Boolean {
        if (embeddedAvailable) {
            embeddedLoaded = true
            return true
        }
        return false
    }

    override fun isAvailable(): Boolean = loadedPath != null || embeddedLoaded

    override fun startStream(
        magnetUrl: String,
        torrentPath: String,
        saveDir: String,
        episodeHint: String,
    ): String = "http://127.0.0.1:18080/stream/0"

    override fun stopStream() = Unit

    override fun getStreamStats(): LongArray = longArrayOf(0L, 100_000_000L, 512_000L, 4L, AndroidTorrentStreamEngine.STATS_STATE_HEALTHY)
}

/** 前 [failuresBeforeSuccess] 次 [getStreamStats] 抛异常，用于验证轮询“瞬时失败不致命”。 */
private class FlakyStatsNativeTorrentBridge(
    private val failuresBeforeSuccess: Int,
    private val onFailure: () -> Unit = {},
) : NativeTorrentBridge {
    private var calls = 0

    override fun loadFromPath(absolutePath: String): Boolean = File(absolutePath).isFile

    override fun loadEmbeddedLibrary(): Boolean = true

    override fun isAvailable(): Boolean = true

    override fun startStream(
        magnetUrl: String,
        torrentPath: String,
        saveDir: String,
        episodeHint: String,
    ): String = "http://127.0.0.1:18080/stream/0"

    override fun stopStream() = Unit

    override fun getStreamStats(): LongArray {
        calls++
        if (calls <= failuresBeforeSuccess) {
            onFailure()
            throw IllegalStateException("transient stats failure #$calls")
        }
        return longArrayOf(0L, 100_000_000L, 512_000L, 4L, AndroidTorrentStreamEngine.STATS_STATE_HEALTHY)
    }
}

/** [healthyReads] 次健康统计后转为引擎级错误态（模拟磁盘写入失败 / 初始化校验失败等致命错误）。 */
private class EngineErrorNativeTorrentBridge(
    private val healthyReads: Int,
    private val reason: String,
) : NativeTorrentBridge {
    private var calls = 0

    override fun loadFromPath(absolutePath: String): Boolean = File(absolutePath).isFile

    override fun loadEmbeddedLibrary(): Boolean = true

    override fun isAvailable(): Boolean = true

    override fun startStream(
        magnetUrl: String,
        torrentPath: String,
        saveDir: String,
        episodeHint: String,
    ): String = "http://127.0.0.1:18080/stream/0"

    override fun stopStream() = Unit

    override fun getStreamStats(): LongArray {
        calls++
        return if (calls <= healthyReads) {
            longArrayOf(0L, 100_000_000L, 512_000L, 4L, AndroidTorrentStreamEngine.STATS_STATE_HEALTHY)
        } else {
            longArrayOf(0L, 100_000_000L, 0L, 0L, AndroidTorrentStreamEngine.STATS_STATE_ENGINE_ERROR)
        }
    }

    override fun getLastError(): String = if (calls > healthyReads) reason else ""
}

/** 记录每次真正拉起原生会话时的 episodeHint，用于断言「换集必须重新解析而非复用会话」。 */
private class RecordingNativeTorrentBridge : NativeTorrentBridge {
    val startEpisodeHints = mutableListOf<String>()

    override fun loadFromPath(absolutePath: String): Boolean = File(absolutePath).isFile

    override fun loadEmbeddedLibrary(): Boolean = true

    override fun isAvailable(): Boolean = true

    override fun startStream(
        magnetUrl: String,
        torrentPath: String,
        saveDir: String,
        episodeHint: String,
    ): String {
        startEpisodeHints += episodeHint
        return "http://127.0.0.1:18080/stream/${startEpisodeHints.size - 1}"
    }

    override fun stopStream() = Unit

    override fun getStreamStats(): LongArray = longArrayOf(0L, 100_000_000L, 512_000L, 4L, AndroidTorrentStreamEngine.STATS_STATE_HEALTHY)
}
