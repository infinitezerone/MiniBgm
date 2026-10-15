package com.infinitezerone.minibgm.core.data.repository

import android.content.Context
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.download.AssetDownloader
import com.infinitezerone.minibgm.core.data.download.DOWNLOAD_TEMP_SUFFIX
import com.infinitezerone.minibgm.core.data.download.TorrentDownloadService
import com.infinitezerone.minibgm.core.data.download.TorrentFileCache
import com.infinitezerone.minibgm.core.data.download.TorrentStreamEngine
import com.infinitezerone.minibgm.core.database.dao.TorrentTaskDao
import com.infinitezerone.minibgm.core.database.entity.TorrentTaskEntity
import com.infinitezerone.minibgm.core.model.DownloadState
import com.infinitezerone.minibgm.core.model.TorrentStreamState
import com.infinitezerone.minibgm.core.model.TorrentTask
import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
import com.infinitezerone.minibgm.core.model.magnetInfoHash
import com.infinitezerone.minibgm.core.model.torrentEpisodeKey
import com.infinitezerone.minibgm.core.network.AssetDownloadService
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID

class AndroidTorrentRepository(
    private val context: Context,
    private val assetDownloader: AssetDownloader,
    private val assetDownloadService: AssetDownloadService,
    private val streamEngine: TorrentStreamEngine,
    private val torrentTaskDao: TorrentTaskDao,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val startForegroundService: (Context, String, String) -> Unit = TorrentDownloadService::start,
    private val peerConnectTimeoutMs: Long = DEFAULT_PEER_CONNECT_TIMEOUT_MS,
) : TorrentRepository {
    private val logger = bgmLogger("Bgm/TorrentRepository")
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val restoreDeferred = CompletableDeferred<Unit>()
    private val persistenceChannel = Channel<PersistenceAction>(Channel.UNLIMITED)

    private val _tasks = MutableStateFlow<List<TorrentTask>>(emptyList())
    override val tasks: StateFlow<List<TorrentTask>> = _tasks.asStateFlow()

    override val streamState: StateFlow<TorrentStreamState> = streamEngine.streamState

    private val activeJobs = mutableMapOf<String, Job>()

    @Volatile
    private var activeRunningTaskId: String? = null

    @Volatile
    private var isPreempting = false

    private sealed interface PersistenceAction {
        data class Upsert(
            val entity: TorrentTaskEntity,
        ) : PersistenceAction

        data class Delete(
            val taskId: String,
        ) : PersistenceAction
    }

    init {
        scope.launch {
            try {
                restorePersistedTasks()
            } catch (e: Exception) {
                logger.e(e) { "恢复本地 Torrent 任务持久化失败" }
            } finally {
                restoreDeferred.complete(Unit)
            }
            for (action in persistenceChannel) {
                runCatching {
                    when (action) {
                        is PersistenceAction.Upsert -> torrentTaskDao.upsertTask(action.entity)
                        is PersistenceAction.Delete -> torrentTaskDao.deleteTask(action.taskId)
                    }
                }.onFailure { e ->
                    logger.e(e) { "持久化 Torrent 任务操作失败: $action" }
                }
            }
        }
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
        restoreDeferred.await()
        val incomingHash = magnetInfoHash(magnetUrl)
        val incomingEpisodeKey = torrentEpisodeKey(episodeId, episodeSort, episodeName)
        // 分集隔离：合集/BD 包内所有分集共用同一磁力，去重必须同时比对分集身份键。
        // 只比 magnet/infoHash 时，点第 N 集会命中第 M 集的既有任务，直接复用其
        // streamUrl（或已完成任务的 file:// 落盘路径），静默播错集且全程无提示。
        val existingTask =
            _tasks.value.firstOrNull { task ->
                task.episodeKey == incomingEpisodeKey &&
                    (
                        task.magnetUrl == magnetUrl ||
                            (
                                incomingHash.isNotBlank() &&
                                    magnetInfoHash(task.magnetUrl).equals(incomingHash, ignoreCase = true)
                            )
                    )
            }

        if (existingTask != null) {
            val completedFileMissing =
                existingTask.status == TorrentTaskStatus.COMPLETED &&
                    (existingTask.savePath.isBlank() || !File(existingTask.savePath).exists())
            if (existingTask.status == TorrentTaskStatus.PAUSED ||
                existingTask.status == TorrentTaskStatus.ERROR ||
                completedFileMissing
            ) {
                resumeDownload(existingTask.id, resetDownloadedBytes = completedFileMissing)
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
        persistenceChannel.trySend(PersistenceAction.Upsert(initialTask.toEntity()))

        // 唤起前台通知服务并投入串行下载队列（避免多任务并发争抢底层单实例 librqbit 会话互相掐断）
        startForegroundService(context, taskId, title)
        scheduleNextIfIdle()

        return taskId
    }

    override suspend fun startStreamDownload(
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
    ): Result<String> {
        try {
            val taskId =
                startDownload(
                    title = title,
                    magnetUrl = magnetUrl,
                    torrentUrl = torrentUrl,
                    totalBytes = totalBytes,
                    subjectId = subjectId,
                    subjectName = subjectName,
                    subjectCoverUrl = subjectCoverUrl,
                    episodeId = episodeId,
                    episodeSort = episodeSort,
                    episodeName = episodeName,
                )

            val currentSnapshot = _tasks.value.firstOrNull { it.id == taskId }
            currentSnapshot?.localPlayableUri()?.let { return Result.success(it) }

            // 若当前有其他任务占用了单实例 P2P 会话，通过优先权抢占启动用户正在点击“边下边播”的任务
            preemptAndPrioritizeTask(taskId)

            val playUrl =
                withTimeout(STREAM_ACQUISITION_TIMEOUT_MS) {
                    _tasks
                        .mapNotNull { list ->
                            val task =
                                list.firstOrNull { it.id == taskId }
                                    ?: throw IOException(TASK_CANCELLED_MESSAGE)
                            task.localPlayableUri()
                                ?: when (task.status) {
                                    TorrentTaskStatus.ERROR ->
                                        throw IOException(task.errorMessage ?: STREAM_ACQUISITION_FAILED_MESSAGE)
                                    TorrentTaskStatus.PAUSED -> throw IOException(TASK_PAUSED_MESSAGE)
                                    else -> null
                                }
                        }.first()
                }
            return Result.success(playUrl)
        } catch (e: TimeoutCancellationException) {
            // 业务失败必须走 Result，绝不能让它以 CancellationException 形式冒泡：
            // 那会连带取消调用方协程，UI 的 onFailure 永不执行，用户全程无任何提示。
            return Result.failure(IOException(STREAM_ACQUISITION_TIMEOUT_MESSAGE))
        } catch (e: CancellationException) {
            // 真实协程取消（用户离开页面 / 上层 Job 取消）必须原样向上传播
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /** 已完成落盘文件或已就绪的本地流地址；两者都不可用时返回 null。 */
    private fun TorrentTask.localPlayableUri(): String? =
        when {
            isCompleted && savePath.isNotBlank() && File(savePath).exists() -> "file://$savePath"
            status == TorrentTaskStatus.DOWNLOADING && streamUrl.isNotBlank() -> streamUrl
            else -> null
        }

    private suspend fun preemptAndPrioritizeTask(targetTaskId: String) {
        val preemptedJob: Job?
        val preemptedTaskId: String?
        synchronized(this) {
            val currentId = activeRunningTaskId
            if (currentId != null && currentId != targetTaskId && activeJobs[currentId]?.isActive == true) {
                preemptedTaskId = currentId
                preemptedJob = activeJobs.remove(currentId)
                activeRunningTaskId = null
                isPreempting = true
            } else {
                preemptedTaskId = null
                preemptedJob = null
            }
        }

        if (preemptedTaskId != null) {
            updateTask(preemptedTaskId) {
                if (it.status == TorrentTaskStatus.DOWNLOADING || it.status == TorrentTaskStatus.RESOLVING) {
                    it.copy(
                        status = TorrentTaskStatus.QUEUED,
                        downloadSpeedBytesPerSec = 0L,
                        connectedPeers = 0,
                        streamUrl = "",
                    )
                } else {
                    it
                }
            }
        }

        if (preemptedJob != null) {
            try {
                // 先强停底层原生会话并等待旧协程完成，期间 isPreempting 锁住 scheduleNextIfIdle 防乱序启动
                streamEngine.setDownloadSessionProtected(false)
                streamEngine.stopStream(force = true)
                preemptedJob.cancelAndJoin()
            } finally {
                synchronized(this) {
                    isPreempting = false
                }
            }
        }
        scheduleNextIfIdle(preferredTaskId = targetTaskId)
    }

    @Synchronized
    private fun scheduleNextIfIdle(preferredTaskId: String? = null) {
        if (isPreempting) return
        val currentId = activeRunningTaskId
        if (currentId != null && activeJobs[currentId]?.isActive == true) {
            return
        }
        val queuedTasks = _tasks.value.filter { it.status == TorrentTaskStatus.QUEUED }
        val nextTask =
            if (preferredTaskId != null) {
                queuedTasks.firstOrNull { it.id == preferredTaskId } ?: queuedTasks.firstOrNull()
            } else {
                queuedTasks.firstOrNull()
            } ?: return
        val nextId = nextTask.id
        activeRunningTaskId = nextId
        val job =
            scope.launch {
                try {
                    runTaskPipeline(nextId)
                } finally {
                    val shouldScheduleNext =
                        synchronized(this@AndroidTorrentRepository) {
                            val isStillCurrent = activeRunningTaskId == nextId
                            if (isStillCurrent) {
                                activeRunningTaskId = null
                            }
                            activeJobs.remove(nextId)
                            isStillCurrent
                        }
                    if (shouldScheduleNext) {
                        scheduleNextIfIdle()
                    }
                }
            }
        activeJobs[nextId] = job
    }

    private suspend fun runTaskPipeline(taskId: String) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        logger.i { "启动任务管线: $taskId - ${task.title}" }

        // 1. 进入元数据解析阶段（下载 .torrent 文件或等待磁力 DHT/Tracker 解析）
        updateTask(taskId) {
            it.copy(
                status = TorrentTaskStatus.RESOLVING,
                errorMessage = null,
            )
        }

        var localTorrentPath: String? = task.torrentFilePath
        val torrentUrl = task.torrentUrl
        // DB 中记录的路径必须与缓存命中同一把尺子校验：路径失效（文件被清理）或载荷非法
        // （镜像 HTTP 200 的 HTML 错误页）时若照旧喂给引擎，坏种子会随 DB 记录永久复现，
        // 用户只能清应用数据自救。校验失败即删除并置空，改走下面的重新获取分支。
        val stalePersistedTorrentPath =
            localTorrentPath?.takeIf { it.isNotBlank() && !TorrentFileCache.isValidPayload(File(it)) }
        if (stalePersistedTorrentPath != null) {
            logger.w { "DB 记录的种子文件已失效（缺失或载荷非法），将重新获取: $stalePersistedTorrentPath" }
            runCatching { File(stalePersistedTorrentPath).delete() }
            localTorrentPath = null
        }
        if (localTorrentPath.isNullOrBlank() && !torrentUrl.isNullOrBlank()) {
            val downloadResult = downloadTorrentFile(torrentUrl, task.title)
            if (downloadResult.isSuccess) {
                localTorrentPath = downloadResult.getOrNull()
                updateTask(taskId) { it.copy(torrentFilePath = localTorrentPath) }
            }
        }

        // 2. 启动流媒体 P2P 引擎，将目标目录直接指向持久化下载目录（使 librqbit 直接落盘真实文件，杜绝二次 HTTP 拷贝）
        val downloadDir = resolveDownloadDir()
        streamEngine.setDownloadSessionProtected(true)
        val episodeHint =
            when {
                task.episodeSort > 0f -> {
                    if (task.episodeSort % 1.0f == 0f) task.episodeSort.toInt().toString() else task.episodeSort.toString()
                }
                task.episodeName.isNotBlank() -> task.episodeName
                else -> null
            }
        val streamResult =
            streamEngine.startStream(
                title = task.title,
                magnetUrl = task.magnetUrl,
                torrentUrl = localTorrentPath ?: task.torrentUrl,
                saveDirPath = downloadDir.absolutePath,
                episodeHint = episodeHint,
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
                        connectedPeers = 0,
                        streamUrl = "",
                    )
                }
                return
            }

        val nativeTargetPath = streamEngine.getActiveTargetFilePath()?.takeIf { it.isNotBlank() }
        updateTask(taskId) {
            it.copy(
                status = TorrentTaskStatus.DOWNLOADING,
                errorMessage = null,
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
        var timedOut = false
        try {
            coroutineScope {
                val monitorScope = this
                var timeoutJob: Job? = null
                var lastBufferedBytes = _tasks.value.firstOrNull { it.id == taskId }?.downloadedBytes ?: 0L

                fun updateStallWatchdog(isActiveProgress: Boolean) {
                    if (isActiveProgress) {
                        timeoutJob?.cancel()
                        timeoutJob = null
                    } else if (peerConnectTimeoutMs > 0L && timeoutJob?.isActive != true) {
                        timeoutJob =
                            launch {
                                delay(peerConnectTimeoutMs)
                                timedOut = true
                                monitorScope.cancel(CancellationException(PEER_CONNECT_TIMEOUT_MESSAGE))
                            }
                    }
                }

                updateStallWatchdog(isActiveProgress = false)
                try {
                    streamEngine.streamState.first { state ->
                        when (state) {
                            is TorrentStreamState.Ready -> {
                                updateStallWatchdog(isActiveProgress = false)
                                if (state.totalBytes > 0L || state.connectedPeers > 0) {
                                    updateTask(taskId, persist = false) {
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
                                val bytesAdvanced = state.bufferedBytes > lastBufferedBytes
                                if (bytesAdvanced) {
                                    lastBufferedBytes = state.bufferedBytes
                                }
                                val isCompleted = state.targetBytes > 0L && state.bufferedBytes >= state.targetBytes
                                val hasActiveTransfer =
                                    state.speedBytesPerSec > 0L ||
                                        bytesAdvanced ||
                                        isCompleted
                                updateStallWatchdog(isActiveProgress = hasActiveTransfer)
                                updateTask(taskId, persist = false) {
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
                                isCompleted
                            }

                            is TorrentStreamState.Error -> {
                                throw IOException(state.message)
                            }

                            else -> false
                        }
                    }
                } finally {
                    timeoutJob?.cancel()
                }
            }

            if (targetFile.exists() && targetFile.length() > 0L) {
                val finalLength = targetFile.length()
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
            } else {
                throw IOException("下载完成但目标文件未在磁盘生成: ${targetFile.absolutePath}")
            }
        } catch (e: CancellationException) {
            if (timedOut) {
                logger.w { "任务 $taskId 连接做种节点超时（${peerConnectTimeoutMs}ms 无可用源）" }
                updateTask(taskId) {
                    it.copy(
                        status = TorrentTaskStatus.ERROR,
                        errorMessage = PEER_CONNECT_TIMEOUT_MESSAGE,
                        downloadSpeedBytesPerSec = 0L,
                        connectedPeers = 0,
                        streamUrl = "",
                    )
                }
            } else {
                logger.i { "任务 $taskId 协程被取消（已暂停或被移除）" }
                throw e
            }
        } catch (e: Exception) {
            val currentStatus = _tasks.value.firstOrNull { it.id == taskId }?.status
            if (currentStatus == TorrentTaskStatus.QUEUED || currentStatus == TorrentTaskStatus.PAUSED) {
                logger.i { "任务 $taskId 已处于 $currentStatus 态，跳过错误状态覆盖" }
                return
            }
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
        val tempFile = File("${targetFile.absolutePath}$DOWNLOAD_TEMP_SUFFIX")
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
                                updateTask(taskId, persist = false) {
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
                            updateTask(taskId, persist = false) {
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
                        updateTask(taskId, persist = false) {
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
            val currentStatus = _tasks.value.firstOrNull { it.id == taskId }?.status
            if (currentStatus == TorrentTaskStatus.QUEUED || currentStatus == TorrentTaskStatus.PAUSED) {
                logger.i { "任务 $taskId 已处于 $currentStatus 态，跳过错误状态覆盖" }
                return
            }
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
        restoreDeferred.await()
        val runningJob: Job?
        val wasRunning =
            synchronized(this) {
                val running = activeRunningTaskId == taskId
                if (running) {
                    activeRunningTaskId = null
                }
                runningJob = activeJobs.remove(taskId)
                running
            }
        if (wasRunning) {
            streamEngine.setDownloadSessionProtected(false)
            streamEngine.stopStream(force = true)
        }
        runningJob?.cancelAndJoin()
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

    override suspend fun resumeDownload(
        taskId: String,
        resetDownloadedBytes: Boolean,
    ) {
        restoreDeferred.await()
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (task.status == TorrentTaskStatus.PAUSED ||
            task.status == TorrentTaskStatus.ERROR ||
            task.status == TorrentTaskStatus.COMPLETED
        ) {
            updateTask(taskId) {
                it.copy(
                    status = TorrentTaskStatus.QUEUED,
                    errorMessage = null,
                    streamUrl = "",
                    downloadedBytes = if (resetDownloadedBytes) 0L else it.downloadedBytes,
                )
            }
            startForegroundService(context, taskId, task.title)
            scheduleNextIfIdle()
        }
    }

    override suspend fun isPluginInstalled(): Boolean = streamEngine.isPluginInstalled()

    override suspend fun installPlugin(): Result<Unit> = streamEngine.installPlugin()

    override suspend fun cancelDownload(
        taskId: String,
        deleteFiles: Boolean,
    ) {
        restoreDeferred.await()
        val runningJob: Job?
        val wasRunning =
            synchronized(this) {
                val running = activeRunningTaskId == taskId
                if (running) {
                    activeRunningTaskId = null
                }
                runningJob = activeJobs.remove(taskId)
                running
            }
        if (wasRunning) {
            streamEngine.setDownloadSessionProtected(false)
            streamEngine.stopStream(force = true)
        }
        runningJob?.cancelAndJoin()
        val task = _tasks.value.firstOrNull { it.id == taskId }
        if (deleteFiles && task != null) {
            runCatching {
                File(task.savePath).delete()
                File("${task.savePath}$DOWNLOAD_TEMP_SUFFIX").delete()
                task.torrentFilePath?.let { File(it).delete() }
            }
        }
        _tasks.update { list -> list.filterNot { it.id == taskId } }
        persistenceChannel.trySend(PersistenceAction.Delete(taskId))
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
                // 已落盘的本地种子同样要过载荷校验：坏种子就地删除交给后续重新获取，
                // 杜绝"DB 里记着路径 → 每次启动都读到同一个坏种子"的永久复现。
                if (TorrentFileCache.isValidPayload(existingLocal)) {
                    return@runCatching existingLocal.absolutePath
                }
                logger.w { "本地种子载荷非法（非 bencode/无 info 键），已删除: ${existingLocal.absolutePath}" }
                runCatching { existingLocal.delete() }
            }
            val torrentsDir = File(context.cacheDir, "torrents").apply { mkdirs() }
            // 缓存键优先 infoHash / 种子直链指纹，严禁只按标题命名（同名不同种会永久互串）
            val cacheKey = TorrentFileCache.cacheKey(magnetUrl = "", torrentUrl = torrentUrl, title = fileName)
            val targetFile = File(torrentsDir, "$cacheKey.torrent")
            if (TorrentFileCache.isUsableCachedFile(targetFile)) {
                logger.i { "复用已缓存的种子文件: ${targetFile.absolutePath}" }
                return@runCatching targetFile.absolutePath
            }

            val downloadState = assetDownloader.downloadAsset(torrentUrl, targetFile.absolutePath)
            val resolvedPath =
                when (downloadState) {
                    is DownloadState.Completed -> targetFile.absolutePath
                    is DownloadState.Failed -> error(downloadState.message)
                    else ->
                        targetFile
                            .takeIf { it.exists() && it.length() > 0L }
                            ?.absolutePath
                            ?: error("种子文件下载未完成")
                }
            if (!TorrentFileCache.isValidPayload(File(resolvedPath))) {
                // 镜像返回 HTTP 200 的 HTML 错误页时 AssetDownloader 只按字节数判定成功，
                // 必须在此拦下并删除，否则坏种子会随 DB 路径被永久复用。
                runCatching { File(resolvedPath).delete() }
                error("种子文件内容非法（疑似镜像错误页），请重试或更换其他资源")
            }
            resolvedPath
        }

    private fun updateTask(
        taskId: String,
        persist: Boolean = true,
        transform: (TorrentTask) -> TorrentTask,
    ) {
        var updatedTask: TorrentTask? = null
        _tasks.update { list ->
            list.map {
                if (it.id == taskId) {
                    val updated = transform(it)
                    updatedTask = updated
                    updated
                } else {
                    it
                }
            }
        }
        if (persist) {
            updatedTask?.let { task ->
                persistenceChannel.trySend(PersistenceAction.Upsert(task.toEntity()))
            }
        }
    }

    private fun resolveDownloadDir(): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads").apply { mkdirs() }

    private fun resolveManifestFile(): File = File(resolveDownloadDir(), "tasks_manifest.json")

    private suspend fun restorePersistedTasks() {
        val entities = torrentTaskDao.getAllTasks()
        if (entities.isNotEmpty()) {
            val normalized = entities.map { it.toDomain().normalizeOnStartup() }
            _tasks.update { inMemory ->
                val inMemoryIds = inMemory.map { it.id }.toSet()
                val restoredFiltered = normalized.filterNot { it.id in inMemoryIds }
                inMemory + restoredFiltered
            }
            val currentTasks = _tasks.value
            torrentTaskDao.upsertTasks(currentTasks.map { it.toEntity() })
            return
        }
        // 若数据库为空，尝试从存量 tasks_manifest.json 单次迁移数据入库
        val legacyTasks = loadLegacyJsonTasks()
        if (legacyTasks.isNotEmpty()) {
            val normalized = legacyTasks.map { it.normalizeOnStartup() }
            _tasks.update { inMemory ->
                val inMemoryIds = inMemory.map { it.id }.toSet()
                val restoredFiltered = normalized.filterNot { it.id in inMemoryIds }
                inMemory + restoredFiltered
            }
            val currentTasks = _tasks.value
            torrentTaskDao.upsertTasks(currentTasks.map { it.toEntity() })
            runCatching { resolveManifestFile().delete() }
            return
        }
    }

    private fun loadLegacyJsonTasks(): List<TorrentTask> =
        runCatching {
            val manifest = resolveManifestFile()
            val oldManifest = File(context.filesDir, "downloads/tasks_manifest.json")
            val target = if (manifest.isFile && manifest.length() > 0L) manifest else oldManifest
            if (!target.isFile || target.length() == 0L) return emptyList()
            BgmHttpClient.jsonConfig.decodeFromString<List<TorrentTask>>(target.readText())
        }.getOrDefault(emptyList())

    private fun TorrentTask.normalizeOnStartup(): TorrentTask =
        when (status) {
            TorrentTaskStatus.DOWNLOADING,
            TorrentTaskStatus.RESOLVING,
            TorrentTaskStatus.QUEUED,
            ->
                copy(
                    status = TorrentTaskStatus.PAUSED,
                    downloadSpeedBytesPerSec = 0L,
                    connectedPeers = 0,
                    streamUrl = "",
                )

            TorrentTaskStatus.COMPLETED -> {
                val file = File(savePath)
                if (savePath.isNotBlank() && file.exists() && file.length() > 0L) {
                    copy(downloadSpeedBytesPerSec = 0L, connectedPeers = 0, streamUrl = "")
                } else {
                    copy(
                        status = TorrentTaskStatus.PAUSED,
                        downloadedBytes = 0L,
                        downloadSpeedBytesPerSec = 0L,
                        connectedPeers = 0,
                        streamUrl = "",
                    )
                }
            }

            else -> copy(downloadSpeedBytesPerSec = 0L, connectedPeers = 0, streamUrl = "")
        }

    private fun TorrentTask.toEntity(): TorrentTaskEntity =
        TorrentTaskEntity(
            id = id,
            title = title,
            magnetUrl = magnetUrl,
            torrentUrl = torrentUrl,
            torrentFilePath = torrentFilePath,
            status = status.name,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            downloadSpeedBytesPerSec = downloadSpeedBytesPerSec,
            uploadSpeedBytesPerSec = uploadSpeedBytesPerSec,
            connectedPeers = connectedPeers,
            connectedSeeds = connectedSeeds,
            savePath = savePath,
            streamUrl = streamUrl,
            errorMessage = errorMessage,
            createdTimestamp = createdTimestamp,
            completionTimestamp = completionTimestamp,
            subjectId = subjectId,
            subjectName = subjectName,
            subjectCoverUrl = subjectCoverUrl,
            episodeId = episodeId,
            episodeSort = episodeSort,
            episodeName = episodeName,
        )

    private fun TorrentTaskEntity.toDomain(): TorrentTask =
        TorrentTask(
            id = id,
            title = title,
            magnetUrl = magnetUrl,
            torrentUrl = torrentUrl,
            torrentFilePath = torrentFilePath,
            status = runCatching { TorrentTaskStatus.valueOf(status) }.getOrDefault(TorrentTaskStatus.PAUSED),
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            downloadSpeedBytesPerSec = downloadSpeedBytesPerSec,
            uploadSpeedBytesPerSec = uploadSpeedBytesPerSec,
            connectedPeers = connectedPeers,
            connectedSeeds = connectedSeeds,
            savePath = savePath,
            streamUrl = streamUrl,
            errorMessage = errorMessage,
            createdTimestamp = createdTimestamp,
            completionTimestamp = completionTimestamp,
            subjectId = subjectId,
            subjectName = subjectName,
            subjectCoverUrl = subjectCoverUrl,
            episodeId = episodeId,
            episodeSort = episodeSort,
            episodeName = episodeName,
        )

    override suspend fun getStorageUsage(): com.infinitezerone.minibgm.core.model.DownloadStorageUsage =
        withContext(Dispatchers.IO) {
            val downloadDir = resolveDownloadDir()
            var totalDownloadBytes = 0L
            runCatching {
                downloadDir.walkTopDown().filter { it.isFile }.forEach {
                    totalDownloadBytes += it.length()
                }
            }
            val availableBytes =
                runCatching {
                    android.os.StatFs(downloadDir.absolutePath).availableBytes
                }.getOrDefault(0L)

            com.infinitezerone.minibgm.core.model.DownloadStorageUsage(
                downloadSizeBytes = totalDownloadBytes,
                availableBytes = availableBytes,
            )
        }

    override suspend fun verifyTaskFiles() {
        withContext(Dispatchers.IO) {
            restoreDeferred.await()
            _tasks.update { list ->
                list.map { task ->
                    if (task.status == TorrentTaskStatus.COMPLETED) {
                        val fileExists = task.savePath.isNotBlank() && File(task.savePath).exists()
                        task.copy(isFileMissing = !fileExists)
                    } else {
                        task
                    }
                }
            }
        }
    }

    companion object {
        const val DEFAULT_PEER_CONNECT_TIMEOUT_MS = 45_000L
        const val PEER_CONNECT_TIMEOUT_MESSAGE = "连接做种节点超时（暂无可用源），请点击重试或更换其他资源"
        const val STREAM_ACQUISITION_TIMEOUT_MS = 60_000L
        const val STREAM_ACQUISITION_TIMEOUT_MESSAGE = "起播等待超时，未能取得可播放地址（可能是死种），请点击重试或更换其他资源"
        const val STREAM_ACQUISITION_FAILED_MESSAGE = "起播失败"
        const val TASK_PAUSED_MESSAGE = "下载任务已暂停"
        const val TASK_CANCELLED_MESSAGE = "下载任务已取消"
    }
}
