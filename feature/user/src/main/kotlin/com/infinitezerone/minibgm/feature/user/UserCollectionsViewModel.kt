package com.infinitezerone.minibgm.feature.user

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.common.onSuccess
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.model.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 条目类别筛选（全部、动画、书籍、游戏、音乐）
 */
enum class CollectionSubjectFilter(
    val typeId: Int,
    val label: String,
) {
    ALL(0, "全部"),
    ANIME(2, "动画"),
    BOOK(1, "书籍"),
    GAME(4, "游戏"),
    MUSIC(3, "音乐"),
}

/** 用户收藏列表 UI 状态 */
@Immutable
data class UserCollectionsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val activeProfile: UserProfile? = null,
    val selectedType: CollectionType = CollectionType.DOING,
    val selectedSubjectFilter: CollectionSubjectFilter = CollectionSubjectFilter.ALL,
    val collectionsByType: Map<CollectionType, List<UserCollection>> = emptyMap(),
    val loadingTypes: Set<CollectionType> = emptySet(),
    val loadingMoreTypes: Set<CollectionType> = emptySet(),
    val hasMoreByType: Map<CollectionType, Boolean> = emptyMap(),
    val errorByType: Map<CollectionType, String?> = emptyMap(),
    val updatingSubjectIds: Set<Long> = emptySet(),
) {
    /** 当前选中分类的收藏列表（严格隔离，保证切换分类时绝不串台显示上一个 Tab 的内容） */
    val collections: List<UserCollection>
        get() = collectionsByType[selectedType].orEmpty()

    /** 当前选中分类是否正在加载中 */
    val isCurrentTabLoading: Boolean
        get() = loadingTypes.contains(selectedType) || (isLoading && !collectionsByType.containsKey(selectedType))

    /** 当前选中分类是否正在加载更多 */
    val isCurrentTabLoadingMore: Boolean
        get() = loadingMoreTypes.contains(selectedType)

    /** 当前选中分类是否还有更多数据可供加载 */
    val currentTabHasMore: Boolean
        get() = hasMoreByType[selectedType] ?: true

    /** 当前选中分类是否已经加载完成（哪怕内容为空也是已加载） */
    val isCurrentTabLoaded: Boolean
        get() = collectionsByType.containsKey(selectedType)

    /** 当前选中分类的专属错误信息 */
    val currentTabError: String?
        get() = errorByType[selectedType] ?: error
}

