package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.feature.subject.R

/**
 * 分集焦点大卡片 (Episode Spotlight Card)。
 *
 * 紧随分集网格下方，实时呈现所选单集的标题、日文原名、放送时间、单集剧情简介、
 * 单集起播按钮、打卡状态切换及单集讨论入口，彻底解决短番剧下半屏的黑屏空洞。
 */
@Composable
fun EpisodeSpotlightCard(
    episode: Episode,
    isWatched: Boolean,
    isNextToWatch: Boolean,
    onPlayClick: (() -> Unit)?,
    onToggleWatched: () -> Unit,
    onOpenEpisodeDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. 顶部集数标签 + 标题 + 放送日期
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color =
                            if (isNextToWatch) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                    ) {
                        Text(
                            text = episode.guideLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color =
                                if (isNextToWatch) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }

                    Text(
                        text = episode.primaryName.ifBlank { stringResource(R.string.feature_subject_episode_format, episode.formattedNumber) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (episode.airdate.isNotBlank()) {
                    Text(
                        text = episode.airdate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }

            // 2. 日文原名（若与主标题不同且存在）
            if (episode.name.isNotBlank() && episode.name != episode.primaryName) {
                Text(
                    text = episode.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 3. 本集单集剧情梗概 (desc)
            val descText = episode.desc.trim()
            Text(
                text = descText.ifBlank { stringResource(R.string.feature_subject_episode_no_desc) },
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (descText.isNotBlank()) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            // 4. 操作底栏：左侧讨论跳转，右侧打卡与起播
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 讨论入口
                TextButton(
                    onClick = onOpenEpisodeDetails,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text =
                            if (episode.comment > 0) {
                                stringResource(R.string.feature_subject_comments_short_format, episode.comment)
                            } else {
                                stringResource(R.string.feature_subject_ep_quick_comments_title)
                            },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 打卡按钮
                    OutlinedButton(
                        onClick = onToggleWatched,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        if (isWatched) {
                            Icon(
                                imageVector = BgmIcons.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.feature_subject_status_watched),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.feature_subject_ep_mark_watched),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }

                    // 起播按钮
                    if (onPlayClick != null) {
                        Button(
                            onClick = onPlayClick,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.feature_subject_episodes_play),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 概览页横向分集选集架 (Overview Episodes Carousel)。
 *
 * 允许用户在概览页看完故事简介后，无需切换 Tab 即可立即在横滑架中点击目标分集起播。
 */
@Composable
fun OverviewEpisodeCarousel(
    episodes: List<Episode>,
    watchedCount: Int,
    onPlayEpisode: (Episode) -> Unit,
    onViewAllEpisodes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (episodes.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 标头行：左侧「选集 · 共 N 话」，右侧「全部 >」
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.feature_subject_overview_episodes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "· ${episodes.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(
                onClick = onViewAllEpisodes,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text(
                    text = "${stringResource(R.string.feature_subject_view_all_episodes)} ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // 分集横滑卡片序列
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = episodes.take(30), key = { it.id }) { episode ->
                val isWatched = isEpisodeWatched(episode, watchedCount)
                val isNext = isEpisodeNextToWatch(episode, watchedCount, hasProgress = watchedCount > 0)

                val cardShape = RoundedCornerShape(8.dp)
                val cardBorder =
                    if (isNext) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }

                Surface(
                    shape = cardShape,
                    border = cardBorder,
                    color =
                        if (isNext) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    modifier =
                        Modifier
                            .width(136.dp)
                            .height(72.dp)
                            .clip(cardShape)
                            .clickable { onPlayEpisode(episode) },
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = episode.guideLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isNext) FontWeight.ExtraBold else FontWeight.Bold,
                                color =
                                    if (isNext) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )

                            if (isWatched) {
                                Icon(
                                    imageVector = BgmIcons.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }

                        Text(
                            text = episode.primaryName.ifBlank { episode.formattedNumber },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
