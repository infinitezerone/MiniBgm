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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopBarMode
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.DownloadStorageUsage
import com.infinitezerone.minibgm.core.model.SubjectDownloadGroup
import com.infinitezerone.minibgm.core.model.formatTransferSpeed
import org.koin.androidx.compose.koinViewModel
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/**
 * 下载管理状态筛选分类。
 */
enum class DownloadFilterTab {
    ALL,
    DOWNLOADING,
    COMPLETED,
}

/**
 * 第一级：下载管理总览界面。
 *
 * 遵循成熟下载客户端范式：以番剧合集（专辑/文件夹）聚合归类展示，
 * 封面、标题、分集总揽、动态总速度一目了然，支持状态分类过滤与单番剧/全局快捷控制，
 * 点击下钻进入该番剧分集详情。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onBackClick: () -> Unit,
    onSubjectClick: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = koinViewModel(),
) {
    val downloadGroups by viewModel.downloadGroups.collectAsStateWithLifecycle()
    val storageUsage by viewModel.storageUsage.collectAsStateWithLifecycle()
    DownloadScreenContent(
        downloadGroups = downloadGroups,
        storageUsage = storageUsage,
        onBackClick = onBackClick,
        onSubjectClick = onSubjectClick,
        onPauseAll = viewModel::pauseAll,
        onResumeAll = viewModel::resumeAll,
        onPauseSubject = viewModel::pauseAllForSubject,
        onResumeSubject = viewModel::resumeAllForSubject,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadScreenContent(
    downloadGroups: List<SubjectDownloadGroup>,
    storageUsage: DownloadStorageUsage = DownloadStorageUsage(),
    onBackClick: () -> Unit,
    onSubjectClick: (Long, String) -> Unit,
    onPauseAll: () -> Unit = {},
    onResumeAll: () -> Unit = {},
    onPauseSubject: (Long) -> Unit = {},
    onResumeSubject: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedFilter by rememberSaveable { mutableStateOf(DownloadFilterTab.ALL) }

    val hasActive =
        remember(downloadGroups) {
            downloadGroups.any {
                it.downloadingCount > 0 || it.resolvingCount > 0 || it.queuedCount > 0
            }
        }
    val hasResumable =
        remember(downloadGroups) {
            downloadGroups.any {
                !it.isAllCompleted && (it.pausedCount > 0 || it.errorCount > 0)
            }
        }

    val downloadingCount =
        remember(downloadGroups) {
            downloadGroups.count { !it.isAllCompleted }
        }
    val completedCount =
        remember(downloadGroups) {
            downloadGroups.count { it.isAllCompleted }
        }

    val filteredGroups =
        remember(downloadGroups, selectedFilter) {
            when (selectedFilter) {
                DownloadFilterTab.ALL -> downloadGroups
                DownloadFilterTab.DOWNLOADING -> downloadGroups.filter { !it.isAllCompleted }
                DownloadFilterTab.COMPLETED -> downloadGroups.filter { it.isAllCompleted }
            }
        }

    Scaffold(
        topBar = {
            BgmTopAppBar(
                mode = BgmTopBarMode.Solid,
                title = { Text(text = stringResource(R.string.feature_user_downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = BgmIcons.ArrowBack,
                            contentDescription = stringResource(DesignSystemR.string.core_designsystem_action_back),
                        )
                    }
                },
                actions = {
                    if (downloadGroups.isNotEmpty()) {
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
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        if (downloadGroups.isEmpty()) {
            EmptyDownloadView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "downloads_header_summary") {
                    DownloadSummaryBanner(
                        groups = downloadGroups,
                        storageUsage = storageUsage,
                    )
                }

                item(key = "downloads_filter_chips") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedFilter == DownloadFilterTab.ALL,
                            onClick = { selectedFilter = DownloadFilterTab.ALL },
                            label = {
                                Text(stringResource(R.string.feature_user_downloads_tab_all, downloadGroups.size))
                            },
                        )
                        FilterChip(
                            selected = selectedFilter == DownloadFilterTab.DOWNLOADING,
                            onClick = { selectedFilter = DownloadFilterTab.DOWNLOADING },
                            label = {
                                Text(stringResource(R.string.feature_user_downloads_tab_downloading, downloadingCount))
                            },
                        )
                        FilterChip(
                            selected = selectedFilter == DownloadFilterTab.COMPLETED,
                            onClick = { selectedFilter = DownloadFilterTab.COMPLETED },
                            label = {
                                Text(stringResource(R.string.feature_user_downloads_tab_completed, completedCount))
                            },
                        )
                    }
                }

                if (filteredGroups.isEmpty()) {
                    item(key = "downloads_filter_empty") {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.feature_user_downloads_filter_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { selectedFilter = DownloadFilterTab.ALL }) {
                                    Text(stringResource(R.string.feature_user_downloads_filter_reset))
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredGroups,
                        key = { it.subjectId },
                    ) { group ->
                        SubjectDownloadCard(
                            group = group,
                            onClick = { onSubjectClick(group.subjectId, group.subjectName) },
                            onPauseSubject = onPauseSubject,
                            onResumeSubject = onResumeSubject,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloadView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = BgmIcons.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.feature_user_downloads_empty),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.feature_user_downloads_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadSummaryBanner(
    groups: List<SubjectDownloadGroup>,
    storageUsage: DownloadStorageUsage,
    modifier: Modifier = Modifier,
) {
    val activeCount = groups.sumOf { it.downloadingCount + it.resolvingCount }
    val errorCount = groups.sumOf { it.errorCount }
    val pausedCount = groups.sumOf { it.pausedCount }
    val isAllDone = groups.isNotEmpty() && groups.all { it.isAllCompleted }
    val totalSpeed = groups.sumOf { it.totalSpeedBytesPerSec }
    val formattedSpeed = formatTransferSpeed(totalSpeed)

    val statusText =
        when {
            activeCount > 0 -> stringResource(R.string.feature_user_downloads_active_summary, activeCount, formattedSpeed)
            errorCount > 0 -> stringResource(R.string.feature_user_downloads_summary_error, errorCount)
            pausedCount > 0 -> stringResource(R.string.feature_user_downloads_summary_paused, pausedCount)
            isAllDone -> stringResource(R.string.feature_user_downloads_card_all_done, groups.size)
            else -> stringResource(R.string.feature_user_downloads_idle_summary)
        }
    val statusColor =
        when {
            activeCount > 0 -> MaterialTheme.colorScheme.primary
            errorCount > 0 -> MaterialTheme.colorScheme.error
            isAllDone -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    val totalBytes = storageUsage.downloadSizeBytes + storageUsage.availableBytes
    val storageFraction =
        if (totalBytes > 0L) {
            (storageUsage.downloadSizeBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.feature_user_downloads_total_subjects, groups.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (activeCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = BgmShapes.small,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = formattedSpeed,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                } else if (isAllDone) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = BgmIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            if (storageUsage.downloadSizeBytes > 0L || storageUsage.availableBytes > 0L) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { storageFraction },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(BgmShapes.extraSmall),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.feature_user_downloads_storage_info,
                                storageUsage.formattedDownloadSize,
                                storageUsage.formattedAvailableSize,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (storageFraction > 0f) {
                        Text(
                            text = "${(storageFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectDownloadCard(
    group: SubjectDownloadGroup,
    onClick: () -> Unit,
    onPauseSubject: (Long) -> Unit = {},
    onResumeSubject: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val hasActive = group.downloadingCount > 0 || group.resolvingCount > 0 || group.queuedCount > 0
    val hasPausedOrError = !group.isAllCompleted && (group.pausedCount > 0 || group.errorCount > 0)

    Surface(
        onClick = onClick,
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                url = group.subjectCoverUrl,
                contentDescription = group.subjectName,
                modifier = Modifier.width(60.dp),
                cornerRadius = 8.dp,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = group.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (group.isAllCompleted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.feature_user_downloads_finished_summary,
                                    group.totalTasks,
                                    group.formattedTotalSize,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Text(
                        text =
                            stringResource(
                                R.string.feature_user_downloads_completed_format,
                                group.completedCount,
                                group.totalTasks,
                            ) + " · ${group.formattedTotalSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    when {
                        group.downloadingCount > 0 -> {
                            val detailText =
                                if (group.isConnectingPeers) {
                                    stringResource(R.string.feature_user_downloads_status_connecting_peers)
                                } else {
                                    group.formattedTotalSpeed
                                }
                            val statusLabel =
                                stringResource(
                                    R.string.feature_user_downloads_status_downloading_count,
                                    group.downloadingCount,
                                    detailText,
                                )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${group.percentage}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { group.progress },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(BgmShapes.small),
                            )
                        }

                        group.resolvingCount > 0 -> {
                            Text(
                                text = stringResource(R.string.feature_user_downloads_status_resolving),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { group.progress },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(BgmShapes.small),
                            )
                        }

                        group.queuedCount > 0 -> {
                            Text(
                                text = stringResource(R.string.feature_user_downloads_status_queued),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        group.errorCount > 0 -> {
                            Text(
                                text =
                                    stringResource(
                                        R.string.feature_user_downloads_group_error_format,
                                        group.errorCount,
                                        group.firstErrorMessage ?: stringResource(R.string.feature_user_downloads_status_error),
                                    ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        else -> {
                            Text(
                                text = stringResource(R.string.feature_user_downloads_status_paused),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (hasActive) {
                    FilledTonalIconButton(
                        onClick = { onPauseSubject(group.subjectId) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.Pause,
                            contentDescription = stringResource(R.string.feature_user_downloads_all_pause),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else if (hasPausedOrError) {
                    FilledTonalIconButton(
                        onClick = { onResumeSubject(group.subjectId) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.PlayArrow,
                            contentDescription = stringResource(R.string.feature_user_downloads_all_resume),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Icon(
                    imageVector = BgmIcons.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
