package com.infinitezerone.minibgm.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectType
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 搜索功能 ViewModel：
 * - [onQueryChange] 仅更新输入框内容，空白时自动重置搜索结果回到历史发现页；
 * - [search] 响应软键盘搜索/手动点击搜索按钮，触发网络请求；
 * - [onTypeSelect] 切换分类（已搜索时即时以新分类重搜）；
 * - [onSortChange] 切换排序维度（综合 / 热门 / 高分 / 排名）；
 * - [onViewModeToggle] 切换列表 / 3列海报网格视图；
 * - [toggleCollection] 0ms 乐观快捷打卡（自适应动词：想看/想读/想听/想玩），未登录弹窗拦截；
 * - [loadMore] 触底增量分页加载；
 * - [clearQuery] 一键清空输入与结果。
 */
class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    /**
     * 服务端分页游标：翻页 offset 必须用"已向服务端索取的条目数"推进，
     * 而非去重后的列表长度——服务端数据漂移导致某页与已有结果重叠时，
     * 去重会让列表长度停滞，若用列表长度做 offset 会在重叠区间死循环。
     */
    private var serverCursor = 0

    // 存储未排序的原始搜索数据，方便即时客户端切换排序
    private var rawSearchResults: List<Subject> = emptyList()

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

    init {
        observeSearchHistory()
        observeCollections()
    }

    private fun observeSearchHistory() {
        viewModelScope.launch {
            searchRepository.getSearchHistory().collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }
    }

    private fun observeCollections() {
        viewModelScope.launch {
            combine(
                collectionRepository.getCollectionsByTypeStream(CollectionType.WISH),
                collectionRepository.getCollectionsByTypeStream(CollectionType.DOING),
                collectionRepository.getCollectionsByTypeStream(CollectionType.COLLECT),
            ) { wish, doing, collect ->
                val map = mutableMapOf<Long, CollectionType>()
                wish.forEach { map[it.subjectId] = CollectionType.WISH }
                doing.forEach { map[it.subjectId] = CollectionType.DOING }
                collect.forEach { map[it.subjectId] = CollectionType.COLLECT }
                map
            }.catch {
                // Ignore collection observe errors
            }.collect { collectionsMap ->
                _uiState.update { it.copy(userCollections = collectionsMap) }
            }
        }
    }

    fun onQueryChange(query: String) {
        if (_uiState.value.query == query) return
        _uiState.update {
            it.copy(
                query = query,
                hasSearched = false,
                error = null,
            )
        }

        if (query.isBlank()) {
            searchJob?.cancel()
            loadMoreJob?.cancel()
            rawSearchResults = emptyList()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    hasMore = false,
                    totalCount = 0,
                    results = emptyList(),
                    error = null,
                )
            }
        }
    }

    fun onTypeSelect(type: Int) {
        if (_uiState.value.selectedType == type) return
        _uiState.update { it.copy(selectedType = type) }
        val currentQuery = _uiState.value.query.trim()
        if (currentQuery.isNotBlank() && _uiState.value.hasSearched) {
            performSearch(currentQuery, type, _uiState.value.selectedSort)
        }
    }

    fun onSortChange(sort: SearchSort) {
        if (_uiState.value.selectedSort == sort) return
        // 1. 本地即刻 0ms 视觉即时响应
        _uiState.update {
            it.copy(
                selectedSort = sort,
                results = sortResults(rawSearchResults, sort),
            )
        }
        val currentQuery = _uiState.value.query.trim()
        if (currentQuery.isNotBlank() && _uiState.value.hasSearched) {
            // 2. 服务端异步全局排序查询
            performSearch(
                query = currentQuery,
                type = _uiState.value.selectedType,
                sort = sort,
            )
        }
    }

    fun onViewModeToggle() {
        _uiState.update {
            val nextMode = if (it.viewMode == SearchViewMode.LIST) SearchViewMode.GRID else SearchViewMode.LIST
            it.copy(viewMode = nextMode)
        }
    }

    fun toggleCollection(
        subject: Subject,
        targetType: CollectionType,
    ) {
        viewModelScope.launch {
            val isLoggedIn = authRepository.isLoggedIn.first()
            if (!isLoggedIn) {
                _uiState.update { it.copy(showLoginPromptDialog = true) }
                return@launch
            }

            val currentType = _uiState.value.userCollections[subject.id]
            if (currentType == targetType) {
                // 已标记为该状态，无需重复打卡（Bangumi v0 API 不支持 Subject 删除收藏操作）
                return@launch
            }

            val subjectType = SubjectType.fromValue(subject.type)
            val verb = targetType.getVerb(subjectType)
            val feedbackMsg = "已标记为「$verb」"

            // 0ms 乐观更新本地 UI
            _uiState.update { state ->
                val updated = state.userCollections.toMutableMap()
                updated[subject.id] = targetType
                state.copy(userCollections = updated, userMessage = feedbackMsg)
            }

            // 后台静默同步至 Bangumi 远端（防因导航切换取消）
            withContext(NonCancellable) {
                val syncResult =
                    collectionRepository.updateCollectionStatus(
                        subjectId = subject.id,
                        type = targetType,
                        subjectType = subject.type,
                    )

                if (syncResult is AppResult.Error) {
                    // 同步失败，回滚状态
                    _uiState.update { current ->
                        val rollback = current.userCollections.toMutableMap()
                        if (currentType != null) {
                            rollback[subject.id] = currentType
                        } else {
                            rollback.remove(subject.id)
                        }
                        current.copy(
                            userCollections = rollback,
                            userMessage = "打卡失败：${syncResult.message.ifBlank { "网络异常" }}",
                        )
                    }
                }
            }
        }
    }

    /** 开始 OAuth 授权流程，隐藏提示弹窗并生成授权 URL（由 UI 层通过系统浏览器/Custom Tabs 打开，保持 ViewModel 与 Android Context 零耦合） */
    suspend fun beginLogin(): String {
        _uiState.update { it.copy(showLoginPromptDialog = false) }
        return authRepository.beginLogin()
    }

    fun dismissLoginPrompt() {
        _uiState.update { it.copy(showLoginPromptDialog = false) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun search(overrideQuery: String? = null) {
        val targetQuery = (overrideQuery ?: _uiState.value.query).trim()
        if (targetQuery.isNotBlank()) {
            _uiState.update { it.copy(query = targetQuery, hasSearched = true) }
            performSearch(targetQuery, _uiState.value.selectedType, _uiState.value.selectedSort)
        }
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

    private fun resetScrollState() {
        listScrollIndex = 0
        listScrollOffset = 0
        gridScrollIndex = 0
        gridScrollOffset = 0
        searchGeneration += 1
    }

    fun clearQuery() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        rawSearchResults = emptyList()
        resetScrollState()
        _uiState.update {
            it.copy(
                query = "",
                hasSearched = false,
                isLoading = false,
                isLoadingMore = false,
                hasMore = false,
                totalCount = 0,
                results = emptyList(),
                error = null,
                localMatches = emptyList(),
                offlineNotice = null,
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore || state.query.isBlank()) return

        loadMoreJob?.cancel()
        loadMoreJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingMore = true) }
                val currentOffset = serverCursor
                when (
                    val result =
                        searchRepository.searchSubjects(
                            query = state.query.trim(),
                            type = state.selectedType,
                            sort = state.selectedSort.serverSort,
                            limit = PAGE_SIZE,
                            offset = currentOffset,
                        )
                ) {
                    is AppResult.Success -> {
                        val newItems = result.data.list
                        val existingIds = rawSearchResults.map { s -> s.id }.toSet()
                        val uniqueNew = newItems.filter { it.id !in existingIds }
                        rawSearchResults = rawSearchResults + uniqueNew
                        // 游标按服务端实际返回条数推进，与去重后的列表长度解耦
                        serverCursor = currentOffset + newItems.size
                        val newTotal = if (result.data.total > 0) result.data.total else state.totalCount
                        val sorted = sortResults(rawSearchResults, state.selectedSort)

                        _uiState.update { current ->
                            current.copy(
                                isLoadingMore = false,
                                results = sorted,
                                totalCount = newTotal,
                                hasMore = newItems.isNotEmpty() && rawSearchResults.size < newTotal,
                            )
                        }
                    }

                    is AppResult.Error -> {
                        _uiState.update { it.copy(isLoadingMore = false) }
                    }

                    is AppResult.Loading -> Unit
                }
            }
    }

    private fun performSearch(
        query: String,
        type: Int,
        sort: SearchSort = _uiState.value.selectedSort,
    ) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotBlank()) {
            viewModelScope.launch {
                searchRepository.addSearchHistory(trimmedQuery)
            }
        }
        resetScrollState()
        searchJob?.cancel()
        loadMoreJob?.cancel()
        searchJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        hasSearched = true,
                        isLoading = true,
                        isLoadingMore = false,
                        hasMore = false,
                        totalCount = 0,
                        error = null,
                        offlineNotice = null,
                        localMatches = emptyList(),
                    )
                }
                // 06-A：先查本地别名词典（Room 缓存窗口内），网络失败时即为离线降级结果
                val localMatches = scheduleRepository.searchLocalSubjects(trimmedQuery)
                when (
                    val result =
                        searchRepository.searchSubjects(
                            query = trimmedQuery,
                            type = type,
                            sort = sort.serverSort,
                            limit = PAGE_SIZE,
                            offset = 0,
                        )
                ) {
                    is AppResult.Success -> {
                        val data = result.data
                        rawSearchResults = data.list
                        serverCursor = data.list.size
                        val sorted = sortResults(data.list, sort)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                results = sorted,
                                totalCount = data.total,
                                hasMore = data.list.isNotEmpty() && data.list.size < data.total,
                                error = null,
                                // 别名兜底：本地命中但网络结果未覆盖时补充展示
                                localMatches = localMatches.filter { m -> data.list.none { it.id == m.bgmId } },
                                offlineNotice = null,
                            )
                        }
                    }

                    is AppResult.Error -> {
                        // 07-A：弱网降级——本地索引命中时降级为软提示 + 离线结果；否则维持全屏错误
                        val offline = localMatches.take(6)
                        if (offline.isNotEmpty()) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    localMatches = offline,
                                    offlineNotice = result.message.ifBlank { "网络异常，已展示本地索引命中" },
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = result.message,
                                    localMatches = emptyList(),
                                    offlineNotice = null,
                                )
                            }
                        }
                    }

                    is AppResult.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
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

    companion object {
        private const val PAGE_SIZE = 20
    }
}
