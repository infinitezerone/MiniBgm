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
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.matchesForEpisode
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
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
 *
 * 遵循 feature/search `SeasonalGuideViewModel` 类 KDoc 声明的响应式 UDF 规范（全仓 ViewModel 迁移标杆）：
 * 可变状态只剩用户意图（选日页索引 / 筛选 / 刷新触发器），UI 状态是 `combine(意图流, 仓库流)` 的
 * 投影；打卡与追番为「写仓 → 仓库流回读」（规范第 3 条），不存在乐观旁路与手动回滚。
 */
class ScheduleViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val collectionRepository: CollectionRepository,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val subjectRepository: SubjectRepository,
    /**
     * 时钟注入点，默认取系统时间。
     *
     * 排期相关判定全是"今天/这一周"的日期比较，直接调 `System.currentTimeMillis()` 会让
     * 跨日时刻的测试变成时间炸弹。注入后测试可钉死时间。
     */
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    /**
     * 今天。**按 CST 取，不用 `LocalDate.now()`**。
     *
     * `LocalDate.now()` 走 JVM 默认时区，而排期时间本身在 CST 坐标系里算（`CST_ZONE_ID`）：
     * CI 跑在 UTC，本地在 CST(+8)，跨日那几小时两边会差一天，同一份排期一边算"今天"、
     * 一边算"明天"，判定随之分叉。日期与时间必须同源。
     */
    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(CST_ZONE_ID).toLocalDate()

    // ---- 用户意图流（规范第 2 条：可变状态只剩用户意图）----

    /**
     * 单一选日意图：13 天长卷上的页索引。
     *
     * `selectedWeekday` 不再单独持流——weekday 是页索引在 [calculateDateItems] 上的投影，
     * 在 baseUiState 投影处换算；[selectWeekday] 对外保留星期语义、内部换算为页索引，
     * 消除两流双向同步的冗余。
     */
    private val selectedPageIndex = MutableStateFlow(TODAY_PAGE_INDEX)
    private val onlyWatching = MutableStateFlow(false)
    private val showLoginPromptDialog = MutableStateFlow(false)

    /**
     * 刷新触发器意图：[refresh] 只发射请求，仓库刷新在收集侧执行（规范第 2 条，无手动 Job 管理）。
     *
     * `replay = 1` 保证 init 阶段收集器订阅完成前发出的首刷不丢；连续触发时保留最新意图
     * （DROP_OLDEST）——刷新幂等，折叠无害。
     */
    private val refreshRequests =
        MutableSharedFlow<Boolean>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /**
     * 刷新会话命令态（isRefreshing / isSyncing / error），归入规范第 5 条：这不是仓库数据的投影，
     * 而是「一次刷新动作」的意图驱动会话态（命令密集型会话允许保留显式命令层，数据侧仍投影化）。
     * 触发走 [refreshRequests]，执行与状态回收收敛在唯一的收集协程里天然串行；
     * error 由下一次成功刷新经流回读清空。
     */
    private val refreshState = MutableStateFlow(RefreshState())

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
            settingsRepository.settings,
        ) { rawSchedules, allAirEvents, userSettings ->
            val allSchedules = if (userSettings.showRestrictedContent) rawSchedules else rawSchedules.filter { !it.isAdult }
            val currentToday = today()
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
                    val schedule = schedulesByBgmId[subId]
                    if (schedule != null) {
                        seenSubjectIdsOnThisDay.add(subId)
                        val distinctEvents = events.distinctBy { it.episode }.sortedBy { it.episode }
                        distinctEvents.forEach { event ->
                            list.add(
                                schedule.copy(
                                    weekday = weekday,
                                    nextEpisodeNumber = event.episode,
                                    nextEpisodeAtUtc = event.airAtUtc,
                                    timeCst = TimeUtils.formatToCstTime(event.airAtUtc).ifBlank { schedule.timeCst },
                                    timeJst = TimeUtils.formatToJstTime(event.airAtUtc).ifBlank { schedule.timeJst },
                                ),
                            )
                        }
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

    // 收藏状态直接从仓库流投影（规范第 3 条「写后读」）：打卡 / 追番写仓后，收藏流自动重发，
    // 在看集合与进度随之更新——不存在本地乐观旁路，也无需回滚代码。
    private val collectionsStateFlow =
        userCollectionsFlow
            .map { userCollections ->
                userCollections.map { it.subjectId }.toSet() to userCollections.associateBy { it.subjectId }
            }.distinctUntilChanged()

    private val filterFlow =
        combine(selectedPageIndex, onlyWatching) { pageIndex, onlyWatch ->
            pageIndex to onlyWatch
        }

    private data class ExtraScheduleState(
        val showLogin: Boolean,
        val loggedIn: Boolean,
    )

    private val extraStateFlow =
        combine(
            showLoginPromptDialog,
            isLoggedIn,
        ) { showLogin, loggedIn ->
            ExtraScheduleState(showLogin, loggedIn)
        }

    private val baseUiState: StateFlow<ScheduleUiState> =
        combine(
            scheduleDataFlow,
            collectionsStateFlow,
            filterFlow,
            refreshState,
            extraStateFlow,
        ) {
            (daySchedules, weeklySchedules),
            (watchingIds, collectionMap),
            (pageIndex, onlyWatch),
            (refreshing, syncing, error),
            extra,
            ->
            val (showLogin, loggedIn) = extra
            val currentToday = today()
            val currentWeekday = currentToday.dayOfWeek.value
            val currentDateItems = calculateDateItems(currentToday)
            val selectedWeekday = currentDateItems.getOrNull(pageIndex)?.weekday ?: currentWeekday

            ScheduleUiState(
                isLoading =
                    (syncing || refreshing) && daySchedules.values.all { it.isEmpty() } && weeklySchedules.values.all { it.isEmpty() },
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
                showLoginPromptDialog = showLogin,
                isLoggedIn = loggedIn,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
                run {
                    val initialToday = today()
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

    val uiState: StateFlow<ScheduleUiState> = baseUiState

    init {
        viewModelScope.launch {
            // 刷新执行收敛在唯一收集协程里（无手动 Job 管理）；runCatching 防御仓库意外抛出，
            // 保证触发器收集器不会被单次异常杀死（performRefresh 的 finally 已回收命令态）
            refreshRequests.collect { force -> runCatching { performRefresh(force) } }
        }
        viewModelScope.launch {
            if (scheduleRepository.getScheduleDefaultOnlyWatching()) {
                onlyWatching.value = true
            }
        }
        refresh()
    }

    fun selectPage(pageIndex: Int) {
        selectedPageIndex.value = pageIndex.coerceIn(0, TOTAL_SCHEDULE_DAYS - 1)
    }

    /** 对外保持星期语义；内部换算成 13 天长卷的页索引写入单一选日意图流 */
    fun selectWeekday(weekday: Int) {
        val currentToday = today()
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
            return PlayerRoute(subjectId = subjectId, episodeId = 0L, subjectName = schedule.displayName)
        }
        return buildEpisodeRoute(subjectId, schedule.displayName, target, playlists)
    }

    /** 下一待看集：进度 +1 优先，回退第一个未看正篇，再回退第一集 */
    private fun resolveTargetEpisode(
        episodes: List<Episode>,
        watchedCount: Int,
    ): Episode? {
        val mainEpisodes = episodes.filter { it.isMain }.sortedBy { it.sort }

        return mainEpisodes.firstOrNull { it.episodeInt == watchedCount + 1 }
            ?: mainEpisodes.firstOrNull { it.episodeInt > watchedCount }
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
                .matchesForEpisode(subjectId, episode.episodeNumber)
                .firstOrNull()
                ?.entry
        return PlayerRoute(
            subjectId = subjectId,
            episodeId = episode.id,
            streamUrl = matchedEntry?.url.orEmpty(),
            requestHeaders = matchedEntry?.headers.orEmpty(),
            episodeName = episode.primaryName,
            subjectName = subjectName,
            episodeSort = episode.episodeNumber,
            episodeType = episode.type,
        )
    }

    /**
     * 1-tap 快捷追番/移出追番（规范第 3 条「写后读」：写仓后由收藏流回读新状态，无本地乐观旁路；
     * 仓库层本地优先写库 + 失败回滚，NonCancellable 约束在 CollectionRepository 内）。
     * 未登录时拦截弹窗；写失败仅弹 Snackbar 提示。
     */
    fun toggleWatching(subjectId: Long) {
        if (!isLoggedIn.value) {
            showLoginPromptDialog.value = true
            return
        }

        val isWatching = uiState.value.watchingSubjectIds.contains(subjectId)
        val targetType = if (isWatching) CollectionType.DROPPED else CollectionType.DOING

        viewModelScope.launch {
            collectionRepository
                .updateCollectionStatus(subjectId, targetType)
                .onSuccess {
                    val msg = if (targetType == CollectionType.DOING) "已加入在看追番" else "已移出在看追番"
                    _userMessage.send(msg)
                }.onError { _, message ->
                    _userMessage.send(message.ifBlank { "操作失败，请确认是否已登录账号" })
                }
        }
    }

    /**
     * 1-tap 快捷标记某话为看过，供待补清单一键打卡（规范第 3 条「写后读」：写仓后由收藏流回读
     * 新进度，无本地乐观旁路）。未登录时拦截弹窗；写失败仅弹 Snackbar 提示。
     */
    fun markEpisodeWatched(
        subjectId: Long,
        epNumber: Int,
    ) {
        if (!isLoggedIn.value) {
            showLoginPromptDialog.value = true
            return
        }

        viewModelScope.launch {
            collectionRepository
                .updateEpisodeStatus(
                    subjectId = subjectId,
                    // 时间表数据无单集 ID：由仓库按话数解析真实单集 ID
                    episodeId = null,
                    isWatched = true,
                    epNumber = epNumber,
                ).onSuccess {
                    _userMessage.send("已标记第 $epNumber 话已看过")
                }.onError { _, message ->
                    _userMessage.send(message.ifBlank { "标记失败，请确认是否已登录账号" })
                }
        }
    }

    fun dismissLoginPrompt() {
        showLoginPromptDialog.value = false
    }

    /** [force] = 下拉刷新等用户显式动作；页面重建触发的静默刷新走仓库层 12 小时节流 */
    fun refresh(force: Boolean = false) {
        refreshRequests.tryEmit(force)
    }

    /** 刷新执行体：仅在 [refreshRequests] 的收集协程里运行，天然串行，无需手动 Job 管理 */
    private suspend fun performRefresh(force: Boolean) {
        if (force) {
            refreshState.update { it.copy(isRefreshing = true) }
        }
        refreshState.update { it.copy(isSyncing = true) }
        try {
            if (isLoggedIn.value) {
                viewModelScope.launch {
                    collectionRepository.syncWatchingCollections(force = force)
                }
            }
            // 全量快照管线：单次 CDN 快照直拉并直接入库
            scheduleRepository
                .refreshAllSchedules(force = force)
                .onSuccess { refreshState.update { state -> state.copy(error = null) } }
                .onError { _, message -> refreshState.update { state -> state.copy(error = message) } }
        } finally {
            refreshState.update { it.copy(isSyncing = false, isRefreshing = false) }
        }
    }

    companion object {
        const val TODAY_PAGE_INDEX = 6
        const val TOTAL_SCHEDULE_DAYS = 13
        val CST_ZONE_ID: ZoneId = ZoneId.of("Asia/Shanghai")

        fun currentLocalDate(): LocalDate = LocalDate.now(CST_ZONE_ID)

        fun calculateDateItems(today: LocalDate): List<WeekdayDateItem> =
            (-6..6).mapIndexed { index, offset ->
                val date = today.plusDays(offset.toLong())
                val weekday = date.dayOfWeek.value
                val weekdayLabel = TimeUtils.weekdayCnLabel(weekday)
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

/** 一次刷新动作的命令态快照，归类说明见 [ScheduleViewModel.refreshState] */
    private data class RefreshState(
        val isRefreshing: Boolean = false,
        val isSyncing: Boolean = true,
        val error: String? = null,
    )
}
