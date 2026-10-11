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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.CoverPlaceholder
import com.infinitezerone.minibgm.core.designsystem.component.formatScore
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.feature.subject.R
import java.util.Locale

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
    matchedVodName: String? = null,
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
            if (!matchedVodName.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "· $matchedVodName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
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
    totalEpisodes: Int = 0,
    hasMoreEpisodes: Boolean = false,
    isLoadingMoreEpisodes: Boolean = false,
    onLoadMoreEpisodes: () -> Unit = {},
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
            if (hasMoreEpisodes) {
                item(key = "load_more_episodes_card") {
                    Surface(
                        onClick = onLoadMoreEpisodes,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.height(46.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        ) {
                            if (isLoadingMoreEpisodes) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.feature_subject_load_more_episodes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
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
internal fun BiliPill(
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
internal fun BiliTogglePill(
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
internal fun BiliFollowPill(
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
