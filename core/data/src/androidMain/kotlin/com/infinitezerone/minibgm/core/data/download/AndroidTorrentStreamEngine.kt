package com.infinitezerone.minibgm.core.data.download

import android.content.Context
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.model.DownloadState
import com.infinitezerone.minibgm.core.model.TorrentStreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

internal interface NativeTorrentBridge {
    fun loadFromPath(absolutePath: String): Boolean

    fun loadEmbeddedLibrary(): Boolean

    fun isAvailable(): Boolean

    fun startStream(
        magnetUrl: String,
        torrentPath: String,
        saveDir: String,
        episodeHint: String = "",
    ): String

    fun stopStream()

    fun getStreamStats(): LongArray

    fun getTargetFilePath(): String = ""
}

/**
 * Android 平台磁力/BT 边下边播引擎实现。
 *
 * 依托 `crates/minibgm-torrent` (Rust cdylib) 原生实现的嵌入式 HTTP 流媒体服务端与顺序缓冲调度，
 * 彻底消除虚假空 Socket 占位与伪装库崩溃风险。
 */
class AndroidTorrentStreamEngine internal constructor(
    private val context: Context,
    private val assetDownloader: AssetDownloader? = null,
    private val pluginVersionProvider: () -> String = { resolveAppVersion(context) },
    private val supportedAbisProvider: () -> Array<String> = { android.os.Build.SUPPORTED_ABIS },
    private val nativeBridge: NativeTorrentBridge = JniNativeTorrentBridge,
) : TorrentStreamEngine {
    constructor(
        context: Context,
        assetDownloader: AssetDownloader? = null,
    ) : this(
        context = context,
        assetDownloader = assetDownloader,
        pluginVersionProvider = { resolveAppVersion(context) },
        supportedAbisProvider = { android.os.Build.SUPPORTED_ABIS },
        nativeBridge = JniNativeTorrentBridge,
    )

    private val logger = bgmLogger("Bgm/TorrentStreamEngine")
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val installMutex = Mutex()
    private val _streamState = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Idle)
    override val streamState: StateFlow<TorrentStreamState> = _streamState.asStateFlow()

    private var activeStreamJob: Job? = null

    @Volatile
    private var loadedVersion: String? = null

    @Volatile
    private var loadedDynamicFile: File? = null

    @Volatile
    private var debugSimulateUninstalled: Boolean = false

    @Volatile
    private var activeTargetFilePath: String? = null

    @Volatile
    private var activeInfoHash: String? = null

    @Volatile
    private var activeStreamUrl: String? = null

    @Volatile
    private var activeSaveDir: String? = null

    @Volatile
    private var downloadSessionProtected: Boolean = false

    @Volatile
    private var playerSessionActive: Boolean = false

    override fun isDebugSimulateUninstalled(): Boolean = debugSimulateUninstalled

    override fun getActiveTargetFilePath(): String? = activeTargetFilePath

    override fun getActiveInfoHash(): String? = activeInfoHash

    override fun setDownloadSessionProtected(protected: Boolean) {
        downloadSessionProtected = protected
    }

    override fun isDownloadSessionProtected(): Boolean = downloadSessionProtected

    override fun setPlayerSessionActive(active: Boolean) {
        playerSessionActive = active
    }

    override fun isPlayerSessionActive(): Boolean = playerSessionActive

    override fun releasePlayerSession() {
        playerSessionActive = false
        scope.launch {
            stopStream(force = false)
        }
    }

    override suspend fun isPluginInstalled(): Boolean {
        val currentVersion = pluginVersionProvider()
        return ensureVersionedPluginLoaded(currentVersion) &&
            runCatching { nativeBridge.isAvailable() }.getOrDefault(false)
    }

    override suspend fun installPlugin(): Result<Unit> =
        withContext(Dispatchers.IO) {
            installMutex.withLock {
                if (isPluginInstalled()) {
                    return@withLock Result.success(Unit)
                }

                val downloader =
                    assetDownloader
                        ?: return@withLock Result.failure(IllegalStateException("未配置组件下载服务"))

                val supportedDeviceAbis = supportedAbisProvider()
                val abi =
                    supportedDeviceAbis
                        .firstOrNull { it in SUPPORTED_ABIS }
                        ?: return@withLock Result.failure(
                            IllegalStateException("当前设备架构 (${supportedDeviceAbis.joinToString()}) 不支持 BT 原生扩展"),
                        )

                val currentVersion = pluginVersionProvider()
                val modulesRoot = File(context.filesDir, PLUGIN_DIR_REL_PATH).apply { mkdirs() }
                cleanStalePluginDirs(modulesRoot, expectedDirName = versionDirName(currentVersion))

                val versionDir = File(modulesRoot, versionDirName(currentVersion)).apply { mkdirs() }
                val targetSoFile = File(versionDir, PLUGIN_SO_NAME)
                val stagingFile = File(versionDir, "$PLUGIN_SO_NAME$STAGING_SUFFIX")

                val candidateReleaseUrls =
                    listOf(
                        "$RELEASE_REPO_BASE_URL/download/v$currentVersion/libminibgm_torrent-$abi.so",
                        "$RELEASE_PLUGIN_BASE_URL/libminibgm_torrent-$abi.so",
                    ).distinct()

                _streamState.value = TorrentStreamState.InstallingPlugin(0f)

                var lastErrMsg = "BT 原生扩展下载未完成"
                var downloaded = false

                if (debugSimulateUninstalled && extractDebugBundledPlugin(abi, stagingFile)) {
                    logger.i { "开发者调试模拟态：已从本地 APK 提取 BT 原生扩展 ($abi) 至动态装载目录" }
                    downloaded = true
                }

                if (!downloaded) {
                    for (downloadUrl in candidateReleaseUrls) {
                        cleanStagingLeftovers(stagingFile)
                        logger.i { "开始按需下载 BT 原生扩展 ($abi, v$currentVersion): $downloadUrl" }
                        val dlState =
                            downloader
                                .download(downloadUrl, stagingFile.absolutePath)
                                .onEach { state ->
                                    if (state is DownloadState.Downloading) {
                                        val total = state.progress.totalBytes
                                        val ratio =
                                            if (total > 0L) {
                                                (state.progress.bytesDownloaded.toFloat() / total.toFloat()).coerceIn(0f, 0.99f)
                                            } else {
                                                0f
                                            }
                                        _streamState.value = TorrentStreamState.InstallingPlugin(ratio)
                                    }
                                }.filter { it is DownloadState.Completed || it is DownloadState.Failed }
                                .first()

                        if (dlState is DownloadState.Completed && stagingFile.isFile && stagingFile.length() > 0L) {
                            downloaded = true
                            break
                        }
                        lastErrMsg = (dlState as? DownloadState.Failed)?.message ?: lastErrMsg
                    }
                }

                if (!downloaded) {
                    _streamState.value = TorrentStreamState.Error(lastErrMsg)
                    return@withLock Result.failure(IllegalStateException(lastErrMsg))
                }

                if (targetSoFile.exists()) {
                    targetSoFile.setWritable(true)
                    targetSoFile.delete()
                }
                if (!stagingFile.renameTo(targetSoFile)) {
                    stagingFile.copyTo(targetSoFile, overwrite = true)
                    stagingFile.delete()
                }

                // Android 10+ (API 29+) W^X 安全约束：动态加载的私有目录 .so 必须标记为只读
                targetSoFile.setReadOnly()

                if (ensureVersionedPluginLoaded(currentVersion) &&
                    runCatching { nativeBridge.isAvailable() }.getOrDefault(false)
                ) {
                    _streamState.value = TorrentStreamState.Idle
                    logger.i { "BT 原生扩展动态装载成功 (v$currentVersion): ${targetSoFile.absolutePath}" }
                    Result.success(Unit)
                } else {
                    _streamState.value = TorrentStreamState.Error("原生扩展校验或装载失败")
                    Result.failure(IllegalStateException("BT 原生流化库装载失败"))
                }
            }
        }

    override suspend fun startStream(
        title: String,
        magnetUrl: String,
        torrentUrl: String?,
        saveDirPath: String?,
        episodeHint: String?,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!isPluginInstalled()) {
                    _streamState.value = TorrentStreamState.PluginRequired()
                    error("BT 流化组件未安装或当前设备架构不可用")
                }

                val infoHash = extractInfoHash(magnetUrl)
                val targetSaveDir =
                    saveDirPath
                        ?.takeIf { it.isNotBlank() }
                        ?.let { File(it).apply { mkdirs() }.absolutePath }
                        ?: File(context.cacheDir, "torrent_stream").apply { mkdirs() }.absolutePath

                val existingUrl = activeStreamUrl
                if (infoHash != "unknown_hash" &&
                    activeInfoHash.equals(infoHash, ignoreCase = true) &&
                    !existingUrl.isNullOrBlank() &&
                    activeStreamJob?.isActive == true &&
                    (saveDirPath == null || activeSaveDir == targetSaveDir)
                ) {
                    logger.i { "复用已激活的原生 BT 流媒体会话: $infoHash -> $existingUrl" }
                    return@runCatching existingUrl
                }

                releaseCurrentStreamInternal()
                _streamState.value = TorrentStreamState.Resolving(infoHash = infoHash)

                val torrentFilePath = prefetchTorrentIfPresent(title, torrentUrl)

                logger.i {
                    "调用 Rust 原生引擎启动 HTTP 流媒体代理: $infoHash (torrent: $torrentFilePath, saveDir: $targetSaveDir, episodeHint: $episodeHint)"
                }
                val resultUrl =
                    nativeBridge.startStream(
                        magnetUrl = magnetUrl,
                        torrentPath = torrentFilePath,
                        saveDir = targetSaveDir,
                        episodeHint = episodeHint ?: "",
                    )

                if (resultUrl.startsWith("ERROR:")) {
                    val errMsg = resultUrl.removePrefix("ERROR:").trim()
                    _streamState.value = TorrentStreamState.Error(errMsg)
                    error("启动原生流媒体服务失败: $errMsg")
                }

                val resolvedTargetFile =
                    runCatching { nativeBridge.getTargetFilePath() }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }

                activeInfoHash = infoHash
                activeStreamUrl = resultUrl
                activeTargetFilePath = resolvedTargetFile
                activeSaveDir = targetSaveDir

                logger.i { "原生流媒体服务就绪: $resultUrl (targetFile: $resolvedTargetFile)" }

                // 立即获取初次真值 stats（杜绝 100MB 伪分母污染）
                val initialStats = runCatching { nativeBridge.getStreamStats() }.getOrNull()
                val initialDownloaded = initialStats?.getOrNull(0)?.coerceAtLeast(0L) ?: 0L
                val initialTotal = initialStats?.getOrNull(1)?.takeIf { it > 0L } ?: 0L
                val initialSpeed = initialStats?.getOrNull(2)?.coerceAtLeast(0L) ?: 0L
                val initialPeers = initialStats?.getOrNull(3)?.coerceAtLeast(0L)?.toInt() ?: 0

                // 启动流状态与速率轮询
                activeStreamJob =
                    scope.launch {
                        _streamState.value =
                            TorrentStreamState.Ready(
                                streamUrl = resultUrl,
                                title = title,
                                totalBytes = initialTotal,
                                connectedPeers = initialPeers,
                            )

                        if (initialTotal > 0L && initialDownloaded >= initialTotal) {
                            _streamState.value =
                                TorrentStreamState.Buffering(
                                    progress = 1f,
                                    bufferedBytes = initialDownloaded,
                                    targetBytes = initialTotal,
                                    speedBytesPerSec = initialSpeed,
                                    connectedPeers = initialPeers,
                                )
                        }

                        while (isActive) {
                            delay(1000)
                            val stats = runCatching { nativeBridge.getStreamStats() }.getOrNull() ?: break
                            if (stats.size >= 4) {
                                val downloaded = stats[0]
                                val total = stats[1]
                                val speed = stats[2]
                                val peers = stats[3].coerceAtLeast(0L).toInt()
                                if (total > 0L) {
                                    val progress = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    _streamState.value =
                                        TorrentStreamState.Buffering(
                                            progress = progress,
                                            bufferedBytes = downloaded,
                                            targetBytes = total,
                                            speedBytesPerSec = speed,
                                            connectedPeers = peers,
                                        )
                                }
                            }
                        }
                    }

                resultUrl
            }
        }

    private suspend fun prefetchTorrentIfPresent(
        title: String,
        torrentUrl: String?,
    ): String {
        if (torrentUrl.isNullOrBlank()) {
            return ""
        }
        val existingLocalFile = File(torrentUrl)
        if (existingLocalFile.isFile && existingLocalFile.length() > 0L) {
            logger.i { "直接使用已落盘的本地种子文件: ${existingLocalFile.absolutePath}" }
            return existingLocalFile.absolutePath
        }
        val downloader = assetDownloader ?: return ""
        val torrentsDir = File(context.cacheDir, "torrents").apply { mkdirs() }
        val safeName = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val targetTorrentFile = File(torrentsDir, "$safeName.torrent")
        if (targetTorrentFile.exists() && targetTorrentFile.length() > 0L) {
            logger.i { "使用已缓存的种子文件: ${targetTorrentFile.absolutePath}" }
            return targetTorrentFile.absolutePath
        }
        val dlResult =
            runCatching {
                downloader.downloadAsset(torrentUrl, targetTorrentFile.absolutePath)
            }.getOrNull()
        return if (dlResult is DownloadState.Completed || (targetTorrentFile.exists() && targetTorrentFile.length() > 0L)) {
            logger.i { "预取种子文件成功，直接装载种子: ${targetTorrentFile.absolutePath}" }
            targetTorrentFile.absolutePath
        } else {
            logger.w { "预取种子文件失败，平滑降级至纯磁力 DHT 寻道" }
            ""
        }
    }

    override suspend fun stopStream(force: Boolean) {
        withContext(Dispatchers.IO) {
            if (!force && (downloadSessionProtected || playerSessionActive)) {
                logger.i {
                    "当前 BT 会话仍被活跃消费者持有 (downloadProtected=$downloadSessionProtected, playerActive=$playerSessionActive)，忽略非强制 stopStream"
                }
                return@withContext
            }
            downloadSessionProtected = false
            playerSessionActive = false
            releaseCurrentStreamInternal()
        }
    }

    private fun releaseCurrentStreamInternal() {
        activeStreamJob?.cancel()
        activeStreamJob = null
        activeInfoHash = null
        activeStreamUrl = null
        activeTargetFilePath = null
        activeSaveDir = null
        if (loadedVersion != null) {
            runCatching { nativeBridge.stopStream() }
        }
        _streamState.value = TorrentStreamState.Idle
    }

    override suspend fun setDebugSimulateUninstalled(simulateUninstalled: Boolean) {
        withContext(Dispatchers.IO) {
            installMutex.withLock {
                stopStream(force = true)
                debugSimulateUninstalled = simulateUninstalled
                loadedVersion = null
                loadedDynamicFile = null
                val modulesRoot = File(context.filesDir, PLUGIN_DIR_REL_PATH)
                if (modulesRoot.exists()) {
                    modulesRoot.walkBottomUp().forEach { it.setWritable(true) }
                    modulesRoot.deleteRecursively()
                }
                logger.i { "开发者调试面板切换 BT 扩展未安装模拟态: simulateUninstalled=$simulateUninstalled" }
            }
        }
    }

    @Synchronized
    private fun ensureVersionedPluginLoaded(currentVersion: String): Boolean {
        val existingDynamic = loadedDynamicFile
        if (loadedVersion == currentVersion) {
            if (existingDynamic == null && !debugSimulateUninstalled) {
                return true
            }
            if (existingDynamic != null && existingDynamic.isFile && existingDynamic.length() > 0L) {
                return true
            }
            // 动态文件在运行期被清理或切换至模拟未安装态，重置装载标记
            loadedVersion = null
            loadedDynamicFile = null
        }

        val modulesRoot = File(context.filesDir, PLUGIN_DIR_REL_PATH)
        if (modulesRoot.isDirectory) {
            cleanStalePluginDirs(modulesRoot, expectedDirName = versionDirName(currentVersion))
        }

        val dynamicSo = File(modulesRoot, "${versionDirName(currentVersion)}/$PLUGIN_SO_NAME")
        if (dynamicSo.isFile && dynamicSo.length() > 0L) {
            val loaded =
                runCatching {
                    dynamicSo.setReadOnly()
                    nativeBridge.loadFromPath(dynamicSo.absolutePath)
                }.getOrDefault(false)
            if (loaded) {
                loadedVersion = currentVersion
                loadedDynamicFile = dynamicSo
                return true
            }
        }

        // 兼容本地 Debug 变体直接内置在 APK jniLibs 中的回退加载（若开发者面板开启了模拟未安装则跳过内置库）
        if (!debugSimulateUninstalled) {
            val embeddedLoaded = runCatching { nativeBridge.loadEmbeddedLibrary() }.getOrDefault(false)
            if (embeddedLoaded) {
                loadedVersion = currentVersion
                loadedDynamicFile = null
                return true
            }
        }
        return false
    }

    private fun cleanStagingLeftovers(stagingFile: File) {
        // 续传中转文件（<staging>.tmp）必须一并清理：否则上一次中断留下的残缺字节
        // 会被 HTTP 206 续传拼接在新版本下载流之前，产出一个永远无法自愈的损坏 .so。
        listOf(stagingFile, File(stagingFile.absolutePath + DOWNLOAD_TEMP_SUFFIX)).forEach { leftover ->
            if (leftover.exists()) {
                leftover.setWritable(true)
                leftover.delete()
            }
        }
    }

    private fun cleanStalePluginDirs(
        modulesRoot: File,
        expectedDirName: String,
    ) {
        modulesRoot.listFiles()?.forEach { entry ->
            if (entry.name != expectedDirName) {
                entry.walkBottomUp().forEach { it.setWritable(true) }
                entry.deleteRecursively()
            }
        }
    }

    /**
     * 在开发者调试面板开启「模拟 BT 扩展未安装」时，从当前已安装的 Debug APK（`nativeLibraryDir` 或 `base.apk!/lib/<abi>/`）
     * 将原生库提取至 `stagingFile`，使未发版版本也能在本地走通「弹窗确认 -> 落盘只读校验 -> System.load 动态路径装载」全链路。
     */
    private suspend fun extractDebugBundledPlugin(
        abi: String,
        stagingFile: File,
    ): Boolean {
        cleanStagingLeftovers(stagingFile)
        val appInfo = runCatching { context.applicationInfo }.getOrNull() ?: return false

        val extractedSo =
            appInfo.nativeLibraryDir
                ?.takeIf { it.isNotBlank() }
                ?.let { File(it, PLUGIN_SO_NAME) }
                ?.takeIf { it.isFile && it.length() > 0L }

        if (extractedSo != null) {
            _streamState.value = TorrentStreamState.InstallingPlugin(0.35f)
            delay(150)
            extractedSo.copyTo(stagingFile, overwrite = true)
            _streamState.value = TorrentStreamState.InstallingPlugin(0.95f)
            delay(100)
            return stagingFile.isFile && stagingFile.length() > 0L
        }

        val apkPaths =
            buildList {
                appInfo.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
                appInfo.splitSourceDirs?.filter { it.isNotBlank() }?.let(::addAll)
            }

        val entryPath = "lib/$abi/$PLUGIN_SO_NAME"
        for (apkPath in apkPaths) {
            val apkFile = File(apkPath)
            if (!apkFile.isFile) continue
            val extracted =
                runCatching {
                    ZipFile(apkFile).use { zip ->
                        val entry = zip.getEntry(entryPath) ?: return@use false
                        val totalBytes = entry.size.coerceAtLeast(1L)
                        zip.getInputStream(entry).use { input ->
                            stagingFile.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var copied = 0L
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                    copied += read
                                    val ratio = (copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                                    _streamState.value = TorrentStreamState.InstallingPlugin(ratio)
                                }
                            }
                        }
                        true
                    }
                }.getOrDefault(false)
            if (extracted && stagingFile.isFile && stagingFile.length() > 0L) {
                delay(150)
                return true
            }
        }
        return false
    }

    private fun extractInfoHash(magnetUrl: String): String {
        val xt = magnetUrl.substringAfter("xt=urn:btih:", "")
        return if (xt.isNotBlank()) xt.substringBefore("&") else "unknown_hash"
    }

    private object JniNativeTorrentBridge : NativeTorrentBridge {
        override fun loadFromPath(absolutePath: String): Boolean {
            System.load(absolutePath)
            return true
        }

        override fun loadEmbeddedLibrary(): Boolean {
            System.loadLibrary("minibgm_torrent")
            return true
        }

        override fun isAvailable(): Boolean = nativeIsAvailable()

        override fun startStream(
            magnetUrl: String,
            torrentPath: String,
            saveDir: String,
            episodeHint: String,
        ): String = nativeStartStream(magnetUrl, torrentPath, saveDir, episodeHint)

        override fun stopStream() = nativeStopStream()

        override fun getStreamStats(): LongArray = nativeGetStreamStats()

        override fun getTargetFilePath(): String = nativeGetTargetFilePath()
    }

    companion object {
        internal const val PLUGIN_DIR_REL_PATH = "modules/torrent"
        internal const val PLUGIN_SO_NAME = "libminibgm_torrent.so"
        internal const val STAGING_SUFFIX = ".staging"
        internal const val DEFAULT_PLUGIN_VERSION = "0.2.8"
        internal const val RELEASE_REPO_BASE_URL =
            "https://github.com/infinitezerone/MiniBgm/releases"
        internal const val RELEASE_PLUGIN_BASE_URL =
            "$RELEASE_REPO_BASE_URL/latest/download"

        /** 可接受的插件架构集合，真源为仓库根 `native/abis.txt`（由 NativeAbiContractTest 强制一致） */
        internal val SUPPORTED_ABIS = setOf("arm64-v8a", "x86_64")

        internal fun versionDirName(version: String): String = "v${version.removePrefix("v").trim()}"

        internal fun normalizePluginVersion(rawVersionName: String?): String {
            val cleaned =
                rawVersionName
                    .orEmpty()
                    .substringBefore("-")
                    .substringBefore(".debug")
                    .trim()
            return cleaned.ifEmpty { DEFAULT_PLUGIN_VERSION }
        }

        private fun resolveAppVersion(context: Context): String {
            val rawVersion =
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull()
            return normalizePluginVersion(rawVersion)
        }

        @JvmStatic
        private external fun nativeIsAvailable(): Boolean

        @JvmStatic
        private external fun nativeStartStream(
            magnetUrl: String,
            torrentPath: String,
            saveDir: String,
            episodeHint: String,
        ): String

        @JvmStatic
        private external fun nativeStopStream()

        @JvmStatic
        private external fun nativeGetStreamStats(): LongArray

        @JvmStatic
        private external fun nativeGetTargetFilePath(): String
    }
}
