package com.infinitezerone.minibgm.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.LocalSubjectMatch
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_SIZE = 20

/**
 * 搜索意图：输入文本 + 分类 + 排序 + 是否已发起搜索。
 * 这是本类仅有的可变状态之一；只有 [hasSearched] 为真且文本非空才构成请求。
 */
private data class SearchIntent(
    val text: String = "",
    val hasSearched: Boolean = false,
    val selectedType: Int = 0,
    val selectedSort: SearchSort = SearchSort.MATCH,
)

/** 换挡的唯一判据：请求是否真的变了（文本 / 分类 / 排序）；null = 当前无待执行请求 */
private data class SearchRequestKey(
    val query: String,
    val type: Int,
    val sort: SearchSort,
)

private fun SearchIntent.requestKey(): SearchRequestKey? =
    if (!hasSearched || text.isBlank()) {
        null
    } else {
        SearchRequestKey(query = text.trim(), type = selectedType, sort = selectedSort)
    }

/**
 * 搜索分页会话的投影容器，私有于换挡管线：
 * - [serverCursor] 服务端分页游标：翻页 offset 必须用"已向服务端索取的条目数"推进，
 *   而非去重后的列表长度——服务端数据漂移导致某页与已有结果重叠时，
 *   去重会让列表长度停滞，若用列表长度做 offset 会在重叠区间死循环；
 * - [rawResults] 未排序的原始搜索数据：客户端即时切换排序的双轨数据源。
 */
private data class SearchSession(
    val results: List<Subject> = emptyList(),
    val rawResults: List<Subject> = emptyList(),
    val serverCursor: Int = 0,
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val localMatches: List<LocalSubjectMatch> = emptyList(),
    val offlineNotice: String? = null,
    val error: String? = null,
)

