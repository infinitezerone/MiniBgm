package com.infinitezerone.minibgm.feature.user.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.designsystem.component.BgmCircleActionButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmGhostIconButton
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.PlaybackHistory
import com.infinitezerone.minibgm.core.model.toEpisodeLabel
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.feature.user.HistoryGroup
import com.infinitezerone.minibgm.feature.user.R
import java.time.Instant
import java.time.ZoneId

/** 封面缩略图尺寸：3:4 海报比例，与收藏卡片的视觉重量错开一档 */
private val HISTORY_COVER_WIDTH = 56.dp
private val HISTORY_COVER_HEIGHT = 76.dp

/**
 * 观看记录单条卡片（按番剧聚合）：
 * - 封面可点击直达条目详情；
 * - 顶部主行展示番剧名、最近观看集数、断点进度、相对时间；
 * - 右侧集中收敛主续播按钮与更多菜单；
 * - 多集时底部提供独立展开条，杜绝横向挤压。
 */
@Composable
internal fun PlaybackHistoryCard(
    group: HistoryGroup,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onResume: (PlaybackHistory) -> Unit,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onDeleteEpisode: (PlaybackHistory) -> Unit,
    onDeleteGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandable = group.episodeCount > 1

    Surface(
        shape = BgmShapes.medium,
        // 与下载列表统一卡片层级：surfaceContainerHigh 作为列表主容器色
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = { onResume(group.latest) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            PlaybackHistoryMainRow(
                group = group,
                onResume = onResume,
                onSubjectClick = onSubjectClick,
                onDeleteGroup = onDeleteGroup,
            )

            if (expandable) {
                PlaybackHistoryExpandBar(
                    expanded = expanded,
                    episodeCount = group.episodeCount,
                    onToggle = onToggleExpanded,
                )
            }

            AnimatedVisibility(visible = expanded && expandable) {
                PlaybackHistoryEpisodeSubList(
                    episodes = group.episodes,
                    onResume = onResume,
                    onDelete = onDeleteEpisode,
                )
            }
        }
    }
}

/** 观看记录主行：封面 + 标题进度 + 续播与菜单 */
@Composable
private fun PlaybackHistoryMainRow(
    group: HistoryGroup,
    onResume: (PlaybackHistory) -> Unit,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onDeleteGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
    ) {
        HistoryCover(
            url = group.coverUrl,
            progress = group.latest.progress,
            showProgress = group.latest.durationMs > 0L && group.latest.progress > 0f,
            onClick =
                if (group.subjectId > 0L) {
                    { onSubjectClick(SubjectDetailRoute(group.subjectId)) }
                } else {
                    null
                },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = group.subjectName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = episodeText(group.latest),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
            ) {
                Text(
                    text = historySummaryText(group.latest),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (group.latest.isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                HistoryTimeText(updatedAt = group.latest.updatedAt)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        BgmCircleActionButton(
            onClick = { onResume(group.latest) },
            icon = BgmIcons.PlayArrow,
            contentDescription = stringResource(R.string.feature_user_history_resume_play),
            size = 36.dp,
            iconSize = 20.dp,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            hasDiffuseShadow = false,
        )
        Box {
            BgmGhostIconButton(
                onClick = { menuExpanded = true },
                icon = BgmIcons.MoreVert,
                contentDescription = stringResource(R.string.feature_user_history_group_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                iconSize = 18.dp,
            )
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (group.subjectId > 0L) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.feature_user_history_view_subject)) },
                        leadingIcon = {
                            Icon(
                                imageVector = BgmIcons.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onSubjectClick(SubjectDetailRoute(group.subjectId))
                        },
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text =
                                stringResource(
                                    if (group.subjectId > 0L) {
                                        R.string.feature_user_history_group_delete
                                    } else {
                                        R.string.feature_user_history_cd_delete
                                    },
                                ),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = BgmIcons.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDeleteGroup()
                    },
                )
            }
        }
    }
}

