package com.infinitezerone.minibgm.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmDarkColors
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmLightColors
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.navigation.BgmNavIntents
import com.infinitezerone.minibgm.feature.widget.R
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.context.GlobalContext

/**
 * 现代化「今日更新」极简桌面排期小组件：
 * 1. 响应式布局：基于 SizeMode.Responsive 自适应 2x2（焦点排期卡片）、4x2（英雄双列时间线）、4x4（全天排期看板）；
 * 2. 纯文本与时间轴：零 Bitmap、零网络延迟，彻底避免 IPC Binder 1MB 事务超限 (TransactionTooLargeException)；
 * 3. 全天覆盖与空间补全：当天已播剧集常驻展示「已更新」，追番不足时自动以今日新番日历平滑填补；
 * 4. 细粒度深链：单项点击直达番剧详情页，右上角无感刷新回调。
 */
class ScheduleWidget : GlanceAppWidget() {
    companion object {
        val SMALL_SQUARE = DpSize(100.dp, 100.dp) // 2x2: 焦点卡片
        val MEDIUM_CARD = DpSize(220.dp, 100.dp) // 4x2: 英雄双列
        val LARGE_CARD = DpSize(220.dp, 220.dp) // 4x4: 全天排期看板
        const val HOURS_AHEAD = 24L
        const val LOOKBACK_HOURS = 18L
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL_SQUARE, MEDIUM_CARD, LARGE_CARD))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val koin = GlobalContext.getOrNull()
        val isLoggedIn =
            koin
                ?.getOrNull<UserPreferencesDataSource>()
                ?.userPreferences
                ?.firstOrNull()
                ?.isLoggedIn == true
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

        val todayWeekday = TimeUtils.jstWeekdayOfEpoch(System.currentTimeMillis())
        val todaySchedules =
            scheduleRepo
                ?.getSchedulesByWeekday(todayWeekday)
                ?.firstOrNull()
                .orEmpty()

        val uiState =
            ScheduleWidgetPlanner.plan(
                isLoggedIn = isLoggedIn,
                upcoming = upcoming,
                todaySchedules = todaySchedules,
                nowEpochMillis = System.currentTimeMillis(),
                maxItems = 6,
            )

        provideContent {
            GlanceTheme(colors = ColorProviders(MiniBgmLightColors, MiniBgmDarkColors)) {
                WidgetRoot(uiState = uiState)
            }
        }
    }
}

@Composable
private fun WidgetRoot(uiState: ScheduleWidgetUiState) {
    val size = LocalSize.current
    val isCompact = size.width < 210.dp
    val isExpanded = size.width >= 210.dp && size.height >= 230.dp

    when {
        uiState.items.isEmpty() -> {
            if (!uiState.isLoggedIn) {
                WidgetPlaceholder(
                    message = "登录小番盒后，汇总「我追的」每日更新",
                    ctaText = "去登录 ›",
                    action = openLoginAction(),
                )
            } else {
                WidgetPlaceholder(
                    message = "今日暂无更新或正在同步时间表",
                    ctaText = "查看全部时间表 ›",
                    action = openScheduleAction(),
                )
            }
        }
        isCompact ->
            CompactWidgetContent(
                uiState = uiState,
            )
        isExpanded ->
            ExpandedWidgetContent(
                uiState = uiState,
            )
        else ->
            MediumWidgetContent(
                uiState = uiState,
            )
    }
}

/**
 * 2x2 极简焦点排期卡片：
 * 1. 顶部：简洁标题栏（左侧「今日追番」，右侧轻量周几/计数）；
 * 2. 焦点项：核心展示即将播出的番剧，大号状态胶囊 + 标题 + 话数与倒计时；
 * 3. 次要项/提示：若今日有后续番剧，展示单行紧凑预览或「还有 X 部待播 ›」。
 */
