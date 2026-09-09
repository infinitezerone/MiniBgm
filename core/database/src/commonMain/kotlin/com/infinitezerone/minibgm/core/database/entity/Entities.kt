package com.infinitezerone.minibgm.core.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: Long,
    val type: Int,
    val name: String,
    val nameCn: String,
    val summary: String,
    val date: String,
    val eps: Int,
    val totalEpisodes: Int,
    val coverUrl: String,
    val ratingScore: Double,
    val ratingRank: Int,
    val ratingTotal: Int = 0,
    val ratingCountJson: String = "",
    val collectionWish: Int = 0,
    val collectionCollect: Int = 0,
    val collectionDoing: Int = 0,
    val collectionOnHold: Int = 0,
    val collectionDropped: Int = 0,
    val tagsJson: String = "",
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "episodes",
    indices = [Index(value = ["subjectId"])],
)
data class EpisodeEntity(
    @PrimaryKey val id: Long,
    val subjectId: Long,
    val sort: Float,
    val ep: Float,
    val name: String,
    val nameCn: String,
    val duration: String,
    val airdate: String,
    val type: Int = 0,
    val desc: String = "",
    val comment: Int = 0,
    val isCollected: Boolean = false,
)

@Entity(tableName = "air_schedules")
data class AirScheduleEntity(
    @PrimaryKey val bgmId: Long,
    val title: String,
    val titleCn: String,
    val coverUrl: String,
    val ratingScore: Double,
    val beginUtc: String,
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
    val updatedAt: Long = 0L,
) {
    companion object {
        const val SOURCE_OFFICIAL = "official"
        const val SOURCE_BGM_DATA = "bgm_data"
    }
}

/**
 * 逐话播出事件流：时刻表排期的第一手事实。
 * 同一话允许多源并存（kind/source 进主键），由仓库层按
 * actual > scheduled > predicted 仲裁后回写条目的 next* 字段。
 */
@Entity(
    tableName = "air_events",
    primaryKeys = ["subjectId", "episode", "kind", "source"],
)
data class AirEventEntity(
    val subjectId: Long,
    val episode: Int,
    /** UTC ISO-8601 时刻 */
    val airAtUtc: String,
    val kind: String,
    val source: String,
)

@Entity(
    tableName = "user_collections",
    primaryKeys = ["userId", "subjectId"],
    indices = [Index(value = ["userId", "type"])],
)
data class UserCollectionEntity(
    val userId: Long,
    val subjectId: Long,
    val subjectType: Int,
    val type: Int,
    val epStatus: Int,
    val updatedAt: String,
)
