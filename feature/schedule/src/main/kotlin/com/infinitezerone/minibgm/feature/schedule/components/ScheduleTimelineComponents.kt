package com.infinitezerone.minibgm.feature.schedule.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.component.BgmFloatingFrame
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.ScoreBadge
import com.infinitezerone.minibgm.core.designsystem.component.expressivePress
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

/** 相邻时间线插槽行之间的纵向间距，呼吸感充沛，区分不同时段 */
val TIMELINE_SLOT_SPACING = 18.dp

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

/**
 * 聚合排期条目：将同一个时间槽或同一天内相同 bgmId 的多集播出事件合并为单个视图项。
 */
data class TimelineSubjectGroup(
    val primarySchedule: AirSchedule,
    val episodes: List<Int>,
)

fun List<AirSchedule>.groupTimelineSchedules(): List<TimelineSubjectGroup> =
    groupBy { it.bgmId }.values.map { list ->
        val primary = list.first()
        val eps =
            list
                .map { it.nextEpisodeNumber }
                .filter { it > 0 }
                .distinct()
                .sorted()
        TimelineSubjectGroup(
            primarySchedule = primary,
            episodes =
                eps.ifEmpty {
                    if (primary.nextEpisodeNumber > 0) listOf(primary.nextEpisodeNumber) else emptyList()
                },
        )
    }

/** 时间线单行插槽：顶部时间与状态标题 + 完整通透番剧卡片流（彻底告别狭窄挤占的左侧轨道） */
@Composable
fun TimelineSlotRow(
    time: String,
    schedules: List<AirSchedule>,
    isToday: Boolean,
    watchingSubjectIds: Set<Long>,
    todayYear: Int,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
    onToggleWatching: (Long) -> Unit = {},
    currentMinutesCst: Int = getCurrentMinutesCst(),
) {
    val airStatus = getAirStatus(time, isToday = isToday, currentMinutesCst = currentMinutesCst)
    val upcomingCountdown =
        if (airStatus == AirStatus.UPCOMING) {
            parseTimeMinutesCst(time)?.let { countdownLabel(it, currentMinutesCst) }
        } else {
            null
        }

    val groupedSchedules =
        remember(schedules) {
            schedules.groupTimelineSchedules()
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimelineSlotHeader(
            time = time,
            airStatus = airStatus,
            count = groupedSchedules.size,
            upcomingCountdown = upcomingCountdown,
        )

        groupedSchedules.forEach { group ->
            ScheduleTimelineCard(
                schedule = group.primarySchedule,
                episodes = group.episodes,
                isWatching = watchingSubjectIds.contains(group.primarySchedule.bgmId),
                todayYear = todayYear,
                onSubjectClick = onSubjectClick,
                onToggleWatching = { onToggleWatching(group.primarySchedule.bgmId) },
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
 * 时间线卡片关键词语义档位：纯函数只产出资源档位与格式化参数，
 * 展示文案一律由 UI 经 [labelRes] 的 `stringResource` 解析，严禁回退到字面量。
 */
internal data class ScheduleKeyword(
    @get:StringRes val labelRes: Int,
    val formatArgs: List<Any> = emptyList(),
)

/**
 * 从番剧属性与标题提取高价值关键词语义档位（档期、载体、季数、题材标签）。
 * 时钟依赖经 [todayYear] 注入（VM 按 CST 口径下发），保持纯函数可测。
 */
internal fun extractScheduleKeywords(
    schedule: AirSchedule,
    todayYear: Int,
): List<ScheduleKeyword> {
    val keywords = mutableListOf<ScheduleKeyword>()

    // 1. 档期/年份
    val airDate = schedule.airDate
    val nextEp = schedule.nextEpisodeNumber
    if (nextEp > 52) {
        keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_long_running))
    } else if (airDate.length >= 4) {
        val year = airDate.take(4).toIntOrNull()
        val month = if (airDate.length >= 7) airDate.substring(5, 7).toIntOrNull() else null
        if (year == todayYear && month != null) {
            month.takeIf { it in 1..12 }?.let {
                keywords.add(ScheduleKeyword(R.string.feature_schedule_season_format, listOf(it)))
            }
        } else if (year != null && year < 2015 && nextEp > 24) {
            keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_long_running))
        } else if (year != null && month != null) {
            keywords.add(ScheduleKeyword(R.string.feature_schedule_season_year_month_format, listOf(year, month)))
        } else if (year != null) {
            keywords.add(ScheduleKeyword(R.string.feature_schedule_season_year_format, listOf(year)))
        }
    }

    // 2. 载体类型
    when (schedule.format.uppercase()) {
        "TV_SHORT" -> keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_format_tv_short))
        "TV" -> keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_format_tv))
        "ONA", "WEB" -> keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_format_web))
        "OVA", "OAD" -> keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_format_ova))
        "MOVIE" -> keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_format_movie))
        "SPECIAL" -> keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_format_special))
    }

    // 3. 季数/续作属性（contains 里的中文字面量是标题匹配模式，不是展示文案）
    val title = schedule.displayName
    when {
        title.contains("第二季") || title.contains("第2季") || title.contains("Season 2", ignoreCase = true) ->
            keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_season_2))
        title.contains("第三季") || title.contains("第3季") || title.contains("Season 3", ignoreCase = true) ->
            keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_season_3))
        title.contains("第四季") || title.contains("第4季") || title.contains("Season 4", ignoreCase = true) ->
            keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_season_4))
        title.contains("Final") || title.contains("完结篇") || title.contains("最终季") ->
            keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_final_season))
        title.contains("第") && title.contains("幕") ->
            keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_sequel))
        nextEp == 1 -> keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_premiere))
    }

    // 4. 题材关键词
    if (title.contains("异世界") || title.contains("转生")) {
        keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_genre_isekai))
    }
    if (title.contains("战车") || title.contains("重骑士") || title.contains("战斗") || title.contains("无双") || title.contains("暗鬼")) {
        keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_genre_action))
    }
    if (title.contains("恋爱") || title.contains("出嫁") || title.contains("女友") || title.contains("婚")) {
        keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_genre_romance))
    }
    if (title.contains("侦探") || title.contains("推理")) {
        keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_genre_mystery))
    }
    if (title.contains("日常") || title.contains("涂鸦") || title.contains("放松")) {
        keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_genre_daily))
    }
    if (title.contains("少女") && !title.contains("魔法少女")) {
        keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_genre_shoujo))
    }

    // 5. 高分口碑
    if (schedule.ratingScore >= 8.0) {
        keywords.add(ScheduleKeyword(R.string.feature_schedule_tag_acclaimed))
    }

    return keywords.distinct().take(4)
}

