package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.AiConfigProfile
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
    /** AMOLED 纯黑模式（仅在深色模式下生效：表面/容器阶梯取纯黑或近纯黑） */
    val amoledDarkMode: Boolean = false,
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

    /** AMOLED 纯黑模式开关（仅在深色模式下生效） */
    suspend fun setAmoledDarkMode(enabled: Boolean)

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
     * 对调用方给出的订阅地址 / 单站地址 / 规则 JSON 进行拉取、格式校验与端侧并发测速探活。
     *
     * 这里不代客户端检索社区：没有任何内置站点清单，也不接受「关键词搜索」这类入口，
     * 目标一律由调用方（用户输入）显式给出。
     */
    suspend fun validateAndTestSubscription(
        url: String,
    ): com.infinitezerone.minibgm.core.common.AppResult<com.infinitezerone.minibgm.core.model.SubscriptionValidationReport>
}

class SettingsRepositoryImpl(
    private val userPreferences: UserPreferencesDataSource,
    private val communitySubscriptionService: com.infinitezerone.minibgm.core.network.CommunitySubscriptionService? = null,
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
                amoledDarkMode = prefs.amoledDarkMode,
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

    override suspend fun setAmoledDarkMode(enabled: Boolean) {
        userPreferences.setAmoledDarkMode(enabled)
    }

    override suspend fun setAiConfig(config: AiConfig) {
        userPreferences.setAiConfig(
            endpoint = config.endpoint,
            apiKey = config.apiKey,
            model = config.model,
            provider = config.provider,
        )
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
        userPreferences.userPreferences.map { prefs ->
            decodeAiProfiles(prefs.aiConfigProfilesJson)
        }

    override val activeAiProfileId: Flow<String> =
        userPreferences.userPreferences.map { prefs ->
            prefs.aiActiveProfileId
        }

    override suspend fun saveAiConfigProfile(profile: AiConfigProfile) {
        aiProfilesWriteMutex.withLock {
            val current = decodeAiProfiles(userPreferences.userPreferences.first().aiConfigProfilesJson)
            val updated = current.filterNot { it.id == profile.id } + profile
            userPreferences.setAiConfigProfilesJson(json.encodeToString(updated))
        }
    }

    override suspend fun activateAiConfigProfile(profileId: String) {
        val profile =
            decodeAiProfiles(userPreferences.userPreferences.first().aiConfigProfilesJson)
                .firstOrNull { it.id == profileId } ?: return
        userPreferences.setAiConfig(
            endpoint = profile.config.endpoint,
            apiKey = profile.config.apiKey,
            model = profile.config.model,
            provider = profile.config.provider,
        )
        userPreferences.setAiActiveProfileId(profileId)
    }

    override suspend fun deleteAiConfigProfile(profileId: String) {
        aiProfilesWriteMutex.withLock {
            val prefs = userPreferences.userPreferences.first()
            val current = decodeAiProfiles(prefs.aiConfigProfilesJson)
            if (current.none { it.id == profileId }) return@withLock
            userPreferences.setAiConfigProfilesJson(json.encodeToString(current.filterNot { it.id == profileId }))
            if (prefs.aiActiveProfileId == profileId) {
                userPreferences.setAiActiveProfileId("")
            }
        }
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

    override suspend fun importPlaybackRules(rules: List<PlaybackSourceRule>) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
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
