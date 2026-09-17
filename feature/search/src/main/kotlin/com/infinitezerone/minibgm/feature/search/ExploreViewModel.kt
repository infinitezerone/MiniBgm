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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 探索发现页面 ViewModel：
 * - 支持双模式切换 [onViewModeChange]（双列瀑布流 / 全屏沉浸流）；
 * - 支持心境/场景筛选 [onMoodSelect]（本季热门/高分神作/治愈/热血...）；
 * - 支持未登录拦截并弹窗引导登录 [toggleWish]、[beginLogin]、[dismissLoginPrompt]；
 * - 支持季度/年份/年代全维度时间切换 [onSeasonSelect]；
 * - 支持类型切换 [onCategorySelect]（动画/书籍/游戏/音乐/全部）；
 * - 支持预设与自定义标签筛选 [onTagSelect]、[onCustomTagSubmit]；
 * - 支持排序切换 [onSortSelect]（热门/评分/排名）；
 * - 支持下拉刷新 [refresh]、上拉无限分页加载更多 [loadMore] 与错误重试 [retry]；
 * - 异步为探索条目注入真实社区热评 [fetchHotCommentsForSubjects]。
 */
class ExploreViewModel(
    private val searchRepository: SearchRepository,
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
    private val communityRepository: CommunityRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var hotCommentsJob: Job? = null

    init {
        observeAuth()
        observeCollections()
        loadDiscovery()
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
            combine(
                collectionRepository.getCollectionsByTypeStream(CollectionType.WISH),
                collectionRepository.getCollectionsByTypeStream(CollectionType.DOING),
                collectionRepository.getCollectionsByTypeStream(CollectionType.COLLECT),
            ) { wish, doing, collect ->
                (wish + doing + collect).map { it.subjectId }.toSet()
            }.catch {
                // Ignore errors from collection stream
            }.collect { wishedIds ->
                _uiState.update { it.copy(wishedSubjectIds = wishedIds) }
            }
        }
    }

    fun onMoodSelect(mood: ExploreMood) {
        if (_uiState.value.selectedMood == mood) return
        _uiState.update {
            val season =
                when (mood) {
                    ExploreMood.TRENDING -> CURRENT_SEASON
                    else -> ALL_TIME_SEASON
                }
            it.copy(
                selectedMood = mood,
                selectedTags = mood.tags.toSet(),
                selectedSort = mood.sort,
                selectedSeason = season,
            )
        }
        loadDiscovery()
    }

    fun toggleWish(subjectId: Long) {
        if (!_uiState.value.isLoggedIn) {
            _uiState.update { it.copy(showLoginPromptDialog = true) }
            return
        }

        val isWished = _uiState.value.wishedSubjectIds.contains(subjectId)
        viewModelScope.launch {
            if (isWished) {
                _uiState.update { it.copy(userMessage = "该番剧已在您的追番列表中") }
            } else {
                _uiState.update {
                    it.copy(
                        wishedSubjectIds = it.wishedSubjectIds + subjectId,
                        userMessage = "已加入「想看」列表",
                    )
                }
                when (val result = collectionRepository.updateCollectionStatus(subjectId, CollectionType.WISH)) {
                    is AppResult.Success -> Unit
                    is AppResult.Error -> {
                        _uiState.update {
                            it.copy(
                                wishedSubjectIds = it.wishedSubjectIds - subjectId,
                                userMessage = result.message.ifBlank { "添加收藏失败，请先确认登录状态" },
                            )
                        }
                    }
                    is AppResult.Loading -> Unit
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

    fun onSeasonSelect(season: SeasonOption) {
        if (_uiState.value.selectedSeason == season) return
        _uiState.update { it.copy(selectedSeason = season, selectedMood = null) }
        loadDiscovery()
    }

    fun onCategorySelect(category: ExploreCategory) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category, selectedMood = null) }
        loadDiscovery()
    }

    fun onTagToggle(tag: String) {
        _uiState.update {
            val newTags =
                if (tag in it.selectedTags) {
                    it.selectedTags - tag
                } else {
                    it.selectedTags + tag
                }
            val season = if (newTags.isNotEmpty() && it.selectedSeason == CURRENT_SEASON) ALL_TIME_SEASON else it.selectedSeason
            it.copy(selectedTags = newTags, selectedSeason = season, selectedMood = null)
        }
        loadDiscovery()
    }

    fun onClearAllTags() {
        if (_uiState.value.selectedTags.isEmpty()) return
        _uiState.update { it.copy(selectedTags = emptySet(), selectedMood = null) }
        loadDiscovery()
    }

    fun onCustomTagSubmit(customTag: String) {
        val trimmed = customTag.trim()
        if (trimmed.isBlank()) return
        _uiState.update {
            val newTags = it.selectedTags + trimmed
            val season = if (it.selectedSeason == CURRENT_SEASON) ALL_TIME_SEASON else it.selectedSeason
            it.copy(selectedTags = newTags, selectedSeason = season, selectedMood = null)
        }
        loadDiscovery()
    }

    fun onSortSelect(sort: ExploreSort) {
        if (_uiState.value.selectedSort == sort) return
        _uiState.update { it.copy(selectedSort = sort, selectedMood = null) }
        loadDiscovery()
    }

    fun refresh() {
        loadDiscovery(isRefresh = true)
    }

    fun retry() {
        loadDiscovery()
    }

    fun loadMore() {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.isLoadingMore || currentState.isRefreshing || !currentState.hasMore) {
            return
        }

        loadMoreJob?.cancel()
        loadMoreJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingMore = true) }

                // 翻页 offset 必须用服务端游标 pageOffset 而非 subjects.size：
                // 去重可能丢弃部分返回条目，两者一旦错位就会跳过或重复返回数据
                val offset = currentState.pageOffset
                when (
                    val result =
                        searchRepository.searchSubjectsAdvanced(
                            request = buildExploreRequest(currentState),
                            limit = PAGE_SIZE,
                            offset = offset,
                        )
                ) {
                    is AppResult.Success -> {
                        val newSubjects = result.data
                        _uiState.update {
                            val existingIds = it.subjects.map { s -> s.id }.toSet()
                            val uniqueNew = newSubjects.filter { s -> s.id !in existingIds }
                            it.copy(
                                isLoadingMore = false,
                                subjects = it.subjects + uniqueNew,
                                hasMore = newSubjects.size >= PAGE_SIZE,
                                pageOffset = offset + newSubjects.size,
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

    /** 统一构建探索页搜索请求，避免首屏与翻页两份 filter 逻辑漂移 */
    private fun buildExploreRequest(state: ExploreUiState): SearchSubjectsRequest {
        val rankFilter =
            if (state.selectedSort in setOf(ExploreSort.RANK, ExploreSort.SCORE) ||
                state.selectedMood == ExploreMood.MASTERPIECE
            ) {
                listOf(">0")
            } else {
                null
            }
        val filter =
            SearchFilter(
                type = state.selectedCategory.type?.let { listOf(it) },
                tag = state.selectedTags.toList().ifEmpty { null },
                airDate = state.selectedSeason.airDateFilter,
                rank = rankFilter,
                nsfw = false,
            )
        return SearchSubjectsRequest(
            sort = state.selectedSort.sortKey,
            filter = filter,
        )
    }

    private fun loadDiscovery(isRefresh: Boolean = false) {
        fetchJob?.cancel()
        loadMoreJob?.cancel()
        hotCommentsJob?.cancel()
        val currentState = _uiState.value
        val request = buildExploreRequest(currentState)
        fetchJob =
            viewModelScope.launch {
                _uiState.update {
                    if (isRefresh) {
                        it.copy(isRefreshing = true, hotComments = emptyMap(), error = null)
                    } else {
                        it.copy(isLoading = true, hotComments = emptyMap(), error = null)
                    }
                }

                when (val result = searchRepository.searchSubjectsAdvanced(request = request, limit = PAGE_SIZE, offset = 0)) {
                    is AppResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                subjects = result.data,
                                hasMore = result.data.size >= PAGE_SIZE,
                                pageOffset = result.data.size,
                                error = null,
                            )
                        }
                        fetchHotCommentsForSubjects(result.data)
                    }

                    is AppResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = result.message,
                            )
                        }
                    }

                    is AppResult.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            }
    }

    /**
     * 异步为当前探索列表中的置顶焦点大卡（ExploreSpotlightCard）拉取真实社区热评；
     * 其余普通瀑布流卡片直接消费条目自带的剧情简介与特色同好标签，杜绝单次加载 8 次网络并发请求。
     */
    private fun fetchHotCommentsForSubjects(subjects: List<Subject>) {
        val repo = communityRepository ?: return
        val firstSubject = subjects.firstOrNull() ?: return

        hotCommentsJob?.cancel()
        hotCommentsJob =
            viewModelScope.launch {
                if (!_uiState.value.hotComments.containsKey(firstSubject.id)) {
                    val result = repo.getSubjectComments(firstSubject.id, limit = 5)
                    if (result is AppResult.Success) {
                        val best = selectBestComment(result.data.data)
                        if (best != null) {
                            _uiState.update {
                                it.copy(hotComments = it.hotComments + (firstSubject.id to best))
                            }
                        }
                    }
                }
            }
    }

    private fun selectBestComment(comments: List<SubjectComment>): SubjectComment? =
        comments.firstOrNull { it.comment.isNotBlank() && it.comment.length in 8..120 }
            ?: comments.firstOrNull { it.comment.isNotBlank() }

    companion object {
        private const val PAGE_SIZE = 20
    }
}
