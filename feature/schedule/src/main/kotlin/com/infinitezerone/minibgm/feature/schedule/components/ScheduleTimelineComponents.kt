package com.infinitezerone.minibgm.feature.schedule.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.component.BgmFloatingFrame
import com.infinitezerone.minibgm.core.designsystem.component.BgmFloatingFrameDefaults
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.ScoreBadge
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_POSTER_ASPECT_RATIO
import com.infinitezerone.minibgm.core.designsystem.theme.StatusAiring
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.navigation.BgmSharedElementKeys
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.bgmSharedElement
import com.infinitezerone.minibgm.feature.schedule.R

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
@Composable
fun countdownLabel(
    slotMinutes: Int,
    nowMinutes: Int,
): String? {
    val remaining = slotMinutes - nowMinutes
    if (remaining <= 0) return null
    val hour = remaining / 60
    val minute = remaining % 60
    return when {
        hour >= 1 ->
            if (minute > 0) {
                stringResource(R.string.feature_schedule_countdown_hours_minutes, hour, minute)
            } else {
                stringResource(R.string.feature_schedule_countdown_hours, hour)
            }
        else -> stringResource(R.string.feature_schedule_countdown_minutes, minute)
    }
}

/** 时间线单行插槽：顶部时间与状态标题 + 完整通透番剧卡片流（彻底告别狭窄挤占的左侧轨道） */
@Composable
fun TimelineSlotRow(
    time: String,
    schedules: List<AirSchedule>,
    isToday: Boolean,
    watchingSubjectIds: Set<Long>,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimelineSlotHeader(
            time = time,
            airStatus = airStatus,
            count = schedules.size,
            upcomingCountdown = upcomingCountdown,
        )

        schedules.forEach { singleSchedule ->
            ScheduleTimelineSingleCard(
                schedule = singleSchedule,
                isWatching = watchingSubjectIds.contains(singleSchedule.bgmId),
                onSubjectClick = onSubjectClick,
            )
        }
    }
}

/** 时间时段标头：醒目时间数值、状态标识与数量，告别死线挤压，排印沉静清晰 */
@Composable
fun TimelineSlotHeader(
    time: String,
    airStatus: AirStatus,
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
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color =
                    when (airStatus) {
                        AirStatus.AIRING -> StatusAiring
                        AirStatus.UPCOMING -> MaterialTheme.colorScheme.primary
                        AirStatus.AIRED -> MaterialTheme.colorScheme.onSurfaceVariant
                        AirStatus.NORMAL -> MaterialTheme.colorScheme.onSurface
                    },
            )

            if (airStatus != AirStatus.NORMAL) {
                val statusText =
                    when (airStatus) {
                        AirStatus.AIRED -> stringResource(R.string.feature_schedule_status_aired)
                        AirStatus.AIRING -> stringResource(R.string.feature_schedule_status_airing)
                        AirStatus.UPCOMING -> upcomingCountdown ?: stringResource(R.string.feature_schedule_status_upcoming)
                        AirStatus.NORMAL -> ""
                    }
                if (statusText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        if (count > 1) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
            ) {
                Text(
                    text = stringResource(R.string.feature_schedule_anime_count, count),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * 单番现代时间线卡片：
 * 采用全幅通透卡片、加上海报高度、强化排印对比、纯净实体色阶分层。
 */
@Composable
fun ScheduleTimelineSingleCard(
    schedule: AirSchedule,
    isWatching: Boolean,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            } else {
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                )
            },
        modifier = modifier.fillMaxWidth().height(112.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. 舒展封面海报（高度充实至 92dp，宽约 64dp）
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(BGM_POSTER_ASPECT_RATIO)
                        .clip(RoundedCornerShape(8.dp))
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

                // 封面左下角：评分徽章
                ScoreBadge(
                    score = score,
                    modifier = Modifier.align(Alignment.BottomStart),
                    shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 8.dp),
                    starSize = 9.dp,
                    textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.5.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. 信息列：纯白大标题 + 优雅副标 + 底部状态与操作
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = displayName,
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 19.sp,
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
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                ) {
                                    Icon(
                                        imageVector = BgmIcons.Bookmark,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.feature_schedule_watching_badge),
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
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 底行：集数徽章 + 首选播映平台
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
                            val epText =
                                if (isFirstEp) {
                                    stringResource(R.string.feature_schedule_first_episode)
                                } else {
                                    stringResource(R.string.feature_schedule_episode_format, schedule.nextEpisodeNumber)
                                }
                            Text(
                                text = epText,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color =
                                    if (isFirstEp) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 今日时间线上的「现在」指示：贯穿全宽的精细指示线与实心发光节点 */
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
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(StatusAiring.copy(alpha = 0.45f)),
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
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
                    text = stringResource(R.string.feature_schedule_now_indicator, nowLabel),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(StatusAiring.copy(alpha = 0.45f)),
        )
    }
}
}

/** 全天 / 时间待定番剧自然收容折叠区 */
@Composable
fun ScheduleUntimedSection(
    schedules: List<AirSchedule>,
    watchingSubjectIds: Set<Long>,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }

    BgmFloatingFrame(
        contentPadding = PaddingValues(12.dp),
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
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
                        text = stringResource(R.string.feature_schedule_untimed_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = stringResource(R.string.feature_schedule_untimed_count, schedules.size),
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
                    contentDescription =
                        if (isExpanded) {
                            stringResource(R.string.feature_schedule_cd_collapse)
                        } else {
                            stringResource(R.string.feature_schedule_cd_expand)
                        },
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
                    )
                }
            }
        }
    }
}
