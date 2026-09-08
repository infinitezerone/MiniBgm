package com.infinitezerone.minibgm.core.datastore

import com.infinitezerone.minibgm.core.model.UserProfile
import kotlinx.serialization.Serializable

/**
 * 普通用户偏好。OAuth token 不在此处存储——它们由 AuthTokensDataSource
 * 经 AndroidKeyStore 加密后写入独立文件，并被备份规则整体排除。
 */
@Serializable
data class UserPreferences(
    val activeUserId: Long = 0L,
    val savedProfiles: Map<Long, UserProfile> = emptyMap(),
    val isLoggedIn: Boolean = false,
    /** 进行中登录的 PKCE 等价 verifier（其 sha256 指纹作为 OAuth state，见 BgmPkce） */
    val pendingOAuthVerifier: String = "",
    val isDarkMode: Boolean = false,
    val notifyBeforeAirMinutes: Int = 15,
    /** 开播提醒总开关（每日追番更新汇总通知） */
    val airingReminderEnabled: Boolean = true,
    /** 上次发出更新提醒的日期（yyyy-MM-dd，用于每日去重） */
    val airingReminderLastNotifiedDate: String = "",
    /** 每日提醒的触发时刻（设备本地时间小时 0-23，默认早上 8 点） */
    val airingReminderHour: Int = 8,
    /** 已逐集提醒过的开播事件键（"yyyy-MM-dd:subjectId:episode"，读取侧按当日裁剪去重） */
    val airingReminderNotifiedKeys: List<String> = emptyList(),
    /** bangumi-data CDN 静态数据的 HTTP ETag 指纹（用于 304 条件请求，避免全量重复拉取） */
    val bangumiDataEtag: String = "",
    /** 播放源后台自动同步频率 */
    val syncInterval: com.infinitezerone.minibgm.core.model.SyncInterval = com.infinitezerone.minibgm.core.model.SyncInterval.WEEKLY,
    /** 上次成功同步 bangumi-data 播放源的时间戳 (毫秒) */
    val bangumiDataLastSyncTimestamp: Long = 0L,
    /** 放送时刻表默认筛选：false 为全部，true 为仅展示我追的番 */
    val scheduleDefaultOnlyWatching: Boolean = false,
    /** 本地最近搜索历史词条列表（按最近使用降序，最多 20 条） */
    val searchHistory: List<String> = emptyList(),
) {
    val activeProfile: UserProfile?
        get() = if (isLoggedIn) savedProfiles[activeUserId] else null

    val allProfiles: List<UserProfile>
        get() = savedProfiles.values.toList()
}
