package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.SecureSecretStore
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.AiConfigProfile
import com.infinitezerone.minibgm.core.model.DanmakuConfig
import com.infinitezerone.minibgm.core.model.DeveloperSettings
import com.infinitezerone.minibgm.core.model.FeatureCapabilities
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.model.ThemeMode
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString

/** 当前生效的 AI 密钥在加密库中的键；方案池密钥用 `ai.profile.<id>.apiKey` */
private const val AI_ACTIVE_KEY = "ai.active.apiKey"

/** 弹弹play 开放平台应用密钥在加密库中的键 */
private const val DANDANPLAY_SECRET_KEY = "danmaku.appSecret"

/**
 * 面向 UI 的用户设置投影：仅包含展示与行为偏好，不含登录态与账号数据
 * （后者属 [AuthRepository]）。feature 层经此仓库读写偏好，
 * 严禁直接依赖 :core:datastore。
 */
data class UserSettings(
    val syncInterval: SyncInterval = SyncInterval.WEEKLY,
    val bangumiDataLastSyncTimestamp: Long = 0L,
    val airingReminderEnabled: Boolean = true,
    val airingDailySummaryEnabled: Boolean = true,
    val airingPreAirEnabled: Boolean = true,
    val airingBingeFinaleEnabled: Boolean = true,
    val airingReminderHour: Int = 8,
    val notifyBeforeAirMinutes: Int = 15,
    val airDelayOffsetMinutes: Int = 0,
    val airingNotificationOffsetMinutes: Int = -15,
    val aiConfig: AiConfig = AiConfig(),
    val danmakuConfig: DanmakuConfig = DanmakuConfig(),
    /** 主题模式：跟随系统 / 强制亮色 / 强制深色 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You 动态取色（minSdk 31 恒可用） */
    val dynamicColor: Boolean = false,
    /** 画中画（PiP）模式开关（离开播放页时自动进入画中画小窗） */
    val pipEnabled: Boolean = true,
    /** 弹幕显示开关（播放页长期偏好：关闭期间不发起任何弹幕请求） */
    val danmakuEnabled: Boolean = true,
    /** 弹幕不透明度（0.1f ~ 1.0f） */
    val danmakuAlpha: Float = 1.0f,
    /** 弹幕全屏滚动耗时毫秒数 */
    val danmakuDurationMs: Long = 8500L,
    /** 播放结束自动连播下一集（播放页长期偏好） */
    val autoNextEnabled: Boolean = true,
    /** 吐槽/评论排序偏好（hot / asc / desc；三处评论区共用同一条偏好；空串按各页默认） */
    val commentSortOrder: String = "",
    /** 关闭字幕（长期偏好：进页即按它关掉引擎的字幕轨；不记具体选了哪条轨道） */
    val subtitlesDisabled: Boolean = false,
    val showRestrictedContent: Boolean = false,
    /** 清晰度选择记忆（0 = 自动自适应；>0 = 用户偏好的视频高度，如 1080，下次起播自动匹配最接近档位） */
    val preferredQualityHeight: Int = 0,
)

/**
 * 规则 JSON 导入结果：acceptedCount 为实际落库条数，rejectedCount 为因 kind/parserType
 * 无执行路径或 minClientApi 超出客户端能力级别而被拒的条数。
 * 两者都为 0 表示输入没有解析出任何规则。
 */
data class RuleImportSummary(
    val acceptedCount: Int,
    val rejectedCount: Int,
)

interface SettingsRepository {
    val settings: Flow<UserSettings>
    val aiConfig: Flow<AiConfig>
    val airDelayOffsetMinutes: Flow<Int>
    val playbackRules: Flow<List<PlaybackSourceRule>>

    suspend fun setSyncInterval(interval: SyncInterval)

    /** 开播提醒总开关（通知权限的授予与否由 UI 层请求） */
    suspend fun setAiringReminderEnabled(enabled: Boolean)

    /** 每日更新汇总子开关 */
    suspend fun setAiringDailySummaryEnabled(enabled: Boolean)

    /** 单集开播即时提醒子开关 */
    suspend fun setAiringPreAirEnabled(enabled: Boolean)

    /** 囤番完结开播提醒子开关 */
    suspend fun setAiringBingeFinaleEnabled(enabled: Boolean)

