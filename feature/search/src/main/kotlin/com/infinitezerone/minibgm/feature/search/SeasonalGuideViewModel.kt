package com.infinitezerone.minibgm.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.runCatchingCancellable
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SearchFilter
import com.infinitezerone.minibgm.core.model.SearchSubjectsRequest
import com.infinitezerone.minibgm.core.model.Subject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Bangumi `POST /v0/search/subjects` 单页**硬上限**：limit 传更大不会报错，只会静默按 20 截断。
 * 因此这里必须与真实页宽一致，翻页进度才能与响应 total 对上。
 */
private const val PAGE_SIZE = 20

/** 「本季连载中」最多补充的条目数：限制逐条补全详情的网络开销 */
private const val MAX_ONGOING_SUBJECTS = 20

/** 补全连载中条目详情时的并发上限，避免瞬间打满连接 */
private const val ONGOING_FETCH_CONCURRENCY = 4

/** "YYYY-MM-DD" 的固定长度；比它短的（如仅年份、空串）无法参与日历比较 */
private const val NORMALIZED_DATE_LENGTH = 10

/**
 * 季度新番导视 ViewModel
 *
 * 列表由两条互补来源拼成：
 * 1. **本季首播**：Bangumi 高级搜索按 `air_date` 区间过滤，即"首播日落在本季"的条目；
 * 2. **本季连载中**：长期连载番（名侦探柯南、蜡笔小新等）的首播日在很多年前，
 *    `air_date` 过滤永远捞不到它们，只能靠排期仓"窗口内确有播出事件"反查补回。
 */
