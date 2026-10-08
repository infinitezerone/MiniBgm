package com.infinitezerone.minibgm.feature.schedule.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.ScoreBadge
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_POSTER_ASPECT_RATIO
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

/** 相邻时间线插槽行之间的纵向间距，轨道连线向下的延伸量必须与它一致才能视觉连续 */
val TIMELINE_SLOT_SPACING = 10.dp

/** 当前时刻（按东八区）折算的当日分钟数 */
fun getCurrentMinutesCst(): Int {
    val millisInDay = (System.currentTimeMillis() + 8 * 3600_000L) % (24 * 3600_000L)
    return (millisInDay / 60_000L).toInt()
}

/** 解析 "HH:mm"（CST 或 JST 均为该格式）为当日分钟数，解析失败返回 null */
fun parseTimeMinutesCst(time: String): Int? {
    val colonIndex = time.indexOf(':')
    if (colonIndex <= 0 || colonIndex >= time.length - 1) return null
    val hour = time.substring(0, colonIndex).toIntOrNull() ?: return null
    val minute = time.substring(colonIndex + 1).toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

fun getAirStatus(
    timeCst: String,
    isToday: Boolean,
    currentMinutesCst: Int = getCurrentMinutesCst(),
): AirStatus {
    if (!isToday || timeCst.length < 3) return AirStatus.NORMAL
    val slotMinutes = parseTimeMinutesCst(timeCst) ?: return AirStatus.NORMAL

    return when {
        currentMinutesCst > slotMinutes + 35 -> AirStatus.AIRED
        currentMinutesCst >= slotMinutes -> AirStatus.AIRING
        else -> AirStatus.UPCOMING
    }
}

/** 「还有多久开播」倒计时文案（对标 AniList / LiveChart 的播出表），remaining <= 0 时返回 null */
fun countdownLabel(
    slotMinutes: Int,
    nowMinutes: Int,
): String? {
    val remaining = slotMinutes - nowMinutes
    if (remaining <= 0) return null
    val hour = remaining / 60
    val minute = remaining % 60
    return when {
        hour >= 1 -> if (minute > 0) "${hour}时${minute}分后" else "${hour}时后"
        else -> "${minute}分后"
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
    currentMinutesCst: Int = getCurrentMinutesCst(),
) {
    val airStatus = getAirStatus(time, isToday = isToday, currentMinutesCst = currentMinutesCst)
    val upcomingCountdown =
        if (airStatus == AirStatus.UPCOMING) {
            parseTimeMinutesCst(time)?.let { countdownLabel(it, currentMinutesCst) }
        } else {
            null
        }
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
                    val trackCenterX = 50.dp.toPx()
                    val dotCenterY = 12.dp.toPx()

                    // 垂直轨道连线（向下延伸一个插槽间距，与相邻行首绘的线段拼成连续轨道）
                    drawLine(
                        color = outlineVariant,
                        start = Offset(trackCenterX, 0f),
                        end = Offset(trackCenterX, size.height + TIMELINE_SLOT_SPACING.toPx()),
                        strokeWidth = 2.dp.toPx(),
                    )

                    // 状态节点
                    when (airStatus) {
                        AirStatus.AIRING -> {
                            drawCircle(
                                color = StatusAiring.copy(alpha = 0.25f),
                                radius = 7.dp.toPx(),
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
                                radius = 6.dp.toPx(),
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
            upcomingCountdown = upcomingCountdown,
            modifier = Modifier.width(56.dp),
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

/** 垂直时间线轨道：醒目的时间数值、状态标识（待播时显示实时倒计时）、连接线与节点 */
@Composable
fun TimelineTrackRail(
    time: String,
    airStatus: AirStatus,
    jstTime: String?,
    count: Int,
    modifier: Modifier = Modifier,
    upcomingCountdown: String? = null,
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
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
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
                val statusText =
                    when (airStatus) {
                        AirStatus.AIRED -> "已播"
                        AirStatus.AIRING -> "热播"
                        AirStatus.UPCOMING -> upcomingCountdown ?: "待播"
                        AirStatus.NORMAL -> ""
                    }
                if (statusText.isNotBlank()) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        maxLines = 1,
                    )
                }
            }

            if (count > 1) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        text = "${count}部",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            if (!jstTime.isNullOrBlank() && jstTime != time) {
                Text(
                    text = "JP $jstTime",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
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

/**
 * 单番时间线卡片（紧凑行）：
 * 左侧海报只保留评分角标，首播/在追状态由集数徽章与右侧书签图标表达，播放入口收敛为圆形图标按钮。
 */
@Composable
fun ScheduleTimelineSingleCard(
    schedule: AirSchedule,
    isWatching: Boolean,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onShowSources: (AirSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val displayName = schedule.displayName
    val score = schedule.ratingScore
    val isFirstEp = schedule.nextEpisodeNumber == 1

    Card(
        onClick = {
            onSubjectClick(
                SubjectDetailRoute(
                    subjectId = schedule.bgmId,
                    initialName = displayName,
                    initialCoverUrl = schedule.coverUrl,
                    initialScore = score,
                    source = "schedule",
                ),
            )
        },
        shape = RoundedCornerShape(14.dp),
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
        modifier = modifier.fillMaxWidth().height(86.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. 封面海报（内部 72dp 高，宽约 50dp）
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

                // 封面左下角：Bangumi 评分
                ScoreBadge(
                    score = score,
                    modifier = Modifier.align(Alignment.BottomStart),
                    shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 8.dp),
                    starSize = 9.dp,
                    textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.5.dp),
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 2. 信息列：标题 + 原名/副标 + 状态与快捷操作
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = displayName,
                            style =
                                MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        if (isWatching) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.padding(start = 6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp),
                                ) {
                                    Icon(
                                        imageVector = BgmIcons.Bookmark,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = "在追",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    if (schedule.title.isNotBlank() && schedule.title != displayName) {
                        Text(
                            text = schedule.title,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
                        )
                    }
                }

                // 底行：集数徽章 + 首选播映平台 + 播放/找源按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (schedule.nextEpisodeNumber > 0) {
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
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color =
                                    if (isFirstEp) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                            )
                        }
                    }

                    val primarySite = schedule.siteLinks.firstOrNull()
                    if (primarySite != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                            modifier = Modifier.padding(start = 6.dp),
                        ) {
                            Text(
                                text = primarySite.displayName.ifBlank { primarySite.siteName },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 播放按钮：视觉 28dp，触控判定区 38dp
                    Box(
                        modifier =
                            Modifier
                                .size(38.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onShowSources(schedule)
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = BgmIcons.Play,
                                    contentDescription = "播放",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 今日时间线上的「现在」指示：插在上一个已开播时段与下一个未开播时段之间，承担时间轴的当前时刻锚点 */
@Composable
fun ScheduleNowIndicator(
    nowMinutesCst: Int,
    modifier: Modifier = Modifier,
) {
    val nowLabel = String.format(java.util.Locale.US, "%02d:%02d", nowMinutesCst / 60, nowMinutesCst % 60)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(22.dp)
                .drawBehind {
                    // 左侧轨道延续：竖线贯穿 + 实心节点标记当前时刻
                    val trackCenterX = 50.dp.toPx()
                    drawLine(
                        color = StatusAiring,
                        start = Offset(trackCenterX, 0f),
                        end = Offset(trackCenterX, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                    drawCircle(
                        color = StatusAiring,
                        radius = 4.dp.toPx(),
                        center = Offset(trackCenterX, size.height / 2f),
                    )
                },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(56.dp))

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(1.5.dp)
                    .background(StatusAiring.copy(alpha = 0.45f), RoundedCornerShape(1.dp)),
        )

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = StatusAiring,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                )
                Text(
                    text = "现在 $nowLabel",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
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
        shape = RoundedCornerShape(14.dp),
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
                            imageVector = BgmIcons.CloudQueue,
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

                    val arrowRotation by animateFloatAsState(
                        targetValue = if (isExpanded) 180f else 0f,
                        label = "untimed_expand_arrow",
                    )
                    Icon(
                        imageVector = BgmIcons.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "折叠" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer(rotationZ = arrowRotation),
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
