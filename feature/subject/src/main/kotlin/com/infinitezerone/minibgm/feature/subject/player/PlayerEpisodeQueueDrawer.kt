package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.navigation.PlayerQueueEntry

/**
 * 全屏内选集抽屉：分集分段列表 + 播放源切换 + 自动连播开关。
 */
@Composable
internal fun PlayerEpisodeQueueDrawer(
    queue: List<PlayerQueueEntry>,
    currentIndex: Int,
    episodes: List<PlayerEpisodeItem>,
    selectedEpisodeSort: Float,
    sources: List<PlayerSourceTab>,
    sourceFailureCounts: Map<String, Int>,
    selectedSourceIndex: Int,
    autoNextEnabled: Boolean,
    onToggleAutoNext: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onSelectEpisode: (PlayerEpisodeItem) -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 剧集超过 30 话时分段展示（如 1-30, 31-60）
    val chunkSize = 30
    val chunks =
        remember(episodes) {
            if (episodes.size > chunkSize) {
                episodes.chunked(chunkSize)
            } else {
                emptyList()
            }
        }

    val initialChunkIndex =
        remember(episodes, selectedEpisodeSort) {
            val idx = episodes.indexOfFirst { it.sort == selectedEpisodeSort }
            if (idx >= 0 && chunks.isNotEmpty()) idx / chunkSize else 0
        }

    var selectedChunkIndex by remember(chunks) { mutableIntStateOf(initialChunkIndex) }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        modifier = modifier.width(320.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val totalCount = if (episodes.isNotEmpty()) episodes.size else queue.size
                Text(
                    text = "选集 · $totalCount 话",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "自动连播",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(checked = autoNextEnabled, onCheckedChange = { onToggleAutoNext() })
            }

            if (sources.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(sources) { index, source ->
                        val isSelected = index == selectedSourceIndex
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectSource(index) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(source.name)
                                    SourceFailureHint(sourceFailureCounts[source.id] ?: 0)
                                }
                            },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                        )
                    }
                }
            }

            // 分集分段 Tab（长篇动画）
            if (chunks.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(chunks) { index, chunkList ->
                        val isSelected = index == selectedChunkIndex
                        val startEp = chunkList.first().sort.toInt()
                        val endEp = chunkList.last().sort.toInt()
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedChunkIndex = index },
                            label = {
                                Text(
                                    text = "$startEp-$endEp",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            if (episodes.isNotEmpty()) {
                val displayEpisodes = if (chunks.isNotEmpty()) chunks.getOrElse(selectedChunkIndex) { episodes } else episodes
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(displayEpisodes) { _, ep ->
                        val selected = ep.sort == selectedEpisodeSort
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectEpisode(ep) }
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        } else {
                                            Color.Transparent
                                        },
                                    ).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = ep.sort.toInt().toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                            Text(
                                text = ep.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(queue) { index, entry ->
                        val selected = index == currentIndex
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectQueueIndex(index) }
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        } else {
                                            Color.Transparent
                                        },
                                    ).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                            Text(
                                text = entry.label.ifBlank { entry.episodeName.ifBlank { "第 ${index + 1} 条" } },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