/**
 * 搜索功能 ViewModel —— 响应式 UDF（同模块 [ExploreViewModel] 模板）：
 * - 对外只读 `uiState: StateFlow<SearchUiState>`，状态 = `combine(意图流, 会话投影, 仓库流)`；
 * - 搜索是**查询意图换挡**：`searchIntent` 投影成请求键（query/type/sort）后
 *   `distinctUntilChanged` + `flatMapLatest`——请求真变时旧链整条取消、首取自动发起；
 *   同键重搜（错误重试 / 再次点击搜索）经 [PagingTriggers] 的 Retry 信号重跑会话；
 * - `loadMore` 用 [PagingTriggers] 的 More 信号接入同一会话，无手动 Job 判空判序；
 * - 服务端游标（防漂移死循环）、客户端即时排序双轨、离线降级（别名兜底）原样保留在会话内；
 * - 一次性提示（打卡结果 / 失败原因）走 `Channel(BUFFERED) + receiveAsFlow()`，与状态流隔离；
 * - Room 收藏与搜索历史作为唯一真源直接进入 [uiState] 响应式合并。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {
    // ── 输入：用户意图（仅有的可变状态）──
    private val searchIntent = MutableStateFlow(SearchIntent())
    private val viewMode = MutableStateFlow(SearchViewMode.LIST)
    private val loginPromptVisible = MutableStateFlow(false)

    // 一次性提示：形态与 TagSubjectsViewModel.userMessage 一致（Channel(BUFFERED) + receiveAsFlow）
    private val _userMessage = Channel<String>(Channel.BUFFERED)

    /** 一次性事件流。UI 收集它来弹 Snackbar；不需要也不应该有人回写它 */
    val userMessage: Flow<String> = _userMessage.receiveAsFlow()

    /** 取页信号源：More（触底）/ Retry（同键重搜）接入换挡会话（见 [runSearchSession]） */
    private val pageTriggers = PagingTriggers()

    /** 搜索分页投影：游标 / 原始结果 / 降级状态私有于换挡管线，对外只经 [uiState] 暴露 */
    private val searchSession = MutableStateFlow(SearchSession())

    private val collectionsStream: Flow<Map<Long, CollectionType>> =
        combine(
            collectionRepository.getCollectionsByTypeStream(CollectionType.WISH),
            collectionRepository.getCollectionsByTypeStream(CollectionType.DOING),
            collectionRepository.getCollectionsByTypeStream(CollectionType.COLLECT),
        ) { wish, doing, collect ->
            val map = HashMap<Long, CollectionType>(wish.size + doing.size + collect.size)
            wish.forEach { map[it.subjectId] = CollectionType.WISH }
            doing.forEach { map[it.subjectId] = CollectionType.DOING }
            collect.forEach { map[it.subjectId] = CollectionType.COLLECT }
            map as Map<Long, CollectionType>
        }.catch { emit(emptyMap<Long, CollectionType>()) }

    /** 对外只读不可变 UI 快照：响应式多路合并 */
    val uiState: StateFlow<SearchUiState> =
        combine(
            combine(searchSession, searchIntent, viewMode) { session, intent, mode ->
                SearchUiState(
                    query = intent.text,
                    hasSearched = intent.hasSearched,
                    selectedType = intent.selectedType,
                    selectedSort = intent.selectedSort,
                    viewMode = mode,
                    isLoading = session.isLoading,
                    isLoadingMore = session.isLoadingMore,
                    hasMore = session.hasMore,
                    totalCount = session.totalCount,
                    results = session.results,
                    localMatches = session.localMatches,
                    offlineNotice = session.offlineNotice,
                    error = session.error,
                )
            },
            collectionsStream,
            searchRepository.getSearchHistory().catch { emit(emptyList()) },
            loginPromptVisible,
        ) { base, collections, history, loginPrompt ->
            base.copy(
                userCollections = collections,
                searchHistory = history,
                showLoginPromptDialog = loginPrompt,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SearchUiState(),
        )

    init {
        searchIntent
            .map { it.requestKey() }
            .distinctUntilChanged()
            .onEach { key ->
                // 键变 null（清空输入 / 未发起搜索）：整条会话清空，flatMapLatest 同时取消在飞请求
                if (key == null) searchSession.value = SearchSession()
            }.flatMapLatest { key ->
                if (key == null) {
                    emptyFlow()
                } else {
                    pageTriggers.signals().onEach { signal -> runSearchSession(key, signal) }
                }
            }.launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        if (searchIntent.value.text == query) return
        searchIntent.value = searchIntent.value.copy(text = query, hasSearched = false)
    }

    /** 切换分类：已搜索时请求键真变 → 自动换挡重搜；未搜索时只改筛选，不发请求 */
    fun onTypeSelect(type: Int) {
        if (searchIntent.value.selectedType == type) return
        searchIntent.value = searchIntent.value.copy(selectedType = type)
    }

    fun onSortChange(sort: SearchSort) {
        if (searchIntent.value.selectedSort == sort) return
        // 1. 本地即刻 0ms 视觉即时响应：用未排序原始数据重排当前结果（客户端排序双轨）
        searchSession.update { it.copy(results = sortResults(it.rawResults, sort)) }
        // 2. 服务端异步全局排序查询：排序进请求键，键真变自动换挡重搜
        searchIntent.value = searchIntent.value.copy(selectedSort = sort)
    }

    fun onViewModeToggle() {
        viewMode.value =
            if (viewMode.value == SearchViewMode.LIST) {
                SearchViewMode.GRID
            } else {
                SearchViewMode.LIST
            }
    }

    fun toggleCollection(
        subject: Subject,
        targetType: CollectionType,
    ) {
        viewModelScope.launch {
            val isLoggedIn = authRepository.isLoggedIn.first()
            if (!isLoggedIn) {
                loginPromptVisible.value = true
                return@launch
            }

            val currentType = uiState.value.userCollections[subject.id]
            if (currentType == targetType) {
                // 已标记为该状态，无需重复打卡（Bangumi v0 API 不支持 Subject 删除收藏操作）
                return@launch
            }

            val subjectType = SubjectType.fromValue(subject.type)
            val verb = targetType.getVerb(subjectType)

            // 后台静默同步至 Bangumi 远端（防因导航切换取消）
            withContext(NonCancellable) {
                val syncResult =
                    collectionRepository.updateCollectionStatus(
                        subjectId = subject.id,
                        type = targetType,
                        subjectType = subject.type,
                    )

                when (syncResult) {
                    is AppResult.Success -> {
                        _userMessage.trySend("已标记为「$verb」")
                    }

                    is AppResult.Error -> {
                        // syncResult.message 已由仓库层带上动作前缀（如「打卡失败：网络超时，请重试」）。
                        // 这里再拼一层就会变成「打卡失败：打卡失败：…」——只兜底，不加前缀。
                        _userMessage.trySend(syncResult.message.ifBlank { "打卡失败，请重试" })
                    }

                    is AppResult.Loading -> Unit
                }
            }
        }
    }

    fun dismissLoginPrompt() {
        loginPromptVisible.value = false
    }

    /** 发起搜索（软键盘 / 搜索按钮 / 历史词条 / 错误重试共用入口） */
    fun search(overrideQuery: String? = null) {
        val targetQuery = (overrideQuery ?: searchIntent.value.text).trim()
        if (targetQuery.isBlank()) return
        val next = searchIntent.value.copy(text = targetQuery, hasSearched = true)
        val keyChanged = next.requestKey() != searchIntent.value.requestKey()
        searchIntent.value = next
        // 同键重搜（错误重试 / 再次点击搜索）不会触发换挡，用 Retry 信号强制重跑会话
        if (!keyChanged) pageTriggers.retry()
    }

    fun deleteHistoryItem(query: String) {
        viewModelScope.launch {
            searchRepository.removeSearchHistory(query)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            searchRepository.clearSearchHistory()
        }
    }

    /** 列表滚动位置回报（UI 逐帧调用，仅写 plain 字段不触发重组） */
    fun onListScrollPositionChanged(
        index: Int,
        offset: Int,
    ) {
        listScrollIndex = index
        listScrollOffset = offset
    }

    /** 网格滚动位置回报 */
    fun onGridScrollPositionChanged(
        index: Int,
        offset: Int,
    ) {
        gridScrollIndex = index
        gridScrollOffset = offset
    }

    // 06-B：滚动位置在 VM 层记忆（plain 字段，不驱动重组），重建组合时作为初始位置回填
    var listScrollIndex: Int = 0
        private set
    var listScrollOffset: Int = 0
        private set
    var gridScrollIndex: Int = 0
        private set
    var gridScrollOffset: Int = 0
        private set

    // 06-B：搜索代数——每次新搜索/切类/切序自增，UI 仅在该值变化时回滚到顶部（返回不触发）
    var searchGeneration: Int = 0
        private set

    private fun resetScrollState() {
        listScrollIndex = 0
        listScrollOffset = 0
        gridScrollIndex = 0
        gridScrollOffset = 0
        searchGeneration += 1
    }

    fun clearQuery() {
        resetScrollState()
        searchIntent.value = searchIntent.value.copy(text = "", hasSearched = false)
    }

    /** 触底增量分页加载：作为 More 信号接入当前换挡会话 */
    fun loadMore() {
        pageTriggers.loadMore()
    }

    /**
     * 搜索分页会话：首取（Initial / Retry / Refresh）从 offset 0 重取；
     * 触底（More）从服务端游标追加。并发与越界控制全部以会话快照判据，无手动 Job。
     */
    private suspend fun runSearchSession(
        key: SearchRequestKey,
        signal: PagingSignal,
    ) {
        val isMore = signal is PagingSignal.More
        val current = searchSession.value

        if (isMore) {
            // 并发控制：正在拉取或已取尽时忽略触底信号
            if (current.isLoading || current.isLoadingMore || !current.hasMore) return
            searchSession.value = current.copy(isLoadingMore = true)
        } else {
            // 首取/重试：重置进度与派生标志；保留已展示结果（非破坏性，顶部进度条表达刷新）
            resetScrollState()
            // 主动搜索写入搜索历史（旧 performSearch 语义：历史与取数同源同节奏）
            searchRepository.addSearchHistory(key.query)
            searchSession.value =
                current.copy(
                    isLoading = true,
                    isLoadingMore = false,
                    hasMore = false,
                    totalCount = 0,
                    error = null,
                    offlineNotice = null,
                    localMatches = emptyList(),
                )
        }

        val offset = if (isMore) current.serverCursor else 0
        // 06-A：先查本地别名词典（Room 缓存窗口内），网络失败时即为离线降级结果
        val localMatches = scheduleRepository.searchLocalSubjects(key.query)
        when (
            val result =
                searchRepository.searchSubjects(
                    query = key.query,
                    type = key.type,
                    sort = key.sort.serverSort,
                    limit = PAGE_SIZE,
                    offset = offset,
                )
        ) {
            is AppResult.Success -> {
                val newItems = result.data.list
                val existingIds = current.rawResults.map { s -> s.id }.toSet()
                val rawResults =
                    if (isMore) {
                        current.rawResults + newItems.filter { it.id !in existingIds }
                    } else {
                        newItems
                    }
                // 游标按服务端实际返回条数推进，与去重后的列表长度解耦（防数据漂移死循环）
                val serverCursor = offset + newItems.size
                val totalCount =
                    if (isMore && result.data.total <= 0) current.totalCount else result.data.total
                searchSession.value =
                    SearchSession(
                        results = sortResults(rawResults, key.sort),
                        rawResults = rawResults,
                        serverCursor = serverCursor,
                        totalCount = totalCount,
                        hasMore = newItems.isNotEmpty() && rawResults.size < totalCount,
                        isLoading = false,
                        isLoadingMore = false,
                        // 别名兜底：本地命中但网络结果未覆盖时补充展示
                        localMatches =
                            if (isMore) {
                                current.localMatches
                            } else {
                                localMatches.filter { m -> newItems.none { it.id == m.bgmId } }
                            },
                        offlineNotice = null,
                        error = null,
                    )
            }

            is AppResult.Error -> {
                if (isMore) {
                    // 追加失败：保留原数据展示，仅收起进度（与既有语义一致，不改错误位）
                    searchSession.value = searchSession.value.copy(isLoadingMore = false)
                } else {
                    // 07-A：弱网降级——本地索引命中时降级为软提示 + 离线结果；否则维持全屏错误
                    val offline = localMatches.take(6)
                    searchSession.value =
                        if (offline.isNotEmpty()) {
                            searchSession.value.copy(
                                isLoading = false,
                                localMatches = offline,
                                offlineNotice = result.message.ifBlank { "网络异常，已展示本地索引命中" },
                            )
                        } else {
                            searchSession.value.copy(
                                isLoading = false,
                                error = result.message,
                                localMatches = emptyList(),
                                offlineNotice = null,
                            )
                        }
                }
            }

            is AppResult.Loading -> Unit
        }
    }

    private fun sortResults(
        list: List<Subject>,
        sort: SearchSort,
    ): List<Subject> =
        when (sort) {
            SearchSort.MATCH -> list
            SearchSort.HEAT -> list.sortedByDescending { it.collection?.collect ?: 0 }
            SearchSort.SCORE ->
                list.sortedWith(
                    compareByDescending<Subject> { it.rating?.score ?: 0.0 }
                        .thenByDescending { it.rating?.total ?: 0 },
                )
            SearchSort.RANK ->
                list.sortedWith(
                    compareBy<Subject> {
                        val r = it.rating?.rank ?: 0
                        if (r > 0) r else Int.MAX_VALUE
                    }.thenByDescending { it.rating?.score ?: 0.0 },
                )
        }
}
