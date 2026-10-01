package com.infinitezerone.minibgm.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
import com.infinitezerone.minibgm.core.database.entity.AniListBgmMappingEntity
import com.infinitezerone.minibgm.core.database.entity.AssistantMessageEntity
import com.infinitezerone.minibgm.core.database.entity.AssistantSessionEntity
import com.infinitezerone.minibgm.core.database.entity.BangumiDataMonthEtagEntity
import com.infinitezerone.minibgm.core.database.entity.UserCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AirScheduleDao {
    @Query("SELECT * FROM air_schedules WHERE weekday = :weekday ORDER BY sortMinutes ASC, ratingScore DESC")
    fun getSchedulesByWeekday(weekday: Int): Flow<List<AirScheduleEntity>>

    @Query("SELECT * FROM air_schedules")
    fun getAllSchedules(): Flow<List<AirScheduleEntity>>

    @Query("SELECT * FROM air_schedules")
    suspend fun getAllSchedulesList(): List<AirScheduleEntity>

    @Query("SELECT * FROM air_schedules WHERE bgmId IN (:ids)")
    suspend fun getSchedulesByIds(ids: List<Long>): List<AirScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<AirScheduleEntity>)

    /** 清理超出名单窗口的 bgm-data 合并行（防止过期网播番长期滞留） */
    @Query("DELETE FROM air_schedules WHERE source = 'bgm_data' AND airDate < :date")
    suspend fun deleteStaleBgmDataSchedules(date: String)

    /** 删除指定的 bgm-data 条目（用于清理已播完或无未来排期的网播僵尸条目） */
    @Query("DELETE FROM air_schedules WHERE source = 'bgm_data' AND bgmId IN (:ids)")
    suspend fun deleteBgmDataSchedulesByIds(ids: List<Long>)

    @Query("DELETE FROM air_schedules WHERE bgmId NOT IN (:keepIds)")
    suspend fun deleteSchedulesNotIn(keepIds: List<Long>)

    @Query("DELETE FROM air_schedules")
    suspend fun clearSchedules()

    /** 单一事务整体同步与裁决时刻表条目，避免中间态产生 UI 抖动 */
    @Transaction
    suspend fun syncSchedulesTransaction(
        keepBgmIds: List<Long>,
        cutoffDate: String,
        zombieBgmIds: List<Long>,
        reconciledSchedules: List<AirScheduleEntity>,
    ) {
        deleteSchedulesNotIn(keepBgmIds)
        deleteStaleBgmDataSchedules(cutoffDate)
        if (zombieBgmIds.isNotEmpty()) {
            deleteBgmDataSchedulesByIds(zombieBgmIds)
        }
        if (reconciledSchedules.isNotEmpty()) {
            insertSchedules(reconciledSchedules)
        }
    }
}

@Dao
interface AirEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAirEvents(events: List<AirEventEntity>)

    @Query("SELECT * FROM air_events")
    suspend fun getAllAirEvents(): List<AirEventEntity>

    @Query("SELECT * FROM air_events")
    fun getAllAirEventsStream(): Flow<List<AirEventEntity>>

    @Query("DELETE FROM air_events WHERE kind = 'predicted' AND airAtUtc < :isoUtc")
    suspend fun deleteStalePredictedEvents(isoUtc: String)

    @Query("DELETE FROM air_events WHERE kind = 'predicted'")
    suspend fun deleteAllPredictedEvents()

    @Query("DELETE FROM air_events WHERE source = 'anilist' AND subjectId = :subjectId AND airAtUtc IN (:airAts)")
    suspend fun deleteAnilistEventsAt(
        subjectId: Long,
        airAts: List<String>,
    )

    /** 用逐话真值替换同 (subjectId, airAt) 的旧事件（含周排期写入的原始集数事件）；原子执行，避免删一半崩溃丢事件。 */
    @Transaction
    suspend fun replaceAnilistEventsAt(
        subjectId: Long,
        airAts: List<String>,
        events: List<AirEventEntity>,
    ) {
        if (airAts.isNotEmpty()) deleteAnilistEventsAt(subjectId, airAts)
        if (events.isNotEmpty()) insertAirEvents(events)
    }

    @Query("DELETE FROM air_events WHERE subjectId NOT IN (:keepIds)")
    suspend fun deleteEventsNotIn(keepIds: List<Long>)

    /** 单一事务整体同步与裁决播出事件，避免向外界发射部分被清空的中间态 */
    @Transaction
    suspend fun syncAirEventsTransaction(
        keepSubjectIds: List<Long>,
        eventsGroupedBySubject: Map<Long, List<AirEventEntity>>,
    ) {
        deleteEventsNotIn(keepSubjectIds)
        deleteAllPredictedEvents()
        for ((subjectId, events) in eventsGroupedBySubject) {
            val airAts = events.map { it.airAtUtc }.distinct()
            if (airAts.isNotEmpty()) deleteAnilistEventsAt(subjectId, airAts)
            if (events.isNotEmpty()) insertAirEvents(events)
        }
    }

    @Query(
        "SELECT * FROM air_events " +
            "WHERE subjectId IN (:subjectIds) AND airAtUtc >= :fromIso AND airAtUtc <= :toIso " +
            "ORDER BY airAtUtc ASC",
    )
    suspend fun getUpcomingEvents(
        subjectIds: List<Long>,
        fromIso: String,
        toIso: String,
    ): List<AirEventEntity>

    /**
     * 指定时间窗内确有播出事件的条目 id 去重列表（不限定"我追的"）。
     * 供季度导视判断"本季在播"：长期连载番的首播日远在本季之前，
     * 只能靠真实播出事件证明它本季仍在播。
     */
    @Query(
        "SELECT DISTINCT subjectId FROM air_events " +
            "WHERE airAtUtc >= :fromIso AND airAtUtc <= :toIso",
    )
    suspend fun getSubjectIdsWithEventsBetween(
        fromIso: String,
        toIso: String,
    ): List<Long>
}

