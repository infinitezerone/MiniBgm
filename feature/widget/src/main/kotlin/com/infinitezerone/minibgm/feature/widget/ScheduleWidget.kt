package com.infinitezerone.minibgm.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.material3.ColorProviders
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmDarkColors
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmLightColors
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.feature.widget.components.CompactWidgetContent
import com.infinitezerone.minibgm.feature.widget.components.ExpandedWidgetContent
import com.infinitezerone.minibgm.feature.widget.components.LineWidgetContent
import com.infinitezerone.minibgm.feature.widget.components.MediumWidgetContent
import com.infinitezerone.minibgm.feature.widget.components.WidgetPlaceholder
import com.infinitezerone.minibgm.feature.widget.components.openLoginAction
import com.infinitezerone.minibgm.feature.widget.components.openScheduleAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.time.Instant
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
