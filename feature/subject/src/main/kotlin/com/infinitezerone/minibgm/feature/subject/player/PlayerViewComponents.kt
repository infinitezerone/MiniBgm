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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.CoverPlaceholder
import com.infinitezerone.minibgm.core.designsystem.component.formatScore
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.toEpisodeLabel
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.components.CommentSortOrder
import com.infinitezerone.minibgm.feature.subject.components.episodeCommentsSection
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

private const val BILI_EPISODE_CHUNK_SIZE = 30

/**
 * 播放页主信息条：当前分集标题 (左) + [已看] 与 [追番] 胶囊 (右)；
 * 第二行显示番剧名 (可点跳详情)、评分、首播年份及追番状态。
 *
 * 合并原先分散的番剧标题栏与单集打卡栏，将追番核心动作（看过打卡、追番状态）
 * 提至第一视觉焦点，常驻显示在播放器正下方。
 */
@Composable
internal fun BiliSubjectTitleBar(
    subjectName: String,
    epLabel: String,
    episodeName: String,
    score: Double,
    airDate: String,
    collectionType: Int?,
    isWatched: Boolean,
    onSubjectClick: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleWatched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val currentEpTitle =
        if (episodeName.isNotBlank()) {
            "$epLabel · $episodeName"
        } else {
            epLabel.ifBlank { subjectName }
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentEpTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
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
                leadingIcon = BgmIcons.Check,
            )
            Spacer(modifier = Modifier.width(6.dp))
            BiliFollowPill(
                collectionType = collectionType,
                onClick = onToggleFollow,
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (subjectName.isNotBlank() && currentEpTitle != subjectName) {
                Text(
                    text = subjectName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onSubjectClick),
                )
            }
            if (score > 0.0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = score.formatScore(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
                Text(
                    text = CollectionType.fromValue(collectionType).label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 将技术向的规则名称（如域名 cj.lziapi.com）清洗为用户友好的线路名（如 线路 1 · lziapi），
 * 若规则本身已有中文或自定义友好名称则予以保留。
 */
internal fun formatSourceDisplayName(
    rawName: String,
    index: Int,
    isDirect: Boolean,
): String {
    if (isDirect) return rawName
    val trimmed = rawName.trim()
    val isDomainOrUrl = trimmed.contains(".") || trimmed.startsWith("http://") || trimmed.startsWith("https://")
    if (!isDomainOrUrl) {
        return trimmed.ifBlank { "线路 ${index + 1}" }
    }
    val host =
        trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")
    val cleanHost =
        host
            .removePrefix("www.")
            .removePrefix("cj.")
            .removePrefix("api.")
            .substringBefore(".")
    return if (cleanHost.isNotBlank()) {
        "线路 ${index + 1} · $cleanHost"
    } else {
        "线路 ${index + 1}"
    }
}

/**
 * 竖屏播放页 - 播放线路分区：
 * 采用与选集区一致的标准分区排版（左侧标题+线路数，右侧管理入口），
 * 单项采用圆角卡片呈现，彻底替换原先类似开发者悬浮调试工具条的单行紧缩样式。
 */
@Composable
internal fun BiliPlayerSourceBar(
    sources: List<PlayerSourceTab>,
    sourceFailureCounts: Map<String, Int>,
    selectedIndex: Int,
    onSelectSource: (Int) -> Unit,
    onRequestOpenSources: (() -> Unit)?,
    onManageRules: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_subject_player_sources),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (sources.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.feature_subject_player_sources_count_format, sources.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            val manageAction = onManageRules ?: onRequestOpenSources
            if (manageAction != null) {
                Row(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = manageAction)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.feature_subject_player_manage_sources),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = BgmIcons.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(sources) { index, source ->
                val isSelected = index == selectedIndex
                val failureCount = sourceFailureCounts[source.id] ?: 0
                val isFailing = failureCount > 0 && !isSelected
                val rawName = source.nameRes?.let { stringResource(it) } ?: source.name
                val displayLabel = formatSourceDisplayName(rawName, index, source.isDirect)

                Surface(
                    onClick = { onSelectSource(index) },
                    shape = RoundedCornerShape(8.dp),
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    contentColor =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier =
                        Modifier
                            .height(34.dp)
                            .alpha(if (isFailing) 0.55f else 1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = if (source.isDirect) BgmIcons.CloudQueue else BgmIcons.Tv,
                            contentDescription = null,
                            tint =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                },
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = displayLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 选集区：横向滑动单集条，右上角"全部 >"点击呼出半屏选集底栏。
 */
@Composable
internal fun BiliEpisodesSection(
    episodes: List<PlayerEpisodeItem>,
    selectedEpisodeSort: Float,
    selectedEpisodeId: Long,
    onSelectEpisode: (PlayerEpisodeItem) -> Unit,
    modifier: Modifier = Modifier,
    autoNextEnabled: Boolean = true,
    onToggleAutoNext: () -> Unit = {},
    onOpenAllEpisodes: () -> Unit = {},
) {
    val lazyListState = rememberLazyListState()

    val selectedIndex =
        remember(episodes, selectedEpisodeSort, selectedEpisodeId) {
            val idx =
                episodes.indexOfFirst {
                    it.sort == selectedEpisodeSort && (selectedEpisodeId == 0L || it.id == 0L || it.id == selectedEpisodeId)
                }
            if (idx >= 0) idx else 0
        }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in episodes.indices) {
            lazyListState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_subject_player_episodes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (episodes.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.feature_subject_player_total_episodes_count, episodes.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onOpenAllEpisodes)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.feature_subject_player_expand_all),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = BgmIcons.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(episodes, key = { "${it.type}_${it.id}_${it.sort}" }) { ep ->
                val isSelected =
                    ep.sort == selectedEpisodeSort && (selectedEpisodeId == 0L || ep.id == 0L || ep.id == selectedEpisodeId)
                BiliEpisodeRowCard(
                    episode = ep,
                    isSelected = isSelected,
                    onClick = { onSelectEpisode(ep) },
                )
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
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val chunks =
        remember(episodes) {
            if (episodes.size > BILI_EPISODE_CHUNK_SIZE) {
                episodes.chunked(BILI_EPISODE_CHUNK_SIZE)
            } else {
                emptyList()
            }
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
        mutableIntStateOf(
            if (chunks.isNotEmpty() && selectedIndex in episodes.indices) {
                selectedIndex / BILI_EPISODE_CHUNK_SIZE
            } else {
                0
            },
        )
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
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.feature_subject_player_total_episodes_count, episodes.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (chunks.size > 1) {
                val paginationLabels =
                    remember(chunks) {
                        chunks.map { list -> "${list.first().sort.toInt()}-${list.last().sort.toInt()}" }
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
        }
    }
}

/**
 * 横向滑动的单集格子：当前播放 = 主色实心，其余 = 中性底色。
 *
 * 状态改由填充色与右下角标表达，不再往 46dp 高的格子里塞第二行"播放中"小字
 * （两行字会把序号挤得偏上、一行里对不齐）；播放中与已看状态通过语义描述保留给读屏。
 */
@Composable
internal fun BiliEpisodeRowCard(
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

    val stateLabel =
        when {
            isSelected -> stringResource(R.string.feature_subject_player_playing)
            episode.isWatched -> stringResource(R.string.feature_subject_player_watched)
            else -> null
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
        modifier =
            modifier
                .width(54.dp)
                .height(40.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (stateLabel != null) "$sortLabel, $stateLabel" else sortLabel
                },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = sortLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
            if (episode.isWatched) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(5.dp)
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
 * 播放页下半部统一使用的轻量胶囊。
 *
 * 刻意不用 M3 的 FilterChip——FilterChip 带 1dp 描边、容器色与较大内边距，竖屏列表里
 * 连排会显得"厚"；这里只用填充色区分选中态，形态恒定，尺寸由调用方给定。
 */
@Composable
private fun BiliPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    height: Dp = 32.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
) {
    Row(
        modifier =
            modifier
                .height(height)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(onClick = onClick)
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = textStyle,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** 可切换的小标签：选中 = primaryContainer，未选 = 中性容器色。 */
@Composable
private fun BiliTogglePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    height: Dp = 28.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp),
) {
    BiliPill(
        label = label,
        containerColor =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        contentColor =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        height = height,
        textStyle = textStyle,
        contentPadding = contentPadding,
    )
}

/** 追番药丸：三态只换填充色与文案，按钮形态与打卡/找源胶囊保持严格一致（高度 32dp）。 */
@Composable
private fun BiliFollowPill(
    collectionType: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val isFollowing = collectionType == CollectionType.DOING.value
    val isWatched = collectionType == CollectionType.COLLECT.value
    val isTracked = isFollowing || isWatched

    BiliPill(
        label =
            when {
                isFollowing -> stringResource(R.string.feature_subject_player_followed_anime)
                isWatched -> stringResource(R.string.feature_subject_player_watched_anime)
                else -> stringResource(R.string.feature_subject_player_follow_anime)
            },
        containerColor =
            if (isTracked) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        contentColor =
            if (isTracked) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        leadingIcon = if (isTracked) BgmIcons.Check else null,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier,
        height = 32.dp,
        textStyle = MaterialTheme.typography.labelMedium,
        contentPadding = PaddingValues(horizontal = 12.dp),
    )
}

/**
 * 竖屏播放页下半部分 Tab 分栏
 */
enum class PlayerPortraitTab {
    INTRO,
    DISCUSSION,
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

/**
 * 竖屏播放页 - 简介 Tab 内容（分集信息与操作、选集横滑、播放源切换）
 */
@Composable
internal fun PlayerIntroContent(
    uiState: PlayerUiState,
    epLabel: String,
    onToggleWatched: () -> Unit,
    onToggleFollow: () -> Unit,
    onSelectEpisode: (PlayerEpisodeItem) -> Unit,
    onOpenAllEpisodes: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onRequestOpenSources: (() -> Unit)?,
    onManageRules: (() -> Unit)?,
    onSubjectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. 番剧标题、当前分集、打卡/追番/找源操作与剧情简介
        item(key = "episode_overview") {
            PlayerEpisodeOverviewCard(
                epLabel = epLabel,
                episodeName = uiState.episodeName,
                airDate = uiState.currentEpisodeAirdate,
                description = uiState.currentEpisodeDesc,
                isWatched = uiState.isWatched,
                collectionType = uiState.subjectCollectionType,
                onToggleWatched = onToggleWatched,
                onToggleFollow = onToggleFollow,
                onRequestOpenSources = onRequestOpenSources,
                subjectName = uiState.subjectName,
                score = uiState.subjectScore,
                onSubjectClick = onSubjectClick,
            )
        }

        // 2. 选集横滑
        if (uiState.episodes.isNotEmpty()) {
            item(key = "episodes_section") {
                BiliEpisodesSection(
                    episodes = uiState.episodes,
                    selectedEpisodeSort = uiState.episodeSort,
                    selectedEpisodeId = uiState.episodeId,
                    onSelectEpisode = onSelectEpisode,
                    onOpenAllEpisodes = onOpenAllEpisodes,
                )
            }
        }

        // 3. 播放源切换条
        if (uiState.sources.isNotEmpty()) {
            item(key = "player_sources") {
                BiliPlayerSourceBar(
                    sources = uiState.sources,
                    sourceFailureCounts = uiState.sourceFailureCounts,
                    selectedIndex = uiState.selectedSourceIndex,
                    onSelectSource = onSelectSource,
                    onRequestOpenSources = onRequestOpenSources,
                    onManageRules = onManageRules,
                )
            }
        }
    }
}

/**
 * 竖屏播放页 - 讨论 Tab 内容（全高独立滚动视口、排序、Reaction）
 */
@Composable
internal fun PlayerDiscussionContent(
    uiState: PlayerUiState,
    onSortChange: (CommentSortOrder) -> Unit,
    onRetry: () -> Unit,
    onReactionClick: (EpisodeComment, CommentReaction) -> Unit,
    onAddReaction: (EpisodeComment, Int) -> Unit,
    onCopyComment: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val commentsListState = rememberLazyListState()

    LazyColumn(
        state = commentsListState,
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        episodeCommentsSection(
            comments = uiState.comments,
            commentCount = uiState.currentEpisodeCommentCount,
            isLoading = uiState.isCommentsLoading,
            error = uiState.commentsError,
            sortOrder = uiState.commentSortOrder,
            currentUserId = uiState.currentUserId,
            onSortChange = onSortChange,
            onRetry = onRetry,
            onReactionClick = onReactionClick,
            onAddReaction = onAddReaction,
            onCopyComment = onCopyComment,
            onUserClick = onUserClick,
            onUrlClick = onUrlClick,
            showHeader = true,
        )
    }
}
