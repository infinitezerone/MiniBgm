package com.infinitezerone.minibgm.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SearchFilter
import com.infinitezerone.minibgm.core.model.SearchSubjectsRequest
import com.infinitezerone.minibgm.core.model.Subject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Bangumi `POST /v0/search/subjects` 单页**硬上限**：limit 传更大只会被服务端静默按 20 截断，
 * 翻页进度才能与响应对上（与 [SeasonalGuideViewModel] 同名常量语义一致）。
 */
private const val PAGE_SIZE = 20

/**
 * 一次网络请求的全部输入 —— **换挡的唯一判据**。
 *
 * 标签固定于本 ViewModel 实例，能改变请求的只有分类与排序。
 */
private data class TagRequestKey(
    val type: Int,
    val sort: SearchSort,
)

private fun TagRequestKey.toRequest(tag: String): SearchSubjectsRequest {
    val typeList = if (type > 0) listOf(type) else null
    return SearchSubjectsRequest(
        sort = sort.serverSort,
        filter =
            SearchFilter(
                tag = listOf(tag),
                type = typeList,
            ),
    )
}

/**
 * 「标签条目」分页链路的投影；[pageOffset] 是顺序游标，取值恒等于去重后
 * [TagPagedSubjects.subjects] 的长度（与旧实现的 offset 语义一致），见 [TagSubjectsViewModel]
 */
