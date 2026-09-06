package com.infinitezerone.minibgm.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.infinitezerone.minibgm.MainActivity
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmDarkColors
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmLightColors
import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.context.GlobalContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 「今日更新」桌面小组件：展示「我追的」（DOING）番剧未来 24 小时内的播出事件。
 * 数据由 [provideGlance] 经仓库层从本地 Room 派生，零网络；
 * 点击组件经 open_schedule 深链直达时间表页。
 */
class ScheduleWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val koin = GlobalContext.get()
        val isLoggedIn =
            koin
                .get<UserPreferencesDataSource>()
                .userPreferences
                .firstOrNull()
                ?.isLoggedIn == true
        val trackedSubjectIds =
            koin
                .get<CollectionRepository>()
                .getCollectionsByTypeStream(CollectionType.DOING)
                .firstOrNull()
                .orEmpty()
                .map { it.subjectId }
        val upcoming =
            koin
                .get<ScheduleRepository>()
                .getUpcomingAiringForSubjects(
                    subjectIds = trackedSubjectIds,
                    hoursAhead = HOURS_AHEAD,
                )
        val openScheduleAction =
            actionStartActivity(
                Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_OPEN_SCHEDULE, true)
                },
            )

        provideContent {
            GlanceTheme(colors = ColorProviders(MiniBgmLightColors, MiniBgmDarkColors)) {
                WidgetContent(
                    isLoggedIn = isLoggedIn,
                    upcoming = upcoming,
                    openScheduleAction = openScheduleAction,
                )
            }
        }
    }

    companion object {
        const val HOURS_AHEAD = 24L
        const val MAX_ROWS = 4
    }
}

@Composable
private fun WidgetContent(
    isLoggedIn: Boolean,
    upcoming: List<UpcomingAiring>,
    openScheduleAction: Action,
) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .cornerRadius(16.dp)
                .clickable(openScheduleAction)
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "今日更新",
                maxLines = 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "时间表 ›",
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp),
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        when {
            !isLoggedIn -> WidgetPlaceholder("登录小番盒后，这里会汇总「我追的」番剧的更新")
            upcoming.isEmpty() -> WidgetPlaceholder("未来 24 小时暂无追番更新")
            else -> {
                val shown = upcoming.take(ScheduleWidget.MAX_ROWS)
                shown.forEachIndexed { index, item ->
                    AiringRow(item)
                    if (index < shown.size - 1) {
                        Spacer(modifier = GlanceModifier.height(5.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AiringRow(item: UpcomingAiring) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.displayName,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp),
            )
            Text(
                text = rowSubtitle(item),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 10.sp),
            )
        }
        KindTag(item.kind)
    }
}

/** actual 已是确定档期不打标，仅对表定/预估数据暴露置信度（回应"光靠 bgmdata 容易猜错集数"） */
@Composable
private fun KindTag(kind: String) {
    val tag =
        when (kind) {
            AirEventKind.PREDICTED -> "预估" to GlanceTheme.colors.tertiary
            AirEventKind.SCHEDULED -> "表定" to GlanceTheme.colors.secondary
            else -> return
        }
    Text(
        text = tag.first,
        maxLines = 1,
        style =
            TextStyle(
                color = tag.second,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ),
        modifier = GlanceModifier.padding(start = 6.dp),
    )
}

@Composable
private fun WidgetPlaceholder(text: String) {
    Text(
        text = text,
        maxLines = 2,
        style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 11.sp),
        modifier = GlanceModifier.padding(top = 8.dp),
    )
}

private val widgetTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun rowSubtitle(item: UpcomingAiring): String {
    val instant = runCatching { Instant.parse(item.airAtUtc) }.getOrNull() ?: return "第${item.episode}集"
    val zoned = instant.atZone(ZoneId.systemDefault())
    val dayPrefix =
        if (zoned.toLocalDate() == LocalDate.now(ZoneId.systemDefault())) "" else "明天 "
    return "第${item.episode}集 · $dayPrefix${zoned.format(widgetTimeFormatter)}"
}
