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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.model.PlayableEpisodeList
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.navigation.PlayerRoute

/**
 * 找源结果卡片：把工具返回的可播放清单按集数列出。
 *
 * DIRECT 条目点击后进内置播放器（连同解析出的请求头一起传递），PAGE 条目只能外部打开。
 */
@Composable
fun PlayableSourcesCard(
    sources: PlayableEpisodeList,
    onPlaySource: (PlayerRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(
                    text = "可播放清单",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text =
                        buildString {
                            append(sources.title.ifBlank { "本条目" })
                            if (sources.source.isNotBlank()) append(" · ${sources.source}")
                            append(" · ${sources.episodes.size} 条")
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
            imageVector = if (playable) Icons.Filled.PlayArrow else Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = if (playable) "播放" else "外部打开",
            tint = if (playable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.label.ifBlank { "第 ${episode.episodeSort.toInt()} 条" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = episode.siteName.ifBlank { hostOf(episode.url) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (playable) "播放" else "打开",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(40.dp),
        )
    }
}

private fun PlayableSource.toPlayerRoute(sources: PlayableEpisodeList) =
    PlayerRoute(
        subjectId = sources.subjectId,
        episodeId = 0L,
        streamUrl = url,
        episodeName = label,
        subjectName = sources.title,
        episodeSort = if (episodeSort > 0f) episodeSort else 1f,
        requestHeaders = headers,
    )

/** 站点名缺失时用 URL 主机名兜底展示 */
private fun hostOf(url: String): String {
    val afterScheme = url.substringAfter("://", url)
    return afterScheme.substringBefore('/').substringBefore('?')
}