private data class TagPagedSubjects(
    val subjects: List<Subject> = emptyList(),
    val pageOffset: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * 标签专题条目 ViewModel —— 套用 [SeasonalGuideViewModel] 的响应式 UDF 规范：
 * 1. 对外只读 `uiState: StateFlow<…>`，状态 = `combine(意图流, 仓库流)` 的投影 + `stateIn`；
 * 2. 可变状态只剩**用户意图**（分类 / 排序 / 视图形态）与私有分页游标；按意图取数用
 *    `flatMapLatest` 换挡，禁止手动 Job 判空判序（searchJob / loadMoreJob 一律不允许）；
 * 3. **写后读**：收藏集合是仓库流（Room）直接 `combine` 汇入，写入后它自己重发，
 *    不写手动乐观状态与回滚代码；
 * 4. 一次性事件（Snackbar）走 `Channel(BUFFERED) + receiveAsFlow()`，与状态流隔离；
 * 5. 本页无对话框多步会话，无第 5 条的局部会话态。
 *
 * 「标签条目」分页链：`(selectedType, selectedSort)` 投影成 [TagRequestKey]、经
 * `distinctUntilChanged` + `flatMapLatest` 换挡——分类/排序一变，旧链整条取消
 * （含在途的追加请求，旧过滤结果不可能混入新列表）、游标重置、自动先取一次首页。
 * 唯一保留的顺序状态是 [TagPagedSubjects.pageOffset]：第 N 页依赖第 N-1 页的 offset
 * （恒为去重后列表长度），这天生是顺序过程，私有于本类，对外仍只通过 [uiState] 暴露。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagSubjectsViewModel(
    val tag: String,
    initialType: Int = 0,
    private val searchRepository: SearchRepository,
    private val collectionRepository: CollectionRepository,
) : ViewModel() {
    // ── 输入：用户意图。这是本类仅有的可变用户状态 ──
    private val selectedType = MutableStateFlow(initialType)
    private val selectedSort = MutableStateFlow(SearchSort.RANK)
    private val viewMode = MutableStateFlow(SearchViewMode.LIST)

    // 一次性提示（追加失败 / 打卡失败）走 Channel：发生一次就该消失，与状态流物理隔离
    private val _userMessage = Channel<String>(Channel.BUFFERED)

    /** 一次性事件流。UI 收集它来弹 Snackbar；不需要也不应该有人回写它 */
    val userMessage: Flow<String> = _userMessage.receiveAsFlow()

    /** 取页信号源：公共触发器管线，`signals()` 汇流后经 `flatMapLatest` 换挡 */
    private val pageTriggers = PagingTriggers()

    // ── 分页投影。游标是顺序状态（第 N 页依赖第 N-1 页的 offset），留一个私有容器；
    //    竞态全部交给 flatMapLatest：请求键一变，旧链取消、游标重置。──
    private val pagedSubjects = MutableStateFlow(TagPagedSubjects())

    private val stateTemplate =
        TagSubjectsUiState(
            tag = tag,
            selectedType = initialType,
            isLoading = true,
        )

    /**
     * 收藏三流（ wish / doing / collect ）并成一张 `subjectId → CollectionType` 的映射，
     * 作为仓库流直接 `combine` 汇入主投影——不存在离散 collect 回写状态。
     */
    private val collectionsMap: Flow<Map<Long, CollectionType>> =
        combine(
            collectionRepository.getCollectionsByTypeStream(CollectionType.WISH),
            collectionRepository.getCollectionsByTypeStream(CollectionType.DOING),
            collectionRepository.getCollectionsByTypeStream(CollectionType.COLLECT),
        ) { wish, doing, collect ->
            buildMap {
                wish.forEach { put(it.subjectId, CollectionType.WISH) }
                doing.forEach { put(it.subjectId, CollectionType.DOING) }
                collect.forEach { put(it.subjectId, CollectionType.COLLECT) }
            }
        }.catch {
            // Ignore collection observe errors
            emit(emptyMap())
        }

    /**
     * 对外只读投影：`combine` 出来的不可变快照。
     *
     * Eagerly 而非 WhileSubscribed：投影必须在无人订阅时也保持最新（进页即取数的原语义），
     * 否则单测读到的永远是初值；上游都是内存流与 Room 流，常驻收集的开销可忽略。
     */
    val uiState: StateFlow<TagSubjectsUiState> =
        combine(
            pagedSubjects,
            selectedType,
            selectedSort,
            viewMode,
            collectionsMap,
        ) { pages, type, sort, mode, collections ->
            stateTemplate.copy(
                selectedType = type,
                selectedSort = sort,
                viewMode = mode,
                subjects = pages.subjects,
                userCollections = collections,
                isLoading = pages.isLoading,
                isRefreshing = pages.isRefreshing,
                isLoadingMore = pages.isLoadingMore,
                hasMore = pages.hasMore,
                errorMessage = pages.errorMessage,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, stateTemplate)

    init {
        // 分页链在 TagRequestKey 变化时换挡重查：旧链（含在途追加）整条取消、游标重置
        combine(selectedType, selectedSort) { type, sort -> TagRequestKey(type, sort) }
            .distinctUntilChanged()
            .onEach { pagedSubjects.value = TagPagedSubjects() }
            .flatMapLatest { key -> onPageSignals(key) }
            .launchIn(viewModelScope)
    }

    /** 三条触发流汇成一条信号流；进页 / 换挡后自动先取一次首页 */
    private fun onPageSignals(key: TagRequestKey): Flow<PagingSignal> =
        pageTriggers.signals().onEach { signal -> runPagingSession(key, signal) }

    /** 切换分类（全部/动画/书籍/游戏/音乐）；服务端下推，切换即换挡重查 */
    fun onSelectType(type: Int) {
        if (selectedType.value == type) return
        selectedType.value = type
    }

    /** 切换排序方式；服务端排序，切换即换挡重查 */
    fun onSelectSort(sort: SearchSort) {
        if (selectedSort.value == sort) return
        selectedSort.value = sort
    }

    /** 切换列表/网格双视图；纯展示偏好，不触发重新取数 */
    fun setViewMode(viewMode: SearchViewMode) {
        this.viewMode.value = viewMode
    }

    fun refresh() {
        pageTriggers.refresh()
    }

    fun retry() {
        pageTriggers.retry()
    }

    /** 触底追加：游标 = 去重后列表长度；进行中或已取尽时信号被会话守卫忽略 */
    fun loadMore() {
        pageTriggers.loadMore()
    }

    /**
     * 标记 / 取消收藏。
     *
     * 不做乐观更新：收藏集合是仓库流（Room）经 [collectionsMap] `combine` 汇入的投影，
     * 写入后它会自己重发，UI 随之更新。失败只通过一次性提示位告知。
     */
    fun updateCollection(
        subject: Subject,
        type: CollectionType,
    ) {
        viewModelScope.launch {
            val syncResult =
                collectionRepository.updateCollectionStatus(
                    subjectId = subject.id,
                    type = type,
                    comment = "",
                    rate = null,
                    private = false,
                )
            if (syncResult is AppResult.Error) {
                // message 自带仓库层的动作前缀，再拼「打卡失败：」会叠成两层——只兜底，不加前缀
                _userMessage.send(syncResult.message.ifBlank { "打卡失败，请重试" })
            }
        }
    }

    // ── 分页会话：从当前游标取一页；首屏/重试/刷新 offset 归零，追加用游标续取 ──

    private suspend fun runPagingSession(
        key: TagRequestKey,
        signal: PagingSignal,
    ) {
        val isRefresh = signal is PagingSignal.Refresh
        val isInitialOrRetry = signal is PagingSignal.Initial || signal is PagingSignal.Retry
        val isMore = signal is PagingSignal.More

        val current = pagedSubjects.value
        // 并发控制（会话守卫取代旧的 Job 判空判序）：
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
            pagedSubjects.value = TagPagedSubjects(isLoading = true)
        } else if (isRefresh) {
            pagedSubjects.value = current.copy(isRefreshing = true, errorMessage = null)
        } else {
            pagedSubjects.value = current.copy(isLoadingMore = true, errorMessage = null)
        }

        // offset 语义与旧实现一致：追加时用去重后列表长度作为顺序游标
        val offset = if (isMore) current.pageOffset else 0

        try {
            when (
                val result =
                    searchRepository.searchSubjectsAdvanced(
                        request = key.toRequest(tag),
                        limit = PAGE_SIZE,
                        offset = offset,
                    )
            ) {
                is AppResult.Error -> {
                    if (isMore) {
                        // 追加失败：保留原数据展示，仅收起进度并通过一次性提示告知
                        pagedSubjects.value = pagedSubjects.value.copy(isLoadingMore = false)
                        _userMessage.send(result.message.ifBlank { "加载更多失败" })
                    } else {
                        // 首屏 / 重试 / 刷新失败：交由全屏错误态呈现（刷新时保留原列表）
                        settle(error = result.message.ifBlank { "获取标签条目失败" })
                    }
                }

                is AppResult.Loading -> settle()

                is AppResult.Success -> {
                    val items = result.data.list
                    val updatedSubjects =
                        if (isMore) {
                            val existingIds = current.subjects.map { it.id }.toSet()
                            current.subjects + items.filter { it.id !in existingIds }
                        } else {
                            items
                        }
                    pagedSubjects.value =
                        TagPagedSubjects(
                            subjects = updatedSubjects,
                            // 游标恒等于去重后列表长度
                            pageOffset = updatedSubjects.size,
                            hasMore = items.size >= PAGE_SIZE,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = null,
                        )
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            if (isMore) {
                pagedSubjects.value = pagedSubjects.value.copy(isLoadingMore = false)
                _userMessage.send("加载更多失败")
            } else {
                settle(error = "网络连接异常，请重试")
            }
        }
    }

    /** 会话收尾：把进行中的标记位清掉；错误位只在失败时带出去 */
    private fun settle(error: String? = null) {
        val current = pagedSubjects.value
        pagedSubjects.value =
            current.copy(isLoading = false, isRefreshing = false, isLoadingMore = false, errorMessage = error)
    }
}
