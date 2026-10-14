package com.infinitezerone.minibgm.feature.user.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.infinitezerone.minibgm.feature.user.R

/** 封面缩略图尺寸：3:4 海报比例，与收藏卡片的视觉重量错开一档 */
private val HISTORY_COVER_WIDTH = 52.dp
private val HISTORY_COVER_HEIGHT = 70.dp

/**
 * 观看记录单条：封面 + 番剧/分集 + 进度条。
 *
 * 整卡点击即按该分集续播（进度由播放器自己按 [PlaybackHistory.positionMs] 接管），
 * 右侧提供单条删除。删除按钮独立于卡片点击区，避免误删。
 */
@Composable
internal fun PlaybackHistoryCard(
    history: PlaybackHistory,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodeLabel =
        stringResource(
            R.string.feature_user_history_episode_label,
            history.episodeSort.toEpisodeLabel(),
        )
    val title = history.subjectName.ifBlank { history.episodeName }
    // 分集名常与「第 N 话」重复（正片多为默认命名），重复时不再叠一遍
    val subtitle =
        if (history.episodeName.isNotBlank() && history.episodeName != episodeLabel) {
            "$episodeLabel · ${history.episodeName}"
        } else {
            episodeLabel
        }

    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onResume,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
        ) {
            HistoryCover(url = history.coverUrl)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { history.progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = historyProgressText(history),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (history.isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = BgmIcons.Delete,
                    contentDescription = stringResource(R.string.feature_user_history_cd_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
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

/** 封面缩略图：无封面时退化为播放图标占位，不阻塞整行布局 */
@Composable
private fun HistoryCover(url: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier =
            Modifier
                .size(width = HISTORY_COVER_WIDTH, height = HISTORY_COVER_HEIGHT),
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = BgmIcons.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
