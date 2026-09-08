package com.infinitezerone.minibgm.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
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
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmDarkColors
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmLightColors
import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.navigation.BgmNavIntents
import com.infinitezerone.minibgm.feature.widget.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 「今日追番」桌面小组件：
 * 1. 信息主体是状态而非时刻表：已更新可看 -> 今天待播 -> 未来 7 天三个分区，回答「我现在有什么可看」；
 * 2. 响应式四档：2x1/4x1 单行条 / 2x2 焦点卡 / 4x2 摘要列表 / 4x4 分区看板，断点常量与 SizeMode 候选尺寸共用单一来源；
 * 3. 条目单一状态胶囊（结构化 [AirStatusLine] + 语言资源渲染），溢出计数来自截断前的真实总数；
 * 4. 单项深链番剧详情；溢出计数行与「时间表」入口深链时间表页；
 * 5. 仅 4x4 hero 加载封面小图（Coil 异步 + 失败回退纯文本），其余尺寸保持零图片。
 */
class ScheduleWidget : GlanceAppWidget() {
    companion object {
        // 响应式断点（单一来源）：SizeMode.Responsive 候选尺寸与 WidgetRoot 布局判断阈值共用，
        // 包含超矮单行条，避免系统无法映射到扁长档位导致 2x1/4x1 裁剪
        val LINE_MIN = DpSize(100.dp, 48.dp) // 2x1 / 4x1: 超矮单行条
        val SMALL_MIN = DpSize(100.dp, 100.dp) // 2x2: 焦点卡
        val MEDIUM_MIN = DpSize(220.dp, 100.dp) // 4x2: 摘要列表
        val LARGE_MIN = DpSize(220.dp, 220.dp) // 4x4: 分区看板

        /** 向前看 7 天：动画周更，「今日无更新」时也能回答「下一部是周几」 */
        const val HOURS_AHEAD = 7 * 24L
        const val LOOKBACK_HOURS = 18L
    }

    override val sizeMode = SizeMode.Responsive(setOf(LINE_MIN, SMALL_MIN, MEDIUM_MIN, LARGE_MIN))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val (uiState, heroCover) =
            withContext(Dispatchers.IO) {
                val koin = GlobalContext.getOrNull()
                // 登录态走 AuthRepository（偏好标记 + token 实际存在），避免"偏好已标记但凭据缺失"的假登录
                val isLoggedIn =
                    koin
                        ?.getOrNull<AuthRepository>()
                        ?.isLoggedIn
                        ?.firstOrNull() == true
                val trackedSubjectIds =
                    if (isLoggedIn) {
                        koin
                            .getOrNull<CollectionRepository>()
                            ?.getCollectionsByTypeStream(CollectionType.DOING)
                            ?.firstOrNull()
                            .orEmpty()
                            .map { it.subjectId }
                    } else {
                        emptyList()
                    }
                val scheduleRepo = koin?.getOrNull<ScheduleRepository>()
                val upcoming =
                    if (isLoggedIn && trackedSubjectIds.isNotEmpty()) {
                        scheduleRepo
                            ?.getUpcomingAiringForSubjects(
                                subjectIds = trackedSubjectIds,
                                hoursAhead = HOURS_AHEAD,
                                lookbackHours = LOOKBACK_HOURS,
                            ).orEmpty()
                    } else {
                        emptyList()
                    }
                // 公共日历仅作为未登录用户的获客面；登录用户绝不掺入陌生番剧
                val todaySchedules =
                    if (!isLoggedIn) {
                        scheduleRepo
                            ?.getSchedulesByWeekday(
                                Instant
                                    .ofEpochMilli(System.currentTimeMillis())
                                    .atZone(ZoneId.systemDefault())
                                    .dayOfWeek
                                    .value,
                            )?.firstOrNull()
                            .orEmpty()
                    } else {
                        emptyList()
                    }

                val nowEpochMillis = System.currentTimeMillis()
                val zoneId = ZoneId.systemDefault()

                val state =
                    ScheduleWidgetPlanner.plan(
                        isLoggedIn = isLoggedIn,
                        hasTrackedSubjects = trackedSubjectIds.isNotEmpty(),
                        upcoming = upcoming,
                        todaySchedules = todaySchedules,
                        nowEpochMillis = nowEpochMillis,
                        zoneId = zoneId,
                    )

                // 封面仅 4x4 hero 使用：加载失败/无地址时回退纯文本布局，仍是合法形态
                val cover =
                    state.orderedItems
                        .firstOrNull()
                        ?.coverUrl
                        ?.takeIf { it.isNotBlank() }
                        ?.let { url -> loadHeroCoverBitmap(context, url) }

                state to cover
            }

        provideContent {
            GlanceTheme(colors = ColorProviders(MiniBgmLightColors, MiniBgmDarkColors)) {
                // 顶层统一承载系统小组件背景与动态大圆角（Android 12+）
                Box(
                    modifier =
                        GlanceModifier
                            .fillMaxSize()
                            .background(GlanceTheme.colors.background)
                            .appWidgetBackground(),
                ) {
                    WidgetRoot(uiState = uiState, heroCover = heroCover)
                }
            }
        }
    }
}

/**
 * hero 封面小图：经 Coil（复用应用级 ImageLoader 的磁盘缓存与网络栈）取约 44x60dp@3x 的位图。
 * 请求失败（无网/解析失败/Koin 未就绪）一律返回 null，由调用方回退纯文本布局。
 */
private suspend fun loadHeroCoverBitmap(
    context: Context,
    url: String,
): Bitmap? =
    runCatching {
        val request =
            ImageRequest
                .Builder(context)
                .data(url)
                .size(132, 180)
                .build()
        val result = SingletonImageLoader.get(context).execute(request)
        ((result as? SuccessResult)?.image?.asDrawable(context.resources) as? BitmapDrawable)?.bitmap
    }.getOrNull()

