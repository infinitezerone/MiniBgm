package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.UserSettings
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.SyncInterval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

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
}
