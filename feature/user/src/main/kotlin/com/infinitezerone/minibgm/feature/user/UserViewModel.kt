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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    val uiState: StateFlow<UserUiState> =
        combine(
            authRepository.isLoggedIn,
            authRepository.activeProfile,
            authRepository.savedAccounts,
            authRepository.isAuthenticating,
            settingsRepository.settings,
            syncManager.isSyncing,
            isManualSyncing,
            collectionCountsFlow,
            isCountsLoadingFlow,
            isRefreshingFlow,
        ) { args: Array<Any?> ->
            val isLoggedIn = args[0] as Boolean
            val activeProfile = args[1] as? UserProfile

            @Suppress("UNCHECKED_CAST")
            val savedAccounts = args[2] as List<UserProfile>
            val isAuthenticating = args[3] as Boolean
            val settings = args[4] as UserSettings
            val workSyncing = args[5] as Boolean
            val manualSyncing = args[6] as Boolean

            @Suppress("UNCHECKED_CAST")
            val collectionCounts = args[7] as Map<CollectionType, Int>
            val isCountsLoading = args[8] as Boolean
            val isRefreshing = args[9] as Boolean

            UserUiState(
                isLoggedIn = isLoggedIn,
                activeProfile = activeProfile,
                savedAccounts = savedAccounts,
                isAuthenticating = isAuthenticating,
                isRefreshing = isRefreshing,
                syncInterval = settings.syncInterval,
                lastSyncTimestamp = settings.bangumiDataLastSyncTimestamp,
                isSyncing = workSyncing || manualSyncing,
                collectionCounts = collectionCounts,
                isCountsLoading = isCountsLoading,
                airingReminderEnabled = settings.airingReminderEnabled,
                airingReminderHour = settings.airingReminderHour,
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
                }
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
            val results =
                CollectionType.entries
                    .map { type ->
                        async {
                            type to collectionRepository.fetchCollectionCount(username, type)
                        }
                    }.awaitAll()

            val newCounts = mutableMapOf<CollectionType, Int>()
            results.forEach { (type, res) ->
                if (res is AppResult.Success) {
                    newCounts[type] = res.data
                }
            }
            if (newCounts.isNotEmpty()) {
                collectionCountsFlow.value = newCounts
            }
            isCountsLoadingFlow.value = false
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
            val result = scheduleRepository.syncBangumiData(force = false)
            isManualSyncing.value = false
            onComplete(result is AppResult.Success)
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
