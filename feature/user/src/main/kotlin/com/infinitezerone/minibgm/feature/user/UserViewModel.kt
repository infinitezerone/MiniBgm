package com.infinitezerone.minibgm.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import com.infinitezerone.minibgm.core.data.repository.RatingInsights
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.data.repository.TrackingFootprint
import com.infinitezerone.minibgm.core.data.repository.UserSettings
import com.infinitezerone.minibgm.core.data.util.SyncManager
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.model.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UserUiState(
    val isLoggedIn: Boolean = false,
    val activeProfile: UserProfile? = null,
    val savedAccounts: List<UserProfile> = emptyList(),
    val isLoading: Boolean = false,
    val isAuthenticating: Boolean = false,
    val isRefreshing: Boolean = false,
    val syncInterval: SyncInterval = SyncInterval.WEEKLY,
    val lastSyncTimestamp: Long = 0L,
    val isSyncing: Boolean = false,
    val collectionCounts: Map<CollectionType, Int> = emptyMap(),
    val isCountsLoading: Boolean = false,
    val ratingInsights: RatingInsights? = null,
    val isInsightsLoading: Boolean = false,
    val trackingFootprint: TrackingFootprint? = null,
    val subjectActivity: SubjectActivityState = SubjectActivityState(),
    val airingReminderEnabled: Boolean = true,
    val airingDailySummaryEnabled: Boolean = true,
    val airingPreAirEnabled: Boolean = true,
    val airingReminderHour: Int = 8,
    val notifyBeforeAirMinutes: Int = 15,
    val airingNotificationOffsetMinutes: Int = -15,
    val aiConfig: AiConfig = AiConfig(),
    val airDelayOffsetMinutes: Int = 0,
    val amoledDarkMode: Boolean = false,
    val pipEnabled: Boolean = true,
    val showRestrictedContent: Boolean = false,
)

/** 认证域切片：登录态、活跃账号、账号池与登录进行中标记 */
private data class AuthSlice(
    val isLoggedIn: Boolean,
    val activeProfile: UserProfile?,
    val savedAccounts: List<UserProfile>,
    val isAuthenticating: Boolean,
)

/** 同步域切片：播放源设置与后台同步状态 */
private data class SyncSlice(
    val settings: UserSettings,
    val workSyncing: Boolean,
    val airDelayOffsetMinutes: Int,
)

/**
 * 收藏派生统计切片：五大分类计数与评分洞察。
 * 二者同属「远端聚合 + 内存 TTL 缓存」，先归为一组再做上层合并，
 * 以免 [LocalSlice] 的 combine 超出类型安全重载上限。
 */
private data class CollectionStatsSlice(
    val collectionCounts: Map<CollectionType, Int>,
    val isCountsLoading: Boolean,
    val ratingInsights: RatingInsights?,
    val isInsightsLoading: Boolean,
)

