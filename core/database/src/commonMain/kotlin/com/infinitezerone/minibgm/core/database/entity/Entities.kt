package com.infinitezerone.minibgm.core.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "air_schedules",
    indices = [
        Index(value = ["weekday", "sortMinutes", "ratingScore"]),
    ],
)
data class AirScheduleEntity(
    @PrimaryKey val bgmId: Long,
    val title: String,
    val titleCn: String,
    val coverUrl: String,
    val ratingScore: Double,
    val airDate: String = "",
    val beginAtUtc: String? = null,
    val sortMinutes: Int = UNKNOWN_SORT_MINUTES,
    val weekday: Int,
    val timeCst: String,
    val timeJst: String,
    val sitesJson: String,
    /** bangumi-data sites 映射出的 AniList 条目 ID（逐话播出事件来源） */
    val anilistId: Long? = null,
    /** bangumi-data 的周期播出规则（ISO 8601 重复区间），用于推算预计事件 */
    val broadcastRule: String = "",
    /** 官方话数（用于封顶预计集数） */
    val totalEpisodes: Int = 0,
    /** 条目来源：official（官方日历）/ bgm_data（网播番合并插入），官方刷新只清理 official 来源 */
    val source: String = SOURCE_OFFICIAL,
    /** 仲裁后的下一话集数（0 表示未知） */
    val nextEpisode: Int = 0,
    /** 下一话播出时刻（UTC ISO-8601），空串表示未知 */
    val nextEpisodeAtUtc: String = "",
    /** 下一话时刻可信度（actual / scheduled / predicted），空串表示未知 */
    val nextEpisodeKind: String = "",
) {
    /** 向后兼容属性：若有带具体时间的 UTC 则优先返回，否则退化为裸日期 */
    val beginUtc: String
        get() = beginAtUtc ?: airDate

    companion object {
        const val SOURCE_OFFICIAL = "official"
        const val SOURCE_BGM_DATA = "bgm_data"
        const val UNKNOWN_SORT_MINUTES = 9999
    }
}

/**
 * 逐话播出事件流：时刻表排期的第一手事实。
 * 主键为 (subjectId, episode, source)，同一来源在同一话只保留最新一条事件记录。
 */
@Entity(
    tableName = "air_events",
    primaryKeys = ["subjectId", "episode", "source"],
    indices = [
        Index(value = ["kind", "airAtUtc"]),
        Index(value = ["airAtUtc"]),
    ],
)
data class AirEventEntity(
    val subjectId: Long,
    val episode: Int,
    /** UTC ISO-8601 时刻 */
    val airAtUtc: String,
    val kind: String,
    val source: String,
)

/**
 * 用户追番状态与进度索引表：
 * 仅用于时刻表「我追的」过滤、桌面小组件、开播提醒以及打卡时的本地秒级响应。
 * 个人评分与长短评属于展示层数据，由远端实时下发，不在此表持久化。
 */
@Entity(
    tableName = "user_collections",
    primaryKeys = ["userId", "subjectId"],
    indices = [
        Index(value = ["userId", "type", "updatedAt"]),
    ],
)
data class UserCollectionEntity(
    val userId: Long,
    val subjectId: Long,
    val subjectType: Int,
    val type: Int,
    val epStatus: Int,
    val updatedAt: String,
)

/**
 * AI 追番助手本地会话历史表：
 * 持久化保存用户的历史提问、智能体回复、HITL 操作提案与找源播放清单。
 */
@Entity(
    tableName = "assistant_messages",
    indices = [
        Index(value = ["timestamp"]),
    ],
)
data class AssistantMessageEntity(
    @PrimaryKey val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val pendingActionsJson: String? = null,
    val playableSourcesJson: String? = null,
    val isError: Boolean = false,
    /** 所属会话；多会话引入前的历史消息随破坏性升级整体丢弃 */
    val sessionId: String = "default",
)

/**
 * AniList ↔ bangumi.tv 条目映射缓存。
 *
 * 映射来源只有两条：bangumi-data 月切片里的 `sites` 桥，或 bgm.tv 实时搜索。
 * 一旦解析就持久化——既避免重复网络解析，也让映射在排期行被裁剪后依旧留存。
 * 一个 bgmId 可对应多个 anilistId（拆季/多条目），故以 anilistId 为主键。
 */
@Entity(tableName = "anilist_bgm_mapping")
data class AniListBgmMappingEntity(
    @PrimaryKey val anilistId: Long,
    val bgmId: Long,
    /** 映射到的 bangumi-data sites（已解析成 SiteLink JSON），可直接作为排期的可播来源 */
    val sitesJson: String,
    val title: String,
    val titleCn: String,
    val beginIso: String,
    val endIso: String,
    /** 该映射来自哪个月切片（`YYYY-MM`）；来自实时搜索时为空串 */
    val monthKey: String,
    val updatedAt: Long,
)

/** bangumi-data 月份切片的 ETag 缓存：条件请求命中即 0 字节，未命中才重新解析。 */
@Entity(tableName = "bangumi_data_month_etag")
data class BangumiDataMonthEtagEntity(
    /** `YYYY-MM` */
    @PrimaryKey val monthKey: String,
    val etag: String,
    val updatedAt: Long,
)

/** 追番助手会话：多会话管理的元数据行；消息正文在 assistant_messages 表按 sessionId 归组 */
@Entity(tableName = "assistant_sessions")
data class AssistantSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)
