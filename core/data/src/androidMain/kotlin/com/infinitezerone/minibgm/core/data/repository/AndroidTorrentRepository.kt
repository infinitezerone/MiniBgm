package com.infinitezerone.minibgm.core.data.repository

import android.content.Context
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.download.AssetDownloader
import com.infinitezerone.minibgm.core.data.download.TorrentDownloadService
import com.infinitezerone.minibgm.core.data.download.TorrentStreamEngine
import com.infinitezerone.minibgm.core.model.DownloadState
import com.infinitezerone.minibgm.core.model.TorrentEngineState
import com.infinitezerone.minibgm.core.model.TorrentTask
import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
import com.infinitezerone.minibgm.core.network.AssetDownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

class AndroidTorrentRepository(
    private val context: Context,
    private val assetDownloader: AssetDownloader,
    private val assetDownloadService: AssetDownloadService,
    private val streamEngine: TorrentStreamEngine,
) : TorrentRepository {
    private val logger = bgmLogger("Bgm/TorrentRepository")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow<List<TorrentTask>>(emptyList())
    override val tasks: StateFlow<List<TorrentTask>> = _tasks.asStateFlow()

    private val _engineState = MutableStateFlow<TorrentEngineState>(TorrentEngineState.Ready)
    override val engineState: StateFlow<TorrentEngineState> = _engineState.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

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
        val taskId = UUID.randomUUID().toString().take(8)
        val downloadDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads").apply { mkdirs() }
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val targetVideoFile = File(downloadDir, "$safeTitle.mp4")

        val initialTask =
            TorrentTask(
                id = taskId,
                title = title,
                magnetUrl = magnetUrl,
                torrentUrl = torrentUrl,
                status = TorrentTaskStatus.QUEUED,
                totalBytes = totalBytes,
                savePath = targetVideoFile.absolutePath,
                createdTimestamp = System.currentTimeMillis(),
                subjectId = subjectId,
                subjectName = subjectName,
                subjectCoverUrl = subjectCoverUrl,
                episodeId = episodeId,
                episodeSort = episodeSort,
                episodeName = episodeName,
            )

        _tasks.update { it + initialTask }

        // 唤起前台通知服务
        TorrentDownloadService.start(context, taskId, title)

        // 启动后台调度作业
        val job =
            scope.launch {
                runTaskPipeline(initialTask)
            }
        activeJobs[taskId] = job

        return taskId
    }

    private suspend fun runTaskPipeline(task: TorrentTask) {
        val taskId = task.id
        logger.i { "启动任务管线: $taskId - ${task.title}" }

        // 1. 如果有种子直链，优先在后台下载种子文件
        var localTorrentPath: String? = null
        val torrentUrl = task.torrentUrl
        if (!torrentUrl.isNullOrBlank()) {
            updateTask(taskId) { it.copy(status = TorrentTaskStatus.RESOLVING) }
            val downloadResult = downloadTorrentFile(torrentUrl, task.title)
            if (downloadResult.isSuccess) {
                localTorrentPath = downloadResult.getOrNull()
                updateTask(taskId) { it.copy(torrentFilePath = localTorrentPath) }
            }
        }

        // 2. 检查本地目标文件是否已存在
        val targetFile = File(task.savePath)
        if (targetFile.exists() && targetFile.length() > 0L) {
            val length = targetFile.length()
            updateTask(taskId) {
                it.copy(
                    status = TorrentTaskStatus.COMPLETED,
                    downloadedBytes = length,
                    totalBytes = if (it.totalBytes <= 0L) length else it.totalBytes,
                    completionTimestamp = System.currentTimeMillis(),
                    downloadSpeedBytesPerSec = 0L,
                )
            }
            logger.i { "任务 $taskId 目标文件已存在，标记完成: ${targetFile.absolutePath}" }
            return
        }

        // 3. 启动流媒体 P2P 引擎，获取本地流地址
        updateTask(taskId) {
            it.copy(
                status = TorrentTaskStatus.DOWNLOADING,
                errorMessage = null,
            )
        }

        val streamResult =
            streamEngine.startStream(
                title = task.title,
                magnetUrl = task.magnetUrl,
                torrentUrl = localTorrentPath ?: task.torrentUrl,
            )

        val streamUrl =
            streamResult.getOrElse { error ->
                logger.e(error) { "启动流引擎失败: $taskId" }
                updateTask(taskId) {
                    it.copy(
                        status = TorrentTaskStatus.ERROR,
                        errorMessage = error.message ?: "启动 P2P 引擎失败",
                        downloadSpeedBytesPerSec = 0L,
                    )
                }
                return
            }

        // 4. 执行本地流式落盘写文件
        downloadStreamToFile(taskId, streamUrl, targetFile)
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

                    if (now - lastEmitTime >= 500) {
                        lastEmitTime = now
                        updateTask(taskId) {
                            it.copy(
                                downloadedBytes = currentDownloaded,
                                totalBytes = if (finalTotalBytes > 0L) finalTotalBytes else it.totalBytes,
                                downloadSpeedBytesPerSec = currentSpeed,
                                status = TorrentTaskStatus.DOWNLOADING,
                            )
                        }
                    }
                }
            } finally {
                raf.close()
            }

            // 流读取完成，重命名临时文件并标记完成
            if (tempFile.exists() && (finalTotalBytes <= 0L || tempFile.length() >= finalTotalBytes)) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                val finalLength = targetFile.length()
                updateTask(taskId) {
                    it.copy(
                        status = TorrentTaskStatus.COMPLETED,
                        downloadedBytes = finalLength,
                        totalBytes = finalLength,
                        downloadSpeedBytesPerSec = 0L,
                        completionTimestamp = System.currentTimeMillis(),
                    )
                }
                logger.i { "任务 $taskId 下载完成并落盘: ${targetFile.absolutePath} ($finalLength 字节)" }
            }
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
                )
            }
        } finally {
            streamEngine.stopStream()
        }
    }

    override suspend fun pauseDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        streamEngine.stopStream()
        updateTask(taskId) {
            it.copy(
                status = TorrentTaskStatus.PAUSED,
                downloadSpeedBytesPerSec = 0L,
            )
        }
    }

    override suspend fun resumeDownload(taskId: String) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (task.status == TorrentTaskStatus.PAUSED) {
            updateTask(taskId) { it.copy(status = TorrentTaskStatus.DOWNLOADING) }
            TorrentDownloadService.start(context, taskId, task.title)
            val job = scope.launch { runTaskPipeline(task) }
            activeJobs[taskId] = job
        }
    }

    override suspend fun cancelDownload(
        taskId: String,
        deleteFiles: Boolean,
    ) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        streamEngine.stopStream()
        val task = _tasks.value.firstOrNull { it.id == taskId }
        if (deleteFiles && task != null) {
            runCatching {
                File(task.savePath).delete()
                File("${task.savePath}.tmp").delete()
                task.torrentFilePath?.let { File(it).delete() }
            }
        }
        _tasks.update { list -> list.filterNot { it.id == taskId } }
    }

    override suspend fun downloadTorrentFile(
        torrentUrl: String,
        fileName: String,
    ): Result<String> =
        runCatching {
            val torrentsDir = File(context.cacheDir, "torrents").apply { mkdirs() }
            val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val targetFile = File(torrentsDir, "$safeName.torrent")

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

    override suspend fun checkOrInstallEngine(): TorrentEngineState {
        val modulesDir = File(context.filesDir, "modules/torrent")
        val isEnginePresent = modulesDir.exists() && (modulesDir.listFiles()?.isNotEmpty() == true)

        return if (isEnginePresent) {
            _engineState.value = TorrentEngineState.Ready
            TorrentEngineState.Ready
        } else {
            // 模块就绪或支持按需初始化
            _engineState.value = TorrentEngineState.Ready
            TorrentEngineState.Ready
        }
    }

    private fun updateTask(
        taskId: String,
        transform: (TorrentTask) -> TorrentTask,
    ) {
        _tasks.update { list ->
            list.map { if (it.id == taskId) transform(it) else it }
        }
    }
}
