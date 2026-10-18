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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.PlaybackHistory
import com.infinitezerone.minibgm.core.model.toEpisodeLabel
import com.infinitezerone.minibgm.feature.user.HistoryGroup
import com.infinitezerone.minibgm.feature.user.R
import java.time.Instant
import java.time.ZoneId

/** 封面缩略图尺寸：3:4 海报比例，与收藏卡片的视觉重量错开一档 */
private val HISTORY_COVER_WIDTH = 52.dp
private val HISTORY_COVER_HEIGHT = 70.dp

/**
 * 观看记录单条（按番剧聚合）：封面 + 番名/最近一集 + 进度条 + 相对时间。
 *
 * 整卡点击按组内**最近一集**续播；分集多于一条时右侧出现展开箭头，
 * 展开后逐集列出，点某一集直接续播、行尾删除只删那一集。
 * 整组删除收进 `⋮` 菜单，避免主列表每行都挂一个删除按钮。
 */
@Composable
internal fun PlaybackHistoryCard(
    group: HistoryGroup,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onResume: (PlaybackHistory) -> Unit,
    onDeleteEpisode: (PlaybackHistory) -> Unit,
    onDeleteGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val expandable = group.episodeCount > 1

    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = { onResume(group.latest) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
            ) {
                HistoryCover(
                    url = group.coverUrl,
                    progress = group.latest.progress,
                    showProgress = group.latest.durationMs > 0L && group.latest.progress > 0f,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = group.subjectName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
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
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(
                            text = historyProgressText(group.latest),
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
                        Column(horizontalAlignment = Alignment.End) {
                            HistoryTimeText(updatedAt = group.latest.updatedAt)
                            if (group.latest.sourceName.isNotBlank()) {
                                Text(
                                    text = group.latest.sourceName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 108.dp),
                                )
                            }
                        }
                    }
                }
                if (expandable) {
                    IconButton(onClick = onToggleExpanded) {
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
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = BgmIcons.MoreVert,
                            contentDescription = stringResource(R.string.feature_user_history_group_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
                            onClick = {
                                menuExpanded = false
                                onDeleteGroup()
                            },
                        )
                    }
                }
            }
            AnimatedVisibility(visible = expanded && expandable) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        text = stringResource(R.string.feature_user_history_group_count, group.episodeCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                    )
                    group.episodes.forEach { entry ->
                        HistoryEpisodeRow(
                            history = entry,
                            onResume = { onResume(entry) },
                            onDelete = { onDeleteEpisode(entry) },
                        )
                    }
                }
            }
        }
    }
}

/** 展开后的单集行：集名 + 进度 + 相对时间 + 删除（只删这一集） */
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
                .padding(start = 12.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
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
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = BgmIcons.Delete,
                contentDescription = stringResource(R.string.feature_user_history_cd_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
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
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier =
            Modifier
                .size(width = HISTORY_COVER_WIDTH, height = HISTORY_COVER_HEIGHT),
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