    /** 每日提醒触发时刻（设备本地时间小时） */
    suspend fun setAiringReminderHour(hour: Int)

    /** 单集临近提醒提前时间量（分钟） */
    suspend fun setNotifyBeforeAirMinutes(minutes: Int)

    /** 单集提醒时机相对开播时刻的偏移量（分钟，负数提前，0准时，正数延后） */
    suspend fun setAiringNotificationOffsetMinutes(offsetMinutes: Int)

    /** 主题模式：跟随系统 / 强制亮色 / 强制深色 */
    suspend fun setThemeMode(mode: ThemeMode)

    /** Material You 动态取色开关 */
    suspend fun setDynamicColor(enabled: Boolean)

    /** 画中画（PiP）开关 */
    suspend fun setPipEnabled(enabled: Boolean)

    /** 弹幕显示开关（播放页长期偏好） */
    suspend fun setDanmakuEnabled(enabled: Boolean)

    /** 弹幕不透明度（0.1f ~ 1.0f） */
    suspend fun setDanmakuAlpha(alpha: Float)

    /** 弹幕滚动时长毫秒数 */
    suspend fun setDanmakuDurationMs(durationMs: Long)

    /** 播放结束自动连播下一集 */
    suspend fun setAutoNextEnabled(enabled: Boolean)

    /** 记录吐槽/评论排序偏好（hot / asc / desc） */
    suspend fun setCommentSortOrder(sortKey: String)

    /** 记录「关闭字幕」长期偏好 */
    suspend fun setSubtitlesDisabled(disabled: Boolean)

    /** 记录清晰度选择偏好（0 = 自动自适应；>0 = 偏好视频高度） */
    suspend fun setPreferredQualityHeight(height: Int)

    suspend fun setShowRestrictedContent(enabled: Boolean)

    /** 更新 AI 服务配置 */
    suspend fun setAiConfig(config: AiConfig)

    /** 已保存的 AI 配置方案池（命名快照，供多套端点/密钥快速切换） */
    val aiConfigProfiles: Flow<List<AiConfigProfile>>

    /** 当前启用的方案 id；空串表示未启用任何方案（生效配置仍以 aiConfig 为准） */
    val activeAiProfileId: Flow<String>

    /** 保存 AI 配置方案：同 id 覆盖、新 id 追加 */
    suspend fun saveAiConfigProfile(profile: AiConfigProfile)

    /**
     * 启用指定方案：把方案配置写回当前生效配置（aiConfig 四字段）并记录启用标记。
     * 未知 id 是 no-op——启用动作不允许凭空产生配置。
     */
    suspend fun activateAiConfigProfile(profileId: String)

    /** 删除方案；若删除的是启用中的方案，仅清除启用标记（生效配置保留，不产生行为回退） */
    suspend fun deleteAiConfigProfile(profileId: String)

    suspend fun setAirDelayOffsetMinutes(minutes: Int)

    suspend fun addPlaybackRule(rule: PlaybackSourceRule)

    suspend fun updatePlaybackRule(rule: PlaybackSourceRule)

    suspend fun deletePlaybackRule(ruleId: String)

    suspend fun togglePlaybackRule(
        ruleId: String,
        isEnabled: Boolean,
    )

    suspend fun importPlaybackRules(rules: List<PlaybackSourceRule>)

    /**
     * 解析并导入用户粘贴的播放源规则 JSON（支持单条对象或数组，解析/校验/落库都在仓库层完成）。
     *
     * 只收下 [PlaybackSourceRule.isImportable] 的规则（kind 与 parserType 有执行路径、
     * minClientApi 未超客户端能力级别），被拒条数经 [RuleImportSummary.rejectedCount] 报给调用方。
     * JSON 解析失败返回 [AppResult.Error]，文案固定为「规则解析失败，请检查 JSON 格式」。
     */
    suspend fun importRulesFromJson(jsonText: String): AppResult<RuleImportSummary>

    /**
     * 断点续播位置表（key = 播放地址，value = 上次观看位置毫秒）。
     * 与 Bangumi 的"看过/在看"打卡是两套独立进度，互不覆盖；最多保留 [MAX_PLAYBACK_POSITIONS] 条最近记录。
     */
    val playbackPositions: Flow<Map<String, Long>>

