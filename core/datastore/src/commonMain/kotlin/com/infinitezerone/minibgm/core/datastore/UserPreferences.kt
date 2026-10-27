package com.infinitezerone.minibgm.core.datastore

import com.infinitezerone.minibgm.core.model.ThemeMode
import com.infinitezerone.minibgm.core.model.UserProfile
import kotlinx.serialization.Serializable

/**
 * 设备侧偏好 + 账号资料池。会话事实（活跃用户、登录与否）唯一存放在
 * AuthTokensDataSource（加密、备份排除），本模型不承载任何登录标记——
 * 云备份/设备迁移即使恢复本文件也不会产生「假登录态」。
 */
@Serializable
data class UserPreferences(
    /** 账号资料池：曾登录过的资料缓存（展示与快捷切换用），会话有效性以凭据库为准 */
    val savedProfiles: Map<Long, UserProfile> = emptyMap(),
    /** 进行中登录的 PKCE 等价 verifier（其 sha256 指纹作为 OAuth state，见 BgmPkce） */
    val pendingOAuthVerifier: String = "",
    val isDarkMode: Boolean = false,
    /** 主题模式：跟随系统 / 强制亮色 / 强制深色（默认跟随系统） */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You 动态取色（minSdk 31 恒可用） */
    val dynamicColor: Boolean = false,
    /** 是否在主界面底部导航栏旁显示独立 AI 助手入口按钮（默认开启） */
    val showBottomAiButton: Boolean = true,
    /** 画中画模式开关（开启后播放中划回桌面自动进入小窗） */
    val pipEnabled: Boolean = true,
    /** 弹幕显示开关（播放页长期偏好：关闭期间不发起任何弹幕请求，重开时才补拉） */
    val danmakuEnabled: Boolean = true,
    /** 弹幕不透明度（0.1f ~ 1.0f，默认 1.0f 全不透明） */
    val danmakuAlpha: Float = 1.0f,
    /** 弹幕全屏滚动耗时毫秒数（例如 11000L 慢速，8500L 适中默认，6500L 较快，5000L 快速） */
    val danmakuDurationMs: Long = 8500L,
    /** 播放结束自动连播下一集（播放页长期偏好） */
    val autoNextEnabled: Boolean = true,
    val notifyBeforeAirMinutes: Int = 15,
    /** 开播提醒总开关 */
    val airingReminderEnabled: Boolean = true,
    /** 单集开播即时通知子开关 */
    val airingPreAirEnabled: Boolean = true,
    /** 囤番完结提醒子开关：开启时，标记为囤番的条目在最终话开播时提醒 */
    val airingBingeFinaleEnabled: Boolean = true,
    /** 单集提醒时机相对开播时刻的偏移量（分钟，负数提前，0准时，正数延后，默认 -15 即提前 15 分钟） */
    val airingNotificationOffsetMinutes: Int = -15,
    /** 已逐集提醒过的开播事件键（"yyyy-MM-dd:subjectId:episode"，读取侧按当日裁剪去重） */
    val airingReminderNotifiedKeys: List<String> = emptyList(),
    /** 放送时刻表快照的 HTTP ETag 指纹（用于 304 条件请求，避免全量重复拉取） */
    val scheduleSnapshotEtag: String = "",
    /** 播放源后台自动同步频率 */
    val syncInterval: com.infinitezerone.minibgm.core.model.SyncInterval = com.infinitezerone.minibgm.core.model.SyncInterval.WEEKLY,
    /** 上次成功同步 bangumi-data 播放源的时间戳 (毫秒) */
    val bangumiDataLastSyncTimestamp: Long = 0L,
    /** 上次成功同步「在看」收藏的时间戳 (毫秒) */
    val collectionsLastSyncTimestamp: Long = 0L,
    /** 放送时刻表默认筛选：false 为全部，true 为仅展示我追的番 */
    val scheduleDefaultOnlyWatching: Boolean = false,
    val showRestrictedContent: Boolean = false,
    /** 本地最近搜索历史词条列表（按最近使用降序，最多 20 条） */
    val searchHistory: List<String> = emptyList(),
    /** 探索发现页排序偏好（heat / score / rank） */
    val exploreSortPreference: String = "rank",
    /**
     * 吐槽/评论排序偏好（hot / asc / desc）。
     *
     * 播放页与分集详情页共用同一条设备侧偏好——同一个评论区组件在两处各持一份会话状态时，
     * 用户在一处改成「最新」、另一处仍是「热门」，重启又双双回默认。空串表示尚未设置（按热门）。
     */
    val commentSortOrder: String = "",
    /**
     * 关闭字幕（长期偏好：进页即按它把引擎的字幕轨关掉）。
     *
     * 只记「关不关」这条立场，不记用户选了哪条轨道——轨道 override 跟着具体媒体走，换源/换集
     * 由 `PlayerController.resetTrackOverridesOnMediaChange()` 收敛，落库等于把上一部的轨道选择
     * 带到下一部毫不相干的番上。
     */
    val subtitlesDisabled: Boolean = false,
    /** 题材标签排除黑名单 */
    val blockedSubjectTags: List<String> = emptyList(),
    /** 囤番（待全剧终开刷）条目 ID 集合：普通集数静默通知，仅完结最终话提醒 */
    val bingeSubjectIds: Set<Long> = emptySet(),
    /** 开播提醒延迟偏移 (分钟) */
    val airDelayOffsetMinutes: Int = 0,
    /** AI 服务配置：端点地址（如本地 Ollama http://10.0.2.2:11434/v1 或兼容 OpenAI 协议的云端端点） */
    val aiEndpoint: String = "",
    /** AI 服务配置：模型名称（如 qwen2.5:7b, gpt-4o-mini） */
    val aiModel: String = "",
    /** AI 服务配置：服务商类型（如 ollama, openai） */
    val aiProvider: String = "ollama",
    /** AI 配置方案池 JSON（List<AiConfigProfile>；空串表示尚未保存任何方案） */
    val aiConfigProfilesJson: String = "",
    /** 当前启用的 AI 配置方案 id（空串表示未启用任何方案；生效配置始终以 ai* 四字段为准） */
    val aiActiveProfileId: String = "",
    /** 自定义播放规则列表 JSON 序列化字符串 */
    val playbackRulesJson: String = "",
    /** 断点续播位置表 JSON（Map<播放地址, 毫秒>，最多 50 条最近记录；与打卡进度是两套独立数据） */
    val playbackPositionsJson: String = "",
    /** 上次成功起播的播放源标识（PlayerSourceTab.id / 规则 id；空串表示尚无记录），用于进页时优先选中 */
    val lastPlaybackSourceId: String = "",
    /** 社区/外部播放规则订阅地址（空串表示未配置） */
    val playbackSubscriptionUrl: String = "",
    /** 社区 TVBox 聚合解析接口配置列表 JSON (List<TvBoxParse>) */
    val playbackParsesJson: String = "",
    /** 用户保存的季度导视与探索发现自定义常用筛选标签列表（有序） */
    val customFilterTags: List<String> = emptyList(),
    /** 弹幕服务端点（空串表示使用默认官方端点） */
    val danmakuEndpoint: String = "",
    /** 弹弹play 开放平台应用 ID */
    val danmakuAppId: String = "",
    /** 清晰度选择记忆（0 = 自动自适应；>0 = 偏好视频高度，如 1080，下次起播自动匹配最接近档位） */
    val preferredQualityHeight: Int = 0,
    /** 是否已激活开发者选项（连点版本号7次激活，或开发调试包默认激活） */
    val developerModeEnabled: Boolean = false,
    /** 开发者覆盖：AI 助手功能（null = 跟随默认，true = 强开，false = 强关） */
    val devOverrideEnableAi: Boolean? = null,
    /** 开发者覆盖：内置播放器功能（null = 跟随默认，true = 强开，false = 强关） */
    val devOverrideEnablePlayer: Boolean? = null,
    /** 开发者覆盖：详细网络与调试日志 */
    val devVerboseLogging: Boolean = false,
    /** 开发者覆盖：模拟未安装 BT 原生扩展组件 */
    val devSimulateTorrentUninstalled: Boolean = false,
    /** 开发者覆盖：加速镜像前缀 */
    val devCustomMirrorPrefix: String = "",
) {
    val allProfiles: List<UserProfile>
        get() = savedProfiles.values.toList()
}
