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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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
    val selectedAirFilter: CollectionAirFilter = CollectionAirFilter.ALL,
    val bingeSubjectIds: Set<Long> = emptySet(),
    val collectionsByType: Map<CollectionType, List<UserCollection>> = emptyMap(),
    val loadingTypes: Set<CollectionType> = emptySet(),
    val loadingMoreTypes: Set<CollectionType> = emptySet(),
    val hasMoreByType: Map<CollectionType, Boolean> = emptyMap(),
    val errorByType: Map<CollectionType, String?> = emptyMap(),
    val updatingSubjectIds: Set<Long> = emptySet(),
) {
    /** 当前选中分类的原始收藏列表 */
    val collections: List<UserCollection>
        get() = collectionsByType[selectedType].orEmpty()

    /** 经连载/完结/囤番多维过滤后的当前视图收藏列表 */
    val visibleCollections: List<UserCollection>
        get() {
            val list = collectionsByType[selectedType].orEmpty()
            return when (selectedAirFilter) {
                CollectionAirFilter.ALL -> list
                CollectionAirFilter.AIRING -> list.filter { !it.isFinished() }
                CollectionAirFilter.FINISHED -> list.filter { it.isFinished() }
                CollectionAirFilter.BINGE -> list.filter { !it.isFinished() && bingeSubjectIds.contains(it.subjectId) }
            }
        }

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

/** 收藏列表一次性单发事件 */
sealed interface UserCollectionsEvent {
    /** +1 打卡成功：携带打卡前快照，供「撤销」回写 */
    data class ProgressIncremented(
        val previous: UserCollection,
        val newEp: Int,
    ) : UserCollectionsEvent

    data class ShowSnackbar(
        val message: String,
    ) : UserCollectionsEvent
}

class UserCollectionsViewModel(
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UserCollectionsUiState())
    val uiState: StateFlow<UserCollectionsUiState> = _uiState.asStateFlow()

    private val _events = Channel<UserCollectionsEvent>(Channel.BUFFERED)
    val events: Flow<UserCollectionsEvent> = _events.receiveAsFlow()

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
        viewModelScope.launch {
            collectionRepository.getBingeSubjectIdsStream().collect { ids ->
                _uiState.update { it.copy(bingeSubjectIds = ids) }
            }
        }
    }

    fun setInitialType(type: CollectionType) {
        if (isInitialized) return
        isInitialized = true
        _uiState.update { it.copy(selectedType = type) }
        loadCollectionsForType(type, isRefresh = false)
    }

    fun selectAirFilter(filter: CollectionAirFilter) {
        _uiState.update { it.copy(selectedAirFilter = filter) }
    }

    fun toggleBingeSubject(subjectId: Long) {
        viewModelScope.launch {
            collectionRepository.toggleBingeSubject(subjectId)
        }
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

    /**
     * 加载失败后重试当前分区。
     *
     * 刻意走**非 refresh** 路径。失败时该分区没有任何内容，此刻正确的反馈是骨架屏
     * （"正在取数据"）；而 `refresh` 只置 `isRefreshing`，会让 `PullToRefreshBox` 转圈——
     * 那个转圈表达的是"内容还在、正在更新"，与"空着且刚失败"的事实不符。
     */
    fun retry() {
        loadCollectionsForType(_uiState.value.selectedType, isRefresh = false)
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

            result
                .onSuccess {
                    _events.trySend(UserCollectionsEvent.ProgressIncremented(previous = collection, newEp = nextEp))
                }.onError { _, message ->
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
                    // 列表非空时 error 不可见（不打断内容），必须以事件形式显式反馈
                    _events.trySend(UserCollectionsEvent.ShowSnackbar("打卡失败：" + message.ifBlank { "网络异常" }))
                }

            _uiState.update { state ->
                state.copy(updatingSubjectIds = state.updatingSubjectIds - subjectId)
            }
        }
    }

    /** 撤销一次 +1 打卡：进度回退到快照值（本地乐观回退 + 网络回写，失败仅提示） */
    fun undoIncrement(previous: UserCollection) {
        viewModelScope.launch {
            val type = CollectionType.fromValue(previous.type)
            _uiState.update { state ->
                val currentList = state.collectionsByType[type]
                val rolledBack =
                    if (currentList != null) {
                        state.collectionsByType + (
                            type to
                                currentList.map { item ->
                                    if (item.subjectId == previous.subjectId) item.copy(epStatus = previous.epStatus) else item
                                }
                        )
                    } else {
                        state.collectionsByType
                    }
                state.copy(collectionsByType = rolledBack)
            }

            val result =
                if (previous.subjectType == 1) {
                    collectionRepository.updateCollectionStatus(
                        subjectId = previous.subjectId,
                        type = type,
                        rate = previous.rate.takeIf { it > 0 },
                        comment = previous.comment.ifBlank { null },
                        epStatus = previous.epStatus,
                    )
                } else {
                    // 动画/剧集类：把刚标记的那一话（快照进度 +1）恢复为未看
                    collectionRepository.updateEpisodeStatus(
                        subjectId = previous.subjectId,
                        episodeId = null,
                        isWatched = false,
                        epNumber = previous.epStatus + 1,
                    )
                }
            result.onError { _, message ->
                _events.trySend(UserCollectionsEvent.ShowSnackbar("撤销失败：" + message.ifBlank { "网络异常" }))
            }
        }
    }

    /**
     * 就地编辑收藏（状态/评分/短评/进度/标签）：本地先把条目迁入目标分区并乐观更新，
     * 网络失败整体回滚到编辑前快照。
     */
    fun updateCollection(
        collection: UserCollection,
        type: CollectionType,
        rate: Int?,
        comment: String?,
        private: Boolean,
        epStatus: Int?,
        tags: List<String>?,
    ) {
        val oldType = CollectionType.fromValue(collection.type)
        val updated =
            collection.copy(
                type = type.value,
                rate = rate ?: 0,
                comment = comment.orEmpty(),
                epStatus = epStatus ?: collection.epStatus,
                tags = tags.orEmpty(),
            )
        val previousMap = _uiState.value.collectionsByType
        _uiState.update { state ->
            val mutable = state.collectionsByType.toMutableMap()
            val oldList = mutable[oldType].orEmpty().filterNot { it.subjectId == collection.subjectId }
            val newList = mutable[type].orEmpty().filterNot { it.subjectId == collection.subjectId } + updated
            mutable[oldType] = oldList
            mutable[type] = newList
            state.copy(collectionsByType = mutable, error = null)
        }

        viewModelScope.launch {
            collectionRepository
                .updateCollectionStatus(
                    subjectId = collection.subjectId,
                    type = type,
                    rate = rate,
                    comment = comment,
                    private = private,
                    epStatus = epStatus,
                    subjectType = collection.subjectType,
                    tags = tags,
                ).onSuccess {
                    _events.trySend(UserCollectionsEvent.ShowSnackbar("收藏已更新"))
                }.onError { _, message ->
                    _uiState.update { it.copy(collectionsByType = previousMap, error = message) }
                    _events.trySend(UserCollectionsEvent.ShowSnackbar("更新失败：" + message.ifBlank { "网络异常" }))
                }
        }
    }
}
