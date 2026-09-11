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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TagSubjectsViewModel(
    val tag: String,
    initialType: Int = 0,
    private val searchRepository: SearchRepository,
    private val collectionRepository: CollectionRepository,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            TagSubjectsUiState(
                tag = tag,
                selectedType = initialType,
            ),
        )
    val uiState: StateFlow<TagSubjectsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    companion object {
        private const val PAGE_SIZE = 20
    }

    init {
        observeCollections()
        loadSubjects()
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

    fun onSelectType(type: Int) {
        if (_uiState.value.selectedType == type) return
        _uiState.update {
            it.copy(
                selectedType = type,
                subjects = emptyList(),
                isLoading = true,
                errorMessage = null,
                hasMore = true,
            )
        }
        loadSubjects()
    }

    fun onSelectSort(sort: SearchSort) {
        if (_uiState.value.selectedSort == sort) return
        _uiState.update {
            it.copy(
                selectedSort = sort,
                subjects = emptyList(),
                isLoading = true,
                errorMessage = null,
                hasMore = true,
            )
        }
        loadSubjects()
    }

    fun setViewMode(viewMode: SearchViewMode) {
        _uiState.update { it.copy(viewMode = viewMode) }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        loadSubjects(isRefresh = true)
    }

    fun loadMore() {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.isLoadingMore || !currentState.hasMore) return

        loadMoreJob?.cancel()
        loadMoreJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingMore = true) }
                try {
                    val offset = currentState.subjects.size
                    val request = buildRequest(currentState)
                    when (
                        val result =
                            searchRepository.searchSubjectsAdvanced(
                                request = request,
                                limit = PAGE_SIZE,
                                offset = offset,
                            )
                    ) {
                        is AppResult.Success -> {
                            val newItems = result.data
                            _uiState.update { state ->
                                val existingIds = state.subjects.map { it.id }.toSet()
                                val uniqueNew = newItems.filter { it.id !in existingIds }
                                state.copy(
                                    isLoadingMore = false,
                                    subjects = state.subjects + uniqueNew,
                                    hasMore = newItems.size >= PAGE_SIZE,
                                )
                            }
                        }
                        is AppResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    isLoadingMore = false,
                                    userMessage = result.message.ifBlank { "加载更多失败" },
                                )
                            }
                        }
                        AppResult.Loading -> Unit
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            userMessage = "加载更多失败",
                        )
                    }
                }
            }
    }

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
                _uiState.update {
                    it.copy(userMessage = "打卡失败：${syncResult.message.ifBlank { "网络异常" }}")
                }
            }
        }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        loadSubjects()
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun loadSubjects(isRefresh: Boolean = false) {
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                try {
                    val currentState = _uiState.value
                    val request = buildRequest(currentState)
                    when (
                        val result =
                            searchRepository.searchSubjectsAdvanced(
                                request = request,
                                limit = PAGE_SIZE,
                                offset = 0,
                            )
                    ) {
                        is AppResult.Success -> {
                            val items = result.data
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isRefreshing = false,
                                    subjects = items,
                                    hasMore = items.size >= PAGE_SIZE,
                                    errorMessage = null,
                                )
                            }
                        }
                        is AppResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isRefreshing = false,
                                    errorMessage = result.message.ifBlank { "获取标签条目失败" },
                                )
                            }
                        }
                        AppResult.Loading -> Unit
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = "网络连接异常，请重试",
                        )
                    }
                }
            }
    }

    private fun buildRequest(state: TagSubjectsUiState): SearchSubjectsRequest {
        val typeList = if (state.selectedType > 0) listOf(state.selectedType) else null
        return SearchSubjectsRequest(
            sort = state.selectedSort.serverSort,
            filter =
                SearchFilter(
                    tag = listOf(state.tag),
                    type = typeList,
                    nsfw = false,
                ),
        )
    }
}
