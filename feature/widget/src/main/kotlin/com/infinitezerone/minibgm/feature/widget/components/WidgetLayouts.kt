package com.infinitezerone.minibgm.feature.widget.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import com.infinitezerone.minibgm.feature.widget.R
import com.infinitezerone.minibgm.feature.widget.RefreshScheduleWidgetCallback
import com.infinitezerone.minibgm.feature.widget.ScheduleWidgetUiState

/**
 * 单行条（高度 < 2x2 的扁长形态，如 2x1 / 4x1）：
 * 与三档布局同一套 hero 优先级（可看 -> 今日待播 -> 下一部），横向单行承载。
 */
@Composable
internal fun LineWidgetContent(uiState: ScheduleWidgetUiState) {
    val todayItems = uiState.watchable + uiState.upcomingToday
    val heroIsToday = todayItems.isNotEmpty()
    val hero = if (heroIsToday) todayItems.first() else uiState.later.first()

    Row(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .clickable(
                    if (heroIsToday) openSubjectAction(hero.subjectId) else openScheduleAction(),
                ).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {
        StatusBadge(item = hero)
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = hero.title,
            maxLines = 1,
            style = textStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = heroEpisodeLine(hero),
            maxLines = 1,
            style = textStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/**
 * 2x2 焦点卡：无标题栏，按优先级只回答一件事——
 * 有可看 → 「已更新」焦点；今天全待播 → 最近一部的时刻；今天没有 → 未来 7 天内的下一部。
 */
@Composable
internal fun CompactWidgetContent(uiState: ScheduleWidgetUiState) {
    val context = LocalContext.current
    val todayItems = uiState.watchable + uiState.upcomingToday
    val heroIsToday = todayItems.isNotEmpty()
    val hero = if (heroIsToday) todayItems.first() else uiState.later.first()

    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .clickable(
                    if (heroIsToday) openSubjectAction(hero.subjectId) else openScheduleAction(),
                ).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusBadge(item = hero)
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = weekdayTextOf(uiState),
                maxLines = 1,
                style = textStyle(color = GlanceTheme.colors.outline, fontSize = 10.sp),
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        Text(
            text = hero.title,
            maxLines = 2,
            style = textStyle(color = GlanceTheme.colors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight(),
        )

        Spacer(modifier = GlanceModifier.height(2.dp))

        Text(
            text = heroEpisodeLine(hero),
            maxLines = 1,
            style = textStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        Text(
            text =
                when {
                    heroIsToday && uiState.totalToday > 1 ->
                        context.getString(R.string.widget_hint_more_today, uiState.totalToday - 1)
                    uiState.totalWeek > 1 ->
                        context.getString(R.string.widget_hint_total_week, uiState.totalWeek)
                    else ->
                        dateLineOf(uiState)
                },
            maxLines = 1,
            style = textStyle(color = GlanceTheme.colors.outline, fontSize = 11.sp),
        )
    }
}

/** 4x2 摘要列表：单列 3 行「[状态胶囊][番名][话数]」+ 真实计数溢出行 */
@Composable
internal fun MediumWidgetContent(uiState: ScheduleWidgetUiState) {
    val context = LocalContext.current
    val rows = uiState.orderedItems.take(3)

    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        WidgetHeader(
            title = headerTitleOf(uiState),
            dateLine = dateLineOf(uiState),
            onRefreshClick = actionRunCallback<RefreshScheduleWidgetCallback>(),
            onScheduleClick = openScheduleAction(),
        )
        Spacer(modifier = GlanceModifier.height(4.dp))

        Column(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        ) {
            rows.forEach { item ->
                WidgetItemRow(
                    item = item,
                    onClick = openSubjectAction(item.subjectId),
                    modifier = if (rows.size >= 3) GlanceModifier.defaultWeight() else GlanceModifier,
                )
            }
            if (rows.size < 3) {
                Spacer(modifier = GlanceModifier.defaultWeight())
            }
        }

        val hidden = uiState.totalWeek - rows.size
        if (hidden > 0) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Row(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .clickable(openScheduleAction()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = context.getString(R.string.widget_hint_more, hidden),
                    maxLines = 1,
                    style = textStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

/** 4x4 分区看板：hero 卡（带封面）+ 「今日后续」「接下来」分区 + 真实计数溢出行 */
@Composable
internal fun ExpandedWidgetContent(
    uiState: ScheduleWidgetUiState,
    heroCover: Bitmap?,
) {
    val context = LocalContext.current
    val hero = uiState.orderedItems.first()
    val todayRest = (uiState.watchable + uiState.upcomingToday).drop(1).take(3)
    val heroFromLater = uiState.totalToday == 0
    val laterRows = uiState.later.drop(if (heroFromLater) 1 else 0).take(2)
    val totalListItems = todayRest.size + laterRows.size
    val useWeight = totalListItems >= 4

    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        WidgetHeader(
            title = headerTitleOf(uiState),
            dateLine = dateLineOf(uiState),
            onRefreshClick = actionRunCallback<RefreshScheduleWidgetCallback>(),
            onScheduleClick = openScheduleAction(),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        // Hero 卡：最近可看的一部，或今天的下一部
        Row(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .background(GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(android.R.dimen.system_app_widget_inner_radius)
                    .clickable(openSubjectAction(hero.subjectId))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (heroCover != null) {
                Image(
                    provider = ImageProvider(heroCover),
                    contentDescription = context.getString(R.string.widget_cd_cover),
                    modifier = GlanceModifier.width(44.dp).height(60.dp).cornerRadius(6.dp),
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge(item = hero)
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = heroEpisodeLine(hero),
                        maxLines = 1,
                        style = textStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = hero.title,
                    maxLines = 2,
                    style = textStyle(color = GlanceTheme.colors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (todayRest.isEmpty() && laterRows.isEmpty()) {
            Column(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .clickable(openScheduleAction()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = context.getString(R.string.widget_empty_no_schedule),
                    style = textStyle(color = GlanceTheme.colors.outline, fontSize = 11.sp),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = context.getString(R.string.widget_cta_view_week),
                    style = textStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                )
            }
        } else {
            Column(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            ) {
                val itemModifier = if (useWeight) GlanceModifier.defaultWeight() else GlanceModifier
                if (todayRest.isNotEmpty()) {
                    Text(
                        text = context.getString(R.string.widget_section_today),
                        style = textStyle(color = GlanceTheme.colors.outline, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    todayRest.forEach { item ->
                        WidgetItemRow(
                            item = item,
                            onClick = openSubjectAction(item.subjectId),
                            modifier = itemModifier,
                        )
                    }
                }
                if (todayRest.isNotEmpty() && laterRows.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
                if (laterRows.isNotEmpty()) {
                    Text(
                        text = context.getString(R.string.widget_section_later),
                        style = textStyle(color = GlanceTheme.colors.outline, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    laterRows.forEach { item ->
                        WidgetItemRow(
                            item = item,
                            onClick = openSubjectAction(item.subjectId),
                            modifier = itemModifier,
                        )
                    }
                }
                if (!useWeight) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
            }

            val hidden = uiState.totalWeek - 1 - todayRest.size - laterRows.size
            if (hidden > 0) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Row(
                    modifier =
                        GlanceModifier
                            .fillMaxWidth()
                            .clickable(openScheduleAction()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = context.getString(R.string.widget_hint_more_week, hidden),
                        maxLines = 1,
                        style = textStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }
}
