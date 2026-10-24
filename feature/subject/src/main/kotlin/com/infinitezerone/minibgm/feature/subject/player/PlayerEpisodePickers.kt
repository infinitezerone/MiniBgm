package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.component.BgmGhostIconButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.formatScore
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.toEpisodeLabel
import com.infinitezerone.minibgm.feature.subject.R

/**
 * 本集标题与快捷操作栏（打卡、吐槽入口、剧情简介展开）
 */
private const val BILI_EPISODE_CHUNK_SIZE = 30

@Composable
internal fun PlayerEpisodeActionBar(
    epLabel: String,
    episodeName: String,
    isWatched: Boolean,
    commentCount: Int,
    desc: String,
    airdate: String,
    onToggleWatched: () -> Unit,
    onEpisodeDetailClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var isDescExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        // 第一行：标题 + 快捷打卡 + 吐槽入口
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = epLabel + if (episodeName.isNotBlank()) " · $episodeName" else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            // 打卡按钮
            FilterChip(
                selected = isWatched,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleWatched()
                },
                label = {
                    Text(
                        text =
                            if (isWatched) {
                                stringResource(R.string.feature_subject_player_watched)
                            } else {
                                stringResource(R.string.feature_subject_player_mark_as_watched)
                            },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isWatched) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = BgmIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )

            // 吐槽入口
            if (onEpisodeDetailClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                FilledTonalButton(
                    onClick = onEpisodeDetailClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (commentCount > 0) "$commentCount" else stringResource(R.string.feature_subject_player_episode_comments),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        // 剧情简介展开/收起
        if (desc.isNotBlank() || airdate.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { isDescExpanded = !isDescExpanded }
                        .padding(vertical = 2.dp),
            ) {
                Text(
                    text =
                        if (airdate.isNotBlank()) {
                            stringResource(R.string.feature_subject_player_airdate, airdate)
                        } else {
                            stringResource(R.string.feature_subject_player_episode_desc_title)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (desc.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isDescExpanded) BgmIcons.KeyboardArrowUp else BgmIcons.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (isDescExpanded && desc.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = desc.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}

/**
 * 选集网格标题行与分页分段
 */
@Composable
internal fun EpisodeSectionHeader(
    episodeCount: Int,
    autoNextEnabled: Boolean,
    onToggleAutoNext: () -> Unit,
    isListView: Boolean = false,
    onToggleViewMode: (() -> Unit)? = null,
    paginationChunks: List<String> = emptyList(),
    selectedChunkIndex: Int = 0,
    onSelectChunk: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_subject_player_episodes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (episodeCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.feature_subject_player_total_episodes_count, episodeCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (onToggleViewMode != null) {
                BgmGhostIconButton(
                    onClick = onToggleViewMode,
                    icon = if (isListView) BgmIcons.GridView else BgmIcons.ViewList,
                    contentDescription =
                        stringResource(
                            if (isListView) {
                                R.string.feature_subject_player_view_mode_grid
                            } else {
                                R.string.feature_subject_player_view_mode_list
                            },
                        ),
                    modifier = Modifier.size(32.dp),
                    iconSize = 18.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onToggleAutoNext),
            ) {
                Text(
                    text = stringResource(R.string.feature_subject_player_auto_play_next),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = autoNextEnabled,
                    onCheckedChange = { onToggleAutoNext() },
                )
            }
        }

        // 分集很多时展示分段 Tab（例如 1-30, 31-60）
        if (paginationChunks.size > 1) {
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(paginationChunks) { index, chunkLabel ->
                    val isSelected = index == selectedChunkIndex
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectChunk(index) },
                        label = {
                            Text(
                                text = chunkLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
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
    }
}

/**
 * 分集网格卡片
 */
@Composable
internal fun EpisodeGridCard(
    episode: PlayerEpisodeItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortLabel =
        if (episode.type == 0) {
            if (episode.sort == episode.sort.toInt().toFloat()) {
                episode.sort.toInt().toString()
            } else {
                episode.sort.toString()
            }
        } else {
            "SP${episode.sort.toInt()}"
        }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        contentColor =
            if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        modifier = modifier.fillMaxWidth().height(48.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(2.dp),
            ) {
                Text(
                    text = sortLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
                if (isSelected) {
                    Text(
                        text = stringResource(R.string.feature_subject_player_playing),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    )
                }
            }
            if (episode.isWatched) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            ),
                )
            }
        }
    }
}

/**
 * 选集列表模式卡片（展示分集序号、中文标题、日文原名、吐槽数、已看标记）
 */
@Composable
internal fun EpisodeListCard(
    episode: PlayerEpisodeItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        contentColor =
            if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sortText = if (episode.type == 0) "第 ${episode.sort.toEpisodeLabel()} 话" else "SP ${episode.sort.toInt()}"
            Text(
                text = sortText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                val title = episode.nameCn.ifBlank { episode.name }
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (episode.nameCn.isNotBlank() && episode.name.isNotBlank() && episode.name != episode.nameCn) {
                    Text(
                        text = episode.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (episode.commentCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = BgmIcons.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${episode.commentCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (episode.isWatched) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = stringResource(R.string.feature_subject_player_watched),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/**
 * 竖屏分集完整选择底栏（Material 3 [BgmModalBottomSheet]）
 *
 * 点击选集栏右侧"全部 >"呼出，替代原先在常驻区原地撑大的网格，杜绝挤占主布局。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerEpisodesBottomSheet(
    episodes: List<PlayerEpisodeItem>,
    selectedEpisodeSort: Float,
    selectedEpisodeId: Long,
    onSelectEpisode: (PlayerEpisodeItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    totalEpisodes: Int = 0,
    hasMoreEpisodes: Boolean = false,
    isLoadingMoreEpisodes: Boolean = false,
    onLoadMoreEpisodes: () -> Unit = {},
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val chunks =
        remember(episodes) {
            val byType = episodes.groupBy { it.type }
            val result = mutableListOf<List<PlayerEpisodeItem>>()
            byType.toSortedMap().forEach { (_, list) ->
                result.addAll(list.chunked(BILI_EPISODE_CHUNK_SIZE))
            }
            if (result.size > 1) result else emptyList()
        }
    val selectedIndex =
        remember(episodes, selectedEpisodeSort, selectedEpisodeId) {
            val idx =
                episodes.indexOfFirst {
                    it.sort == selectedEpisodeSort && (selectedEpisodeId == 0L || it.id == 0L || it.id == selectedEpisodeId)
                }
            if (idx >= 0) idx else 0
        }
    var selectedChunkIndex by remember(chunks) {
        val targetEp = episodes.getOrNull(selectedIndex)
        val chunkIdx =
            if (targetEp != null && chunks.isNotEmpty()) {
                chunks
                    .indexOfFirst { chunk ->
                        chunk.any { (targetEp.id > 0 && it.id == targetEp.id) || (it.sort == targetEp.sort && it.type == targetEp.type) }
                    }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }
        mutableIntStateOf(chunkIdx)
    }
    val displayEpisodes =
        if (chunks.isNotEmpty()) {
            chunks.getOrElse(selectedChunkIndex) { episodes }
        } else {
            episodes
        }

    BgmModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.feature_subject_player_episodes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val count = if (totalEpisodes > 0) totalEpisodes else episodes.size
                if (count > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.feature_subject_player_total_episodes_count, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isLoadingMoreEpisodes) {
                        Spacer(modifier = Modifier.width(6.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (chunks.size > 1) {
                val paginationLabels =
                    remember(chunks) {
                        chunks.map { list ->
                            val prefix = if (list.first().type == 1) "SP " else ""
                            "$prefix${list.first().sort.toInt()}-${list.last().sort.toInt()}"
                        }
                    }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(paginationLabels) { index, chunkLabel ->
                        BiliTogglePill(
                            label = chunkLabel,
                            selected = index == selectedChunkIndex,
                            onClick = { selectedChunkIndex = index },
                        )
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 5,
            ) {
                displayEpisodes.forEach { ep ->
                    val isSelected =
                        ep.sort == selectedEpisodeSort && (selectedEpisodeId == 0L || ep.id == 0L || ep.id == selectedEpisodeId)
                    EpisodeGridCard(
                        episode = ep,
                        isSelected = isSelected,
                        onClick = {
                            onSelectEpisode(ep)
                            onDismiss()
                        },
                        modifier = Modifier.width(60.dp),
                    )
                }
            }

            if (hasMoreEpisodes) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onLoadMoreEpisodes)
                            .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoadingMoreEpisodes) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.feature_subject_load_more_episodes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 竖屏播放页 - 当前分集标题、核心操作栏、剧情梗概与元信息卡片
 */
@Composable
internal fun PlayerEpisodeOverviewCard(
    epLabel: String,
    episodeName: String,
    airDate: String,
    description: String,
    isWatched: Boolean,
    collectionType: Int?,
    onToggleWatched: () -> Unit,
    onToggleFollow: () -> Unit,
    onRequestOpenSources: (() -> Unit)?,
    subjectName: String = "",
    score: Double = 0.0,
    onSubjectClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isDescExpanded by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 1. 番剧标题（轻量跳转入口）
        if (subjectName.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = onSubjectClick != null) { onSubjectClick?.invoke() }
                        .padding(vertical = 2.dp),
            ) {
                Text(
                    text = subjectName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (onSubjectClick != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = BgmIcons.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.feature_subject_player_view_subject_detail),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // 2. 标题与分集徽标
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            ) {
                Text(
                    text = epLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Text(
                text = episodeName.ifBlank { epLabel },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // 3. 播映与评分元信息
        val metaParts =
            buildList {
                if (airDate.isNotBlank()) {
                    add(stringResource(R.string.feature_subject_player_airdate, airDate))
                }
                if (score > 0.0) {
                    add(stringResource(R.string.feature_subject_rating_score_stars, score.formatScore()))
                }
            }
        if (metaParts.isNotEmpty()) {
            Text(
                text = metaParts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }

        // 4. 核心轻量操作胶囊行（打卡、追番、AI 找源）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BiliTogglePill(
                label =
                    if (isWatched) {
                        stringResource(R.string.feature_subject_player_watched)
                    } else {
                        stringResource(R.string.feature_subject_player_mark_as_watched)
                    },
                selected = isWatched,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleWatched()
                },
                leadingIcon = if (isWatched) BgmIcons.Check else null,
                height = 32.dp,
                textStyle = MaterialTheme.typography.labelMedium,
                contentPadding = PaddingValues(horizontal = 12.dp),
            )

            BiliFollowPill(
                collectionType = collectionType,
                onClick = onToggleFollow,
            )

            if (onRequestOpenSources != null) {
                BiliPill(
                    label = stringResource(R.string.feature_subject_source_ai_search),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    leadingIcon = BgmIcons.AutoAwesome,
                    onClick = onRequestOpenSources,
                    height = 32.dp,
                    textStyle = MaterialTheme.typography.labelMedium,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                )
            }
        }

        // 5. 剧情简介（去盒子化：自然流式段落与折叠开关）
        if (description.isNotBlank()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isDescExpanded = !isDescExpanded }
                        .padding(vertical = 2.dp),
            ) {
                Text(
                    text = description.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    maxLines = if (isDescExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text =
                            if (isDescExpanded) {
                                stringResource(R.string.feature_subject_player_collapse)
                            } else {
                                stringResource(R.string.feature_subject_expand)
                            },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (isDescExpanded) BgmIcons.KeyboardArrowUp else BgmIcons.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}