/** 独立底部分集折叠触发条：释放主标题横向空间 */
@Composable
private fun PlaybackHistoryExpandBar(
    expanded: Boolean,
    episodeCount: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp),
        // 用中性发丝线：主题的 outlineVariant 带主色倾向，会被误读成进度条
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                // 可点区域至少 44dp：原 6dp 垂直 padding 只有约 30dp 高，容易点空
                .heightIn(min = 44.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.feature_user_history_group_count, episodeCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (expanded) BgmIcons.KeyboardArrowUp else BgmIcons.KeyboardArrowDown,
            contentDescription =
                stringResource(
                    if (expanded) {
                        R.string.feature_user_history_group_collapse
                    } else {
                        R.string.feature_user_history_group_expand
                    },
                ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 展开后的分集子列表 */
@Composable
private fun PlaybackHistoryEpisodeSubList(
    episodes: List<PlaybackHistory>,
    onResume: (PlaybackHistory) -> Unit,
    onDelete: (PlaybackHistory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        episodes.forEach { entry ->
            HistoryEpisodeRow(
                history = entry,
                onResume = { onResume(entry) },
                onDelete = { onDelete(entry) },
            )
        }
    }
}

/** 展开后的单集行：集名 + 进度 + 相对时间 + 删除（直接删除 + 撤销） */
@Composable
private fun HistoryEpisodeRow(
    history: PlaybackHistory,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onResume)
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episodeText(history),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = historyProgressText(history),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HistoryTimeText(updatedAt = history.updatedAt)
        BgmGhostIconButton(
            onClick = onDelete,
            icon = BgmIcons.Delete,
            contentDescription = stringResource(R.string.feature_user_history_cd_delete),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            iconSize = 16.dp,
        )
    }
}

/** 分集标题：「第 N 话」与分集名重复时只显示一次 */
@Composable
private fun episodeText(history: PlaybackHistory): String {
    val label =
        stringResource(
            R.string.feature_user_history_episode_label,
            history.episodeSort.toEpisodeLabel(),
        )
    return if (history.episodeName.isNotBlank() && history.episodeName != label) {
        "$label · ${history.episodeName}"
    } else {
        label
    }
}

/** 进度文案：已看完直说已看完，时长未知时只报已看位置，避免出现 "看到 00:12 / 00:00" */
@Composable
private fun historyProgressText(history: PlaybackHistory): String =
    when {
        history.isCompleted -> stringResource(R.string.feature_user_history_completed)
        history.durationMs <= 0L ->
            stringResource(
                R.string.feature_user_history_seen_only,
                formatPlaybackPosition(history.positionMs),
            )
        else ->
            stringResource(
                R.string.feature_user_history_progress,
                formatPlaybackPosition(history.positionMs),
                formatPlaybackPosition(history.durationMs),
            )
    }

/** 主行摘要：进度文案 + 播放源（源名作为弱信息跟在后面，不再单独占一列） */
@Composable
private fun historySummaryText(history: PlaybackHistory): String {
    val progress = historyProgressText(history)
    return if (history.sourceName.isNotBlank()) "$progress · ${history.sourceName}" else progress
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / 昨天 / N 天前 / yyyy-MM-dd */
@Composable
private fun HistoryTimeText(
    updatedAt: Long,
    modifier: Modifier = Modifier,
) {
    val text = historyTimeText(updatedAt) ?: return
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun historyTimeText(updatedAt: Long): String? {
    if (updatedAt <= 0L) return null
    val now = remember { System.currentTimeMillis() }
    val diff = (now - updatedAt).coerceAtLeast(0L)
    return when {
        diff < ONE_MINUTE_MILLIS -> stringResource(R.string.feature_user_history_time_just_now)
        diff < ONE_HOUR_MILLIS ->
            stringResource(
                R.string.feature_user_history_time_minutes_ago,
                (diff / ONE_MINUTE_MILLIS).toInt().coerceAtLeast(1),
            )
        diff < ONE_DAY_MILLIS ->
            stringResource(
                R.string.feature_user_history_time_hours_ago,
                (diff / ONE_HOUR_MILLIS).toInt().coerceAtLeast(1),
            )
        diff < 2 * ONE_DAY_MILLIS -> stringResource(R.string.feature_user_history_time_yesterday)
        diff < 7 * ONE_DAY_MILLIS ->
            stringResource(
                R.string.feature_user_history_time_days_ago,
                (diff / ONE_DAY_MILLIS).toInt().coerceAtLeast(1),
            )
        else ->
            remember(updatedAt) {
                Instant
                    .ofEpochMilli(updatedAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .toString()
            }
    }
}

private const val ONE_MINUTE_MILLIS = 60_000L
private const val ONE_HOUR_MILLIS = 60 * ONE_MINUTE_MILLIS
private const val ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS

/** 封面缩略图：无封面时退化为播放图标占位，底边贴合微型断点进度条 */
@Composable
private fun HistoryCover(
    url: String,
    progress: Float = 0f,
    showProgress: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier =
        if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier =
            Modifier
                .size(width = HISTORY_COVER_WIDTH, height = HISTORY_COVER_HEIGHT)
                .then(clickableModifier),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (url.isNotBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = BgmIcons.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (showProgress) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                )
            }
        }
    }
}