    /** 记录/更新某播放地址的观看位置（0 或负值忽略） */
    suspend fun savePlaybackPosition(
        url: String,
        positionMs: Long,
    )

    /** 播完或位置失效时清除该地址的续播点 */
    suspend fun clearPlaybackPosition(url: String)

    /**
     * 上次成功起播的播放源标识（`PlayerSourceTab.id`）。进播放页时优先选中它，
     * 避免每次都从第一个源开始试错；空串表示尚无记录。
     */
    val lastPlaybackSourceId: Flow<String>

    /** 记录上次成功起播的播放源标识 */
    suspend fun setLastPlaybackSourceId(id: String)

    /**
     * 对调用方给出的订阅地址 / 单站地址 / 规则 JSON 进行拉取、格式校验与端侧并发测速探活。
     *
     * 这里不代客户端检索社区：没有任何内置站点清单，也不接受「关键词搜索」这类入口，
     * 目标一律由调用方（用户输入）显式给出。
     */
    suspend fun validateAndTestSubscription(
        url: String,
    ): com.infinitezerone.minibgm.core.common.AppResult<com.infinitezerone.minibgm.core.model.SubscriptionValidationReport>

    /** 社区/外部播放规则订阅地址（空串表示未配置） */
    val playbackSubscriptionUrl: Flow<String>

    suspend fun setPlaybackSubscriptionUrl(url: String)

    /**
     * 按已保存的 [playbackSubscriptionUrl] 刷新订阅：
     * 重新拉取、探活测速，并将可用规则更新/并入本地规则库。
     */
    suspend fun refreshPlaybackSubscription(): AppResult<RuleImportSummary>

    /** 社区 TVBox 聚合解析接口配置列表流 */
    val playbackParses: Flow<List<com.infinitezerone.minibgm.core.model.TvBoxParse>>

    /** 更新社区 TVBox 聚合解析接口配置列表 */
    suspend fun setPlaybackParses(parses: List<com.infinitezerone.minibgm.core.model.TvBoxParse>)

    /** 弹弹play 聚合弹幕配置流 */
    val danmakuConfig: Flow<DanmakuConfig>

    suspend fun setDanmakuConfig(config: DanmakuConfig)

    /** 运行时开发者调试设置只读流 */
    val developerSettings: Flow<DeveloperSettings>

    /** 动态判断当前 AI 助手是否启用（结合编译期默认与开发者动态覆写） */
    val isAiAssistantEnabled: Flow<Boolean>

    /** 动态判断当前内置播放器是否启用（结合编译期默认与开发者动态覆写） */
    val isInternalPlayerEnabled: Flow<Boolean>

    /** 应用全局特性能力开关集只读流（单一真源） */
    val featureCapabilities: Flow<FeatureCapabilities>

    /** 设置开发者模式激活状态 */
    suspend fun setDeveloperModeEnabled(enabled: Boolean)

    /** 设置 AI 助手功能开关覆写（null = 恢复默认） */
    suspend fun setDevOverrideEnableAi(enabled: Boolean?)

    /** 设置内置播放器开关覆写（null = 恢复默认） */
    suspend fun setDevOverrideEnablePlayer(enabled: Boolean?)

    /** 设置详细网络与调试日志开关 */
    suspend fun setDevVerboseLogging(enabled: Boolean)

    /** 设置模拟 BT 原生扩展组件未安装状态 */
    suspend fun setDevSimulateTorrentUninstalled(enabled: Boolean)

    /** 设置自定义加速镜像前缀覆写 */
    suspend fun setDevCustomMirrorPrefix(prefix: String)
}

