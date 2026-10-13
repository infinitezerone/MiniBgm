package com.infinitezerone.minibgm.core.data.repository

import android.content.Context
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.download.AssetDownloader
import com.infinitezerone.minibgm.core.data.download.TorrentDownloadService
import com.infinitezerone.minibgm.core.data.download.TorrentStreamEngine
import com.infinitezerone.minibgm.core.model.DownloadState
import com.infinitezerone.minibgm.core.model.TorrentStreamState
import com.infinitezerone.minibgm.core.model.TorrentTask
import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
import com.infinitezerone.minibgm.core.network.AssetDownloadService
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID

class AndroidTorrentRepository(
    private val context: Context,
    private val assetDownloader: AssetDownloader,
    private val assetDownloadService: AssetDownloadService,
    private val streamEngine: TorrentStreamEngine,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val startForegroundService: (Context, String, String) -> Unit = TorrentDownloadService::start,
) : TorrentRepository {
    private val logger = bgmLogger("Bgm/TorrentRepository")
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _tasks = MutableStateFlow<List<TorrentTask>>(emptyList())
    override val tasks: StateFlow<List<TorrentTask>> = _tasks.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    @Volatile
    private var activeRunningTaskId: String? = null

    init {
        restorePersistedTasks()
    }

    override suspend fun startDownload(
        title: String,
        magnetUrl: String,
        torrentUrl: String?,
        totalBytes: Long,
        subjectId: Long,
        subjectName: String,
        subjectCoverUrl: String,
        episodeId: Long,
        episodeSort: Float,
        episodeName: String,
    ): String {
        val incomingHash = extractInfoHash(magnetUrl)
        val existingTask =
            _tasks.value.firstOrNull { task ->
                task.magnetUrl == magnetUrl ||
                    (incomingHash.isNotBlank() && extractInfoHash(task.magnetUrl).equals(incomingHash, ignoreCase = true))
            }

        if (existingTask != null) {
            if (existingTask.status == TorrentTaskStatus.PAUSED || existingTask.status == TorrentTaskStatus.ERROR) {
                resumeDownload(existingTask.id)
            }
            return existingTask.id
        }

        val taskId = UUID.randomUUID().toString().take(8)
        val downloadDir = resolveDownloadDir()
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val fallbackVideoFile = File(downloadDir, "$safeTitle.mp4")

        val initialTask =
            TorrentTask(
                id = taskId,
                title = title,
                magnetUrl = magnetUrl,
                torrentUrl = torrentUrl,
                status = TorrentTaskStatus.QUEUED,
                totalBytes = totalBytes,
                savePath = fallbackVideoFile.absolutePath,
                createdTimestamp = System.currentTimeMillis(),
                subjectId = subjectId,
                subjectName = subjectName,
                subjectCoverUrl = subjectCoverUrl,
                episodeId = episodeId,
                episodeSort = episodeSort,
                episodeName = episodeName,
            )

        _tasks.update { it + initialTask }
        persistTasksSnapshot()

        // 唤起前台通知服务并投入串行下载队列（避免多任务并发争抢底层单实例 librqbit 会话互相掐断）
        startForegroundService(context, taskId, title)
        scheduleNextIfIdle()

        return taskId
    }

    @Synchronized
    private fun scheduleNextIfIdle() {
        val currentId = activeRunningTaskId
        if (currentId != null && activeJobs[currentId]?.isActive == true) {
            return
        }
        val nextTask = _tasks.value.firstOrNull { it.status == TorrentTaskStatus.QUEUED } ?: return
        val nextId = nextTask.id
        activeRunningTaskId = nextId
        val job =
            scope.launch {
                try {
                    runTaskPipeline(nextId)
                } finally {
                    synchronized(this@AndroidTorrentRepository) {
                        if (activeRunningTaskId == nextId) {
                            activeRunningTaskId = null
                        }
                        activeJobs.remove(nextId)
                    }
                    scheduleNextIfIdle()
                }
            }
        activeJobs[nextId] = job
    }

    private suspend fun runTaskPipeline(taskId: String) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        logger.i { "启动任务管线: $taskId - ${task.title}" }

        // 1. 如果有种子直链，优先在后台下载种子文件
        var localTorrentPath: String? = task.torrentFilePath
        val torrentUrl = task.torrentUrl
        if (localTorrentPath.isNullOrBlank() && !torrentUrl.isNullOrBlank()) {
            updateTask(taskId) { it.copy(status = TorrentTaskStatus.RESOLVING) }
            val downloadResult = downloadTorrentFile(torrentUrl, task.title)
            if (downloadResult.isSuccess) {
                localTorrentPath = downloadResult.getOrNull()
                updateTask(taskId) { it.copy(torrentFilePath = localTorrentPath) }
            }
        }

        // 2. 启动流媒体 P2P 引擎，将目标目录直接指向持久化下载目录（使 librqbit 直接落盘真实文件，杜绝二次 HTTP 拷贝）
        val downloadDir = resolveDownloadDir()
        updateTask(taskId) {
            it.copy(
                status = TorrentTaskStatus.DOWNLOADING,
                errorMessage = null,
            )
        }

        streamEngine.setDownloadSessionProtected(true)
        val streamResult =
            streamEngine.startStream(
                title = task.title,
                magnetUrl = task.magnetUrl,
                torrentUrl = localTorrentPath ?: task.torrentUrl,
                saveDirPath = downloadDir.absolutePath,
            )

        val streamUrl =
            streamResult.getOrElse { error ->
                streamEngine.setDownloadSessionProtected(false)
                logger.e(error) { "启动流引擎失败: $taskId" }
                updateTask(taskId) {
                    it.copy(
                        status = TorrentTaskStatus.ERROR,
                        errorMessage = error.message ?: "启动 P2P 引擎失败",
                        downloadSpeedBytesPerSec = 0L,
                        streamUrl = "",
                    )
                }
                return
            }

        val nativeTargetPath = streamEngine.getActiveTargetFilePath()?.takeIf { it.isNotBlank() }
        updateTask(taskId) {
            it.copy(
                streamUrl = streamUrl,
                savePath = nativeTargetPath ?: it.savePath,
            )
        }

        // 3. 若原生引擎已暴露真实落盘路径（保留 .mkv/.mp4 等原始扩展名），直接监听 librqbit 分片进度直至 100%，
        //    彻底消除通过 127.0.0.1 再拉一遍 HTTP 顺序流的双倍磁盘写入与分片 0 死等；
        //    仅在单测 Fake 引擎未返回真实路径时回退至流式落盘。
        if (nativeTargetPath != null) {
            monitorDirectEngineDownload(taskId, File(nativeTargetPath))
        } else {
            downloadStreamToFile(taskId, streamUrl, File(task.savePath))
        }
    }

    private suspend fun monitorDirectEngineDownload(
        taskId: String,
        targetFile: File,
    ) {
        try {
            streamEngine.streamState.first { state ->
                when (state) {
                    is TorrentStreamState.Ready -> {
                        if (state.totalBytes > 0L || state.connectedPeers > 0) {
                            updateTask(taskId) {
                                if (it.status == TorrentTaskStatus.DOWNLOADING) {
                                    it.copy(
                                        totalBytes = if (state.totalBytes > 0L) state.totalBytes else it.totalBytes,
                                        connectedPeers = state.connectedPeers,
                                    )
                                } else {
                                    it
                                }
                            }
                        }
                        false
                    }

                    is TorrentStreamState.Buffering -> {
                        updateTask(taskId) {
                            if (it.status == TorrentTaskStatus.DOWNLOADING) {
                                it.copy(
                                    downloadedBytes = maxOf(it.downloadedBytes, state.bufferedBytes),
                                    totalBytes = if (state.targetBytes > 0L) state.targetBytes else it.totalBytes,
                                    downloadSpeedBytesPerSec = state.speedBytesPerSec,
                                    connectedPeers = state.connectedPeers,
                                )
                            } else {
                                it
                            }
                        }
                        state.targetBytes > 0L && state.bufferedBytes >= state.targetBytes
                    }

                    is TorrentStreamState.Error -> {
                        throw IOException(state.message)
                    }

                    else -> false
                }
            }

            val currentTask = _tasks.value.firstOrNull { it.id == taskId }
            val finalLength =
                targetFile.takeIf { it.exists() }?.length()?.takeIf { it > 0L }
                    ?: currentTask?.totalBytes?.takeIf { it > 0L }
                    ?: currentTask?.downloadedBytes
                    ?: 0L

            updateTask(taskId) {
                it.copy(
                    status = TorrentTaskStatus.COMPLETED,
                    downloadedBytes = finalLength,
                    totalBytes = finalLength,
                    downloadSpeedBytesPerSec = 0L,
                    connectedPeers = 0,
                    streamUrl = "",
                    savePath = targetFile.absolutePath,
                    completionTimestamp = System.currentTimeMillis(),
                )
            }
            logger.i { "任务 $taskId 原生引擎直接落盘完成: ${targetFile.absolutePath} ($finalLength 字节)" }
        } catch (e: CancellationException) {
            logger.i { "任务 $taskId 协程被取消（已暂停或被移除）" }
            throw e
        } catch (e: Exception) {
            logger.e(e) { "任务 $taskId 原生下载异常: ${e.message}" }
            updateTask(taskId) {
                it.copy(
                    status = TorrentTaskStatus.ERROR,
                    errorMessage = e.message ?: "P2P 下载失败",
                    downloadSpeedBytesPerSec = 0L,
                    connectedPeers = 0,
                    streamUrl = "",
                )
            }
        } finally {
            withContext(NonCancellable) {
                streamEngine.setDownloadSessionProtected(false)
                streamEngine.stopStream(force = false)
            }
        }
    }

    private suspend fun downloadStreamToFile(
        taskId: String,
        streamUrl: String,
        targetFile: File,
    ) {
        val tempFile = File("${targetFile.absolutePath}.tmp")
        tempFile.parentFile?.mkdirs()
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        var lastEmitTime = 0L
        var lastSpeedCalcTime = System.currentTimeMillis()
        var bytesSinceLastSpeed = 0L
        var currentSpeed = 0L

        // 同步监听 P2P 引擎原生状态：在顺序流等待分片 0 期间也能实时反馈 P2P 真实总大小、已缓冲字节与速率
        val statsObserverJob =
            scope.launch {
                streamEngine.streamState.collect { state ->
                    when (state) {
                        is TorrentStreamState.Ready -> {
                            if (state.totalBytes > 0L || state.connectedPeers > 0) {
                                updateTask(taskId) {
                                    if (it.status == TorrentTaskStatus.DOWNLOADING) {
                                        it.copy(
                                            totalBytes = if (state.totalBytes > 0L) state.totalBytes else it.totalBytes,
                                            connectedPeers = state.connectedPeers,
                                        )
                                    } else {
                                        it
                                    }
                                }
                            }
                        }

                        is TorrentStreamState.Buffering -> {
                            updateTask(taskId) {
                                if (it.status == TorrentTaskStatus.DOWNLOADING) {
                                    it.copy(
                                        downloadedBytes = maxOf(it.downloadedBytes, state.bufferedBytes),
                                        totalBytes = if (state.targetBytes > 0L) state.targetBytes else it.totalBytes,
                                        downloadSpeedBytesPerSec =
                                            if (state.speedBytesPerSec > 0L) {
                                                state.speedBytesPerSec
                                            } else {
                                                it.downloadSpeedBytesPerSec
                                            },
                                        connectedPeers = state.connectedPeers,
                                    )
                                } else {
                                    it
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            }

        try {
            val raf = RandomAccessFile(tempFile, "rw")
            var currentDownloaded = existingBytes
            var finalTotalBytes = -1L
            var isFirstChunk = true

            try {
                assetDownloadService.streamAsset(
                    url = streamUrl,
                    rangeStart = existingBytes,
                ) { chunk, length, totalBytes, isPartial ->
                    if (isFirstChunk) {
                        isFirstChunk = false
                        if (!isPartial) {
                            raf.setLength(0L)
                            raf.seek(0L)
                            currentDownloaded = 0L
                        } else {
                            raf.seek(existingBytes)
                            currentDownloaded = existingBytes
                        }
                    }

                    raf.write(chunk, 0, length)
                    currentDownloaded += length
                    finalTotalBytes = totalBytes
                    bytesSinceLastSpeed += length

                    val now = System.currentTimeMillis()
                    if (now - lastSpeedCalcTime >= 1000) {
                        val elapsed = now - lastSpeedCalcTime
                        currentSpeed = if (elapsed > 0) (bytesSinceLastSpeed * 1000) / elapsed else 0L
                        lastSpeedCalcTime = now
                        bytesSinceLastSpeed = 0L
                    }

                    if (now - lastEmitTime >= 500 || (finalTotalBytes > 0L && currentDownloaded >= finalTotalBytes)) {
                        lastEmitTime = now
                        updateTask(taskId) {
                            it.copy(
                                downloadedBytes = maxOf(it.downloadedBytes, currentDownloaded),
                                totalBytes = if (finalTotalBytes > 0L) finalTotalBytes else it.totalBytes,
                                downloadSpeedBytesPerSec = if (currentSpeed > 0L) currentSpeed else it.downloadSpeedBytesPerSec,
                                status = TorrentTaskStatus.DOWNLOADING,
                            )
                        }
                    }
                }
            } finally {
                raf.close()
            }

            // 流读取完成，校验完整性并原子替换落盘
            val writtenLength = if (tempFile.exists()) tempFile.length() else 0L
            if (writtenLength <= 0L || (finalTotalBytes > 0L && writtenLength < finalTotalBytes)) {
                throw IOException("P2P 下载流未完成传输 (已写入 $writtenLength / 总 $finalTotalBytes 字节)")
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            val finalLength = targetFile.length()
            updateTask(taskId) {
                it.copy(
                    status = TorrentTaskStatus.COMPLETED,
                    downloadedBytes = finalLength,
                    totalBytes = finalLength,
                    downloadSpeedBytesPerSec = 0L,
                    connectedPeers = 0,
                    streamUrl = "",
                    completionTimestamp = System.currentTimeMillis(),
                )
            }
            logger.i { "任务 $taskId 下载完成并落盘: ${targetFile.absolutePath} ($finalLength 字节)" }
        } catch (e: CancellationException) {
            logger.i { "任务 $taskId 协程被取消（已暂停或被移除）" }
            throw e
        } catch (e: Exception) {
            logger.e(e) { "任务 $taskId 下载异常: ${e.message}" }
            updateTask(taskId) {
                it.copy(
                    status = TorrentTaskStatus.ERROR,
                    errorMessage = e.message ?: "下载流读取失败",
                    downloadSpeedBytesPerSec = 0L,
                    connectedPeers = 0,
                    streamUrl = "",
                )
            }
        } finally {
            statsObserverJob.cancel()
            withContext(NonCancellable) {
                streamEngine.setDownloadSessionProtected(false)
                streamEngine.stopStream(force = false)
            }
        }
    }

    override suspend fun pauseDownload(taskId: String) {
        val wasRunning =
            synchronized(this) {
                val running = activeRunningTaskId == taskId
                if (running) {
                    activeRunningTaskId = null
                }
                activeJobs.remove(taskId)?.cancel()
                running
            }
        if (wasRunning) {
            streamEngine.setDownloadSessionProtected(false)
            streamEngine.stopStream(force = true)
        }
        updateTask(taskId) {
            it.copy(
                status = TorrentTaskStatus.PAUSED,
                downloadSpeedBytesPerSec = 0L,
                connectedPeers = 0,
                streamUrl = "",
            )
        }
        if (wasRunning) {
            scheduleNextIfIdle()
        }
    }

    override suspend fun resumeDownload(taskId: String) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (task.status == TorrentTaskStatus.PAUSED || task.status == TorrentTaskStatus.ERROR) {
            updateTask(taskId) {
                it.copy(
                    status = TorrentTaskStatus.QUEUED,
                    errorMessage = null,
                    streamUrl = "",
                )
            }
            startForegroundService(context, taskId, task.title)
            scheduleNextIfIdle()
        }
    }

    override suspend fun cancelDownload(
        taskId: String,
        deleteFiles: Boolean,
    ) {
        val wasRunning =
            synchronized(this) {
                val running = activeRunningTaskId == taskId
                if (running) {
                    activeRunningTaskId = null
                }
                activeJobs.remove(taskId)?.cancel()
                running
            }
        if (wasRunning) {
            streamEngine.setDownloadSessionProtected(false)
            streamEngine.stopStream(force = true)
        }
        val task = _tasks.value.firstOrNull { it.id == taskId }
        if (deleteFiles && task != null) {
            runCatching {
                File(task.savePath).delete()
                File("${task.savePath}.tmp").delete()
                task.torrentFilePath?.let { File(it).delete() }
            }
        }
        _tasks.update { list -> list.filterNot { it.id == taskId } }
        persistTasksSnapshot()
        if (wasRunning) {
            scheduleNextIfIdle()
        }
    }

    override suspend fun downloadTorrentFile(
        torrentUrl: String,
        fileName: String,
    ): Result<String> =
        runCatching {
            val existingLocal = File(torrentUrl)
            if (existingLocal.isFile && existingLocal.length() > 0L) {
                return@runCatching existingLocal.absolutePath
            }
            val torrentsDir = File(context.cacheDir, "torrents").apply { mkdirs() }
            val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val targetFile = File(torrentsDir, "$safeName.torrent")
            if (targetFile.isFile && targetFile.length() > 0L) {
                logger.i { "复用已缓存的种子文件: ${targetFile.absolutePath}" }
                return@runCatching targetFile.absolutePath
            }

            val downloadState = assetDownloader.downloadAsset(torrentUrl, targetFile.absolutePath)
            when (downloadState) {
                is DownloadState.Completed -> targetFile.absolutePath
                is DownloadState.Failed -> error(downloadState.message)
                else -> {
                    if (targetFile.exists() && targetFile.length() > 0L) {
                        targetFile.absolutePath
                    } else {
                        error("种子文件下载未完成")
                    }
                }
            }
        }

    private fun updateTask(
        taskId: String,
        transform: (TorrentTask) -> TorrentTask,
    ) {
        _tasks.update { list ->
            list.map { if (it.id == taskId) transform(it) else it }
        }
        persistTasksSnapshot()
    }

    private fun resolveDownloadDir(): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads").apply { mkdirs() }

    private fun resolveManifestFile(): File = File(context.filesDir, "downloads/tasks_manifest.json").apply { parentFile?.mkdirs() }

    private fun restorePersistedTasks() {
        runCatching {
            val manifest = resolveManifestFile()
            if (!manifest.isFile || manifest.length() == 0L) return@runCatching
            val decoded = BgmHttpClient.jsonConfig.decodeFromString<List<TorrentTask>>(manifest.readText())
            val normalized =
                decoded.map { task ->
                    when (task.status) {
                        TorrentTaskStatus.DOWNLOADING,
                        TorrentTaskStatus.RESOLVING,
                        TorrentTaskStatus.QUEUED,
                        ->
                            task.copy(
                                status = TorrentTaskStatus.PAUSED,
                                downloadSpeedBytesPerSec = 0L,
                                connectedPeers = 0,
                                streamUrl = "",
                            )

                        TorrentTaskStatus.COMPLETED -> {
                            val file = File(task.savePath)
                            if (task.savePath.isNotBlank() && file.exists() && file.length() > 0L) {
                                task.copy(downloadSpeedBytesPerSec = 0L, connectedPeers = 0, streamUrl = "")
                            } else {
                                task.copy(
                                    status = TorrentTaskStatus.PAUSED,
                                    downloadedBytes = 0L,
                                    downloadSpeedBytesPerSec = 0L,
                                    connectedPeers = 0,
                                    streamUrl = "",
                                )
                            }
                        }

                        else -> task.copy(downloadSpeedBytesPerSec = 0L, connectedPeers = 0, streamUrl = "")
                    }
                }
            _tasks.value = normalized
        }
    }

    private fun persistTasksSnapshot() {
        runCatching {
            val manifest = resolveManifestFile()
            val payload = BgmHttpClient.jsonConfig.encodeToString(_tasks.value)
            manifest.writeText(payload)
        }
    }

    private fun extractInfoHash(magnetUrl: String): String {
        val xt = magnetUrl.substringAfter("xt=urn:btih:", "")
        return if (xt.isNotBlank()) xt.substringBefore("&") else ""
    }
}
