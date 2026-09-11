package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.WishOrange
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.SubjectType

/** 分集/曲目/章节列表头部栏：总数/打卡进度与列表/网格切换 */
@Composable
fun EpisodesSectionHeader(
    totalEpisodes: Int,
    watchedEpisodes: Int,
    subjectType: SubjectType,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val headerTitle =
        when (subjectType) {
            SubjectType.MUSIC -> "曲目列表"
            SubjectType.BOOK -> "章节与卷册"
            SubjectType.GAME -> "关卡与章节"
            SubjectType.ANIME, SubjectType.REAL -> "分集列表"
        }

    val progressLabel =
        when (subjectType) {
            SubjectType.MUSIC -> "已听 $watchedEpisodes / 全 $totalEpisodes 首"
            SubjectType.BOOK -> "已读 $watchedEpisodes / 全 $totalEpisodes 话"
            SubjectType.GAME -> "已过 $watchedEpisodes / 全 $totalEpisodes 关"
            SubjectType.ANIME, SubjectType.REAL -> "已看 $watchedEpisodes / 全 $totalEpisodes 话"
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (totalEpisodes > 0) {
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = onToggleView) {
            Icon(
                imageVector = if (isGridView) Icons.Filled.FormatListNumbered else Icons.Filled.GridView,
                contentDescription = if (isGridView) "切换为列表视图" else "切换为网格视图",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 分集类型分组枚举 */
enum class EpisodeGroup(
    val label: String,
) {
    MAIN("本篇"),
    SP("特别篇"),
    OP_ED("OP/ED"),
    OTHER("其他"),
    ;

    companion object {
        fun fromType(type: Int): EpisodeGroup =
            when (type) {
                0 -> MAIN
                1 -> SP
                2, 3 -> OP_ED
                else -> OTHER
            }
    }
}

/** 分集类型筛选 Chip 栏 */
@Composable
fun EpisodeGroupFilterChips(
    availableGroups: List<EpisodeGroup>,
    groupedEpisodes: Map<EpisodeGroup, List<Episode>>,
    selectedGroup: EpisodeGroup,
    onGroupSelected: (EpisodeGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        items(items = availableGroups, key = { it.name }) { group ->
            val count = groupedEpisodes[group]?.size ?: 0
            val isSelected = group == selectedGroup
            FilterChip(
                selected = isSelected,
                onClick = { onGroupSelected(group) },
                label = { Text("${group.label} ($count)") },
                leadingIcon =
                    if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        null
                    },
            )
        }
    }
}

/** 分集列表项（列表模式） */
@Composable
fun EpisodeListItem(
    episode: Episode,
    isWatched: Boolean,
    onClick: () -> Unit,
    onToggleWatched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isWatched) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color =
                    if (isWatched) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
            ) {
                val group = EpisodeGroup.fromType(episode.type)
                val epLabel =
                    if (episode.type == 0) {
                        "第 ${episode.ep.toEpisodeLabel()} 话"
                    } else {
                        "${group.label} ${episode.sort.toInt()}"
                    }
                Text(
                    text = epLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (isWatched) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isWatched) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitleParts =
                    buildList {
                        if (episode.airdate.isNotBlank()) add("放送：${episode.airdate}")
                        if (episode.duration.isNotBlank()) add(episode.duration)
                    }
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (subtitleParts.isNotEmpty()) {
                        Text(
                            text = subtitleParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (episode.comment > 0) {
                        val isHot = episode.comment >= 50
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color =
                                if (isHot) {
                                    WishOrange.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Icon(
                                    imageVector = if (isHot) Icons.Filled.LocalFireDepartment else Icons.Filled.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = if (isHot) WishOrange else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = "${episode.comment} 吐槽",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isHot) WishOrange else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            FilledTonalIconButton(
                onClick = onToggleWatched,
                colors =
                    if (isWatched) {
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
            ) {
                Icon(
                    imageVector = if (isWatched) Icons.Filled.Check else Icons.Outlined.Check,
                    contentDescription = if (isWatched) "已看过，点击取消打卡" else "未看，点击打卡",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 分集网格布局（网格模式） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeGrid(
    episodes: List<Episode>,
    watchedCount: Int,
    onToggleWatched: (episode: Episode, isWatched: Boolean) -> Unit,
    onEpisodeLongClick: (Episode) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 6,
) {
    val columnCount = columns.coerceAtLeast(1)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        episodes.chunked(columnCount).forEach { rowEpisodes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowEpisodes.forEach { episode ->
                    val isWatched = isEpisodeWatched(episode, watchedCount)
                    val label =
                        if (episode.type == 0) {
                            episode.ep.toEpisodeLabel()
                        } else {
                            val prefix =
                                when (episode.type) {
                                    1 -> "SP"
                                    2 -> "OP"
                                    3 -> "ED"
                                    else -> "E"
                                }
                            "$prefix${episode.sort.toInt()}"
                        }
                    val cellShape = RoundedCornerShape(8.dp)
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(cellShape)
                                .background(
                                    if (isWatched) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                ).then(
                                    if (isWatched) {
                                        Modifier
                                    } else {
                                        Modifier.border(
                                            BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                            cellShape,
                                        )
                                    },
                                ).combinedClickable(
                                    onClick = { onToggleWatched(episode, !isWatched) },
                                    onLongClick = { onEpisodeLongClick(episode) },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color =
                                    if (isWatched) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                            if (isWatched) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }

                        if (episode.comment >= 100) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(WishOrange),
                            )
                        }
                    }
                }
                // 补齐末行空位保持对齐
                repeat(columnCount - rowEpisodes.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 辅助方法：判断分集是否已看过 */
fun isEpisodeWatched(
    episode: Episode,
    watchedCount: Int,
): Boolean {
    val epNumber = if (episode.ep > 0f) episode.ep.toInt() else episode.sort.toInt()
    return watchedCount >= epNumber && epNumber > 0
}

/** 格式化分集话数编号 */
fun Float.toEpisodeLabel(): String {
    if (this <= 0f) return "1"
    val whole = toInt()
    return if (this == whole.toFloat()) whole.toString() else toString()
}
