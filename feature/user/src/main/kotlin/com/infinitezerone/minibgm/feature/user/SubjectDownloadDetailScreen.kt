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
    val tasks = group?.sortedTasks ?: emptyList()
    val overlayHostState = rememberOverlayHostState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val hasDownloading = tasks.any { it.status == TorrentTaskStatus.DOWNLOADING }
    val hasPaused = tasks.any { it.status == TorrentTaskStatus.PAUSED }

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
                    if (hasDownloading) {
                        IconButton(onClick = { viewModel.pauseAllForSubject(subjectId) }) {
                            Icon(
                                imageVector = BgmIcons.Pause,
                                contentDescription = stringResource(R.string.feature_user_downloads_all_pause),
                            )
                        }
                    } else if (hasPaused) {
                        IconButton(onClick = { viewModel.resumeAllForSubject(subjectId) }) {
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
                                        viewModel.deleteAllForSubject(subjectId)
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
                            val playUrl = if (task.savePath.isNotBlank()) "file://${task.savePath}" else ""
                            val route =
                                PlayerRoute(
                                    subjectId = task.subjectId,
                                    episodeId = task.episodeId,
                                    streamUrl = playUrl,
                                    episodeName = task.episodeName.ifBlank { task.title },
                                    subjectName = task.subjectName,
                                    episodeSort = task.episodeSort,
                                )
                            onPlayEpisode(route)
                        },
                        onPause = { viewModel.pauseTask(task.id) },
                        onResume = { viewModel.resumeTask(task.id) },
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
                                    viewModel.deleteTask(task.id)
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
private fun EpisodeDownloadCard(
    task: TorrentTask,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompleted = task.status == TorrentTaskStatus.COMPLETED

    Surface(
        onClick =
            if (isCompleted) {
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
                    if (isCompleted) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
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
                        if (isCompleted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
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
                                text = "已完成 · ${task.formattedProgress}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    TorrentTaskStatus.DOWNLOADING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${task.percentage}% · ${task.formattedDownloadSpeed}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = task.formattedProgress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { task.progress },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(BgmShapes.small),
                        )
                    }

                    TorrentTaskStatus.PAUSED -> {
                        Text(
                            text = "已暂停 · ${task.formattedProgress}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    TorrentTaskStatus.RESOLVING -> {
                        Text(
                            text = stringResource(R.string.feature_user_downloads_status_resolving),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    TorrentTaskStatus.QUEUED -> {
                        Text(
                            text = stringResource(R.string.feature_user_downloads_status_queued),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    TorrentTaskStatus.ERROR -> {
                        Text(
                            text = task.errorMessage ?: stringResource(R.string.feature_user_downloads_status_error),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                                contentDescription = stringResource(R.string.feature_user_downloads_all_resume),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    else -> {}
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
