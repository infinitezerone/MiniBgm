package com.infinitezerone.minibgm.core.data.download

import android.content.ContextWrapper
import com.infinitezerone.minibgm.core.model.DownloadState
import com.infinitezerone.minibgm.core.model.TorrentStreamState
import com.infinitezerone.minibgm.core.testing.repository.FakeTorrentStreamEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
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
                val downloader =
                    StubAssetDownloader { url, destinationPath ->
                        requestedUrls += url
                        File(destinationPath).apply {
                            parentFile?.mkdirs()
                            writeBytes(ByteArray(1024) { 0x7F })
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
    fun androidEngine_installPlugin_fallsBackToLatestWhenVersionedTag404s_andSerializesConcurrentCalls() =
        runTest {
            val tempRoot = Files.createTempDirectory("minibgm_torrent_fallback_test").toFile()
            try {
                val context = StubDirContext(tempRoot)
                val downloadCalls = AtomicInteger(0)
                val downloader =
                    StubAssetDownloader { url, destinationPath ->
                        downloadCalls.incrementAndGet()
                        delay(20)
                        if (url.contains("/download/v0.9.9/")) {
                            DownloadState.Failed("HTTP 404")
                        } else {
                            File(destinationPath).apply {
                                parentFile?.mkdirs()
                                writeBytes(ByteArray(512) { 1 })
                            }
                            DownloadState.Completed(filePath = destinationPath, totalBytes = 512L)
                        }
                    }
                val bridge = StubNativeTorrentBridge()
                val engine =
                    AndroidTorrentStreamEngine(
                        context = context,
                        assetDownloader = downloader,
                        pluginVersionProvider = { "0.9.9" },
                        supportedAbisProvider = { arrayOf("x86_64") },
                        nativeBridge = bridge,
                    )

                // Launch two concurrent installPlugin() calls; Mutex should ensure second sees installed state
                val first = async { engine.installPlugin() }
                val second = async { engine.installPlugin() }

                assertTrue(first.await().isSuccess)
                assertTrue(second.await().isSuccess)
                // Only 2 HTTP attempts total (1 failed tag URL + 1 succeeded latest fallback), not 4
                assertEquals(2, downloadCalls.get())

                val streamRes =
                    engine.startStream(
                        title = "[Sub] Ep 01",
                        magnetUrl = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
                        torrentUrl = "https://example.com/ep01.torrent",
                    )
                assertTrue(streamRes.isSuccess)
                assertEquals("http://127.0.0.1:18080/stream/0", streamRes.getOrNull())

                // Second call hits cached .torrent file
                val cachedStreamRes =
                    engine.startStream(
                        title = "[Sub] Ep 01",
                        magnetUrl = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
                        torrentUrl = "https://example.com/ep01.torrent",
                    )
                assertTrue(cachedStreamRes.isSuccess)
                engine.stopStream()
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
                val downloader =
                    StubAssetDownloader { _, destinationPath ->
                        File(destinationPath).apply {
                            parentFile?.mkdirs()
                            writeBytes(ByteArray(256) { 1 })
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
    ): String = "http://127.0.0.1:18080/stream/0"

    override fun stopStream() = Unit

    override fun getStreamStats(): LongArray = longArrayOf(0L, 100_000_000L, 512_000L, 4L, 2L)
}
