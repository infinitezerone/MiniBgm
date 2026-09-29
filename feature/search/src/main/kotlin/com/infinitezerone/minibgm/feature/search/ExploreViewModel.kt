package com.infinitezerone.minibgm.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import com.infinitezerone.minibgm.core.data.repository.SearchRepository
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SearchFilter
import com.infinitezerone.minibgm.core.model.SearchSubjectsRequest
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectComment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_SIZE = 20

/** 一次探索查询的完整条件；任一变化都构成一次新查询换挡 */
private data class ExploreQuery(
    val season: SeasonOption = ALL_TIME_SEASON,
    val category: ExploreCategory = ExploreCategory.ANIME,
    val tags: Set<String> = emptySet(),
    val sort: ExploreSort = ExploreSort.RANK,
    val mood: ExploreMood? = ExploreMood.MASTERPIECE,
)

/** 探索分页链路的投影；[pageOffset] 是服务端顺序游标 */
private data class ExplorePagedSubjects(
    val subjects: List<Subject> = emptyList(),
    val pageOffset: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)

/** 取页信号源：由各用户意图触发流 `merge` 而来 */
private sealed interface ExplorePageSignal {
    data object Initial : ExplorePageSignal

    data object Retry : ExplorePageSignal

    data object Refresh : ExplorePageSignal

    data object More : ExplorePageSignal
}

