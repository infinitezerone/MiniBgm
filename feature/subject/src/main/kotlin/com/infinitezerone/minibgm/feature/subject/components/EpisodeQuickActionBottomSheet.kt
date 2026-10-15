package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.designsystem.theme.LocalFeatureCapabilities
import com.infinitezerone.minibgm.core.designsystem.theme.WishOrange
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusAiringContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusDoingContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusAiringContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusDoingContainerColor
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.feature.subject.R

private data class EpisodeStatusDisplay(
    val text: String,
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector?,
)

/**
 * 分集轻量微操作与信息面板（扁平化无嵌套设计，支持原位切集、剧情简介展开、一键播放与打卡）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeQuickActionBottomSheet(
    episode: Episode,
    isWatched: Boolean,
    isNextToWatch: Boolean,
    onDismiss: () -> Unit,
    onToggleWatched: () -> Unit,
    onSelectEpisodeForDetail: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
    onOpenSources: (() -> Unit)? = null,
    onBatchMark: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onNavigateToPrevious: (() -> Unit)? = null,
    onNavigateToNext: (() -> Unit)? = null,
    currentIndex: Int = -1,
    totalCount: Int = -1,
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val isFuture = remember(episode.airdate) { isEpisodeFutureAir(episode) }
    val epLabel = episode.guideLabel
    val primaryTitle = episode.nameCn.ifBlank { episode.name.ifBlank { epLabel } }
    val secondaryTitle = if (episode.name.isNotBlank() && episode.name != primaryTitle) episode.name else ""
    val capabilities = LocalFeatureCapabilities.current
    val canPlay = !isFuture && onPlayClick != null && capabilities.isInternalPlayerEnabled
    val canOpenSources = !isFuture && onOpenSources != null
    var isDescExpanded by rememberSaveable(episode.id) { mutableStateOf(false) }

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
                    .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. 顶部原位切集导航（若有切集上下文，扁平呈现，杜绝反复开关弹窗）
            if (onNavigateToPrevious != null || onNavigateToNext != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FilledTonalButton(
                        onClick = { onNavigateToPrevious?.invoke() },
                        enabled = onNavigateToPrevious != null,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        shape = BgmShapes.small,
                    ) {
                        Icon(
                            imageVector = BgmIcons.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.feature_subject_ep_previous),
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = stringResource(R.string.feature_subject_ep_previous),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    Text(
                        text =
                            if (currentIndex >= 0 && totalCount > 0) {
                                "${currentIndex + 1} / $totalCount"
                            } else {
                                epLabel
                            },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FilledTonalButton(
                        onClick = { onNavigateToNext?.invoke() },
                        enabled = onNavigateToNext != null,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        shape = BgmShapes.small,
                    ) {
                        Text(
                            text = stringResource(R.string.feature_subject_ep_next),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = BgmIcons.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.feature_subject_ep_next),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            // 2. 扁平化 Hero 标题与状态（单层流式排版，移除割裂的 48dp 框中框）
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val fullHeading = if (primaryTitle != epLabel) "$epLabel · $primaryTitle" else epLabel
                    Text(
                        text = fullHeading,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val status =
                        when {
                            isWatched ->
                                EpisodeStatusDisplay(
                                    text = stringResource(R.string.feature_subject_ep_status_watched),
                                    containerColor = statusCollectContainerColor(),
                                    contentColor = onStatusCollectContainerColor(),
                                    icon = BgmIcons.Check,
                                )
                            isFuture ->
                                EpisodeStatusDisplay(
                                    text =
                                        if (episode.airdate.isNotBlank()) {
                                            stringResource(R.string.feature_subject_player_airdate, episode.airdate)
                                        } else {
                                            stringResource(R.string.feature_subject_episodes_status_to_watch)
                                        },
                                    containerColor = statusAiringContainerColor(),
                                    contentColor = onStatusAiringContainerColor(),
                                    icon = BgmIcons.Schedule,
                                )
                            isNextToWatch ->
                                EpisodeStatusDisplay(
                                    text = stringResource(R.string.feature_subject_episodes_status_to_watch),
                                    containerColor = statusDoingContainerColor(),
                                    contentColor = onStatusDoingContainerColor(),
                                    icon = BgmIcons.Play,
                                )
                            else ->
                                EpisodeStatusDisplay(
                                    text = stringResource(R.string.feature_subject_ep_status_unwatched),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    icon = null,
                                )
                        }

                    Surface(
                        shape = BgmShapes.small,
                        color = status.containerColor,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            if (status.icon != null) {
                                Icon(
                                    imageVector = status.icon,
                                    contentDescription = null,
                                    tint = status.contentColor,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                            Text(
                                text = status.text,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = status.contentColor,
                            )
                        }
                    }
                }

                // 日文原名/副标题
                if (secondaryTitle.isNotBlank()) {
                    Text(
                        text = secondaryTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 行内轻量元信息（放送时间 · 时长 · 吐槽讨论直达链接，彻底消灭全宽大卡片套娃）
                val metaItems =
                    buildList {
                        if (episode.airdate.isNotBlank()) add(episode.airdate)
                        if (episode.duration.isNotBlank()) add(episode.duration)
                    }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    if (metaItems.isNotEmpty()) {
                        Text(
                            text = metaItems.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier =
                            Modifier
                                .clip(BgmShapes.small)
                                .clickable {
                                    onDismiss()
                                    onSelectEpisodeForDetail()
                                }.padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            imageVector = if (episode.comment >= 50) BgmIcons.LocalFireDepartment else BgmIcons.ChatBubbleOutline,
                            contentDescription = null,
                            tint = if (episode.comment >= 50) WishOrange else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text =
                                if (episode.comment > 0) {
                                    stringResource(R.string.feature_subject_ep_comments_total, episode.comment) + " ›"
                                } else {
                                    stringResource(R.string.feature_subject_ep_quick_no_comments) + " ›"
                                },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (episode.comment >= 50) WishOrange else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // 3. 剧情梗概：去盒子化自然流式排版（支持展开收起）
            if (episode.desc.isNotBlank()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(BgmShapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable { isDescExpanded = !isDescExpanded }
                            .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.feature_subject_ep_synopsis),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            imageVector = if (isDescExpanded) BgmIcons.KeyboardArrowUp else BgmIcons.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = episode.desc.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isDescExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 19.sp,
                    )
                }
            }

            // 4. 扁平化动作组（主次分明，不再在按钮外层套各种边框和孤零零的小图标）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 播放动作（若可播放，作为最高优先级主操作）
                if (canPlay) {
                    Button(
                        onClick = {
                            onDismiss()
                            onPlayClick.invoke()
                        },
                        modifier = Modifier.weight(1f),
                        shape = BgmShapes.medium,
                    ) {
                        Icon(
                            imageVector = BgmIcons.Play,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text =
                                if (isWatched) {
                                    stringResource(R.string.feature_subject_player_play)
                                } else {
                                    stringResource(R.string.feature_subject_ep_play_current)
                                },
                            maxLines = 1,
                        )
                    }
                }

                // 打卡 / 撤销打卡动作
                if (canPlay) {
                    FilledTonalButton(
                        onClick = {
                            onToggleWatched()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = BgmShapes.medium,
                    ) {
                        Icon(
                            imageVector = if (isWatched) BgmIcons.Check else BgmIcons.CheckBorder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text =
                                if (isWatched) {
                                    stringResource(R.string.feature_subject_ep_unwatch)
                                } else {
                                    stringResource(R.string.feature_subject_ep_mark_watched)
                                },
                            maxLines = 1,
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            onToggleWatched()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = BgmShapes.medium,
                        colors =
                            if (isWatched) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                    ) {
                        Icon(
                            imageVector = if (isWatched) BgmIcons.Check else BgmIcons.CheckBorder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text =
                                if (isWatched) {
                                    stringResource(R.string.feature_subject_ep_unwatch)
                                } else {
                                    stringResource(R.string.feature_subject_ep_mark_watched)
                                },
                            maxLines = 1,
                        )
                    }
                }

                // 播放源辅助入口（带文字，消除无字图标歧义）
                if (canOpenSources) {
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onOpenSources.invoke()
                        },
                        shape = BgmShapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.Tv,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.feature_subject_ep_sources),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // 5. 看到此集（轻量化次要操作，自然置底）
            if (onBatchMark != null && !isWatched && episode.isMain && episode.episodeInt > 1) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onBatchMark()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = BgmShapes.medium,
                ) {
                    Icon(
                        imageVector = BgmIcons.FormatListNumbered,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.feature_subject_ep_watch_up_to_this, episode.episodeInt),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
