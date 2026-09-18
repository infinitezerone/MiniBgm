package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.PlaybackPlaylistDocument
import com.infinitezerone.minibgm.core.model.PlaybackPlaylistSchema
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistImportSummary
import com.infinitezerone.minibgm.core.model.SyncInterval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val playbackRules: Flow<List<PlaybackSourceRule>>

    suspend fun setSyncInterval(interval: SyncInterval)

    /** 开播提醒总开关（通知权限的授予与否由 UI 层请求） */
    suspend fun setAiringReminderEnabled(enabled: Boolean)

    /** 每日提醒触发时刻（设备本地时间小时） */
    suspend fun setAiringReminderHour(hour: Int)

    /** 更新 AI 服务配置 */
    suspend fun setAiConfig(config: AiConfig)

    suspend fun setAirDelayOffsetMinutes(minutes: Int)

    suspend fun addPlaybackRule(rule: PlaybackSourceRule)

    suspend fun updatePlaybackRule(rule: PlaybackSourceRule)

    suspend fun deletePlaybackRule(ruleId: String)

    suspend fun togglePlaybackRule(
        ruleId: String,
        isEnabled: Boolean,
    )

    suspend fun importPlaybackRules(rules: List<PlaybackSourceRule>)

    /** 用户自备播放列表（解码失败按空处理；损坏的原始数据只读不写回） */
    val playlists: Flow<List<PlaybackPlaylist>>

    /**
     * 校验并导入播放列表 JSON（信封格式，见 [PlaybackPlaylistDocument]）。
     * 同 id 覆盖、新 id 追加；现有数据损坏时拒绝写入以免静默覆盖。
     */
    suspend fun importPlaylistsFromJson(jsonText: String): AppResult<PlaylistImportSummary>

    suspend fun deletePlaylist(playlistId: String)

    /** 重置全部播放列表（现有数据损坏时的恢复出口） */
    suspend fun clearPlaylists()
}

class SettingsRepositoryImpl(
    private val userPreferences: UserPreferencesDataSource,
) : SettingsRepository {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

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

    override val playbackRules: Flow<List<PlaybackSourceRule>> =
        userPreferences.userPreferences.map { prefs ->
            if (prefs.playbackRulesJson.isBlank()) {
                emptyList()
            } else {
                runCatching {
                    json.decodeFromString<List<PlaybackSourceRule>>(prefs.playbackRulesJson)
                }.getOrDefault(emptyList())
            }
        }

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

    override suspend fun addPlaybackRule(rule: PlaybackSourceRule) {
        val current = playbackRules.first()
        val updated = current + rule
        userPreferences.setPlaybackRulesJson(json.encodeToString(updated))
    }

    override suspend fun updatePlaybackRule(rule: PlaybackSourceRule) {
        val current = playbackRules.first()
        val updated = current.map { if (it.id == rule.id) rule else it }
        userPreferences.setPlaybackRulesJson(json.encodeToString(updated))
    }

    override suspend fun deletePlaybackRule(ruleId: String) {
        val current = playbackRules.first()
        val updated = current.filterNot { it.id == ruleId }
        userPreferences.setPlaybackRulesJson(json.encodeToString(updated))
    }

    override suspend fun togglePlaybackRule(
        ruleId: String,
        isEnabled: Boolean,
    ) {
        val current = playbackRules.first()
        val updated = current.map { if (it.id == ruleId) it.copy(isEnabled = isEnabled) else it }
        userPreferences.setPlaybackRulesJson(json.encodeToString(updated))
    }

    override suspend fun importPlaybackRules(rules: List<PlaybackSourceRule>) {
        val current = playbackRules.first()
        val existingIds = current.map { it.id }.toSet()
        val newRules = rules.filterNot { it.id in existingIds }
        val updated = current + newRules
        userPreferences.setPlaybackRulesJson(json.encodeToString(updated))
    }

    private val playlistsWriteMutex = Mutex()

    /**
     * 片单信封专用编解码：schemaVersion 带默认值，必须 encodeDefaults 才能落盘，
     * 否则重启后无法判断数据属于哪个模式版本。
     */
    private val playlistJson =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

    private fun decodePlaylistDocument(raw: String): PlaybackPlaylistDocument? =
        if (raw.isBlank()) {
            PlaybackPlaylistDocument(playlists = emptyList())
        } else {
            runCatching { playlistJson.decodeFromString<PlaybackPlaylistDocument>(raw) }.getOrNull()
        }

    override val playlists: Flow<List<PlaybackPlaylist>> =
        userPreferences.userPreferences.map { prefs ->
            decodePlaylistDocument(prefs.playlistsJson)?.playlists ?: emptyList()
        }

    override suspend fun importPlaylistsFromJson(jsonText: String): AppResult<PlaylistImportSummary> =
        playlistsWriteMutex.withLock {
            val raw = userPreferences.userPreferences.first().playlistsJson
            val existingDocument = decodePlaylistDocument(raw)
            if (existingDocument == null) {
                return@withLock AppResult.Error(
                    IllegalStateException("现有播放列表数据无法解析，为避免丢失未执行覆盖导入；可在片单管理中重置后重试。"),
                )
            }
            val document =
                runCatching { playlistJson.decodeFromString<PlaybackPlaylistDocument>(jsonText) }
                    .getOrElse {
                        return@withLock AppResult.Error(
                            IllegalStateException("JSON 解析失败：${it.message ?: "格式不合法"}（需为 {\"schemaVersion\":1,\"playlists\":[...]} 信封格式）"),
                        )
                    }
            val validation = PlaybackPlaylistSchema.validate(document)
            if (validation.validPlaylists.isEmpty()) {
                return@withLock AppResult.Error(
                    IllegalStateException(validation.issues.joinToString("；").ifBlank { "没有可导入的合法播放列表" }),
                )
            }

            var added = 0
            var replaced = 0
            val issues = validation.issues.toMutableList()
            val merged = existingDocument.playlists.toMutableList()
            validation.validPlaylists.forEach { incoming ->
                val index = merged.indexOfFirst { it.id == incoming.id }
                if (index >= 0) {
                    merged[index] = incoming
                    replaced++
                } else if (merged.size >= PlaybackPlaylistSchema.MAX_PLAYLISTS) {
                    issues += "播放列表已达 ${PlaybackPlaylistSchema.MAX_PLAYLISTS} 份上限，《${incoming.name}》未加入"
                } else {
                    merged += incoming
                    added++
                }
            }
            userPreferences.setPlaylistsJson(
                playlistJson.encodeToString(PlaybackPlaylistDocument(playlists = merged)),
            )
            AppResult.Success(PlaylistImportSummary(addedCount = added, replacedCount = replaced, issues = issues))
        }

    override suspend fun deletePlaylist(playlistId: String) {
        playlistsWriteMutex.withLock {
            val raw = userPreferences.userPreferences.first().playlistsJson
            val document = decodePlaylistDocument(raw) ?: return@withLock
            val updated = document.playlists.filterNot { it.id == playlistId }
            if (updated.size == document.playlists.size) return@withLock
            userPreferences.setPlaylistsJson(
                playlistJson.encodeToString(PlaybackPlaylistDocument(playlists = updated)),
            )
        }
    }

    override suspend fun clearPlaylists() {
        playlistsWriteMutex.withLock {
            userPreferences.setPlaylistsJson("")
        }
    }
}
