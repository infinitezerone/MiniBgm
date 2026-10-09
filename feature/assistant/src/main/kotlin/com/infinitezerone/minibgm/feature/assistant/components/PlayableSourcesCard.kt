package com.infinitezerone.minibgm.feature.assistant.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.PlayableEpisodeList
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.navigation.PlayerQueueEntry
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.feature.assistant.R

/**
 * 找源结果卡片：把工具返回的可播放清单按集数列出。
 *
 * DIRECT 条目点击后进内置播放器（连同解析出的请求头一起传递），PAGE 条目只能外部打开；
 * 最近播放失败（[PlaybackFailureStore] 归因）的地址在行内标出"打不开"与原因，仍可点击重试。
 */
@Composable
fun PlayableSourcesCard(
    sources: PlayableEpisodeList,
    onPlaySource: (PlayerRoute) -> Unit,
    modifier: Modifier = Modifier,
    failedReasons: Map<String, String> = emptyMap(),
) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.feature_assistant_playable_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text =
                        buildString {
                            append(
                                if (sources.title.isBlank()) {
                                    stringResource(R.string.feature_assistant_playable_default_title)
                                } else {
                                    sources.title
                                },
                            )
                            if (sources.source.isNotBlank()) append(" · ${sources.source}")
                            append(" · ")
                            append(
                                stringResource(
                                    R.string.feature_assistant_playable_episode_count,
                                    sources.episodes.size,
                                ),
                            )
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            sources.episodes.forEachIndexed { index, episode ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                }
                PlayableSourceRow(
                    episode = episode,
                    rowNumber = index + 1,
                    failureReason = failedReasons[episode.url],
                    onClick = {
                        if (episode.kind == PlaylistEntryKind.DIRECT) {
                            onPlaySource(episode.toPlayerRoute(sources))
                        } else {
                            uriHandler.openUri(episode.url)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayableSourceRow(
    episode: PlayableSource,
    rowNumber: Int,
    failureReason: String?,
    onClick: () -> Unit,
) {
    val playable = episode.kind == PlaylistEntryKind.DIRECT

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (playable) BgmIcons.Play else BgmIcons.OpenInNew,
            contentDescription =
                if (playable) {
                    stringResource(R.string.feature_assistant_playable_cd_play)
                } else {
                    stringResource(R.string.feature_assistant_playable_cd_open_external)
                },
            tint =
                when {
                    failureReason != null -> MaterialTheme.colorScheme.error
                    playable -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    if (episode.label.isBlank()) {
                        stringResource(R.string.feature_assistant_playable_row_number, rowNumber)
                    } else {
                        episode.label
                    },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    playbackSourceSubtitle(
                        episode.siteName.ifBlank { hostOf(episode.url) },
                        failureReason,
                        stringResource(R.string.feature_assistant_source_last_failed_format),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (failureReason != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text =
                when {
                    failureReason != null -> stringResource(R.string.feature_assistant_playable_action_unavailable)
                    playable -> stringResource(R.string.feature_assistant_playable_action_play)
                    else -> stringResource(R.string.feature_assistant_playable_action_open)
                },
            style = MaterialTheme.typography.labelMedium,
            color =
                if (failureReason != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            modifier = Modifier.width(40.dp),
        )
    }
}

/** 站点名副标题：叠加最近一次播放失败归因（与来源向导的展示口径一致） */
private fun playbackSourceSubtitle(
    base: String,
    failureReason: String?,
    failedFormat: String,
): String =
    if (failureReason == null) {
        base
    } else {
        String.format(java.util.Locale.getDefault(), failedFormat, base, failureReason)
    }

private fun PlayableSource.toPlayerRoute(sources: PlayableEpisodeList): PlayerRoute {
    val playable = sources.episodes.filter { it.kind == PlaylistEntryKind.DIRECT }
    return PlayerRoute(
        subjectId = sources.subjectId,
        episodeId = 0L,
        streamUrl = url,
        episodeName = label,
        subjectName = sources.title,
        episodeSort = if (episodeSort > 0f) episodeSort else 1f,
        requestHeaders = headers,
        // 整张清单的可播条目按序入队：播放器内支持连播与选集抽屉
        queue = playable.map { it.toQueueEntry() },
        startIndex = playable.indexOf(this).coerceAtLeast(0),
    )
}

private fun PlayableSource.toQueueEntry() =
    PlayerQueueEntry(
        streamUrl = url,
        label = label,
        episodeSort = if (episodeSort > 0f) episodeSort else 1f,
        requestHeaders = headers,
    )

/** 站点名缺失时用 URL 主机名兜底展示 */
private fun hostOf(url: String): String {
    val afterScheme = url.substringAfter("://", url)
    return afterScheme.substringBefore('/').substringBefore('?')
}
