package com.infinitezerone.minibgm.feature.schedule

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.common.onSuccess
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.NextUpAction
import com.infinitezerone.minibgm.core.model.NextUpUrgency
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.model.matchesForEpisode
import com.infinitezerone.minibgm.core.model.sortedBySitePriority
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** 星期与真实日期模型 */
@Immutable
data class WeekdayDateItem(
    val weekday: Int, // 1=周一, ..., 7=周日
    val weekdayLabel: String, // "周一", "周二" ...
    val dateLabel: String, // "9/3"
    val isToday: Boolean,
    val pageIndex: Int = 0,
    val dateString: String = "",
    val dayOffset: Int = 0,
)

/** 「放送」Tab 的单一不可变 UI 状态 */
@Immutable
data class ScheduleUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedPageIndex: Int = ScheduleViewModel.TODAY_PAGE_INDEX,
    val selectedWeekday: Int = ScheduleViewModel.currentLocalDate().dayOfWeek.value,
    val todayWeekday: Int = ScheduleViewModel.currentLocalDate().dayOfWeek.value,
    val todayPageIndex: Int = ScheduleViewModel.TODAY_PAGE_INDEX,
    val dateItems: List<WeekdayDateItem> = emptyList(),
    val daySchedules: Map<Int, List<AirSchedule>> = emptyMap(),
    val weeklySchedules: Map<Int, List<AirSchedule>> = emptyMap(),
    val watchingSubjectIds: Set<Long> = emptySet(),
    val onlyWatching: Boolean = false,
    val nextUpAction: NextUpAction? = null,
    /** 续看卡的应用内直达路由：按追番进度定位下一待看集（null = 数据未就绪，卡片退回外部跳转） */
    val nextUpPlayRoute: PlayerRoute? = null,
    val isActionDismissed: Boolean = false,
    val showLoginPromptDialog: Boolean = false,
    val isLoggedIn: Boolean = false,
) {
    /** 兼容旧接口：当前所选星期的原始番剧列表 */
    val schedules: List<AirSchedule>
        get() = daySchedules[selectedPageIndex] ?: weeklySchedules[selectedWeekday].orEmpty()

    /** 本地是否已有时刻表数据（用于本地优先策略，有数据时绝不展示全屏骨架图） */
    val hasSchedules: Boolean
        get() = daySchedules.values.any { it.isNotEmpty() } || weeklySchedules.values.any { it.isNotEmpty() }

    /** 是否处于离线缓存展示状态：有网络错误发生但本地有缓存数据 */
    val isOfflineCache: Boolean
        get() = error != null && hasSchedules

    /** 当前选中天经过筛选与时间线排序后的条目 */
    val currentDaySchedules: List<AirSchedule>
        get() = getSortedSchedulesForPage(selectedPageIndex, onlyWatching)

    /** 获取指定天（pageIndex）过滤并排序后的条目 */
    fun getSortedSchedulesForPage(
        pageIndex: Int,
        onlyWatchingFilter: Boolean = onlyWatching,
    ): List<AirSchedule> {
        val raw =
            daySchedules[pageIndex] ?: run {
                val weekday = dateItems.getOrNull(pageIndex)?.weekday ?: selectedWeekday
                weeklySchedules[weekday].orEmpty()
            }
        val list =
            if (onlyWatchingFilter) {
                raw.filter { watchingSubjectIds.contains(it.bgmId) }
            } else {
                raw
            }
        return list.sortedWith(
            compareBy<AirSchedule> {
                val time = it.timeCst.ifBlank { it.timeJst }
                if (time.isNotBlank()) 0 else 1
            }.thenBy {
                it.timeCst.ifBlank { it.timeJst }
            },
        )
    }

    fun getTimedSchedulesForPage(pageIndex: Int): List<AirSchedule> =
        getSortedSchedulesForPage(pageIndex).filter {
            (it.timeCst.ifBlank { it.timeJst }).isNotBlank()
        }

    fun getTimeGroupedSchedulesForPage(pageIndex: Int): Map<String, List<AirSchedule>> {
        val timed = getTimedSchedulesForPage(pageIndex)
        val linkedMap = linkedMapOf<String, MutableList<AirSchedule>>()
        for (item in timed) {
            val timeKey = item.timeCst.ifBlank { item.timeJst }
            linkedMap.getOrPut(timeKey) { mutableListOf() }.add(item)
        }
        return linkedMap
    }

    fun getAllDaySchedulesForPage(pageIndex: Int): List<AirSchedule> =
        getSortedSchedulesForPage(pageIndex).filter {
            (it.timeCst.ifBlank { it.timeJst }).isBlank()
        }

    fun getWatchingCountForPage(pageIndex: Int): Int {
        val list =
            daySchedules[pageIndex] ?: run {
                val weekday = dateItems.getOrNull(pageIndex)?.weekday ?: 1
                weeklySchedules[weekday].orEmpty()
            }
        return list.count { watchingSubjectIds.contains(it.bgmId) }
    }

    fun getTotalCountForPage(pageIndex: Int): Int {
        val list =
            daySchedules[pageIndex] ?: run {
                val weekday = dateItems.getOrNull(pageIndex)?.weekday ?: 1
                weeklySchedules[weekday].orEmpty()
            }
        return list.size
    }

    /** 兼容旧接口：获取指定星期过滤并排序后的条目 */
    fun getSortedSchedulesForWeekday(
        weekday: Int,
        onlyWatchingFilter: Boolean = onlyWatching,
    ): List<AirSchedule> {
        val pageIndex = findPageIndexForWeekday(weekday)
        return getSortedSchedulesForPage(pageIndex, onlyWatchingFilter)
    }

    fun getTimedSchedulesForWeekday(weekday: Int): List<AirSchedule> = getTimedSchedulesForPage(findPageIndexForWeekday(weekday))

    fun getTimeGroupedSchedulesForWeekday(weekday: Int): Map<String, List<AirSchedule>> =
        getTimeGroupedSchedulesForPage(findPageIndexForWeekday(weekday))

    fun getAllDaySchedulesForWeekday(weekday: Int): List<AirSchedule> = getAllDaySchedulesForPage(findPageIndexForWeekday(weekday))

    fun getWatchingCountForWeekday(weekday: Int): Int = getWatchingCountForPage(findPageIndexForWeekday(weekday))

    fun getTotalCountForWeekday(weekday: Int): Int = getTotalCountForPage(findPageIndexForWeekday(weekday))

    private fun findPageIndexForWeekday(weekday: Int): Int {
        val currentWeekRange = (-todayWeekday + 1)..(7 - todayWeekday)
        return dateItems
            .indexOfFirst { it.weekday == weekday && it.dayOffset in currentWeekRange }
            .takeIf { it >= 0 }
            ?: dateItems
                .indexOfFirst { it.weekday == weekday }
                .takeIf { it >= 0 }
            ?: (weekday - 1).coerceIn(0, (dateItems.size - 1).coerceAtLeast(0))
    }

    /** 今日正在追番的更新列表（在“今天”视图置顶呈现） */
    val todayWatchingSchedules: List<AirSchedule>
        get() {
            val todayRaw = daySchedules[todayPageIndex] ?: weeklySchedules[todayWeekday].orEmpty()
            return todayRaw
                .filter { watchingSubjectIds.contains(it.bgmId) }
                .sortedWith(
                    compareBy<AirSchedule> {
                        val time = it.timeCst.ifBlank { it.timeJst }
                        if (time.isNotBlank()) 0 else 1
                    }.thenBy {
                        it.timeCst.ifBlank { it.timeJst }
                    },
                )
        }
}

