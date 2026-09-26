package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.UserSettings
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.AiConfigProfile
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
    var setAmoledDarkModeCallCount: Int = 0
        private set
    var setPipEnabledCallCount: Int = 0
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

    override suspend fun setAmoledDarkMode(enabled: Boolean) {
        setAmoledDarkModeCallCount++
        settingsState.value = settingsState.value.copy(amoledDarkMode = enabled)
    }

    override suspend fun setPipEnabled(enabled: Boolean) {
        setPipEnabledCallCount++
        settingsState.value = settingsState.value.copy(pipEnabled = enabled)
    }

    override suspend fun setAiConfig(config: AiConfig) {
        setAiConfigCallCount++
        settingsState.value = settingsState.value.copy(aiConfig = config)
    }

    private val aiProfilesState = MutableStateFlow<List<AiConfigProfile>>(emptyList())
    private val activeAiProfileIdState = MutableStateFlow("")

    override val aiConfigProfiles: Flow<List<AiConfigProfile>> = aiProfilesState

    override val activeAiProfileId: Flow<String> = activeAiProfileIdState

    override suspend fun saveAiConfigProfile(profile: AiConfigProfile) {
        val current = aiProfilesState.value.filterNot { it.id == profile.id }
        aiProfilesState.value = current + profile
    }

    override suspend fun activateAiConfigProfile(profileId: String) {
        val profile = aiProfilesState.value.firstOrNull { it.id == profileId } ?: return
        settingsState.value = settingsState.value.copy(aiConfig = profile.config)
        activeAiProfileIdState.value = profileId
    }

    override suspend fun deleteAiConfigProfile(profileId: String) {
        aiProfilesState.value = aiProfilesState.value.filterNot { it.id == profileId }
        if (activeAiProfileIdState.value == profileId) {
            activeAiProfileIdState.value = ""
        }
    }

    /** 直接注入 AI 配置方案池（供 ViewModel 测试构造场景） */
    fun setAiProfiles(profiles: List<AiConfigProfile>) {
        aiProfilesState.value = profiles
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

    private val playbackPositionsState = MutableStateFlow<Map<String, Long>>(emptyMap())
    override val playbackPositions: Flow<Map<String, Long>> = playbackPositionsState

    override suspend fun savePlaybackPosition(
        url: String,
        positionMs: Long,
    ) {
        if (url.isBlank() || positionMs <= 0L) return
        playbackPositionsState.value = playbackPositionsState.value + (url to positionMs)
    }

    override suspend fun clearPlaybackPosition(url: String) {
        playbackPositionsState.value = playbackPositionsState.value - url
    }

    /** 直接注入续播位置（供 ViewModel 测试构造恢复场景） */
    fun setPlaybackPositions(positions: Map<String, Long>) {
        playbackPositionsState.value = positions
    }

    private val lastPlaybackSourceIdState = MutableStateFlow("")
    override val lastPlaybackSourceId: Flow<String> = lastPlaybackSourceIdState

    override suspend fun setLastPlaybackSourceId(id: String) {
        lastPlaybackSourceIdState.value = id
    }

    var validationReportResult:
        com.infinitezerone.minibgm.core.common.AppResult<com.infinitezerone.minibgm.core.model.SubscriptionValidationReport> =
        com.infinitezerone.minibgm.core.common.AppResult.Success(
            com.infinitezerone.minibgm.core.model.SubscriptionValidationReport(
                isHealthy = true,
                subscriptionUrl = "",
            ),
        )

    override suspend fun validateAndTestSubscription(
        url: String,
    ): com.infinitezerone.minibgm.core.common.AppResult<com.infinitezerone.minibgm.core.model.SubscriptionValidationReport> =
        validationReportResult
}
