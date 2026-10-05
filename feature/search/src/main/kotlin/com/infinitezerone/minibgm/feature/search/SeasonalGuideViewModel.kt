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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Bangumi `POST /v0/search/subjects` 单页**硬上限**：limit 传更大不会报错，只会静默按 20 截断。
 * 因此这里必须与真实页宽一致，翻页进度才能与响应 total 对上。
 */
private const val PAGE_SIZE = 20

/**
 * 界面上的完整筛选条件：年份／季度／排序／产地／形式／自选标签。
 * 所有条件 100% 精确下推服务端，作为 UI 的单一输入源。
 */
private data class SeasonQuery(
    val year: Int,
    val quarter: SeasonQuarter,
    val origin: SeasonOriginFilter,
    val form: SeasonFormFilter,
    val sort: SeasonSortOption,
    val tags: Set<String> = emptySet(),
)

/**
 * 一次网络请求的全部输入 —— **换挡的唯一判据**。
 *
 * 由 [SeasonQuery] 投影而来，只保留写入请求体的部分。
 */
private data class RequestKey(
    val year: Int,
    val quarter: SeasonQuarter,
    val sort: SeasonSortOption,
    val metaTags: List<String>,
    val tags: List<String>,
)

private fun SeasonQuery.requestKey(): RequestKey =
    RequestKey(
        year = year,
        quarter = quarter,
        sort = sort,
        metaTags = listOfNotNull(origin.metaTag, form.metaTag),
        tags = tags.toList().sorted(),
    )

private val EXCLUDED_HOT_TAGS = setOf("日本", "中国", "TV", "WEB", "剧场版", "OVA", "动画", "动态漫画", "国产")

private fun extractHotTags(subjects: List<Subject>): List<Pair<String, Int>> =
    subjects
        .flatMap { subject ->
            subject.tags.map { it.name.trim() } + subject.metaTags.map { it.trim() }
        }.filter { it.isNotBlank() && it.length <= 10 && it !in EXCLUDED_HOT_TAGS }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }
        .take(25)

private fun RequestKey.toRequest(): SearchSubjectsRequest {
    val (startDay, endDay) = quarter.getAirDateRange(year)
    return SearchSubjectsRequest(
        sort = sort.apiValue,
        filter =
            SearchFilter(
                type = listOf(2),
                airDate = listOf(">=$startDay", "<=$endDay"),
                metaTags = metaTags.ifEmpty { null },
                tag = tags.ifEmpty { null },
            ),
    )
}

/** 「本季首播」分页链路的投影；[pageOffset] 是顺序游标，见 [SeasonalGuideViewModel.pagedSubjects] */
private data class PagedSubjects(
    val subjects: List<Subject> = emptyList(),
    val pageOffset: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)

