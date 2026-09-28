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

    fun selectCategory(category: SeasonCategoryFilter) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }
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
        if (currentState.isLoading || currentState.isLoadingMore || currentState.isRefreshing || !currentState.hasMore) {
            return
        }

        loadMoreJob?.cancel()
        // 翻页 offset 用独立的服务端游标而非 subjects.size：去重会丢弃重复条目，两者一旦错位就会跳过数据
        val offset = currentState.pageOffset
        val request = buildSearchRequest(currentState)

        loadMoreJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingMore = true) }
                when (val result = searchRepository.searchSubjectsAdvanced(request = request, limit = PAGE_SIZE, offset = offset)) {
                    is AppResult.Success -> {
                        val newSubjects = result.data.list
                        _uiState.update {
                            val existingIds = it.subjects.map { s -> s.id }.toSet()
                            val uniqueNew = newSubjects.filter { s -> s.id !in existingIds }
                            val loadedCount = offset + newSubjects.size
                            it.copy(
                                isLoadingMore = false,
                                subjects = it.subjects + uniqueNew,
                                pageOffset = loadedCount,
                                hasMore = loadedCount < result.data.total,
                            )
                        }
                    }
                    is AppResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                userMessage = "加载更多失败：${result.message}",
                            )
                        }
                    }
                    is AppResult.Loading -> Unit
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
        return SearchSubjectsRequest(
            sort = "heat",
            filter =
                SearchFilter(
                    type = listOf(2),
                    airDate = listOf(">=$startDay", "<=$endDay"),
                ),
        )
    }

    private fun loadSeasonalAnime(isRefresh: Boolean = false) {
        fetchJob?.cancel()
        loadMoreJob?.cancel()
        ongoingJob?.cancel()
        val currentState = _uiState.value
        val request = buildSearchRequest(currentState)
        val selectedYear = currentState.selectedYear
        val selectedQuarter = currentState.selectedQuarter

        // 换季/刷新时先清空连载中分组，避免上一季的条目串到本季
        _uiState.update { it.copy(ongoingSubjects = emptyList(), isLoadingOngoing = false) }

        fetchJob =
            viewModelScope.launch {
                _uiState.update {
                    if (isRefresh) {
                        it.copy(isRefreshing = true, error = null)
                    } else {
                        it.copy(isLoading = true, error = null)
                    }
                }

                when (val result = searchRepository.searchSubjectsAdvanced(request = request, limit = PAGE_SIZE, offset = 0)) {
                    is AppResult.Success -> {
                        val firstPage = result.data.list
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                subjects = firstPage,
                                pageOffset = firstPage.size,
                                hasMore = firstPage.size < result.data.total,
                                error = null,
                            )
                        }
                        loadOngoingSubjects(selectedYear, selectedQuarter)
                    }
                    is AppResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = result.message.ifBlank { "获取新番导视失败" },
                            )
                        }
                    }
                    is AppResult.Loading -> Unit
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
