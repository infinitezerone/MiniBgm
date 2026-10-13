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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.SubjectDownloadGroup
import org.koin.androidx.compose.koinViewModel
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/**
 * 第一级：下载管理总览界面。
 *
 * 遵循成熟下载客户端范式：以番剧合集（专辑/文件夹）聚合归类展示，
 * 封面、标题、分集总揽、动态总速度一目了然，点击下钻进入该番剧分集详情。
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
    DownloadScreenContent(
        downloadGroups = downloadGroups,
        onBackClick = onBackClick,
        onSubjectClick = onSubjectClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadScreenContent(
    downloadGroups: List<SubjectDownloadGroup>,
    onBackClick: () -> Unit,
    onSubjectClick: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = { Text(text = stringResource(R.string.feature_user_downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = BgmIcons.ArrowBack,
                            contentDescription = stringResource(DesignSystemR.string.core_designsystem_action_back),
                        )
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
                    DownloadSummaryBanner(groups = downloadGroups)
                }

                items(
                    items = downloadGroups,
                    key = { it.subjectId },
                ) { group ->
                    SubjectDownloadCard(
                        group = group,
                        onClick = { onSubjectClick(group.subjectId, group.subjectName) },
                    )
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
    modifier: Modifier = Modifier,
) {
    val activeCount = groups.sumOf { it.downloadingCount + it.resolvingCount }
    val errorCount = groups.sumOf { it.errorCount }
    val pausedCount = groups.sumOf { it.pausedCount }
    val totalSpeed = groups.sumOf { it.totalSpeedBytesPerSec }
    val formattedSpeed =
        when {
            totalSpeed <= 0L -> "0 KB/s"
            totalSpeed >= 1024 * 1024 -> "%.1f MB/s".format(totalSpeed / (1024.0 * 1024.0))
            else -> "%d KB/s".format(totalSpeed / 1024)
        }

    val summaryText =
        when {
            activeCount > 0 -> "正在下载 $activeCount 集 · 总速度 $formattedSpeed"
            errorCount > 0 -> stringResource(R.string.feature_user_downloads_summary_error, errorCount)
            pausedCount > 0 -> stringResource(R.string.feature_user_downloads_summary_paused, pausedCount)
            else -> "全部任务已就绪"
        }
    val summaryColor =
        when {
            activeCount > 0 -> MaterialTheme.colorScheme.primary
            errorCount > 0 -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "共 ${groups.size} 部番剧离线合集",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = summaryColor,
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
            }
        }
    }
}

@Composable
private fun SubjectDownloadCard(
    group: SubjectDownloadGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                            text = "已完结离线 · ${group.totalTasks} 集 · ${group.formattedTotalSize}",
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

            Icon(
                imageVector = BgmIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
