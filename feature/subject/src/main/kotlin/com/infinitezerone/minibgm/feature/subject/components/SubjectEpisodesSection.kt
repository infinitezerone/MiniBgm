package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.designsystem.theme.WishOrange
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusAiringContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusDoingContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusAiringContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusDoingContainerColor
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
    onOpenSources: (() -> Unit)? = null,
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
            modifier = Modifier.weight(1f, fill = false),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onOpenSources != null && (subjectType == SubjectType.ANIME || subjectType == SubjectType.REAL)) {
                Surface(
                    onClick = onOpenSources,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "播放源",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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
                border = null,
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
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
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun EpisodeListItem(
    episode: Episode,
    isWatched: Boolean,
    onClick: () -> Unit,
    onToggleWatched: () -> Unit,
    modifier: Modifier = Modifier,
    isNextToWatch: Boolean = false,
    onPlayClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val isFuture = remember(episode.airdate) { isEpisodeFutureAir(episode) }
    val cardShape = BgmShapes.large

    val cardBorder =
        when {
            isNextToWatch -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
            isWatched -> BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            isFuture -> BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        }

    val cardContainerColor =
        when {
            isNextToWatch -> MaterialTheme.colorScheme.surfaceContainerHigh
            isWatched -> MaterialTheme.colorScheme.surfaceContainerLow
            isFuture -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f)
            else -> MaterialTheme.colorScheme.surfaceContainerLowest
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(cardShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        shape = cardShape,
        border = cardBorder,
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. 左侧集数标牌 (Badge)
            val badgeContainerColor =
                when {
                    isWatched -> statusCollectContainerColor().copy(alpha = 0.65f)
                    isFuture -> statusAiringContainerColor().copy(alpha = 0.35f)
                    isNextToWatch -> statusDoingContainerColor()
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
            val badgeContentColor =
                when {
                    isWatched -> onStatusCollectContainerColor()
                    isFuture -> onStatusAiringContainerColor()
                    isNextToWatch -> onStatusDoingContainerColor()
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

            Surface(
                shape = BgmShapes.medium,
                color = badgeContainerColor,
                modifier = Modifier.widthIn(min = 46.dp).height(46.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    if (episode.type == 0) {
                        val epNum = if (episode.ep > 0f) episode.ep else episode.sort
                        val epLabel = epNum.toEpisodeLabel()
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = epLabel,
                                style =
                                    if (epLabel.length >
                                        3
                                    ) {
                                        MaterialTheme.typography.titleSmall
                                    } else {
                                        MaterialTheme.typography.titleMedium
                                    },
                                fontWeight = FontWeight.Bold,
                                color = badgeContentColor,
                                maxLines = 1,
                            )
                            if (isWatched) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = badgeContentColor,
                                    modifier = Modifier.size(11.dp),
                                )
                            }
                        }
                    } else {
                        val group = EpisodeGroup.fromType(episode.type)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = group.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeContentColor,
                                maxLines = 1,
                            )
                            Text(
                                text = "${episode.sort.toInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = badgeContentColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // 2. 中间信息区 (中文名/主标题、原名、状态胶囊、放送、时长、讨论)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 主标题：支持多行自适应展示（maxLines = 3），独占横向宽度避免与胶囊争抢造成严重省略截断
                val group = EpisodeGroup.fromType(episode.type)
                val fallbackTitle =
                    if (episode.type == 0) {
                        val num = if (episode.ep > 0f) episode.ep else episode.sort
                        "第 ${num.toEpisodeLabel()} 话"
                    } else {
                        "${group.label} ${episode.sort.toInt()}"
                    }
                val primaryTitle = episode.nameCn.ifBlank { episode.name.ifBlank { fallbackTitle } }
                Text(
                    text = primaryTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isNextToWatch || isWatched) FontWeight.Bold else FontWeight.SemiBold,
                    color =
                        when {
                            isWatched -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 次行：日文原名（若与中文译名不同且非空则展示，支持至多 2 行自适应折行）
                if (episode.nameCn.isNotBlank() && episode.name.isNotBlank() && episode.name != episode.nameCn) {
                    Text(
                        text = episode.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // 第三行：状态胶囊与放送日期、时长、讨论热度元信息（统一采用 FlowRow 排版，层次分明自适应换行）
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    when {
                        isWatched -> {
                            Surface(
                                shape = BgmShapes.extraSmall,
                                color = statusCollectContainerColor(),
                            ) {
                                Text(
                                    text = "已看",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = onStatusCollectContainerColor(),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                        isFuture -> {
                            Surface(
                                shape = BgmShapes.extraSmall,
                                color = statusAiringContainerColor(),
                            ) {
                                Text(
                                    text = "待播",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = onStatusAiringContainerColor(),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                        isNextToWatch -> {
                            Surface(
                                shape = BgmShapes.extraSmall,
                                color = statusDoingContainerColor(),
                            ) {
                                Text(
                                    text = "在看",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = onStatusDoingContainerColor(),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                        else -> {
                            Surface(
                                shape = BgmShapes.extraSmall,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = "未看",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }

                    if (episode.airdate.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint =
                                    if (isFuture) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                            Text(
                                text = if (isFuture) "放送: ${episode.airdate}" else episode.airdate,
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (isFuture) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                fontWeight = if (isFuture) FontWeight.Medium else FontWeight.Normal,
                            )
                        }
                    }

                    if (episode.duration.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = episode.duration,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (episode.comment > 0) {
                        val isHot = episode.comment >= 50
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color =
                                if (isHot) {
                                    WishOrange.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
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

            // 3. 右侧快捷操作区 (播放入口 + 打卡按钮)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 仅当分集已放送时展示播放入口，避免对未播分集展示虚假播放按钮
                if (!isFuture && onPlayClick != null) {
                    val epNum = if (episode.ep > 0f) episode.ep else episode.sort
                    val playDescription =
                        if (episode.type == 0) {
                            "播放第 ${epNum.toEpisodeLabel()} 话"
                        } else {
                            val group = EpisodeGroup.fromType(episode.type)
                            "播放${group.label} ${episode.sort.toInt()}"
                        }
                    FilledTonalIconButton(
                        onClick = onPlayClick,
                        modifier = Modifier.size(36.dp),
                        colors =
                            if (isNextToWatch) {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            } else {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                )
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = playDescription,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onToggleWatched,
                    modifier = Modifier.size(36.dp),
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
                        contentDescription = if (isWatched) "已看过，点击取消打卡" else "未看，点击标记为已看",
                        modifier = Modifier.size(18.dp),
                    )
                }
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
                    val isFuture = isEpisodeFutureAir(episode)
                    val isNextToWatch = isEpisodeNextToWatch(episode, watchedCount)
                    val label =
                        if (episode.type == 0) {
                            val num = if (episode.ep > 0f) episode.ep else episode.sort
                            num.toEpisodeLabel()
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
                    val cellShape = BgmShapes.small
                    val cellBorder =
                        when {
                            isNextToWatch -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            isFuture -> BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            else -> null
                        }
                    val cellBackground =
                        when {
                            isWatched -> MaterialTheme.colorScheme.primaryContainer
                            isNextToWatch -> statusDoingContainerColor()
                            isFuture -> MaterialTheme.colorScheme.surfaceContainerLowest
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    val cellContentColor =
                        when {
                            isWatched -> MaterialTheme.colorScheme.onPrimaryContainer
                            isNextToWatch -> onStatusDoingContainerColor()
                            isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(cellShape)
                                .then(
                                    if (cellBorder != null) {
                                        Modifier.border(cellBorder, cellShape)
                                    } else {
                                        Modifier
                                    },
                                ).background(cellBackground)
                                .combinedClickable(
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
                                fontWeight = if (isNextToWatch) FontWeight.ExtraBold else FontWeight.Bold,
                                color = cellContentColor,
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

                        if (isNextToWatch) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(10.dp),
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

/** 辅助方法：判断分集是否已看过（非正篇 SP/OP/ED 不受条目全局正篇观看数 epStatus 判定） */
fun isEpisodeWatched(
    episode: Episode,
    watchedCount: Int,
): Boolean {
    if (episode.type != 0) return false
    val epNumber = if (episode.ep > 0f) episode.ep.toInt() else episode.sort.toInt()
    return watchedCount >= epNumber && epNumber > 0
}

/** 辅助方法：判断分集是否为当前待看/在看下一集（仅针对已播出的正篇，未播出的未来分集不可作为在看集） */
fun isEpisodeNextToWatch(
    episode: Episode,
    watchedCount: Int,
    nowMillis: Long = TimeUtils.nowEpochMillis(),
): Boolean {
    if (episode.type != 0) return false
    if (isEpisodeFutureAir(episode, nowMillis)) return false
    val epNumber = if (episode.ep > 0f) episode.ep.toInt() else episode.sort.toInt()
    return epNumber == watchedCount + 1 && epNumber > 0
}

/** 辅助方法：判断分集是否为未来待播分集 */
fun isEpisodeFutureAir(
    episode: Episode,
    nowMillis: Long = TimeUtils.nowEpochMillis(),
): Boolean {
    val airMillis = TimeUtils.epochMillisOfIso(episode.airdate) ?: return false
    return airMillis > nowMillis
}

/** 格式化分集话数编号 */
fun Float.toEpisodeLabel(): String {
    if (this <= 0f) return "1"
    val whole = toInt()
    return if (this == whole.toFloat()) whole.toString() else toString()
}