/**
 * AniList ↔ bangumi.tv 映射与 bangumi-data 月切片 ETag 的本地缓存。
 * 时刻表解析 AniList 周排期时先查这里，命中则零网络开销。
 */
@Dao
interface AniListMappingDao {
    @Query("SELECT * FROM anilist_bgm_mapping WHERE anilistId IN (:anilistIds)")
    suspend fun getMappingsByAniListIds(anilistIds: List<Long>): List<AniListBgmMappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMappings(mappings: List<AniListBgmMappingEntity>)

    @Query("SELECT * FROM bangumi_data_month_etag WHERE monthKey = :monthKey")
    suspend fun getMonthEtag(monthKey: String): BangumiDataMonthEtagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonthEtag(etag: BangumiDataMonthEtagEntity)
}

/** 追番足迹聚合投影：单行统计结果（表为空时 COUNT=0、SUM=0、MAX=NULL） */
data class WatchingFootprintProjection(
    val watchingCount: Int,
    val episodesWatched: Int,
    val monthActiveCount: Int,
    val lastActiveAt: String?,
)

@Dao
interface UserCollectionDao {
    @Query("SELECT * FROM user_collections WHERE userId = :userId AND type = :type ORDER BY updatedAt DESC")
    fun getCollectionsByType(
        userId: Long,
        type: Int,
    ): Flow<List<UserCollectionEntity>>

    /**
     * 追番足迹聚合（在看状态，type = 3）：在看部数、在看中累计追集、
     * 本月有打卡动作的部数与最近一次打卡时刻。
     * updatedAt 为归一化 ISO 文本（TimeUtils.normalizeIsoUtc），LIKE 前缀匹配即按月过滤。
     */
    @Query(
        """
        SELECT
            COUNT(*) AS watchingCount,
            COALESCE(SUM(epStatus), 0) AS episodesWatched,
            COALESCE(SUM(CASE WHEN updatedAt LIKE :monthPrefix || '%' THEN 1 ELSE 0 END), 0) AS monthActiveCount,
            MAX(updatedAt) AS lastActiveAt
        FROM user_collections
        WHERE userId = :userId AND type = 3
        """,
    )
    fun observeWatchingFootprint(
        userId: Long,
        monthPrefix: String,
    ): Flow<WatchingFootprintProjection>

    @Query("SELECT * FROM user_collections WHERE userId = :userId AND subjectId = :subjectId")
    fun getCollectionBySubjectId(
        userId: Long,
        subjectId: Long,
    ): Flow<UserCollectionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: UserCollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollections(collections: List<UserCollectionEntity>)

    @Query("DELETE FROM user_collections WHERE userId = :userId AND subjectId = :subjectId")
    suspend fun deleteBySubjectId(
        userId: Long,
        subjectId: Long,
    )

    @Query("DELETE FROM user_collections WHERE userId = :userId AND type = :type")
    suspend fun deleteByType(
        userId: Long,
        type: Int,
    )

    @Transaction
    suspend fun replaceCollectionsByType(
        userId: Long,
        type: Int,
        collections: List<UserCollectionEntity>,
    ) {
        deleteByType(userId, type)
        insertCollections(collections)
    }

    @Query("DELETE FROM user_collections WHERE userId = :userId")
    suspend fun clearByUserId(userId: Long)

    @Query("DELETE FROM user_collections")
    suspend fun clearAll()
}

@Dao
interface AssistantMessageDao {
    @Query("SELECT * FROM assistant_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<AssistantMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AssistantMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<AssistantMessageEntity>)

    @Query("DELETE FROM assistant_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM assistant_messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)

    @Query("DELETE FROM assistant_messages")
    suspend fun clearAll()
}

@Dao
interface AssistantSessionDao {
    @Query("SELECT * FROM assistant_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<AssistantSessionEntity>>

    @Query("SELECT * FROM assistant_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): AssistantSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AssistantSessionEntity)

    @Query("UPDATE assistant_sessions SET title = :title WHERE id = :sessionId")
    suspend fun renameSession(
        sessionId: String,
        title: String,
    )

    @Query("UPDATE assistant_sessions SET updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun touchSession(
        sessionId: String,
        updatedAt: Long,
    )

    @Query("DELETE FROM assistant_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)
}
