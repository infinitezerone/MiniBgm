package com.infinitezerone.minibgm.feature.user

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.ConfirmDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.ObserveAsEvents
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.designsystem.theme.LocalFeatureCapabilities
import com.infinitezerone.minibgm.core.model.TorrentTask
import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.launchMediaWithChooser
import com.infinitezerone.minibgm.feature.user.components.VideoTaskDetailBottomSheet
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/**
 * 第二级：番剧分集下载管理与离线播放列表界面。
 *
 * 聚合指定番剧的所有分集任务，支持单集离线播放、暂停/恢复、删除，
 * 以及整部番剧的总控（全部暂停/全部恢复/一键清空）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDownloadDetailScreen(
    subjectId: Long,
    subjectName: String,
    onBackClick: () -> Unit,
    onPlayEpisode: ((PlayerRoute) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = koinViewModel(),
) {
    val downloadGroups by viewModel.downloadGroups.collectAsStateWithLifecycle()
    val group = downloadGroups.firstOrNull { it.subjectId == subjectId }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DownloadEvent.ExportSuccess -> {
                snackbarHostState.showSnackbar(context.getString(R.string.feature_user_downloads_export_success))
            }
            is DownloadEvent.ExportFailed -> {
                val message =
                    event.message?.let {
                        context.getString(R.string.feature_user_downloads_export_failed, it)
                    } ?: context.getString(R.string.feature_user_downloads_export_failed_generic)
                snackbarHostState.showSnackbar(message)
            }
            is DownloadEvent.BatchExportResult -> {
                val message =
                    when {
                        event.failureCount == 0 ->
                            context.getString(R.string.feature_user_downloads_batch_export_success, event.successCount)
                        event.successCount > 0 ->
                            context.getString(
                                R.string.feature_user_downloads_batch_export_partial,
                                event.successCount,
                                event.failureCount,
                            )
                        else ->
                            context.getString(R.string.feature_user_downloads_batch_export_failed)
                    }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    SubjectDownloadDetailContent(
        subjectId = subjectId,
        subjectName = subjectName,
        group = group,
        onBackClick = onBackClick,
        onPlayEpisode = onPlayEpisode,
        onPauseTask = viewModel::pauseTask,
        onResumeTask = viewModel::resumeTask,
        onDeleteTask = viewModel::deleteTask,
        onRedownloadTask = viewModel::redownloadTask,
        onBatchDeleteTasks = viewModel::deleteTasks,
        onExportToGallery = viewModel::exportToGallery,
        onBatchExportToGallery = viewModel::exportTasksToGallery,
        onBatchPauseTasks = viewModel::pauseTasks,
        onBatchResumeTasks = viewModel::resumeTasks,
        onPauseAll = { viewModel.pauseAllForSubject(subjectId) },
        onResumeAll = { viewModel.resumeAllForSubject(subjectId) },
        onDeleteAll = { viewModel.deleteAllForSubject(subjectId) },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubjectDownloadDetailContent(
    subjectId: Long,
    subjectName: String,
    group: com.infinitezerone.minibgm.core.model.SubjectDownloadGroup?,
    onBackClick: () -> Unit,
    onPlayEpisode: ((PlayerRoute) -> Unit)? = null,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onRedownloadTask: (String) -> Unit = {},
    onBatchDeleteTasks: (List<String>) -> Unit = {},
    onExportToGallery: (String) -> Unit = {},
    onBatchExportToGallery: (List<String>) -> Unit = {},
    onBatchPauseTasks: (List<String>) -> Unit = {},
    onBatchResumeTasks: (List<String>) -> Unit = {},
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onDeleteAll: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
) {
    val tasks = group?.sortedTasks ?: emptyList()
    val overlayHostState = rememberOverlayHostState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var isBatchMode by remember { mutableStateOf(false) }
    var selectedTaskIds by remember { mutableStateOf(emptySet<String>()) }
    var detailTaskForSheet by remember { mutableStateOf<TorrentTask?>(null) }

    val hasActive =
        tasks.any {
            it.status == TorrentTaskStatus.DOWNLOADING ||
                it.status == TorrentTaskStatus.RESOLVING ||
                it.status == TorrentTaskStatus.QUEUED
        }
    val hasResumable =
        tasks.any {
            it.status == TorrentTaskStatus.PAUSED ||
                it.status == TorrentTaskStatus.ERROR
        }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            BgmTopAppBar(
                title = {
                    Text(
                        text =
                            if (isBatchMode) {
                                stringResource(R.string.feature_user_downloads_selected_count, selectedTaskIds.size)
                            } else {
                                subjectName.ifBlank { group?.subjectName.orEmpty() }
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isBatchMode) {
                                isBatchMode = false
                                selectedTaskIds = emptySet()
                            } else {
                                onBackClick()
                            }
                        },
                    ) {
                        val navigationActionRes =
                            if (isBatchMode) {
                                DesignSystemR.string.core_designsystem_action_cancel
                            } else {
                                DesignSystemR.string.core_designsystem_action_back
                            }
                        Icon(
                            imageVector = if (isBatchMode) BgmIcons.Close else BgmIcons.ArrowBack,
                            contentDescription = stringResource(navigationActionRes),
                        )
                    }
                },
                actions = {
                    if (isBatchMode) {
                        val allSelected = tasks.isNotEmpty() && selectedTaskIds.size == tasks.size
                        TextButton(
                            onClick = {
                                selectedTaskIds = if (allSelected) emptySet() else tasks.map { it.id }.toSet()
                            },
                        ) {
                            Text(
                                stringResource(
                                    if (allSelected) {
                                        R.string.feature_user_downloads_deselect_all
                                    } else {
                                        R.string.feature_user_downloads_select_all
                                    },
                                ),
                            )
                        }
                    } else {
                        if (hasActive) {
                            IconButton(onClick = onPauseAll) {
                                Icon(
                                    imageVector = BgmIcons.Pause,
                                    contentDescription = stringResource(R.string.feature_user_downloads_all_pause),
                                )
                            }
                        } else if (hasResumable) {
                            IconButton(onClick = onResumeAll) {
                                Icon(
                                    imageVector = BgmIcons.PlayArrow,
                                    contentDescription = stringResource(R.string.feature_user_downloads_all_resume),
                                )
                            }
                        }

                        if (tasks.isNotEmpty()) {
                            IconButton(onClick = { isBatchMode = true }) {
                                Icon(
                                    imageVector = BgmIcons.Check,
                                    contentDescription = stringResource(R.string.feature_user_downloads_batch_mode),
                                )
                            }

                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val confirmed =
                                            overlayHostState.await(
                                                ConfirmDialogAction(
                                                    title = context.getString(R.string.feature_user_downloads_delete_all_subject_title),
                                                    message =
                                                        context.getString(
                                                            R.string.feature_user_downloads_delete_all_subject_message,
                                                            subjectName.ifBlank { group?.subjectName.orEmpty() },
                                                        ),
                                                    confirmText = context.getString(R.string.feature_user_action_delete),
                                                    dismissText = context.getString(DesignSystemR.string.core_designsystem_action_cancel),
                                                    isDestructive = true,
                                                ),
                                            )
                                        if (confirmed) {
                                            onDeleteAll()
                                            onBackClick()
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = BgmIcons.DeleteBorder,
                                    contentDescription = stringResource(R.string.feature_user_action_delete),
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isBatchMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                val selectedCompletedTaskIds =
                    remember(selectedTaskIds, tasks) {
                        tasks
                            .filter { selectedTaskIds.contains(it.id) && it.status == TorrentTaskStatus.COMPLETED && !it.isFileMissing }
                            .map { it.id }
                    }
                val selectedActiveTaskIds =
                    remember(selectedTaskIds, tasks) {
                        tasks
                            .filter {
                                selectedTaskIds.contains(it.id) &&
                                    (
                                        it.status == TorrentTaskStatus.DOWNLOADING ||
                                            it.status == TorrentTaskStatus.RESOLVING ||
                                            it.status == TorrentTaskStatus.QUEUED
                                    )
                            }.map { it.id }
                    }
                val selectedPausedTaskIds =
                    remember(selectedTaskIds, tasks) {
                        tasks
                            .filter {
                                selectedTaskIds.contains(it.id) &&
                                    (it.status == TorrentTaskStatus.PAUSED || it.status == TorrentTaskStatus.ERROR)
                            }.map { it.id }
                    }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 存相册
                        FilledTonalButton(
                            onClick = {
                                if (selectedCompletedTaskIds.isNotEmpty()) {
                                    onBatchExportToGallery(selectedCompletedTaskIds)
                                }
                            },
                            enabled = selectedCompletedTaskIds.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text =
                                    if (selectedCompletedTaskIds.isNotEmpty()) {
                                        "${stringResource(R.string.feature_user_downloads_batch_export)} (${selectedCompletedTaskIds.size})"
                                    } else {
                                        stringResource(R.string.feature_user_downloads_batch_export)
                                    },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }

                        // 暂停 / 恢复
                        if (selectedActiveTaskIds.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = { onBatchPauseTasks(selectedActiveTaskIds) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            ) {
                                Icon(
                                    imageVector = BgmIcons.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.feature_user_downloads_batch_pause),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            }
                        } else if (selectedPausedTaskIds.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = { onBatchResumeTasks(selectedPausedTaskIds) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            ) {
                                Icon(
                                    imageVector = BgmIcons.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.feature_user_downloads_batch_resume),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            }
                        }

                        // 删除
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val count = selectedTaskIds.size
                                    val confirmed =
                                        overlayHostState.await(
                                            ConfirmDialogAction(
                                                title = context.getString(R.string.feature_user_downloads_batch_delete_confirm_title),
                                                message =
                                                    context.getString(
                                                        R.string.feature_user_downloads_batch_delete_confirm_message,
                                                        count,
                                                    ),
                                                confirmText = context.getString(R.string.feature_user_action_delete),
                                                dismissText = context.getString(DesignSystemR.string.core_designsystem_action_cancel),
                                                isDestructive = true,
                                            ),
                                        )
                                    if (confirmed) {
                                        onBatchDeleteTasks(selectedTaskIds.toList())
                                        selectedTaskIds = emptySet()
                                        isBatchMode = false
                                    }
                                }
                            },
                            enabled = selectedTaskIds.isNotEmpty(),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.feature_user_downloads_batch_delete),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.feature_user_downloads_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (group != null) {
                    item(key = "subject_header_banner") {
                        SubjectHeaderCard(group = group)
                    }
                }

                items(
                    items = tasks,
                    key = { it.id },
                ) { task ->
                    EpisodeDownloadCard(
                        task = task,
                        subjectCoverUrl = group?.subjectCoverUrl.orEmpty(),
                        isBatchMode = isBatchMode,
                        isSelected = selectedTaskIds.contains(task.id),
                        onToggleSelect = {
                            selectedTaskIds =
                                if (selectedTaskIds.contains(task.id)) {
                                    selectedTaskIds - task.id
                                } else {
                                    selectedTaskIds + task.id
                                }
                        },
                        onPlay =
                            onPlayEpisode?.let { play ->
                                {
                                    val playUrl =
                                        when {
                                            task.isCompleted &&
                                                task.savePath.isNotBlank() &&
                                                !task.isFileMissing -> "file://${task.savePath}"
                                            task.status == TorrentTaskStatus.DOWNLOADING && task.streamUrl.isNotBlank() -> task.streamUrl
                                            task.savePath.isNotBlank() && !task.isFileMissing -> "file://${task.savePath}"
                                            else -> ""
                                        }
                                    if (playUrl.isNotBlank()) {
                                        val route =
                                            PlayerRoute(
                                                subjectId = task.subjectId.takeIf { it > 0L } ?: subjectId,
                                                episodeId = task.episodeId,
                                                streamUrl = playUrl,
                                                episodeName = task.episodeName.ifBlank { task.title },
                                                subjectName = task.subjectName.ifBlank { subjectName },
                                                episodeSort = task.episodeSort,
                                                coverUrl = task.subjectCoverUrl,
                                            )
                                        play(route)
                                    }
                                }
                            },
                        onPause = { onPauseTask(task.id) },
                        onResume = { onResumeTask(task.id) },
                        onDelete = {
                            coroutineScope.launch {
                                val confirmed =
                                    overlayHostState.await(
                                        ConfirmDialogAction(
                                            title = context.getString(R.string.feature_user_downloads_delete_task_title),
                                            message =
                                                context.getString(
                                                    R.string.feature_user_downloads_delete_task_message,
                                                    task.episodeName.ifBlank { task.title },
                                                ),
                                            confirmText = context.getString(R.string.feature_user_action_delete),
                                            dismissText = context.getString(DesignSystemR.string.core_designsystem_action_cancel),
                                            isDestructive = true,
                                        ),
                                    )
                                if (confirmed) {
                                    onDeleteTask(task.id)
                                }
                            }
                        },
                        onRedownload = { onRedownloadTask(task.id) },
                        onExportToGallery = { onExportToGallery(task.id) },
                        onShowDetail = { detailTaskForSheet = task },
                    )
                }
            }
        }
    }

    BgmOverlayHost(hostState = overlayHostState) {
        confirmDialog()
    }

    if (detailTaskForSheet != null) {
        val currentTask = detailTaskForSheet!!
        VideoTaskDetailBottomSheet(
            task = currentTask,
            subjectCoverUrl = group?.subjectCoverUrl.orEmpty(),
            onDismiss = { detailTaskForSheet = null },
            onPlayExternal = {
                val mediaPathOrUrl =
                    if (currentTask.isCompleted && currentTask.savePath.isNotBlank()) {
                        currentTask.savePath
                    } else {
                        currentTask.streamUrl
                    }
                context.launchMediaWithChooser(
                    mediaUriOrPath = mediaPathOrUrl,
                    title = currentTask.episodeName.ifBlank { currentTask.title },
                    chooserTitle = context.getString(R.string.feature_user_downloads_open_with_external),
                )
            },
            onExportToGallery = {
                onExportToGallery(currentTask.id)
            },
            onRedownload = {
                onRedownloadTask(currentTask.id)
            },
            onDelete = {
                coroutineScope.launch {
                    val confirmed =
                        overlayHostState.await(
                            ConfirmDialogAction(
                                title = context.getString(R.string.feature_user_downloads_delete_task_title),
                                message =
                                    context.getString(
                                        R.string.feature_user_downloads_delete_task_message,
                                        currentTask.episodeName.ifBlank { currentTask.title },
                                    ),
                                confirmText = context.getString(R.string.feature_user_action_delete),
                                dismissText = context.getString(DesignSystemR.string.core_designsystem_action_cancel),
                                isDestructive = true,
                            ),
                        )
                    if (confirmed) {
                        onDeleteTask(currentTask.id)
                    }
                }
            },
        )
    }
}

@Composable
private fun SubjectHeaderCard(
    group: com.infinitezerone.minibgm.core.model.SubjectDownloadGroup,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                url = group.subjectCoverUrl,
                contentDescription = group.subjectName,
                modifier = Modifier.width(52.dp),
                cornerRadius = 8.dp,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        stringResource(
                            R.string.feature_user_downloads_detail_summary,
                            group.completedCount,
                            group.totalTasks,
                            group.formattedTotalSize,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun EpisodeDownloadCard(
    task: TorrentTask,
    subjectCoverUrl: String = "",
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onPlay: (() -> Unit)? = null,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onRedownload: () -> Unit = {},
    onExportToGallery: (() -> Unit)? = null,
    onShowDetail: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isCompleted = task.status == TorrentTaskStatus.COMPLETED
    val isError = task.status == TorrentTaskStatus.ERROR
    val canStreamWhileDownloading =
        task.status == TorrentTaskStatus.DOWNLOADING &&
            task.streamUrl.isNotBlank() &&
            (task.downloadedBytes > 0L || task.connectedPeers > 0)
    val isInternalPlayerEnabled = LocalFeatureCapabilities.current.isInternalPlayerEnabled
    val isPlayable = isInternalPlayerEnabled && onPlay != null && ((isCompleted && !task.isFileMissing) || canStreamWhileDownloading)

    Surface(
        onClick =
            if (isBatchMode) {
                onToggleSelect
            } else if (isPlayable) {
                onPlay
            } else {
                onShowDetail
            },
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isBatchMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 4.dp),
                )
            }

            // 1. 16:9 宽屏视频封面预览
            Box(
                modifier =
                    Modifier
                        .width(112.dp)
                        .aspectRatio(16f / 9f)
                        .clip(BgmShapes.small),
            ) {
                CoverImage(
                    url = task.subjectCoverUrl.ifBlank { subjectCoverUrl },
                    contentDescription = task.title,
                    aspectRatio = 16f / 9f,
                    cornerRadius = 8.dp,
                    modifier = Modifier.fillMaxSize(),
                )

                // 播放标志浮层 (当可直接播放时)
                if (isPlayable) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.size(26.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = BgmIcons.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }

                // 左下角：集数微胶囊 (如 "03" 或 "全集")
                Surface(
                    shape = RoundedCornerShape(topEnd = 4.dp),
                    color = Color.Black.copy(alpha = 0.68f),
                    modifier = Modifier.align(Alignment.BottomStart),
                ) {
                    Text(
                        text =
                            if (task.episodeSort > 0f) {
                                stringResource(R.string.feature_user_downloads_ep_sort, task.episodeSort.toInt())
                            } else {
                                stringResource(R.string.feature_user_downloads_ep_all)
                            },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }

                // 贴底极细进度条 (下载中状态)
                if (task.status == TorrentTaskStatus.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. 信息与操作 Column (高度贴合 16:9 封面，上下 SpaceBetween 结构)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 63.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 第一行：规范分集标题 + 紧凑操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val displayTitle =
                        remember(task.episodeName, task.title, task.episodeSort) {
                            val epPrefix = if (task.episodeSort > 0f) "第 ${task.episodeSort.toInt()} 话" else ""
                            val rawName = task.episodeName.ifBlank { cleanTorrentTitle(task.title) }.trim()
                            when {
                                epPrefix.isNotBlank() && rawName.isNotBlank() -> "$epPrefix · $rawName"
                                epPrefix.isNotBlank() -> epPrefix
                                else -> rawName
                            }
                        }
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if (!isBatchMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            // 仅下载/排队/暂停/异常时提供顶行快捷动作，已完成时直接点击卡片起播
                            when {
                                task.isFileMissing -> {
                                    IconButton(
                                        onClick = onRedownload,
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = BgmIcons.Refresh,
                                            contentDescription = stringResource(R.string.feature_user_downloads_action_redownload),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }

                                task.status == TorrentTaskStatus.DOWNLOADING ||
                                    task.status == TorrentTaskStatus.RESOLVING ||
                                    task.status == TorrentTaskStatus.QUEUED -> {
                                    IconButton(
                                        onClick = onPause,
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = BgmIcons.Pause,
                                            contentDescription = stringResource(R.string.feature_user_downloads_status_paused),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }

                                task.status == TorrentTaskStatus.PAUSED -> {
                                    IconButton(
                                        onClick = onResume,
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = BgmIcons.PlayArrow,
                                            contentDescription = stringResource(R.string.feature_user_downloads_all_resume),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }

                                task.status == TorrentTaskStatus.ERROR -> {
                                    IconButton(
                                        onClick = onResume,
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = BgmIcons.Refresh,
                                            contentDescription = stringResource(R.string.feature_user_downloads_action_retry),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }

                            // 更多操作 (⋮) 统一收口二级抽屉与深层动作
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = BgmIcons.MoreVert,
                                        contentDescription = stringResource(R.string.feature_user_downloads_action_more),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    // 任务与文件详情 (二级抽屉入口)
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.feature_user_downloads_action_detail)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = BgmIcons.Info,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onShowDetail()
                                        },
                                    )

                                    // 边下边播 (下载中且已生成播放流时)
                                    if (canStreamWhileDownloading && isInternalPlayerEnabled && onPlay != null) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.feature_user_downloads_stream_while_downloading)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = BgmIcons.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onPlay()
                                            },
                                        )
                                    }

                                    // 以其他应用打开 (落盘完成或有流地址时)
                                    val canOpenExternal =
                                        (isCompleted && task.savePath.isNotBlank() && !task.isFileMissing) ||
                                            (task.streamUrl.isNotBlank())
                                    if (canOpenExternal) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.feature_user_downloads_open_with_external)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = BgmIcons.OpenInNew,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                val mediaPathOrUrl =
                                                    if (isCompleted && task.savePath.isNotBlank()) {
                                                        task.savePath
                                                    } else {
                                                        task.streamUrl
                                                    }
                                                context.launchMediaWithChooser(
                                                    mediaUriOrPath = mediaPathOrUrl,
                                                    title = task.episodeName.ifBlank { task.title },
                                                    chooserTitle = context.getString(R.string.feature_user_downloads_open_with_external),
                                                )
                                            },
                                        )
                                    }

                                    // 保存到系统相册 (仅已完成文件)
                                    if (isCompleted && !task.isFileMissing && onExportToGallery != null) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.feature_user_downloads_export_to_gallery)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = BgmIcons.Download,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onExportToGallery()
                                            },
                                        )
                                    }

                                    // 删除下载
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.feature_user_action_delete),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = BgmIcons.DeleteBorder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onDelete()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // 第二行：点分隔元数据标签 (例如 1080P · AVC · MKV · 878.2 MB)，规避 Chip 折行
                val specsText =
                    remember(task.title, task.savePath, task.totalBytes, task.downloadedBytes) {
                        extractMediaTags(task).joinToString(" · ")
                    }
                if (specsText.isNotBlank()) {
                    Text(
                        text = specsText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 第三行：状态与动态进度
                when {
                    task.isFileMissing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = stringResource(R.string.feature_user_downloads_file_missing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    task.status == TorrentTaskStatus.COMPLETED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = stringResource(R.string.feature_user_downloads_status_completed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    task.status == TorrentTaskStatus.DOWNLOADING -> {
                        val statusText =
                            if (task.isConnectingPeers) {
                                "${task.percentage}% · " + stringResource(R.string.feature_user_downloads_status_connecting_peers)
                            } else {
                                val peerSuffix =
                                    if (task.connectedPeers > 0) {
                                        " · " + stringResource(R.string.feature_user_downloads_peers_count, task.connectedPeers)
                                    } else {
                                        ""
                                    }
                                "${task.percentage}% · ${task.formattedDownloadSpeed}$peerSuffix"
                            }
                        Column {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            LinearProgressIndicator(
                                progress = { task.progress },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(BgmShapes.extraSmall),
                            )
                        }
                    }

                    task.status == TorrentTaskStatus.PAUSED -> {
                        Text(
                            text = stringResource(R.string.feature_user_downloads_status_paused_detail, task.formattedProgress),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    task.status == TorrentTaskStatus.RESOLVING -> {
                        Text(
                            text = stringResource(R.string.feature_user_downloads_status_resolving),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    task.status == TorrentTaskStatus.QUEUED -> {
                        Text(
                            text = stringResource(R.string.feature_user_downloads_status_queued_detail, task.formattedProgress),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    task.status == TorrentTaskStatus.ERROR -> {
                        val errorMsg = task.errorMessage ?: stringResource(R.string.feature_user_downloads_status_error)
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaMetadataTagsRow(
    task: TorrentTask,
    modifier: Modifier = Modifier,
) {
    val tags =
        remember(task.title, task.savePath, task.totalBytes, task.downloadedBytes) {
            extractMediaTags(task)
        }
    if (tags.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            Surface(
                shape = BgmShapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}

internal fun extractMediaTags(task: TorrentTask): List<String> {
    val list = mutableListOf<String>()
    val searchStr = "${task.title} ${task.savePath}".uppercase()

    // 1. 分辨率
    when {
        searchStr.contains("4K") || searchStr.contains("2160P") -> list.add("4K")
        searchStr.contains("1080P") -> list.add("1080P")
        searchStr.contains("720P") -> list.add("720P")
    }

    // 2. 编码
    when {
        searchStr.contains("HEVC") || searchStr.contains("H.265") || searchStr.contains("X265") -> list.add("HEVC")
        searchStr.contains("AVC") || searchStr.contains("H.264") || searchStr.contains("X264") -> list.add("AVC")
        searchStr.contains("AV1") -> list.add("AV1")
    }

    // 3. 容器格式
    val ext =
        task.savePath
            .substringAfterLast('.', "")
            .ifBlank {
                task.title.substringAfterLast('.', "")
            }.uppercase()
    if (ext in listOf("MKV", "MP4", "WEBM")) {
        list.add(ext)
    }

    // 4. 大小
    val sizeBytes = if (task.totalBytes > 0L) task.totalBytes else task.downloadedBytes
    if (sizeBytes > 0L) {
        val mb = sizeBytes / (1024.0 * 1024.0)
        val sizeLabel =
            if (mb >= 1024) {
                "%.1f GB".format(mb / 1024.0)
            } else {
                "%.0f MB".format(mb)
            }
        list.add(sizeLabel)
    }

    return list.take(4)
}

internal fun cleanTorrentTitle(rawTitle: String): String {
    if (rawTitle.isBlank()) return ""
    var title = rawTitle.trim()
    title = title.replaceFirst(Regex("^(\\[[^\\]]+\\]|【[^】]+】)\\s*"), "")
    title = title.replace(Regex("\\.(mkv|mp4|avi|flv)$", RegexOption.IGNORE_CASE), "")
    return title.ifBlank { rawTitle }
}