@Composable
private fun WidgetRoot(
    uiState: ScheduleWidgetUiState,
    heroCover: Bitmap?,
) {
    val size = LocalSize.current
    when {
        uiState.orderedItems.isEmpty() -> {
            // 空态三态：未登录 / 登录但没在追 / 在追但 7 天窗口内无排期
            val context = LocalContext.current
            val (message, ctaText, action) =
                when {
                    !uiState.isLoggedIn ->
                        Triple(
                            context.getString(R.string.widget_empty_not_logged_in),
                            context.getString(R.string.widget_cta_login),
                            openLoginAction(),
                        )
                    !uiState.hasTrackedSubjects ->
                        Triple(
                            context.getString(R.string.widget_empty_no_tracking),
                            context.getString(R.string.widget_cta_open_schedule),
                            openScheduleAction(),
                        )
                    else ->
                        Triple(
                            context.getString(R.string.widget_empty_no_updates),
                            context.getString(R.string.widget_cta_view_schedule),
                            openScheduleAction(),
                        )
                }
            WidgetPlaceholder(message = message, ctaText = ctaText, action = action)
        }
        // 布局判断阈值与 companion 里的断点常量同源，杜绝阈值漂移。
        size.height < ScheduleWidget.SMALL_MIN.height ->
            LineWidgetContent(uiState = uiState)
        size.width < ScheduleWidget.MEDIUM_MIN.width ->
            CompactWidgetContent(uiState = uiState)
        size.width >= ScheduleWidget.LARGE_MIN.width && size.height >= ScheduleWidget.LARGE_MIN.height ->
            ExpandedWidgetContent(uiState = uiState, heroCover = heroCover)
        else ->
            MediumWidgetContent(uiState = uiState)
    }
}

/**
 * 单行条（高度 < 2x2 的扁长形态，如 2x1 / 4x1）：
 * 与三档布局同一套 hero 优先级（可看 -> 今日待播 -> 下一部），横向单行承载。
 */
@Composable
private fun LineWidgetContent(uiState: ScheduleWidgetUiState) {
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
private fun CompactWidgetContent(uiState: ScheduleWidgetUiState) {
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
private fun MediumWidgetContent(uiState: ScheduleWidgetUiState) {
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
private fun ExpandedWidgetContent(
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

/** 统一条目行：状态胶囊 + 番名 + 话数，4x2 与 4x4 共用 */
@Composable
private fun WidgetItemRow(
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
private fun StatusBadge(item: ScheduleWidgetItemUiModel) {
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
private fun WidgetHeader(
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
private fun WidgetPlaceholder(
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
private fun weekdayTextOf(uiState: ScheduleWidgetUiState): String {
    val context = LocalContext.current
    val weekdayIndex = LocalDate.ofEpochDay(uiState.todayEpochDay).dayOfWeek.value
    return context.widgetWeekday(weekdayIndex)
}

/** 「周X · M月D日」 */
@Composable
private fun dateLineOf(uiState: ScheduleWidgetUiState): String {
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
private fun headerTitleOf(uiState: ScheduleWidgetUiState): String {
    val context = LocalContext.current
    return when {
        !uiState.isLoggedIn -> context.getString(R.string.widget_title_calendar)
        uiState.totalToday > 0 -> context.getString(R.string.widget_title_today)
        else -> context.getString(R.string.widget_title_next)
    }
}

private fun Context.widgetWeekday(index: Int): String = resources.getStringArray(R.array.widget_weekdays)[(index - 1).coerceIn(0, 6)]

private fun dayLabel(
    context: Context,
    day: AirDay,
): String =
    when (day) {
        AirDay.Yesterday -> context.getString(R.string.widget_day_yesterday)
        AirDay.Tomorrow -> context.getString(R.string.widget_day_tomorrow)
        is AirDay.Weekday -> context.widgetWeekday(day.index)
    }

@Composable
private fun statusLineText(status: AirStatusLine): String {
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

private fun kindLabel(
    context: Context,
    airKind: String?,
): String? =
    when (airKind) {
        AirEventKind.SCHEDULED -> context.getString(R.string.widget_kind_scheduled)
        AirEventKind.PREDICTED -> context.getString(R.string.widget_kind_predicted)
        else -> null
    }

@Composable
private fun episodeText(item: ScheduleWidgetItemUiModel): String {
    val context = LocalContext.current
    return if (item.episode > 0) {
        context.getString(R.string.widget_episode, item.episode)
    } else {
        context.getString(R.string.widget_episode_latest)
    }
}

/** 「第X话」或「最新话」，附带表定/预估置信度标签 */
@Composable
private fun heroEpisodeLine(item: ScheduleWidgetItemUiModel): String {
    val context = LocalContext.current
    val episode = episodeText(item)
    val kind = kindLabel(context, item.airKind) ?: return episode
    return context.getString(R.string.widget_episode_with_kind, episode, kind)
}

// endregion

private fun textStyle(
    color: androidx.glance.unit.ColorProvider,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight? = null,
) = TextStyle(
    color = color,
    fontSize = fontSize,
    fontWeight = fontWeight,
)

@Composable
private fun openScheduleAction(): Action {
    val context = LocalContext.current
    return actionStartActivity(BgmNavIntents.createScheduleIntent(context))
}

@Composable
private fun openSubjectAction(subjectId: Long): Action {
    val context = LocalContext.current
    return actionStartActivity(BgmNavIntents.createSubjectIntent(context, subjectId))
}

@Composable
private fun openLoginAction(): Action {
    val context = LocalContext.current
    return actionStartActivity(BgmNavIntents.createLoginIntent(context))
}
