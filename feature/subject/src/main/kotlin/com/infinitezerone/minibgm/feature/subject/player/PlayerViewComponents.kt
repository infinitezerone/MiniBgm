package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.CoverPlaceholder
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.toEpisodeLabel
import com.infinitezerone.minibgm.feature.subject.R
import java.util.Locale

/**
 * 格式化时间毫秒为 mm:ss 或 hh:mm:ss 格式
 */
internal fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / 1000L
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/**
 * 嗅探加载中视图
 */
@Composable
internal fun PlayerResolvingView(
    sourceName: String,
    attempt: Int = 0,
    attemptTotal: Int = 0,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        ResolvingIndicator(sourceName = sourceName, attempt = attempt, attemptTotal = attemptTotal)
    }
}

/**
 * 切换源/切集时的半透明嗅探遮罩：叠在**仍在播放**的旧画面上，而不是整块黑，
 * 让用户看得到“正在换源”而不只是“卡住”。
 */
@Composable
internal fun PlayerResolvingOverlay(
    sourceName: String,
    attempt: Int = 0,
    attemptTotal: Int = 0,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        ResolvingIndicator(
            sourceName = sourceName,
            attempt = attempt,
            attemptTotal = attemptTotal,
            prefix = stringResource(R.string.feature_subject_player_switching_source),
        )
    }
}

@Composable
private fun ResolvingIndicator(
    sourceName: String,
    attempt: Int,
    attemptTotal: Int,
    prefix: String = stringResource(R.string.feature_subject_player_sniffing_from_prefix),
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(40.dp),
        )
        val sniffing =
            if (attemptTotal > 0) {
                stringResource(R.string.feature_subject_player_sniffing_stream_with_attempt, attempt, attemptTotal)
            } else {
                stringResource(R.string.feature_subject_player_sniffing_stream)
            }
        Text(
            text = "$prefix【$sourceName】$sniffing",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
    }
}

/**
 * 剧集标题与看过标记
 */
@Composable
internal fun PlayerHeaderInfo(
    subjectName: String,
    episodeTitle: String,
    isWatched: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text = subjectName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = episodeTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isWatched) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ) {
                    Text(
                        text = stringResource(R.string.feature_subject_player_watched),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * 竖屏播放页顶部番剧条目卡片：海报 + 标题 + 评分/年份/收藏状态 + 跳转详情页箭头
 */
@Composable
internal fun PlayerSubjectBannerCard(
    subjectName: String,
    coverUrl: String,
    score: Double,
    airDate: String,
    collectionType: Int?,
    onSubjectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onSubjectClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                url = coverUrl,
                contentDescription = subjectName,
                modifier = Modifier.width(38.dp),
                cornerRadius = 8.dp,
                placeholder = CoverPlaceholder.Subject,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (score > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = BgmIcons.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp),
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f", score),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    if (airDate.isNotBlank()) {
                        Text(
                            text = airDate.take(4) + "年",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (collectionType != null) {
                        val statusLabel = CollectionType.fromValue(collectionType).label
                        Text(
                            text = "· $statusLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = BgmIcons.KeyboardArrowRight,
                contentDescription = stringResource(R.string.feature_subject_player_view_subject_detail),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 本集标题与快捷操作栏（打卡、吐槽入口、剧情简介展开）
 */
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
 * 屡试屡败的源提示
 */
@Composable
internal fun SourceFailureHint(failureCount: Int) {
    if (failureCount <= 0) return
    Spacer(modifier = Modifier.width(6.dp))
    Text(
        text = stringResource(R.string.feature_subject_player_failures_count, failureCount),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/**
 * 播放源切换选择器
 */
@Composable
internal fun PlayerSourceSelector(
    sources: List<PlayerSourceTab>,
    sourceFailureCounts: Map<String, Int>,
    selectedIndex: Int,
    onSelectSource: (Int) -> Unit,
    onRequestOpenSources: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onManageRules: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_subject_player_sources),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            // 「播放源管理」优先跳转规则管理页；未接线时退回来源入口（如 AI 找源）
            val manageRulesAction = onManageRules ?: onRequestOpenSources
            if (manageRulesAction != null) {
                TextButton(
                    onClick = manageRulesAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.feature_subject_player_manage_sources),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(sources) { index, source ->
                val isSelected = index == selectedIndex
                val failureCount = sourceFailureCounts[source.id] ?: 0
                val isFailing = failureCount > 0
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectSource(index) },
                    modifier = if (isFailing && !isSelected) Modifier.alpha(0.65f) else Modifier,
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = source.nameRes?.let { stringResource(it) } ?: source.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            SourceFailureHint(sourceFailureCounts[source.id] ?: 0)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (source.isDirect) BgmIcons.CloudQueue else BgmIcons.TravelExplore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                )
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
                IconButton(
                    onClick = onToggleViewMode,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (isListView) BgmIcons.GridView else BgmIcons.ViewList,
                        contentDescription =
                            stringResource(
                                if (isListView) {
                                    R.string.feature_subject_player_view_mode_grid
                                } else {
                                    R.string.feature_subject_player_view_mode_list
                                },
                            ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
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
 * 暂无直链时的友好占位与向导入口
 */
@Composable
internal fun PlayerEmptyView(
    subjectName: String,
    epLabel: String,
    errorMessage: String? = null,
    onBackClick: () -> Unit,
    onRetry: () -> Unit = {},
    onNextSource: (() -> Unit)? = null,
    onRequestOpenSources: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = BgmIcons.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(64.dp),
            )
            Text(
                text =
                    if (errorMessage != null) {
                        stringResource(R.string.feature_subject_player_unresolved_stream)
                    } else {
                        stringResource(R.string.feature_subject_player_no_online_stream)
                    },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text =
                    if (errorMessage != null) {
                        errorMessage
                    } else {
                        stringResource(R.string.feature_subject_player_no_stream_hint, subjectName, epLabel)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBackClick) {
                    Text(stringResource(R.string.feature_subject_cd_back), color = Color.White)
                }
                if (errorMessage != null) {
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.feature_subject_player_retry_sniff))
                    }
                    if (onNextSource != null) {
                        OutlinedButton(onClick = onNextSource) {
                            Text(stringResource(R.string.feature_subject_player_switch_next_source), color = Color.White)
                        }
                    }
                } else if (onRequestOpenSources != null) {
                    Button(onClick = onRequestOpenSources) {
                        Text(stringResource(R.string.feature_subject_source_ai_search))
                    }
                }
            }
        }
    }
}
