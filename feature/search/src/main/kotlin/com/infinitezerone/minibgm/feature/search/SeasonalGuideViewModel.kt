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
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val PAGE_SIZE = 50

/**
 * 季度新番导视 ViewModel
 */
class SeasonalGuideViewModel(
    private val searchRepository: SearchRepository,
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
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
        val offset = currentState.subjects.size
        val request = buildSearchRequest(currentState)

        loadMoreJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingMore = true) }
                when (val result = searchRepository.searchSubjectsAdvanced(request = request, limit = PAGE_SIZE, offset = offset)) {
                    is AppResult.Success -> {
                        val newSubjects = result.data
                        _uiState.update {
                            val existingIds = it.subjects.map { s -> s.id }.toSet()
                            val uniqueNew = newSubjects.filter { s -> s.id !in existingIds }
                            it.copy(
                                isLoadingMore = false,
                                subjects = it.subjects + uniqueNew,
                                hasMore = newSubjects.size >= PAGE_SIZE,
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
        val currentState = _uiState.value
        val request = buildSearchRequest(currentState)

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
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                subjects = result.data,
                                hasMore = result.data.size >= PAGE_SIZE,
                                error = null,
                            )
                        }
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
}
