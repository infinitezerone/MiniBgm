package com.infinitezerone.minibgm.core.datastore

import androidx.datastore.core.DataStore
import com.infinitezerone.minibgm.core.common.UserDataClearable
import com.infinitezerone.minibgm.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * 设备侧偏好与账号资料池。会话事实（活跃用户、登录与否）由 AuthTokensDataSource
 * 承载——本类不写任何登录标记，从结构上杜绝「偏好标记已登录而凭据缺失」的假登录态。
 */
class UserPreferencesDataSource(
    private val dataStore: DataStore<UserPreferences>,
) : UserDataClearable {
    val userPreferences: Flow<UserPreferences> = dataStore.data

    /** 将资料存入账号池（展示与快捷切换用），不影响会话状态 */
    suspend fun saveUserProfile(profile: UserProfile) {
        dataStore.updateData { current ->
            current.copy(savedProfiles = current.savedProfiles + (profile.id to profile))
        }
    }

    /** 从账号池移除指定资料 */
    suspend fun removeAccount(userId: Long) {
        dataStore.updateData { current ->
            current.copy(savedProfiles = current.savedProfiles - userId)
        }
    }

    suspend fun setPendingOAuthVerifier(verifier: String) {
        dataStore.updateData { current ->
            current.copy(pendingOAuthVerifier = verifier)
        }
    }

    override suspend fun clearUserData(userId: Long) {
        removeAccount(userId)
    }

    override suspend fun clearAllUserData() {
        dataStore.updateData { current ->
            current.copy(
                savedProfiles = emptyMap(),
                pendingOAuthVerifier = "",
            )
        }
    }

    suspend fun setDarkMode(isDark: Boolean) {
        dataStore.updateData { current ->
            current.copy(isDarkMode = isDark)
        }
    }

    suspend fun setNotifyBeforeAirMinutes(minutes: Int) {
        dataStore.updateData { current ->
            current.copy(notifyBeforeAirMinutes = minutes)
        }
    }

    suspend fun setAiringReminderEnabled(enabled: Boolean) {
        dataStore.updateData { current ->
            current.copy(airingReminderEnabled = enabled)
        }
    }

    suspend fun setAiringReminderLastNotifiedDate(date: String) {
        dataStore.updateData { current ->
            current.copy(airingReminderLastNotifiedDate = date)
        }
    }

    /** 每日提醒触发时刻（设备本地时间小时，越界值收敛到 0-23） */
    suspend fun setAiringReminderHour(hour: Int) {
        dataStore.updateData { current ->
            current.copy(airingReminderHour = hour.coerceIn(0, 23))
        }
    }

    /** 逐集开播前提醒的去重键集合（上限 200 条，读取侧按日期裁剪） */
    suspend fun setAiringReminderNotifiedKeys(keys: List<String>) {
        dataStore.updateData { current ->
            current.copy(airingReminderNotifiedKeys = keys.take(200))
        }
    }

    suspend fun setBangumiDataEtag(etag: String) {
        dataStore.updateData { current ->
            current.copy(bangumiDataEtag = etag)
        }
    }

    suspend fun setSyncInterval(interval: com.infinitezerone.minibgm.core.model.SyncInterval) {
        dataStore.updateData { current ->
            current.copy(syncInterval = interval)
        }
    }

    suspend fun setBangumiDataLastSyncTimestamp(timestamp: Long) {
        dataStore.updateData { current ->
            current.copy(bangumiDataLastSyncTimestamp = timestamp)
        }
    }

    suspend fun setScheduleDefaultOnlyWatching(onlyWatching: Boolean) {
        dataStore.updateData { current ->
            current.copy(scheduleDefaultOnlyWatching = onlyWatching)
        }
    }

    /** 添加或更新搜索历史（去重置顶，最多保留 20 条） */
    suspend fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        dataStore.updateData { current ->
            val updated = listOf(trimmed) + (current.searchHistory - trimmed)
            current.copy(searchHistory = updated.take(20))
        }
    }

    /** 移除单条搜索历史 */
    suspend fun removeSearchHistory(query: String) {
        val trimmed = query.trim()
        dataStore.updateData { current ->
            current.copy(searchHistory = current.searchHistory - trimmed)
        }
    }

    /** 清空全部搜索历史 */
    suspend fun clearSearchHistory() {
        dataStore.updateData { current ->
            current.copy(searchHistory = emptyList())
        }
    }
}
