package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.TorrentTask
import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
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
    onPlayEpisode: (PlayerRoute) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = koinViewModel(),
) {
    val downloadGroups by viewModel.downloadGroups.collectAsStateWithLifecycle()
    val group = downloadGroups.firstOrNull { it.subjectId == subjectId }
    SubjectDownloadDetailContent(
        subjectId = subjectId,
        subjectName = subjectName,
        group = group,
        onBackClick = onBackClick,
        onPlayEpisode = onPlayEpisode,
        onPauseTask = viewModel::pauseTask,
        onResumeTask = viewModel::resumeTask,
        onDeleteTask = viewModel::deleteTask,
        onPauseAll = { viewModel.pauseAllForSubject(subjectId) },
        onResumeAll = { viewModel.resumeAllForSubject(subjectId) },
        onDeleteAll = { viewModel.deleteAllForSubject(subjectId) },
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
    onPlayEpisode: (PlayerRoute) -> Unit,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tasks = group?.sortedTasks ?: emptyList()
    val overlayHostState = rememberOverlayHostState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

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
        topBar = {
            BgmTopAppBar(
                title = {
                    Text(
                        text = subjectName.ifBlank { group?.subjectName.orEmpty() },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = BgmIcons.ArrowBack,
                            contentDescription = stringResource(DesignSystemR.string.core_designsystem_action_back),
                        )
                    }
                },
                actions = {
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
                },
            )
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
                        onPlay = {
                            val playUrl =
                                when {
                                    task.isCompleted && task.savePath.isNotBlank() -> "file://${task.savePath}"
                                    task.status == TorrentTaskStatus.DOWNLOADING && task.streamUrl.isNotBlank() -> task.streamUrl
                                    task.savePath.isNotBlank() -> "file://${task.savePath}"
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
                                    )
                                onPlayEpisode(route)
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
                    text = "已离线 ${group.completedCount} 集 / 共 ${group.totalTasks} 集 · ${group.formattedTotalSize}",
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
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompleted = task.status == TorrentTaskStatus.COMPLETED
    val isError = task.status == TorrentTaskStatus.ERROR
    val canStreamWhileDownloading =
        task.status == TorrentTaskStatus.DOWNLOADING &&
            task.streamUrl.isNotBlank() &&
            (task.downloadedBytes > 0L || task.connectedPeers > 0)
    val isPlayable = isCompleted || canStreamWhileDownloading

    Surface(
        onClick =
            if (isPlayable) {
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
            Surface(
                shape = BgmShapes.small,
                color =
                    when {
                        isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
            ) {
                Text(
                    text =
                        if (task.episodeSort > 0f) {
                            "第 ${task.episodeSort.toInt()} 话"
                        } else {
                            "全集"
                        },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color =
                        when {
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

                when (task.status) {
                    TorrentTaskStatus.COMPLETED -> {
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
                        }
                    }

                    TorrentTaskStatus.DOWNLOADING -> {
                        val isConnectingPeers = task.connectedPeers == 0 && task.downloadSpeedBytesPerSec <= 0L
                        val statusText =
                            if (isConnectingPeers) {
                                "${task.percentage}% · ${stringResource(R.string.feature_user_downloads_status_connecting_peers)}"
                            } else {
                                val peerSuffix = if (task.connectedPeers > 0) " · ${task.connectedPeers} 节点" else ""
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

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                when (task.status) {
                    TorrentTaskStatus.COMPLETED -> {
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

                    TorrentTaskStatus.DOWNLOADING -> {
                        if (canStreamWhileDownloading) {
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
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        IconButton(
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

                    TorrentTaskStatus.RESOLVING,
                    TorrentTaskStatus.QUEUED,
                    -> {
                        IconButton(
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

                    TorrentTaskStatus.PAUSED,
                    TorrentTaskStatus.ERROR,
                    -> {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.PlayArrow,
                                contentDescription =
                                    if (isError) {
                                        stringResource(R.string.feature_user_downloads_action_retry)
                                    } else {
                                        stringResource(R.string.feature_user_downloads_all_resume)
                                    },
                                tint =
                                    if (isError) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.DeleteBorder,
                        contentDescription = stringResource(R.string.feature_user_action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