class UserCollectionsViewModel(
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UserCollectionsUiState())
    val uiState: StateFlow<UserCollectionsUiState> = _uiState.asStateFlow()

    private val loadJobs = mutableMapOf<CollectionType, Job>()
    private var isInitialized = false

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
            }
        }
        viewModelScope.launch {
            var initialProfileObserved = false
            authRepository.activeProfile.collect { profile ->
                val previousProfile = _uiState.value.activeProfile
                _uiState.update { it.copy(activeProfile = profile) }
                if (!initialProfileObserved) {
                    initialProfileObserved = true
                    return@collect
                }
                if (profile != null && (previousProfile == null || previousProfile.id != profile.id)) {
                    loadJobs.values.forEach { it.cancel() }
                    loadJobs.clear()
                    _uiState.update { state ->
                        state.copy(
                            collectionsByType = emptyMap(),
                            loadingTypes = emptySet(),
                            loadingMoreTypes = emptySet(),
                            hasMoreByType = emptyMap(),
                            errorByType = emptyMap(),
                            error = null,
                        )
                    }
                    loadCollectionsForType(_uiState.value.selectedType, isRefresh = false)
                } else if (profile == null && previousProfile != null) {
                    loadJobs.values.forEach { it.cancel() }
                    loadJobs.clear()
                    _uiState.update { state ->
                        state.copy(
                            collectionsByType = emptyMap(),
                            loadingTypes = emptySet(),
                            loadingMoreTypes = emptySet(),
                            hasMoreByType = emptyMap(),
                            errorByType = mapOf(state.selectedType to "请先登录 Bangumi 账号"),
                            error = "请先登录 Bangumi 账号",
                        )
                    }
                }
            }
        }
    }

    fun setInitialType(type: CollectionType) {
        if (isInitialized) return
        isInitialized = true
        _uiState.update { it.copy(selectedType = type) }
        loadCollectionsForType(type, isRefresh = false)
    }

    fun selectType(type: CollectionType) {
        if (_uiState.value.selectedType != type) {
            _uiState.update { it.copy(selectedType = type) }
            if (!_uiState.value.collectionsByType.containsKey(type)) {
                loadCollectionsForType(type, isRefresh = false)
            }
        }
    }

    fun selectSubjectFilter(filter: CollectionSubjectFilter) {
        if (_uiState.value.selectedSubjectFilter != filter) {
            loadJobs.values.forEach { it.cancel() }
            loadJobs.clear()
            _uiState.update { state ->
                state.copy(
                    selectedSubjectFilter = filter,
                    collectionsByType = emptyMap(),
                    loadingTypes = emptySet(),
                    loadingMoreTypes = emptySet(),
                    hasMoreByType = emptyMap(),
                    errorByType = emptyMap(),
                    error = null,
                )
            }
            loadCollectionsForType(_uiState.value.selectedType, isRefresh = false)
        }
    }

    fun refresh() {
        loadCollectionsForType(_uiState.value.selectedType, isRefresh = true)
    }

    private fun loadCollectionsForType(
        type: CollectionType,
        isRefresh: Boolean = false,
    ) {
        loadJobs[type]?.cancel()
        loadJobs[type] =
            viewModelScope.launch {
                if (isRefresh) {
                    _uiState.update { state ->
                        state.copy(
                            isRefreshing = true,
                            errorByType = state.errorByType - type,
                            error = null,
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            loadingTypes = state.loadingTypes + type,
                            isLoading = true,
                            errorByType = state.errorByType - type,
                            error = null,
                        )
                    }
                }

                val profile = _uiState.value.activeProfile ?: authRepository.activeProfile.first()
                if (profile == null) {
                    _uiState.update { state ->
                        state.copy(
                            loadingTypes = state.loadingTypes - type,
                            isLoading = false,
                            isRefreshing = false,
                            error = "请先登录 Bangumi 账号",
                            errorByType = state.errorByType + (type to "请先登录 Bangumi 账号"),
                        )
                    }
                    return@launch
                }

                val username =
                    profile.username
                        .ifBlank { profile.id.toString() }
                        .takeIf { it.isNotBlank() } ?: profile.id.toString()
                val subjectType = _uiState.value.selectedSubjectFilter.typeId

                collectionRepository
                    .fetchUserCollections(
                        username = username,
                        subjectType = subjectType,
                        type = type,
                        limit = 50,
                    ).onSuccess { data ->
                        val hasMore = data.size >= 50
                        _uiState.update { state ->
                            val newLoading = state.loadingTypes - type
                            state.copy(
                                collectionsByType = state.collectionsByType + (type to data),
                                hasMoreByType = state.hasMoreByType + (type to hasMore),
                                loadingTypes = newLoading,
                                errorByType = state.errorByType - type,
                                error = null,
                                isLoading = newLoading.isNotEmpty(),
                                isRefreshing = false,
                            )
                        }
                    }.onError { _, message ->
                        _uiState.update { state ->
                            val newLoading = state.loadingTypes - type
                            state.copy(
                                loadingTypes = newLoading,
                                errorByType = state.errorByType + (type to message),
                                error = message,
                                isLoading = newLoading.isNotEmpty(),
                                isRefreshing = false,
                            )
                        }
                    }
            }
    }

    /** 触底加载下一页收藏列表（增量追加并自动去重） */
    fun loadMore(type: CollectionType = _uiState.value.selectedType) {
        val currentState = _uiState.value
        if (currentState.loadingTypes.contains(type) || currentState.loadingMoreTypes.contains(type)) return
        if (currentState.hasMoreByType[type] == false) return

        val currentList = currentState.collectionsByType[type].orEmpty()
        if (currentList.isEmpty()) return

        _uiState.update { it.copy(loadingMoreTypes = it.loadingMoreTypes + type) }

        viewModelScope.launch {
            val profile = _uiState.value.activeProfile ?: authRepository.activeProfile.first()
            if (profile == null) {
                _uiState.update { it.copy(loadingMoreTypes = it.loadingMoreTypes - type) }
                return@launch
            }

            val username =
                profile.username
                    .ifBlank { profile.id.toString() }
                    .takeIf { it.isNotBlank() } ?: profile.id.toString()
            val subjectType = _uiState.value.selectedSubjectFilter.typeId
            val offset = currentList.size

            collectionRepository
                .fetchUserCollections(
                    username = username,
                    subjectType = subjectType,
                    type = type,
                    limit = 50,
                    offset = offset,
                ).onSuccess { data ->
                    _uiState.update { curState ->
                        val existing = curState.collectionsByType[type].orEmpty()
                        val existingIds = existing.map { it.subjectId }.toSet()
                        val uniqueNewData = data.filterNot { existingIds.contains(it.subjectId) }
                        val combined = existing + uniqueNewData
                        val hasMore = data.size >= 50
                        curState.copy(
                            collectionsByType = curState.collectionsByType + (type to combined),
                            hasMoreByType = curState.hasMoreByType + (type to hasMore),
                            loadingMoreTypes = curState.loadingMoreTypes - type,
                        )
                    }
                }.onError { _, _ ->
                    _uiState.update { curState ->
                        curState.copy(loadingMoreTypes = curState.loadingMoreTypes - type)
                    }
                }
        }
    }

    fun incrementEpisodeProgress(collection: UserCollection) {
        val nextEp = collection.epStatus + 1
        val total =
            collection.subject?.totalEpisodes?.takeIf { it > 0 }
                ?: collection.subject?.eps?.takeIf { it > 0 }
        if (total != null && nextEp > total) return

        val subjectId = collection.subjectId
        if (_uiState.value.updatingSubjectIds.contains(subjectId)) return
        val type = CollectionType.fromValue(collection.type)

        viewModelScope.launch {
            _uiState.update { state ->
                val currentList = state.collectionsByType[type]
                val updatedByType =
                    if (currentList != null) {
                        state.collectionsByType + (
                            type to
                                currentList.map { item ->
                                    if (item.subjectId == subjectId) item.copy(epStatus = nextEp) else item
                                }
                        )
                    } else {
                        state.collectionsByType
                    }
                state.copy(
                    updatingSubjectIds = state.updatingSubjectIds + subjectId,
                    collectionsByType = updatedByType,
                )
            }

            val result =
                if (collection.subjectType == 1) {
                    // 书籍类条目：通过 updateCollectionStatus 提交话数进度
                    collectionRepository.updateCollectionStatus(
                        subjectId = subjectId,
                        type = type,
                        rate = collection.rate.takeIf { it > 0 },
                        comment = collection.comment.ifBlank { null },
                        epStatus = nextEp,
                    )
                } else {
                    // 动画/剧集类条目：遵循 Bangumi 规范，通过 updateEpisodeStatus 单集打卡驱动完成度
                    collectionRepository.updateEpisodeStatus(
                        subjectId = subjectId,
                        episodeId = null,
                        isWatched = true,
                        epNumber = nextEp,
                    )
                }

            result.onError { _, message ->
                _uiState.update { state ->
                    val currentList = state.collectionsByType[type]
                    val rollbackByType =
                        if (currentList != null) {
                            state.collectionsByType + (
                                type to
                                    currentList.map { item ->
                                        if (item.subjectId == subjectId) item.copy(epStatus = collection.epStatus) else item
                                    }
                            )
                        } else {
                            state.collectionsByType
                        }
                    state.copy(
                        collectionsByType = rollbackByType,
                        error = message,
                    )
                }
            }

            _uiState.update { state ->
                state.copy(updatingSubjectIds = state.updatingSubjectIds - subjectId)
            }
        }
    }
}
