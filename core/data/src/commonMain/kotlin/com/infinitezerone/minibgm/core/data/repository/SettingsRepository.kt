package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.SyncInterval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 面向 UI 的用户设置投影：仅包含展示与行为偏好，不含登录态与账号数据
 * （后者属 [AuthRepository]）。feature 层经此仓库读写偏好，
 * 严禁直接依赖 :core:datastore。
 */
data class UserSettings(
    val syncInterval: SyncInterval = SyncInterval.WEEKLY,
    val bangumiDataLastSyncTimestamp: Long = 0L,
    val airingReminderEnabled: Boolean = true,
    val airingReminderHour: Int = 8,
    val aiConfig: AiConfig = AiConfig(),
)

interface SettingsRepository {
    val settings: Flow<UserSettings>
    val aiConfig: Flow<AiConfig>
    val airDelayOffsetMinutes: Flow<Int>

    suspend fun setSyncInterval(interval: SyncInterval)

    /** 开播提醒总开关（通知权限的授予与否由 UI 层请求） */
    suspend fun setAiringReminderEnabled(enabled: Boolean)

    /** 每日提醒触发时刻（设备本地时间小时） */
    suspend fun setAiringReminderHour(hour: Int)

    /** 更新 AI 服务配置 */
    suspend fun setAiConfig(config: AiConfig)

    suspend fun setAirDelayOffsetMinutes(minutes: Int)
}

class SettingsRepositoryImpl(
    private val userPreferences: UserPreferencesDataSource,
) : SettingsRepository {
    override val settings: Flow<UserSettings> =
        userPreferences.userPreferences.map { prefs ->
            UserSettings(
                syncInterval = prefs.syncInterval,
                bangumiDataLastSyncTimestamp = prefs.bangumiDataLastSyncTimestamp,
                airingReminderEnabled = prefs.airingReminderEnabled,
                airingReminderHour = prefs.airingReminderHour,
                aiConfig =
                    AiConfig(
                        endpoint = prefs.aiEndpoint,
                        apiKey = prefs.aiApiKey,
                        model = prefs.aiModel,
                        provider = prefs.aiProvider,
                    ),
            )
        }

    override val aiConfig: Flow<AiConfig> =
        userPreferences.userPreferences.map { prefs ->
            AiConfig(
                endpoint = prefs.aiEndpoint,
                apiKey = prefs.aiApiKey,
                model = prefs.aiModel,
                provider = prefs.aiProvider,
            )
        }

    override val airDelayOffsetMinutes: Flow<Int> =
        userPreferences.userPreferences.map { it.airDelayOffsetMinutes }

    override suspend fun setSyncInterval(interval: SyncInterval) {
        userPreferences.setSyncInterval(interval)
    }

    override suspend fun setAiringReminderEnabled(enabled: Boolean) {
        userPreferences.setAiringReminderEnabled(enabled)
    }

    override suspend fun setAiringReminderHour(hour: Int) {
        userPreferences.setAiringReminderHour(hour)
    }

    override suspend fun setAiConfig(config: AiConfig) {
        userPreferences.setAiConfig(
            endpoint = config.endpoint,
            apiKey = config.apiKey,
            model = config.model,
            provider = config.provider,
        )
    }

    override suspend fun setAirDelayOffsetMinutes(minutes: Int) {
        userPreferences.setAirDelayOffsetMinutes(minutes)
    }
}
