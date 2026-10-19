package com.infinitezerone.minibgm.feature.user

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
                    )
                }
            }
        }
    }

    BgmOverlayHost(hostState = overlayHostState) {
        confirmDialog()
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
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onPlay: (() -> Unit)? = null,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onRedownload: () -> Unit = {},
    onExportToGallery: (() -> Unit)? = null,
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
                {}
            },
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isBatchMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }

            Surface(
                shape = BgmShapes.small,
                color =
                    when {
                        task.isFileMissing -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
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
                    color =
                        when {
                            task.isFileMissing -> MaterialTheme.colorScheme.error
                            isCompleted -> MaterialTheme.colorScheme.primary
                            isError -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.episodeName.ifBlank { task.title },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(3.dp))

                if (task.isFileMissing) {
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
                } else {
                    when (task.status) {
                        TorrentTaskStatus.COMPLETED -> {
                            val ext =
                                task.savePath
                                    .substringAfterLast('.', "")
                                    .uppercase()
                                    .takeIf { it.isNotBlank() && it.length <= 4 }
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
                                    text = stringResource(R.string.feature_user_downloads_status_completed_detail, task.formattedProgress),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (ext != null) {
                                    Surface(
                                        shape = BgmShapes.extraSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    ) {
                                        Text(
                                            text = ext,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                        }

                        TorrentTaskStatus.DOWNLOADING -> {
                            val statusText =
                                if (task.isConnectingPeers) {
                                    "${task.percentage}% · ${stringResource(R.string.feature_user_downloads_status_connecting_peers)}"
                                } else {
                                    val peerSuffix =
                                        if (task.connectedPeers > 0) {
                                            " · " + stringResource(R.string.feature_user_downloads_peers_count, task.connectedPeers)
                                        } else {
                                            ""
                                        }
                                    "${task.percentage}% · ${task.formattedDownloadSpeed}$peerSuffix"
                                }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { task.progress },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(BgmShapes.small),
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = task.formattedProgress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        TorrentTaskStatus.PAUSED -> {
                            Text(
                                text = stringResource(R.string.feature_user_downloads_status_paused_detail, task.formattedProgress),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        TorrentTaskStatus.RESOLVING -> {
                            Text(
                                text = stringResource(R.string.feature_user_downloads_status_resolving),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { task.progress },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(BgmShapes.small),
                            )
                            if (task.totalBytes > 0L) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = task.formattedProgress,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        TorrentTaskStatus.QUEUED -> {
                            Text(
                                text = stringResource(R.string.feature_user_downloads_status_queued_detail, task.formattedProgress),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        TorrentTaskStatus.ERROR -> {
                            Text(
                                text = task.errorMessage ?: stringResource(R.string.feature_user_downloads_status_error),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (task.downloadedBytes > 0L || task.totalBytes > 0L) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = task.formattedProgress,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (!isBatchMode) {
                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 主动作按钮 (至多 1 个)
                    when {
                        task.isFileMissing -> {
                            FilledTonalIconButton(
                                onClick = onRedownload,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = BgmIcons.Refresh,
                                    contentDescription = stringResource(R.string.feature_user_downloads_action_redownload),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        task.status == TorrentTaskStatus.COMPLETED -> {
                            if (isInternalPlayerEnabled && onPlay != null) {
                                FilledTonalIconButton(
                                    onClick = onPlay,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = BgmIcons.PlayArrow,
                                        contentDescription = stringResource(R.string.feature_user_downloads_play_offline),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

                        task.status == TorrentTaskStatus.DOWNLOADING ||
                            task.status == TorrentTaskStatus.RESOLVING ||
                            task.status == TorrentTaskStatus.QUEUED -> {
                            FilledTonalIconButton(
                                onClick = onPause,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = BgmIcons.Pause,
                                    contentDescription = stringResource(R.string.feature_user_downloads_status_paused),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        task.status == TorrentTaskStatus.PAUSED -> {
                            FilledTonalIconButton(
                                onClick = onResume,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = BgmIcons.PlayArrow,
                                    contentDescription = stringResource(R.string.feature_user_downloads_all_resume),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        task.status == TorrentTaskStatus.ERROR -> {
                            FilledTonalIconButton(
                                onClick = onResume,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = BgmIcons.Refresh,
                                    contentDescription = stringResource(R.string.feature_user_downloads_action_retry),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    // 更多操作 (⋮) 统一收口辅助与破坏性操作
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.MoreVert,
                                contentDescription = stringResource(R.string.feature_user_downloads_action_more),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
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
    }
}
