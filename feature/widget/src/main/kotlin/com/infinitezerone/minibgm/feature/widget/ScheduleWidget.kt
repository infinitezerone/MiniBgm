package com.infinitezerone.minibgm.feature.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
import androidx.glance.layout.ContentScale
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
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmDarkColors
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmLightColors
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.navigation.BgmNavIntents
import com.infinitezerone.minibgm.feature.widget.R
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.context.GlobalContext

/**
 * 现代化「今日更新」桌面小组件：
 * 1. 响应式布局：基于 SizeMode.Responsive 自适应 2x2（大微缩海报焦点卡片）、4x2（英雄双列/切分）、4x4（2列x3行大卡片流）；
 * 2. 全天覆盖与空间补全：当天已播剧集常驻展示「已更新」，追番不足时自动以今日新番日历平滑填补；
 * 3. 异步微缩防超限：RGB_565 微缩封面加载，严格约束 IPC Binder 传输体积；
 * 4. 细粒度深链：单项点击直达番剧详情页，右上角无感刷新回调。
 */
class ScheduleWidget : GlanceAppWidget() {
    companion object {
        val SMALL_SQUARE = DpSize(100.dp, 100.dp) // 2x2: 焦点卡片
        val MEDIUM_CARD = DpSize(220.dp, 100.dp) // 4x2: 英雄切分双列
        val LARGE_CARD = DpSize(220.dp, 220.dp) // 4x4: 2列x3行海报网格
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
        val subjectRepo = koin?.getOrNull<SubjectRepository>()
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

        // 异步并发预加载微缩封面（双重离线缓存 + 抗锯齿圆角裁剪）
        val coverBitmaps =
            coroutineScope {
                uiState.items
                    .take(6)
                    .map { item ->
                        async {
                            var url = item.coverUrl
                            if (url.isBlank() && subjectRepo != null) {
                                url =
                                    subjectRepo
                                        .getSubjectStream(item.subjectId)
                                        .firstOrNull()
                                        ?.images
                                        ?.bestImage
                                        .orEmpty()
                            }
                            item.subjectId to WidgetImageLoader.loadThumbnailBitmap(context, url)
                        }
                    }.awaitAll()
                    .toMap()
            }

        provideContent {
            GlanceTheme(colors = ColorProviders(MiniBgmLightColors, MiniBgmDarkColors)) {
                WidgetRoot(
                    uiState = uiState,
                    coverBitmaps = coverBitmaps,
                )
            }
        }
    }
}

@Composable
private fun WidgetRoot(
    uiState: ScheduleWidgetUiState,
    coverBitmaps: Map<Long, Bitmap?>,
) {
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
                coverBitmaps = coverBitmaps,
            )
        isExpanded ->
            ExpandedWidgetContent(
                items = uiState.items.take(6),
                coverBitmaps = coverBitmaps,
                headerTitle = uiState.headerTitle,
                headerSubtitle = uiState.headerSubtitle,
            )
        else ->
            MediumWidgetContent(
                items = uiState.items,
                coverBitmaps = coverBitmaps,
                headerTitle = uiState.headerTitle,
                headerSubtitle = uiState.headerSubtitle,
            )
    }
}

/**
 * 2x2 焦点看板：
 * 1. 顶部：简洁标题栏（左侧「今日追番」，右侧轻量周几/计数）；
 * 2. 核心区：左侧 54x76dp 纯净海报（无覆盖遮挡），右侧状态胶囊（「● 23:00 待播」/「✔ 已更新」）+ 粗体标题 + 话数；
 * 3. 底部：若今日还有后续待播番剧，显示微型引导（「还有 X 部待播 ›」）。
 */
