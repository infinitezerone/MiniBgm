package com.infinitezerone.minibgm.feature.user.components

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.TorrentTask
import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
import com.infinitezerone.minibgm.feature.user.R
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 视频与下载任务的二级详细信息抽屉。
 *
 * 渐进式呈现（Progressive Disclosure）：
 * 一级列表卡片仅保留核心流媒体播放属性（16:9 封面、话数、标签、进度）；
 * 原始长标题、本地绝对存储路径、精确字节大小、InfoHash、节点连接与底层诊断堆栈收口于此抽屉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoTaskDetailBottomSheet(
    task: TorrentTask,
    subjectCoverUrl: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayExternal: (() -> Unit)? = null,
    onExportToGallery: (() -> Unit)? = null,
    onRedownload: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val copySuccessToast = stringResource(R.string.feature_user_downloads_detail_copied)

    val copyToClipboard: (String) -> Unit = { text ->
        if (text.isNotBlank()) {
            coroutineScope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("text", text)))
            }
            Toast.makeText(context, copySuccessToast, Toast.LENGTH_SHORT).show()
        }
    }

    val isCompleted = task.status == TorrentTaskStatus.COMPLETED
    val formatExt =
        remember(task.savePath, task.title) {
            val candidate =
                task.savePath.substringAfterLast('.', "").ifBlank {
                    task.title.substringAfterLast('.', "")
                }
            candidate.uppercase().takeIf { it.isNotBlank() && it.length <= 5 } ?: "VIDEO"
        }

    val infoHash =
        remember(task.magnetUrl) {
            val match = Regex("xt=urn:btih:([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE).find(task.magnetUrl)
            match?.groupValues?.getOrNull(1)?.uppercase() ?: ""
        }

    val formattedCreatedTime =
        remember(task.createdTimestamp) {
            if (task.createdTimestamp > 0L) {
                try {
                    val instant = Instant.ofEpochMilli(task.createdTimestamp)
                    DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                        .format(instant)
                } catch (_: Exception) {
                    ""
                }
            } else {
                ""
            }
        }

    val formattedCompletedTime =
        remember(task.completionTimestamp) {
            val ts = task.completionTimestamp ?: 0L
            if (ts > 0L) {
                try {
                    val instant = Instant.ofEpochMilli(ts)
                    DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                        .format(instant)
                } catch (_: Exception) {
                    ""
                }
            } else {
                ""
            }
        }

    val exactBytesFormatted =
        remember(task.totalBytes, task.downloadedBytes) {
            val targetBytes = if (task.totalBytes > 0L) task.totalBytes else task.downloadedBytes
            String.format(Locale.getDefault(), "%,d", targetBytes)
        }

    BgmModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
        ) {
            // 抽屉顶栏标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.feature_user_downloads_detail_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = BgmShapes.small,
                    color =
                        when {
                            task.isFileMissing -> MaterialTheme.colorScheme.errorContainer
                            isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            task.status == TorrentTaskStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                ) {
                    Text(
                        text =
                            when {
                                task.isFileMissing -> stringResource(R.string.feature_user_downloads_file_missing)
                                isCompleted -> stringResource(R.string.feature_user_downloads_status_completed)
                                task.status == TorrentTaskStatus.DOWNLOADING -> "${task.percentage}%"
                                task.status == TorrentTaskStatus.PAUSED -> stringResource(R.string.feature_user_downloads_status_paused)
                                task.status == TorrentTaskStatus.RESOLVING ->
                                    stringResource(
                                        R.string.feature_user_downloads_status_resolving,
                                    )
                                task.status == TorrentTaskStatus.QUEUED -> stringResource(R.string.feature_user_downloads_status_queued)
                                else -> stringResource(R.string.feature_user_downloads_status_error)
                            },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color =
                            when {
                                task.isFileMissing || task.status == TorrentTaskStatus.ERROR -> MaterialTheme.colorScheme.error
                                isCompleted -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 头部：16:9 预览与完整文件名
            Surface(
                shape = BgmShapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        CoverImage(
                            url = task.subjectCoverUrl.ifBlank { subjectCoverUrl },
                            contentDescription = task.title,
                            aspectRatio = 16f / 9f,
                            cornerRadius = 8.dp,
                            modifier = Modifier.width(96.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text =
                                    if (task.episodeSort > 0f) {
                                        if (task.episodeName.isNotBlank()) {
                                            stringResource(R.string.feature_user_downloads_ep_sort, task.episodeSort.toInt()) +
                                                " · ${task.episodeName}"
                                        } else {
                                            stringResource(R.string.feature_user_downloads_ep_sort, task.episodeSort.toInt())
                                        }
                                    } else {
                                        task.episodeName.ifBlank { stringResource(R.string.feature_user_downloads_ep_all) }
                                    },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = BgmShapes.extraSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            ) {
                                Text(
                                    text = formatExt,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 原始完整文件名展示与复制
                    Text(
                        text = stringResource(R.string.feature_user_downloads_detail_raw_name),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { copyToClipboard(task.title) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 1: 本地文件属性
            DetailSectionCard(title = stringResource(R.string.feature_user_downloads_detail_section_file)) {
                // 路径
                if (task.savePath.isNotBlank()) {
                    DetailInfoRow(
                        label = stringResource(R.string.feature_user_downloads_detail_file_path),
                        value = task.savePath,
                        isMono = true,
                        onCopy = { copyToClipboard(task.savePath) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 文件大小 (格式化 + 精确字节)
                DetailInfoRow(
                    label = stringResource(R.string.feature_user_downloads_detail_file_size),
                    value =
                        stringResource(
                            R.string.feature_user_downloads_detail_bytes_format,
                            task.formattedProgress,
                            exactBytesFormatted,
                        ),
                )

                // 封装格式
                Spacer(modifier = Modifier.height(8.dp))
                DetailInfoRow(
                    label = stringResource(R.string.feature_user_downloads_detail_file_format),
                    value = formatExt,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 2: 传输与网络
            DetailSectionCard(title = stringResource(R.string.feature_user_downloads_detail_section_network)) {
                if (infoHash.isNotBlank()) {
                    DetailInfoRow(
                        label = stringResource(R.string.feature_user_downloads_detail_info_hash),
                        value = infoHash,
                        isMono = true,
                        onCopy = { copyToClipboard(infoHash) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 节点连接
                if (task.connectedPeers > 0 || task.connectedSeeds > 0) {
                    DetailInfoRow(
                        label = stringResource(R.string.feature_user_downloads_detail_peers),
                        value =
                            stringResource(
                                R.string.feature_user_downloads_detail_peers_count,
                                task.connectedPeers,
                                task.connectedSeeds,
                            ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 传输进度与速度
                if (task.status == TorrentTaskStatus.DOWNLOADING) {
                    DetailInfoRow(
                        label = stringResource(R.string.feature_user_downloads_detail_status_title),
                        value = "${task.formattedDownloadSpeed} · ${task.percentage}%",
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (formattedCreatedTime.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.feature_user_downloads_detail_created_time, formattedCreatedTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (formattedCompletedTime.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.feature_user_downloads_detail_completed_time, formattedCompletedTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 错误信息完整堆栈展示
                val errorMsg = task.errorMessage
                if (!errorMsg.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.feature_user_downloads_detail_error_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = BgmShapes.small,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 底部操作区
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 外部播放器打开
                if (isCompleted && !task.isFileMissing && onPlayExternal != null) {
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onPlayExternal()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.feature_user_downloads_open_with_external),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }

                // 保存到系统相册
                if (isCompleted && !task.isFileMissing && onExportToGallery != null) {
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onExportToGallery()
                        },
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
                            text = stringResource(R.string.feature_user_downloads_export_to_gallery),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }

                // 重新下载
                if ((task.isFileMissing || task.status == TorrentTaskStatus.ERROR) && onRedownload != null) {
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onRedownload()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.feature_user_downloads_action_retry),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }

                // 删除
                if (onDelete != null) {
                    Button(
                        onClick = {
                            onDismiss()
                            onDelete()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                    ) {
                        Icon(
                            imageVector = BgmIcons.DeleteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.feature_user_action_delete),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isMono: Boolean = false,
    onCopy: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.Medium,
            )
        }
        if (onCopy != null) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = BgmIcons.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}
