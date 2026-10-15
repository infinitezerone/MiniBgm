package com.infinitezerone.minibgm.core.data.download

import android.content.Context
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.model.DownloadState
import com.infinitezerone.minibgm.core.model.TorrentStreamState
import com.infinitezerone.minibgm.core.model.magnetInfoHash
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

    /** 引擎级错误原文；仅在 [getStreamStats] 第 5 位上报错误码时读取，健康态/无会话返回空串。 */
    fun getLastError(): String = ""

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
    private val statsPollIntervalMs: Long = DEFAULT_STATS_POLL_INTERVAL_MS,
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

    /**
     * 已激活会话绑定的分集提示。合集/BD 包内多集共用同一 infoHash，
     * 仅比对 infoHash 会把新分集错误复用到旧分集的解析结果（旧 targetFile / 旧流），静默播错集。
     */
    @Volatile
    private var activeEpisodeHint: String? = null

    @Volatile
    private var downloadSessionProtected: Boolean = false

    private val _playerSessionActive = MutableStateFlow(false)

    /**
     * 播放器消费状态的可观察镜像：下载调度据此实现「播放优先」，
     * 并在播放器释放会话时被唤醒接手排队任务（只读布尔值无法感知释放）。
     */
    override val playerSessionActive: StateFlow<Boolean> = _playerSessionActive.asStateFlow()

    override fun isDebugSimulateUninstalled(): Boolean = debugSimulateUninstalled

    override fun getActiveTargetFilePath(): String? = activeTargetFilePath

    override fun getActiveInfoHash(): String? = activeInfoHash

    override fun setDownloadSessionProtected(protected: Boolean) {
        downloadSessionProtected = protected
    }

    override fun isDownloadSessionProtected(): Boolean = downloadSessionProtected

    override fun setPlayerSessionActive(active: Boolean) {
        _playerSessionActive.value = active
    }

    override fun isPlayerSessionActive(): Boolean = _playerSessionActive.value

    override fun releasePlayerSession() {
        _playerSessionActive.value = false
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
                                        // 复用 DownloadProgress.fraction，不再就地手搓比例；
                                        // 上限压到 0.99：下载完成前不得先报 100%（后面还要真正装载 .so）。
                                        _streamState.value =
                                            TorrentStreamState.InstallingPlugin(state.progress.fraction.coerceAtMost(0.99f))
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

                val infoHash = magnetInfoHash(magnetUrl)
                val targetSaveDir =
                    saveDirPath
                        ?.takeIf { it.isNotBlank() }
                        ?.let { File(it).apply { mkdirs() }.absolutePath }
                        ?: File(context.cacheDir, "torrent_stream").apply { mkdirs() }.absolutePath

                val normalizedEpisodeHint = episodeHint?.trim().orEmpty()

                val existingUrl = activeStreamUrl
                if (infoHash.isNotBlank() &&
                    activeInfoHash.equals(infoHash, ignoreCase = true) &&
                    !existingUrl.isNullOrBlank() &&
                    activeStreamJob?.isActive == true &&
                    activeEpisodeHint == normalizedEpisodeHint &&
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
                        episodeHint = normalizedEpisodeHint,
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
                activeEpisodeHint = normalizedEpisodeHint

                logger.i { "原生流媒体服务就绪: $resultUrl (targetFile: $resolvedTargetFile)" }

                // 立即获取初次真值 stats（杜绝 100MB 伪分母污染）
                val initialStats = runCatching { nativeBridge.getStreamStats() }.getOrNull()
                val initialDownloaded = initialStats?.getOrNull(0)?.coerceAtLeast(0L) ?: 0L
                val initialTotal = initialStats?.getOrNull(1)?.takeIf { it > 0L } ?: 0L
                val initialSpeed = initialStats?.getOrNull(2)?.coerceAtLeast(0L) ?: 0L
                val initialPeers = initialStats?.getOrNull(3)?.coerceAtLeast(0L)?.toInt() ?: 0

                if (initialStats?.getOrNull(STATS_INDEX_STATE) == STATS_STATE_ENGINE_ERROR) {
                    // 起播即处于引擎错误态：直接暴露真实原因，不先发一个会被立刻推翻的 Ready
                    val reason = resolveEngineErrorReason()
                    logger.w { "BT 引擎启动后立即报告种子错误态（$reason）" }
                    _streamState.value = TorrentStreamState.Error(reason)
                    return@runCatching resultUrl
                }

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

                        var consecutiveStatFailures = 0
                        while (isActive) {
                            delay(statsPollIntervalMs)
                            val stats = runCatching { nativeBridge.getStreamStats() }.getOrNull()
                            if (stats == null || stats.size < STATS_ARRAY_SIZE) {
                                // 单次 stats 读取失败（JNI 瞬时抖动/引擎重启中）绝不能 break：
                                // 一旦退出本循环 streamState 再也不会发射，下游 monitorDirectEngineDownload
                                // 的 first{} 与死种看门狗会双双无界挂起（任务永远停在 DOWNLOADING）。
                                // 仅当连续失败超过阈值时才收敛为 Error，让下游走正常失败路径。
                                consecutiveStatFailures++
                                if (consecutiveStatFailures >= MAX_CONSECUTIVE_STAT_FAILURES) {
                                    logger.w { "连续 $consecutiveStatFailures 次读取流统计失败，收束状态流为 Error" }
                                    _streamState.value = TorrentStreamState.Error(STATS_UNAVAILABLE_MESSAGE)
                                    break
                                }
                                continue
                            }
                            if (stats[STATS_INDEX_STATE] == STATS_STATE_ENGINE_ERROR) {
                                // 引擎级致命错误（磁盘写入失败 / 初始化校验失败）：必须立刻以真实原因收敛，
                                // 绝不能静默等到死种看门狗超时——那会把"磁盘写满"伪装成"连不上做种节点"。
                                // 该状态需重新初始化才能恢复，瞬时抖动不会进入，故直接退出轮询是诚实的终态。
                                val reason = resolveEngineErrorReason()
                                logger.w { "BT 引擎报告种子错误态（$reason），收束状态流为 Error" }
                                _streamState.value = TorrentStreamState.Error(reason)
                                break
                            }
                            consecutiveStatFailures = 0
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
            if (TorrentFileCache.isUsableCachedFile(existingLocalFile)) {
                logger.i { "直接使用已落盘的本地种子文件: ${existingLocalFile.absolutePath}" }
                return existingLocalFile.absolutePath
            }
            // 落盘路径可能已被写进 DB（torrentFilePath），坏种子必须就地删除，否则重启后永久命中
            logger.w { "已落盘的本地种子文件内容非法（疑似镜像错误页），已删除并改为重新下载: ${existingLocalFile.absolutePath}" }
        }
        val downloader = assetDownloader ?: return ""
        val torrentsDir = File(context.cacheDir, "torrents").apply { mkdirs() }
        val targetTorrentFile =
            File(torrentsDir, "${TorrentFileCache.cacheKey(magnetUrl = "", torrentUrl = torrentUrl, title = title)}.torrent")
        if (TorrentFileCache.isUsableCachedFile(targetTorrentFile)) {
            logger.i { "使用已缓存的种子文件: ${targetTorrentFile.absolutePath}" }
            return targetTorrentFile.absolutePath
        }
        val dlResult =
            runCatching {
                downloader.downloadAsset(torrentUrl, targetTorrentFile.absolutePath)
            }.getOrNull()
        val downloaded = dlResult is DownloadState.Completed || (targetTorrentFile.exists() && targetTorrentFile.length() > 0L)
        return if (downloaded && TorrentFileCache.isUsableCachedFile(targetTorrentFile)) {
            logger.i { "预取种子文件成功，直接装载种子: ${targetTorrentFile.absolutePath}" }
            targetTorrentFile.absolutePath
        } else {
            logger.w { "预取种子文件失败，平滑降级至纯磁力 DHT 寻道" }
            ""
        }
    }

    override suspend fun stopStream(force: Boolean) {
        withContext(Dispatchers.IO) {
            if (!force && (downloadSessionProtected || _playerSessionActive.value)) {
                logger.i {
                    "当前 BT 会话仍被活跃消费者持有 (downloadProtected=$downloadSessionProtected, playerActive=${_playerSessionActive.value})，忽略非强制 stopStream"
                }
                return@withContext
            }
            downloadSessionProtected = false
            _playerSessionActive.value = false
            releaseCurrentStreamInternal()
        }
    }

    /** 引擎级错误的用户可见原因：优先透出原生原文，缺失时退回通用文案（严禁伪造死种结论）。 */
    private fun resolveEngineErrorReason(): String =
        runCatching { nativeBridge.getLastError() }
            .getOrNull()
            .orEmpty()
            .trim()
            .ifBlank { ENGINE_ERROR_FALLBACK_MESSAGE }

    private fun releaseCurrentStreamInternal() {
        activeStreamJob?.cancel()
        activeStreamJob = null
        activeInfoHash = null
        activeStreamUrl = null
        activeTargetFilePath = null
        activeSaveDir = null
        activeEpisodeHint = null
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

        override fun getLastError(): String = nativeGetLastError()

        override fun getTargetFilePath(): String = nativeGetTargetFilePath()
    }

    companion object {
        /** 流统计轮询间隔（测试可注入更短间隔）。 */
        internal const val DEFAULT_STATS_POLL_INTERVAL_MS = 1000L

        /** 流统计连续读取失败上限：超过才收敛为 Error，单次抖动只是跳过本轮。 */
        internal const val MAX_CONSECUTIVE_STAT_FAILURES = 5
        internal const val STATS_UNAVAILABLE_MESSAGE = "无法读取流媒体统计信息，P2P 会话可能已异常退出"

        /**
         * Rust 侧统计数组布局（唯一真源见 `crates/minibgm-torrent/src/lib.rs#stats_to_jni`）：
         * `[已下载, 总长, 速度, Peer 数, 引擎状态码]`。长度不足 [STATS_ARRAY_SIZE] 即读取失败。
         */
        internal const val STATS_ARRAY_SIZE = 5
        internal const val STATS_INDEX_STATE = 4

        /** 引擎状态码：0 = 正常（Initializing / Live / Paused），1 = librqbit 判定种子致命错误。 */
        internal const val STATS_STATE_HEALTHY = 0L
        internal const val STATS_STATE_ENGINE_ERROR = 1L

        /** 原生未给出错误原文时的兜底文案（严禁写成"连不上做种节点"伪造死种结论）。 */
        internal const val ENGINE_ERROR_FALLBACK_MESSAGE = "BT 引擎报告种子致命错误（磁盘写入或校验失败），已停止该下载"

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
        private external fun nativeGetLastError(): String

        @JvmStatic
        private external fun nativeGetTargetFilePath(): String
    }
}