/**
 * 时间线番剧项：
 * 采用扁平通透、上下结构呼吸有致的现代排印列表样式：
 * - 封面、标题、关键词行与底部今日分集胶囊垂直呼应；
 * - 移除了杂乱堆叠的零碎小框，改为典雅的内联点分隔 (·) 关键词标签；
 * - 挂载 [expressivePress] 弹性微交互与沉浸水生触感反馈；
 * - 原生支持多集放出的紧凑折叠与分集直达胶囊导轨。
 */
@Composable
fun ScheduleTimelineCard(
    schedule: AirSchedule,
    isWatching: Boolean,
    todayYear: Int,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
    onToggleWatching: (() -> Unit)? = null,
    episodes: List<Int> = if (schedule.nextEpisodeNumber > 0) listOf(schedule.nextEpisodeNumber) else emptyList(),
) {
    val displayName = schedule.displayName
    val score = schedule.ratingScore
    val isMultiEpisode = episodes.size > 1
    val isFirstEp = episodes.firstOrNull() == 1
    val rowInteractionSource = remember { MutableInteractionSource() }
    val keywords = remember(schedule, todayYear) { extractScheduleKeywords(schedule, todayYear) }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = rowInteractionSource,
                    indication = null,
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
                ).expressivePress(interactionSource = rowInteractionSource, pressedScale = 0.985f)
                .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. 封面海报 (88dp 高度比例沉稳)
        Box(
            modifier =
                Modifier
                    .height(88.dp)
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

        // 2. 中间信息列 (与海报 88dp 等高，上下两段式自然分布)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .height(88.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 上半区：主标题 + 关键词小行
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = displayName,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (keywords.isNotEmpty()) {
                    val keywordLabels = keywords.map { stringResource(it.labelRes, *it.formatArgs.toTypedArray()) }
                    Text(
                        text = keywordLabels.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 下半区：分集徽章与多集直达导轨
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (episodes.isNotEmpty() || schedule.nextEpisodeNumber > 0) {
                        val epText =
                            when {
                                isMultiEpisode -> {
                                    val minEp = episodes.first()
                                    val maxEp = episodes.last()
                                    val isConsecutive = maxEp - minEp == episodes.size - 1
                                    if (isConsecutive && minEp == 1 && episodes.size >= 6) {
                                        stringResource(R.string.feature_schedule_batch_episodes_format, minEp, maxEp)
                                    } else if (isConsecutive) {
                                        stringResource(R.string.feature_schedule_multi_episodes_format, minEp, maxEp, episodes.size)
                                    } else {
                                        stringResource(R.string.feature_schedule_episodes_list_format, episodes.joinToString(","))
                                    }
                                }
                                isFirstEp -> {
                                    if (schedule.totalEpisodes > 0) {
                                        stringResource(R.string.feature_schedule_first_episode_with_total, schedule.totalEpisodes)
                                    } else {
                                        stringResource(R.string.feature_schedule_first_episode)
                                    }
                                }
                                else -> {
                                    val ep = episodes.firstOrNull() ?: schedule.nextEpisodeNumber
                                    if (schedule.totalEpisodes > 0 && ep > 0) {
                                        stringResource(R.string.feature_schedule_episode_with_total_format, ep, schedule.totalEpisodes)
                                    } else {
                                        stringResource(R.string.feature_schedule_episode_format, ep)
                                    }
                                }
                            }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color =
                                when {
                                    isMultiEpisode -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                                    isFirstEp -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                },
                        ) {
                            Text(
                                text = epText,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color =
                                    when {
                                        isMultiEpisode -> MaterialTheme.colorScheme.onTertiaryContainer
                                        isFirstEp -> MaterialTheme.colorScheme.onTertiaryContainer
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    // 限制级标签
                    if (schedule.isAdult) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = stringResource(R.string.feature_schedule_tag_adult),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp),
                            )
                        }
                    }
                }

                if (isMultiEpisode) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.feature_schedule_quick_jump_label),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        episodes.forEach { epNum ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                onClick = {
                                    onSubjectClick(
                                        SubjectDetailRoute(
                                            subjectId = schedule.bgmId,
                                            initialName = displayName,
                                            initialCoverUrl = schedule.coverUrl,
                                            initialScore = score,
                                            source = "schedule_ep_$epNum",
                                        ),
                                    )
                                },
                            ) {
                                Text(
                                    text = stringResource(R.string.feature_schedule_ep_pill_format, epNum),
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 右侧追番按钮
        if (onToggleWatching != null) {
            val watchInteraction = remember { MutableInteractionSource() }
            Surface(
                shape = CircleShape,
                color =
                    if (isWatching) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                modifier =
                    Modifier
                        .expressivePress(interactionSource = watchInteraction, pressedScale = 0.94f)
                        .clickable(
                            interactionSource = watchInteraction,
                            indication = null,
                            onClick = onToggleWatching,
                        ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = if (isWatching) BgmIcons.Bookmark else BgmIcons.BookmarkBorder,
                        contentDescription = null,
                        tint =
                            if (isWatching) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text =
                            if (isWatching) {
                                stringResource(R.string.feature_schedule_action_watching)
                            } else {
                                stringResource(R.string.feature_schedule_action_watch)
                            },
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color =
                            if (isWatching) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
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
                .padding(top = 2.dp, bottom = 0.dp),
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

/** 全天 / 时间待定番剧自然收容折叠区 */
@Composable
fun ScheduleUntimedSection(
    schedules: List<AirSchedule>,
    watchingSubjectIds: Set<Long>,
    todayYear: Int,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
    onToggleWatching: (Long) -> Unit = {},
) {
    var isExpanded by remember { mutableStateOf(true) }
    val groupedSchedules =
        remember(schedules) {
            schedules.groupTimelineSchedules()
        }

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
                            text = stringResource(R.string.feature_schedule_untimed_count, groupedSchedules.size),
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
                groupedSchedules.forEach { group ->
                    ScheduleTimelineCard(
                        schedule = group.primarySchedule,
                        episodes = group.episodes,
                        isWatching = watchingSubjectIds.contains(group.primarySchedule.bgmId),
                        todayYear = todayYear,
                        onSubjectClick = onSubjectClick,
                        onToggleWatching = { onToggleWatching(group.primarySchedule.bgmId) },
                    )
                }
            }
        }
    }
}
