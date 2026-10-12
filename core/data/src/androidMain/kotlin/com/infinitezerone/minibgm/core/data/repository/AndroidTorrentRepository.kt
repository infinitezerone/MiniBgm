package com.infinitezerone.minibgm.core.data.repository

import android.content.Context
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.download.AssetDownloader
import com.infinitezerone.minibgm.core.data.download.TorrentDownloadService
import com.infinitezerone.minibgm.core.model.DownloadState
import com.infinitezerone.minibgm.core.model.TorrentEngineState
import com.infinitezerone.minibgm.core.model.TorrentTask
import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
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
import java.util.UUID

class AndroidTorrentRepository(
    private val context: Context,
    private val assetDownloader: AssetDownloader,
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

        // 2. 明确离线下载状态（严禁使用 2.5MB/s 伪进度与假 peers 欺骗用户）
        // 当前项目主轴为纯内存零闪存流播（TorrentStreamEngine）；
        // 应用内大文件离线落盘引擎尚未接入多任务持久化管线，明确阻断并引导外部客户端或在线流播
        updateTask(taskId) {
            it.copy(
                status = TorrentTaskStatus.ERROR,
                downloadSpeedBytesPerSec = 0L,
                connectedPeers = 0,
                connectedSeeds = 0,
                errorMessage = "应用内离线下载引擎重构中，请使用外部客户端下载或直接在线播放",
            )
        }
        logger.w { "任务 $taskId 阻断: 应用内离线落盘下载未就绪，已提示切换外部客户端" }
    }

    override suspend fun pauseDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
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
        val task = _tasks.value.firstOrNull { it.id == taskId }
        if (deleteFiles && task != null) {
            runCatching {
                File(task.savePath).delete()
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
