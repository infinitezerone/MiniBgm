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

    @Query("DELETE FROM air_schedules")
    suspend fun clearSchedules()
}

@Dao
interface AirEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAirEvents(events: List<AirEventEntity>)

    @Query("SELECT * FROM air_events")
    suspend fun getAllAirEvents(): List<AirEventEntity>

    @Query("DELETE FROM air_events WHERE kind = 'predicted' AND airAtUtc < :isoUtc")
    suspend fun deleteStalePredictedEvents(isoUtc: String)

    @Query("DELETE FROM air_events WHERE kind = 'predicted'")
    suspend fun deleteAllPredictedEvents()

    @Query("DELETE FROM air_events WHERE subjectId NOT IN (:keepIds)")
    suspend fun deleteEventsNotIn(keepIds: List<Long>)

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

@Dao
interface UserCollectionDao {
    @Query("SELECT * FROM user_collections WHERE userId = :userId AND type = :type ORDER BY updatedAt DESC")
    fun getCollectionsByType(
        userId: Long,
        type: Int,
    ): Flow<List<UserCollectionEntity>>

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
