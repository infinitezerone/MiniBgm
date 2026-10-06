package com.infinitezerone.minibgm.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.crash.CrashLog
import com.infinitezerone.minibgm.core.data.crash.CrashLogRepository
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.TrackingFootprint
import com.infinitezerone.minibgm.core.data.repository.UpdateRepository
import com.infinitezerone.minibgm.core.data.repository.UserSettings
import com.infinitezerone.minibgm.core.data.util.SyncManager
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.AppUpdateInfo
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.model.ThemeMode
import com.infinitezerone.minibgm.core.model.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val trackingFootprint: TrackingFootprint? = null,
    val airingReminderEnabled: Boolean = true,
    val airingDailySummaryEnabled: Boolean = true,
    val airingPreAirEnabled: Boolean = true,
    val airingBingeFinaleEnabled: Boolean = true,
    val airingReminderHour: Int = 8,
    val notifyBeforeAirMinutes: Int = 15,
    val airingNotificationOffsetMinutes: Int = -15,
    val aiConfig: AiConfig = AiConfig(),
    val airDelayOffsetMinutes: Int = 0,
    val amoledDarkMode: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val pipEnabled: Boolean = true,
    val showRestrictedContent: Boolean = false,
    val isCheckingUpdate: Boolean = false,
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

/** 本地 UI 域切片：手动同步、收藏统计、追番足迹与刷新标记 */
private data class LocalSlice(
    val manualSyncing: Boolean,
    val collectionCounts: Map<CollectionType, Int>,
    val isCountsLoading: Boolean,
    val isRefreshing: Boolean,
    val trackingFootprint: TrackingFootprint?,
    val isCheckingUpdate: Boolean,
)

/** 收藏统计域投影的局部状态：counts 快照 + 拉取中标记（flatMapLatest 链的产出） */
private data class CollectionCountsState(
    val collectionCounts: Map<CollectionType, Int> = emptyMap(),
    val isCountsLoading: Boolean = false,
)