/**
 * 探索发现页面 ViewModel —— **响应式派生流架构**。
 *
 * 状态是底层数据流与用户意图的数学映射：
 * - 可变状态收敛于用户意图（查询条件、登录弹窗标记、临时消息提示）；
 * - 列表数据由 [exploreQuery] 经 `flatMapLatest` 驱动，查询一变自动换挡并重置游标；
 * - 登录态与用户收藏直接来自仓库流（Room / Flow）在 `combine` 汇入，彻底消除手动乐观回滚；
 * - 社区热评由焦点条目按需异步派生。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModel(
    private val searchRepository: SearchRepository,
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
    private val communityRepository: CommunityRepository? = null,
) : ViewModel() {
    // ── 输入：用户意图（仅有的可变状态）──
    private val exploreQuery = MutableStateFlow(ExploreQuery())
    private val loginPromptVisible = MutableStateFlow(false)
    private val userMessage = MutableStateFlow<String?>(null)
    private val hotComments = MutableStateFlow<Map<Long, SubjectComment>>(emptyMap())

    // ── 取页信号源 ──
    private val retryTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val loadMoreTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // ── 分页投影 ──
    private val pagedSubjects = MutableStateFlow(ExplorePagedSubjects())

    /** 对外只读快照：响应式组合 */
    val uiState: StateFlow<ExploreUiState> =
        combine(
            combine(pagedSubjects, exploreQuery, hotComments) { pages, query, comments ->
                ExploreUiState(
                    selectedSeason = query.season,
                    selectedCategory = query.category,
                    selectedTags = query.tags,
                    selectedSort = query.sort,
                    selectedMood = query.mood,
                    subjects = pages.subjects,
                    pageOffset = pages.pageOffset,
                    hasMore = pages.hasMore,
                    isLoading = pages.isLoading,
                    isRefreshing = pages.isRefreshing,
                    isLoadingMore = pages.isLoadingMore,
                    error = pages.error,
                    hotComments = comments,
                )
            },
            combine(
                authRepository.isLoggedIn,
                combine(
                    collectionRepository.getCollectionsByTypeStream(CollectionType.WISH),
                    collectionRepository.getCollectionsByTypeStream(CollectionType.DOING),
                    collectionRepository.getCollectionsByTypeStream(CollectionType.COLLECT),
                ) { wish, doing, collect ->
                    (wish + doing + collect).map { it.subjectId }.toSet()
                },
            ) { loggedIn, wishedIds ->
                loggedIn to wishedIds
            },
            loginPromptVisible,
            userMessage,
        ) { base, (loggedIn, wishedIds), loginPrompt, msg ->
            base.copy(
                isLoggedIn = loggedIn,
                wishedSubjectIds = wishedIds,
                showLoginPromptDialog = loginPrompt,
                userMessage = msg,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ExploreUiState(),
        )

    init {
        exploreQuery
            .onEach {
                pagedSubjects.value = ExplorePagedSubjects()
                hotComments.value = emptyMap()
            }.flatMapLatest { query -> onPageSignals(query) }
            .launchIn(viewModelScope)
    }

    private fun onPageSignals(query: ExploreQuery): Flow<ExplorePageSignal> =
        merge(
            retryTrigger.map { ExplorePageSignal.Retry },
            refreshTrigger.map { ExplorePageSignal.Refresh },
            loadMoreTrigger.map { ExplorePageSignal.More },
        ).onStart { emit(ExplorePageSignal.Initial) }
            .onEach { signal -> runPagingSession(query, signal) }

    private suspend fun runPagingSession(
        query: ExploreQuery,
        signal: ExplorePageSignal,
    ) {
        val current = pagedSubjects.value
        if (signal is ExplorePageSignal.More) {
            if (current.isLoading || current.isRefreshing || current.isLoadingMore || !current.hasMore) {
                return
            }
        }

        val isRefresh = signal is ExplorePageSignal.Refresh
        val isInitial = signal is ExplorePageSignal.Initial || signal is ExplorePageSignal.Retry
        val isMore = signal is ExplorePageSignal.More

        if (isRefresh) {
            hotComments.value = emptyMap()
        }

        pagedSubjects.value =
            current.copy(
                isLoading = isInitial,
                isRefreshing = isRefresh,
                isLoadingMore = isMore,
                error = if (isInitial || isRefresh) null else current.error,
            )

        val offset = if (isMore) current.pageOffset else 0
        val request = buildExploreRequest(query)

        when (val result = searchRepository.searchSubjectsAdvanced(request = request, limit = PAGE_SIZE, offset = offset)) {
            is AppResult.Success -> {
                val newSubjects = result.data.list
                val updatedSubjects =
                    if (isMore) {
                        val existingIds = current.subjects.map { s -> s.id }.toSet()
                        val uniqueNew = newSubjects.filter { s -> s.id !in existingIds }
                        current.subjects + uniqueNew
                    } else {
                        newSubjects
                    }
                val newOffset = offset + newSubjects.size
                val hasMore = newSubjects.size >= PAGE_SIZE
                pagedSubjects.value =
                    pagedSubjects.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        subjects = updatedSubjects,
                        pageOffset = newOffset,
                        hasMore = hasMore,
                        error = null,
                    )
                if (!isMore) {
                    fetchHotCommentForFirstSubject(newSubjects.firstOrNull())
                }
            }

            is AppResult.Error -> {
                pagedSubjects.value =
                    pagedSubjects.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        error = if (isMore) current.error else result.message,
                    )
                if (isMore) {
                    userMessage.value = "加载更多失败：${result.message}"
                }
            }

            is AppResult.Loading -> Unit
        }
    }

    private suspend fun fetchHotCommentForFirstSubject(firstSubject: Subject?) {
        val repo = communityRepository ?: return
        val subject = firstSubject ?: return
        if (hotComments.value.containsKey(subject.id)) return
        val result = repo.getSubjectComments(subject.id, limit = 5)
        if (result is AppResult.Success) {
            val best = selectBestComment(result.data.data)
            if (best != null) {
                hotComments.value = hotComments.value + (subject.id to best)
            }
        }
    }

    private fun selectBestComment(comments: List<SubjectComment>): SubjectComment? =
        comments.firstOrNull { it.comment.isNotBlank() && it.comment.length in 8..120 }
            ?: comments.firstOrNull { it.comment.isNotBlank() }

    private fun buildExploreRequest(query: ExploreQuery): SearchSubjectsRequest {
        val rankFilter =
            if (query.sort in setOf(ExploreSort.RANK, ExploreSort.SCORE) ||
                query.mood == ExploreMood.MASTERPIECE
            ) {
                listOf(">0")
            } else {
                null
            }
        val filter =
            SearchFilter(
                type = query.category.type?.let { listOf(it) },
                tag = query.tags.toList().ifEmpty { null },
                airDate = query.season.airDateFilter,
                rank = rankFilter,
            )
        return SearchSubjectsRequest(
            sort = query.sort.sortKey,
            filter = filter,
        )
    }

    private inline fun setQuery(transform: (ExploreQuery) -> ExploreQuery) {
        val next = transform(exploreQuery.value)
        if (next != exploreQuery.value) {
            exploreQuery.value = next
        }
    }

    fun onMoodSelect(mood: ExploreMood) {
        if (exploreQuery.value.mood == mood) return
        setQuery {
            it.copy(
                mood = mood,
                tags = mood.tags.toSet(),
                sort = mood.sort,
                season = ALL_TIME_SEASON,
            )
        }
    }

    fun onSeasonSelect(season: SeasonOption) {
        if (exploreQuery.value.season == season) return
        setQuery { it.copy(season = season, mood = null) }
    }

    fun onCategorySelect(category: ExploreCategory) {
        if (exploreQuery.value.category == category) return
        setQuery { it.copy(category = category, mood = null) }
    }

    fun onTagToggle(tag: String) {
        setQuery {
            val newTags = if (tag in it.tags) it.tags - tag else it.tags + tag
            it.copy(tags = newTags, mood = null)
        }
    }

    fun onClearAllTags() {
        if (exploreQuery.value.tags.isEmpty()) return
        setQuery { it.copy(tags = emptySet(), mood = null) }
    }

    fun onCustomTagSubmit(customTag: String) {
        val trimmed = customTag.trim()
        if (trimmed.isBlank()) return
        setQuery {
            it.copy(tags = it.tags + trimmed, mood = null)
        }
    }

    fun onSortSelect(sort: ExploreSort) {
        if (exploreQuery.value.sort == sort) return
        setQuery { it.copy(sort = sort, mood = null) }
    }

    fun toggleWish(subjectId: Long) {
        if (!uiState.value.isLoggedIn) {
            loginPromptVisible.value = true
            return
        }

        val isWished = uiState.value.wishedSubjectIds.contains(subjectId)
        if (isWished) {
            userMessage.value = "该番剧已在您的追番列表中"
            return
        }

        viewModelScope.launch {
            val result =
                withContext(NonCancellable) {
                    collectionRepository.updateCollectionStatus(subjectId, CollectionType.WISH)
                }
            when (result) {
                is AppResult.Success -> {
                    userMessage.value = "已加入「想看」列表"
                }

                is AppResult.Error -> {
                    userMessage.value = result.message.ifBlank { "添加收藏失败，请先确认登录状态" }
                }

                is AppResult.Loading -> Unit
            }
        }
    }

    suspend fun beginLogin(): String {
        loginPromptVisible.value = false
        return authRepository.beginLogin()
    }

    fun dismissLoginPrompt() {
        loginPromptVisible.value = false
    }

    fun clearUserMessage() {
        userMessage.value = null
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
}
