package com.infinitezerone.minibgm.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SearchFilter
import com.infinitezerone.minibgm.core.model.SearchSubjectsRequest
import com.infinitezerone.minibgm.core.model.Subject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Bangumi `POST /v0/search/subjects` 单页**硬上限**：limit 传更大不会报错，只会静默按 20 截断。
 * 因此这里必须与真实页宽一致，翻页进度才能与响应 total 对上。
 */
private const val PAGE_SIZE = 20

/** 一次查询的完整条件；五者任一变化都构成一次**新查询** */
private data class SeasonQuery(
    val year: Int,
    val quarter: SeasonQuarter,
    val origin: SeasonOriginFilter,
    val forms: Set<SeasonFormFilter>,
    val sort: SeasonSortOption,
)

/** 「本季首播」分页链路的投影；[pageOffset] 是顺序游标，见 [SeasonalGuideViewModel.pagedSubjects] */
private data class PagedSubjects(
    val subjects: List<Subject> = emptyList(),
    val pageOffset: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)

/**
 * 取页信号：由各用户意图触发流 `merge` 而来。
 *
 * `Initial` 是进页 / 查询换挡时的自动首取；`Retry` / `Refresh` / `More` 分别对应
 * 重试、下拉刷新、触底追加。
 */
private sealed interface PageSignal {
    data object Initial : PageSignal

    data object Retry : PageSignal

    data object Refresh : PageSignal

    data object More : PageSignal
}