/**
 * 季度片单 ViewModel —— **方案 B：响应式派生流**。
 *
 * 本类与 [ExploreViewModel] 是全仓 ViewModel 的**响应式 UDF 规范标杆**，新 ViewModel 与迁移
 * 旧 ViewModel 时以此为准：
 * 1. 对外只读 `uiState: StateFlow<…>`，状态 = `combine(意图流, 仓库流)` 的投影 + `stateIn`；
 * 2. 可变状态只剩**用户意图**（不可分查询对象 / 触发器）；按意图取数用 `flatMapLatest` 换挡，
 *    禁止手动 Job 判空判序（loadJobs / refreshJob 一律不允许）；
 * 3. **写后读**：能写仓后经仓库流回读的，不写手动乐观状态与回滚代码；
 * 4. 一次性事件（Snackbar / 导航）走 `Channel(BUFFERED) + receiveAsFlow()`，与状态流隔离；
 * 5. 局部会话态（对话框多步流程）允许保留为独立意图流，不强并进主投影。
 * 命令密集型会话（播放器会话、智能体执行、乐观打卡撤销）可保留显式命令层，但数据侧仍应投影化。
 *
 * 状态是「底层事件流的数学映射」，不是被命令式修改的容器：
 * - 可变状态只剩**用户意图**（档期 / 产地 / 形式 / 视图形态）与两个一次性 UI 提示位；
 * - 「本季首播」是 [seasonQuery] 先投影成 [RequestKey]、再经 `distinctUntilChanged` + `flatMapLatest`
 *   换挡的分页链——**换挡判据是"请求是否真的变了"**，不是"筛选条件是否变了"。因此
 *   「全部→欧美」「正片→正片+短片」这类服务端表达不了、只由客户端补筛的改动不会重取，
 *   已加载的条目与滚动位置都留着；只有请求真变时旧链才整条取消、游标才重置，
 *   **不需要手动管理 fetchJob / loadMoreJob 的判空与判序**；
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
                form = SeasonFormFilter.DEFAULT,
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

    /** 取页信号源：公共触发器管线，`signals()` 汇流后经 `flatMapLatest` 换挡（见 [onPageSignals]） */
    private val pageTriggers = PagingTriggers()

    // ── 本季首播：分页投影。游标是顺序状态（第 N 页依赖第 N-1 页的 offset），留一个私有容器；
    //    竞态全部交给下面的 flatMapLatest：查询一变，旧链取消、游标重置。──
    private val pagedSubjects = MutableStateFlow(PagedSubjects())
    private val purifyContent = MutableStateFlow(true)
    private val expandedGroupKeys = MutableStateFlow<Set<String>>(emptySet())

    /** 常量部分：年份/季度选项与「当季」标记，建一次不再变 */
    private val stateTemplate =
        SeasonalGuideUiState(
            currentYear = SeasonQuarter.seasonYearOf(currentDate),
            // 季界在每月 21 日，12 月下旬属于**次年**冬季，年份与季度要一起取
            currentQuarter = SeasonQuarter.fromDate(currentDate),
            availableYears = ((currentDate.year + 1) downTo (currentDate.year - 15)).toList(),
            selectedYear = seasonQuery.value.year,
            selectedQuarter = seasonQuery.value.quarter,
            isLoading = true,
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
                    selectedForm = query.form,
                    selectedSort = query.sort,
                    selectedTags = query.tags,
                    seasonalHotTags = extractHotTags(pages.subjects),
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
            searchRepository.getCustomFilterTags(),
            combine(purifyContent, expandedGroupKeys) { purify, keys -> Pair(purify, keys) },
        ) { data, identity, prompt, customTags, purifyData ->
            data.copy(
                isLoggedIn = identity.isLoggedIn,
                wishedSubjectIds = identity.wishedSubjectIds,
                doingSubjectIds = identity.doingSubjectIds,
                showLoginPromptDialog = prompt,
                customFilterTags = customTags,
                purifyContent = purifyData.first,
                expandedGroupKeys = purifyData.second,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, stateTemplate)

    init {
        // 分页链在 RequestKey 变化时换挡重查
        seasonQuery
            .map { it.requestKey() }
            .distinctUntilChanged()
            .onEach {
                pagedSubjects.value = PagedSubjects()
                expandedGroupKeys.value = emptySet()
            }.flatMapLatest { key -> onPageSignals(key) }
            .launchIn(viewModelScope)
    }

    /** 三条触发流汇成一条信号流；进页 / 换挡后自动先取一次首页 */
    private fun onPageSignals(key: RequestKey): Flow<PagingSignal> =
        pageTriggers.signals().onEach { signal -> runPagingSession(key, signal) }

    fun selectYear(year: Int) = setQuery { it.copy(year = year) }

    fun selectQuarter(quarter: SeasonQuarter) = setQuery { it.copy(quarter = quarter) }

    fun selectSeason(
        year: Int,
        quarter: SeasonQuarter,
    ) = setQuery { it.copy(year = year, quarter = quarter) }

    /** 切换产地筛选（全部 / 日本 / 国产）；100% 服务端下推，切换即重查 */
    fun selectOrigin(origin: SeasonOriginFilter) = setQuery { it.copy(origin = origin) }

    /** 切换形式筛选（全部 / 剧场版）；100% 服务端下推，切换即重查 */
    fun selectForm(form: SeasonFormFilter) = setQuery { it.copy(form = form) }

    /** 切换排序方式；服务端排序，切换即一次新查询 */
    fun selectSort(sort: SeasonSortOption) = setQuery { it.copy(sort = sort) }

    /** 切换激活/反选某个标签（多选下推） */
    fun toggleTag(tag: String) =
        setQuery { current ->
            val updated = if (tag in current.tags) current.tags - tag else current.tags + tag
            current.copy(tags = updated)
        }

    /** 清空当前选中的所有标签 */
    fun clearSelectedTags() =
        setQuery { current ->
            current.copy(tags = emptySet())
        }

    /** 添加自定义常用筛选标签（持久化） */
    fun addCustomFilterTag(tag: String) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                searchRepository.addCustomFilterTag(tag)
            }
        }
    }

    /** 移除自定义常用筛选标签（持久化） */
    fun removeCustomFilterTag(tag: String) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                searchRepository.removeCustomFilterTag(tag)
            }
            if (tag in seasonQuery.value.tags) {
                toggleTag(tag)
            }
        }
    }

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

    /** 切换内容净化开关（折叠短片、MV、泡面番、动态漫等杂音条目） */
    fun togglePurifyContent() {
        purifyContent.update { !it }
    }

    /** 就地展开/收起特定的折叠胶囊组 */
    fun toggleFoldedGroup(groupKey: String) {
        expandedGroupKeys.update { if (groupKey in it) it - groupKey else it + groupKey }
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
        pageTriggers.refresh()
    }

    fun retry() {
        pageTriggers.retry()
    }

    /**
     * 触底追加。
     *
     * 它同时是**客户端筛选后的补取入口**：改产地／形式若不下推服务端，就不会重新取数，
     * 于是可能出现"可见条目为 0、列表滚不动"的死角。UI 侧观察 `filteredSubjects.size < 阈值`
     * 时调这里，会话会一路取到可见条目确实变多或取尽为止（见 [runPagingSession] 的判据）。
     */
    fun loadMore() {
        pageTriggers.loadMore()
    }

    fun dismissLoginPrompt() {
        loginPromptVisible.value = false
    }

    // ── 分页会话：从当前游标连续取页，直到"可见条目比会话开始时多"或取尽 ──

    private suspend fun runPagingSession(
        key: RequestKey,
        signal: PagingSignal,
    ) {
        val isRefresh = signal is PagingSignal.Refresh
        val isInitialOrRetry = signal is PagingSignal.Initial || signal is PagingSignal.Retry
        val isMore = signal is PagingSignal.More

        val current = pagedSubjects.value
        // 并发控制：
        // 1. More 信号：若已有任意拉取正在进行或已经没有更多，忽略
        if (isMore && (current.isLoading || current.isRefreshing || current.isLoadingMore || !current.hasMore)) {
            return
        }
        // 2. Refresh 信号：若正在首屏加载或刷新中，忽略
        if (isRefresh && (current.isLoading || current.isRefreshing)) {
            return
        }

        // 仅在 Initial/Retry 时清空现有列表；Refresh 保持原列表展示（非破坏性刷新）
        if (isInitialOrRetry) {
            pagedSubjects.value = PagedSubjects(isLoading = true)
        } else if (isRefresh) {
            pagedSubjects.value = current.copy(isRefreshing = true, error = null)
        } else if (isMore) {
            pagedSubjects.value = current.copy(isLoadingMore = true, error = null)
        }

        val offset = if (isMore) current.pageOffset else 0

        when (val result = searchRepository.searchSubjectsAdvanced(key.toRequest(), PAGE_SIZE, offset)) {
            is AppResult.Error -> {
                val message = result.message
                if (isRefresh || isMore) {
                    // 刷新或追加失败：保留原数据展示，仅收起进度并通过 Snackbar 提示
                    pagedSubjects.value =
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                        )
                    if (current.subjects.isNotEmpty()) {
                        postEffect(UiEffect.ShowMessage(message))
                    } else {
                        pagedSubjects.value = pagedSubjects.value.copy(error = message)
                    }
                } else {
                    // 首屏或重试失败：交由全屏错误态呈现
                    settle(error = message)
                }
            }

            is AppResult.Loading -> {
                settle()
            }

            is AppResult.Success -> {
                val page = result.data.list
                val updatedSubjects =
                    if (isMore) {
                        current.subjects.mergeDistinct(page)
                    } else {
                        page
                    }
                val newOffset = offset + page.size
                val hasMore = newOffset < result.data.total && page.isNotEmpty()

                pagedSubjects.value =
                    PagedSubjects(
                        subjects = updatedSubjects,
                        pageOffset = newOffset,
                        hasMore = hasMore,
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        error = null,
                    )
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

    /** 按已加载条目的 id 去重后追加一页 */
    private fun List<Subject>.mergeDistinct(page: List<Subject>): List<Subject> {
        val known = map { it.id }.toSet()
        return this + page.filter { it.id !in known }
    }
}
