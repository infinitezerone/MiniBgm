package com.infinitezerone.minibgm.feature.widget.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.navigation.BgmNavIntents
import com.infinitezerone.minibgm.feature.widget.AirDay
import com.infinitezerone.minibgm.feature.widget.AirStatusLine
import com.infinitezerone.minibgm.feature.widget.R
import com.infinitezerone.minibgm.feature.widget.ScheduleWidgetItemUiModel
import com.infinitezerone.minibgm.feature.widget.ScheduleWidgetUiState
import java.time.LocalDate

/** 统一条目行：状态胶囊 + 番名 + 话数，4x2 与 4x4 共用 */
@Composable
internal fun WidgetItemRow(
    item: ScheduleWidgetItemUiModel,
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick)
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBadge(item = item)
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = item.title,
            maxLines = 1,
            style = textStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = episodeText(item),
            maxLines = 1,
            style = textStyle(color = GlanceTheme.colors.outline, fontSize = 11.sp),
        )
    }
}

/** 单一状态胶囊：已更新/可看（tertiary 强调）、时刻、周几+时刻（primary） */
@Composable
internal fun StatusBadge(item: ScheduleWidgetItemUiModel) {
    Box(
        modifier =
            GlanceModifier
                .background(
                    if (item.isWatchable) {
                        GlanceTheme.colors.tertiaryContainer
                    } else {
                        GlanceTheme.colors.primaryContainer
                    },
                ).cornerRadius(4.dp)
                .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = statusLineText(item.status),
            maxLines = 1,
            style =
                TextStyle(
                    color =
                        if (item.isWatchable) {
                            GlanceTheme.colors.onTertiaryContainer
                        } else {
                            GlanceTheme.colors.onPrimaryContainer
                        },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
        )
    }
}

@Composable
internal fun WidgetHeader(
    title: String,
    dateLine: String,
    onRefreshClick: Action,
    onScheduleClick: Action,
) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            maxLines = 1,
            style = textStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = dateLine,
            maxLines = 1,
            style = textStyle(color = GlanceTheme.colors.outline, fontSize = 11.sp),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        // 40dp 热区承载 16dp 图标：视觉不变，触控面积符合最低标准
        Box(
            modifier =
                GlanceModifier
                    .size(40.dp)
                    .clickable(onRefreshClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = context.getString(R.string.widget_cd_refresh),
                modifier = GlanceModifier.size(16.dp),
            )
        }
        Box(
            modifier =
                GlanceModifier
                    .clickable(onScheduleClick)
                    .padding(horizontal = 4.dp, vertical = 12.dp),
        ) {
            Text(
                text = context.getString(R.string.widget_open_schedule),
                maxLines = 1,
                style = textStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
internal fun WidgetPlaceholder(
    message: String,
    ctaText: String,
    action: Action,
) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .clickable(action)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            maxLines = 2,
            style = textStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = ctaText,
            maxLines = 1,
            style = textStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
    }
}

// region 文案渲染：结构化状态 -> 语言资源（Planner 零文案）

/** 「周X」（完整日期见 [dateLineOf]） */
@Composable
internal fun weekdayTextOf(uiState: ScheduleWidgetUiState): String {
    val context = LocalContext.current
    val weekdayIndex = LocalDate.ofEpochDay(uiState.todayEpochDay).dayOfWeek.value
    return context.widgetWeekday(weekdayIndex)
}

/** 「周X · M月D日」 */
@Composable
internal fun dateLineOf(uiState: ScheduleWidgetUiState): String {
    val context = LocalContext.current
    val date = LocalDate.ofEpochDay(uiState.todayEpochDay)
    return context.getString(
        R.string.widget_date_line,
        context.widgetWeekday(date.dayOfWeek.value),
        date.monthValue,
        date.dayOfMonth,
    )
}

@Composable
internal fun headerTitleOf(uiState: ScheduleWidgetUiState): String {
    val context = LocalContext.current
    return when {
        !uiState.isLoggedIn -> context.getString(R.string.widget_title_calendar)
        uiState.totalToday > 0 -> context.getString(R.string.widget_title_today)
        else -> context.getString(R.string.widget_title_next)
    }
}

internal fun Context.widgetWeekday(index: Int): String = resources.getStringArray(R.array.widget_weekdays)[(index - 1).coerceIn(0, 6)]

internal fun dayLabel(
    context: Context,
    day: AirDay,
): String =
    when (day) {
        AirDay.Yesterday -> context.getString(R.string.widget_day_yesterday)
        AirDay.Tomorrow -> context.getString(R.string.widget_day_tomorrow)
        is AirDay.Weekday -> context.widgetWeekday(day.index)
    }

@Composable
internal fun statusLineText(status: AirStatusLine): String {
    val context = LocalContext.current
    return when (status) {
        AirStatusLine.AiredToday -> context.getString(R.string.widget_status_aired_today)
        is AirStatusLine.Aired ->
            context.getString(R.string.widget_status_day_time, dayLabel(context, status.day), status.time)
        is AirStatusLine.TodayAt -> status.time
        is AirStatusLine.Upcoming ->
            context.getString(R.string.widget_status_day_time, dayLabel(context, status.day), status.time)
        AirStatusLine.Watching -> context.getString(R.string.widget_status_watching)
        AirStatusLine.OnAir -> context.getString(R.string.widget_status_on_air)
    }
}

internal fun kindLabel(
    context: Context,
    airKind: String?,
): String? =
    when (airKind) {
        AirEventKind.SCHEDULED -> context.getString(R.string.widget_kind_scheduled)
        AirEventKind.PREDICTED -> context.getString(R.string.widget_kind_predicted)
        else -> null
    }

@Composable
internal fun episodeText(item: ScheduleWidgetItemUiModel): String {
    val context = LocalContext.current
    return if (item.episode > 0) {
        context.getString(R.string.widget_episode, item.episode)
    } else {
        context.getString(R.string.widget_episode_latest)
    }
}

/** 「第X话」或「最新话」，附带表定/预估置信度标签 */
@Composable
internal fun heroEpisodeLine(item: ScheduleWidgetItemUiModel): String {
    val context = LocalContext.current
    val episode = episodeText(item)
    val kind = kindLabel(context, item.airKind) ?: return episode
    return context.getString(R.string.widget_episode_with_kind, episode, kind)
}

// endregion

internal fun textStyle(
    color: androidx.glance.unit.ColorProvider,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight? = null,
) = TextStyle(
    color = color,
    fontSize = fontSize,
    fontWeight = fontWeight,
)

@Composable
internal fun openScheduleAction(): Action {
    val context = LocalContext.current
    return actionStartActivity(BgmNavIntents.createScheduleIntent(context))
}

@Composable
internal fun openSubjectAction(subjectId: Long): Action {
    val context = LocalContext.current
    return actionStartActivity(BgmNavIntents.createSubjectIntent(context, subjectId))
}

@Composable
internal fun openLoginAction(): Action {
    val context = LocalContext.current
    return actionStartActivity(BgmNavIntents.createLoginIntent(context))
}
