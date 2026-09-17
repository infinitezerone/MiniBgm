package com.infinitezerone.minibgm.feature.schedule

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.common.onSuccess
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.NextUpAction
import com.infinitezerone.minibgm.core.model.NextUpUrgency
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.model.sortedBySitePriority
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** 待补番剧条目（包含所属日期与已看/在播进度） */
@Immutable
data class CatchupScheduleItem(
    val schedule: AirSchedule,
    val dayLabel: String, // "昨天" 或 "前天"
    val epStatus: Int, // 用户已打卡集数
    val targetEp: Int, // 官方当前播出集数
)

/** 星期与真实日期模型 */
@Immutable
data class WeekdayDateItem(
    val weekday: Int, // 1=周一, ..., 7=周日
    val weekdayLabel: String, // "周一", "周二" ...
    val dateLabel: String, // "9/3"
    val isToday: Boolean,
)

/** 「放送」Tab 的单一不可变 UI 状态 */
@Immutable
data class ScheduleUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedWeekday: Int,
    val todayWeekday: Int = ScheduleViewModel.currentCstDate().dayOfWeek.value,
    val dateItems: List<WeekdayDateItem> = emptyList(),
    val weeklySchedules: Map<Int, List<AirSchedule>> = emptyMap(),
    val watchingSubjectIds: Set<Long> = emptySet(),
    val onlyWatching: Boolean = false,
    val catchupItems: List<CatchupScheduleItem> = emptyList(),
    val yesterdaySchedules: List<AirSchedule> = emptyList(),
    val nextUpAction: NextUpAction? = null,
    val isActionDismissed: Boolean = false,
    val showLoginPromptDialog: Boolean = false,
) {
    /** 兼容旧接口：当前所选星期的原始番剧列表 */
    val schedules: List<AirSchedule>
        get() = weeklySchedules[selectedWeekday].orEmpty()

    /** 是否处于离线缓存展示状态：有网络错误发生但本地有缓存数据 */
    val isOfflineCache: Boolean
        get() = error != null && weeklySchedules.values.any { it.isNotEmpty() }

    /** 当前选中星期经过筛选与时间线排序后的条目 */
    val currentDaySchedules: List<AirSchedule>
        get() = getSortedSchedulesForWeekday(selectedWeekday, onlyWatching)

    /** 获取指定星期过滤并排序后的条目 */
    fun getSortedSchedulesForWeekday(
        weekday: Int,
        onlyWatchingFilter: Boolean = onlyWatching,
    ): List<AirSchedule> {
        val raw = weeklySchedules[weekday].orEmpty()
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
            }.thenByDescending {
                it.ratingScore
            },
        )
    }

    /** 获取指定星期有具体播放时间的条目（供连续时间轴使用） */
    fun getTimedSchedulesForWeekday(weekday: Int): List<AirSchedule> =
        getSortedSchedulesForWeekday(weekday).filter {
            (it.timeCst.ifBlank { it.timeJst }).isNotBlank()
        }

    /** 按具体播出时间分组的排播条目，key 为时间字符串（如 "23:30"），保持时间先后顺序 */
    fun getTimeGroupedSchedulesForWeekday(weekday: Int): Map<String, List<AirSchedule>> {
        val timed = getTimedSchedulesForWeekday(weekday)
        val linkedMap = linkedMapOf<String, MutableList<AirSchedule>>()
        for (item in timed) {
            val timeKey = item.timeCst.ifBlank { item.timeJst }
            linkedMap.getOrPut(timeKey) { mutableListOf() }.add(item)
        }
        return linkedMap
    }

    /** 获取指定星期全天/时间未定的条目（供解耦全天专区使用） */
    fun getAllDaySchedulesForWeekday(weekday: Int): List<AirSchedule> =
        getSortedSchedulesForWeekday(weekday).filter {
            (it.timeCst.ifBlank { it.timeJst }).isBlank()
        }

    /** 今日正在追番的更新列表（在“今天”视图置顶呈现） */
    val todayWatchingSchedules: List<AirSchedule>
        get() {
            val todayRaw = weeklySchedules[todayWeekday].orEmpty()
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

    /** 某一天的在追番剧数量 */
    fun getWatchingCountForWeekday(weekday: Int): Int = weeklySchedules[weekday].orEmpty().count { watchingSubjectIds.contains(it.bgmId) }

    /** 某一天的总番剧数量 */
    fun getTotalCountForWeekday(weekday: Int): Int = weeklySchedules[weekday].orEmpty().size
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
    private val settingsRepository: com.infinitezerone.minibgm.core.data.repository.SettingsRepository,
    private val authRepository: com.infinitezerone.minibgm.core.data.repository.AuthRepository,
) : ViewModel() {
    private val selectedWeekday = MutableStateFlow(currentCstDate().dayOfWeek.value)
    private val onlyWatching = MutableStateFlow(false)
    private val isRefreshing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val showLoginPromptDialog = MutableStateFlow(false)

    private val isLoggedIn =
        authRepository.isLoggedIn
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _userMessage = Channel<String>(Channel.BUFFERED)
    val userMessage: Flow<String> = _userMessage.receiveAsFlow()

    // 监听全量放送流并按星期聚合（单流监听，避免按天并发 7 路订阅导致的重复 SQL 查询与高频重组）
    private val weeklySchedulesFlow: Flow<Map<Int, List<AirSchedule>>> =
        scheduleRepository.getAllSchedulesStream().map { all ->
            val map = (1..7).associateWith { mutableListOf<AirSchedule>() }
            all.forEach { schedule ->
                map[schedule.weekday]?.add(schedule)
            }
            map
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
        combine(selectedWeekday, onlyWatching) { weekday, onlyWatch ->
            weekday to onlyWatch
        }

    private val statusFlow =
        combine(isRefreshing, errorMessage) { refreshing, error ->
            refreshing to error
        }

    private val extraStateFlow =
        combine(settingsRepository.airDelayOffsetMinutes, isActionDismissed, showLoginPromptDialog) { delayMinutes, dismissed, showLogin ->
            Triple(delayMinutes, dismissed, showLogin)
        }

    val uiState: StateFlow<ScheduleUiState> =
        combine(
            weeklySchedulesFlow,
            collectionsStateFlow,
            filterFlow,
            statusFlow,
            extraStateFlow,
        ) {
            weeklySchedules,
            (watchingIds, collectionMap),
            (weekday, onlyWatch),
            (refreshing, error),
            (delayMinutes, dismissed, showLogin),
            ->
            val currentToday = currentCstDate()
            val currentWeekday = currentToday.dayOfWeek.value
            val currentDateItems = calculateDateItems(currentToday)

            val yesterdayWeekday = if (currentWeekday == 1) 7 else currentWeekday - 1
            val dayBeforeWeekday = if (yesterdayWeekday == 1) 7 else yesterdayWeekday - 1

            // 提取待补更新：昨日、前天已播出的在追番，且进度落后
            val catchupList = mutableListOf<CatchupScheduleItem>()
            val yesterdayRaw = weeklySchedules[yesterdayWeekday].orEmpty()
            for (item in yesterdayRaw) {
                val col = collectionMap[item.bgmId]
                if (col != null) {
                    val targetEp = item.nextEpisodeNumber
                    if (targetEp > 0 && col.epStatus < targetEp) {
                        catchupList.add(
                            CatchupScheduleItem(
                                schedule = item,
                                dayLabel = "昨天",
                                epStatus = col.epStatus,
                                targetEp = targetEp,
                            ),
                        )
                    }
                }
            }

            val dayBeforeRaw = weeklySchedules[dayBeforeWeekday].orEmpty()
            for (item in dayBeforeRaw) {
                val col = collectionMap[item.bgmId]
                if (col != null) {
                    val targetEp = item.nextEpisodeNumber
                    if (targetEp > 0 && col.epStatus < targetEp) {
                        catchupList.add(
                            CatchupScheduleItem(
                                schedule = item,
                                dayLabel = "前天",
                                epStatus = col.epStatus,
                                targetEp = targetEp,
                            ),
                        )
                    }
                }
            }

            // 昨日全部播映新番（按评分与时间排列，供未登录/快捷浏览速览）
            val yesterdayList =
                yesterdayRaw.sortedWith(
                    compareByDescending<AirSchedule> { it.ratingScore }
                        .thenBy { it.timeCst.ifBlank { it.timeJst } },
                )

            var computedNextUpAction: NextUpAction? = null
            if (!dismissed) {
                val todayRaw = weeklySchedules[currentWeekday].orEmpty()
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
                isLoading = refreshing && weeklySchedules.values.all { it.isEmpty() },
                isRefreshing = refreshing,
                error = error,
                selectedWeekday = weekday,
                todayWeekday = currentWeekday,
                dateItems = currentDateItems,
                weeklySchedules = weeklySchedules,
                watchingSubjectIds = watchingIds,
                onlyWatching = onlyWatch,
                catchupItems = catchupList,
                yesterdaySchedules = yesterdayList,
                nextUpAction = computedNextUpAction,
                isActionDismissed = dismissed,
                showLoginPromptDialog = showLogin,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
                run {
                    val initialToday = currentCstDate()
                    val initialWeekday = initialToday.dayOfWeek.value
                    ScheduleUiState(
                        isLoading = true,
                        isRefreshing = false,
                        selectedWeekday = initialWeekday,
                        todayWeekday = initialWeekday,
                        dateItems = calculateDateItems(initialToday),
                    )
                },
        )

    init {
        viewModelScope.launch {
            if (scheduleRepository.getScheduleDefaultOnlyWatching()) {
                onlyWatching.value = true
            }
        }
        refresh()
    }

    fun selectWeekday(weekday: Int) {
        selectedWeekday.value = weekday.coerceIn(1, 7)
    }

    fun toggleOnlyWatching() {
        val next = !onlyWatching.value
        onlyWatching.value = next
        viewModelScope.launch {
            scheduleRepository.setScheduleDefaultOnlyWatching(next)
        }
    }

    fun dismissNextUpAction() {
        isActionDismissed.value = true
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

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            val schedulesResult = scheduleRepository.refreshSchedules()
            collectionRepository.syncWatchingCollections()
            schedulesResult
                .onSuccess { errorMessage.value = null }
                .onError { _, message -> errorMessage.value = message }
            isRefreshing.value = false
        }
    }

    companion object {
        val CST_ZONE_ID: ZoneId = ZoneId.of("Asia/Shanghai")

        fun currentCstDate(): LocalDate = LocalDate.now(CST_ZONE_ID)

        fun calculateDateItems(today: LocalDate): List<WeekdayDateItem> {
            val todayWeekday = today.dayOfWeek.value
            val monday = today.minusDays((todayWeekday - 1).toLong())
            return (1..7).map { weekday ->
                val date = monday.plusDays((weekday - 1).toLong())
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
                    isToday = weekday == todayWeekday,
                )
            }
        }
    }
}
