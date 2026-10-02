package com.infinitezerone.minibgm.feature.schedule.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_POSTER_ASPECT_RATIO
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.StatusAiring
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.navigation.BgmSharedElementKeys
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.bgmSharedElement

enum class AirStatus {
    NORMAL,
    UPCOMING,
    AIRING,
    AIRED,
}

private fun getCurrentMinutesCst(): Int {
    val millisInDay = (System.currentTimeMillis() + 8 * 3600_000L) % (24 * 3600_000L)
    return (millisInDay / 60_000L).toInt()
}

fun getAirStatus(
    timeCst: String,
    isToday: Boolean,
    currentMinutesCst: Int = getCurrentMinutesCst(),
): AirStatus {
    if (!isToday || timeCst.length < 3) return AirStatus.NORMAL
    val colonIndex = timeCst.indexOf(':')
    if (colonIndex <= 0 || colonIndex >= timeCst.length - 1) return AirStatus.NORMAL
    val hour = timeCst.substring(0, colonIndex).toIntOrNull() ?: return AirStatus.NORMAL
    val minute = timeCst.substring(colonIndex + 1).toIntOrNull() ?: return AirStatus.NORMAL
    val slotMinutes = hour * 60 + minute

    return when {
        currentMinutesCst > slotMinutes + 35 -> AirStatus.AIRED
        currentMinutesCst >= slotMinutes -> AirStatus.AIRING
        else -> AirStatus.UPCOMING
    }
}

/** 时间线单行插槽：左侧醒目时间轴轨道 + 右侧番剧卡片/聚合卡片 */
@Composable
fun TimelineSlotRow(
    time: String,
    schedules: List<AirSchedule>,
    isToday: Boolean,
    watchingSubjectIds: Set<Long>,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onShowSources: (AirSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val airStatus = getAirStatus(time, isToday = isToday)
    val jstTime = schedules.firstOrNull()?.timeJst
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val statusColor =
        when (airStatus) {
            AirStatus.AIRING -> StatusAiring
            AirStatus.UPCOMING -> MaterialTheme.colorScheme.primary
            AirStatus.AIRED -> MaterialTheme.colorScheme.outline
            AirStatus.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    // 左侧轨道轨线与节点绘制在 draw 阶段，消除 IntrinsicSize.Min 双重测量开销
                    val trackCenterX = 48.dp.toPx()
                    val dotCenterY = 11.dp.toPx()

                    // 垂直轨道连线（向下延伸连接到下一个 item 的 spacing 8.dp）
                    drawLine(
                        color = outlineVariant,
                        start = Offset(trackCenterX, 0f),
                        end = Offset(trackCenterX, size.height + 8.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                    )

                    // 状态节点
                    when (airStatus) {
                        AirStatus.AIRING -> {
                            drawCircle(
                                color = StatusAiring.copy(alpha = 0.25f),
                                radius = 6.5.dp.toPx(),
                                center = Offset(trackCenterX, dotCenterY),
                            )
                            drawCircle(
                                color = StatusAiring,
                                radius = 3.5.dp.toPx(),
                                center = Offset(trackCenterX, dotCenterY),
                            )
                        }
                        AirStatus.UPCOMING -> {
                            drawCircle(
                                color = statusColor.copy(alpha = 0.2f),
                                radius = 5.5.dp.toPx(),
                                center = Offset(trackCenterX, dotCenterY),
                            )
                            drawCircle(
                                color = statusColor,
                                radius = 3.dp.toPx(),
                                center = Offset(trackCenterX, dotCenterY),
                            )
                        }
                        else -> {
                            drawCircle(
                                color = outlineVariant,
                                radius = 3.dp.toPx(),
                                center = Offset(trackCenterX, dotCenterY),
                            )
                        }
                    }
                },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TimelineTrackRail(
            time = time,
            airStatus = airStatus,
            jstTime = jstTime,
            count = schedules.size,
            modifier = Modifier.width(52.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            schedules.forEach { singleSchedule ->
                ScheduleTimelineSingleCard(
                    schedule = singleSchedule,
                    isWatching = watchingSubjectIds.contains(singleSchedule.bgmId),
                    onSubjectClick = onSubjectClick,
                    onShowSources = onShowSources,
                )
            }
        }
    }
}

/** 垂直时间线轨道：醒目的时间数值、状态标识、连接线与节点 */
@Composable
fun TimelineTrackRail(
    time: String,
    airStatus: AirStatus,
    jstTime: String?,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val statusColor =
        when (airStatus) {
            AirStatus.AIRING -> StatusAiring
            AirStatus.UPCOMING -> MaterialTheme.colorScheme.primary
            AirStatus.AIRED -> MaterialTheme.colorScheme.outline
            AirStatus.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 左列：时间数值与状态标签
        Column(
            horizontalAlignment = Alignment.End,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(top = 2.dp),
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
                color =
                    when (airStatus) {
                        AirStatus.AIRING -> StatusAiring
                        AirStatus.UPCOMING -> MaterialTheme.colorScheme.primary
                        AirStatus.AIRED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        AirStatus.NORMAL -> MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 1,
            )

            if (airStatus != AirStatus.NORMAL) {
                Text(
                    text =
                        when (airStatus) {
                            AirStatus.AIRED -> "已播"
                            AirStatus.AIRING -> "热播"
                            AirStatus.UPCOMING -> "待播"
                            AirStatus.NORMAL -> ""
                        },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.82f,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )
            }

            if (count > 1) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        text = "${count}部",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.75f,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp),
                    )
                }
            }

            if (!jstTime.isNullOrBlank() && jstTime != time) {
                Text(
                    text = jstTime,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.7f,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }

        // 右列：占位 8.dp，轨道线与节点由父级 Row 的 drawBehind 统一绘制
        Spacer(modifier = Modifier.width(8.dp))
    }
}

