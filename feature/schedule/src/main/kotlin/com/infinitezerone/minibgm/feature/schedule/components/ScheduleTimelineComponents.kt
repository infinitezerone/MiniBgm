package com.infinitezerone.minibgm.feature.schedule.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.StatusAiring
import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.SiteLink
import com.infinitezerone.minibgm.feature.schedule.CatchupScheduleItem
import java.time.ZoneId
import java.time.ZonedDateTime

enum class AirStatus {
    NORMAL,
    UPCOMING,
    AIRING,
    AIRED,
}

fun getAirStatus(
    timeCst: String,
    isToday: Boolean,
): AirStatus {
    if (!isToday || timeCst.isBlank()) return AirStatus.NORMAL
    val parts = timeCst.split(":")
    if (parts.size < 2) return AirStatus.NORMAL
    val hour = parts[0].toIntOrNull() ?: return AirStatus.NORMAL
    val minute = parts[1].toIntOrNull() ?: return AirStatus.NORMAL

    val zoneCst = ZoneId.of("Asia/Shanghai")
    val now = ZonedDateTime.now(zoneCst)
    val today = now.toLocalDate()
    val airDateTime = today.atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).atZone(zoneCst)
    val endDateTime = airDateTime.plusMinutes(35)

    return when {
        now.isAfter(endDateTime) -> AirStatus.AIRED
        now.isAfter(airDateTime) -> AirStatus.AIRING
        else -> AirStatus.UPCOMING
    }
}

fun sortSiteLinks(links: List<SiteLink>): List<SiteLink> {
    val priorityOrder =
        listOf(
            "bilibili",
            "gamer",
            "gamer_hk",
            "bahamut",
            "iqiyi",
            "qq",
            "youku",
            "mikan",
            "muse_tw",
            "muse_hk",
            "ani_one",
            "ani_one_asia",
            "netflix",
            "disneyplus",
            "crunchyroll",
            "abema",
            "danime",
            "unext",
            "prime",
            "nicovideo",
        )
    return links.distinctBy { it.displayName }.sortedBy { link ->
        val index = priorityOrder.indexOf(link.siteName.lowercase())
        if (index >= 0) index else 100
    }
}

fun openWebUrl(
    context: Context,
    url: String,
) {
    if (url.isBlank()) return
    try {
        val uri = Uri.parse(url)
        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    } catch (_: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {
            // Ignore if no browser can handle
        }
    }
}