class SeasonalGuideViewModel(
    private val searchRepository: SearchRepository,
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
    private val scheduleRepository: ScheduleRepository,
    private val subjectRepository: SubjectRepository,
    initialYear: Int = 0,
    initialSeasonMonth: Int = 0,
    timeProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val currentDate = timeProvider()
    private val defaultYear = if (initialYear > 0) initialYear else currentDate.year
    private val defaultQuarter =
        if (initialSeasonMonth > 0) {
            SeasonQuarter.fromMonth(initialSeasonMonth)
        } else {
            SeasonQuarter.fromMonth(currentDate.monthValue)
        }

    // 提供未来 1 年至过去 15 年的年份切换选项
    private val availableYearsList = ((currentDate.year + 1) downTo (currentDate.year - 15)).toList()

    private val currentYear = currentDate.year
    private val currentQuarter = SeasonQuarter.fromMonth(currentDate.monthValue)

    private val _uiState =
        MutableStateFlow(
            SeasonalGuideUiState(
                selectedYear = defaultYear,
                selectedQuarter = defaultQuarter,
                currentYear = currentYear,
                currentQuarter = currentQuarter,
                availableYears = availableYearsList,
            ),
        )
    val uiState: StateFlow<SeasonalGuideUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var ongoingJob: Job? = null

    init {
        observeAuth()
        observeCollections()
        loadSeasonalAnime()
    }

    private fun observeAuth() {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
            }
        }
    }

    private fun observeCollections() {
        viewModelScope.launch {
            collectionRepository
                .getCollectionsByTypeStream(CollectionType.WISH)
                .catch { }
                .collect { wishList ->
                    _uiState.update {
                        it.copy(wishedSubjectIds = wishList.map { c -> c.subjectId }.toSet())
                    }
                }
        }
        viewModelScope.launch {
            collectionRepository
                .getCollectionsByTypeStream(CollectionType.DOING)
                .catch { }
                .collect { doingList ->
                    _uiState.update {
                        it.copy(doingSubjectIds = doingList.map { c -> c.subjectId }.toSet())
                    }
                }
        }
    }

    fun selectYear(year: Int) {
        if (_uiState.value.selectedYear == year) return
        _uiState.update { it.copy(selectedYear = year) }
        loadSeasonalAnime()
    }

    fun selectQuarter(quarter: SeasonQuarter) {
        if (_uiState.value.selectedQuarter == quarter) return
        _uiState.update { it.copy(selectedQuarter = quarter) }
        loadSeasonalAnime()
    }

    fun selectSeason(
        year: Int,
        quarter: SeasonQuarter,
    ) {
        if (_uiState.value.selectedYear == year && _uiState.value.selectedQuarter == quarter) return
        _uiState.update { it.copy(selectedYear = year, selectedQuarter = quarter) }
        loadSeasonalAnime()
    }

    /**
     * 切换产地筛选。
     *
     * 必须重新取数：条件下推服务端后 `total` 会变，若沿用已加载的数据做客户端过滤，
     * 会显示成"选国产只有 2 部"——因为当前只加载了 20 条。连载中分组不依赖搜索接口，
     * 不重取，其可见性由派生属性按新条件自动收敛。
     */
    fun selectOrigin(origin: SeasonOriginFilter) {
        if (_uiState.value.selectedOrigin == origin) return
        _uiState.update { it.copy(selectedOrigin = origin) }
        loadSeasonalAnime(reloadOngoing = false)
    }

    /** 切换放送形式筛选；理由同 [selectOrigin]，同样需要重新取数 */
    fun selectForm(form: SeasonFormFilter) {
        if (_uiState.value.selectedForm == form) return
        _uiState.update { it.copy(selectedForm = form) }
        loadSeasonalAnime(reloadOngoing = false)
    }

    /** 切换海报网格／紧凑列表；纯展示偏好，不重新取数（翻页游标对两种形态是同一份数据） */
    fun toggleViewMode() {
        _uiState.update {
            it.copy(
                viewMode =
                    if (it.viewMode == SeasonalViewMode.LIST) {
                        SeasonalViewMode.POSTER
                    } else {
                        SeasonalViewMode.LIST
                    },
            )
        }
    }

    fun toggleCollection(
        subjectId: Long,
        targetType: CollectionType,
    ) {
        if (!_uiState.value.isLoggedIn) {
            _uiState.update { it.copy(showLoginPromptDialog = true) }
            return
        }

        val wasWished = _uiState.value.wishedSubjectIds.contains(subjectId)
        val wasDoing = _uiState.value.doingSubjectIds.contains(subjectId)

        if ((targetType == CollectionType.DOING && wasDoing) ||
            (targetType == CollectionType.WISH && wasWished)
        ) {
            _uiState.update { it.copy(userMessage = "已在您的「${targetType.label}」列表中") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                when (targetType) {
                    CollectionType.DOING ->
                        it.copy(
                            doingSubjectIds = it.doingSubjectIds + subjectId,
                            wishedSubjectIds = it.wishedSubjectIds - subjectId,
                            userMessage = "已标记为「在看」",
                        )
                    CollectionType.WISH ->
                        it.copy(
                            wishedSubjectIds = it.wishedSubjectIds + subjectId,
                            doingSubjectIds = it.doingSubjectIds - subjectId,
                            userMessage = "已加入「想看」列表",
                        )
                    else -> it
                }
            }

            val result =
                withContext(NonCancellable) {
                    collectionRepository.updateCollectionStatus(
                        subjectId = subjectId,
                        type = targetType,
                        subjectType = 2,
                    )
                }

            if (result is AppResult.Error) {
                _uiState.update { current ->
                    val restoredDoing =
                        if (wasDoing) current.doingSubjectIds + subjectId else current.doingSubjectIds - subjectId
                    val restoredWished =
                        if (wasWished) current.wishedSubjectIds + subjectId else current.wishedSubjectIds - subjectId
                    current.copy(
                        doingSubjectIds = restoredDoing,
                        wishedSubjectIds = restoredWished,
                        userMessage = result.message.ifBlank { "操作失败，请重试" },
                    )
                }
            }
        }
    }

    fun refresh() {
        loadSeasonalAnime(isRefresh = true)
    }

    fun retry() {
        loadSeasonalAnime(isRefresh = false)
    }

    fun loadMore() {
        val currentState = _uiState.value
        // 这道 guard 是并发控制的唯一入口：isLoadingMore 由下面置位、由任务收尾复位，
        // 同一时刻只可能有一个翻页任务在跑，所以这里既不必要也不该再 cancel 上一个任务——
        // 自取消会把刚置上的标志连同收尾逻辑一起丢掉（见 try/finally 处的说明）。
        if (currentState.isLoading || currentState.isLoadingMore || currentState.isRefreshing || !currentState.hasMore) {
            return
        }

        loadMoreJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingMore = true) }
                // 必须在 finally 里复位：切产地/形式会 cancel 本任务，若只在正常路径复位，
                // isLoadingMore 会永久停在 true，上面的 guard 从此恒真——表现是切完筛选后再也翻不了页。
                val error =
                    try {
                        fetchPages(startOffset = _uiState.value.pageOffset)
                    } finally {
                        _uiState.update { it.copy(isLoadingMore = false) }
                    }
                _uiState.update {
                    it.copy(userMessage = error?.takeIf { msg -> msg.isNotBlank() }?.let { msg -> "加载更多失败：$msg" })
                }
            }
    }

    /**
     * 从 [startOffset] 起取页并写入状态，返回错误消息（成功为 null）。
     *
     * 会**连续取页直到本次调用确实多出可见条目**，原因是客户端还压着三层服务端表达不了的过滤：
     * 产地「欧美」（服务端 meta_tags 精确匹配，"欧美 / 美国 / 英国…"之间的"或"表达不了）、
     * 形式「全部」要折叠片段型、「短片 / MV」要只留片段型（都没有排除语法，多值又是 AND）。
     * 这些过滤会整页整页地把条目滤掉——列表一旦为空就没有可滚动内容，触底加载永远不会触发，
     * 用户会停在一片空白上。所以这里主动往后取。
     *
     * 判据是"可见条目数比调用前多"，不是"列表非空"：翻页时列表本来就是非空的，用后者只会取一页就收工；
     * 若这一页整页被滤掉，可见内容与滚动范围都没变，触底加载会卡死在这里（滑到底不动、既不转圈也不加载）。
     *
     * [startOffset] 为 0 时首屏整批替换，否则按已加载 ID 去重后追加。
     * 翻页游标始终按服务端返回的原始条数前进，过滤不参与——所以一路取到底得到的过滤结果是完整的。
     */
    private suspend fun fetchPages(startOffset: Int): String? {
        var offset = startOffset
        var isFirstIteration = true
        val visibleBefore = if (startOffset == 0) 0 else _uiState.value.filteredSubjects.size
        while (true) {
            val replaceFirstPage = isFirstIteration && startOffset == 0
            isFirstIteration = false

            val result =
                searchRepository.searchSubjectsAdvanced(
                    request = buildSearchRequest(_uiState.value),
                    limit = PAGE_SIZE,
                    offset = offset,
                )

            when (result) {
                is AppResult.Success -> {
                    val page = result.data.list
                    val loadedCount = offset + page.size
                    _uiState.update { current ->
                        val merged =
                            if (replaceFirstPage) {
                                page
                            } else {
                                val existingIds = current.subjects.map { s -> s.id }.toSet()
                                current.subjects + page.filter { s -> s.id !in existingIds }
                            }
                        current.copy(
                            subjects = merged,
                            pageOffset = loadedCount,
                            hasMore = loadedCount < result.data.total,
                            error = null,
                        )
                    }
                    offset = loadedCount

                    val updated = _uiState.value
                    // 取空、已取尽 → 收工；本次调用已经多出可见条目 → 也收工
                    if (page.isEmpty() || !updated.hasMore) return null
                    if (updated.filteredSubjects.size > visibleBefore) return null
                }
                is AppResult.Error -> return result.message
                is AppResult.Loading -> return null
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun dismissLoginPrompt() {
        _uiState.update { it.copy(showLoginPromptDialog = false) }
    }

    suspend fun beginLogin(): String {
        _uiState.update { it.copy(showLoginPromptDialog = false) }
        return authRepository.beginLogin()
    }

    private fun buildSearchRequest(state: SeasonalGuideUiState): SearchSubjectsRequest {
        val (startDay, endDay) = state.selectedQuarter.getAirDateRange(state.selectedYear)
        // 能精确表达的条件一律下推服务端，并组合成 AND（如「日本 + TV」＝当季日本 TV 动画）。
        // 产地「欧美」与形式「全部」/「短片 / MV」的 metaTag 为 null——服务端 meta_tags 是多值 AND、
        // 且没有排除语法，"或"（欧美 vs 只标具体国家）与"以上皆非"（片段型）都表达不了，
        // 这三档改由客户端兜，见 SeasonalGuideUiState.filteredSubjects。
        val metaTags = listOfNotNull(state.selectedOrigin.metaTag, state.selectedForm.metaTag)
        return SearchSubjectsRequest(
            sort = "heat",
            filter =
                SearchFilter(
                    type = listOf(2),
                    airDate = listOf(">=$startDay", "<=$endDay"),
                    metaTags = metaTags.ifEmpty { null },
                ),
        )
    }

    private fun loadSeasonalAnime(
        isRefresh: Boolean = false,
        reloadOngoing: Boolean = true,
    ) {
        fetchJob?.cancel()
        // 交接正在飞的翻页任务：它由 [loadMore] 启动，携带的是**旧筛选条件**的响应。
        // 协程被 cancel 后在下一次挂起点抛出之前，仍可能执行到状态写入，所以新一轮取数必须先等它彻底结束
        // （见 fetchJob 里的 join），否则旧条件的结果会混进按新条件重建的列表里。
        val staleLoadMore = loadMoreJob
        loadMoreJob = null
        staleLoadMore?.cancel()
        ongoingJob?.cancel()
        val currentState = _uiState.value
        val selectedYear = currentState.selectedYear
        val selectedQuarter = currentState.selectedQuarter

        // 换季/刷新时先清空连载中分组，避免上一季的条目串到本季；
        // 仅切换产地/形式时不重取它——连载中分组不走搜索接口，其可见性由派生属性自动收敛
        if (reloadOngoing) {
            _uiState.update { it.copy(ongoingSubjects = emptyList(), isLoadingOngoing = false) }
        }

        fetchJob =
            viewModelScope.launch {
                // 先置加载态再 join：join 是挂起点，若排在后面，这段窗口里 isLoading 还是旧值，
                // 触底兜底可能挤进来发起一次携带陈旧游标的翻页
                _uiState.update {
                    if (isRefresh) {
                        it.copy(isRefreshing = true, error = null)
                    } else {
                        it.copy(isLoading = true, error = null)
                    }
                }

                staleLoadMore?.join()

                val error = fetchPages(startOffset = 0)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error?.ifBlank { "获取新番导视失败" },
                    )
                }

                if (error == null && reloadOngoing) {
                    loadOngoingSubjects(selectedYear, selectedQuarter)
                }
            }
    }

    /**
     * 补全「本季连载中」：窗口内确有播出事件、但首播日不在本季的条目。
     *
     * 用 Bangumi 权威的 `date` 做最终判据（排期名册里的 airDate 可能缺失），
     * 因此不会有条目同时出现在"本季首播"网格与"本季连载中"分组里。
     * 整段 best-effort：任何失败都退化为"没有这一组"，绝不崩掉主列表。
     */
    private fun loadOngoingSubjects(
        year: Int,
        quarter: SeasonQuarter,
    ) {
        ongoingJob?.cancel()
        val (startDay, endDay) = quarter.getAirDateRange(year)

        // 播出事件来自滚动快照，历史季度查不到任何事件；
        // 强行查询会把"没有数据"误报成"本季没有连载番"，所以干脆不呈现这一组。
        if (endDay < currentDate.toString()) return

        ongoingJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingOngoing = true) }
                val ongoing =
                    runCatchingCancellable {
                        scheduleRepository
                            .getSchedulesAiringBetween(
                                fromUtcIso = "${startDay}T00:00:00Z",
                                toUtcIso = "${endDay}T23:59:59Z",
                            )
                            // 先用名册自带的 airDate 粗筛，避免为"本季首播"的条目白白补一轮详情
                            .filter { candidate -> !isPremiereWithin(candidate.airDate, startDay, endDay) }
                            .take(MAX_ONGOING_SUBJECTS)
                            .fetchSubjectDetails()
                            // 再以 Bangumi 的 date 终判：名册 airDate 缺失的条目在这里被剔除；
                            // 若连 Bangumi 的 date 都缺失，则保留——它本季确有播出事件，宁可多显示也不漏
                            .filter { subject -> !isPremiereWithin(subject.date, startDay, endDay) }
                    }.getOrElse { emptyList() }

                _uiState.update { it.copy(ongoingSubjects = ongoing, isLoadingOngoing = false) }
            }
    }

    /** 逐条补全条目详情；单条失败只跳过该条，不影响整组 */
    private suspend fun List<AirSchedule>.fetchSubjectDetails(): List<Subject> =
        coroutineScope {
            chunked(ONGOING_FETCH_CONCURRENCY)
                .flatMap { chunk ->
                    chunk
                        .map { candidate -> async { subjectRepository.fetchSubjectDetail(candidate.bgmId) } }
                        .awaitAll()
                        .mapNotNull { (it as? AppResult.Success)?.data }
                }.distinctBy { it.id }
        }

    /**
     * 首播日是否落在 [startDay, endDay] 内（含两端）。
     * 空/非法日期视为"无法证明在本季首播"，交由后续以 Bangumi `date` 终判。
     */
    private fun isPremiereWithin(
        date: String,
        startDay: String,
        endDay: String,
    ): Boolean {
        val day = date.take(NORMALIZED_DATE_LENGTH)
        if (day.length < NORMALIZED_DATE_LENGTH) return false
        return day >= startDay && day <= endDay
    }
}
