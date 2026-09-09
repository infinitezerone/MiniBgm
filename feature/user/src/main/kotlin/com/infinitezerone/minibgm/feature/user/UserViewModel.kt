package com.infinitezerone.minibgm.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.ScheduleRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.UserSettings
import com.infinitezerone.minibgm.core.data.util.SyncManager
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val airingReminderEnabled: Boolean = true,
    val airingReminderHour: Int = 8,
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
)

/** 本地 UI 域切片：手动同步、收藏统计与刷新标记 */
private data class LocalSlice(
    val manualSyncing: Boolean,
    val collectionCounts: Map<CollectionType, Int>,
    val isCountsLoading: Boolean,
    val isRefreshing: Boolean,
)

class UserViewModel(
    private val authRepository: AuthRepository,
    private val scheduleRepository: ScheduleRepository,
    private val collectionRepository: CollectionRepository,
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
) : ViewModel() {
    private val isManualSyncing = MutableStateFlow(false)
    private val isRefreshingFlow = MutableStateFlow(false)
    private val collectionCountsFlow = MutableStateFlow<Map<CollectionType, Int>>(emptyMap())
    private val isCountsLoadingFlow = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            authRepository.activeProfile.collect { profile ->
                if (profile != null) {
                    refreshCollectionCounts(profile)
                } else {
                    collectionCountsFlow.value = emptyMap()
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
        ) { settings, workSyncing ->
            SyncSlice(settings, workSyncing)
        }

    private val localSlice: Flow<LocalSlice> =
        combine(
            isManualSyncing,
            collectionCountsFlow,
            isCountsLoadingFlow,
            isRefreshingFlow,
        ) { manualSyncing, collectionCounts, isCountsLoading, isRefreshing ->
            LocalSlice(manualSyncing, collectionCounts, isCountsLoading, isRefreshing)
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
                isAuthenticating = auth.isAuthenticating,
                isRefreshing = local.isRefreshing,
                syncInterval = sync.settings.syncInterval,
                lastSyncTimestamp = sync.settings.bangumiDataLastSyncTimestamp,
                isSyncing = sync.workSyncing || local.manualSyncing,
                collectionCounts = local.collectionCounts,
                isCountsLoading = local.isCountsLoading,
                airingReminderEnabled = sync.settings.airingReminderEnabled,
                airingReminderHour = sync.settings.airingReminderHour,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserUiState())

    val isLoggedIn: StateFlow<Boolean> =
        authRepository.isLoggedIn
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
                        refreshCollectionCounts(currentProfile)
                    }
                    collectionRepository.syncWatchingCollections()
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

    /** 刷新活跃用户的五大收藏分类条目总数（真实 Bangumi 远端统计） */
    fun refreshCollectionCounts(profile: UserProfile? = null) {
        val currentProfile = profile ?: uiState.value.activeProfile ?: return
        val username = currentProfile.username.ifBlank { currentProfile.id.toString() }
        if (username.isBlank() || username == "0") return

        viewModelScope.launch {
            isCountsLoadingFlow.value = true
            try {
                val newCounts = mutableMapOf<CollectionType, Int>()
                // 逐个平滑请求 5 大分类计数，避免瞬间并发轰炸触发 429 限流；
                // 每次获取成功即时更新 Flow，UI 呈现递增动效，消除长时间白屏/转圈
                for (type in CollectionType.entries) {
                    val res = collectionRepository.fetchCollectionCount(username, type)
                    if (res is AppResult.Success) {
                        newCounts[type] = res.data
                        collectionCountsFlow.value = newCounts.toMap()
                    }
                }
            } finally {
                isCountsLoadingFlow.value = false
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

    /** 每日提醒触发时刻（设备本地时间小时） */
    fun setAiringReminderHour(hour: Int) {
        viewModelScope.launch {
            settingsRepository.setAiringReminderHour(hour)
        }
    }

    fun syncBangumiDataNow(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            isManualSyncing.value = true
            var success = false
            try {
                val result = scheduleRepository.syncBangumiData(force = false)
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
