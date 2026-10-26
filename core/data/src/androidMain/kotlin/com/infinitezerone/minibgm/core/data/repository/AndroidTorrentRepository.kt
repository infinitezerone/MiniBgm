package com.infinitezerone.minibgm.core.data.repository

import android.content.Context
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.download.AssetDownloader
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
import java.util.UUID

class AndroidTorrentRepository(
    private val context: Context,
    private val assetDownloader: AssetDownloader,
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
            // 播放优先联动：排队任务在播放器消费本地流期间必须礼让（scheduleNextIfIdle 的守卫），
            // 播放器释放会话（退出播放器 / 切到在线线路）后必须被唤醒接手——只读一次布尔值
            // 感知不到"释放"，被礼让的任务会永久停在 QUEUED。
            streamEngine.playerSessionActive.collect { active ->
                if (!active) scheduleNextIfIdle()
            }
        }
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
        onTaskQueued: (String) -> Unit,
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

            // 任务身份必须在等待可播放地址之前交出去：UI 的「停止起播」要靠它暂停底层任务，
            // 而不是只把自己这条等待协程取消掉（那会留下占着队列的幽灵任务）。
            runCatching { onTaskQueued(taskId) }

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
            // 引擎已判定种子致命错误（磁盘写满 / 校验失败）时严禁交出地址：那个地址背后的会话已经死了，
            // 交出去用户只会拿到一个泛化的播放错误；正确路径是让等待方看到任务 ERROR 与真实原因。
            status == TorrentTaskStatus.DOWNLOADING &&
                streamUrl.isNotBlank() &&
                streamEngine.streamState.value !is TorrentStreamState.Error -> streamUrl
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
        // 只有用户显式点播（边下边播）才允许接管播放中的会话：此刻播放器本来就准备切到新地址。
        scheduleNextIfIdle(preferredTaskId = targetTaskId, allowPlayerTakeover = true)
    }

    @Synchronized
    private fun scheduleNextIfIdle(
        preferredTaskId: String? = null,
        allowPlayerTakeover: Boolean = false,
    ) {
        if (isPreempting) return
        // 播放优先：前台播放器正在消费本地流时，后台下载严禁起新任务。单实例 librqbit 会话下
        // 任何新任务都会 release 掉正在播放的会话，把用户正在看的画面（"看第 5 集时下第 6 集"）
        // 直接掐断。此处保持 QUEUED，等播放器释放会话后由 playerSessionActive 收集器唤醒接手；
        // 用户显式点播（allowPlayerTakeover）与前台点播抢占不受此限。
        if (!allowPlayerTakeover && streamEngine.isPlayerSessionActive()) return
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

        // 2. 准备有效种子载荷（校验缓存与 DB 路径有效性）
        val localTorrentPath = resolveValidTorrentPath(task, taskId)

        // 3. 启动流媒体 P2P 引擎，将目标目录直接指向持久化下载目录
        val streamUrl = startEngineForTask(task, localTorrentPath, taskId) ?: return

        val nativeTargetPath = streamEngine.getActiveTargetFilePath()?.takeIf { it.isNotBlank() }
        if (nativeTargetPath == null) {
            // 磁盘写入完全由 librqbit 在 saveDir 下直接执行；引擎未暴露目标文件路径说明会话异常。
            // 快速失败收敛为 ERROR，不再回退到「回环 HTTP 流式拷贝落盘」的旧路径。
            logger.e { "任务 $taskId 原生引擎未暴露落盘路径，无法继续下载" }
            markTaskFailed(taskId, "BT 引擎未返回目标文件路径")
            withContext(NonCancellable) {
                streamEngine.setDownloadSessionProtected(false)
                streamEngine.stopStream(force = false)
            }
            return
        }
        updateTask(taskId) {
            it.copy(
                status = TorrentTaskStatus.DOWNLOADING,
                errorMessage = null,
                streamUrl = streamUrl,
                savePath = nativeTargetPath,
            )
        }

        // 4. 直接监听 librqbit 分片进度（分片落盘全部由原生引擎完成）
        monitorDirectEngineDownload(taskId, File(nativeTargetPath))
    }

    private suspend fun resolveValidTorrentPath(
        task: TorrentTask,
        taskId: String,
    ): String? {
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
        return localTorrentPath
    }

    private suspend fun startEngineForTask(
        task: TorrentTask,
        localTorrentPath: String?,
        taskId: String,
    ): String? {
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

        return streamResult.getOrElse { error ->
            streamEngine.setDownloadSessionProtected(false)
            logger.e(error) { "启动流引擎失败: $taskId" }
            markTaskFailed(taskId, error.message ?: "启动 P2P 引擎失败")
            null
        }
    }

    private suspend fun monitorDirectEngineDownload(
        taskId: String,
        targetFile: File,
    ) {
        var timedOut = false
        try {
            coroutineScope {
                val watchdog =
                    StallWatchdog(peerConnectTimeoutMs, this) {
                        timedOut = true
                        cancel(CancellationException(PEER_CONNECT_TIMEOUT_MESSAGE))
                    }
                var lastBufferedBytes = _tasks.value.firstOrNull { it.id == taskId }?.downloadedBytes ?: 0L

                watchdog.update(hasActiveProgress = false)
                try {
                    streamEngine.streamState.first { state ->
                        when (state) {
                            is TorrentStreamState.Ready -> {
                                watchdog.update(hasActiveProgress = false)
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
                                watchdog.update(hasActiveProgress = hasActiveTransfer)
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
                    watchdog.cancel()
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
                markTaskFailed(taskId, PEER_CONNECT_TIMEOUT_MESSAGE)
            } else {
                logger.i { "任务 $taskId 协程被取消（已暂停或被移除）" }
                throw e
            }
        } catch (e: Exception) {
            logger.e(e) { "任务 $taskId 原生下载异常: ${e.message}" }
            markTaskFailed(taskId, e.message ?: "P2P 下载失败")
        } finally {
            withContext(NonCancellable) {
                streamEngine.setDownloadSessionProtected(false)
                streamEngine.stopStream(force = false)
            }
        }
    }

    private fun markTaskFailed(
        taskId: String,
        errorMessage: String,
    ) {
        val currentStatus = _tasks.value.firstOrNull { it.id == taskId }?.status
        if (currentStatus == TorrentTaskStatus.QUEUED || currentStatus == TorrentTaskStatus.PAUSED) {
            logger.i { "任务 $taskId 已处于 $currentStatus 态，跳过错误状态覆盖" }
            return
        }
        updateTask(taskId) {
            it.copy(
                status = TorrentTaskStatus.ERROR,
                errorMessage = errorMessage,
                downloadSpeedBytesPerSec = 0L,
                connectedPeers = 0,
                streamUrl = "",
            )
        }
    }

    /**
     * 死种看门狗探针：在指定毫秒数内若无活动进度（字节前进 / 传输速率 > 0 / 完成），触发超时回调。
     */
    private class StallWatchdog(
        private val timeoutMs: Long,
        private val scope: CoroutineScope,
        private val onTimeout: () -> Unit,
    ) {
        private var job: Job? = null

        fun update(hasActiveProgress: Boolean) {
            if (hasActiveProgress) {
                job?.cancel()
                job = null
            } else if (timeoutMs > 0L && job?.isActive != true) {
                job =
                    scope.launch {
                        delay(timeoutMs)
                        onTimeout()
                    }
            }
        }

        fun cancel() {
            job?.cancel()
            job = null
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
        try {
            val existingLocal = File(torrentUrl)
            if (existingLocal.isFile && existingLocal.length() > 0L) {
                // 已落盘的本地种子同样要过载荷校验：坏种子就地删除交给后续重新获取，
                // 杜绝"DB 里记着路径 → 每次启动都读到同一个坏种子"的永久复现。
                if (TorrentFileCache.isValidPayload(existingLocal)) {
                    Result.success(existingLocal.absolutePath)
                } else {
                    logger.w { "本地种子载荷非法（非 bencode/无 info 键），已删除: ${existingLocal.absolutePath}" }
                    runCatching { existingLocal.delete() }
                    downloadAndCacheTorrent(torrentUrl, fileName)
                }
            } else {
                downloadAndCacheTorrent(torrentUrl, fileName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun downloadAndCacheTorrent(
        torrentUrl: String,
        fileName: String,
    ): Result<String> {
        val torrentsDir = File(context.cacheDir, "torrents").apply { mkdirs() }
        // 缓存键优先 infoHash / 种子直链指纹，严禁只按标题命名（同名不同种会永久互串）
        val cacheKey = TorrentFileCache.cacheKey(magnetUrl = "", torrentUrl = torrentUrl, title = fileName)
        val targetFile = File(torrentsDir, "$cacheKey.torrent")
        if (TorrentFileCache.isUsableCachedFile(targetFile)) {
            logger.i { "复用已缓存的种子文件: ${targetFile.absolutePath}" }
            return Result.success(targetFile.absolutePath)
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
        return Result.success(resolvedPath)
    }

    private fun updateTask(
        taskId: String,
        persist: Boolean = true,
        transform: (TorrentTask) -> TorrentTask,
    ) {
        var updatedTask: TorrentTask? = null
        var changed = false
        _tasks.update { list ->
            val index = list.indexOfFirst { it.id == taskId }
            if (index < 0) return@update list
            val current = list[index]
            val updated = transform(current)
            if (updated == current) return@update list
            changed = true
            updatedTask = updated
            list.toMutableList().apply { set(index, updated) }
        }
        if (persist && changed) {
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

    override suspend fun exportTaskToGallery(taskId: String): Result<String> =
        withContext(Dispatchers.IO) {
            restoreDeferred.await()
            val task =
                _tasks.value.firstOrNull { it.id == taskId }
                    ?: return@withContext Result.failure(IllegalArgumentException("任务不存在: $taskId"))

            if (task.status != TorrentTaskStatus.COMPLETED || task.savePath.isBlank()) {
                return@withContext Result.failure(IllegalStateException("任务未完成或文件路径为空"))
            }

            val sourceFile = File(task.savePath)
            if (!sourceFile.exists() || sourceFile.length() <= 0L) {
                return@withContext Result.failure(IOException("目标文件不存在或为空: ${task.savePath}"))
            }

            val fileName = sourceFile.name
            val extension = sourceFile.extension.lowercase()
            val mimeType =
                when (extension) {
                    "mkv" -> "video/x-matroska"
                    "mp4" -> "video/mp4"
                    "webm" -> "video/webm"
                    "ts", "m2ts" -> "video/mp2t"
                    "avi" -> "video/x-msvideo"
                    else -> "video/*"
                }

            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val values =
                        android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, fileName)
                            put(android.provider.MediaStore.Video.Media.MIME_TYPE, mimeType)
                            put(
                                android.provider.MediaStore.Video.Media.RELATIVE_PATH,
                                "${android.os.Environment.DIRECTORY_MOVIES}/MiniBgm",
                            )
                            put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
                        }

                    val resolver = context.contentResolver
                    val collection = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    val itemUri =
                        resolver.insert(collection, values)
                            ?: throw IOException("向 MediaStore 插入记录失败")

                    try {
                        resolver.openOutputStream(itemUri)?.use { outputStream ->
                            sourceFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        } ?: throw IOException("无法打开 MediaStore 输出流")

                        values.clear()
                        values.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                        resolver.update(itemUri, values, null, null)
                        logger.i { "任务 $taskId 已成功导出至系统相册 (MediaStore): $itemUri" }
                        itemUri.toString()
                    } catch (e: Exception) {
                        resolver.delete(itemUri, null, null)
                        throw e
                    }
                } else {
                    val moviesDir =
                        File(
                            android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_MOVIES,
                            ),
                            "MiniBgm",
                        ).apply { mkdirs() }
                    val targetFile = File(moviesDir, fileName)
                    sourceFile.copyTo(targetFile, overwrite = true)
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        arrayOf(mimeType),
                        null,
                    )
                    logger.i { "任务 $taskId 已成功导出至系统媒体目录: ${targetFile.absolutePath}" }
                    targetFile.absolutePath
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