/**
 * 每周放送时刻表 ViewModel：
 * - 聚合每周 7 天全部番剧，支持单流连续时间长卷与快速锚点跳转；
 * - 响应式监听用户在看收藏，支持“我追的更新”过滤与卡片 1 键追番；
 * - 智能提取“昨日/前天在追待补更新”与“昨日播映速览”，实现今日首屏极速消费；
 * - 提供时间段聚合槽逻辑，释放空间并消除同时间冗余；
 * - 离线优先展示，支持下拉刷新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val collectionRepository: CollectionRepository,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val subjectRepository: SubjectRepository,
) : ViewModel() {
    private val selectedPageIndex = MutableStateFlow(TODAY_PAGE_INDEX)
    private val selectedWeekday = MutableStateFlow(currentLocalDate().dayOfWeek.value)
    private val onlyWatching = MutableStateFlow(false)
    private val isRefreshing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val showLoginPromptDialog = MutableStateFlow(false)

    private val isLoggedIn =
        authRepository.isLoggedIn
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _userMessage = Channel<String>(Channel.BUFFERED)
    val userMessage: Flow<String> = _userMessage.receiveAsFlow()

    // 监听全量放送流与播出事件，聚合生成 13 天时刻表（以今天为中心锚点，前6天 + 今天 + 后6天）
    private val scheduleDataFlow: Flow<Pair<Map<Int, List<AirSchedule>>, Map<Int, List<AirSchedule>>>> =
        combine(
            scheduleRepository.getAllSchedulesStream(),
            scheduleRepository.getAllAirEventsStream(),
        ) { allSchedules, allAirEvents ->
            val currentToday = currentLocalDate()
            val dateItems = calculateDateItems(currentToday)
            val todayWeekday = currentToday.dayOfWeek.value
            val currentWeekRange = (-todayWeekday + 1)..(7 - todayWeekday)

            val schedulesByBgmId = allSchedules.associateBy { it.bgmId }
            val eventsByDate = allAirEvents.groupBy { TimeUtils.formatIsoToCstDate(it.airAtUtc) }
            val subjectIdsWithEvents = allAirEvents.map { it.subjectId }.toSet()

            val dayMap = (0 until TOTAL_SCHEDULE_DAYS).associateWith { mutableListOf<AirSchedule>() }

            dateItems.forEachIndexed { pageIndex, item ->
                val dateStr = item.dateString
                val weekday = item.weekday
                val list = dayMap[pageIndex]!!

                // 1. 命中当天具体 air_events 的条目
                val eventsOnThisDay = eventsByDate[dateStr].orEmpty()
                val seenSubjectIdsOnThisDay = mutableSetOf<Long>()

                eventsOnThisDay.groupBy { it.subjectId }.forEach { (subId, events) ->
                    val bestEvent = events.maxByOrNull { it.episode } ?: events.first()
                    val schedule = schedulesByBgmId[subId]
                    if (schedule != null) {
                        seenSubjectIdsOnThisDay.add(subId)
                        list.add(
                            schedule.copy(
                                weekday = weekday,
                                nextEpisodeNumber = bestEvent.episode,
                                nextEpisodeAtUtc = bestEvent.airAtUtc,
                                timeCst = TimeUtils.formatToCstTime(bestEvent.airAtUtc).ifBlank { schedule.timeCst },
                                timeJst = TimeUtils.formatToJstTime(bestEvent.airAtUtc).ifBlank { schedule.timeJst },
                            ),
                        )
                    }
                }

                // 2. 没有在 air_events 出现过的番剧（如纯静态 bangumi-data 或单周兜底）
                allSchedules.forEach { schedule ->
                    if (schedule.bgmId in seenSubjectIdsOnThisDay) return@forEach
                    if (schedule.bgmId in subjectIdsWithEvents) return@forEach

                    if (schedule.nextEpisodeAtUtc.isNotBlank()) {
                        val scheduleDate = TimeUtils.formatIsoToCstDate(schedule.nextEpisodeAtUtc)
                        if (scheduleDate == dateStr) {
                            list.add(schedule.copy(weekday = weekday))
                        }
                    } else if (schedule.weekday == weekday && item.dayOffset in currentWeekRange) {
                        list.add(schedule)
                    }
                }
            }

            // 保持对 1..7 周视图的向下兼容
            val weeklyMap = (1..7).associateWith { mutableListOf<AirSchedule>() }
            allSchedules.forEach { schedule ->
                weeklyMap[schedule.weekday]?.add(schedule)
            }

            dayMap to weeklyMap
        }

    // 响应式观察用户正在追番与想看的条目集合及收藏详情（【我的追番】包含在看与想看）
    private val userCollectionsFlow =
        combine(
            collectionRepository.getCollectionsByTypeStream(CollectionType.DOING),
            collectionRepository.getCollectionsByTypeStream(CollectionType.WISH),
        ) { doing, wish ->
            (doing + wish).distinctBy { it.subjectId }
        }.distinctUntilChanged()

    // 本地乐观更新追番状态缓存：subjectId -> isWatching (true: 加入在看, false: 移出在看)
    private val optimisticWatching = MutableStateFlow<Map<Long, Boolean>>(emptyMap())

    // 本地乐观打卡集数进度缓存：subjectId -> epNumber
    private val optimisticEpStatus = MutableStateFlow<Map<Long, Int>>(emptyMap())

    private val collectionsStateFlow =
        combine(
            userCollectionsFlow,
            optimisticWatching,
            optimisticEpStatus,
        ) { userCollections, optimisticWatchMap, optimisticEpMap ->
            val baseWatchingIds = userCollections.map { it.subjectId }.toSet()
            val finalWatchingIds =
                (baseWatchingIds + optimisticWatchMap.filterValues { it }.keys) -
                    optimisticWatchMap.filterValues { !it }.keys
            val collectionMap = userCollections.associateBy { it.subjectId }.toMutableMap()
            // 补全乐观追番时的默认收藏进度对象，确保本地未登录/即时追番时立即参与卡片计算
            finalWatchingIds.forEach { subId ->
                if (!collectionMap.containsKey(subId)) {
                    collectionMap[subId] =
                        UserCollection(
                            subjectId = subId,
                            type = CollectionType.DOING.value,
                            epStatus = 0,
                        )
                }
            }
            optimisticEpMap.forEach { (subId, ep) ->
                val existing = collectionMap[subId]
                if (existing != null) {
                    collectionMap[subId] = existing.copy(epStatus = maxOf(existing.epStatus, ep))
                }
            }
            finalWatchingIds to collectionMap
        }.distinctUntilChanged()

    private val isActionDismissed = MutableStateFlow(false)

    private val filterFlow =
        combine(selectedPageIndex, onlyWatching) { pageIndex, onlyWatch ->
            pageIndex to onlyWatch
        }

    private val statusFlow =
        combine(isRefreshing, errorMessage) { refreshing, error ->
            refreshing to error
        }

    private data class ExtraScheduleState(
        val delayMinutes: Int,
        val dismissed: Boolean,
        val showLogin: Boolean,
        val loggedIn: Boolean,
    )

    private val extraStateFlow =
        combine(
            settingsRepository.airDelayOffsetMinutes,
            isActionDismissed,
            showLoginPromptDialog,
            isLoggedIn,
        ) { delayMinutes, dismissed, showLogin, loggedIn ->
            ExtraScheduleState(delayMinutes, dismissed, showLogin, loggedIn)
        }

    private val baseUiState: StateFlow<ScheduleUiState> =
        combine(
            scheduleDataFlow,
            collectionsStateFlow,
            filterFlow,
            statusFlow,
            extraStateFlow,
        ) {
            (daySchedules, weeklySchedules),
            (watchingIds, collectionMap),
            (pageIndex, onlyWatch),
            (refreshing, error),
            extra,
            ->
            val (delayMinutes, dismissed, showLogin, loggedIn) = extra
            val currentToday = currentLocalDate()
            val currentWeekday = currentToday.dayOfWeek.value
            val currentDateItems = calculateDateItems(currentToday)
            val selectedWeekday = currentDateItems.getOrNull(pageIndex)?.weekday ?: currentWeekday

            var computedNextUpAction: NextUpAction? = null
            if (!dismissed) {
                val todayRaw = daySchedules[TODAY_PAGE_INDEX] ?: weeklySchedules[currentWeekday].orEmpty()
                val nowEpoch = System.currentTimeMillis()

                var bestImminent: NextUpAction? = null
                var bestAired: NextUpAction? = null
                var bestUpcoming: NextUpAction? = null

                for (item in todayRaw) {
                    if (!watchingIds.contains(item.bgmId)) continue
                    val col = collectionMap[item.bgmId] ?: continue

                    val officialAirEpochMillis =
                        if (item.nextEpisodeAtUtc.isNotBlank()) {
                            try {
                                java.time.Instant
                                    .parse(item.nextEpisodeAtUtc)
                                    .toEpochMilli()
                            } catch (e: Exception) {
                                0L
                            }
                        } else if (item.timeCst.isNotBlank() || item.timeJst.isNotBlank()) {
                            try {
                                val timeStr = item.timeCst.ifBlank { item.timeJst }
                                val parts = timeStr.split(":")
                                if (parts.size == 2) {
                                    val h = parts[0].toIntOrNull() ?: 0
                                    val m = parts[1].toIntOrNull() ?: 0
                                    val zdt = currentToday.atTime(h, m).atZone(CST_ZONE_ID)
                                    zdt.toInstant().toEpochMilli()
                                } else {
                                    0L
                                }
                            } catch (e: Exception) {
                                0L
                            }
                        } else {
                            // 针对全天/待定条目（无精确时分），默认按当天中午 12:00 CST 兜底，确保在追番在今日能展示行动卡片
                            try {
                                val zdt = currentToday.atTime(12, 0).atZone(CST_ZONE_ID)
                                zdt.toInstant().toEpochMilli()
                            } catch (e: Exception) {
                                0L
                            }
                        }

                    if (officialAirEpochMillis <= 0L) continue

                    val effectiveAirEpoch = officialAirEpochMillis + delayMinutes * 60_000L
                    val epNumber = if (item.nextEpisodeNumber > 0) item.nextEpisodeNumber else 1

                    if (nowEpoch < effectiveAirEpoch) {
                        val diff = effectiveAirEpoch - nowEpoch
                        if (diff in 0..45 * 60_000L) {
                            val mins = diff / 60_000L
                            val action =
                                NextUpAction(
                                    subjectId = item.bgmId,
                                    title = item.title,
                                    titleCn = item.titleCn,
                                    coverUrl = item.coverUrl,
                                    episodeNumber = epNumber,
                                    airTimeLabel = "还有 ${maxOf(1L, mins)} 分钟开播",
                                    urgency = NextUpUrgency.IMMINENT,
                                )
                            if (bestImminent == null) bestImminent = action
                        } else {
                            val timeStr = item.timeCst.ifBlank { item.timeJst }
                            val label = if (timeStr.isNotBlank()) "今日 $timeStr 准时放送" else "今日放送"
                            val action =
                                NextUpAction(
                                    subjectId = item.bgmId,
                                    title = item.title,
                                    titleCn = item.titleCn,
                                    coverUrl = item.coverUrl,
                                    episodeNumber = epNumber,
                                    airTimeLabel = label,
                                    urgency = NextUpUrgency.TODAY_UPCOMING,
                                )
                            if (bestUpcoming == null) bestUpcoming = action
                        }
                    } else {
                        if (col.epStatus < epNumber) {
                            val sortedLinks = item.siteLinks.sortedBySitePriority()
                            val action =
                                NextUpAction(
                                    subjectId = item.bgmId,
                                    title = item.title,
                                    titleCn = item.titleCn,
                                    coverUrl = item.coverUrl,
                                    episodeNumber = epNumber,
                                    airTimeLabel = "今日已更新 · 第 $epNumber 话",
                                    urgency = NextUpUrgency.TODAY_AIRED,
                                    primaryPlayLink = sortedLinks.firstOrNull(),
                                    allPlayLinks = sortedLinks,
                                    canMarkWatched = true,
                                )
                            if (bestAired == null) bestAired = action
                        }
                    }
                }

                computedNextUpAction = bestImminent ?: bestAired ?: bestUpcoming
            }

            ScheduleUiState(
                isLoading = refreshing && daySchedules.values.all { it.isEmpty() } && weeklySchedules.values.all { it.isEmpty() },
                isRefreshing = refreshing,
                error = error,
                selectedPageIndex = pageIndex,
                selectedWeekday = selectedWeekday,
                todayWeekday = currentWeekday,
                todayPageIndex = TODAY_PAGE_INDEX,
                dateItems = currentDateItems,
                daySchedules = daySchedules,
                weeklySchedules = weeklySchedules,
                watchingSubjectIds = watchingIds,
                onlyWatching = onlyWatch,
                nextUpAction = computedNextUpAction,
                isActionDismissed = dismissed,
                showLoginPromptDialog = showLogin,
                isLoggedIn = loggedIn,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
                run {
                    val initialToday = currentLocalDate()
                    val initialWeekday = initialToday.dayOfWeek.value
                    ScheduleUiState(
                        isLoading = false,
                        isRefreshing = false,
                        selectedPageIndex = TODAY_PAGE_INDEX,
                        selectedWeekday = initialWeekday,
                        todayWeekday = initialWeekday,
                        todayPageIndex = TODAY_PAGE_INDEX,
                        dateItems = calculateDateItems(initialToday),
                        isLoggedIn = false,
                    )
                },
        )

    /**
     * 续看卡的应用内直达路由：跟随 nextUpAction 的条目，响应式合成
     * 「下一待看分集 + 片单直链」。数据未就绪时为 null，卡片退回外部跳转。
     */
    private val nextUpPlayRouteFlow: Flow<PlayerRoute?> =
        baseUiState
            .map { it.nextUpAction?.subjectId }
            .distinctUntilChanged()
            .flatMapLatest { subjectId ->
                if (subjectId == null || subjectId <= 0L) {
                    flowOf(null)
                } else {
                    // 分集列表仅在 fetchEpisodes 成功后进入本地缓存流，先兜底拉取
                    subjectRepository.fetchEpisodes(subjectId)
                    combine(
                        subjectRepository.getEpisodesStream(subjectId),
                        collectionRepository.getCollectionStream(subjectId),
                        settingsRepository.playlists,
                    ) { episodes, collection, playlists ->
                        buildNextUpRoute(subjectId, episodes, collection, playlists)
                    }
                }
            }.distinctUntilChanged()

    val uiState: StateFlow<ScheduleUiState> =
        combine(baseUiState, nextUpPlayRouteFlow) { state, route ->
            if (state.nextUpPlayRoute == route) state else state.copy(nextUpPlayRoute = route)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = baseUiState.value,
        )

    init {
        viewModelScope.launch {
            if (scheduleRepository.getScheduleDefaultOnlyWatching()) {
                onlyWatching.value = true
            }
        }
        refresh()
    }

    fun selectPage(pageIndex: Int) {
        selectedPageIndex.value = pageIndex.coerceIn(0, TOTAL_SCHEDULE_DAYS - 1)
        val dateItem = calculateDateItems(currentLocalDate()).getOrNull(pageIndex)
        if (dateItem != null) {
            selectedWeekday.value = dateItem.weekday
        }
    }

    fun selectWeekday(weekday: Int) {
        val currentToday = currentLocalDate()
        val dateItems = calculateDateItems(currentToday)
        val currentWeekday = currentToday.dayOfWeek.value
        val currentWeekRange = (-currentWeekday + 1)..(7 - currentWeekday)
        val targetIndex =
            dateItems
                .indexOfFirst { it.weekday == weekday && it.dayOffset in currentWeekRange }
                .takeIf { it >= 0 }
                ?: dateItems
                    .indexOfFirst { it.weekday == weekday }
                    .takeIf { it >= 0 }
                ?: TODAY_PAGE_INDEX
        selectedPageIndex.value = targetIndex
        selectedWeekday.value = weekday.coerceIn(1, 7)
    }

    fun toggleOnlyWatching() {
        if (!isLoggedIn.value && !onlyWatching.value) {
            showLoginPromptDialog.value = true
            return
        }
        val next = !onlyWatching.value
        onlyWatching.value = next
        viewModelScope.launch {
            scheduleRepository.setScheduleDefaultOnlyWatching(next)
        }
    }

    fun promptLogin() {
        showLoginPromptDialog.value = true
    }

    fun enableAiringReminder() {
        viewModelScope.launch {
            settingsRepository.setAiringReminderEnabled(true)
            _userMessage.send("已开启追番开播提醒")
        }
    }

    fun dismissNextUpAction() {
        isActionDismissed.value = true
    }

    /** 来源 sheet 的「应用内播放」：按追番进度定位下一集；无分集数据时回退一体化路由（由播放器自主嗅探） */
    suspend fun resolvePlayRoute(schedule: AirSchedule): PlayerRoute {
        val subjectId = schedule.bgmId
        val episodes =
            when (val result = subjectRepository.fetchEpisodes(subjectId)) {
                is AppResult.Success -> result.data
                else -> subjectRepository.getEpisodesStream(subjectId).first()
            }
        val playlists = settingsRepository.playlists.first()
        val collection = collectionRepository.getCollectionStream(subjectId).first()
        val target = resolveTargetEpisode(episodes, collection?.epStatus ?: 0)
        if (target == null) {
            return PlayerRoute(subjectId = subjectId, episodeId = 0L, subjectName = schedule.titleCn.ifBlank { schedule.title })
        }
        return buildEpisodeRoute(subjectId, schedule.titleCn.ifBlank { schedule.title }, target, playlists)
    }

    private fun buildNextUpRoute(
        subjectId: Long,
        episodes: List<Episode>,
        collection: UserCollection?,
        playlists: List<PlaybackPlaylist>,
    ): PlayerRoute? {
        val target = resolveTargetEpisode(episodes, collection?.epStatus ?: 0) ?: return null
        val schedule =
            baseUiState.value.daySchedules.values
                .flatten()
                .firstOrNull { it.bgmId == subjectId }
                ?: baseUiState.value.weeklySchedules.values
                    .flatten()
                    .firstOrNull { it.bgmId == subjectId }
        val subjectName = schedule?.let { it.titleCn.ifBlank { it.title } }.orEmpty()
        return buildEpisodeRoute(subjectId, subjectName, target, playlists)
    }

    /** 下一待看集：进度 +1 优先，回退第一个未看正篇，再回退第一集 */
    private fun resolveTargetEpisode(
        episodes: List<Episode>,
        watchedCount: Int,
    ): Episode? {
        val mainEpisodes = episodes.filter { it.type == 0 }.sortedBy { it.sort }

        fun epNumber(ep: Episode): Int = if (ep.ep > 0f) ep.ep.toInt() else ep.sort.toInt()
        return mainEpisodes.firstOrNull { epNumber(it) == watchedCount + 1 }
            ?: mainEpisodes.firstOrNull { epNumber(it) > watchedCount }
            ?: mainEpisodes.firstOrNull()
    }

    private fun buildEpisodeRoute(
        subjectId: Long,
        subjectName: String,
        episode: Episode,
        playlists: List<PlaybackPlaylist>,
    ): PlayerRoute {
        val matchedEntry =
            playlists
                .matchesForEpisode(subjectId, if (episode.ep > 0f) episode.ep else episode.sort)
                .firstOrNull()
                ?.entry
        return PlayerRoute(
            subjectId = subjectId,
            episodeId = episode.id,
            streamUrl = matchedEntry?.url.orEmpty(),
            requestHeaders = matchedEntry?.headers.orEmpty(),
            episodeName = episode.nameCn.ifBlank { episode.name },
            subjectName = subjectName,
            episodeSort = if (episode.ep > 0f) episode.ep else episode.sort,
            episodeType = episode.type,
        )
    }

    /** 1-tap 快捷追番/移出追番（支持 0ms 本地即时乐观更新与失败自动回滚，未登录时拦截弹窗） */
    fun toggleWatching(subjectId: Long) {
        if (!isLoggedIn.value) {
            showLoginPromptDialog.value = true
            return
        }

        val isWatching = uiState.value.watchingSubjectIds.contains(subjectId)
        val nextIsWatching = !isWatching
        val targetType = if (nextIsWatching) CollectionType.DOING else CollectionType.DROPPED

        // 1. 本地立即乐观更新：0ms 响应用户点击，UI 瞬间切换状态
        optimisticWatching.update { it + (subjectId to nextIsWatching) }

        viewModelScope.launch {
            collectionRepository
                .updateCollectionStatus(subjectId, targetType)
                .onSuccess {
                    // 2. 成功：数据库将写入新状态并自动流式发射，此时移除临时乐观标记
                    optimisticWatching.update { it - subjectId }
                    val msg = if (targetType == CollectionType.DOING) "已加入在看追番" else "已移出在看追番"
                    _userMessage.send(msg)
                }.onError { _, message ->
                    // 3. 失败：回滚本地乐观状态，恢复为原状态并弹窗提示
                    optimisticWatching.update { it - subjectId }
                    _userMessage.send(message.ifBlank { "操作失败，请确认是否已登录账号" })
                }
        }
    }

    /** 1-tap 快捷标记某话为看过（供待补清单一键打卡，支持即时乐观更新，未登录时拦截弹窗） */
    fun markEpisodeWatched(
        subjectId: Long,
        epNumber: Int,
    ) {
        if (!isLoggedIn.value) {
            showLoginPromptDialog.value = true
            return
        }

        // 本地立即乐观更新打卡进度
        optimisticEpStatus.update { it + (subjectId to epNumber) }

        viewModelScope.launch {
            collectionRepository
                .updateEpisodeStatus(
                    subjectId = subjectId,
                    // 时间表数据无单集 ID：由仓库按话数解析真实单集 ID
                    episodeId = null,
                    isWatched = true,
                    epNumber = epNumber,
                ).onSuccess {
                    optimisticEpStatus.update { it - subjectId }
                    _userMessage.send("已标记第 $epNumber 话已看过")
                }.onError { _, message ->
                    optimisticEpStatus.update { it - subjectId }
                    _userMessage.send(message.ifBlank { "标记失败，请确认是否已登录账号" })
                }
        }
    }

    /** 开始 OAuth 授权流程，隐藏提示弹窗并生成授权 URL（由 UI 层通过系统浏览器/Custom Tabs 打开） */
    suspend fun beginLogin(): String {
        showLoginPromptDialog.value = false
        return authRepository.beginLogin()
    }

    fun dismissLoginPrompt() {
        showLoginPromptDialog.value = false
    }

    /** [force] = 下拉刷新等用户显式动作；页面重建触发的静默刷新走仓库层 30 分钟节流 */
    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            if (force) {
                isRefreshing.value = true
            }
            if (isLoggedIn.value) {
                launch {
                    collectionRepository.syncWatchingCollections()
                }
            }
            // 全量快照管线：单次 CDN 快照直拉并直接入库
            val schedulesResult = scheduleRepository.refreshAllSchedules(force = force)
            schedulesResult
                .onSuccess { errorMessage.value = null }
                .onError { _, message -> errorMessage.value = message }
            if (force) {
                isRefreshing.value = false
            }
        }
    }

    companion object {
        const val TODAY_PAGE_INDEX = 6
        const val TOTAL_SCHEDULE_DAYS = 13
        val CST_ZONE_ID: ZoneId = ZoneId.of("Asia/Shanghai")

        fun currentLocalDate(): LocalDate = LocalDate.now()

        @Deprecated("Use currentLocalDate() instead", ReplaceWith("currentLocalDate()"))
        fun currentCstDate(): LocalDate = currentLocalDate()

        fun calculateDateItems(today: LocalDate): List<WeekdayDateItem> =
            (-6..6).mapIndexed { index, offset ->
                val date = today.plusDays(offset.toLong())
                val weekday = date.dayOfWeek.value
                val weekdayLabel =
                    when (weekday) {
                        1 -> "周一"
                        2 -> "周二"
                        3 -> "周三"
                        4 -> "周四"
                        5 -> "周五"
                        6 -> "周六"
                        7 -> "周日"
                        else -> ""
                    }
                WeekdayDateItem(
                    weekday = weekday,
                    weekdayLabel = weekdayLabel,
                    dateLabel = "${date.monthValue}/${date.dayOfMonth}",
                    isToday = offset == 0,
                    pageIndex = index,
                    dateString = date.toString(),
                    dayOffset = offset,
                )
            }
    }
}