internal class SettingsRepositoryImpl(
    private val userPreferences: UserPreferencesDataSource,
    private val secureSecretStore: SecureSecretStore,
    private val communitySubscriptionService: com.infinitezerone.minibgm.core.network.CommunitySubscriptionService? = null,
) : SettingsRepository {
    private val json = BgmHttpClient.jsonConfig

    override val settings: Flow<UserSettings> =
        combine(userPreferences.userPreferences, secureSecretStore.observeSecrets()) { prefs, secrets ->
            UserSettings(
                syncInterval = prefs.syncInterval,
                bangumiDataLastSyncTimestamp = prefs.bangumiDataLastSyncTimestamp,
                airingReminderEnabled = prefs.airingReminderEnabled,
                airingDailySummaryEnabled = prefs.airingDailySummaryEnabled,
                airingPreAirEnabled = prefs.airingPreAirEnabled,
                airingBingeFinaleEnabled = prefs.airingBingeFinaleEnabled,
                airingReminderHour = prefs.airingReminderHour,
                notifyBeforeAirMinutes = prefs.notifyBeforeAirMinutes,
                airDelayOffsetMinutes = prefs.airDelayOffsetMinutes,
                airingNotificationOffsetMinutes = prefs.airingNotificationOffsetMinutes,
                aiConfig =
                    AiConfig(
                        endpoint = prefs.aiEndpoint,
                        apiKey = secrets[AI_ACTIVE_KEY].orEmpty(),
                        model = prefs.aiModel,
                        provider = prefs.aiProvider,
                    ),
                danmakuConfig =
                    DanmakuConfig(
                        endpoint = prefs.danmakuEndpoint,
                        appId = prefs.danmakuAppId,
                        appSecret = secrets[DANDANPLAY_SECRET_KEY].orEmpty(),
                    ),
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor,
                pipEnabled = prefs.pipEnabled,
                danmakuEnabled = prefs.danmakuEnabled,
                danmakuAlpha = prefs.danmakuAlpha,
                danmakuDurationMs = prefs.danmakuDurationMs,
                autoNextEnabled = prefs.autoNextEnabled,
                commentSortOrder = prefs.commentSortOrder,
                subtitlesDisabled = prefs.subtitlesDisabled,
                preferredQualityHeight = prefs.preferredQualityHeight,
                showRestrictedContent = prefs.showRestrictedContent,
            )
        }

    override val aiConfig: Flow<AiConfig> =
        combine(userPreferences.userPreferences, secureSecretStore.observeSecrets()) { prefs, secrets ->
            AiConfig(
                endpoint = prefs.aiEndpoint,
                apiKey = secrets[AI_ACTIVE_KEY].orEmpty(),
                model = prefs.aiModel,
                provider = prefs.aiProvider,
            )
        }

    override val danmakuConfig: Flow<DanmakuConfig> =
        combine(userPreferences.userPreferences, secureSecretStore.observeSecrets()) { prefs, secrets ->
            DanmakuConfig(
                endpoint = prefs.danmakuEndpoint,
                appId = prefs.danmakuAppId,
                appSecret = secrets[DANDANPLAY_SECRET_KEY].orEmpty(),
            )
        }

    override suspend fun setDanmakuConfig(config: DanmakuConfig) {
        userPreferences.setDanmakuSettings(config.endpoint, config.appId)
        val secret = config.appSecret.trim()
        if (secret.isNotBlank()) {
            secureSecretStore.setSecret(DANDANPLAY_SECRET_KEY, secret)
        } else {
            secureSecretStore.removeSecret(DANDANPLAY_SECRET_KEY)
        }
    }

    override val airDelayOffsetMinutes: Flow<Int> =
        userPreferences.userPreferences.map { it.airDelayOffsetMinutes }

    private var healingTriggered = false
    private val healingMutex = Mutex()

    private suspend fun triggerSubscriptionHealingIfMissing() {
        if (healingTriggered) return
        healingMutex.withLock {
            if (healingTriggered) return
            healingTriggered = true
            val prefs = userPreferences.userPreferences.first()
            val url = prefs.playbackSubscriptionUrl.trim()
            val rawJson = prefs.playbackRulesJson.trim()
            val currentRules =
                if (rawJson.isBlank()) {
                    emptyList()
                } else {
                    runCatching { json.decodeFromString<List<PlaybackSourceRule>>(rawJson) }.getOrDefault(emptyList())
                }
            if (url.isNotBlank()) {
                val hasSubRules = currentRules.any { it.id.startsWith("sub_") }
                if (!hasSubRules || currentRules.size <= 1) {
                    runCatching { refreshPlaybackSubscription() }
                }
            }
        }
    }

    override val playbackRules: Flow<List<PlaybackSourceRule>> =
        userPreferences.userPreferences
            .map { prefs ->
                if (prefs.playbackRulesJson.isBlank()) {
                    emptyList()
                } else {
                    runCatching {
                        json.decodeFromString<List<PlaybackSourceRule>>(prefs.playbackRulesJson)
                    }.getOrDefault(emptyList())
                }
            }.onStart {
                triggerSubscriptionHealingIfMissing()
            }

    override val playbackParses: Flow<List<com.infinitezerone.minibgm.core.model.TvBoxParse>> =
        userPreferences.userPreferences.map { prefs ->
            if (prefs.playbackParsesJson.isBlank()) {
                emptyList()
            } else {
                runCatching {
                    json.decodeFromString<List<com.infinitezerone.minibgm.core.model.TvBoxParse>>(prefs.playbackParsesJson)
                }.getOrDefault(emptyList())
            }
        }

    override suspend fun setPlaybackParses(parses: List<com.infinitezerone.minibgm.core.model.TvBoxParse>) {
        userPreferences.setPlaybackParsesJson(json.encodeToString(parses))
    }

    override suspend fun setSyncInterval(interval: SyncInterval) {
        userPreferences.setSyncInterval(interval)
    }

    override suspend fun setAiringReminderEnabled(enabled: Boolean) {
        userPreferences.setAiringReminderEnabled(enabled)
    }

    override suspend fun setAiringDailySummaryEnabled(enabled: Boolean) {
        userPreferences.setAiringDailySummaryEnabled(enabled)
    }

    override suspend fun setAiringPreAirEnabled(enabled: Boolean) {
        userPreferences.setAiringPreAirEnabled(enabled)
    }

    override suspend fun setAiringBingeFinaleEnabled(enabled: Boolean) {
        userPreferences.setAiringBingeFinaleEnabled(enabled)
    }

    override suspend fun setAiringReminderHour(hour: Int) {
        userPreferences.setAiringReminderHour(hour)
    }

    override suspend fun setNotifyBeforeAirMinutes(minutes: Int) {
        userPreferences.setNotifyBeforeAirMinutes(minutes)
    }

    override suspend fun setAiringNotificationOffsetMinutes(offsetMinutes: Int) {
        userPreferences.setAiringNotificationOffsetMinutes(offsetMinutes)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        userPreferences.setThemeMode(mode)
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        userPreferences.setDynamicColor(enabled)
    }

    override suspend fun setPipEnabled(enabled: Boolean) {
        userPreferences.setPipEnabled(enabled)
    }

    override suspend fun setDanmakuEnabled(enabled: Boolean) {
        userPreferences.setDanmakuEnabled(enabled)
    }

    override suspend fun setDanmakuAlpha(alpha: Float) {
        userPreferences.setDanmakuAlpha(alpha)
    }

    override suspend fun setDanmakuDurationMs(durationMs: Long) {
        userPreferences.setDanmakuDurationMs(durationMs)
    }

    override suspend fun setAutoNextEnabled(enabled: Boolean) {
        userPreferences.setAutoNextEnabled(enabled)
    }

    override suspend fun setCommentSortOrder(sortKey: String) {
        userPreferences.setCommentSortOrder(sortKey)
    }

    override suspend fun setSubtitlesDisabled(disabled: Boolean) {
        userPreferences.setSubtitlesDisabled(disabled)
    }

    override suspend fun setPreferredQualityHeight(height: Int) {
        userPreferences.setPreferredQualityHeight(height)
    }

    override suspend fun setShowRestrictedContent(enabled: Boolean) {
        userPreferences.setShowRestrictedContent(enabled)
    }

    override suspend fun setAiConfig(config: AiConfig) {
        userPreferences.setAiConfig(
            endpoint = config.endpoint,
            model = config.model,
            provider = config.provider,
        )
        secureSecretStore.setSecret(AI_ACTIVE_KEY, config.apiKey)
    }

    private val aiProfilesWriteMutex = Mutex()

    private fun decodeAiProfiles(raw: String): List<AiConfigProfile> =
        if (raw.isBlank()) {
            emptyList()
        } else {
            runCatching {
                json.decodeFromString<List<AiConfigProfile>>(raw)
            }.getOrDefault(emptyList())
        }

    override val aiConfigProfiles: Flow<List<AiConfigProfile>> =
        combine(userPreferences.userPreferences, secureSecretStore.observeSecrets()) { prefs, secrets ->
            decodeAiProfiles(prefs.aiConfigProfilesJson).map { profile ->
                profile.copy(config = profile.config.copy(apiKey = secrets[aiProfileSecretKey(profile.id)].orEmpty()))
            }
        }

    override val activeAiProfileId: Flow<String> =
        userPreferences.userPreferences.map { prefs ->
            prefs.aiActiveProfileId
        }

    override suspend fun saveAiConfigProfile(profile: AiConfigProfile) {
        aiProfilesWriteMutex.withLock {
            val current = decodeAiProfiles(userPreferences.userPreferences.first().aiConfigProfilesJson)
            // 方案池 JSON 只存非密钥字段，密钥单独进加密库
            val sanitized = profile.copy(config = profile.config.copy(apiKey = ""))
            val updated = current.filterNot { it.id == profile.id }.map { it.copy(config = it.config.copy(apiKey = "")) } + sanitized
            userPreferences.setAiConfigProfilesJson(json.encodeToString(updated))
            secureSecretStore.setSecret(aiProfileSecretKey(profile.id), profile.config.apiKey)
        }
    }

    override suspend fun activateAiConfigProfile(profileId: String) {
        val profile =
            decodeAiProfiles(userPreferences.userPreferences.first().aiConfigProfilesJson)
                .firstOrNull { it.id == profileId } ?: return
        userPreferences.setAiConfig(
            endpoint = profile.config.endpoint,
            model = profile.config.model,
            provider = profile.config.provider,
        )
        secureSecretStore.setSecret(AI_ACTIVE_KEY, secureSecretStore.getSecret(aiProfileSecretKey(profileId)).orEmpty())
        userPreferences.setAiActiveProfileId(profileId)
    }

    override suspend fun deleteAiConfigProfile(profileId: String) {
        aiProfilesWriteMutex.withLock {
            val prefs = userPreferences.userPreferences.first()
            val current = decodeAiProfiles(prefs.aiConfigProfilesJson)
            if (current.none { it.id == profileId }) return@withLock
            userPreferences.setAiConfigProfilesJson(
                json.encodeToString(
                    current.filterNot { it.id == profileId }.map { it.copy(config = it.config.copy(apiKey = "")) },
                ),
            )
            secureSecretStore.removeSecret(aiProfileSecretKey(profileId))
            if (prefs.aiActiveProfileId == profileId) {
                userPreferences.setAiActiveProfileId("")
            }
        }
    }

    private fun aiProfileSecretKey(profileId: String): String = "ai.profile.$profileId.apiKey"

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

    override suspend fun importPlaybackRules(rules: List<PlaybackSourceRule>) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            val current = playbackRules.first()
            val existingIds = current.map { it.id }.toSet()
            val newRules = rules.filterNot { it.id in existingIds }
            val updated = current + newRules
            userPreferences.setPlaybackRulesJson(json.encodeToString(updated))
        }

    override suspend fun importRulesFromJson(jsonText: String): AppResult<RuleImportSummary> =
        try {
            val trimmed = jsonText.trim()
            // 1. 优先尝试直接解析标准 PlaybackSourceRule / List<PlaybackSourceRule>
            val directList =
                runCatching {
                    if (trimmed.startsWith("[")) {
                        json.decodeFromString<List<PlaybackSourceRule>>(trimmed)
                    } else {
                        listOf(json.decodeFromString<PlaybackSourceRule>(trimmed))
                    }
                }.getOrNull()

            // 2. 兼容解析 MiniBgm 订阅包 ({"version":1,"sources":[...]})、TVBox 配置包及 DiscoveredSource 数组
            val importedList: List<PlaybackSourceRule> =
                if (!directList.isNullOrEmpty()) {
                    directList
                } else if (communitySubscriptionService != null && (trimmed.startsWith("{") || trimmed.startsWith("["))) {
                    val report = communitySubscriptionService.validateAndTestSubscription(trimmed)
                    if (report.errorMessage != null && report.sources.isEmpty()) {
                        throw IllegalStateException(report.errorMessage)
                    }
                    if (report.parses.isNotEmpty()) {
                        setPlaybackParses(report.parses)
                    }
                    report.sources.map { source ->
                        source.toPlaybackSourceRule(
                            id = "sub_${source.name.hashCode().toUInt().toString(16)}",
                        )
                    }
                } else {
                    throw IllegalStateException("规则解析失败，请检查 JSON 格式")
                }

            when {
                importedList.isEmpty() -> AppResult.Success(RuleImportSummary(acceptedCount = 0, rejectedCount = 0))
                // kind 与 parserType 不匹配时流水线/专用解析器根本没有执行路径，收下只会静默降级成嗅探；
                // minClientApi 超出本客户端能力级别的规则同理——宁拒收，不跑错语义
                else -> {
                    val (accepted, rejected) = importedList.partition { it.isImportable }
                    if (accepted.isNotEmpty()) importPlaybackRules(accepted)
                    AppResult.Success(
                        RuleImportSummary(acceptedCount = accepted.size, rejectedCount = rejected.size),
                    )
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 不拼 it.message：反序列化异常的原文是英文（如 "Unexpected JSON token at offset 12"），
            // 对用户没有意义。这里真正有用的是"检查 JSON 格式"的行动提示。
            AppResult.Error(IllegalStateException("规则解析失败，请检查 JSON 格式"))
        }

    private val playbackPositionsWriteMutex = Mutex()

    override val playbackPositions: Flow<Map<String, Long>> =
        userPreferences.userPreferences.map { prefs ->
            if (prefs.playbackPositionsJson.isBlank()) {
                emptyMap()
            } else {
                runCatching { json.decodeFromString<Map<String, Long>>(prefs.playbackPositionsJson) }.getOrDefault(emptyMap())
            }
        }

    override suspend fun savePlaybackPosition(
        url: String,
        positionMs: Long,
    ) {
        if (url.isBlank() || positionMs <= 0L) return
        playbackPositionsWriteMutex.withLock {
            val current = playbackPositions.first()
            userPreferences.setPlaybackPositionsJson(
                json.encodeToString<Map<String, Long>>(current.withUpdatedPosition(url, positionMs)),
            )
        }
    }

    override suspend fun clearPlaybackPosition(url: String) {
        if (url.isBlank()) return
        playbackPositionsWriteMutex.withLock {
            val current = playbackPositions.first()
            if (url !in current) return@withLock
            userPreferences.setPlaybackPositionsJson(
                json.encodeToString<Map<String, Long>>(current - url),
            )
        }
    }

    override val lastPlaybackSourceId: Flow<String> =
        userPreferences.userPreferences.map { it.lastPlaybackSourceId }

    override suspend fun setLastPlaybackSourceId(id: String) {
        userPreferences.setLastPlaybackSourceId(id)
    }

    override suspend fun validateAndTestSubscription(
        url: String,
    ): com.infinitezerone.minibgm.core.common.AppResult<com.infinitezerone.minibgm.core.model.SubscriptionValidationReport> =
        try {
            val service =
                communitySubscriptionService
                    ?: return com.infinitezerone.minibgm.core.common.AppResult
                        .Success(
                            com.infinitezerone.minibgm.core.model.SubscriptionValidationReport(
                                isHealthy = false,
                                subscriptionUrl = url,
                                errorMessage = "网络服务未就绪",
                            ),
                        )
            val report = service.validateAndTestSubscription(url)
            com.infinitezerone.minibgm.core.common.AppResult
                .Success(report)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.infinitezerone.minibgm.core.common.AppResult
                .Error(e)
        }

    override val playbackSubscriptionUrl: Flow<String> =
        userPreferences.userPreferences.map { it.playbackSubscriptionUrl }

    override suspend fun setPlaybackSubscriptionUrl(url: String) {
        userPreferences.setPlaybackSubscriptionUrl(url)
    }

    override suspend fun refreshPlaybackSubscription(): AppResult<RuleImportSummary> =
        try {
            val url = playbackSubscriptionUrl.first().trim()
            if (url.isBlank()) {
                AppResult.Error(IllegalStateException("尚未配置订阅地址"))
            } else {
                when (val reportResult = validateAndTestSubscription(url)) {
                    is AppResult.Success -> {
                        val report = reportResult.data
                        if (report.parses.isNotEmpty()) {
                            setPlaybackParses(report.parses)
                        }
                        val candidateSources = report.sources
                        if (candidateSources.isEmpty()) {
                            AppResult.Error(IllegalStateException("订阅源中没有解析到有效播放源"))
                        } else {
                            val newRules =
                                candidateSources.map { source ->
                                    source.toPlaybackSourceRule(
                                        id = "sub_${source.name.hashCode().toUInt().toString(16)}",
                                    )
                                }
                            val current = playbackRules.first()
                            val mutableRules = current.toMutableList()
                            var accepted = 0
                            for (rule in newRules) {
                                val existingIndex =
                                    mutableRules.indexOfFirst {
                                        it.name.equals(rule.name, ignoreCase = true) || it.urlTemplate == rule.urlTemplate
                                    }
                                if (existingIndex >= 0) {
                                    val existing = mutableRules[existingIndex]
                                    mutableRules[existingIndex] =
                                        rule.copy(
                                            id = existing.id,
                                            isEnabled = existing.isEnabled,
                                        )
                                } else {
                                    mutableRules.add(rule)
                                }
                                accepted++
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                userPreferences.setPlaybackRulesJson(json.encodeToString(mutableRules))
                            }
                            AppResult.Success(RuleImportSummary(acceptedCount = accepted, rejectedCount = 0))
                        }
                    }
                    is AppResult.Error -> AppResult.Error(reportResult.throwable, reportResult.message)
                    is AppResult.Loading -> AppResult.Error(IllegalStateException("校验未完成"))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Error(e, "刷新订阅失败：${e.message}")
        }

    override val developerSettings: Flow<DeveloperSettings> =
        userPreferences.userPreferences.map { prefs ->
            DeveloperSettings(
                isDeveloperModeEnabled = prefs.developerModeEnabled,
                enableAiOverride = prefs.devOverrideEnableAi,
                enablePlayerOverride = prefs.devOverrideEnablePlayer,
                verboseLoggingEnabled = prefs.devVerboseLogging,
                simulateTorrentUninstalled = prefs.devSimulateTorrentUninstalled,
                customMirrorPrefixOverride = prefs.devCustomMirrorPrefix,
            )
        }

    override val isAiAssistantEnabled: Flow<Boolean> =
        userPreferences.userPreferences.map { prefs ->
            prefs.devOverrideEnableAi ?: true
        }

    override val isInternalPlayerEnabled: Flow<Boolean> =
        userPreferences.userPreferences.map { prefs ->
            prefs.devOverrideEnablePlayer ?: true
        }

    override val featureCapabilities: Flow<FeatureCapabilities> =
        userPreferences.userPreferences.map { prefs ->
            FeatureCapabilities(
                isAiAssistantEnabled = prefs.devOverrideEnableAi ?: true,
                isInternalPlayerEnabled = prefs.devOverrideEnablePlayer ?: true,
            )
        }

    override suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        userPreferences.setDeveloperModeEnabled(enabled)
    }

    override suspend fun setDevOverrideEnableAi(enabled: Boolean?) {
        userPreferences.setDevOverrideEnableAi(enabled)
    }

    override suspend fun setDevOverrideEnablePlayer(enabled: Boolean?) {
        userPreferences.setDevOverrideEnablePlayer(enabled)
    }

    override suspend fun setDevVerboseLogging(enabled: Boolean) {
        userPreferences.setDevVerboseLogging(enabled)
    }

    override suspend fun setDevSimulateTorrentUninstalled(enabled: Boolean) {
        userPreferences.setDevSimulateTorrentUninstalled(enabled)
    }

    override suspend fun setDevCustomMirrorPrefix(prefix: String) {
        userPreferences.setDevCustomMirrorPrefix(prefix)
    }

    companion object {
        const val MAX_PLAYBACK_POSITIONS = 50
    }
}

/**
 * 位置表更新与淘汰：重插即视为"最近使用"（LinkedHashMap 保序），超上限淘汰最旧的地址。
 * internal 供测试直接断言淘汰行为。
 */
internal fun Map<String, Long>.withUpdatedPosition(
    url: String,
    positionMs: Long,
): Map<String, Long> {
    val updated = LinkedHashMap<String, Long>(size + 1)
    for ((key, value) in this) {
        if (key != url) updated[key] = value
    }
    updated[url] = positionMs
    return if (updated.size > SettingsRepositoryImpl.MAX_PLAYBACK_POSITIONS) {
        LinkedHashMap(
            updated
                .entries
                .drop(updated.size - SettingsRepositoryImpl.MAX_PLAYBACK_POSITIONS)
                .associate { it.key to it.value },
        )
    } else {
        updated
    }
}