class UserViewModel(
    private val authRepository: AuthRepository,
    private val scheduleRepository: ScheduleRepository,
    private val collectionRepository: CollectionRepository,
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
    private val crashLogRepository: CrashLogRepository,
    private val updateRepository: UpdateRepository,
) : ViewModel() {
    private val isManualSyncing = MutableStateFlow(false)
    private val isRefreshingFlow = MutableStateFlow(false)
    private val isCheckingUpdateFlow = MutableStateFlow(false)

    /**
     * 用户意图：手动刷新代数。下拉刷新递增它来触发 counts 链换挡重拉（见
     * [collectionCountsState]），本类仅有的命令态输入之一；profile 驱动的自动重拉不经过它。
     */
    private val countsRefreshGeneration = MutableStateFlow(0)

    /**
     * 收藏统计投影：活跃档案（id + username）是换挡判据，经 `distinctUntilChanged` +
     * `flatMapLatest` 自动重拉——档案切换 / 登出（null 档案清空）/ 手动刷新（generation 递增）
     * 都会让旧链整条取消、加载态重置，**没有 lastLoadedUserId 之类的手动去重与 Job 判序**：
     * 「请求是否真的变了」由 distinctUntilChanged 判定，竞态全部交给 flatMapLatest。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val collectionCountsState: Flow<CollectionCountsState> =
        combine(
            authRepository.activeProfile,
            countsRefreshGeneration,
        ) { profile, generation -> profile to generation }
            .distinctUntilChanged { old, new ->
                val (oldProfile, oldGeneration) = old
                val (newProfile, newGeneration) = new
                oldGeneration == newGeneration &&
                    oldProfile?.id == newProfile?.id &&
                    oldProfile?.username == newProfile?.username
            }.flatMapLatest { (profile, generation) ->
                countsForProfile(profile, force = generation > 0)
            }

    /** 单个档案的 counts 一次性拉取流：loading → 结果 → done（isCountsLoading 并入本链） */
    private fun countsForProfile(
        profile: UserProfile?,
        force: Boolean,
    ): Flow<CollectionCountsState> =
        if (profile == null) {
            flowOf(CollectionCountsState())
        } else {
            flow {
                emit(CollectionCountsState(isCountsLoading = true))
                val username = profile.username.ifBlank { profile.id.toString() }
                if (username.isBlank() || username == "0") {
                    emit(CollectionCountsState())
                } else {
                    val state =
                        when (val res = collectionRepository.fetchCollectionCounts(username, force = force)) {
                            is AppResult.Success -> CollectionCountsState(collectionCounts = res.data)
                            else -> CollectionCountsState()
                        }
                    emit(state)
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

    private val localSlice: Flow<LocalSlice> =
        combine(
            isManualSyncing,
            collectionCountsState,
            isRefreshingFlow,
            collectionRepository.observeTrackingFootprint(),
            isCheckingUpdateFlow,
        ) { manualSyncing, countsState, isRefreshing, trackingFootprint, isCheckingUpdate ->
            LocalSlice(
                manualSyncing,
                countsState.collectionCounts,
                countsState.isCountsLoading,
                isRefreshing,
                trackingFootprint,
                isCheckingUpdate,
            )
        }

    val uiState: StateFlow<UserUiState> =
        combine(
            authSlice,
            syncSlice,
            localSlice,
        ) { auth, sync, local ->
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
                collectionCounts = local.collectionCounts,
                isCountsLoading = local.isCountsLoading,
                trackingFootprint = local.trackingFootprint,
                airingReminderEnabled = sync.settings.airingReminderEnabled,
                airingDailySummaryEnabled = sync.settings.airingDailySummaryEnabled,
                airingPreAirEnabled = sync.settings.airingPreAirEnabled,
                airingBingeFinaleEnabled = sync.settings.airingBingeFinaleEnabled,
                airingReminderHour = sync.settings.airingReminderHour,
                notifyBeforeAirMinutes = sync.settings.notifyBeforeAirMinutes,
                airingNotificationOffsetMinutes = sync.settings.airingNotificationOffsetMinutes,
                aiConfig = sync.settings.aiConfig,
                airDelayOffsetMinutes = sync.airDelayOffsetMinutes,
                amoledDarkMode = sync.settings.amoledDarkMode,
                themeMode = sync.settings.themeMode,
                dynamicColor = sync.settings.dynamicColor,
                pipEnabled = sync.settings.pipEnabled,
                showRestrictedContent = sync.settings.showRestrictedContent,
                isCheckingUpdate = local.isCheckingUpdate,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserUiState(isLoading = true))

    /** 检查客户端新版本 */
    suspend fun checkForUpdate(currentVersion: String): AppResult<AppUpdateInfo> {
        isCheckingUpdateFlow.value = true
        return try {
            updateRepository.checkForUpdate(currentVersion)
        } finally {
            isCheckingUpdateFlow.value = false
        }
    }

    /** 刷新个人中心：同步最新个人资料并触发收藏统计链换挡重拉，再全量同步追番收藏 */
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
                    // counts 不再手动拉取：递增刷新代数让 flatMapLatest 取消旧链、强制绕缓存重拉
                    countsRefreshGeneration.value += 1
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

    /** 囤番完结提醒子开关 */
    fun setAiringBingeFinaleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAiringBingeFinaleEnabled(enabled)
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

    /** 主题模式：跟随系统 / 强制亮色 / 强制深色（由 :app 宿主读取并传给 MiniBgmTheme） */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    /** Material You 动态取色开关 */
    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(enabled)
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

    /**
     * 使用个人访问令牌（Personal Access Token）直接登录。
     * 免去外部浏览器 OAuth 交互与域名阻断困扰，直连拉取个人资料并激活会话。
     */
    fun loginWithPersonalAccessToken(
        token: String,
        onResult: (success: Boolean, errorMessage: String?) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = authRepository.loginWithPersonalAccessToken(token)) {
                is AppResult.Success -> {
                    refresh()
                    onResult(true, null)
                }
                is AppResult.Error -> {
                    onResult(false, result.message)
                }
                AppResult.Loading -> Unit
            }
        }
    }

    /**
     * 读取最近一次崩溃日志；从未崩溃过返回 null。
     *
     * 按需读取而不是进页就加载：崩溃是低频事件，没必要每次打开设置页都碰一次磁盘。
     */
    suspend fun loadLatestCrashLog(): CrashLog? = crashLogRepository.latest()

    /** 清空全部崩溃日志 */
    suspend fun clearCrashLogs() = crashLogRepository.clear()

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
