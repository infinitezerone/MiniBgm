package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.designsystem.theme.WishOrange
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusAiringContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusDoingContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusAiringContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusDoingContainerColor
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeGroup
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.model.toEpisodeLabel
import com.infinitezerone.minibgm.feature.subject.R

/** 分集/曲目/章节列表头部栏：总数/打卡进度、列表/网格切换与续看播放源通栏卡片 */
@Composable
fun EpisodesSectionHeader(
    totalEpisodes: Int,
    watchedEpisodes: Int,
    subjectType: SubjectType,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayNext: (() -> Unit)? = null,
    onOpenSources: (() -> Unit)? = null,
    episodeSortDescending: Boolean = false,
    onToggleSort: (() -> Unit)? = null,
    nextUpEpisodeSort: Float? = null,
    onJumpToNextUp: (() -> Unit)? = null,
    group: EpisodeGroup = EpisodeGroup.MAIN,
    hasMoreEpisodes: Boolean = false,
    showPlaybackCard: Boolean = false,
) {
    val headerTitle =
        when (subjectType) {
            SubjectType.MUSIC -> stringResource(R.string.feature_subject_episodes_title_music)
            SubjectType.BOOK -> stringResource(R.string.feature_subject_episodes_title_book)
            SubjectType.GAME -> stringResource(R.string.feature_subject_episodes_title_game)
            SubjectType.ANIME, SubjectType.REAL -> stringResource(R.string.feature_subject_episodes_title_episodes)
        }

    val progressLabel =
        buildEpisodesProgressLabel(
            totalEpisodes = totalEpisodes,
            watchedEpisodes = watchedEpisodes,
            subjectType = subjectType,
            group = group,
            hasMoreEpisodes = hasMoreEpisodes,
        )

    val isEpisodeLike = subjectType == SubjectType.ANIME || subjectType == SubjectType.REAL
    val hasPlaybackEntry = isEpisodeLike && (onPlayNext != null || onOpenSources != null)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 第一行：纯净标题与控制栏（标题和进度在左侧充分展开，不被挤压遮挡；右侧仅放排序与网格切换）
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                if (progressLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (onToggleSort != null) {
                    IconButton(
                        onClick = onToggleSort,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.SwapVert,
                            contentDescription =
                                if (episodeSortDescending) {
                                    stringResource(R.string.feature_subject_episodes_sort_asc)
                                } else {
                                    stringResource(R.string.feature_subject_episodes_sort_desc)
                                },
                            tint =
                                if (episodeSortDescending) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                IconButton(
                    onClick = onToggleView,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isGridView) BgmIcons.FormatListNumbered else BgmIcons.GridView,
                        contentDescription =
                            if (isGridView) {
                                stringResource(R.string.feature_subject_episodes_view_list)
                            } else {
                                stringResource(R.string.feature_subject_episodes_view_grid)
                            },
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // 第二行：续看与播放源通栏卡片（按需展示，避免与顶层个人追番卡片重复）
        if (showPlaybackCard && hasPlaybackEntry) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(BgmShapes.medium)
                        .clickable {
                            onOpenSources?.invoke() ?: onPlayNext?.invoke() ?: Unit
                        },
                shape = BgmShapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // 左侧：续看分集与状态标签
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = BgmIcons.Play,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        if (nextUpEpisodeSort != null) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.feature_subject_episodes_continue_format,
                                        nextUpEpisodeSort.toEpisodeLabel(),
                                    ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = statusDoingContainerColor(),
                            ) {
                                Text(
                                    text = stringResource(R.string.feature_subject_episodes_status_to_watch),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = onStatusDoingContainerColor(),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.feature_subject_episodes_sources_and_rules),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // 右侧：播放源 / 播放下一集
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (onOpenSources != null) {
                            Surface(
                                onClick = onOpenSources,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = BgmIcons.Tv,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        text = stringResource(R.string.feature_subject_ep_sources),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }

                        if (onPlayNext != null) {
                            Surface(
                                onClick = onPlayNext,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = BgmIcons.Play,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Text(
                                        text = stringResource(R.string.feature_subject_episodes_play),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
                                imageVector = BgmIcons.Check,
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
    onOpenSources: (() -> Unit)? = null,
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
                    if (episode.isMain) {
                        val epLabel = episode.formattedNumber
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
                                    imageVector = BgmIcons.Check,
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
                    if (episode.isMain) {
                        stringResource(R.string.feature_subject_episode_format, episode.formattedNumber)
                    } else {
                        stringResource(R.string.feature_subject_episode_custom_format, group.label, episode.sort.toInt())
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
                                    text = stringResource(R.string.feature_subject_status_watched),
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
                                    text = stringResource(R.string.feature_subject_status_future),
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
                                    text = stringResource(R.string.feature_subject_status_watching),
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
                                    text = stringResource(R.string.feature_subject_status_unwatched),
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
                                imageVector = BgmIcons.DateRange,
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
                                text =
                                    if (isFuture) {
                                        stringResource(
                                            R.string.feature_subject_airdate_format,
                                            episode.airdate,
                                        )
                                    } else {
                                        episode.airdate
                                    },
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
                                imageVector = BgmIcons.Schedule,
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
                                    imageVector = if (isHot) BgmIcons.LocalFireDepartment else BgmIcons.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = if (isHot) WishOrange else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = stringResource(R.string.feature_subject_comments_short_format, episode.comment),
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
                if (!isFuture && (onPlayClick != null || onOpenSources != null)) {
                    val playDescription =
                        if (episode.isMain) {
                            stringResource(R.string.feature_subject_play_episode_format, episode.formattedNumber)
                        } else {
                            val group = EpisodeGroup.fromType(episode.type)
                            stringResource(R.string.feature_subject_play_custom_episode_format, group.label, episode.sort.toInt())
                        }
                    val handleAction = onPlayClick ?: onOpenSources
                    if (handleAction != null) {
                        FilledTonalIconButton(
                            onClick = handleAction,
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
                                imageVector = if (onPlayClick != null) BgmIcons.Play else BgmIcons.Tv,
                                contentDescription = playDescription,
                                modifier = Modifier.size(18.dp),
                            )
                        }
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
                        imageVector = if (isWatched) BgmIcons.Check else BgmIcons.CheckBorder,
                        contentDescription =
                            if (isWatched) {
                                stringResource(R.string.feature_subject_ep_unwatch_hint)
                            } else {
                                stringResource(R.string.feature_subject_ep_mark_watched_hint)
                            },
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
    hasProgress: Boolean = true,
    onEpisodeClick: ((Episode) -> Unit)? = null,
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
                    val isNextToWatch = isEpisodeNextToWatch(episode, watchedCount, hasProgress)
                    val label =
                        if (episode.isMain) {
                            episode.formattedNumber
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
                                    onClick = {
                                        if (onEpisodeClick != null) {
                                            onEpisodeClick(episode)
                                        } else {
                                            onToggleWatched(episode, !isWatched)
                                        }
                                    },
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
                                    imageVector = BgmIcons.Check,
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
                                    imageVector = BgmIcons.Play,
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
    if (!episode.isMain) return false
    val epNumber = episode.episodeInt
    return watchedCount >= epNumber && epNumber > 0
}

/**
 * 辅助方法：判断分集是否为当前待看/在看下一集（仅针对已播出的正篇，未播出的未来分集不可作为在看集）。
 * [hasProgress] 表示存在观看进度上下文（有条目收藏记录或已登录同步过进度）：
 * 没有任何进度数据（如未登录且未收藏）时，"第 N 话 == watchedCount + 1" 恒对第一话成立，
 * 会误标"在看"，因此必须显式传入 false。
 */
fun isEpisodeNextToWatch(
    episode: Episode,
    watchedCount: Int,
    hasProgress: Boolean = true,
    nowMillis: Long = TimeUtils.nowEpochMillis(),
): Boolean {
    if (!hasProgress) return false
    if (!episode.isMain) return false
    if (isEpisodeFutureAir(episode, nowMillis)) return false
    val epNumber = episode.episodeInt
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

/** 辅助方法：生成分集列表进度徽章文本 */
fun buildEpisodesProgressLabel(
    totalEpisodes: Int,
    watchedEpisodes: Int,
    subjectType: SubjectType,
    group: EpisodeGroup = EpisodeGroup.MAIN,
    hasMoreEpisodes: Boolean = false,
): String {
    val unit =
        when (subjectType) {
            SubjectType.MUSIC -> "首"
            SubjectType.BOOK -> "话"
            SubjectType.GAME -> "关"
            SubjectType.ANIME, SubjectType.REAL -> "话"
        }
    val verb =
        when (subjectType) {
            SubjectType.MUSIC -> "已听"
            SubjectType.BOOK -> "已读"
            SubjectType.GAME -> "已过"
            SubjectType.ANIME, SubjectType.REAL -> "已看"
        }

    return when {
        group == EpisodeGroup.SP ->
            if (totalEpisodes <= 0) {
                ""
            } else if (watchedEpisodes > 0) {
                "已看 $watchedEpisodes / 共 $totalEpisodes 篇"
            } else {
                "共 $totalEpisodes 篇"
            }

        group == EpisodeGroup.OP_ED ->
            if (totalEpisodes <= 0) "" else "共 $totalEpisodes 首"

        group == EpisodeGroup.OTHER ->
            if (totalEpisodes <= 0) "" else "共 $totalEpisodes 项"

        totalEpisodes > 0 ->
            if (watchedEpisodes > 0) {
                "$verb $watchedEpisodes / 全 $totalEpisodes $unit"
            } else {
                "全 $totalEpisodes $unit"
            }

        hasMoreEpisodes ->
            if (watchedEpisodes > 0) "$verb $watchedEpisodes $unit (更新中)" else "连载中"

        watchedEpisodes > 0 ->
            "$verb $watchedEpisodes $unit"

        else ->
            ""
    }
}

/** 概览页横向分集快捷滑轨（符合 Bilibili / 豆瓣 选集标准）：一行紧凑方块，带全集跳转入口 */
@Composable
fun EpisodeQuickRail(
    episodes: List<Episode>,
    watchedCount: Int,
    onEpisodeClick: (Episode) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasProgress: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_subject_episodes_select_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.feature_subject_episodes_all_count_arrow, episodes.size),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onViewAllClick)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items = episodes.take(24), key = { it.id }) { episode ->
                val isWatched = isEpisodeWatched(episode, watchedCount)
                val isFuture = isEpisodeFutureAir(episode)
                val isNextToWatch = isEpisodeNextToWatch(episode, watchedCount, hasProgress)
                val label =
                    if (episode.isMain) {
                        episode.formattedNumber
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
                            .size(46.dp)
                            .clip(cellShape)
                            .then(
                                if (cellBorder != null) Modifier.border(cellBorder, cellShape) else Modifier,
                            ).background(cellBackground)
                            .clickable { onEpisodeClick(episode) },
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
                                imageVector = BgmIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }

                    if (isNextToWatch) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(2.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.Play,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 最新播出或续看分集的吐槽高光卡片（置顶于分集 Tab 顶部，1 步穿透进当周热烈讨论） */
@Composable
fun SpotlightEpisodeTucaoCard(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (episode.comment >= 30) {
                                    WishOrange.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (episode.comment >= 30) BgmIcons.LocalFireDepartment else BgmIcons.ChatBubbleOutline,
                        contentDescription = null,
                        tint = if (episode.comment >= 30) WishOrange else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column {
                    val epLabel =
                        if (episode.isMain) {
                            stringResource(R.string.feature_subject_episode_format, episode.formattedNumber)
                        } else {
                            episode.guideLabel
                        }
                    Text(
                        text = stringResource(R.string.feature_subject_ep_highlight_banner_title, epLabel),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text =
                            if (episode.comment > 0) {
                                stringResource(R.string.feature_subject_ep_comments_count, episode.comment)
                            } else {
                                stringResource(R.string.feature_subject_ep_no_comments)
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = BgmIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
