package com.infinitezerone.minibgm.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.data.repository.TorrentRepository
import com.infinitezerone.minibgm.core.model.DownloadStorageUsage
import com.infinitezerone.minibgm.core.model.SubjectDownloadGroup
import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    init {
        viewModelScope.launch {
            torrentRepository.verifyTaskFiles()
            refreshStorageUsage()
        }
    }

    /**
     * 按番剧聚合的所有下载合集列表。
     */
    val downloadGroups: StateFlow<List<SubjectDownloadGroup>> =
        torrentRepository.tasks
            .map { tasks ->
                viewModelScope.launch { refreshStorageUsage() }
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
}
