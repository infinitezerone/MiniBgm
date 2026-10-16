package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.RuleImportSummary
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.UserSettings
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.AiConfigProfile
import com.infinitezerone.minibgm.core.model.DanmakuConfig
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.model.ThemeMode
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
    var setDanmakuConfigCallCount: Int = 0
        private set
    var setAmoledDarkModeCallCount: Int = 0
        private set
    var setThemeModeCallCount: Int = 0
        private set
    var setDynamicColorCallCount: Int = 0
        private set
    var setPipEnabledCallCount: Int = 0
        private set
    var setDanmakuEnabledCallCount: Int = 0
        private set
    var setAutoNextEnabledCallCount: Int = 0
        private set
    var setShowRestrictedContentCallCount: Int = 0
        private set

    fun setSettings(settings: UserSettings) {
        settingsState.value = settings
    }

    override val settings: Flow<UserSettings> = settingsState

    override val aiConfig: Flow<AiConfig> = settingsState.map { it.aiConfig }

    override val danmakuConfig: Flow<DanmakuConfig> = settingsState.map { it.danmakuConfig }

    override suspend fun setDanmakuConfig(config: DanmakuConfig) {
        setDanmakuConfigCallCount++
        settingsState.value = settingsState.value.copy(danmakuConfig = config)
    }

    override suspend fun setSyncInterval(interval: SyncInterval) {
        setSyncIntervalCallCount++
        settingsState.value = settingsState.value.copy(syncInterval = interval)
    }

    override suspend fun setAiringReminderEnabled(enabled: Boolean) {
        setAiringReminderEnabledCallCount++
        settingsState.value = settingsState.value.copy(airingReminderEnabled = enabled)
    }

    override suspend fun setAiringDailySummaryEnabled(enabled: Boolean) {
        settingsState.value = settingsState.value.copy(airingDailySummaryEnabled = enabled)
    }

    override suspend fun setAiringPreAirEnabled(enabled: Boolean) {
        settingsState.value = settingsState.value.copy(airingPreAirEnabled = enabled)
    }

    override suspend fun setAiringBingeFinaleEnabled(enabled: Boolean) {
        settingsState.value = settingsState.value.copy(airingBingeFinaleEnabled = enabled)
    }

    override suspend fun setAiringReminderHour(hour: Int) {
        setAiringReminderHourCallCount++
        settingsState.value = settingsState.value.copy(airingReminderHour = hour)
    }

    override suspend fun setNotifyBeforeAirMinutes(minutes: Int) {
        settingsState.value = settingsState.value.copy(notifyBeforeAirMinutes = minutes)
    }

    override suspend fun setAiringNotificationOffsetMinutes(offsetMinutes: Int) {
        val clamped = offsetMinutes.coerceIn(-60, 120)
        settingsState.value =
            settingsState.value.copy(
                airingNotificationOffsetMinutes = clamped,
                notifyBeforeAirMinutes = if (clamped < 0) -clamped else 0,
                airDelayOffsetMinutes = if (clamped > 0) clamped else 0,
            )
    }

    override suspend fun setAmoledDarkMode(enabled: Boolean) {
        setAmoledDarkModeCallCount++
        settingsState.value = settingsState.value.copy(amoledDarkMode = enabled)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        setThemeModeCallCount++
        settingsState.value = settingsState.value.copy(themeMode = mode)
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        setDynamicColorCallCount++
        settingsState.value = settingsState.value.copy(dynamicColor = enabled)
    }

    override suspend fun setPipEnabled(enabled: Boolean) {
        setPipEnabledCallCount++
        settingsState.value = settingsState.value.copy(pipEnabled = enabled)
    }

    override suspend fun setDanmakuEnabled(enabled: Boolean) {
        setDanmakuEnabledCallCount++
        settingsState.value = settingsState.value.copy(danmakuEnabled = enabled)
    }

    override suspend fun setAutoNextEnabled(enabled: Boolean) {
        setAutoNextEnabledCallCount++
        settingsState.value = settingsState.value.copy(autoNextEnabled = enabled)
    }

    override suspend fun setPreferredQualityHeight(height: Int) {
        settingsState.value = settingsState.value.copy(preferredQualityHeight = height)
    }

    override suspend fun setShowRestrictedContent(enabled: Boolean) {
        setShowRestrictedContentCallCount++
        settingsState.value = settingsState.value.copy(showRestrictedContent = enabled)
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

    override val airDelayOffsetMinutes: Flow<Int> = settingsState.map { it.airDelayOffsetMinutes }

    override suspend fun setAirDelayOffsetMinutes(minutes: Int) {
        settingsState.value = settingsState.value.copy(airDelayOffsetMinutes = minutes)
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

    /** 直接注入播放规则（供 ViewModel 测试构造场景） */
    fun setPlaybackRules(rules: List<PlaybackSourceRule>) {
        playbackRulesState.value = rules
    }

    override suspend fun importRulesFromJson(jsonText: String): AppResult<RuleImportSummary> {
        // 与 SettingsRepository 真实实现同样的解析/校验语义（含拒收文案）：
        // 替身一旦和真实实现漂移，钉住替身行为的 ViewModel 测试就成了假绿。
        val importedList =
            runCatching {
                if (jsonText.startsWith("[")) {
                    Json.Default.decodeFromString<List<PlaybackSourceRule>>(jsonText)
                } else {
                    listOf(Json.Default.decodeFromString<PlaybackSourceRule>(jsonText))
                }
            }.getOrElse {
                return AppResult.Error(IllegalStateException("规则解析失败，请检查 JSON 格式"))
            }
        if (importedList.isEmpty()) return AppResult.Success(RuleImportSummary(acceptedCount = 0, rejectedCount = 0))
        val (accepted, rejected) = importedList.partition { it.isImportable }
        if (accepted.isNotEmpty()) importPlaybackRules(accepted)
        return AppResult.Success(RuleImportSummary(acceptedCount = accepted.size, rejectedCount = rejected.size))
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

    private val playbackSubscriptionUrlState = MutableStateFlow("")
    override val playbackSubscriptionUrl: Flow<String> = playbackSubscriptionUrlState

    override suspend fun setPlaybackSubscriptionUrl(url: String) {
        playbackSubscriptionUrlState.value = url.trim()
    }

    private val developerSettingsState =
        MutableStateFlow(
            com.infinitezerone.minibgm.core.model
                .DeveloperSettings(),
        )
    override val developerSettings: Flow<com.infinitezerone.minibgm.core.model.DeveloperSettings> = developerSettingsState

    override val isAiAssistantEnabled: Flow<Boolean> = developerSettingsState.map { it.isAiAssistantEffective }

    override val isInternalPlayerEnabled: Flow<Boolean> = developerSettingsState.map { it.isInternalPlayerEffective }

    override val featureCapabilities: Flow<com.infinitezerone.minibgm.core.model.FeatureCapabilities> =
        developerSettingsState.map {
            com.infinitezerone.minibgm.core.model.FeatureCapabilities(
                isAiAssistantEnabled = it.isAiAssistantEffective,
                isInternalPlayerEnabled = it.isInternalPlayerEffective,
            )
        }

    fun setDeveloperSettings(settings: com.infinitezerone.minibgm.core.model.DeveloperSettings) {
        developerSettingsState.value = settings
    }

    override suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        developerSettingsState.value = developerSettingsState.value.copy(isDeveloperModeEnabled = enabled)
    }

    override suspend fun setDevOverrideEnableAi(enabled: Boolean?) {
        developerSettingsState.value = developerSettingsState.value.copy(enableAiOverride = enabled)
    }

    override suspend fun setDevOverrideEnablePlayer(enabled: Boolean?) {
        developerSettingsState.value = developerSettingsState.value.copy(enablePlayerOverride = enabled)
    }

    override suspend fun setDevVerboseLogging(enabled: Boolean) {
        developerSettingsState.value = developerSettingsState.value.copy(verboseLoggingEnabled = enabled)
    }

    override suspend fun setDevSimulateTorrentUninstalled(enabled: Boolean) {
        developerSettingsState.value = developerSettingsState.value.copy(simulateTorrentUninstalled = enabled)
    }

    override suspend fun setDevCustomMirrorPrefix(prefix: String) {
        developerSettingsState.value = developerSettingsState.value.copy(customMirrorPrefixOverride = prefix.trim())
    }

    override suspend fun refreshPlaybackSubscription(): AppResult<RuleImportSummary> {
        val url = playbackSubscriptionUrlState.value
        if (url.isBlank()) return AppResult.Error(IllegalStateException("尚未配置订阅地址"))
        return when (val report = validateAndTestSubscription(url)) {
            is AppResult.Success -> {
                val sources = report.data.sources.filter { it.isAlive }
                val rules = sources.map { it.toPlaybackSourceRule("fake_${it.name.hashCode()}") }
                importPlaybackRules(rules)
                AppResult.Success(RuleImportSummary(acceptedCount = rules.size, rejectedCount = 0))
            }
            is AppResult.Error -> AppResult.Error(report.throwable, report.message)
            is AppResult.Loading -> AppResult.Error(IllegalStateException("校验未完成"))
        }
    }
}