/** 时间线单行插槽：左侧醒目时间轴轨道 + 右侧番剧卡片/聚合卡片 */
@Composable
fun TimelineSlotRow(
    time: String,
    schedules: List<AirSchedule>,
    isToday: Boolean,
    watchingSubjectIds: Set<Long>,
    onSubjectClick: (Long) -> Unit,
    onToggleWatching: (Long) -> Unit,
    onShowSources: (AirSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val airStatus = getAirStatus(time, isToday = isToday)
    val jstTime = schedules.firstOrNull()?.timeJst

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimelineTrackRail(
            time = time,
            airStatus = airStatus,
            jstTime = jstTime,
            count = schedules.size,
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
                    onToggleWatching = onToggleWatching,
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
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val statusColor =
        when (airStatus) {
            AirStatus.AIRING -> StatusAiring
            AirStatus.UPCOMING -> MaterialTheme.colorScheme.primary
            AirStatus.AIRED -> MaterialTheme.colorScheme.outline
            AirStatus.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier = modifier.fillMaxHeight(),
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
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
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
                    shape = RoundedCornerShape(3.dp),
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

        // 右列：贯穿时间线与节点
        Box(
            modifier =
                Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        val centerX = size.width / 2
                        val dotCenterY = 9.dp.toPx()

                        // 垂直轨道连线（向下延伸连接到下一个 item 的 spacing）
                        drawLine(
                            color = outlineVariant,
                            start = Offset(centerX, 0f),
                            end = Offset(centerX, size.height + 12.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                        )

                        // 状态节点
                        when (airStatus) {
                            AirStatus.AIRING -> {
                                drawCircle(
                                    color = StatusAiring.copy(alpha = 0.25f),
                                    radius = 6.5.dp.toPx(),
                                    center = Offset(centerX, dotCenterY),
                                )
                                drawCircle(
                                    color = StatusAiring,
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(centerX, dotCenterY),
                                )
                            }
                            AirStatus.UPCOMING -> {
                                drawCircle(
                                    color = statusColor.copy(alpha = 0.2f),
                                    radius = 5.5.dp.toPx(),
                                    center = Offset(centerX, dotCenterY),
                                )
                                drawCircle(
                                    color = statusColor,
                                    radius = 3.dp.toPx(),
                                    center = Offset(centerX, dotCenterY),
                                )
                            }
                            else -> {
                                drawCircle(
                                    color = outlineVariant,
                                    radius = 3.dp.toPx(),
                                    center = Offset(centerX, dotCenterY),
                                )
                            }
                        }
                    },
        )
    }
}

/** 单番时间线卡片（左侧有醒目时间轨，右侧卡片遵循古腾堡阅读动线与无遮挡海报） */
@Composable
fun ScheduleTimelineSingleCard(
    schedule: AirSchedule,
    isWatching: Boolean,
    onSubjectClick: (Long) -> Unit,
    onToggleWatching: (Long) -> Unit,
    onShowSources: (AirSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val displayName = schedule.titleCn.ifBlank { schedule.title }
    val originalTitle = schedule.title.takeIf { it.isNotBlank() && it != displayName }

    Card(
        onClick = { onSubjectClick(schedule.bgmId) },
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
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 纯净封面：无任何盖脸黑标，保证视觉艺术完整性
            Box(
                modifier =
                    Modifier
                        .width(58.dp)
                        .height(82.dp)
                        .clip(RoundedCornerShape(8.dp)),
            ) {
                CoverImage(
                    url = schedule.coverUrl,
                    contentDescription = displayName,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 内容区：自上而下的自然阅读动线
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 1. 顶层：标题独享 100% 水平宽度，支持长标题两行舒展
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (originalTitle != null) {
                        Text(
                            text = originalTitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.88f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 2. 中间层：话数胶囊 + ★ 金色评分
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        if (schedule.nextEpisodeNumber > 0) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            ) {
                                Text(
                                    text =
                                        if (schedule.nextEpisodeKind == AirEventKind.PREDICTED) {
                                            "第 ${schedule.nextEpisodeNumber} 话 · 预计"
                                        } else {
                                            "第 ${schedule.nextEpisodeNumber} 话"
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }

                        if (schedule.ratingScore > 0.0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = RatingGold,
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = schedule.ratingScore.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = RatingGold,
                                )
                            }
                        }
                    }
                }

                // 3. 底部终端区（Terminal Area）：左侧播放源 + 右侧行动召唤追番
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    if (schedule.siteLinks.isNotEmpty()) {
                        SiteLinksRow(
                            links = schedule.siteLinks,
                            onOpenUrl = { openWebUrl(context, it) },
                            onShowMoreSources = { onShowSources(schedule) },
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    BookmarkChip(
                        isWatching = isWatching,
                        onToggle = { onToggleWatching(schedule.bgmId) },
                    )
                }
            }
        }
    }
}

@Composable
fun BookmarkChip(
    isWatching: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(7.dp),
        color =
            if (isWatching) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        border =
            if (isWatching) {
                null
            } else {
                BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            },
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = if (isWatching) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                tint =
                    if (isWatching) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if (isWatching) "在追" else "追番",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color =
                    if (isWatching) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }
    }
}

@Composable
fun SiteLinksRow(
    links: List<SiteLink>,
    onOpenUrl: (String) -> Unit,
    onShowMoreSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedLinks = remember(links) { sortSiteLinks(links) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier,
    ) {
        sortedLinks.firstOrNull()?.let { topLink ->
            Surface(
                onClick = { onOpenUrl(topLink.playUrl) },
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                ) {
                    Text(
                        text = topLink.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(9.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }

        if (sortedLinks.size > 1) {
            Surface(
                onClick = onShowMoreSources,
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.5.dp),
                ) {
                    Text(
                        text = "+${sortedLinks.size - 1} 更多源",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

/** 待补更新聚合卡片（昨日·前天已播但用户未打卡的番） */
@Composable
fun ScheduleCatchupSection(
    catchupItems: List<CatchupScheduleItem>,
    onSubjectClick: (Long) -> Unit,
    onMarkEpisodeWatched: (Long, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
            ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ElectricBolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "待补更新 (近期在追)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "${catchupItems.size} 部未看",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                catchupItems.forEach { item ->
                    val displayName = item.schedule.titleCn.ifBlank { item.schedule.title }
                    Surface(
                        onClick = { onSubjectClick(item.schedule.bgmId) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(width = 44.dp, height = 60.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                            ) {
                                CoverImage(
                                    url = item.schedule.coverUrl,
                                    contentDescription = displayName,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(bottomEnd = 4.dp),
                                    modifier = Modifier.align(Alignment.TopStart),
                                ) {
                                    Text(
                                        text = item.dayLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "已更新至第 ${item.targetEp} 话 · 当前打卡第 ${item.epStatus} 话",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Button(
                                onClick = { onMarkEpisodeWatched(item.schedule.bgmId, item.targetEp) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                modifier = Modifier.height(30.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "标为看过",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
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
    onSubjectClick: (Long) -> Unit,
    onToggleWatching: (Long) -> Unit,
    onShowSources: (AirSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                            onToggleWatching = onToggleWatching,
                            onShowSources = onShowSources,
                        )
                    }
                }
            }
        }
    }
}
