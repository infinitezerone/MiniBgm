package com.infinitezerone.minibgm.feature.user

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.common.onSuccess
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.model.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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

/** 远端收藏列表单页大小：与 `fetchUserCollections` 的 limit 语义一致，页宽决定 hasMore 判据 */
private const val COLLECTIONS_PAGE_SIZE = 50

private const val LOGIN_REQUIRED_MESSAGE = "请先登录 Bangumi 账号"

/**
 * 一次收藏列表网络请求的全部输入 —— **换挡的唯一判据**。
 *
 * 由「选中分区 × 条目类别筛选 × 当前账号」投影而来：三者任一真变即视为换了请求，
 * 旧加载链整条取消、新分区走 [CollectionsPagingSignal.Initial] 自动首取。
 * 条目类别筛选与账号兼任**分区缓存剪除**判据（见 [UserCollectionsViewModel.prunePartitions]），
 * 复刻旧实现「筛选/账号切换即清缓存重载」的语义。
 */
private data class RequestKey(
    val profileId: Long?,
    val subjectTypeId: Int,
    val type: CollectionType,
)

/** 分区缓存键：账号 × 条目类别筛选 × 收藏分区 */
private data class PartitionKey(
    val profileId: Long?,
    val subjectTypeId: Int,
    val type: CollectionType,
)

private fun RequestKey.toPartitionKey(): PartitionKey = PartitionKey(profileId, subjectTypeId, type)

/**
 * 单个分区的远端数据快照。
 *
 * [hasMore] 默认 `true`：与旧实现 `hasMoreByType[type] ?: true` 的缺省语义一致——
 * 经乐观编辑（就地迁分区）凭空创建的分区，在用户真正翻到触底前不拦截追加。
 */
private data class PartitionData(
    val collections: List<UserCollection> = emptyList(),
    val hasMore: Boolean = true,
    val error: String? = null,
)

