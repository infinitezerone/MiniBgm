package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusAiringContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusDoingContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusAiringContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusDoingContainerColor
import com.infinitezerone.minibgm.core.model.Episode

/** 分集轻量微操作面板（点击分集网格弹出，破解未看集数无法进入详情/吐槽的死胡同） */
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
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val isFuture = remember(episode.airdate) { isEpisodeFutureAir(episode) }
    val group = EpisodeGroup.fromType(episode.type)
    val epNum = if (episode.ep > 0f) episode.ep else episode.sort
    val epNumberInt = epNum.toInt()
    val epLabel = if (episode.type == 0) "第 ${epNum.toEpisodeLabel()} 话" else "${group.label} ${episode.sort.toInt()}"
    val primaryTitle = episode.nameCn.ifBlank { episode.name.ifBlank { epLabel } }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 头部：话数与主标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = BgmShapes.medium,
                    color =
                        when {
                            isWatched -> statusCollectContainerColor()
                            isFuture -> statusAiringContainerColor()
                            isNextToWatch -> statusDoingContainerColor()
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = epNum.toEpisodeLabel(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color =
                                when {
                                    isWatched -> onStatusCollectContainerColor()
                                    isFuture -> onStatusAiringContainerColor()
                                    isNextToWatch -> onStatusDoingContainerColor()
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = primaryTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (episode.name.isNotBlank() && episode.name != primaryTitle) {
                        Text(
                            text = episode.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 元信息行：放送日期、时长、吐槽数
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (episode.airdate.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = BgmIcons.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = episode.airdate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (episode.duration.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = BgmIcons.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = BgmIcons.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "${episode.comment} 吐槽",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 核心动作按钮区
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 1. 打卡 / 撤销
                Button(
                    onClick = {
                        onToggleWatched()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors =
                        if (isWatched) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        },
                ) {
                    Icon(
                        imageVector = if (isWatched) BgmIcons.Check else BgmIcons.CheckBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isWatched) "取消打卡" else "标记已看")
                }

                // 2. 播放 / 播放源（若可用）
                if (!isFuture) {
                    if (onPlayClick != null) {
                        FilledTonalButton(
                            onClick = {
                                onPlayClick()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = BgmIcons.Play,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "播放本集")
                        }
                    } else if (onOpenSources != null) {
                        FilledTonalButton(
                            onClick = {
                                onDismiss()
                                onOpenSources()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = BgmIcons.Tv,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "播放源")
                        }
                    }
                }
            }

            // 次要动作行：进入单集吐槽详情 & 批量标记至此集
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onSelectEpisodeForDetail()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = BgmIcons.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "查看吐槽与讨论")
                }

                if (onBatchMark != null && !isWatched && episode.type == 0 && epNumberInt > 1) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onBatchMark()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = BgmIcons.FormatListNumbered,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "看到此集 (1~$epNumberInt)")
                    }
                }
            }
        }
    }
}