/** 单番时间线卡片（左侧有醒目时间轨，右侧高质感海报、中日双标题、集数与评分徽章、播放源与一键追番） */
@Composable
fun ScheduleTimelineSingleCard(
    schedule: AirSchedule,
    isWatching: Boolean,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onShowSources: (AirSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val displayName = schedule.titleCn.ifBlank { schedule.title }
    val score = schedule.ratingScore

    Card(
        onClick = {
            if (!schedule.isUnmapped) {
                onSubjectClick(
                    SubjectDetailRoute(
                        subjectId = schedule.bgmId,
                        initialName = displayName,
                        initialCoverUrl = schedule.coverUrl,
                        initialScore = score,
                        source = "schedule",
                    ),
                )
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isWatching) {
                        MaterialTheme.colorScheme.surfaceContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ),
        border =
            if (isWatching) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            } else {
                null
            },
        modifier = modifier.fillMaxWidth().height(104.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. 封面海报（固定比例，高度填满内部 84dp，宽约 59dp）
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(0.7f)
                        .bgmSharedElement(
                            key = BgmSharedElementKeys.subjectCover(schedule.bgmId, "schedule"),
                            clipInOverlayDuringTransition = RoundedCornerShape(8.dp),
                        ),
            ) {
                CoverImage(
                    url = schedule.coverUrl,
                    contentDescription = displayName,
                    cornerRadius = 8.dp,
                    aspectRatio = BGM_POSTER_ASPECT_RATIO,
                    modifier = Modifier.fillMaxSize(),
                )

                // 封面左上角：首播标记
                if (schedule.nextEpisodeNumber == 1) {
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 8.dp, topStart = 8.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Text(
                            text = "首播",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp),
                        )
                    }
                }

                // 封面右上角：已在追状态标记（仅静默小图标，不与底栏抢横向空间）
                if (isWatching) {
                    Surface(
                        shape = RoundedCornerShape(bottomStart = 8.dp, topEnd = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = "已在追",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(3.dp).size(10.dp),
                        )
                    }
                }

                // 封面左下角：Bangumi 评分
                if (score > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 8.dp),
                        color = Color.Black.copy(alpha = 0.76f),
                        modifier = Modifier.align(Alignment.BottomStart),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = RatingGold,
                                modifier = Modifier.size(9.dp),
                            )
                            Text(
                                text = score.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 2. 内容信息流（高度填满，上部标题，下部集数徽章与播放按钮）
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 上半区：番剧标题（单一标题，最多两行）
                Text(
                    text = displayName,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 19.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 下半区：播出集数徽章（左） + 播放操作入口（右）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (schedule.nextEpisodeNumber > 0) {
                        val isFirstEp = schedule.nextEpisodeNumber == 1
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color =
                                if (isFirstEp) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                                },
                        ) {
                            Text(
                                text = if (isFirstEp) "首播 · 第 1 话" else "第 ${schedule.nextEpisodeNumber} 话",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color =
                                    if (isFirstEp) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (schedule.isUnmapped) {
                        Text(
                            text = "AniList 在播 · 暂未收录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    } else {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onShowSources(schedule)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "播放",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = "播放",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 全天 / 时间待定番剧自然收容折叠区 */
@Composable
fun ScheduleUntimedSection(
    schedules: List<AirSchedule>,
    watchingSubjectIds: Set<Long>,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onShowSources: (AirSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Surface(
                onClick = { isExpanded = !isExpanded },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "全天 / 网络独播待定",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = "${schedules.size} 部",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "折叠" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    schedules.forEach { schedule ->
                        ScheduleTimelineSingleCard(
                            schedule = schedule,
                            isWatching = watchingSubjectIds.contains(schedule.bgmId),
                            onSubjectClick = onSubjectClick,
                            onShowSources = onShowSources,
                        )
                    }
                }
            }
        }
    }
}
