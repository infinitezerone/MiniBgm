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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
    val todayWeekday: Int = LocalDate.now().dayOfWeek.value,
    val dateItems: List<WeekdayDateItem> = emptyList(),
    val weeklySchedules: Map<Int, List<AirSchedule>> = emptyMap(),
    val watchingSubjectIds: Set<Long> = emptySet(),
    val onlyWatching: Boolean = false,
    val catchupItems: List<CatchupScheduleItem> = emptyList(),
    val yesterdaySchedules: List<AirSchedule> = emptyList(),
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
) : ViewModel() {
    private val today = LocalDate.now()
    private val initialWeekday = today.dayOfWeek.value

    private val selectedWeekday = MutableStateFlow(initialWeekday)
    private val onlyWatching = MutableStateFlow(false)
    private val isRefreshing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val _userMessage = Channel<String>(Channel.BUFFERED)
    val userMessage: Flow<String> = _userMessage.receiveAsFlow()

    private val dateItems: List<WeekdayDateItem> =
        run {
            val monday = today.minusDays((initialWeekday - 1).toLong())
            (1..7).map { weekday ->
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
                    isToday = weekday == initialWeekday,
                )
            }
        }

    // 组合每周 1~7 天的全部放送流
    private val weeklySchedulesFlow: Flow<Map<Int, List<AirSchedule>>> =
        combine(
            (1..7).map { weekday ->
                scheduleRepository.getSchedulesByWeekday(weekday).map { weekday to it }
            },
        ) { pairs ->
            pairs.toMap()
        }

    // 响应式观察用户正在追番的条目集合及收藏详情
    private val userCollectionsFlow =
        collectionRepository
            .getCollectionsByTypeStream(CollectionType.DOING)
            .distinctUntilChanged()

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
            optimisticEpMap.forEach { (subId, ep) ->
                val existing = collectionMap[subId]
                if (existing != null) {
                    collectionMap[subId] = existing.copy(epStatus = maxOf(existing.epStatus, ep))
                }
            }
            finalWatchingIds to collectionMap
        }.distinctUntilChanged()

    private val filterFlow =
        combine(selectedWeekday, onlyWatching) { weekday, onlyWatch ->
            weekday to onlyWatch
        }

    private val statusFlow =
        combine(isRefreshing, errorMessage) { refreshing, error ->
            refreshing to error
        }

    val uiState: StateFlow<ScheduleUiState> =
        combine(
            weeklySchedulesFlow,
            collectionsStateFlow,
            filterFlow,
            statusFlow,
        ) { weeklySchedules, (watchingIds, collectionMap), (weekday, onlyWatch), (refreshing, error) ->

            val yesterdayWeekday = if (initialWeekday == 1) 7 else initialWeekday - 1
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

            ScheduleUiState(
                isLoading = refreshing && weeklySchedules.values.all { it.isEmpty() },
                isRefreshing = refreshing,
                error = error,
                selectedWeekday = weekday,
                todayWeekday = initialWeekday,
                dateItems = dateItems,
                weeklySchedules = weeklySchedules,
                watchingSubjectIds = watchingIds,
                onlyWatching = onlyWatch,
                catchupItems = catchupList,
                yesterdaySchedules = yesterdayList,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
                ScheduleUiState(
                    isLoading = true,
                    isRefreshing = false,
                    selectedWeekday = initialWeekday,
                    todayWeekday = initialWeekday,
                    dateItems = dateItems,
                ),
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

    /** 1-tap 快捷追番/移出追番（支持 0ms 本地即时乐观更新与失败自动回滚） */
    fun toggleWatching(subjectId: Long) {
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

    /** 1-tap 快捷标记某话为看过（供待补清单一键打卡，支持即时乐观更新） */
    fun markEpisodeWatched(
        subjectId: Long,
        epNumber: Int,
    ) {
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

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            val schedulesDeferred = async { scheduleRepository.refreshSchedules() }
            val watchingDeferred = async { collectionRepository.syncWatchingCollections() }
            val schedulesResult = schedulesDeferred.await()
            watchingDeferred.await()
            schedulesResult
                .onSuccess { errorMessage.value = null }
                .onError { _, message -> errorMessage.value = message }
            isRefreshing.value = false
        }
    }
}
