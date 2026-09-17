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
