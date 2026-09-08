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
    val errorByType: Map<CollectionType, String?> = emptyMap(),
    val updatingSubjectIds: Set<Long> = emptySet(),
) {
    /** 当前选中分类的收藏列表（严格隔离，保证切换分类时绝不串台显示上一个 Tab 的内容） */
    val collections: List<UserCollection>
        get() = collectionsByType[selectedType].orEmpty()

    /** 当前选中分类是否正在加载中 */
    val isCurrentTabLoading: Boolean
        get() = loadingTypes.contains(selectedType) || (isLoading && !collectionsByType.containsKey(selectedType))

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

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
            }
        }
        viewModelScope.launch {
            authRepository.activeProfile.collect { profile ->
                val previousProfile = _uiState.value.activeProfile
                _uiState.update { it.copy(activeProfile = profile) }
                if (profile != null && previousProfile != null && previousProfile.id != profile.id) {
                    loadJobs.values.forEach { it.cancel() }
                    loadJobs.clear()
                    _uiState.update { state ->
                        state.copy(
                            collectionsByType = emptyMap(),
                            loadingTypes = emptySet(),
                            errorByType = emptyMap(),
                            error = null,
                        )
                    }
                    loadCollectionsForType(_uiState.value.selectedType, isRefresh = false)
                }
            }
        }
    }

    fun setInitialType(type: CollectionType) {
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
                        _uiState.update { state ->
                            val newLoading = state.loadingTypes - type
                            state.copy(
                                collectionsByType = state.collectionsByType + (type to data),
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