/** 瞬态进度标记（首屏加载中 / 刷新中 / 追加中）、全局错误位与打卡进行中的条目集合 */
private data class PagingProgress(
    val loadingTypes: Set<CollectionType> = emptySet(),
    val loadingMoreTypes: Set<CollectionType> = emptySet(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val updatingSubjectIds: Set<Long> = emptySet(),
)

/** 分页取页信号：`Initial` 是进页 / 换挡后的自动首取，其余由用户意图触发 */
private sealed interface CollectionsPagingSignal {
    data object Initial : CollectionsPagingSignal

    data object Retry : CollectionsPagingSignal

    data object Refresh : CollectionsPagingSignal

    data object More : CollectionsPagingSignal
}

/** 重试 / 下拉刷新 / 触底加载更多的触发器；换挡后自动先收到一次 [CollectionsPagingSignal.Initial] */
private class CollectionsPagingTriggers {
    private val retryTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val loadMoreTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun signals(): Flow<CollectionsPagingSignal> =
        merge(
            retryTrigger.map { CollectionsPagingSignal.Retry },
            refreshTrigger.map { CollectionsPagingSignal.Refresh },
            loadMoreTrigger.map { CollectionsPagingSignal.More },
        ).onStart { emit(CollectionsPagingSignal.Initial) }

    fun retry() {
        retryTrigger.tryEmit(Unit)
    }

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    fun loadMore() {
        loadMoreTrigger.tryEmit(Unit)
    }
}

/** 数据侧切片：分区缓存 + 瞬态进度 + 三个选中意图（不含身份信息） */
private data class CollectionsDataSlice(
    val partitions: Map<PartitionKey, PartitionData>,
    val progress: PagingProgress,
    val selectedType: CollectionType,
    val selectedSubjectFilter: CollectionSubjectFilter,
    val selectedAirFilter: CollectionAirFilter,
)

/** 身份侧切片：登录态、当前账号与囤番集合（仓库流直接汇入） */
private data class CollectionsIdentitySlice(
    val isLoggedIn: Boolean,
    val activeProfile: UserProfile?,
    val bingeSubjectIds: Set<Long>,
)

/**
 * 用户收藏列表 ViewModel —— 响应式 UDF 投影（对标 `feature/search` 的 SeasonalGuideViewModel 规范）：
 *
 * 1. 对外只读 `uiState: StateFlow<…>`：状态 = `combine(意图流, 分区缓存流, 仓库流)` 的投影 + `stateIn`；
 * 2. 可变状态只剩**用户意图**（选中分区 / 条目类别筛选 / 连载筛选）与分区缓存、瞬态进度；
 *    分页取数用 `distinctUntilChangedBy(请求键) + flatMapLatest` 换挡——请求键
 *    [RequestKey]（账号 × 类别筛选 × 分区）任一真变时旧链整条取消、新分区自动首取，
 *    **不手动管理任何 fetchJob / loadMoreJob**（旧实现的 `loadJobs: MutableMap<CollectionType, Job>` 已删）；
 *    账号切换 / 登出由 `activeProfile` 汇入请求键：换挡自动取消旧链 + 剪除旧账号分区缓存 + 重载当前分区，
 *    未登录（profileId == null）时链上直接落「请先登录」错误位，不发请求；
 * 3. 收藏列表是**远端拉取**（无 Room 流可回读），因此 +1 打卡、撤销、就地编辑迁分区保留
 *    本地乐观更新与失败回滚——这是远端数据的 UI 即时反馈层（规范第 3 条的例外情形），
 *    仓库本身仍只写不读；
 * 4. 一次性事件（Snackbar / 撤销卡片）走 `Channel(BUFFERED) + receiveAsFlow()`，与状态流隔离；
 * 5. 分区缓存按 `PartitionKey` 存储、按当前「账号 × 类别筛选」投影成 `collectionsByType` 等
 *    对外字段——跨账号 / 跨筛选的残留分区天然不可见，等价于旧实现的整图清空。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserCollectionsViewModel(
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    // ── 输入：用户意图。这是本类仅有的"命令式"可变状态 ──
    private val selectedType = MutableStateFlow(CollectionType.DOING)
    private val selectedSubjectFilter = MutableStateFlow(CollectionSubjectFilter.ALL)
    private val selectedAirFilter = MutableStateFlow(CollectionAirFilter.ALL)

    /** 进页前的懒加载闸门：`setInitialType` 首次调用后才允许换挡链发请求（保持"进页才发 1 次请求"） */
    private val isInitialized = MutableStateFlow(false)

    /** 分区缓存：账号 × 类别筛选 × 分区 → 远端数据快照（含乐观更新就地改写的列表） */
    private val partitions = MutableStateFlow<Map<PartitionKey, PartitionData>>(emptyMap())

    /** 瞬态进度：首屏 / 刷新 / 追加标记与全局错误位；换挡时整体复位 */
    private val progress = MutableStateFlow(PagingProgress())

    /** 分页触发器（重试 / 刷新 / 触底追加） */
    private val pagingTriggers = CollectionsPagingTriggers()

    /** 当前换挡键：乐观更新据它定位分区；在链上 `onEach` 里同步维护 */
    private var currentRequestKey: RequestKey? = null

    private val _events = Channel<UserCollectionsEvent>(Channel.BUFFERED)

    /** 一次性事件流。UI 收集它来弹提示 / 展示撤销卡片；不需要也不应该有人回写它 */
    val events: Flow<UserCollectionsEvent> = _events.receiveAsFlow()

    /**
     * 对外只读投影：`combine` 出来的不可变快照。
     *
     * Eagerly 而非 WhileSubscribed：投影必须在无人订阅时也保持最新（进页即取数的原语义），
     * 否则单测读到的永远是初值；上游都是内存流，常驻收集的开销可忽略。
     */
    val uiState: StateFlow<UserCollectionsUiState> =
        combine(
            combine(
                partitions,
                progress,
                selectedType,
                selectedSubjectFilter,
                selectedAirFilter,
            ) { parts, prog, type, subjectFilter, airFilter ->
                CollectionsDataSlice(parts, prog, type, subjectFilter, airFilter)
            },
            combine(
                authRepository.isLoggedIn,
                authRepository.activeProfile,
                collectionRepository.getBingeSubjectIdsStream(),
            ) { loggedIn, profile, binge ->
                CollectionsIdentitySlice(loggedIn, profile, binge)
            },
        ) { data, identity ->
            val currentProfileId = identity.activeProfile?.id
            val currentSubjectTypeId = data.selectedSubjectFilter.typeId
            // 只投影当前「账号 × 类别筛选」下的分区：跨账号/跨筛选残留天然不可见
            val currentPartitions =
                data.partitions.filterKeys { it.profileId == currentProfileId && it.subjectTypeId == currentSubjectTypeId }
            UserCollectionsUiState(
                isLoading = data.progress.loadingTypes.isNotEmpty(),
                isRefreshing = data.progress.isRefreshing,
                error = data.progress.error,
                isLoggedIn = identity.isLoggedIn,
                activeProfile = identity.activeProfile,
                selectedType = data.selectedType,
                selectedSubjectFilter = data.selectedSubjectFilter,
                selectedAirFilter = data.selectedAirFilter,
                bingeSubjectIds = identity.bingeSubjectIds,
                collectionsByType = currentPartitions.mapKeys { it.key.type }.mapValues { it.value.collections },
                loadingTypes = data.progress.loadingTypes,
                loadingMoreTypes = data.progress.loadingMoreTypes,
                hasMoreByType = currentPartitions.mapKeys { it.key.type }.mapValues { it.value.hasMore },
                errorByType = currentPartitions.mapKeys { it.key.type }.mapValues { it.value.error },
                updatingSubjectIds = data.progress.updatingSubjectIds,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UserCollectionsUiState())

    init {
        combine(
            selectedType,
            selectedSubjectFilter,
            authRepository.activeProfile,
            isInitialized,
        ) { type, subjectFilter, profile, initialized ->
            if (!initialized) null else RequestKey(profile?.id, subjectFilter.typeId, type) to profile
        }
            // 换挡判据是"请求是否真的变了"：账号对象内容变化（如同名刷新）不重启链
            .distinctUntilChangedBy { it?.first }
            .onEach { latest ->
                // 换挡清场：瞬态进度复位（打卡进行中集合除外——那是独立命令，不随链取消）；
                // 剪除不属于当前「账号 × 类别筛选」的分区缓存，等价旧实现的"切换后清缓存"
                progress.value = PagingProgress(updatingSubjectIds = progress.value.updatingSubjectIds)
                currentRequestKey = latest?.first
                if (latest != null) prunePartitions(latest.first)
            }.flatMapLatest { latest ->
                if (latest == null) {
                    emptyFlow()
                } else {
                    val (key, profile) = latest
                    pagingTriggers.signals().onEach { signal -> runPagingSession(key, profile, signal) }
                }
            }.launchIn(viewModelScope)
    }

    fun setInitialType(type: CollectionType) {
        if (isInitialized.value) return
        // 先设分区再开门：换挡链读到的是同一个不可分意图，避免首屏出现两次请求
        selectedType.value = type
        isInitialized.value = true
    }

    fun selectType(type: CollectionType) {
        if (selectedType.value != type) selectedType.value = type
    }

    fun selectSubjectFilter(filter: CollectionSubjectFilter) {
        if (selectedSubjectFilter.value != filter) selectedSubjectFilter.value = filter
    }

    /** 切换连载/完结/囤番视图筛选；纯客户端补筛，不触发重新取数 */
    fun selectAirFilter(filter: CollectionAirFilter) {
        selectedAirFilter.value = filter
    }

    fun toggleBingeSubject(subjectId: Long) {
        viewModelScope.launch {
            collectionRepository.toggleBingeSubject(subjectId)
        }
    }

    fun refresh() {
        pagingTriggers.refresh()
    }

    /**
     * 加载失败后重试当前分区。
     *
     * 刻意走**非 refresh** 路径。失败时该分区没有任何内容，此刻正确的反馈是骨架屏
     * （"正在取数据"）；而 `refresh` 只置 `isRefreshing`，会让 `PullToRefreshBox` 转圈——
     * 那个转圈表达的是"内容还在、正在更新"，与"空着且刚失败"的事实不符。
     */
    fun retry() {
        pagingTriggers.retry()
    }

    /** 触底加载下一页收藏列表（增量追加并自动去重）。只服务当前换挡分区 */
    fun loadMore(type: CollectionType = selectedType.value) {
        val key = currentRequestKey
        if (key == null || key.type != type) return
        pagingTriggers.loadMore()
    }

    // ── 分页会话：由换挡链按信号调度，取消语义完全交给 flatMapLatest ──

    private suspend fun runPagingSession(
        key: RequestKey,
        profile: UserProfile?,
        signal: CollectionsPagingSignal,
    ) {
        when (signal) {
            CollectionsPagingSignal.Initial -> loadPartitionHead(key, profile, force = false)
            CollectionsPagingSignal.Retry -> loadPartitionHead(key, profile, force = true)
            CollectionsPagingSignal.Refresh -> refreshPartitionHead(key, profile)
            CollectionsPagingSignal.More -> loadNextPage(key, profile)
        }
    }

    /** 首屏 / 重试：[force] 为 false 时已成功加载的分区直接命中缓存，不发请求 */
    private suspend fun loadPartitionHead(
        key: RequestKey,
        profile: UserProfile?,
        force: Boolean,
    ) {
        if (profile == null) {
            progress.update { it.copy(error = LOGIN_REQUIRED_MESSAGE) }
            return
        }
        if (!force) {
            val cached = partitions.value[key.toPartitionKey()]
            if (cached != null && cached.error == null) return
        }
        progress.update { it.copy(loadingTypes = it.loadingTypes + key.type, error = null) }
        clearPartitionError(key)
        val result =
            collectionRepository.fetchUserCollections(
                username = usernameOf(profile),
                subjectType = key.subjectTypeId,
                type = key.type,
                limit = COLLECTIONS_PAGE_SIZE,
            )
        settlePartitionHead(key, result)
    }

    /** 下拉刷新：保留原列表非破坏性更新；首屏 / 刷新进行中则忽略（换挡已负责取消旧链） */
    private suspend fun refreshPartitionHead(
        key: RequestKey,
        profile: UserProfile?,
    ) {
        if (profile == null) {
            progress.update { it.copy(isRefreshing = false, error = LOGIN_REQUIRED_MESSAGE) }
            return
        }
        val current = progress.value
        if (current.isRefreshing || current.loadingTypes.isNotEmpty()) return
        progress.update { it.copy(isRefreshing = true, error = null) }
        clearPartitionError(key)
        val result =
            collectionRepository.fetchUserCollections(
                username = usernameOf(profile),
                subjectType = key.subjectTypeId,
                type = key.type,
                limit = COLLECTIONS_PAGE_SIZE,
            )
        settlePartitionHead(key, result)
    }

    /** 首屏 / 刷新收尾：整体替换分区数据（刷新失败则保留原列表只落错误位） */
    private suspend fun settlePartitionHead(
        key: RequestKey,
        result: AppResult<List<UserCollection>>,
    ) {
        result
            .onSuccess { data ->
                partitions.update { state ->
                    state + (
                        key.toPartitionKey() to
                            PartitionData(
                                collections = data,
                                hasMore = data.size >= COLLECTIONS_PAGE_SIZE,
                            )
                    )
                }
                progress.update {
                    it.copy(loadingTypes = it.loadingTypes - key.type, isRefreshing = false, error = null)
                }
            }.onError { _, message ->
                partitions.update { state ->
                    val existing = state[key.toPartitionKey()] ?: PartitionData()
                    state + (key.toPartitionKey() to existing.copy(error = message))
                }
                progress.update {
                    it.copy(loadingTypes = it.loadingTypes - key.type, isRefreshing = false, error = message)
                }
            }
    }

    /** 触底追加：守卫条件与旧实现逐一对应（加载中 / 追加中 / 取尽 / 空分区 / 未登录） */
    private suspend fun loadNextPage(
        key: RequestKey,
        profile: UserProfile?,
    ) {
        val partitionKey = key.toPartitionKey()
        val current = partitions.value[partitionKey]
        if (current == null || current.collections.isEmpty()) return
        if (!current.hasMore) return
        if (profile == null) return
        val prog = progress.value
        if (prog.loadingTypes.contains(key.type) || prog.loadingMoreTypes.contains(key.type)) return

        progress.update { it.copy(loadingMoreTypes = it.loadingMoreTypes + key.type) }
        val offset = current.collections.size
        collectionRepository
            .fetchUserCollections(
                username = usernameOf(profile),
                subjectType = key.subjectTypeId,
                type = key.type,
                limit = COLLECTIONS_PAGE_SIZE,
                offset = offset,
            ).onSuccess { data ->
                partitions.update { state ->
                    val existing = state[partitionKey] ?: return@update state
                    val knownIds = existing.collections.map { it.subjectId }.toSet()
                    val combined = existing.collections + data.filterNot { it.subjectId in knownIds }
                    state + (partitionKey to existing.copy(collections = combined, hasMore = data.size >= COLLECTIONS_PAGE_SIZE))
                }
                progress.update { it.copy(loadingMoreTypes = it.loadingMoreTypes - key.type) }
            }.onError { _, _ ->
                // 追加失败：保留原数据，只收起追加进度（与旧实现一致，不落全局错误位）
                progress.update { it.copy(loadingMoreTypes = it.loadingMoreTypes - key.type) }
            }
    }

    /** 剪除不属于当前「账号 × 类别筛选」的分区缓存：复刻旧实现"切换后清缓存重载"语义 */
    private fun prunePartitions(key: RequestKey) {
        partitions.update { map ->
            map.filterKeys { it.profileId == key.profileId && it.subjectTypeId == key.subjectTypeId }
        }
    }

    private fun clearPartitionError(key: RequestKey) {
        partitions.update { state ->
            val existing = state[key.toPartitionKey()] ?: return@update state
            state + (key.toPartitionKey() to existing.copy(error = null))
        }
    }

    private fun usernameOf(profile: UserProfile): String = profile.username.ifBlank { profile.id.toString() }

    // ── 乐观更新层 ──
    // 收藏列表是远端拉取数据，仓库没有本地流可在写入后回读——+1 打卡、撤销、就地编辑迁分区
    // 的即时反馈只能本地先改、失败回滚（响应式 UDF 规范第 3 条"写后读"的例外，刻意保留）。

    /** 就地改写某个已存在分区的列表；分区不存在则不动（与旧实现 `collectionsByType[type] == null` 守卫一致） */
    private inline fun mutatePartitionIfExists(
        type: CollectionType,
        transform: (List<UserCollection>) -> List<UserCollection>,
    ) {
        val key = currentRequestKey ?: return
        val partitionKey = key.toPartitionKey().copy(type = type)
        partitions.update { state ->
            val existing = state[partitionKey] ?: return@update state
            state + (partitionKey to existing.copy(collections = transform(existing.collections)))
        }
    }

    private fun applyOptimisticEpStatus(
        type: CollectionType,
        subjectId: Long,
        epStatus: Int,
    ) = mutatePartitionIfExists(type) { list ->
        list.map { item -> if (item.subjectId == subjectId) item.copy(epStatus = epStatus) else item }
    }

    fun incrementEpisodeProgress(collection: UserCollection) {
        val nextEp = collection.epStatus + 1
        val total =
            collection.subject?.totalEpisodes?.takeIf { it > 0 }
                ?: collection.subject?.eps?.takeIf { it > 0 }
        if (total != null && nextEp > total) return

        val subjectId = collection.subjectId
        if (progress.value.updatingSubjectIds.contains(subjectId)) return
        val type = CollectionType.fromValue(collection.type)

        viewModelScope.launch {
            progress.update { it.copy(updatingSubjectIds = it.updatingSubjectIds + subjectId) }
            // 乐观 +1：远端数据无流可回读，UI 即时反馈靠本地先改
            applyOptimisticEpStatus(type, subjectId, nextEp)

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
                    // 失败回滚到打卡前快照
                    applyOptimisticEpStatus(type, subjectId, collection.epStatus)
                    progress.update { it.copy(error = message) }
                    // 列表非空时 error 不可见（不打断内容），必须以事件形式显式反馈
                    _events.trySend(UserCollectionsEvent.ShowSnackbar("打卡失败：" + message.ifBlank { "网络异常" }))
                }

            progress.update { state ->
                state.copy(updatingSubjectIds = state.updatingSubjectIds - subjectId)
            }
        }
    }

    /** 撤销一次 +1 打卡：进度回退到快照值（本地乐观回退 + 网络回写，失败仅提示） */
    fun undoIncrement(previous: UserCollection) {
        viewModelScope.launch {
            val type = CollectionType.fromValue(previous.type)
            // 乐观回退：远端数据无流可回读，本地先退、网络回写
            applyOptimisticEpStatus(type, previous.subjectId, previous.epStatus)

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
        val updated =
            collection.copy(
                type = type.value,
                rate = rate ?: 0,
                comment = comment.orEmpty(),
                epStatus = epStatus ?: collection.epStatus,
                tags = tags.orEmpty(),
            )
        val key = currentRequestKey
        if (key == null) {
            // 理论不可达（UI 必然已 setInitialType）；兜底只走网络写
            submitCollectionUpdate(collection, type, rate, comment, private, epStatus, tags) { message ->
                _events.trySend(UserCollectionsEvent.ShowSnackbar("更新失败：" + message.ifBlank { "网络异常" }))
            }
            return
        }

        val oldType = CollectionType.fromValue(collection.type)
        val oldPartitionKey = key.toPartitionKey().copy(type = oldType)
        val newPartitionKey = key.toPartitionKey().copy(type = type)
        val previousOld = partitions.value[oldPartitionKey]
        val previousNew = partitions.value[newPartitionKey]

        partitions.update { state ->
            val oldList = state[oldPartitionKey]?.collections.orEmpty().filterNot { it.subjectId == collection.subjectId }
            val newList = state[newPartitionKey]?.collections.orEmpty().filterNot { it.subjectId == collection.subjectId } + updated
            state +
                (oldPartitionKey to (state[oldPartitionKey]?.copy(collections = oldList) ?: PartitionData(collections = oldList))) +
                (newPartitionKey to (state[newPartitionKey]?.copy(collections = newList) ?: PartitionData(collections = newList)))
        }
        progress.update { it.copy(error = null) }

        submitCollectionUpdate(collection, type, rate, comment, private, epStatus, tags) { message ->
            // 整图回滚：两个受影响分区精确恢复到编辑前快照（编辑前不存在的分区移除）
            partitions.update { state ->
                var restored = state
                restored =
                    if (previousOld != null) restored + (oldPartitionKey to previousOld) else restored - oldPartitionKey
                restored =
                    if (previousNew != null) restored + (newPartitionKey to previousNew) else restored - newPartitionKey
                restored
            }
            progress.update { it.copy(error = message) }
            _events.trySend(UserCollectionsEvent.ShowSnackbar("更新失败：" + message.ifBlank { "网络异常" }))
        }
    }

    private fun submitCollectionUpdate(
        collection: UserCollection,
        type: CollectionType,
        rate: Int?,
        comment: String?,
        private: Boolean,
        epStatus: Int?,
        tags: List<String>?,
        onError: (String) -> Unit,
    ) {
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
                    onError(message)
                }
        }
    }
}
