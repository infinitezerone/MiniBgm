package com.infinitezerone.minibgm.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.data.repository.TorrentRepository
import com.infinitezerone.minibgm.core.model.DownloadStorageUsage
import com.infinitezerone.minibgm.core.model.SubjectDownloadGroup
import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 离线下载界面一次性事件（由 [com.infinitezerone.minibgm.core.designsystem.component.ObserveAsEvents] 消费）
 */
sealed interface DownloadEvent {
    data object ExportSuccess : DownloadEvent

    data class ExportFailed(
        val message: String? = null,
    ) : DownloadEvent

    data class BatchExportResult(
        val successCount: Int,
        val failureCount: Int,
    ) : DownloadEvent
}

/**
 * 离线缓存与多任务下载视图模型。
 *
 * 聚合 [TorrentRepository] 的原始任务流为番剧维度 [SubjectDownloadGroup]，
 * 驱动两级下载管理界面（合集总览与分集明细），杜绝散落状态机。
 */
class DownloadViewModel(
    private val torrentRepository: TorrentRepository,
) : ViewModel() {
    private val _storageUsage = MutableStateFlow(DownloadStorageUsage())
    val storageUsage: StateFlow<DownloadStorageUsage> = _storageUsage.asStateFlow()

    private val _events = Channel<DownloadEvent>(Channel.BUFFERED)
    val events: Flow<DownloadEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            torrentRepository.verifyTaskFiles()
            refreshStorageUsage()
        }
        // 存储占用必须随下载进度刷新，但绝不能把刷新塞进 `downloadGroups` 的 map 里 launch：
        // 任务流在下载期间每秒都会推送进度，每次发射都新起一个协程去做一次全量
        // `walkTopDown` 扫盘，会无界并发堆积并反复遍历下载目录。
        // 这里收敛为单条收集协程串行执行——StateFlow 自带 conflate，扫描期间的发射被丢弃，
        // 扫完立刻取最新值，始终只有一个扫描在跑。
        viewModelScope.launch {
            torrentRepository.tasks.collect {
                _storageUsage.value = torrentRepository.getStorageUsage()
            }
        }
    }

    /**
     * 按番剧聚合的所有下载合集列表。
     */
    val downloadGroups: StateFlow<List<SubjectDownloadGroup>> =
        torrentRepository.tasks
            .map { tasks ->
                tasks
                    .groupBy { it.subjectId }
                    .map { (subjectId, groupTasks) ->
                        val first = groupTasks.first()
                        SubjectDownloadGroup(
                            subjectId = subjectId,
                            subjectName = first.subjectName.ifBlank { first.title },
                            subjectCoverUrl = first.subjectCoverUrl,
                            tasks = groupTasks,
                        )
                    }.sortedByDescending { group ->
                        group.tasks.maxOfOrNull { it.createdTimestamp } ?: 0L
                    }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun refreshStorageUsage() {
        viewModelScope.launch {
            _storageUsage.value = torrentRepository.getStorageUsage()
        }
    }

    fun verifyFiles() {
        viewModelScope.launch {
            torrentRepository.verifyTaskFiles()
            refreshStorageUsage()
        }
    }

    fun redownloadTask(taskId: String) {
        viewModelScope.launch {
            torrentRepository.resumeDownload(taskId, resetDownloadedBytes = true)
        }
    }

    fun deleteTasks(
        taskIds: List<String>,
        deleteFiles: Boolean = true,
    ) {
        viewModelScope.launch {
            taskIds.forEach { torrentRepository.cancelDownload(it, deleteFiles) }
            refreshStorageUsage()
        }
    }

    fun pauseTask(taskId: String) {
        viewModelScope.launch {
            torrentRepository.pauseDownload(taskId)
        }
    }

    fun resumeTask(taskId: String) {
        viewModelScope.launch {
            torrentRepository.resumeDownload(taskId)
        }
    }

    fun deleteTask(
        taskId: String,
        deleteFiles: Boolean = true,
    ) {
        viewModelScope.launch {
            torrentRepository.cancelDownload(taskId, deleteFiles)
        }
    }

    fun pauseAllForSubject(subjectId: Long) {
        viewModelScope.launch {
            val subjectTasks = torrentRepository.tasks.value.filter { it.subjectId == subjectId }
            val queued = subjectTasks.filter { it.status == TorrentTaskStatus.QUEUED }
            val active =
                subjectTasks.filter {
                    it.status == TorrentTaskStatus.DOWNLOADING || it.status == TorrentTaskStatus.RESOLVING
                }
            (queued + active).forEach { torrentRepository.pauseDownload(it.id) }
        }
    }

    fun resumeAllForSubject(subjectId: Long) {
        viewModelScope.launch {
            val tasks =
                torrentRepository.tasks.value.filter {
                    it.subjectId == subjectId &&
                        (it.status == TorrentTaskStatus.PAUSED || it.status == TorrentTaskStatus.ERROR)
                }
            tasks.forEach { torrentRepository.resumeDownload(it.id) }
        }
    }

    fun deleteAllForSubject(
        subjectId: Long,
        deleteFiles: Boolean = true,
    ) {
        viewModelScope.launch {
            val tasks = torrentRepository.tasks.value.filter { it.subjectId == subjectId }
            tasks.forEach { torrentRepository.cancelDownload(it.id, deleteFiles) }
        }
    }

    /**
     * 将指定已完成任务的本地文件导出至系统媒体库（相册/Movies 目录）。
     */
    fun exportToGallery(taskId: String) {
        viewModelScope.launch {
            val result = torrentRepository.exportTaskToGallery(taskId)
            if (result.isSuccess) {
                _events.send(DownloadEvent.ExportSuccess)
            } else {
                val errorMsg = result.exceptionOrNull()?.message
                _events.send(DownloadEvent.ExportFailed(errorMsg))
            }
        }
    }

    /**
     * 批量暂停指定任务。
     */
    fun pauseTasks(taskIds: List<String>) {
        if (taskIds.isEmpty()) return
        viewModelScope.launch {
            taskIds.forEach { torrentRepository.pauseDownload(it) }
        }
    }

    /**
     * 批量恢复/继续指定任务。
     */
    fun resumeTasks(taskIds: List<String>) {
        if (taskIds.isEmpty()) return
        viewModelScope.launch {
            taskIds.forEach { torrentRepository.resumeDownload(it) }
        }
    }

    /**
     * 批量将指定已完成任务的本地文件导出至系统媒体库（相册/Movies 目录）。
     */
    fun exportTasksToGallery(taskIds: List<String>) {
        if (taskIds.isEmpty()) return
        viewModelScope.launch {
            var success = 0
            var failure = 0
            for (taskId in taskIds) {
                val result = torrentRepository.exportTaskToGallery(taskId)
                if (result.isSuccess) {
                    success++
                } else {
                    failure++
                }
            }
            _events.send(
                DownloadEvent.BatchExportResult(
                    successCount = success,
                    failureCount = failure,
                ),
            )
        }
    }
}