@Composable
private fun CompactWidgetContent(
    uiState: ScheduleWidgetUiState,
    coverBitmaps: Map<Long, Bitmap?>,
) {
    val item = uiState.items.first()
    val bitmap = coverBitmaps[item.subjectId]

    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .cornerRadius(16.dp)
                .clickable(openSubjectAction(item.subjectId))
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // 顶部精炼状态条
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

        Spacer(modifier = GlanceModifier.height(5.dp))

        // 主体卡片
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverThumbnail(
                bitmap = bitmap,
                width = 54.dp,
                height = 76.dp,
                cornerRadiusDp = 8.dp,
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 状态胶囊
                Box(
                    modifier =
                        GlanceModifier
                            .background(
                                if (item.isAiredToday) {
                                    GlanceTheme.colors.tertiaryContainer
                                } else {
                                    GlanceTheme.colors.primaryContainer
                                },
                            ).cornerRadius(4.dp)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text =
                            if (item.isAiredToday) {
                                "✔ 已更新"
                            } else if (item.airTimeCst.isNotBlank()) {
                                "● ${item.airTimeCst} 待播"
                            } else {
                                item.countdownBadge
                            },
                        maxLines = 1,
                        style =
                            TextStyle(
                                color =
                                    if (item.isAiredToday) {
                                        GlanceTheme.colors.onTertiaryContainer
                                    } else {
                                        GlanceTheme.colors.onPrimaryContainer
                                    },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                }
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = item.title,
                    maxLines = 2,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                Spacer(modifier = GlanceModifier.height(3.dp))
                Text(
                    text = if (item.episode > 0) "第${item.episode}话" else "最新话",
                    maxLines = 1,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }
        }

        // 底部引导条
        if (uiState.items.size > 1) {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Row(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .clickable(openScheduleAction()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "还有 ${uiState.items.size - 1} 部更新",
                    maxLines = 1,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.outline,
                            fontSize = 8.sp,
                        ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    text = "›",
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

/**
 * 4x2 现代化混合看板 (Hero Spotlight + Timeline Queue)：
 * 左侧 50%：焦点英雄卡片（最近或下一部开播），拥有海报、大标题、开播胶囊与倒计时；
 * 右侧 50%：今日排期清单（Timeline），按时刻清晰列出今日后续或全部番剧，杜绝微缩画廊的逼仄感。
 */
@Composable
private fun MediumWidgetContent(
    items: List<ScheduleWidgetItemUiModel>,
    coverBitmaps: Map<Long, Bitmap?>,
    headerTitle: String,
    headerSubtitle: String,
) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        WidgetHeader(
            title = headerTitle,
            subtitle = headerSubtitle,
            onScheduleClick = openScheduleAction(),
            onRefreshClick = actionRunCallback<RefreshScheduleWidgetCallback>(),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))

        val heroItem = items.first()
        val queueItems = items.drop(1)

        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左半区：焦点番剧卡片
            Row(
                modifier =
                    GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .background(GlanceTheme.colors.surfaceVariant)
                        .cornerRadius(10.dp)
                        .clickable(openSubjectAction(heroItem.subjectId))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverThumbnail(
                    bitmap = coverBitmaps[heroItem.subjectId],
                    width = 52.dp,
                    height = 74.dp,
                    cornerRadiusDp = 8.dp,
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Column(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            GlanceModifier
                                .background(
                                    if (heroItem.isAiredToday) {
                                        GlanceTheme.colors.tertiaryContainer
                                    } else {
                                        GlanceTheme.colors.primaryContainer
                                    },
                                ).cornerRadius(4.dp)
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text =
                                if (heroItem.isAiredToday) {
                                    "✔ 已更新"
                                } else if (heroItem.airTimeCst.isNotBlank()) {
                                    "● ${heroItem.airTimeCst} 待播"
                                } else {
                                    heroItem.countdownBadge
                                },
                            maxLines = 1,
                            style =
                                TextStyle(
                                    color =
                                        if (heroItem.isAiredToday) {
                                            GlanceTheme.colors.onTertiaryContainer
                                        } else {
                                            GlanceTheme.colors.onPrimaryContainer
                                        },
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
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
                    )
                    Spacer(modifier = GlanceModifier.height(3.dp))
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
                    // 今日仅 1 部番剧
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
                    // 今日有 2 部或更多番剧，按顺序展示日程行
                    val displayQueue = queueItems.take(2)
                    displayQueue.forEachIndexed { index, queueItem ->
                        Row(
                            modifier =
                                GlanceModifier
                                    .fillMaxWidth()
                                    .defaultWeight()
                                    .clickable(openSubjectAction(queueItem.subjectId)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 时间胶囊
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
                            Spacer(modifier = GlanceModifier.height(3.dp))
                        }
                    }

                    // 底部查看更多指示器
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Row(
                        modifier =
                            GlanceModifier
                                .fillMaxWidth()
                                .clickable(openScheduleAction()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                if (queueItems.size > 2) {
                                    "还有 ${queueItems.size - 2} 部更新"
                                } else {
                                    "查看全部时间表"
                                },
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

/** 4x4 大组件展台：2行 x 3列 (共 6 部) 海报卡片流，视野极其饱满，全面展示今日排片 */
@Composable
private fun ExpandedWidgetContent(
    items: List<ScheduleWidgetItemUiModel>,
    coverBitmaps: Map<Long, Bitmap?>,
    headerTitle: String,
    headerSubtitle: String,
) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        WidgetHeader(
            title = headerTitle,
            subtitle = headerSubtitle,
            onScheduleClick = openScheduleAction(),
            onRefreshClick = actionRunCallback<RefreshScheduleWidgetCallback>(),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        val topRowItems = items.take(3)
        val bottomRowItems = items.drop(3).take(3)

        // 第一行海报列 (最多 3 部)
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            topRowItems.forEachIndexed { index, item ->
                PosterCardItem(
                    item = item,
                    bitmap = coverBitmaps[item.subjectId],
                    modifier = GlanceModifier.defaultWeight(),
                    posterWidth = 54.dp,
                    posterHeight = 76.dp,
                )
                if (index < topRowItems.size - 1) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                }
            }
            val emptySlots = 3 - topRowItems.size
            repeat(emptySlots) {
                Spacer(modifier = GlanceModifier.defaultWeight())
            }
        }

        if (bottomRowItems.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            // 第二行海报列 (最多 3 部)
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                bottomRowItems.forEachIndexed { index, item ->
                    PosterCardItem(
                        item = item,
                        bitmap = coverBitmaps[item.subjectId],
                        modifier = GlanceModifier.defaultWeight(),
                        posterWidth = 54.dp,
                        posterHeight = 76.dp,
                    )
                    if (index < bottomRowItems.size - 1) {
                        Spacer(modifier = GlanceModifier.width(6.dp))
                    }
                }
                val emptySlots = 3 - bottomRowItems.size
                repeat(emptySlots) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

/** 单张竖版海报卡片：纯净海报 + 状态时间药丸 + 标题 + 话数 */
@Composable
private fun PosterCardItem(
    item: ScheduleWidgetItemUiModel,
    bitmap: Bitmap?,
    modifier: GlanceModifier = GlanceModifier,
    posterWidth: Dp = 54.dp,
    posterHeight: Dp = 76.dp,
) {
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .cornerRadius(8.dp)
                .clickable(openSubjectAction(item.subjectId))
                .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverThumbnail(
            bitmap = bitmap,
            width = posterWidth,
            height = posterHeight,
            cornerRadiusDp = 8.dp,
        )

        Spacer(modifier = GlanceModifier.height(3.dp))

        // 纯净状态胶囊，不再遮挡海报人物面孔
        Box(
            modifier =
                GlanceModifier
                    .background(
                        if (item.isAiredToday) {
                            GlanceTheme.colors.tertiaryContainer
                        } else {
                            GlanceTheme.colors.surfaceVariant
                        },
                    ).cornerRadius(3.dp)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            Text(
                text =
                    if (item.isAiredToday) {
                        "已更新"
                    } else if (item.airTimeCst.isNotBlank()) {
                        item.airTimeCst
                    } else {
                        item.countdownBadge
                    },
                maxLines = 1,
                style =
                    TextStyle(
                        color =
                            if (item.isAiredToday) {
                                GlanceTheme.colors.onTertiaryContainer
                            } else {
                                GlanceTheme.colors.onSurfaceVariant
                            },
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
        }

        Spacer(modifier = GlanceModifier.height(2.dp))

        Text(
            text = item.title,
            maxLines = 1,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
        )

        Spacer(modifier = GlanceModifier.height(1.dp))

        Text(
            text = if (item.episode > 0) "第${item.episode}话" else "更新中",
            maxLines = 1,
            style =
                TextStyle(
                    color = if (item.isAiredToday) GlanceTheme.colors.primary else GlanceTheme.colors.outline,
                    fontSize = 8.sp,
                    fontWeight = if (item.isAiredToday) FontWeight.Medium else FontWeight.Normal,
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
private fun CoverThumbnail(
    bitmap: Bitmap?,
    width: Dp,
    height: Dp,
    cornerRadiusDp: Dp = 6.dp,
) {
    Box(
        modifier =
            GlanceModifier
                .size(width, height)
                .cornerRadius(cornerRadiusDp)
                .background(GlanceTheme.colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize(),
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_poster_placeholder),
                contentDescription = null,
                modifier = GlanceModifier.size(width * 0.45f),
            )
        }
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
                .padding(top = 2.dp, end = 2.dp)
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
