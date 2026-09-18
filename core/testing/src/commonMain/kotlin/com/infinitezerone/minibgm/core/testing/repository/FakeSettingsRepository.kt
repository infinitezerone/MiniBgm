package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.UserSettings
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.PlaybackPlaylistDocument
import com.infinitezerone.minibgm.core.model.PlaybackPlaylistSchema
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistImportSummary
import com.infinitezerone.minibgm.core.model.SyncInterval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class FakeSettingsRepository(
    initialSettings: UserSettings = UserSettings(),
) : SettingsRepository {
    private val settingsState = MutableStateFlow(initialSettings)

    var setSyncIntervalCallCount: Int = 0
        private set
    var setAiringReminderEnabledCallCount: Int = 0
        private set
    var setAiringReminderHourCallCount: Int = 0
        private set
    var setAiConfigCallCount: Int = 0
        private set

    fun setSettings(settings: UserSettings) {
        settingsState.value = settings
    }

    override val settings: Flow<UserSettings> = settingsState

    override val aiConfig: Flow<AiConfig> = settingsState.map { it.aiConfig }

    override suspend fun setSyncInterval(interval: SyncInterval) {
        setSyncIntervalCallCount++
        settingsState.value = settingsState.value.copy(syncInterval = interval)
    }

    override suspend fun setAiringReminderEnabled(enabled: Boolean) {
        setAiringReminderEnabledCallCount++
        settingsState.value = settingsState.value.copy(airingReminderEnabled = enabled)
    }

    override suspend fun setAiringReminderHour(hour: Int) {
        setAiringReminderHourCallCount++
        settingsState.value = settingsState.value.copy(airingReminderHour = hour)
    }

    override suspend fun setAiConfig(config: AiConfig) {
        setAiConfigCallCount++
        settingsState.value = settingsState.value.copy(aiConfig = config)
    }

    private val airDelayOffsetMinutesState = MutableStateFlow(0)
    override val airDelayOffsetMinutes: Flow<Int> = airDelayOffsetMinutesState

    override suspend fun setAirDelayOffsetMinutes(minutes: Int) {
        airDelayOffsetMinutesState.value = minutes
    }

    private val playbackRulesState = MutableStateFlow<List<com.infinitezerone.minibgm.core.model.PlaybackSourceRule>>(emptyList())
    override val playbackRules: Flow<List<com.infinitezerone.minibgm.core.model.PlaybackSourceRule>> = playbackRulesState

    override suspend fun addPlaybackRule(rule: com.infinitezerone.minibgm.core.model.PlaybackSourceRule) {
        playbackRulesState.value = playbackRulesState.value + rule
    }

    override suspend fun updatePlaybackRule(rule: com.infinitezerone.minibgm.core.model.PlaybackSourceRule) {
        playbackRulesState.value = playbackRulesState.value.map { if (it.id == rule.id) rule else it }
    }

    override suspend fun deletePlaybackRule(ruleId: String) {
        playbackRulesState.value = playbackRulesState.value.filterNot { it.id == ruleId }
    }

    override suspend fun togglePlaybackRule(
        ruleId: String,
        isEnabled: Boolean,
    ) {
        playbackRulesState.value = playbackRulesState.value.map { if (it.id == ruleId) it.copy(isEnabled = isEnabled) else it }
    }

    override suspend fun importPlaybackRules(rules: List<com.infinitezerone.minibgm.core.model.PlaybackSourceRule>) {
        val existingIds = playbackRulesState.value.map { it.id }.toSet()
        playbackRulesState.value = playbackRulesState.value + rules.filterNot { it.id in existingIds }
    }

    private val playlistsState = MutableStateFlow<List<PlaybackPlaylist>>(emptyList())
    override val playlists: Flow<List<PlaybackPlaylist>> = playlistsState

    /** 直接注入播放列表（跳过 JSON 校验，供 UI/ViewModel 测试使用） */
    fun setPlaylists(playlists: List<PlaybackPlaylist>) {
        playlistsState.value = playlists
    }

    override suspend fun importPlaylistsFromJson(jsonText: String): AppResult<PlaylistImportSummary> {
        val document =
            runCatching { Json.Default.decodeFromString<PlaybackPlaylistDocument>(jsonText) }
                .getOrElse {
                    return AppResult.Error(IllegalStateException("JSON 解析失败：${it.message}"))
                }
        val validation = PlaybackPlaylistSchema.validate(document)
        if (validation.validPlaylists.isEmpty()) {
            return AppResult.Error(IllegalStateException(validation.issues.joinToString("；")))
        }
        var added = 0
        var replaced = 0
        val merged = playlistsState.value.toMutableList()
        validation.validPlaylists.forEach { incoming ->
            val index = merged.indexOfFirst { it.id == incoming.id }
            if (index >= 0) {
                merged[index] = incoming
                replaced++
            } else {
                merged += incoming
                added++
            }
        }
        playlistsState.value = merged
        return AppResult.Success(PlaylistImportSummary(added, replaced, validation.issues))
    }

    override suspend fun deletePlaylist(playlistId: String) {
        playlistsState.value = playlistsState.value.filterNot { it.id == playlistId }
    }

    override suspend fun clearPlaylists() {
        playlistsState.value = emptyList()
    }
}
