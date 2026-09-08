package com.infinitezerone.minibgm.core.datastore

import androidx.datastore.core.DataStore
import com.infinitezerone.minibgm.core.common.UserDataClearable
import com.infinitezerone.minibgm.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

class UserPreferencesDataSource(
    private val dataStore: DataStore<UserPreferences>,
) : UserDataClearable {
    val userPreferences: Flow<UserPreferences> = dataStore.data

    /** token 已由 AuthTokensDataSource 落盘后调用，二者共同构成登录态 */
    suspend fun markLoggedIn(userId: Long = 0L) {
        dataStore.updateData { current ->
            current.copy(isLoggedIn = true, activeUserId = userId)
        }
    }

    /** 登录后或资料刷新时调用：将用户 Profile 存入账号池，并设为当前活跃账号 */
    suspend fun saveUserProfile(profile: UserProfile) {
        dataStore.updateData { current ->
            current.copy(
                activeUserId = profile.id,
                savedProfiles = current.savedProfiles + (profile.id to profile),
                isLoggedIn = true,
            )
        }
    }

    /** 切换当前活跃账号 */
    suspend fun switchAccount(userId: Long) {
        dataStore.updateData { current ->
            current.copy(activeUserId = userId, isLoggedIn = true)
        }
    }

    /** 移除/注销某个账号 */
    suspend fun removeAccount(userId: Long) {
        dataStore.updateData { current ->
            val updatedMap = current.savedProfiles - userId
            val newActiveId =
                if (current.activeUserId == userId) {
                    updatedMap.keys.firstOrNull() ?: 0L
                } else {
                    current.activeUserId
                }
            current.copy(
                activeUserId = newActiveId,
                savedProfiles = updatedMap,
                isLoggedIn = newActiveId != 0L,
            )
        }
    }

    suspend fun setPendingOAuthVerifier(verifier: String) {
        dataStore.updateData { current ->
            current.copy(pendingOAuthVerifier = verifier)
        }
    }

    suspend fun clearAuth() {
        dataStore.updateData { current ->
            UserPreferences(
                isDarkMode = current.isDarkMode,
                notifyBeforeAirMinutes = current.notifyBeforeAirMinutes,
            )
        }
    }

    override suspend fun clearUserData(userId: Long) {
        removeAccount(userId)
    }

    override suspend fun clearAllUserData() {
        clearAuth()
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