/** 本地 UI 域切片：手动同步、收藏派生统计、追番足迹与刷新标记 */
private data class LocalSlice(
    val manualSyncing: Boolean,
    val collectionStats: CollectionStatsSlice,
    val isRefreshing: Boolean,
    val trackingFootprint: TrackingFootprint?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class UserViewModel(
    private val authRepository: AuthRepository,
    private val scheduleRepository: ScheduleRepository,
    private val collectionRepository: CollectionRepository,
    private val communityRepository: CommunityRepository,
    private val subjectRepository: SubjectRepository,
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
) : ViewModel() {
    private val isManualSyncing = MutableStateFlow(false)
    private val isRefreshingFlow = MutableStateFlow(false)
    private val collectionCountsFlow = MutableStateFlow<Map<CollectionType, Int>>(emptyMap())
    private val isCountsLoadingFlow = MutableStateFlow(false)
    private val ratingInsightsFlow = MutableStateFlow<RatingInsights?>(null)
    private val isInsightsLoadingFlow = MutableStateFlow(false)
    private var lastLoadedUserId: Long? = null
    private var countsJob: Job? = null
    private var insightsJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.activeProfile
                .distinctUntilChanged { old, new -> old?.id == new?.id && old?.username == new?.username }
                .collect { profile ->
                    if (profile != null) {
                        if (lastLoadedUserId != profile.id || collectionCountsFlow.value.isEmpty()) {
                            lastLoadedUserId = profile.id
                            refreshCollectionCounts(profile, force = false)
                            refreshRatingInsights(profile, force = false)
                        }
                    } else {
                        lastLoadedUserId = null
                        collectionCountsFlow.value = emptyMap()
                        ratingInsightsFlow.value = null
                    }
                }
        }
    }

    /**
     * uiState 组装：combine 的类型安全重载最多 5 路，超出部分按域分组为
     * 中间切片（auth / sync / local）后再做一次 3 路合并，避免
     * Array<Any?> + unchecked cast。
     */
    private val authSlice: Flow<AuthSlice> =
        combine(
            authRepository.isLoggedIn,
            authRepository.activeProfile,
            authRepository.savedAccounts,
            authRepository.isAuthenticating,
        ) { isLoggedIn, activeProfile, savedAccounts, isAuthenticating ->
            AuthSlice(isLoggedIn, activeProfile, savedAccounts, isAuthenticating)
        }

    private val syncSlice: Flow<SyncSlice> =
        combine(
            settingsRepository.settings,
            syncManager.isSyncing,
            settingsRepository.airDelayOffsetMinutes,
        ) { settings, workSyncing, delayMinutes ->
            SyncSlice(settings, workSyncing, delayMinutes)
        }

    /** 在追动态参与方上限：取最近在追的前 N 部拉取最新讨论，控制网络扇出 */
    private companion object {
        const val ACTIVITY_SUBJECT_LIMIT = 3
        const val ACTIVITY_TOPIC_LIMIT = 1
    }

    /**
     * 在追动态流：在追（DOING）收藏变化（按条目集合去重）后，
     * 对前 N 部各取最新一条讨论。讨论拉取失败静默降级为缺失该条（fail-open），
     * 全部失败即为空列表、卡片由 UI 隐藏。仅在 UI 订阅期间活跃（WhileSubscribed）。
     */
    private val subjectActivityFlow: Flow<SubjectActivityState> =
        collectionRepository
            .getCollectionsByTypeStream(CollectionType.DOING)
            .map { collections -> collections.take(ACTIVITY_SUBJECT_LIMIT).map { it.subjectId } }
            .distinctUntilChanged()
            .flatMapLatest { subjectIds ->
                if (subjectIds.isEmpty()) {
                    flowOf(SubjectActivityState())
                } else {
                    combine(
                        subjectIds.map { subjectId -> observeSubjectLatestTopic(subjectId) },
                    ) { items ->
                        SubjectActivityState(
                            items =
                                items
                                    .filterNotNull()
                                    .sortedByDescending(SubjectActivityItem::updatedAtMs),
                        )
                    }.onStart { emit(SubjectActivityState(isLoading = true)) }
                }
            }

    /** 单部在追番剧的最新讨论（含番名补全）；任一环节失败发 null */
    private fun observeSubjectLatestTopic(subjectId: Long): Flow<SubjectActivityItem?> =
        flow {
            val topicsResult = communityRepository.getSubjectTopics(subjectId, limit = ACTIVITY_TOPIC_LIMIT)
            val topic = (topicsResult as? AppResult.Success)?.data?.firstOrNull()
            if (topic == null) {
                emit(null)
            } else {
                // 番名经详情接口补全（同时写入 SubjectRepository 内存缓存，详情页可直接复用）
                val subjectResult = subjectRepository.fetchSubjectDetail(subjectId)
                val subject = (subjectResult as? AppResult.Success)?.data
                emit(
                    SubjectActivityItem(
                        subjectId = subjectId,
                        subjectName = subject?.nameCn?.ifBlank { subject.name } ?: subject?.name.orEmpty(),
                        topicId = topic.id,
                        topicTitle = topic.title,
                        replyCount = topic.replyCount,
                        updatedAtMs = maxOf(topic.updatedAt, topic.createdAt),
                    ),
                )
            }
        }

    private val collectionStatsSlice: Flow<CollectionStatsSlice> =
        combine(
            collectionCountsFlow,
            isCountsLoadingFlow,
            ratingInsightsFlow,
            isInsightsLoadingFlow,
        ) { collectionCounts, isCountsLoading, ratingInsights, isInsightsLoading ->
            CollectionStatsSlice(collectionCounts, isCountsLoading, ratingInsights, isInsightsLoading)
        }

    private val localSlice: Flow<LocalSlice> =
        combine(
            isManualSyncing,
            collectionStatsSlice,
            isRefreshingFlow,
            collectionRepository.observeTrackingFootprint(),
        ) { manualSyncing, collectionStats, isRefreshing, trackingFootprint ->
            LocalSlice(manualSyncing, collectionStats, isRefreshing, trackingFootprint)
        }

    val uiState: StateFlow<UserUiState> =
        combine(
            authSlice,
            syncSlice,
            localSlice,
            subjectActivityFlow,
        ) { auth, sync, local, subjectActivity ->
            UserUiState(
                isLoggedIn = auth.isLoggedIn,
                activeProfile = auth.activeProfile,
                savedAccounts = auth.savedAccounts,
                isLoading = false,
                isAuthenticating = auth.isAuthenticating,
                isRefreshing = local.isRefreshing,
                syncInterval = sync.settings.syncInterval,
                lastSyncTimestamp = sync.settings.bangumiDataLastSyncTimestamp,
                isSyncing = sync.workSyncing || local.manualSyncing,
                collectionCounts = local.collectionStats.collectionCounts,
                isCountsLoading = local.collectionStats.isCountsLoading,
                ratingInsights = local.collectionStats.ratingInsights,
                isInsightsLoading = local.collectionStats.isInsightsLoading,
                trackingFootprint = local.trackingFootprint,
                subjectActivity = subjectActivity,
                airingReminderEnabled = sync.settings.airingReminderEnabled,
                airingDailySummaryEnabled = sync.settings.airingDailySummaryEnabled,
                airingPreAirEnabled = sync.settings.airingPreAirEnabled,
                airingReminderHour = sync.settings.airingReminderHour,
                notifyBeforeAirMinutes = sync.settings.notifyBeforeAirMinutes,
                airingNotificationOffsetMinutes = sync.settings.airingNotificationOffsetMinutes,
                aiConfig = sync.settings.aiConfig,
                airDelayOffsetMinutes = sync.airDelayOffsetMinutes,
                amoledDarkMode = sync.settings.amoledDarkMode,
                pipEnabled = sync.settings.pipEnabled,
                showRestrictedContent = sync.settings.showRestrictedContent,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserUiState(isLoading = true))

    /** 刷新个人中心：同步最新个人资料与全量收藏统计 */
    fun refresh(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            isRefreshingFlow.value = true
            var success = true
            try {
                val loggedIn = authRepository.isLoggedIn.first()
                if (loggedIn) {
                    val profileRes = authRepository.refreshProfile()
                    if (profileRes is AppResult.Error) {
                        success = false
                    }
                    val currentProfile = (profileRes as? AppResult.Success)?.data ?: uiState.value.activeProfile
                    if (currentProfile != null) {
                        refreshCollectionCounts(currentProfile, force = true)
                        refreshRatingInsights(currentProfile, force = true)
                    }
                    // 追番收藏同步失败必须反映到刷新结果，否则 UI 会误报成功、用户停留在过期收藏数据上
                    if (collectionRepository.syncWatchingCollections() is AppResult.Error) {
                        success = false
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                success = false
            } finally {
                isRefreshingFlow.value = false
            }
            onComplete?.invoke(success)
        }
    }

    /** 刷新活跃用户的五大收藏分类条目总数（真实 Bangumi 远端统计汇总） */
    fun refreshCollectionCounts(
        profile: UserProfile? = null,
        force: Boolean = false,
    ) {
        val currentProfile = profile ?: uiState.value.activeProfile ?: return
        val username = currentProfile.username.ifBlank { currentProfile.id.toString() }
        if (username.isBlank() || username == "0") return

        if (!force && countsJob?.isActive == true) return

        countsJob?.cancel()
        countsJob =
            viewModelScope.launch {
                isCountsLoadingFlow.value = true
                try {
                    val res = collectionRepository.fetchCollectionCounts(username, force = force)
                    if (res is AppResult.Success) {
                        collectionCountsFlow.value = res.data
                    }
                } finally {
                    isCountsLoadingFlow.value = false
                }
            }
    }

    /**
     * 刷新活跃用户的评分洞察（「看过」条目的评分分布与均分）。
     * 首次拉取需分页请求（每页 50，最多 20 页）；命中仓储层 TTL 缓存时零请求。
     * 失败时保留上次结果、不回填错误——卡片由 UI 按数据有无自行隐藏（fail-open）。
     */
    fun refreshRatingInsights(
        profile: UserProfile? = null,
        force: Boolean = false,
    ) {
        val currentProfile = profile ?: uiState.value.activeProfile ?: return
        val username = currentProfile.username.ifBlank { currentProfile.id.toString() }
        if (username.isBlank() || username == "0") return

        if (!force && insightsJob?.isActive == true) return

        insightsJob?.cancel()
        insightsJob =
            viewModelScope.launch {
                isInsightsLoadingFlow.value = true
                try {
                    val res = collectionRepository.fetchRatingInsights(username, force = force)
                    if (res is AppResult.Success) {
                        ratingInsightsFlow.value = res.data
                    }
                } finally {
                    isInsightsLoadingFlow.value = false
                }
            }
    }

    fun setSyncInterval(interval: SyncInterval) {
        viewModelScope.launch {
            settingsRepository.setSyncInterval(interval)
        }
    }

    /** 开播提醒总开关（通知权限的授予与否由 UI 层请求） */
    fun setAiringReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAiringReminderEnabled(enabled)
        }
    }

    /** 每日更新汇总子开关 */
    fun setAiringDailySummaryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAiringDailySummaryEnabled(enabled)
        }
    }

    /** 单集开播即时提醒子开关 */
    fun setAiringPreAirEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAiringPreAirEnabled(enabled)
        }
    }

    /** 每日提醒触发时刻（设备本地时间小时） */
    fun setAiringReminderHour(hour: Int) {
        viewModelScope.launch {
            settingsRepository.setAiringReminderHour(hour)
        }
    }

    /** 单集临近提醒提前时间量（分钟） */
    fun setNotifyBeforeAirMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setNotifyBeforeAirMinutes(minutes)
        }
    }

    /** 单集提醒时机相对开播时刻的偏移量（分钟，负数提前，0准时，正数延后） */
    fun setAiringNotificationOffsetMinutes(offsetMinutes: Int) {
        viewModelScope.launch {
            settingsRepository.setAiringNotificationOffsetMinutes(offsetMinutes)
        }
    }

    /** 更新 AI 服务配置（服务商、端点、密钥与模型） */
    fun setAiConfig(config: AiConfig) {
        viewModelScope.launch {
            settingsRepository.setAiConfig(config)
        }
    }

    fun setAirDelayOffsetMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setAirDelayOffsetMinutes(minutes)
        }
    }

    /** AMOLED 纯黑模式开关（仅在深色模式下生效，由 :app 宿主读取并传给 MiniBgmTheme） */
    fun setAmoledDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAmoledDarkMode(enabled)
        }
    }

    /** 画中画（PiP）开关 */
    fun setPipEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPipEnabled(enabled)
        }
    }

    fun setShowRestrictedContent(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowRestrictedContent(enabled)
            scheduleRepository.refreshAllSchedules(force = true)
        }
    }

    fun syncBangumiDataNow(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            isManualSyncing.value = true
            var success = false
            try {
                val result = scheduleRepository.syncBangumiData()
                success = result is AppResult.Success
            } finally {
                isManualSyncing.value = false
                onComplete(success)
            }
        }
    }

    /** 开始 OAuth 授权流程，生成并返回授权 URL（由 UI 层通过系统浏览器/Custom Tabs 打开，保持 ViewModel 与 Android Context 零耦合） */
    suspend fun beginLogin(): String = authRepository.beginLogin()

    fun switchAccount(userId: Long) {
        viewModelScope.launch {
            authRepository.switchAccount(userId)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun logout(userId: Long) {
        viewModelScope.launch {
            authRepository.logout(userId)
        }
    }

    fun logoutAll() {
        viewModelScope.launch {
            authRepository.logoutAll()
        }
    }
}