/**
 * 季度片单 ViewModel —— **方案 B：响应式派生流**。
 *
 * 状态是「底层事件流的数学映射」，不是被命令式修改的容器：
 * - 可变状态只剩**用户意图**（档期 / 产地 / 形式 / 视图形态）与两个一次性 UI 提示位；
 * - 「本季首播」是 [seasonQuery] 经 `flatMapLatest` 换挡的分页链——查询一变，旧链整条被取消、
 *   游标随之重置，**不需要手动管理 fetchJob / loadMoreJob 的判空与判序**；
 * - 登录态与收藏集合是仓库流，直接作为上游 `combine` 汇入——因此**不存在乐观更新与手动回滚**，
 *   Room 流在写入后会自己重发，单一事实源。
 *
 * 唯一保留的顺序状态是 [pagedSubjects] 里的分页游标：第 N 页依赖第 N-1 页的 offset，
 * 这天生是顺序过程，无法用纯投影表达；它私有于本类，对外仍只通过 [uiState] 暴露。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeasonalGuideViewModel(
    private val searchRepository: SearchRepository,
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
    initialYear: Int = 0,
    initialSeasonMonth: Int = 0,
    timeProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val currentDate = timeProvider()

    // ── 输入：用户意图。这是本类仅有的可变状态 ──
    // 四个查询条件绑成一个不可分状态：若拆成四个 MutableStateFlow 再 combine，
    // selectSeason 的两次赋值会各触发一次换挡——"改个季"变成两次完整请求。
    private val seasonQuery =
        MutableStateFlow(
            SeasonQuery(
                year = if (initialYear > 0) initialYear else currentDate.year,
                quarter =
                    if (initialSeasonMonth > 0) {
                        SeasonQuarter.fromMonth(initialSeasonMonth)
                    } else {
                        SeasonQuarter.fromDate(currentDate)
                    },
                origin = SeasonOriginFilter.ALL,
                forms = SeasonFormFilter.DEFAULT,
                sort = SeasonSortOption.DEFAULT,
            ),
        )
    private val viewMode = MutableStateFlow(SeasonalViewMode.LIST)

    // 一次性提示的载体：对话框要不要弹是**状态**（关掉前一直成立），所以留在投影里；
    // Toast 这类"发生一次就该消失"的事件走 Channel，与状态流物理隔离、消费即消失
    private val loginPromptVisible = MutableStateFlow(false)

    // 形态与 ScheduleViewModel 的 _userMessage 一致：Channel(BUFFERED) + receiveAsFlow
    private val _uiEffects = Channel<UiEffect>(Channel.BUFFERED)

    /** 一次性事件流。UI 收集它来弹提示 / 触发跳转；不需要也不应该有人回写它 */
    val uiEffects: Flow<UiEffect> = _uiEffects.receiveAsFlow()

    /** 取页信号源：每个用户意图一条流，`merge` 后汇入 `flatMapLatest`（见 [onPageSignals]） */
    private val retryTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val loadMoreTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // ── 本季首播：分页投影。游标是顺序状态（第 N 页依赖第 N-1 页的 offset），留一个私有容器；
    //    竞态全部交给下面的 flatMapLatest：查询一变，旧链取消、游标重置。──
    private val pagedSubjects = MutableStateFlow(PagedSubjects())

    /** 常量部分：年份/季度选项与「当季」标记，建一次不再变 */
    private val stateTemplate =
        SeasonalGuideUiState(
            currentYear = SeasonQuarter.seasonYearOf(currentDate),
            // 季界在每月 21 日，12 月下旬属于**次年**冬季，年份与季度要一起取
            currentQuarter = SeasonQuarter.fromDate(currentDate),
            availableYears = ((currentDate.year + 1) downTo (currentDate.year - 15)).toList(),
            selectedYear = seasonQuery.value.year,
            selectedQuarter = seasonQuery.value.quarter,
        )

    /**
     * 对外只读投影：`combine` 出来的不可变快照。
     *
     * UI 不持有它、不修改它——上游任何一条流变化，这里自然产出新的快照。
     *
     * Eagerly 而非 WhileSubscribed：投影必须在无人订阅时也保持最新（进页即取数的原语义），
     * 否则单测读到的永远是初值；上游都是内存流与 Room 流，常驻收集的开销可忽略。
     */
    val uiState: StateFlow<SeasonalGuideUiState> =
        combine(
            combine(pagedSubjects, seasonQuery, viewMode) { pages, query, mode ->
                stateTemplate.copy(
                    selectedYear = query.year,
                    selectedQuarter = query.quarter,
                    selectedOrigin = query.origin,
                    selectedForms = query.forms,
                    selectedSort = query.sort,
                    viewMode = mode,
                    subjects = pages.subjects,
                    pageOffset = pages.pageOffset,
                    hasMore = pages.hasMore,
                    isLoading = pages.isLoading,
                    isRefreshing = pages.isRefreshing,
                    isLoadingMore = pages.isLoadingMore,
                    error = pages.error,
                )
            },
            combine(
                authRepository.isLoggedIn,
                collectionRepository.getCollectionsByTypeStream(CollectionType.WISH),
                collectionRepository.getCollectionsByTypeStream(CollectionType.DOING),
            ) { loggedIn, wish, doing ->
                stateTemplate.copy(
                    isLoggedIn = loggedIn,
                    wishedSubjectIds = wish.map { it.subjectId }.toSet(),
                    doingSubjectIds = doing.map { it.subjectId }.toSet(),
                )
            },
            loginPromptVisible,
        ) { data, identity, prompt ->
            data.copy(
                isLoggedIn = identity.isLoggedIn,
                wishedSubjectIds = identity.wishedSubjectIds,
                doingSubjectIds = identity.doingSubjectIds,
                showLoginPromptDialog = prompt,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, stateTemplate)

    init {
        // 分页链：查询换挡即重置游标；三条触发流 merge 成一条信号流，每个信号触发一次会话
        // （作为信号流的副作用挂起执行）。用 onEach 顺序处理而非 collectLatest——
        // 同一查询内的连续信号应当排队，中途取消会话会把进行中的标记位永久留在 true。
        seasonQuery
            .onEach { pagedSubjects.value = PagedSubjects() }
            .flatMapLatest { query -> onPageSignals(query) }
            .launchIn(viewModelScope)
    }

    /** 三条触发流汇成一条信号流；进页 / 换挡后自动先取一次首页 */
    private fun onPageSignals(query: SeasonQuery): Flow<PageSignal> =
        merge(
            retryTrigger.map { PageSignal.Retry },
            refreshTrigger.map { PageSignal.Refresh },
            loadMoreTrigger.map { PageSignal.More },
        ).onStart { emit(PageSignal.Initial) }
            .onEach { signal -> runPagingSession(query, signal) }

    fun selectYear(year: Int) = setQuery { it.copy(year = year) }

    fun selectQuarter(quarter: SeasonQuarter) = setQuery { it.copy(quarter = quarter) }

    fun selectSeason(
        year: Int,
        quarter: SeasonQuarter,
    ) = setQuery { it.copy(year = year, quarter = quarter) }

    /** 切换产地筛选；是否下推服务端由 [SeasonOriginFilter.metaTag] 决定 */
    fun selectOrigin(origin: SeasonOriginFilter) = setQuery { it.copy(origin = origin) }

    /** 开关一档放送形式。允许把三档全关掉：空集合表示"不筛形式"，与多选筛选的通用语义一致 */
    fun toggleForm(form: SeasonFormFilter) =
        setQuery { current ->
            current.copy(forms = if (form in current.forms) current.forms - form else current.forms + form)
        }

    /** 切换排序方式；服务端排序，切换即一次新查询 */
    fun selectSort(sort: SeasonSortOption) = setQuery { it.copy(sort = sort) }

    /** 一次改一个条件，且值未变时不发射——combine 的上游少一次无谓换挡 */
    private inline fun setQuery(transform: (SeasonQuery) -> SeasonQuery) {
        val next = transform(seasonQuery.value)
        if (next != seasonQuery.value) seasonQuery.value = next
    }

    /** 切换海报网格／紧凑列表；纯展示偏好，不触发重新取数 */
    fun toggleViewMode() {
        viewMode.value =
            if (viewMode.value == SeasonalViewMode.LIST) {
                SeasonalViewMode.POSTER
            } else {
                SeasonalViewMode.LIST
            }
    }

    /**
     * 标记 / 取消收藏。
     *
     * 不做乐观更新：收藏集合是仓库流（Room）经 `combine` 汇入的投影，写入后它会自己重发，
     * UI 随之更新——手写乐观值加失败回滚等于维护第二份事实源。失败只通过提示位告知。
     */
    fun toggleCollection(
        subjectId: Long,
        targetType: CollectionType,
    ) {
        viewModelScope.launch {
            // 登录态是投影的一部分：直接读当前快照，不再另存一份布尔
            if (!uiState.value.isLoggedIn) {
                loginPromptVisible.value = true
                return@launch
            }
            // 已在目标列表就不重复写：判据读的是投影，与 UI 看到的完全一致
            val alreadyThere =
                when (targetType) {
                    CollectionType.DOING -> subjectId in uiState.value.doingSubjectIds
                    CollectionType.WISH -> subjectId in uiState.value.wishedSubjectIds
                    else -> false
                }
            if (alreadyThere) {
                postEffect(UiEffect.ShowMessage("已在您的「${targetType.label}」列表中"))
                return@launch
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
                postEffect(UiEffect.ShowMessage(result.message.ifBlank { "操作失败，请重试" }))
            }
        }
    }

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    fun retry() {
        retryTrigger.tryEmit(Unit)
    }

    fun loadMore() {
        loadMoreTrigger.tryEmit(Unit)
    }

    fun dismissLoginPrompt() {
        loginPromptVisible.value = false
    }

    suspend fun beginLogin(): String {
        loginPromptVisible.value = false
        return authRepository.beginLogin()
    }

    // ── 分页会话：从当前游标连续取页，直到"可见条目比会话开始时多"或取尽 ──

    private suspend fun runPagingSession(
        query: SeasonQuery,
        signal: PageSignal,
    ) {
        val resetting = signal != PageSignal.More
        // 判据是"可见条目数比会话开始时多"：客户端还压着服务端表达不了的过滤
        // （产地「欧美」是"或"关系、正片与短片是"以上皆非"），会整页整页地把条目滤掉——
        // 若只取一页就收工，可见内容与滚动范围都不变，触底加载会卡死（滑到底不动、不转圈也不加载）。
        val visibleBefore =
            if (resetting) {
                0
            } else {
                pagedSubjects.value.subjects.count { it.isVisibleUnder(query) }
            }
        if (resetting) {
            pagedSubjects.value = PagedSubjects()
        } else {
            val current = pagedSubjects.value
            // 并发控制的唯一入口：已在取页/刷新、或已经取尽，就不再发请求
            if (current.isLoading || current.isRefreshing || current.isLoadingMore || !current.hasMore) return
        }

        while (true) {
            val current = pagedSubjects.value
            pagedSubjects.value =
                current.copy(
                    isLoading = signal is PageSignal.Initial || signal is PageSignal.Retry,
                    isRefreshing = signal is PageSignal.Refresh,
                    isLoadingMore = signal is PageSignal.More,
                    error = null,
                )

            when (val result = searchRepository.searchSubjectsAdvanced(requestOf(query), PAGE_SIZE, current.pageOffset)) {
                is AppResult.Error -> {
                    val message = result.message
                    settle(error = message)
                    // 列表还有内容时失败属于"追加失败"，用 Snackbar 提示即可；
                    // 列表为空则交由全屏错误态（uiState.error）呈现，不重复弹
                    if (pagedSubjects.value.subjects.isNotEmpty()) {
                        postEffect(UiEffect.ShowMessage("加载更多失败：$message"))
                    }
                    return
                }

                is AppResult.Loading -> {
                    settle()
                    return
                }

                is AppResult.Success -> {
                    val page = result.data.list
                    // 服务端没有更多条目时必须在此收工：游标按 page.size 前进，空页不会推进 offset，
                    // 若继续循环就会拿同一个 offset 无限重发（曾让单测挂死 8 分钟）
                    if (page.isEmpty()) {
                        // 顺手把 hasMore 关掉，让下一次 More 信号在入口就被 guard 挡住，不再空发请求
                        pagedSubjects.value = current.copy(hasMore = false)
                        settle()
                        return
                    }
                    // 游标按服务端返回的原始条数前进，过滤不参与——一路取到底得到的过滤结果才是完整的
                    pagedSubjects.value =
                        current.copy(
                            subjects =
                                if (current.pageOffset == 0) {
                                    page
                                } else {
                                    current.subjects.mergeDistinct(page)
                                },
                            pageOffset = current.pageOffset + page.size,
                            hasMore = current.pageOffset + page.size < result.data.total,
                        )
                }
            }

            val settled = pagedSubjects.value
            if (settled.subjects.isEmpty() || !settled.hasMore) {
                settle()
                return
            }
            if (settled.pageOffset > 0 && settled.subjects.count { it.isVisibleUnder(query) } > visibleBefore) {
                settle()
                return
            }
        }
    }

    /** 会话收尾：把进行中的标记位清掉；错误位只在失败时带出去 */
    private fun settle(error: String? = null) {
        val current = pagedSubjects.value
        pagedSubjects.value =
            current.copy(isLoading = false, isRefreshing = false, isLoadingMore = false, error = error)
    }

    /** 投递一次性事件；`Channel.BUFFERED` 的容量足够，正常情况下不会挂起也不会丢 */
    private suspend fun postEffect(effect: UiEffect) {
        _uiEffects.send(effect)
    }

    private fun requestOf(query: SeasonQuery): SearchSubjectsRequest {
        val (startDay, endDay) = query.quarter.getAirDateRange(query.year)
        // 能精确表达的条件一律下推服务端，并组合成 AND（如「日本 + 剧场版」）。
        // 产地「欧美」不下推：服务端 meta_tags 是精确单标签匹配，"欧美 vs 只标具体国家"这种"或"表达不了。
        // 形式里只有"恰好只选剧场版"能下推；正片（TV 或 WEB）与短片（MV 或 PV 或 …）都是"或"关系，
        // 服务端多值又是 AND、没有排除语法，只能客户端筛。见 SeasonalGuideUiState.filteredSubjects。
        val metaTags =
            listOfNotNull(
                query.origin.metaTag,
                SeasonFormFilter.serverMetaTagOf(query.forms),
            )
        return SearchSubjectsRequest(
            sort = query.sort.apiValue,
            filter =
                SearchFilter(
                    type = listOf(2),
                    airDate = listOf(">=$startDay", "<=$endDay"),
                    metaTags = metaTags.ifEmpty { null },
                ),
        )
    }

    /** 按已加载条目的 id 去重后追加一页 */
    private fun List<Subject>.mergeDistinct(page: List<Subject>): List<Subject> {
        val known = map { it.id }.toSet()
        return this + page.filter { it.id !in known }
    }

    /** 条目是否落在当前筛选的可见范围内 */
    private fun Subject.isVisibleUnder(query: SeasonQuery): Boolean = matchesOrigin(this, query.origin) && matchesForm(this, query.forms)
}