@Composable
private fun CompactWidgetContent(uiState: ScheduleWidgetUiState) {
    val heroItem = uiState.items.first()
    val secondaryItem = uiState.items.getOrNull(1)

    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .cornerRadius(16.dp)
                .clickable(openSubjectAction(heroItem.subjectId))
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // 顶部状态条
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (uiState.hasTrackedItems) "今日追番" else "今日新番",
                maxLines = 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = if (uiState.items.size > 1) "共${uiState.items.size}部" else uiState.headerSubtitle,
                maxLines = 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.outline,
                        fontSize = 9.sp,
                    ),
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // 焦点卡片主体
        Column(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(10.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            // 状态胶囊与倒计时
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(
                    isAiredToday = heroItem.isAiredToday,
                    airTimeCst = heroItem.airTimeCst,
                    countdownBadge = heroItem.countdownBadge,
                )
                if (!heroItem.isAiredToday && heroItem.countdownBadge.isNotBlank() && heroItem.airTimeCst.isNotBlank()) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    CountdownBadgeView(
                        text = heroItem.countdownBadge,
                        isAiredToday = false,
                        fontSize = 8.sp,
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            Text(
                text = heroItem.title,
                maxLines = 2,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                modifier = GlanceModifier.defaultWeight(),
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (heroItem.episode > 0) "第${heroItem.episode}话" else "最新话",
                    maxLines = 1,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                if (heroItem.kindTag != null) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "· ${heroItem.kindTag}",
                        maxLines = 1,
                        style =
                            TextStyle(
                                color = GlanceTheme.colors.outline,
                                fontSize = 9.sp,
                            ),
                    )
                }
            }
        }

        // 次级番剧或查看更多引导
        if (secondaryItem != null) {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Row(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .clickable(openSubjectAction(secondaryItem.subjectId)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = secondaryItem.airTimeCst.ifBlank { "稍后" },
                    maxLines = 1,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.outline,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = secondaryItem.title,
                    maxLines = 1,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 9.sp,
                        ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (uiState.items.size > 2) {
                    Text(
                        text = "+${uiState.items.size - 2}",
                        style =
                            TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * 4x2 现代化双列看板 (Hero Spotlight + Timeline Queue)：
 * 左侧 45%：焦点英雄卡片（最近或下一部开播），展示大标题、开播胶囊、话数与倒计时；
 * 右侧 55%：今日排期时间线（Timeline），按时刻清晰列出今日后续番剧清单。
 */
@Composable
private fun MediumWidgetContent(uiState: ScheduleWidgetUiState) {
    val items = uiState.items
    val heroItem = items.first()
    val queueItems = items.drop(1)

    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        WidgetHeader(
            title = uiState.headerTitle,
            subtitle = uiState.headerSubtitle,
            onScheduleClick = openScheduleAction(),
            onRefreshClick = actionRunCallback<RefreshScheduleWidgetCallback>(),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左半区：焦点番剧卡片
            Column(
                modifier =
                    GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .background(GlanceTheme.colors.surfaceVariant)
                        .cornerRadius(10.dp)
                        .clickable(openSubjectAction(heroItem.subjectId))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge(
                        isAiredToday = heroItem.isAiredToday,
                        airTimeCst = heroItem.airTimeCst,
                        countdownBadge = heroItem.countdownBadge,
                    )
                    if (!heroItem.isAiredToday && heroItem.countdownBadge.isNotBlank() && heroItem.airTimeCst.isNotBlank()) {
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        CountdownBadgeView(
                            text = heroItem.countdownBadge,
                            isAiredToday = false,
                            fontSize = 8.sp,
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                Text(
                    text = heroItem.title,
                    maxLines = 2,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    modifier = GlanceModifier.defaultWeight(),
                )

                Spacer(modifier = GlanceModifier.height(3.dp))

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (heroItem.episode > 0) "第${heroItem.episode}话" else "最新话",
                        maxLines = 1,
                        style =
                            TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                    if (heroItem.kindTag != null) {
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = "· ${heroItem.kindTag}",
                            maxLines = 1,
                            style =
                                TextStyle(
                                    color = GlanceTheme.colors.outline,
                                    fontSize = 9.sp,
                                ),
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            // 右半区：今日日程时间线列表
            Column(
                modifier =
                    GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (queueItems.isEmpty()) {
                    Column(
                        modifier =
                            GlanceModifier
                                .fillMaxSize()
                                .clickable(openScheduleAction())
                                .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "今日追番仅此 1 部",
                            maxLines = 1,
                            style =
                                TextStyle(
                                    color = GlanceTheme.colors.onBackground,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                        Spacer(modifier = GlanceModifier.height(3.dp))
                        Text(
                            text = "准时守候，不见不散",
                            maxLines = 1,
                            style =
                                TextStyle(
                                    color = GlanceTheme.colors.outline,
                                    fontSize = 9.sp,
                                ),
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = "查看本周完整时间表 ›",
                            maxLines = 1,
                            style =
                                TextStyle(
                                    color = GlanceTheme.colors.primary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                        )
                    }
                } else {
                    val displayQueue = queueItems.take(3)
                    displayQueue.forEachIndexed { index, queueItem ->
                        Row(
                            modifier =
                                GlanceModifier
                                    .fillMaxWidth()
                                    .defaultWeight()
                                    .clickable(openSubjectAction(queueItem.subjectId)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    GlanceModifier
                                        .background(GlanceTheme.colors.surfaceVariant)
                                        .cornerRadius(4.dp)
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = queueItem.airTimeCst.ifBlank { "待播" },
                                    maxLines = 1,
                                    style =
                                        TextStyle(
                                            color = GlanceTheme.colors.onSurfaceVariant,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                )
                            }
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Text(
                                text = queueItem.title,
                                maxLines = 1,
                                style =
                                    TextStyle(
                                        color = GlanceTheme.colors.onBackground,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            Spacer(modifier = GlanceModifier.width(4.dp))
                            Text(
                                text = if (queueItem.episode > 0) "第${queueItem.episode}话" else "更新",
                                maxLines = 1,
                                style =
                                    TextStyle(
                                        color = GlanceTheme.colors.outline,
                                        fontSize = 9.sp,
                                    ),
                            )
                        }
                        if (index < displayQueue.size - 1) {
                            Spacer(modifier = GlanceModifier.height(2.dp))
                        }
                    }

                    if (queueItems.size > 3) {
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Row(
                            modifier =
                                GlanceModifier
                                    .fillMaxWidth()
                                    .clickable(openScheduleAction()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "还有 ${queueItems.size - 3} 部待播",
                                maxLines = 1,
                                style =
                                    TextStyle(
                                        color = GlanceTheme.colors.primary,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                            )
                            Text(
                                text = " ›",
                                style =
                                    TextStyle(
                                        color = GlanceTheme.colors.primary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 4x4 大组件排期看板：顶部焦点大卡片 + 5 行全天时间线清单，信息极其饱满 */
@Composable
private fun ExpandedWidgetContent(uiState: ScheduleWidgetUiState) {
    val items = uiState.items
    val heroItem = items.first()
    val timelineItems = items.drop(1).take(5)

    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        WidgetHeader(
            title = uiState.headerTitle,
            subtitle = uiState.headerSubtitle,
            onScheduleClick = openScheduleAction(),
            onRefreshClick = actionRunCallback<RefreshScheduleWidgetCallback>(),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        // 顶部高光卡片 (Hero Card)
        Column(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .background(GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(12.dp)
                    .clickable(openSubjectAction(heroItem.subjectId))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(
                    isAiredToday = heroItem.isAiredToday,
                    airTimeCst = heroItem.airTimeCst,
                    countdownBadge = heroItem.countdownBadge,
                )
                if (!heroItem.isAiredToday && heroItem.countdownBadge.isNotBlank() && heroItem.airTimeCst.isNotBlank()) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    CountdownBadgeView(
                        text = heroItem.countdownBadge,
                        isAiredToday = false,
                        fontSize = 8.sp,
                    )
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = if (heroItem.episode > 0) "第${heroItem.episode}话" else "最新话",
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            Text(
                text = heroItem.title,
                maxLines = 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // 标题栏：今日日程时间线
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "今日后续排期",
                style =
                    TextStyle(
                        color = GlanceTheme.colors.outline,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            if (items.size > 6) {
                Text(
                    text = "还有 ${items.size - 6} 部",
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.outline,
                            fontSize = 8.sp,
                        ),
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        // 列表区
        if (timelineItems.isEmpty()) {
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
                    text = "今日后续暂无其他番剧播出",
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.outline,
                            fontSize = 10.sp,
                        ),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "查看本周完整时间表 ›",
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                )
            }
        } else {
            Column(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            ) {
                timelineItems.forEachIndexed { index, item ->
                    Row(
                        modifier =
                            GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight()
                                .clickable(openSubjectAction(item.subjectId))
                                .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 播出时刻胶囊
                        Box(
                            modifier =
                                GlanceModifier
                                    .background(GlanceTheme.colors.surfaceVariant)
                                    .cornerRadius(4.dp)
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = item.airTimeCst.ifBlank { "待播" },
                                maxLines = 1,
                                style =
                                    TextStyle(
                                        color = GlanceTheme.colors.onSurfaceVariant,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        // 番剧名
                        Text(
                            text = item.title,
                            maxLines = 1,
                            style =
                                TextStyle(
                                    color = GlanceTheme.colors.onBackground,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            modifier = GlanceModifier.defaultWeight(),
                        )

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        // 话数
                        Text(
                            text = if (item.episode > 0) "第${item.episode}话" else "更新",
                            maxLines = 1,
                            style =
                                TextStyle(
                                    color = GlanceTheme.colors.outline,
                                    fontSize = 9.sp,
                                ),
                        )

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        // 状态标签
                        Text(
                            text = if (item.isAiredToday) "已播" else item.countdownBadge,
                            maxLines = 1,
                            style =
                                TextStyle(
                                    color =
                                        if (item.isAiredToday) {
                                            GlanceTheme.colors.primary
                                        } else {
                                            GlanceTheme.colors.outline
                                        },
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                    }
                    if (index < timelineItems.size - 1) {
                        Spacer(modifier = GlanceModifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    isAiredToday: Boolean,
    airTimeCst: String,
    countdownBadge: String,
) {
    Box(
        modifier =
            GlanceModifier
                .background(
                    if (isAiredToday) {
                        GlanceTheme.colors.tertiaryContainer
                    } else {
                        GlanceTheme.colors.primaryContainer
                    },
                ).cornerRadius(4.dp)
                .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text =
                if (isAiredToday) {
                    "✔ 已更新"
                } else if (airTimeCst.isNotBlank()) {
                    "● $airTimeCst 待播"
                } else {
                    countdownBadge
                },
            maxLines = 1,
            style =
                TextStyle(
                    color =
                        if (isAiredToday) {
                            GlanceTheme.colors.onTertiaryContainer
                        } else {
                            GlanceTheme.colors.onPrimaryContainer
                        },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                ),
        )
    }
}

@Composable
private fun WidgetHeader(
    title: String,
    subtitle: String,
    onScheduleClick: Action,
    onRefreshClick: Action,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            maxLines = 1,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = "· $subtitle",
                maxLines = 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.outline,
                        fontSize = 10.sp,
                    ),
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = "刷新",
            modifier =
                GlanceModifier
                    .size(16.dp)
                    .clickable(onRefreshClick),
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Text(
            text = "时间表 ›",
            maxLines = 1,
            modifier = GlanceModifier.clickable(onScheduleClick),
            style =
                TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
        )
    }
}

@Composable
private fun CountdownBadgeView(
    text: String,
    isAiredToday: Boolean,
    fontSize: TextUnit = 8.sp,
) {
    if (text.isBlank()) return
    val (bgColor, textColor) =
        when {
            isAiredToday -> GlanceTheme.colors.tertiaryContainer to GlanceTheme.colors.onTertiaryContainer
            text == "刚刚开播" -> GlanceTheme.colors.errorContainer to GlanceTheme.colors.onErrorContainer
            else -> GlanceTheme.colors.primaryContainer to GlanceTheme.colors.onPrimaryContainer
        }

    Box(
        modifier =
            GlanceModifier
                .background(bgColor)
                .cornerRadius(3.dp)
                .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            maxLines = 1,
            style =
                TextStyle(
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                ),
        )
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
                .background(GlanceTheme.colors.background)
                .cornerRadius(16.dp)
                .clickable(action)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            maxLines = 2,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 12.sp,
                ),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = ctaText,
            maxLines = 1,
            style =
                TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
        )
    }
}

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
