package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.data.repository.TorrentRepository
import com.infinitezerone.minibgm.core.model.TorrentStreamState
import com.infinitezerone.minibgm.core.model.TorrentTask
import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeTorrentRepository : TorrentRepository {
    val mutableTasks = MutableStateFlow<List<TorrentTask>>(emptyList())
    override val tasks: StateFlow<List<TorrentTask>> = mutableTasks.asStateFlow()

    val mutableStreamState = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Idle)
    override val streamState: StateFlow<TorrentStreamState> = mutableStreamState.asStateFlow()

    var startDownloadCallCount: Int = 0
        private set
    var pauseDownloadCallCount: Int = 0
        private set
    var resumeDownloadCallCount: Int = 0
        private set
    var cancelDownloadCallCount: Int = 0
        private set
    var downloadTorrentFileCallCount: Int = 0
        private set

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
        startDownloadCallCount++
        val taskId = "fake-task-${mutableTasks.value.size + 1}"
        val task =
            TorrentTask(
                id = taskId,
                title = title,
                magnetUrl = magnetUrl,
                torrentUrl = torrentUrl,
                status = TorrentTaskStatus.DOWNLOADING,
                totalBytes = totalBytes,
                downloadedBytes = 0L,
                savePath = "/fake/download/$title.mp4",
                subjectId = subjectId,
                subjectName = subjectName,
                subjectCoverUrl = subjectCoverUrl,
                episodeId = episodeId,
                episodeSort = episodeSort,
                episodeName = episodeName,
            )
        mutableTasks.update { it + task }
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
        val existing = mutableTasks.value.firstOrNull { it.magnetUrl == magnetUrl }
        if (existing != null && existing.isCompleted && existing.savePath.isNotBlank()) {
            return Result.success("file://${existing.savePath}")
        }
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
        val streamUrl = "http://127.0.0.1:18080/torrents/0/stream/0"
        mutableTasks.update { list ->
            list.map { if (it.id == taskId) it.copy(streamUrl = streamUrl) else it }
        }
        return Result.success(streamUrl)
    }

    override suspend fun pauseDownload(taskId: String) {
        pauseDownloadCallCount++
        mutableTasks.update { list ->
            list.map { if (it.id == taskId) it.copy(status = TorrentTaskStatus.PAUSED) else it }
        }
    }

    override suspend fun resumeDownload(
        taskId: String,
        resetDownloadedBytes: Boolean,
    ) {
        resumeDownloadCallCount++
        mutableTasks.update { list ->
            list.map {
                if (it.id == taskId) {
                    it.copy(
                        status = TorrentTaskStatus.DOWNLOADING,
                        downloadedBytes = if (resetDownloadedBytes) 0L else it.downloadedBytes,
                    )
                } else {
                    it
                }
            }
        }
    }

    override suspend fun cancelDownload(
        taskId: String,
        deleteFiles: Boolean,
    ) {
        cancelDownloadCallCount++
        mutableTasks.update { list -> list.filterNot { it.id == taskId } }
    }

    override suspend fun downloadTorrentFile(
        torrentUrl: String,
        fileName: String,
    ): Result<String> {
        downloadTorrentFileCallCount++
        return Result.success("/fake/cache/torrents/$fileName.torrent")
    }

    var isPluginInstalledResult: Boolean = true
    var installPluginCallCount: Int = 0
        private set

    override suspend fun isPluginInstalled(): Boolean = isPluginInstalledResult

    override suspend fun installPlugin(): Result<Unit> {
        installPluginCallCount++
        isPluginInstalledResult = true
        return Result.success(Unit)
    }

    var fakeStorageUsage =
        com.infinitezerone.minibgm.core.model.DownloadStorageUsage(
            downloadSizeBytes = 1024L * 1024L * 500L,
            availableBytes = 1024L * 1024L * 1024L * 20L,
        )

    override suspend fun getStorageUsage(): com.infinitezerone.minibgm.core.model.DownloadStorageUsage = fakeStorageUsage

    override suspend fun verifyTaskFiles() {
        // no-op in fake
    }
}
